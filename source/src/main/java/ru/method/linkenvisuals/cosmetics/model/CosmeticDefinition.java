package ru.method.linkenvisuals.cosmetics.model;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import ru.method.linkenvisuals.LinkenVisuals;

public record CosmeticDefinition(
        String id,
        CosmeticSlot slot,
        String nameKey,
        @Nullable Identifier texture,
        @Nullable Identifier previewTexture,
        @Nullable Identifier modelAsset,
        CosmeticAnchor anchor,
        CosmeticEffectType effectType
) {
    public static CosmeticDefinition none(CosmeticSlot slot) {
        return new CosmeticDefinition("none", slot, "cosmetics.none", null, null, null, CosmeticAnchor.HEAD, CosmeticEffectType.NONE);
    }

    public static CosmeticDefinition cape(String id, String nameKey, String texturePath) {
        Identifier texture = LinkenVisuals.id(texturePath);
        return new CosmeticDefinition(id, CosmeticSlot.CAPE, nameKey, texture, texture, null, CosmeticAnchor.BODY, CosmeticEffectType.NONE);
    }

    public static CosmeticDefinition model(
            String id,
            CosmeticSlot slot,
            String nameKey,
            String previewTexturePath,
            String modelAssetPath,
            CosmeticAnchor anchor
    ) {
        Identifier preview = LinkenVisuals.id(previewTexturePath);
        Identifier modelAsset = LinkenVisuals.id(modelAssetPath);
        return new CosmeticDefinition(id, slot, nameKey, preview, preview, modelAsset, anchor, CosmeticEffectType.NONE);
    }

    /** Эффект-косметика вокруг игрока (нимб/сферы/шлейф): иконка-превью + тип эффекта, без модели. */
    public static CosmeticDefinition effect(String id, String nameKey, String iconPath, CosmeticEffectType effectType) {
        Identifier icon = LinkenVisuals.id(iconPath);
        return new CosmeticDefinition(id, CosmeticSlot.EFFECT, nameKey, icon, icon, null, CosmeticAnchor.BODY, effectType);
    }

    public boolean isNone() {
        return "none".equals(id);
    }

    public boolean hasModel() {
        return modelAsset != null;
    }

    public boolean hasEffect() {
        return effectType != null && effectType != CosmeticEffectType.NONE;
    }

    public Identifier icon() {
        return previewTexture != null ? previewTexture : texture;
    }

    public float offsetX() {
        return 0f;
    }

    public float offsetY() {
        return 0f;
    }

    public float offsetZ() {
        return 0f;
    }

    public float modelScale() {
        return 1f;
    }
}
