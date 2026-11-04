package ru.method.linkenvisuals.managers.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.common.BooleanUtils;
import ru.method.linkenvisuals.utils.common.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class RemoteBuildControl {
    private static final String ENV_ENABLED = "LV_BUILD_CONTROL_ENABLED";
    private static final String ENV_ENDPOINT = "LV_BUILD_CONTROL_ENDPOINT";
    private static final String ENV_BUILD_ID = "LV_BUILD_ID";
    private static final String PROP_BUILD_ID = "lv.buildId";
    private static final String DEFAULT_ENDPOINT = "http://91.92.33.75:8091/build/status";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private RemoteBuildControl() {
    }

    public static void checkStartupAllowed() {
        // В dev-окружении не блокируем запуск (иначе при отключённом билде нельзя разрабатывать)
        // и не тратим до 3 сек на HTTP-проверку при каждом старте.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        if (!isEnabled()) return;

        String endpoint = resolveEndpoint();
        if (endpoint.isBlank()) return;

        try {
            URI uri = buildUri(endpoint);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) return;

            // На отключение билда реагируем ТОЛЬКО при валидной HMAC-подписи ответа: иначе любой
            // on-path атакующий подделал бы {disabled:true} по открытому HTTP и заблокировал запуск (DoS).
            // Подделка не пройдёт проверку → запуск разрешается (fail-open).
            if (!ru.method.linkenvisuals.utils.net.SyncSignature.verify(
                    response.body(), response.headers().firstValue("X-Signature").orElse(null))) {
                LinkenVisuals.LOGGER.warn("[BuildControl] Unsigned/invalid response; allowing launch");
                return;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean disabled = root.has("disabled") && root.get("disabled").getAsBoolean();
            LinkenVisuals.LOGGER.info("[BuildControl] build={} disabled={}", resolveBuildId(), disabled);
            if (!disabled) return;

            String reason = root.has("reason") ? root.get("reason").getAsString() : "";
            String message = reason == null || reason.isBlank()
                    ? "LinkenVisuals build is disabled remotely."
                    : "LinkenVisuals build is disabled remotely: " + reason;
            // Намеренное «билд отключён» — бросаем СВОЙ тип исключения, чтобы отличить его
            // от RuntimeException'ов парсинга Gson (JsonSyntaxException/IllegalStateException).
            throw new BuildDisabledException(message);
        } catch (BuildDisabledException disabled) {
            // Пробрасываем наружу ТОЛЬКО намеренное отключение билда — оно прерывает запуск.
            throw disabled;
        } catch (Exception exception) {
            // Сюда попадают в т.ч. RuntimeException от Gson при структурно кривом JSON.
            // warn, не debug: иначе сбой проверки (и причину «почему не заблокировало»)
            // невозможно увидеть в обычном latest.log.
            LinkenVisuals.LOGGER.warn("[BuildControl] Startup check failed; allowing launch", exception);
        }
    }

    // Намеренный сигнал «билд отключён удалённо»: только это исключение пробрасывается наружу
    // из checkStartupAllowed и прерывает запуск. Отдельный тип нужен, чтобы кривой JSON
    // (RuntimeException от Gson) не путался с реальным отключением и падал в fail-open ниже.
    private static final class BuildDisabledException extends RuntimeException {
        BuildDisabledException(String message) {
            super(message);
        }
    }

    // Проверка включена ПО УМОЛЧАНИЮ: отключение билда через бота (/builddisable) должно
    // работать у всех игроков без настройки env. LV_BUILD_CONTROL_ENABLED=false — выключить
    // (например, для локальной разработки). Если сервер недоступен — запуск разрешается.
    private static boolean isEnabled() {
        String raw = StringUtils.safeTrim(System.getenv(ENV_ENABLED));
        if (raw.isBlank()) {
            return true;
        }
        return BooleanUtils.parseBoolean(raw, true);
    }

    private static String resolveEndpoint() {
        String env = StringUtils.safeTrim(System.getenv(ENV_ENDPOINT));
        return env.isBlank() ? DEFAULT_ENDPOINT : env;
    }

    private static URI buildUri(String endpoint) {
        String separator = endpoint.contains("?") ? "&" : "?";
        String query = "modId=" + encode(LinkenVisuals.MOD_ID) + "&build=" + encode(resolveBuildId());
        return URI.create(endpoint + separator + query);
    }

    private static String resolveBuildId() {
        String property = StringUtils.safeTrim(System.getProperty(PROP_BUILD_ID));
        if (!property.isBlank()) return property;

        String env = StringUtils.safeTrim(System.getenv(ENV_BUILD_ID));
        if (!env.isBlank()) return env;

        // В fabric.mod.json мод зарегистрирован как "linkenvisuals-fabric", а MOD_ID = "linkenvisuals" —
        // поиск по MOD_ID возвращал пустой Optional, клиент слал build=unknown и отключение
        // билда на него не действовало. Пробуем оба id.
        return FabricLoader.getInstance()
                .getModContainer(LinkenVisuals.MOD_ID + "-fabric")
                .or(() -> FabricLoader.getInstance().getModContainer(LinkenVisuals.MOD_ID))
                .map(container -> StringUtils.safeTrim(container.getMetadata().getVersion().getFriendlyString()))
                .filter(version -> !version.isBlank())
                .orElse("unknown");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
