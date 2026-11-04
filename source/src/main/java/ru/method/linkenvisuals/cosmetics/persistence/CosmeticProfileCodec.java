package ru.method.linkenvisuals.cosmetics.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.method.linkenvisuals.cosmetics.catalog.CosmeticCatalog;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class CosmeticProfileCodec {
    private CosmeticProfileCodec() {
    }

    public static JsonObject toJson(CosmeticProfile profile) {
        JsonObject object = new JsonObject();
        if (profile == null) {
            return object;
        }
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            String id = profile.get(slot);
            if (id != null && !"none".equals(id)) {
                object.addProperty(slotKey(slot), id);
            }
            int tint = profile.getTint(slot);
            if (slot == CosmeticSlot.HAT && tint != CosmeticProfile.DEFAULT_TINT) {
                object.addProperty("hat_color", tint);
            }
            if (slot == CosmeticSlot.WINGS && tint != CosmeticProfile.DEFAULT_TINT) {
                object.addProperty("wings_color", tint);
            }
            if (slot == CosmeticSlot.EFFECT && tint != CosmeticProfile.DEFAULT_TINT) {
                object.addProperty("effect_color", tint);
            }
        }
        return object;
    }

    public static CosmeticProfile fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new CosmeticProfile();
        }
        JsonObject object = element.getAsJsonObject();
        EnumMap<CosmeticSlot, String> map = new EnumMap<>(CosmeticSlot.class);
        EnumMap<CosmeticSlot, Integer> tints = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            tints.put(slot, CosmeticProfile.DEFAULT_TINT);
        }
        if (object.has("hat_color")) {
            try {
                tints.put(CosmeticSlot.HAT, object.get("hat_color").getAsInt());
            } catch (Exception ignored) {
            }
        }
        if (object.has("wings_color")) {
            try {
                tints.put(CosmeticSlot.WINGS, object.get("wings_color").getAsInt());
            } catch (Exception ignored) {
            }
        }
        if (object.has("effect_color")) {
            try {
                tints.put(CosmeticSlot.EFFECT, object.get("effect_color").getAsInt());
            } catch (Exception ignored) {
            }
        }
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            String key = slotKey(slot);
            if (!object.has(key)) {
                continue;
            }
            String raw = object.get(key).getAsString();
            if (CosmeticCatalog.isAllowed(raw, slot)) {
                map.put(slot, raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new CosmeticProfile(map, tints);
    }

    public static Map<CosmeticSlot, String> sanitize(CosmeticProfile profile) {
        EnumMap<CosmeticSlot, String> sanitized = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            String id = profile == null ? "none" : profile.get(slot);
            sanitized.put(slot, CosmeticCatalog.isAllowed(id, slot) ? id : "none");
        }
        return sanitized;
    }

    public static Map<CosmeticSlot, Integer> sanitizeTints(CosmeticProfile profile) {
        EnumMap<CosmeticSlot, Integer> sanitized = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            int tint = profile == null ? CosmeticProfile.DEFAULT_TINT : profile.getTint(slot);
            sanitized.put(slot, tint);
        }
        return sanitized;
    }

    private static String slotKey(CosmeticSlot slot) {
        return switch (slot) {
            case CHARACTER -> "character";
            case CAPE -> "cape";
            case HAT -> "hat";
            case HEAD -> "head";
            case BODY -> "body";
            case SHIELD -> "shield";
            case WEAPON -> "weapon";
            case PET -> "pet_model";
            case WINGS -> "wings";
            case EFFECT -> "effect";
        };
    }
}
