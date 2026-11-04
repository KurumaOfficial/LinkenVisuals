package ru.method.linkenvisuals.cosmetics.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.method.linkenvisuals.cosmetics.linken.LinkenCape;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticsRepository {
    // Плащ linken — надет по умолчанию (свежая установка, нет ключа «cosmetics» в конфиге). Сохранённый
    // профиль из конфига это переопределяет, включая явно снятый плащ.
    private volatile CosmeticProfile localProfile = new CosmeticProfile();
    private final Map<UUID, CosmeticProfile> remoteProfiles = new ConcurrentHashMap<>();
    /**
     * Фолбэк по нику: на офлайн-серверах UUID из мира (серверный v3) может не совпадать
     * с UUID лаунчера другого игрока, тогда профиль находится только по имени.
     */
    private final Map<String, CosmeticProfile> remoteProfilesByName = new ConcurrentHashMap<>();

    public CosmeticProfile getLocalProfile() {
        return localProfile;
    }

    public void setLocalProfile(CosmeticProfile profile) {
        if (profile == null) {
            localProfile = new CosmeticProfile();
            return;
        }
        localProfile = new CosmeticProfile(
                CosmeticProfileCodec.sanitize(profile),
                CosmeticProfileCodec.sanitizeTints(profile)
        );
    }

    public CosmeticProfile getRemoteProfile(UUID uuid) {
        return getRemoteProfile(uuid, null);
    }

    public CosmeticProfile getRemoteProfile(UUID uuid, String name) {
        if (uuid != null) {
            CosmeticProfile profile = remoteProfiles.get(uuid);
            if (profile != null) {
                return profile;
            }
        }
        if (name != null && !name.isBlank()) {
            CosmeticProfile profile = remoteProfilesByName.get(name.trim().toLowerCase(Locale.ROOT));
            if (profile != null) {
                return profile;
            }
        }
        return new CosmeticProfile();
    }

    public void putRemoteProfile(UUID uuid, CosmeticProfile profile) {
        if (uuid == null || profile == null || profile.isEmpty()) {
            if (uuid != null) {
                remoteProfiles.remove(uuid);
            }
            return;
        }
        remoteProfiles.put(uuid, new CosmeticProfile(
                CosmeticProfileCodec.sanitize(profile),
                CosmeticProfileCodec.sanitizeTints(profile)
        ));
    }

    /** Полная замена удалённых профилей свежим снапшотом из /sync (устаревшие исчезают).
     *  put → keySet().retainAll (не clear+put): render-поток читает профили каждый кадр, окно пустоты
     *  между clear() и заполнением давало моргание чужой косметики на 1 кадр. */
    public void replaceRemoteProfiles(Map<UUID, CosmeticProfile> byUuid,
                                      Map<String, CosmeticProfile> byName) {
        if (byUuid != null) {
            for (Map.Entry<UUID, CosmeticProfile> entry : byUuid.entrySet()) {
                putRemoteProfile(entry.getKey(), entry.getValue());
            }
            remoteProfiles.keySet().retainAll(byUuid.keySet());
        } else {
            remoteProfiles.clear();
        }
        if (byName != null) {
            java.util.Set<String> keptNames = new java.util.HashSet<>();
            for (Map.Entry<String, CosmeticProfile> entry : byName.entrySet()) {
                String key = entry.getKey();
                CosmeticProfile profile = entry.getValue();
                if (key == null || key.isBlank() || profile == null || profile.isEmpty()) {
                    continue;
                }
                String normalized = key.trim().toLowerCase(Locale.ROOT);
                remoteProfilesByName.put(normalized, new CosmeticProfile(
                        CosmeticProfileCodec.sanitize(profile),
                        CosmeticProfileCodec.sanitizeTints(profile)
                ));
                keptNames.add(normalized);
            }
            remoteProfilesByName.keySet().retainAll(keptNames);
        } else {
            remoteProfilesByName.clear();
        }
    }

    public void clearRemoteProfiles() {
        remoteProfiles.clear();
        remoteProfilesByName.clear();
    }

    public JsonObject localProfileJson() {
        return CosmeticProfileCodec.toJson(localProfile);
    }

    public void loadLocal(JsonElement element) {
        setLocalProfile(CosmeticProfileCodec.fromJson(element));
    }
}
