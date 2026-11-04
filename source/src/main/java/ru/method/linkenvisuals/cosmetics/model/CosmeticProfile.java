package ru.method.linkenvisuals.cosmetics.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class CosmeticProfile {
    /** Neutral tint preserves every model's authored texture colors. */
    public static final int DEFAULT_TINT = 0xFFFFFFFF;

    private final Map<CosmeticSlot, String> selections;
    private final Map<CosmeticSlot, Integer> tints;

    public CosmeticProfile() {
        this(new EnumMap<>(CosmeticSlot.class), new EnumMap<>(CosmeticSlot.class));
    }

    public CosmeticProfile(Map<CosmeticSlot, String> selections) {
        this(selections, new EnumMap<>(CosmeticSlot.class));
    }

    public CosmeticProfile(Map<CosmeticSlot, String> selections, Map<CosmeticSlot, Integer> tints) {
        this.selections = new EnumMap<>(CosmeticSlot.class);
        this.tints = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            String value = selections == null ? null : selections.get(slot);
            if (slot == CosmeticSlot.CAPE && ("cape_classic".equals(value)
                    || "cape_aurora".equals(value) || "cape_shadow".equals(value))) {
                value = "linken_signature";
            }
            // Плащ ПО УМОЛЧАНИЮ — фирменный «linken_signature» (когда слот не задан). Выключается
            // явным выбором «нет» (сохраняется "none"). Остальные слоты по умолчанию — "none".
            String normalized = (slot == CosmeticSlot.CAPE && (value == null || value.isBlank()))
                    ? "linken_signature" : normalize(value);
            this.selections.put(slot, normalized);
            Integer tint = tints == null ? null : tints.get(slot);
            this.tints.put(slot, tint == null ? DEFAULT_TINT : tint);
        }
    }

    public static CosmeticProfile singleSlot(CosmeticSlot slot, String cosmeticId) {
        EnumMap<CosmeticSlot, String> selections = new EnumMap<>(CosmeticSlot.class);
        EnumMap<CosmeticSlot, Integer> tints = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot value : CosmeticSlot.values()) {
            selections.put(value, "none");
            tints.put(value, DEFAULT_TINT);
        }
        if (slot != null) {
            selections.put(slot, normalize(cosmeticId));
        }
        return new CosmeticProfile(selections, tints);
    }

    public String get(CosmeticSlot slot) {
        return selections.getOrDefault(slot, "none");
    }

    public int getTint(CosmeticSlot slot) {
        return tints.getOrDefault(slot, DEFAULT_TINT);
    }

    public CosmeticProfile with(CosmeticSlot slot, String cosmeticId) {
        EnumMap<CosmeticSlot, String> copy = new EnumMap<>(selections);
        copy.put(slot, normalize(cosmeticId));
        return new CosmeticProfile(copy, tints);
    }

    public CosmeticProfile withTint(CosmeticSlot slot, int argb) {
        EnumMap<CosmeticSlot, Integer> copy = new EnumMap<>(tints);
        copy.put(slot, argb);
        return new CosmeticProfile(selections, copy);
    }

    public Map<CosmeticSlot, String> asMap() {
        return Map.copyOf(selections);
    }

    public Map<CosmeticSlot, Integer> tintMap() {
        return Map.copyOf(tints);
    }

    public boolean isEmpty() {
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!"none".equals(get(slot))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CosmeticProfile other)) {
            return false;
        }
        return Objects.equals(selections, other.selections) && Objects.equals(tints, other.tints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selections, tints);
    }

    private static String normalize(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return "none";
        }
        return cosmeticId.trim().toLowerCase();
    }
}
