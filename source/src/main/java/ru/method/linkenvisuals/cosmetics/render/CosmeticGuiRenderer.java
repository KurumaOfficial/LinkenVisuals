package ru.method.linkenvisuals.cosmetics.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.cosmetics.bedrock.BedrockModel;
import ru.method.linkenvisuals.cosmetics.bedrock.CosmeticModelRepository;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoBuiltBone;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoBuiltCube;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoQuad;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.model.CosmeticEffectType;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsReferenceSpec;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTabIcons;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTokens;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

public final class CosmeticGuiRenderer {
    private static final int CAPE_TEXTURE_WIDTH = 64;
    private static final int CAPE_TEXTURE_HEIGHT = 32;

    private CosmeticGuiRenderer() {
    }

    public static void renderGridIcon(
            DrawContext context,
            int x,
            int y,
            int size,
            CosmeticDefinition definition,
            CosmeticSlot slot,
            int tintArgb
    ) {
        if (definition == null || definition.isNone()) {
            return;
        }

        if (slot != CosmeticSlot.HAT && slot != CosmeticSlot.WINGS && slot != CosmeticSlot.EFFECT) {
            tintArgb = (tintArgb & 0xFF000000) | 0x00FFFFFF;
        }

        if (slot == CosmeticSlot.CHARACTER) {
            ru.method.linkenvisuals.cosmetics.character.RudyCharacterIcon.render(context, x, y, size);
            return;
        }
        if (slot == CosmeticSlot.CAPE) {
            Identifier texture = resolveIconTexture(definition);
            if (texture != null) {
                renderCapeIcon(context, x, y, size, texture, tintArgb);
            }
            return;
        }

        // Эффекты рисуем программно (кольцо/сферы/комета) — без текстур, всегда чисто и тематически.
        if (slot == CosmeticSlot.EFFECT) {
            drawEffectIcon(context, x, y, size, definition.effectType());
            return;
        }

        if (definition.hasModel()) {
            // Real 3D preview: the model is projected on the CPU into the deferred GUI, because
            // the 1.21.11 immediate entity pass behind the cards is invisible.
            BedrockModel model = CosmeticModelRepository.get(definition.modelAsset());
            if (model != null && !model.isEmpty() && model.texture() != null) {
                CosmeticModelIconRenderer.render(context, x, y, size, model, slot, tintArgb);
                return;
            }
        }

        Identifier texture = resolveIconTexture(definition);
        if (texture == null) {
            return;
        }
        renderFlatIcon(context, x, y, size, texture, tintArgb, 64, 64);
    }

    public static void renderFlatIcon(
            DrawContext context,
            int x,
            int y,
            int size,
            Identifier texture,
            int tintArgb,
            int textureWidth,
            int textureHeight
    ) {
        renderScaledCentered(context, x, y, size, texture, tintArgb, textureWidth, textureHeight);
    }

    /** Иконка эффекта в карточке GUI — процедурная, как в HTML prototype. */
    public static void renderCosmeticsEffectCardIcon(DrawContext context, int x, int y, int size,
                                                     CosmeticEffectType type, boolean none, boolean selected) {
        if (none || type == null) {
            CosmeticsTabIcons.drawNoneSlash(context, x + size / 2f, y + size / 2f, size * 0.76f,
                    CosmeticsReferenceSpec.TEXT_MUTED);
            return;
        }
        Color col = selected ? Color.WHITE : CosmeticsReferenceSpec.TEXT_SECONDARY;
        drawEffectIcon(context, x, y, size, type, col);
    }

    /** @deprecated use {@link #renderCosmeticsEffectCardIcon(DrawContext, int, int, int, CosmeticEffectType, boolean, boolean)} */
    @Deprecated
    public static void renderCosmeticsEffectCardIcon(DrawContext context, int x, int y, int size,
                                                     CosmeticEffectType type, boolean none) {
        renderCosmeticsEffectCardIcon(context, x, y, size, type, none, false);
    }

    /** Программная иконка эффекта (нимб=кольцо, сферы=орбита с точками, шлейф=затухающая комета). */
    public static void drawEffectIcon(DrawContext context, int x, int y, int size, CosmeticEffectType type) {
        drawEffectIcon(context, x, y, size, type, ColorUtils.getPrimaryColor());
    }

    public static void drawEffectIcon(DrawContext context, int x, int y, int size, CosmeticEffectType type, Color col) {
        if (type == null) {
            return;
        }
        float cx = x + size / 2f;
        float cy = y + size / 2f;
        float r = size * 0.30f;
        switch (type) {
            case HALO -> Render2D.drawRing(context, cx, cy - size * 0.04f, r, Math.max(1.8f, size * 0.08f), col);
            case SPHERES -> {
                Render2D.drawRing(context, cx, cy, r, Math.max(1.2f, size * 0.035f), CosmeticsTokens.alpha(col, 0.55f));
                float dot = size * 0.09f;
                for (int i = 0; i < 3; i++) {
                    double a = i * (Math.PI * 2.0 / 3.0) - Math.PI / 2.0;
                    float sx = cx + (float) Math.cos(a) * r;
                    float sy = cy + (float) Math.sin(a) * r;
                    Render2D.drawCircle(context, sx, sy, dot * 0.5f, col);
                }
            }
            case TRAIL -> {
                float seg = r * 0.85f;
                for (int i = 0; i < 4; i++) {
                    float a = 0.25f + i * 0.25f;
                    float ox = cx - seg + i * (seg * 0.65f);
                    float oy = cy + (float) Math.sin(i * 0.9f) * (size * 0.08f);
                    Render2D.drawCircle(context, ox, oy, size * (0.05f + i * 0.02f), CosmeticsTokens.alpha(col, a));
                }
            }
        }
    }

    public static void renderScaledCentered(
            DrawContext context,
            int x,
            int y,
            int size,
            Identifier texture,
            int tintArgb,
            int textureWidth,
            int textureHeight
    ) {
        float inset = size * 0.12f;
        int drawSize = Math.max(1, Math.round(size - inset * 2f));
        int drawX = Math.round(x + (size - drawSize) * 0.5f);
        int drawY = Math.round(y + (size - drawSize) * 0.5f);
        Render2D.drawTexture(context, texture, drawX, drawY, drawSize, drawSize);
    }

    public static void renderCapeIcon(
            DrawContext context,
            int x,
            int y,
            int size,
            Identifier texture,
            int tintArgb
    ) {
        float centerX = x + size * 0.5f;
        float centerY = y + size * 0.56f;
        float capeHeight = size * 0.78f;
        float topWidth = size * 0.34f;
        float bottomWidth = size * 0.72f;

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY - capeHeight * 0.42f);
        float collarW = topWidth * 1.15f;
        float collarH = capeHeight * 0.14f;
        drawCapeUvRect(
                context,
                -collarW * 0.5f,
                0f,
                collarW,
                collarH,
                texture,
                1f,
                0f,
                10f,
                4f
        );
        matrices.popMatrix();

        drawDrapedCapePanel(context, centerX - bottomWidth * 0.5f, centerY - capeHeight * 0.28f, bottomWidth * 0.5f, capeHeight, texture, true);
        drawDrapedCapePanel(context, centerX, centerY - capeHeight * 0.28f, bottomWidth * 0.5f, capeHeight, texture, false);
    }

    private static void drawDrapedCapePanel(
            DrawContext context,
            float anchorX,
            float anchorY,
            float panelWidth,
            float panelHeight,
            Identifier texture,
            boolean left
    ) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(anchorX, anchorY);
        float tilt = left ? -11f : 11f;
        matrices.rotate((float) Math.toRadians(tilt));

        float u = left ? 1f : 11f;
        drawCapeUvRect(context, 0f, 0f, panelWidth, panelHeight, texture, u, 1f, 10f, 16f);
        matrices.popMatrix();
    }

    private static void drawCapeUvRect(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            Identifier texture,
            float u,
            float v,
            float regionW,
            float regionH
    ) {
        float u0 = u / CAPE_TEXTURE_WIDTH;
        float v0 = v / CAPE_TEXTURE_HEIGHT;
        float u1 = (u + regionW) / CAPE_TEXTURE_WIDTH;
        float v1 = (v + regionH) / CAPE_TEXTURE_HEIGHT;
        Render2D.drawTexture(context, x, y, width, height, 0f, u0, v0, u1, v1, texture, Color.WHITE);
    }

    public static int applyAlpha(int argb, float alpha) {
        int a = MathHelper.clamp(Math.round(((argb >> 24) & 0xFF) * alpha), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static void renderFlatIconUv(DrawContext context, int x, int y, int size, Identifier texture,
                                         int tintArgb, float u0, float v0, float u1, float v1) {
        float inset = size * 0.10f;
        int drawSize = Math.max(1, Math.round(size - inset * 2f));
        int drawX = Math.round(x + (size - drawSize) * 0.5f);
        int drawY = Math.round(y + (size - drawSize) * 0.5f);
        Render2D.drawTexture(context, drawX, drawY, drawSize, drawSize, 0f,
                u0, v0, u1, v1, texture, new Color(tintArgb, true));
    }

    private static float[] modelUvBounds(BedrockModel model) {
        float[] bounds = {1f, 1f, 0f, 0f};
        for (GeckoBuiltBone root : model.geckoRoots()) collectUv(root, bounds);
        if (bounds[2] <= bounds[0] || bounds[3] <= bounds[1]) {
            return new float[]{0f, 0f, 1f, 1f};
        }
        float padU = 0.015f, padV = 0.015f;
        bounds[0] = Math.max(0f, bounds[0] - padU);
        bounds[1] = Math.max(0f, bounds[1] - padV);
        bounds[2] = Math.min(1f, bounds[2] + padU);
        bounds[3] = Math.min(1f, bounds[3] + padV);
        return bounds;
    }

    private static void collectUv(GeckoBuiltBone bone, float[] out) {
        for (GeckoBuiltCube cube : bone.cubes) {
            for (GeckoQuad quad : cube.quads) {
                for (var vertex : quad.vertices) {
                    out[0] = Math.min(out[0], vertex.u);
                    out[1] = Math.min(out[1], vertex.v);
                    out[2] = Math.max(out[2], vertex.u);
                    out[3] = Math.max(out[3], vertex.v);
                }
            }
        }
        for (GeckoBuiltBone child : bone.children) collectUv(child, out);
    }

    private static Identifier resolveIconTexture(CosmeticDefinition definition) {
        if (definition.icon() != null) {
            return definition.icon();
        }
        return definition.texture();
    }
}
