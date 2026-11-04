package ru.method.linkenvisuals.managers.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.persistence.CosmeticProfileCodec;
import ru.method.linkenvisuals.cosmetics.persistence.CosmeticsRepository;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.misc.PartyModule;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.DepthRenderScope;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.world.WorldUtils;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class UserSyncManager implements Wrapper {

    private static final String DEFAULT_ENDPOINT_URL = "http://91.92.33.75:8080/sync";
    private static final long SYNC_INTERVAL_MS = 5000L;
    // В активной пати опрашиваем часто: метки участников должны появляться практически сразу.
    private static final long SYNC_INTERVAL_PARTY_MS = 400L;
    // Жёсткий лимит состава пати из ответа сервера (защита от раздувания при подделанном ответе).
    private static final int MAX_PARTY_MEMBERS = 64;
    private static final long PARTY_MARKER_COOLDOWN_MS = 450L;
    private static final int MAX_PARTY_MARKERS = 2;
    private static final float MARKER_TEXT_EDGE_MARGIN = 4f;
    private static final float MARKER_TEXT_TOP_MARGIN = 24f;
    private static final float MARKER_TEXT_BOTTOM_MARGIN = 48f;
    private static final float MARKER_TEXT_SCOREBOARD_MARGIN = 150f;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /** Гасит HttpClient при выходе (его selector-поток — не-daemon, иначе JVM не завершается). */
    public void shutdown() {
        try {
            client.shutdownNow();
        } catch (Throwable ignored) {
        }
    }

    private final AtomicBoolean requestInFlight = new AtomicBoolean(false);
    private final Set<UUID> syncedUuids = ConcurrentHashMap.newKeySet();
    private final Set<String> syncedNames = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> partyMembers = new ConcurrentHashMap<>();
    // Ники, которых МЫ добавили в друзья из-за пати (не ручные друзья игрока). Держим отдельно, чтобы
    // убрать их при выходе/распаде пати — иначе durable-список друзей копил бы пати-состав НАВСЕГДА
    // (эксплойт: один раз зашёл к жертве в пати → навсегда в вайтлисте комбат-модулей).
    private final java.util.Set<String> partyAddedFriends = ConcurrentHashMap.newKeySet();
    // Список маркеров — volatile-снимок: заменяем ссылку целиком (List.copyOf), а не clear()+addAll,
    // чтобы render-поток никогда не видел пустой/полузаполненный список (моргание метки на 1 кадр).
    private volatile List<PartyMarker> partyMarkers = List.of();
    private final Map<String, Long> partyMarkerFirstSeenAt = new ConcurrentHashMap<>();
    private final Map<UUID, RemotePetInfo> remotePetsByUuid = new ConcurrentHashMap<>();
    private final Map<String, RemotePetInfo> remotePetsByName = new ConcurrentHashMap<>();
    private final Map<UUID, RemoteHaloInfo> remoteHalosByUuid = new ConcurrentHashMap<>();
    private final Map<String, RemoteHaloInfo> remoteHalosByName = new ConcurrentHashMap<>();
    /**
     * Поколение party-операций: ответ фонового /sync, отправленного ДО create/join/leave,
     * не должен затирать свежесозданное состояние пати (гонка «устаревший ответ»).
     */
    private final java.util.concurrent.atomic.AtomicLong partyOpGeneration = new java.util.concurrent.atomic.AtomicLong();

    @Getter private volatile boolean enabled = true;
    @Getter private volatile String endpointUrl = DEFAULT_ENDPOINT_URL;
    @Getter private volatile String apiKey = "";
    @Getter private volatile String partyCode = null;

    private volatile long nextSyncAt = 0L;
    private String lastServerKey = null;
    private long lastMarkerAt = 0L;

    public UserSyncManager() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
    }

    public void applySettings(boolean enabled, String endpointUrl, String apiKey) {
        this.enabled = enabled;
        this.endpointUrl = endpointUrl == null ? "" : endpointUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isSyncedUser(UUID uuid, String name) {
        if (!enabled) return false;
        if (uuid != null && syncedUuids.contains(uuid)) return true;
        return isSyncedName(name);
    }

    public boolean isSyncedName(String name) {
        if (!enabled || name == null || name.isBlank()) return false;
        return syncedNames.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Для диагностики бейджей: сколько synced-ников известно (0 = список пуст или синк выключен). */
    public int getSyncedNameCount() {
        return enabled ? syncedNames.size() : 0;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (Module.fullNullCheck()) return;

        String serverKey = resolveServerKey();
        if (serverKey == null) {
            if (lastServerKey != null) {
                clearSyncedUsers();
                clearPartyState();
                clearCosmeticsState();
                lastServerKey = null;
            }
            return;
        }

        if (!serverKey.equals(lastServerKey)) {
            clearSyncedUsers();
            clearPartyState();
            clearCosmeticsState();
            lastServerKey = serverKey;
        }

        if (!enabled || endpointUrl.isBlank()) {
            clearPartyState();
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextSyncAt) return;
        nextSyncAt = now + (isInParty() ? SYNC_INTERVAL_PARTY_MS : SYNC_INTERVAL_MS);

        syncNow(serverKey);
    }

    private void syncNow(String serverKey) {
        if (!requestInFlight.compareAndSet(false, true)) return;
        if (mc.player == null || mc.player.getGameProfile() == null) {
            requestInFlight.set(false);
            return;
        }

        UUID uuid = mc.player.getGameProfile().id();
        String name = mc.player.getGameProfile().name();
        if (uuid == null || name == null || name.isBlank()) {
            requestInFlight.set(false);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("server", serverKey);
        JsonObject user = new JsonObject();
        user.addProperty("uuid", uuid.toString());
        user.addProperty("name", name);
        payload.add("user", user);
        appendCosmeticsPayload(payload);
        if (!apiKey.isBlank()) payload.addProperty("token", apiKey);

        HttpRequest request;
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json");
            if (!apiKey.isBlank()) requestBuilder.header("X-Api-Key", apiKey);
            request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
        } catch (Exception e) {
            // Кривой endpointUrl из настроек кидал исключение прямо в тик-хендлере и НАВСЕГДА оставлял
            // requestInFlight=true (синк умирал молча). Снимаем флаг и выходим.
            requestInFlight.set(false);
            return;
        }

        long generationAtSend = partyOpGeneration.get();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    requestInFlight.set(false);
                    if (throwable != null || response == null || response.statusCode() != 200) {
                        return;
                    }
                    // Отбрасываем ответ без валидной HMAC-подписи (защита от подделки по открытому HTTP).
                    if (!ru.method.linkenvisuals.utils.net.SyncSignature.verify(
                            response.body(), response.headers().firstValue("X-Signature").orElse(null))) {
                        return;
                    }
                    // whenComplete молча глотает исключения лямбды — оборачиваем, чтобы сбой парсинга
                    // ответа был виден в логе, а не пропадал бесследно.
                    try {
                        updateSyncedUsers(response.body(), generationAtSend);
                    } catch (Exception ex) {
                        LinkenVisuals.LOGGER.warn("[Sync] Failed to apply /sync response", ex);
                    }
                });
    }

    private void updateSyncedUsers(String body, long generationAtSend) {
        if (body == null || body.isBlank()) return;
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        updateSyncedUsers(root);
        // Ответ устарел (пока он летел, случилась party-операция) — не трогаем пати.
        if (partyOpGeneration.get() == generationAtSend) {
            updatePartyState(root);
        }
        updateCosmeticsState(root);
    }

    private void updateCosmeticsState(JsonObject root) {
        CosmeticsService service = LinkenVisuals.getInstance().getCosmeticsService();
        if (service == null) {
            return;
        }
        CosmeticsRepository repository = service.repository();
        if (!root.has("users") || !root.get("users").isJsonArray()) {
            repository.clearRemoteProfiles();
            remotePetsByUuid.clear();
            remotePetsByName.clear();
            remoteHalosByUuid.clear();
            remoteHalosByName.clear();
            return;
        }
        Map<UUID, CosmeticProfile> profilesByUuid = new java.util.HashMap<>();
        Map<String, CosmeticProfile> profilesByName = new java.util.HashMap<>();
        Map<UUID, RemotePetInfo> petsByUuid = new java.util.HashMap<>();
        Map<String, RemotePetInfo> petsByName = new java.util.HashMap<>();
        Map<UUID, RemoteHaloInfo> halosByUuid = new java.util.HashMap<>();
        Map<String, RemoteHaloInfo> halosByName = new java.util.HashMap<>();

        JsonArray users = root.getAsJsonArray("users");
        for (JsonElement element : users) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String uuidRaw = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
            String nameRaw = obj.has("name") ? obj.get("name").getAsString() : "";
            UUID uuid = null;
            if (uuidRaw != null && !uuidRaw.isBlank()) {
                try {
                    uuid = UUID.fromString(uuidRaw);
                } catch (IllegalArgumentException ignored) {
                }
            }
            String nameKey = nameRaw == null || nameRaw.isBlank()
                    ? null
                    : nameRaw.trim().toLowerCase(Locale.ROOT);
            if (uuid == null && nameKey == null) continue;

            if (!obj.has("cosmetics") || !obj.get("cosmetics").isJsonObject()) {
                continue;
            }
            JsonObject cosmetics = obj.getAsJsonObject("cosmetics");
            CosmeticProfile profile = CosmeticProfileCodec.fromJson(cosmetics);
            if (!profile.isEmpty()) {
                if (uuid != null) profilesByUuid.put(uuid, profile);
                if (nameKey != null) profilesByName.put(nameKey, profile);
            }

            RemotePetInfo pet = RemotePetInfo.fromJson(cosmetics.get("pet"));
            if (pet != null) {
                if (uuid != null) petsByUuid.put(uuid, pet);
                if (nameKey != null) petsByName.put(nameKey, pet);
            }

            RemoteHaloInfo halo = RemoteHaloInfo.fromJson(cosmetics.get("halo"));
            if (halo != null) {
                if (uuid != null) halosByUuid.put(uuid, halo);
                if (nameKey != null) halosByName.put(nameKey, halo);
            }
        }

        repository.replaceRemoteProfiles(profilesByUuid, profilesByName);
        // putAll → keySet().retainAll (не clear+putAll): render-поток читает эти карты каждый кадр,
        // окно пустоты между clear() и putAll() давало моргание питомцев/нимбов на 1 кадр.
        remotePetsByUuid.putAll(petsByUuid);
        remotePetsByUuid.keySet().retainAll(petsByUuid.keySet());
        remotePetsByName.putAll(petsByName);
        remotePetsByName.keySet().retainAll(petsByName.keySet());
        remoteHalosByUuid.putAll(halosByUuid);
        remoteHalosByUuid.keySet().retainAll(halosByUuid.keySet());
        remoteHalosByName.putAll(halosByName);
        remoteHalosByName.keySet().retainAll(halosByName.keySet());
    }

    /** Питомец другого игрока (по uuid или, как фолбэк, по нику). null — питомца нет. */
    public RemotePetInfo getRemotePet(UUID uuid, String name) {
        if (!enabled) return null;
        if (uuid != null) {
            RemotePetInfo info = remotePetsByUuid.get(uuid);
            if (info != null) return info;
        }
        if (name != null && !name.isBlank()) {
            return remotePetsByName.get(name.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /** Нимб другого игрока (его цвет/размер). null — нимб выключен или игрок не синхронизирован. */
    public RemoteHaloInfo getRemoteHalo(UUID uuid, String name) {
        if (!enabled) return null;
        if (uuid != null) {
            RemoteHaloInfo info = remoteHalosByUuid.get(uuid);
            if (info != null) return info;
        }
        if (name != null && !name.isBlank()) {
            return remoteHalosByName.get(name.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    public void requestCosmeticsUpdate(Consumer<Void> onSuccess, Consumer<String> onError) {
        String serverKey = resolveServerKey();
        if (!canUseParty(serverKey)) {
            if (onError != null) onError.accept("not_ready");
            return;
        }
        JsonObject payload = buildUserPayload(serverKey);
        appendCosmeticsPayload(payload);
        sendPartyRequest("cosmetics/update", payload, root -> {
            if (onSuccess != null) onSuccess.accept(null);
        }, onError);
    }

    /** Закреплённый сервер из ответа бэкенда (общий для всех клиентов, задаётся админом через бота). */
    public record PinnedServer(String ip, String name) {
    }

    /**
     * Тянет список закреплённых серверов подписанным GET {@code /pinned}. Список общий для всех клиентов
     * (админ задаёт его через Telegram-бота). Ответ проверяется HMAC-подписью так же, как /sync — по
     * открытому HTTP это отсекает подделку. Колбэк приходит в HTTP-потоке: вызывающий сам маршалит в
     * клиентский тред. При выключенном синке/пустом endpoint — пустой список.
     */
    public void fetchPinnedServers(Consumer<List<PinnedServer>> onResult) {
        if (onResult == null) return;
        if (!enabled || endpointUrl.isBlank()) {
            onResult.accept(List.of());
            return;
        }
        URI uri;
        try {
            uri = resolveEndpoint("pinned");
        } catch (Exception e) {
            onResult.accept(List.of());
            return;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(4))
                .GET();
        if (!apiKey.isBlank()) builder.header("X-Api-Key", apiKey);
        client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    if (throwable != null || response == null || response.statusCode() != 200) {
                        onResult.accept(List.of());
                        return;
                    }
                    if (!ru.method.linkenvisuals.utils.net.SyncSignature.verify(
                            response.body(), response.headers().firstValue("X-Signature").orElse(null))) {
                        onResult.accept(List.of());
                        return;
                    }
                    onResult.accept(parsePinned(response.body()));
                });
    }

    /** Разбор ответа /pinned с жёсткими лимитами (защита от раздувания подделанным ответом). */
    private static List<PinnedServer> parsePinned(String body) {
        List<PinnedServer> out = new ArrayList<>();
        if (body == null || body.isBlank()) return out;
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("servers") || !root.get("servers").isJsonArray()) return out;
            for (JsonElement el : root.getAsJsonArray("servers")) {
                if (out.size() >= 20) break;
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String ip = o.has("ip") ? o.get("ip").getAsString() : "";
                if (ip == null || ip.isBlank()) continue;
                ip = ip.trim();
                if (ip.length() > 128) ip = ip.substring(0, 128);
                String name = o.has("name") ? o.get("name").getAsString() : "";
                name = name == null || name.isBlank() ? ip : name.trim();
                if (name.length() > 48) name = name.substring(0, 48);
                out.add(new PinnedServer(ip, name));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void appendCosmeticsPayload(JsonObject payload) {
        CosmeticsService service = LinkenVisuals.getInstance().getCosmeticsService();
        if (service == null) {
            return;
        }
        JsonObject cosmetics = service.repository().localProfileJson();
        ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEffects module = cosmeticEffectsModule();
        if (module != null) {
            JsonObject pet = module.petSyncPayload();
            if (pet != null) {
                cosmetics.add("pet", pet);
            }
            JsonObject halo = module.haloSyncPayload();
            if (halo != null) {
                cosmetics.add("halo", halo);
            }
        }
        payload.add("cosmetics", cosmetics);
    }

    private ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEffects cosmeticEffectsModule() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return null;
        return instance.getModuleManager().getModule(
                ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEffects.class);
    }

    private void updateSyncedUsers(JsonObject root) {
        if (!root.has("users")) return;
        JsonArray users = root.getAsJsonArray("users");
        Set<UUID> newUuids = new HashSet<>();
        Set<String> newNames = new HashSet<>();
        for (JsonElement element : users) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String uuidRaw = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
            String nameRaw = obj.has("name") ? obj.get("name").getAsString() : "";
            if (uuidRaw == null || uuidRaw.isBlank() || nameRaw == null || nameRaw.isBlank()) continue;
            try {
                newUuids.add(UUID.fromString(uuidRaw));
            } catch (IllegalArgumentException ignored) {
            }
            newNames.add(nameRaw.trim().toLowerCase(Locale.ROOT));
        }
        // addAll → retainAll (не clear+addAll): коллекции читаются render-потоком каждый кадр, и между
        // clear() и addAll() читатель видел ПУСТОЙ набор → синхро-косметика/бейджи моргали на 1 кадр.
        // Такой порядок держит набор непустым (кратко надмножество, затем обрезка) — пропаданий нет.
        syncedUuids.addAll(newUuids);
        syncedUuids.retainAll(newUuids);
        syncedNames.addAll(newNames);
        syncedNames.retainAll(newNames);
    }

    public boolean isInParty() {
        return partyCode != null && !partyCode.isBlank();
    }

    /** Состоит ли игрок в НАШЕЙ пати (по uuid, фолбэк по нику — офлайн-сервера). */
    public boolean isPartyMember(UUID uuid, String name) {
        if (!isInParty()) return false;
        if (uuid != null && partyMembers.containsKey(uuid)) return true;
        if (name != null && !name.isBlank()) {
            for (String member : partyMembers.values()) {
                if (name.equalsIgnoreCase(member)) return true;
            }
        }
        return false;
    }

    /** Имена участников текущей пати (отсортированы) — для команды .party info. */
    public java.util.List<String> partyMemberNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String name : partyMembers.values()) {
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean containsPartyMemberName(String message) {
        if (!isInParty() || message == null || message.isBlank()) return false;
        for (String name : partyMembers.values()) {
            if (name == null || name.isBlank()) continue;
            if (message.contains(name)) return true;
        }
        return false;
    }

    public void requestPartyCreate(Consumer<String> onSuccess, Consumer<String> onError) {
        String serverKey = resolveServerKey();
        if (!canUseParty(serverKey)) {
            if (onError != null) onError.accept("not_ready");
            return;
        }
        JsonObject payload = buildUserPayload(serverKey);
        sendPartyRequest("party/create", payload, root -> {
            String code = partyCode;
            if (code == null || code.isBlank()) {
                if (onError != null) onError.accept("bad_response");
                return;
            }
            if (onSuccess != null) onSuccess.accept(code);
        }, onError);
    }

    public void requestPartyJoin(String code, Consumer<String> onSuccess, Consumer<String> onError) {
        String serverKey = resolveServerKey();
        String normalized = normalizeCode(code);
        if (!canUseParty(serverKey) || normalized.isBlank()) {
            if (onError != null) onError.accept("not_ready");
            return;
        }
        JsonObject payload = buildUserPayload(serverKey);
        payload.addProperty("code", normalized);
        sendPartyRequest("party/join", payload, root -> {
            String joined = partyCode;
            if (joined == null || joined.isBlank()) {
                if (onError != null) onError.accept("bad_response");
                return;
            }
            if (onSuccess != null) onSuccess.accept(joined);
        }, onError);
    }

    public void requestPartyLeave(Consumer<Void> onSuccess, Consumer<String> onError) {
        String serverKey = resolveServerKey();
        if (!canUseParty(serverKey)) {
            if (onError != null) onError.accept("not_ready");
            return;
        }
        if (!isInParty()) {
            if (onError != null) onError.accept("not_in_party");
            return;
        }
        JsonObject payload = buildUserPayload(serverKey);
        sendPartyRequest("party/leave", payload, root -> {
            clearPartyState();
            if (onSuccess != null) onSuccess.accept(null);
        }, onError);
    }

    public void sendPartyMarker(Vec3d position) {
        if (position == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMarkerAt < PARTY_MARKER_COOLDOWN_MS) return;
        lastMarkerAt = now;

        String serverKey = resolveServerKey();
        if (!canUseParty(serverKey) || !isInParty()) return;

        JsonObject payload = buildUserPayload(serverKey);
        payload.addProperty("code", normalizeCode(partyCode));
        JsonObject marker = new JsonObject();
        marker.addProperty("x", position.x);
        marker.addProperty("y", position.y);
        marker.addProperty("z", position.z);
        String dimensionKey = getDimensionKey();
        String dim = dimensionKey == null ? "" : dimensionKey;
        marker.addProperty("dim", dim);
        payload.add("marker", marker);

        // Оптимистично показываем СВОЮ метку мгновенно, не дожидаясь ответа сервера: ключ совпадёт
        // с серверным, так что createdAt сохранится, а ближайший updatePartyState пересоберёт список
        // уже с подтверждённой меткой (возможный дубль живёт лишь до этого пересбора и невидим).
        if (mc.player != null && mc.player.getGameProfile() != null) {
            String selfName = mc.player.getGameProfile().name();
            UUID selfUuid = mc.player.getGameProfile().id();
            String markerKey = buildMarkerKey(selfUuid, selfName, position.x, position.y, position.z, dim);
            long createdAt = partyMarkerFirstSeenAt.computeIfAbsent(markerKey, key -> System.currentTimeMillis());
            List<PartyMarker> updated = new ArrayList<>(partyMarkers);
            updated.add(new PartyMarker(selfName, selfUuid, position.x, position.y, position.z, dim, createdAt));
            partyMarkers = List.copyOf(updated);
        }

        sendPartyRequest("party/mark", payload, root -> {}, null);
    }

    private void sendPartyRequest(String path, JsonObject payload, Consumer<JsonObject> onSuccess, Consumer<String> onError) {
        URI uri;
        try {
            uri = resolveEndpoint(path);
        } catch (Exception e) {
            if (onError != null) onError.accept("bad_endpoint");
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json");
        if (!apiKey.isBlank()) requestBuilder.header("X-Api-Key", apiKey);

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        boolean partyMutation = path.startsWith("party/");
        if (partyMutation) {
            // Инвалидируем ответы фоновых /sync УЖЕ ПРИ ОТПРАВКЕ мутации (а не в её ответе): иначе
            // /sync, отправленный до mark/create/join, приходил раньше ответа мутации, проходил проверку
            // generation и затирал оптимистичную метку/состав пати серверным состоянием без изменений.
            partyOpGeneration.incrementAndGet();
        }
        long generationAtSend = partyOpGeneration.get();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    if (throwable != null || response == null) {
                        // Реальный обрыв связи — сервер действительно не ответил.
                        if (onError != null) onError.accept("request_failed");
                        return;
                    }
                    if (response.statusCode() != 200) {
                        // Сервер ОТВЕТИЛ, но ошибкой (404 — пати не найдена и т.п.). Это не «не ответил».
                        if (onError != null) onError.accept(extractServerError(response.body()));
                        return;
                    }
                    // Отбрасываем ответ без валидной HMAC-подписи (защита от подделки по открытому HTTP).
                    if (!ru.method.linkenvisuals.utils.net.SyncSignature.verify(
                            response.body(), response.headers().firstValue("X-Signature").orElse(null))) {
                        if (onError != null) onError.accept("bad_response");
                        return;
                    }
                    JsonObject root;
                    try {
                        root = JsonParser.parseString(response.body()).getAsJsonObject();
                    } catch (Exception e) {
                        if (onError != null) onError.accept("bad_response");
                        return;
                    }
                    // whenComplete молча глотает исключения — оборачиваем применение состояния,
                    // чтобы сбой парсинга/применения был виден в логе.
                    try {
                        if (partyMutation) {
                            // Ответ самой мутации — авторитетный, применяем безусловно + просим свежий /sync.
                            nextSyncAt = 0L;
                            updatePartyState(root);
                        } else if (partyOpGeneration.get() == generationAtSend) {
                            // Не-party запрос (cosmetics/update): применяем пати из ответа только
                            // если за время полёта не случилось party-операции.
                            updatePartyState(root);
                        }
                        updateSyncedUsers(root);
                        updateCosmeticsState(root);
                    } catch (Exception ex) {
                        LinkenVisuals.LOGGER.warn("[Sync] Failed to apply party response", ex);
                        if (onError != null) onError.accept("bad_response");
                        return;
                    }
                    if (onSuccess != null) onSuccess.accept(root);
                });
    }

    private JsonObject buildUserPayload(String serverKey) {
        JsonObject payload = new JsonObject();
        payload.addProperty("server", serverKey);
        JsonObject user = new JsonObject();
        user.addProperty("uuid", mc.player.getGameProfile().id().toString());
        user.addProperty("name", mc.player.getGameProfile().name());
        payload.add("user", user);
        if (!apiKey.isBlank()) payload.addProperty("token", apiKey);
        return payload;
    }

    private boolean canUseParty(String serverKey) {
        return enabled && !endpointUrl.isBlank() && serverKey != null && mc.player != null && mc.player.getGameProfile() != null;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    // Валидный ник Minecraft: 1..16 символов из [A-Za-z0-9_]. Отсекает мусор/инъекции из ответа
    // сервера (open HTTP → ответ можно подделать; ник участника влияет на боевой таргетинг).
    private static boolean isValidUsername(String name) {
        if (name == null) return false;
        int len = name.length();
        if (len < 1 || len > 16) return false;
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    private URI resolveEndpoint(String path) {
        URI base = URI.create(endpointUrl).resolve(".");
        String sanitized = path.startsWith("/") ? path.substring(1) : path;
        return base.resolve(sanitized);
    }

    /** Известный код ошибки из тела не-200 ответа (доверяем только whitelist'у, а не любой строке). */
    private static String extractServerError(String body) {
        if (body == null || body.isBlank()) return "server_error";
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (obj.has("error")) {
                String error = obj.get("error").getAsString();
                return switch (error == null ? "" : error) {
                    case "party_not_found", "missing_code", "forbidden",
                            "not_in_party", "missing_fields", "bad_json" -> error;
                    default -> "server_error";
                };
            }
        } catch (Exception ignored) {
        }
        return "server_error";
    }

    private static UUID localPlayerUuid() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getGameProfile() == null) {
            return null;
        }
        return client.player.getGameProfile().id();
    }

    /** Сообщение в чат о входе/выходе участника. Строго на клиентском треде (вызов из async HTTP-колбэка). */
    private void notifyPartyChange(String key, String memberName) {
        if (memberName == null || memberName.isBlank()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> ChatUtils.sendMessage(I18n.translate(key, memberName)));
    }

    private void updatePartyState(JsonObject root) {
        if (!root.has("party") || !root.get("party").isJsonObject()) {
            clearPartyState();
            return;
        }

        JsonObject party = root.getAsJsonObject("party");
        String code = party.has("code") ? party.get("code").getAsString() : "";
        if (code == null || code.isBlank()) {
            clearPartyState();
            return;
        }
        partyCode = code;

        // Собираем новый состав в локальную карту, затем атомарно переносим (putAll → retainAll),
        // чтобы render-поток (isPartyMember/фильтр видимости PARTY) не видел пустую пати на 1 кадр.
        // Валидация: только корректные MC-ники (защита от подделанного ответа по открытому HTTP —
        // ник участника исключает игрока из боевого таргетинга), с жёстким лимитом состава.
        Map<UUID, String> newMembers = new java.util.HashMap<>();
        if (party.has("members") && party.get("members").isJsonArray()) {
            JsonArray members = party.getAsJsonArray("members");
            for (JsonElement element : members) {
                if (newMembers.size() >= MAX_PARTY_MEMBERS) break;
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                String uuidRaw = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                String nameRaw = obj.has("name") ? obj.get("name").getAsString() : "";
                if (uuidRaw == null || uuidRaw.isBlank() || !isValidUsername(nameRaw)) continue;
                try {
                    newMembers.put(UUID.fromString(uuidRaw), nameRaw);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        // Уведомления о входе/выходе: сравниваем ТЕКУЩИЙ состав (до обновления) с новым.
        // Не спамим при первом входе в пати (текущий состав пуст = мы только что зашли/создали)
        // и не пишем про самого себя.
        if (!partyMembers.isEmpty()) {
            UUID selfUuid = localPlayerUuid();
            for (Map.Entry<UUID, String> entry : newMembers.entrySet()) {
                if (!partyMembers.containsKey(entry.getKey()) && !entry.getKey().equals(selfUuid)) {
                    notifyPartyChange("commands.party.member_joined", entry.getValue());
                }
            }
            for (Map.Entry<UUID, String> entry : partyMembers.entrySet()) {
                if (!newMembers.containsKey(entry.getKey()) && !entry.getKey().equals(selfUuid)) {
                    notifyPartyChange("commands.party.member_left", entry.getValue());
                }
            }
        }
        partyMembers.putAll(newMembers);
        partyMembers.keySet().retainAll(newMembers.keySet());

        syncPartyFriends();

        List<PartyMarker> parsedMarkers = new ArrayList<>();
        Set<String> activeMarkers = new HashSet<>();
        long now = System.currentTimeMillis();
        if (party.has("markers") && party.get("markers").isJsonArray()) {
            JsonArray markers = party.getAsJsonArray("markers");
            for (JsonElement element : markers) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                double x = obj.has("x") ? obj.get("x").getAsDouble() : 0.0;
                double y = obj.has("y") ? obj.get("y").getAsDouble() : 0.0;
                double z = obj.has("z") ? obj.get("z").getAsDouble() : 0.0;
                String owner = obj.has("owner") ? obj.get("owner").getAsString() : "";
                String dim = obj.has("dim") ? obj.get("dim").getAsString() : "";
                UUID ownerUuid = null;
                if (obj.has("owner_uuid")) {
                    try {
                        ownerUuid = UUID.fromString(obj.get("owner_uuid").getAsString());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if ((owner == null || owner.isBlank()) && ownerUuid != null) {
                    owner = partyMembers.getOrDefault(ownerUuid, "");
                }
                String markerKey = buildMarkerKey(ownerUuid, owner, x, y, z, dim);
                activeMarkers.add(markerKey);
                long createdAt = partyMarkerFirstSeenAt.computeIfAbsent(markerKey, key -> now);
                parsedMarkers.add(new PartyMarker(owner, ownerUuid, x, y, z, dim, createdAt));
            }
        }
        if (activeMarkers.isEmpty()) {
            partyMarkerFirstSeenAt.clear();
        } else {
            partyMarkerFirstSeenAt.keySet().removeIf(key -> !activeMarkers.contains(key));
        }
        if (parsedMarkers.size() > MAX_PARTY_MARKERS) {
            parsedMarkers = new ArrayList<>(parsedMarkers.subList(
                    parsedMarkers.size() - MAX_PARTY_MARKERS,
                    parsedMarkers.size()
            ));
        }
        // Атомарная замена ссылки — без окна пустоты (см. поле partyMarkers).
        partyMarkers = List.copyOf(parsedMarkers);
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (Module.fullNullCheck()) return;
        if (!isInParty() || partyMarkers.isEmpty()) return;
        String currentDim = getDimensionKey();
        if (currentDim == null) return;
        long now = System.currentTimeMillis();
        long markerLifetimeMs = resolveMarkerLifetimeMs();

        for (PartyMarker marker : partyMarkers) {
            if (marker == null) continue;
            if (markerLifetimeMs > 0L && now - marker.createdAt > markerLifetimeMs) continue;
            if (!marker.dimension.isBlank() && !marker.dimension.equals(currentDim)) continue;
            renderPartyMarker(e, marker);
        }
    }

    private void renderPartyMarker(EventRender2D e, PartyMarker marker) {
        Vec3d pos = new Vec3d(marker.x, marker.y, marker.z);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d directionToMarker = pos.subtract(cameraPos).normalize();
        org.joml.Vector3fc cameraDirectionF = mc.gameRenderer.getCamera().getHorizontalPlane();
        Vec3d cameraDirection = new Vec3d(cameraDirectionF.x(), cameraDirectionF.y(), cameraDirectionF.z());
        double dotProduct = directionToMarker.dotProduct(cameraDirection);
        if (dotProduct <= 0.05) return;

        Vec3d screenPos = WorldUtils.getPosition(pos);
        if (screenPos.z <= 0) return;

        if (screenPos.getX() < -100 || screenPos.getX() > mc.getWindow().getScaledWidth() + 100) return;
        if (screenPos.getY() < -100 || screenPos.getY() > mc.getWindow().getScaledHeight() + 100) return;

        double distance = mc.player.getEntityPos().distanceTo(pos);
        float alpha = Math.max(0.4f, 1.0f - (float) (distance / 2500.0));

        String owner = marker.owner == null || marker.owner.isBlank() ? "Unknown" : marker.owner;
        String distanceText = Math.round(distance) + "m";

        float screenX = (float) screenPos.getX();
        float screenY = (float) screenPos.getY();

        Color globalColor = ColorUtils.getGlobalColor();
        Color markerColor = new Color(globalColor.getRed(), globalColor.getGreen(), globalColor.getBlue(), (int) (255 * alpha));
        float imageSize = 32f;

        try (DepthRenderScope ignored = DepthRenderScope.lequal()) {
            e.getContext().getMatrices().pushMatrix();
            e.getContext().getMatrices().translate(screenX, screenY);
            Render2D.drawTexture(
                    e.getContext().getMatrices(),
                    -imageSize / 2f,
                    -imageSize / 2f,
                    imageSize,
                    imageSize,
                    0f,
                    LinkenVisuals.id("client/waypoint/way.png"),
                    markerColor
            );
            e.getContext().getMatrices().popMatrix();
        }

        float nameWidth = Fonts.ROUNDBOLD.getWidth(owner, 8f);
        float distanceWidth = Fonts.ROUNDBOLD.getWidth(distanceText, 7f);
        float backgroundWidth = Math.max(nameWidth, distanceWidth) + 12f;
        float backgroundHeight = 26f;

        float backgroundX = screenX - backgroundWidth / 2f;
        float backgroundY = screenY + 16f;
        float[] clampedRect = clampMarkerTextRect(backgroundX, backgroundY, backgroundWidth, backgroundHeight);
        backgroundX = clampedRect[0];
        backgroundY = clampedRect[1];

        Color backgroundColor = new Color(0, 0, 0, (int) (150 * alpha));
        Render2D.drawRoundedRect(
                e.getContext().getMatrices(),
                backgroundX,
                backgroundY,
                backgroundWidth,
                backgroundHeight,
                3f,
                backgroundColor
        );

        Color nameColor = new Color(255, 255, 255, (int) (255 * alpha));
        Color distanceColor = new Color(200, 200, 200, (int) (220 * alpha));
        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(8f),
                owner,
                backgroundX + (backgroundWidth - nameWidth) / 2f,
                backgroundY + 5f,
                nameColor
        );
        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(7f),
                distanceText,
                backgroundX + (backgroundWidth - distanceWidth) / 2f,
                backgroundY + 16f,
                distanceColor
        );
    }

    private float[] clampMarkerTextRect(float x, float y, float width, float height) {
        float screenWidth = mc.getWindow().getScaledWidth();
        float screenHeight = mc.getWindow().getScaledHeight();

        float rightReserved = MARKER_TEXT_EDGE_MARGIN;
        if (mc.world != null
                && mc.world.getScoreboard() != null
                && mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) != null) {
            rightReserved = MARKER_TEXT_SCOREBOARD_MARGIN;
        }

        float minX = MARKER_TEXT_EDGE_MARGIN;
        float maxX = Math.max(minX, screenWidth - rightReserved - width);
        float minY = MARKER_TEXT_TOP_MARGIN;
        float maxY = Math.max(minY, screenHeight - MARKER_TEXT_BOTTOM_MARGIN - height);

        float clampedX = Math.max(minX, Math.min(x, maxX));
        float clampedY = Math.max(minY, Math.min(y, maxY));
        return new float[]{clampedX, clampedY};
    }

    private String getDimensionKey() {
        if (mc.world == null || mc.world.getRegistryKey() == null) return null;
        return mc.world.getRegistryKey().getValue().toString();
    }

    private void clearSyncedUsers() {
        syncedUuids.clear();
        syncedNames.clear();
    }

    private void clearPartyState() {
        partyCode = null;
        partyMembers.clear();
        // Снимаем эфемерных пати-друзей из durable-списка — при распаде/выходе пати они не должны
        // остаться в друзьях (иначе комбат-модули навсегда перестают их атаковать). Ручные друзья
        // сюда не попадали (в partyAddedFriends только те, кого добавили мы).
        FriendManager friendManager = LinkenVisuals.getInstance().getFriendManager();
        if (friendManager != null) {
            for (String name : partyAddedFriends) {
                friendManager.remove(name);
            }
        }
        partyAddedFriends.clear();
        partyMarkers = List.of();
        partyMarkerFirstSeenAt.clear();
    }

    private void clearCosmeticsState() {
        CosmeticsService service = LinkenVisuals.getInstance().getCosmeticsService();
        if (service != null) {
            service.clearRemoteProfiles();
        }
        remotePetsByUuid.clear();
        remotePetsByName.clear();
        remoteHalosByUuid.clear();
        remoteHalosByName.clear();
    }

    /**
     * Держит комбат-исключение для текущего состава пати ЭФЕМЕРНО: добавляет ников пати в друзья, но
     * помнит, кого добавил ({@link #partyAddedFriends}), и снимает выбывших. Полное снятие — в
     * {@link #clearPartyState()}. Ручные друзья игрока не трогаются (добавляем только если ника ещё нет).
     */
    private void syncPartyFriends() {
        FriendManager friendManager = LinkenVisuals.getInstance().getFriendManager();
        if (friendManager == null) return;

        String selfName = null;
        if (mc.player != null && mc.player.getGameProfile() != null) {
            selfName = mc.player.getGameProfile().name();
        }

        java.util.Set<String> current = new java.util.HashSet<>();
        for (String member : partyMembers.values()) {
            if (member == null || member.isBlank()) continue;
            if (selfName != null && selfName.equalsIgnoreCase(member)) continue;
            current.add(member);
            if (!friendManager.isFriendIgnoreCase(member)) {
                friendManager.add(member);
                partyAddedFriends.add(member); // помечаем как эфемерного пати-друга (снимем при выходе)
            }
        }
        // Выбывших из пати (но добавленных нами) — убираем из durable-списка, чтобы он не копил состав.
        partyAddedFriends.removeIf(name -> {
            if (current.contains(name)) return false;
            friendManager.remove(name);
            return true;
        });
    }

    /**
     * Домены, считающиеся ОДНИМ сервером (один проект на нескольких доменах/TLD). Ключ и значение —
     * «зарегистрированный домен» (SLD.TLD); все ключи сводятся к своему значению. Пример: у проекта
     * домены artygrief.su и artygrief.net — один сервер, а artygrief.com — уже другой.
     * Дополняй РЕАЛЬНЫМИ доменами своего проекта (или попроси вынести в конфиг).
     */
    private static final Map<String, String> SERVER_DOMAIN_ALIASES = Map.of(
            "artygrief.net", "artygrief.su"
    );

    private String resolveServerKey() {
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null || info.address == null) return null;
        String address = info.address.trim().toLowerCase(Locale.ROOT);
        if (address.isEmpty()) return null;
        String domain = registrableDomain(address);           // поддомен и порт срезаны → SLD.TLD
        return SERVER_DOMAIN_ALIASES.getOrDefault(domain, domain);
    }

    /**
     * «Зарегистрированный домен» сервера — SLD.TLD, без поддомена и порта. Тот же сервер = совпал этот
     * домен: govno.artygrief.su и test.artygrief.su → artygrief.su; а artygrief.com — уже ДРУГОЙ сервер
     * (разные TLD не сливаем). Один проект на нескольких доменах сводится таблицей SERVER_DOMAIN_ALIASES.
     * IP и одиночные хосты (localhost) — как есть.
     * ponytail: naive SLD.TLD без public-suffix списка — мультилейбловый TLD (.co.uk) дал бы «example.co».
     * Для MC-серверов не встречается.
     */
    private static String registrableDomain(String address) {
        // срезаем порт host:port (ровно одно двоеточие); IPv6 (несколько ':') не трогаем — уйдёт как IP
        int colon = address.indexOf(':');
        if (colon > 0 && colon == address.lastIndexOf(':')) {
            address = address.substring(0, colon);
        }
        // убираем крайние точки
        int start = 0, end = address.length();
        while (start < end && address.charAt(start) == '.') start++;
        while (end > start && address.charAt(end - 1) == '.') end--;
        address = address.substring(start, end);
        if (address.isEmpty()) return address;
        if (isIpLiteral(address)) return address; // IP — домен не извлечь

        String[] labels = address.split("\\.");
        if (labels.length < 2) return address;    // одиночный хост без точки (localhost и т.п.)
        return labels[labels.length - 2] + "." + labels[labels.length - 1]; // SLD.TLD
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) return true; // IPv6
        boolean anyDigit = false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') continue;
            if (c < '0' || c > '9') return false;
            anyDigit = true;
        }
        return anyDigit; // IPv4 — только цифры и точки
    }

    private long resolveMarkerLifetimeMs() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return 60000L;
        PartyModule module = instance.getModuleManager().getModule(PartyModule.class);
        if (module == null) return 60000L;
        return Math.max(0L, module.getMarkerLifetimeMs());
    }

    private String buildMarkerKey(UUID ownerUuid, String owner, double x, double y, double z, String dimension) {
        String ownerKey = ownerUuid != null ? ownerUuid.toString() : (owner == null ? "" : owner.trim().toLowerCase(Locale.ROOT));
        String dimKey = dimension == null ? "" : dimension;
        return ownerKey + "|" + dimKey + "|"
                + Double.doubleToLongBits(x) + ":"
                + Double.doubleToLongBits(y) + ":"
                + Double.doubleToLongBits(z);
    }

    /** Синхронизированный питомец другого игрока (пришёл в блоке cosmetics.pet из /sync). */
    public static final class RemotePetInfo {
        public final String skin;
        public final float radius;
        public final float height;
        public final float speed;
        public final float scale;

        private RemotePetInfo(String skin, float radius, float height, float speed, float scale) {
            this.skin = skin;
            this.radius = radius;
            this.height = height;
            this.speed = speed;
            this.scale = scale;
        }

        static RemotePetInfo fromJson(JsonElement element) {
            if (element == null || !element.isJsonObject()) return null;
            JsonObject obj = element.getAsJsonObject();
            String skin = obj.has("skin") ? obj.get("skin").getAsString() : "";
            if (skin == null || skin.isBlank()) return null;
            return new RemotePetInfo(
                    skin.trim().toUpperCase(Locale.ROOT),
                    readClampedFloat(obj, "radius", 0.65f, 0.15f, 2.5f),
                    readClampedFloat(obj, "height", 0.95f, -0.5f, 3.0f),
                    readClampedFloat(obj, "speed", 1.15f, 0.05f, 5.0f),
                    readClampedFloat(obj, "scale", 0.55f, 0.15f, 2.0f)
            );
        }

        private static float readClampedFloat(JsonObject obj, String key, float fallback, float min, float max) {
            float value = fallback;
            if (obj.has(key)) {
                try {
                    value = obj.get(key).getAsFloat();
                } catch (Exception ignored) {
                }
            }
            return Math.max(min, Math.min(max, value));
        }
    }

    /** Синхронизированный нимб другого игрока (блок cosmetics.halo из /sync). */
    public static final class RemoteHaloInfo {
        public final int color;
        public final float radius;
        public final float thickness;
        public final float yOffset;
        public final boolean animate;

        private RemoteHaloInfo(int color, float radius, float thickness, float yOffset, boolean animate) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
            this.yOffset = yOffset;
            this.animate = animate;
        }

        static RemoteHaloInfo fromJson(JsonElement element) {
            if (element == null || !element.isJsonObject()) return null;
            JsonObject obj = element.getAsJsonObject();
            int color = 0xFFFFFFFF;
            if (obj.has("color")) {
                try {
                    color = obj.get("color").getAsInt();
                } catch (Exception ignored) {
                }
            }
            boolean animate = true;
            if (obj.has("animate")) {
                try {
                    animate = obj.get("animate").getAsBoolean();
                } catch (Exception ignored) {
                }
            }
            return new RemoteHaloInfo(
                    color,
                    RemotePetInfo.readClampedFloat(obj, "radius", 0.36f, 0.18f, 0.90f),
                    RemotePetInfo.readClampedFloat(obj, "thickness", 0.06f, 0.02f, 0.16f),
                    RemotePetInfo.readClampedFloat(obj, "y_offset", 0.10f, -0.60f, 0.60f),
                    animate
            );
        }
    }

    private static final class PartyMarker {
        final String owner;
        final UUID ownerUuid;
        final double x;
        final double y;
        final double z;
        final String dimension;
        final long createdAt;

        private PartyMarker(String owner, UUID ownerUuid, double x, double y, double z, String dimension, long createdAt) {
            this.owner = owner == null ? "" : owner;
            this.ownerUuid = ownerUuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension == null ? "" : dimension;
            this.createdAt = createdAt;
        }
    }
}
