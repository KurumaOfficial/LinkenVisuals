package ru.method.linkenvisuals.cosmetics.bedrock;

import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoBuiltBone;
import ru.method.linkenvisuals.cosmetics.model.CosmeticAnchor;

import java.util.List;

public final class BedrockModel {
    private final Identifier texture;
    private final CosmeticAnchor anchor;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;
    private final float scale;
    private final float yaw;
    private final float pitch;
    private final float roll;
    private final float previewY;
    private final float previewScale;
    private final int textureWidth;
    private final int textureHeight;
    private final List<BedrockBone> roots;
    private final List<GeckoBuiltBone> geckoRoots;
    private final MoreCosmeticsAnimationSet animations;

    public BedrockModel(
            Identifier texture,
            CosmeticAnchor anchor,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scale,
            float yaw,
            float pitch,
            float roll,
            float previewY,
            float previewScale,
            int textureWidth,
            int textureHeight,
            List<BedrockBone> roots,
            List<GeckoBuiltBone> geckoRoots,
            MoreCosmeticsAnimationSet animations
    ) {
        this.texture = texture;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scale = scale;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.previewY = previewY;
        this.previewScale = previewScale;
        this.textureWidth = Math.max(1, textureWidth);
        this.textureHeight = Math.max(1, textureHeight);
        this.roots = roots == null ? List.of() : List.copyOf(roots);
        this.geckoRoots = geckoRoots == null ? List.of() : List.copyOf(geckoRoots);
        this.animations = animations == null ? MoreCosmeticsAnimationSet.empty() : animations;
    }

    public Identifier texture() {
        return texture;
    }

    public CosmeticAnchor anchor() {
        return anchor;
    }

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public float offsetZ() {
        return offsetZ;
    }

    public float scale() {
        return scale;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float roll() {
        return roll;
    }

    public float previewY() {
        return previewY;
    }

    public float previewScale() {
        return previewScale;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public List<BedrockBone> roots() {
        return roots;
    }

    public List<GeckoBuiltBone> geckoRoots() {
        return geckoRoots;
    }

    public MoreCosmeticsAnimationSet animations() {
        return animations;
    }

    public boolean isEmpty() {
        return geckoRoots.isEmpty();
    }

    public boolean hasAnimations() {
        return !animations.isEmpty();
    }
}
