package ru.method.linkenvisuals.network;

import java.util.Locale;

import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import ru.method.linkenvisuals.LinkenVisuals;

/**
 * Регистрация reward-пакетов и клиентская логика.
 *
 * <p>C2S {@code linkenvisuals:reward_claim} — клиент шлёт HWID + ник.
 * <p>S2C {@code linkenvisuals:reward_result} — сервер отвечает {@link RewardStatus}
 * (SUCCESS / ALREADY_GET / HWID_ERROR).
 *
 * <p>Вызвать {@link #init()} один раз на клиенте при старте.
 */
public final class RewardNetworking {

    /** Домены сервера (любой поддомен подходит). Ключевая часть — суффикс с точкой-границей. */
    private static final String[] TARGET_DOMAINS = {"artygrief.su", "artygrief.net"};

    /** Один запрос за сессию (сбрасывается на дисконнекте). volatile — событие канала на сетевом потоке. */
    private static volatile boolean claimedThisSession;

    private RewardNetworking() {
    }

    /** Регистрирует типы пакетов (кодеки), приёмник ответа и автоотправку на нужных бэкендах. */
    public static void init() {
        // Типы пакетов регистрируются на ОБЕИХ сторонах канала (иначе кодек неизвестен).
        PayloadTypeRegistry.playC2S().register(RewardClaimC2SPayload.ID, RewardClaimC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RewardResultS2CPayload.ID, RewardResultS2CPayload.CODEC);

        // Приёмник ответа. Хендлер зовётся в сетевом потоке — прыгаем на игровой поток.
        ClientPlayNetworking.registerGlobalReceiver(RewardResultS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> onResult(payload.status())));

        // Автоотправка. Сервер объявляет приём канала (canSend=true) ТОЛЬКО на бэкендах, где стоит плагин
        // (анархия/гриферский); в лобби, на авторизации и капче канала нет. Как только канал появился и домен
        // — artygrief, шлём один раз за сессию. Так лобби/авторизация/капча исключаются без клиентской логики.
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (!claimedThisSession
                    && channels.contains(RewardClaimC2SPayload.ID.id())
                    && isTargetServer()) {
                claimedThisSession = true;
                client.execute(RewardNetworking::sendClaimForCurrentPlayer);
            }
        });
        // Сброс флага при выходе — следующее подключение сможет отправить заново.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> claimedThisSession = false);

        // HWID (материнка+CPU через WMI) считаем заранее в фоне — sendClaim потом не блокирует поток.
        HwidUtil.warmUp();
    }

    /** true, если текущий сервер — {@code artygrief.su}/{@code .net} или любой их поддомен. */
    private static boolean isTargetServer() {
        ServerInfo entry = MinecraftClient.getInstance().getCurrentServerEntry();
        if (entry == null || entry.address == null) {
            return false;
        }
        String host = entry.address.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':'); // срезаем порт (mc.artygrief.su:25565)
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        for (String base : TARGET_DOMAINS) {
            // Точка-граница: пропускает поддомены (fdsh.artygrief.su), но не evilartygrief.su.
            if (host.equals(base) || host.endsWith("." + base)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Отправить запрос на выдачу награды (HWID + ник). Вызывать в play-фазе (игрок в мире).
     * Ничего не делает, если сервер не объявил поддержку канала.
     */
    public static void sendClaim(String hwid, String nick) {
        String safeHwid = hwid == null ? "" : hwid;
        String safeNick = nick == null ? "" : nick;
        if (ClientPlayNetworking.canSend(RewardClaimC2SPayload.ID)) {
            ClientPlayNetworking.send(new RewardClaimC2SPayload(safeHwid, safeNick));
        } else {
            LinkenVisuals.LOGGER.warn("[Reward] сервер не поддерживает канал {} — запрос не отправлен",
                    RewardClaimC2SPayload.ID.id());
        }
    }

    /** Удобный вызов: HWID из {@link HwidUtil} + текущий ник сессии. */
    public static void sendClaimForCurrentPlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String nick = mc.getSession() != null ? mc.getSession().getUsername() : "";
        sendClaim(HwidUtil.get(), nick);
    }

    /** Обработка ответа сервера — лог + сообщение игроку в чат. */
    private static void onResult(RewardStatus status) {
        switch (status) {
            case SUCCESS -> {
                LinkenVisuals.LOGGER.info("[Reward] выдано успешно");
                ru.method.linkenvisuals.utils.chat.ChatUtils.sendMessage("Награда успешно выдана!");
            }
            case ALREADY_GET ->
                // Без чата: автоотправка срабатывает при каждом заходе на режим — иначе спам «уже получали».
                LinkenVisuals.LOGGER.info("[Reward] уже получал ранее");
            case HWID_ERROR -> {
                LinkenVisuals.LOGGER.info("[Reward] этот HWID уже забирал под другим ником");
                ru.method.linkenvisuals.utils.chat.ChatUtils.sendMessage("Награда уже была получена ранее.");
            }
        }
    }
}
