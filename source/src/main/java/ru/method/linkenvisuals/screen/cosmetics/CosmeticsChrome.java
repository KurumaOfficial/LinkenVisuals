package ru.method.linkenvisuals.screen.cosmetics;

import lombok.experimental.UtilityClass;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

/** Glassmorphism chrome — blur только на fullscreen backdrop (screen space). */
@UtilityClass
public final class CosmeticsChrome {
    private static final float OUTLINE = 0.75f;
    private static final Color BORDER_IDLE = new Color(255, 255, 255, 8);
    private static final Color BORDER_PANEL = new Color(119, 82, 214, 14);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    /** Frosted backdrop — вызывать до canvas transform, в screen px. */
    public void drawBackdrop(Matrix3x2fStack stack, int screenW, int screenH, float fade) {
        if (fade <= 0.001f) {
            return;
        }
        if (ru.method.linkenvisuals.utils.render.ColorUtils.isBlurEnabled()) {
            int a = Math.round(150f * fade);
            int tint = (a << 24) | (8 << 16) | (6 << 8) | 16;
            Render2D.drawRoundedRect(stack, 0, 0, screenW, screenH, 0f, new Color(tint, true));
        }
        Render2D.drawRoundedRect(stack, 0, 0, screenW, screenH, 0f,
                new Color(8, 6, 16, Math.round(55f * fade)));
        Render2D.drawVerticalGradient(stack, 0, 0, screenW, screenH,
                new Color(8, 6, 16, Math.round(25f * fade)),
                new Color(8, 6, 16, Math.round(120f * fade)));
    }

    public void drawModal(Matrix3x2fStack stack, float x, float y, float w, float h, float radius) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        drawModalBoxShadow(stack, x, y, w, h, radius, fade);
        drawModalGradientRing(stack, x, y, w, h, radius, fade);

        WindowGuiRender.drawRoundedRect(stack, x, y, w, h, radius,
                CosmeticsTokens.alpha(new Color(26, 22, 37, 210), fade));
        drawModalCornerWashes(stack, x, y, w, h, fade);
        WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, OUTLINE,
                CosmeticsTokens.alpha(new Color(119, 82, 214, 38), fade));
    }

    public void drawColumnPanel(Matrix3x2fStack stack, float x, float y, float w, float h, float radius) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        WindowGuiRender.drawRoundedRect(stack, x, y, w, h, radius,
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.BG_PANEL, fade));
        WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, OUTLINE,
                CosmeticsTokens.alpha(BORDER_PANEL, fade));
        Render2D.drawHorizontalGradient(stack, x + 1f, y + 1f, x + w - 1f, y + 2f,
                new Color(255, 255, 255, Math.round(8f * fade)),
                new Color(255, 255, 255, Math.round(8f * fade)));
    }

    public void drawViewport(Matrix3x2fStack stack, float x, float y, float w, float h, float radius) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float insetR = radius * 0.55f;
        WindowGuiRender.drawRoundedRect(stack, x, y, w, h, insetR,
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.BG_VIEWPORT, fade));
        WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, insetR, OUTLINE,
                CosmeticsTokens.alpha(BORDER_IDLE, fade));
        Render2D.drawVerticalGradient(stack, x + 1f, y + 1f, x + w - 1f, y + 12f,
                new Color(0, 0, 0, Math.round(115f * fade)), TRANSPARENT);
        Render2D.drawVerticalGradient(stack, x + 1f, y + h - 14f, x + w - 1f, y + h - 1f,
                TRANSPARENT, new Color(0, 0, 0, Math.round(115f * fade)));
    }

    public void drawPreviewStageFrame(Matrix3x2fStack stack, float x, float y, float w, float h) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float r = CosmeticsTokens.INSET_RADIUS * 0.45f;
        WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, r, OUTLINE,
                CosmeticsTokens.alpha(new Color(255, 255, 255, 8), fade));
    }

    public void drawPreviewStage(Matrix3x2fStack stack, float x, float y, float w, float h) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float floorY = y + h * 0.82f;
        float floorW = w * 0.48f;
        float floorH = h * 0.14f;
        float floorX = x + (w - floorW) * 0.5f;

        WindowGuiRender.drawRoundedRect(stack, floorX, floorY - floorH * 0.35f, floorW, floorH, floorH * 0.5f,
                CosmeticsTokens.alpha(new Color(119, 82, 214, 31), fade));
        WindowGuiRender.drawRoundedRect(stack, floorX + floorW * 0.04f, floorY - floorH * 0.28f,
                floorW * 0.92f, floorH * 0.85f, floorH * 0.42f,
                CosmeticsTokens.alpha(new Color(255, 255, 255, 13), fade));

        Render2D.drawVerticalGradient(stack, x + 1f, y + 1f, x + w - 1f, y + h * 0.30f,
                new Color(0, 0, 0, Math.round(97f * fade)), TRANSPARENT);
        Render2D.drawVerticalGradient(stack, x + 1f, y + h * 0.50f, x + w - 1f, y + h - 1f,
                TRANSPARENT, new Color(0, 0, 0, Math.round(128f * fade)));
    }

    public void drawBadgeBar(Matrix3x2fStack stack, float x, float y, float w, float h) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float radius = CosmeticsTokens.INSET_RADIUS * 0.55f;
        WindowGuiRender.drawRoundedRect(stack, x, y, w, h, radius,
                CosmeticsTokens.alpha(new Color(8, 6, 18, 184), fade));
        WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, OUTLINE,
                CosmeticsTokens.alpha(BORDER_IDLE, fade));
    }

    public void drawInnerInset(Matrix3x2fStack stack, float x, float y, float w, float h, float radius) {
        drawViewport(stack, x, y, w, h, radius);
    }

    public void drawHeaderIcon(Matrix3x2fStack stack, float x, float y, float size) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float radius = size * 0.5f;
        WindowGuiRender.drawRoundedDiagonalGradientRect(stack, x, y, size, size, radius,
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.CHIP_GRADIENT_TOP, fade),
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.CHIP_GRADIENT_BOTTOM, fade));
        WindowGuiRender.drawRoundedOutlineRect(stack, x - 0.5f, y - 0.5f, size + 1f, size + 1f, radius,
                OUTLINE, CosmeticsTokens.alpha(new Color(98, 70, 173, 82), fade));
        CosmeticsTabIcons.drawDiamondStar(stack, x + size * 0.5f, y + size * 0.5f, size * 0.40f,
                CosmeticsTokens.alpha(Color.WHITE, fade));
    }

    public void drawCloseButton(Matrix3x2fStack stack, float x, float y, float size, boolean hovered) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float radius = size * 0.5f;
        Color fill = hovered
                ? CosmeticsTokens.alpha(new Color(119, 82, 214, 56), fade)
                : CosmeticsTokens.alpha(new Color(14, 11, 24, 160), fade);
        WindowGuiRender.drawRoundedRect(stack, x, y, size, size, radius, fill);
        if (hovered) {
            WindowGuiRender.drawRoundedOutlineRect(stack, x, y, size, size, radius, OUTLINE,
                    CosmeticsTokens.alpha(new Color(119, 82, 214, 60), fade));
        }
        CosmeticsTabIcons.drawClose(stack, x + size * 0.5f, y + size * 0.5f, size * 0.48f,
                CosmeticsTokens.alpha(new Color(255, 255, 255, 217), fade));
    }

    /** Active category — pill + purple→blue horizontal gradient. */
    public void drawActiveTabPill(Matrix3x2fStack stack, float x, float y, float w, float h) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float radius = h * 0.5f;
        WindowGuiRender.drawRoundedHorLinearGradientRect(stack, x, y, w, h, radius,
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_TAB_TOP, fade),
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_TAB_END, fade));
        WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, OUTLINE,
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_TAB_BORDER, fade));
        Render2D.drawVerticalGradient(stack, x + 1f, y + 1f, x + w - 1f, y + Math.min(14f, h * 0.22f),
                new Color(255, 255, 255, Math.round(26f * fade)), TRANSPARENT);
        WindowGuiRender.drawRoundedOutlineRect(stack, x + 1f, y + 1f, w - 2f, h - 2f, radius - 1f, OUTLINE,
                CosmeticsTokens.alpha(new Color(188, 146, 255, 31), fade));
    }

    public void drawCard(Matrix3x2fStack stack, float x, float y, float w, float h, float radius,
                         boolean selected, boolean hovered) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        Color fill = selected
                ? CosmeticsTokens.alpha(CosmeticsReferenceSpec.BG_CARD_SELECTED, fade)
                : CosmeticsTokens.alpha(CosmeticsReferenceSpec.BG_CARD, fade);
        WindowGuiRender.drawRoundedRect(stack, x, y, w, h, radius, fill);
        drawCardCornerSheen(stack, x, y, w, h, selected, fade);
        drawCardInsetSheen(stack, x, y, w, h, radius, fade);

        if (selected) {
            WindowGuiRender.drawRoundedOutlineRect(stack, x - 4f, y - 4f, w + 8f, h + 8f, radius + 4f, 1f,
                    WindowGuiColors.withAlpha(CosmeticsReferenceSpec.PURPLE, 0.28f * fade));
            WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, 1.5f,
                    CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE, fade));
        } else {
            WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, OUTLINE,
                    CosmeticsTokens.alpha(BORDER_IDLE, fade));
            if (hovered) {
                WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, OUTLINE,
                        CosmeticsTokens.alpha(new Color(119, 82, 214, 41), fade));
            }
        }
    }

    public void drawIconWell(Matrix3x2fStack stack, float x, float y, float size, float radius) {
        drawIconWell(stack, x, y, size, radius, false);
    }

    public void drawIconWell(Matrix3x2fStack stack, float x, float y, float size, float radius, boolean selected) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float r = radius * 0.55f;
        WindowGuiRender.drawRoundedRect(stack, x, y, size, size, r,
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.BG_ICON_WELL, fade));
        Color border = selected
                ? CosmeticsTokens.alpha(new Color(119, 82, 214, 36), fade)
                : CosmeticsTokens.alpha(new Color(255, 255, 255, 10), fade);
        WindowGuiRender.drawRoundedOutlineRect(stack, x, y, size, size, r, OUTLINE, border);
        Render2D.drawVerticalGradient(stack, x + 2f, y + size * 0.55f, x + size - 2f, y + size - 2f,
                TRANSPARENT, new Color(0, 0, 0, Math.round(102f * fade)));
    }

    public void drawToggle(Matrix3x2fStack stack, float x, float y, float w, float h, boolean on) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float radius = h * 0.5f;
        if (on) {
            WindowGuiRender.drawRoundedHorLinearGradientRect(stack, x, y, w, h, radius,
                    CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE, fade),
                    CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_TAB_END, fade));
            WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, OUTLINE,
                    CosmeticsTokens.alpha(new Color(255, 255, 255, 26), fade));
        } else {
            WindowGuiRender.drawRoundedVerLinearGradientRect(stack, x, y, w, h, radius,
                    CosmeticsTokens.alpha(new Color(62, 56, 82, 245), fade),
                    CosmeticsTokens.alpha(new Color(42, 37, 56, 245), fade));
            WindowGuiRender.drawRoundedOutlineRect(stack, x, y, w, h, radius, OUTLINE,
                    CosmeticsTokens.alpha(new Color(255, 255, 255, 15), fade));
        }
        float knob = h - 4f;
        float knobX = on ? x + w - knob - 2f : x + 2f;
        WindowGuiRender.drawRoundedVerLinearGradientRect(stack, knobX, y + 2f, knob, knob, knob * 0.5f,
                CosmeticsTokens.alpha(Color.WHITE, fade),
                CosmeticsTokens.alpha(new Color(229, 231, 235), fade));
    }

    public void drawAccentChip(Matrix3x2fStack stack, float x, float y, float w, float h, float radius) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        WindowGuiRender.drawRoundedRect(stack, x, y, w, h, radius,
                CosmeticsTokens.alpha(new Color(119, 82, 214, 46), fade));
    }

    private void drawCardInsetSheen(Matrix3x2fStack stack, float x, float y, float w, float h, float radius, float fade) {
        Render2D.drawVerticalGradient(stack, x + 1f, y + 1f, x + w - 1f, y + 3f,
                new Color(255, 255, 255, Math.round(10f * fade)), TRANSPARENT);
        WindowGuiRender.drawRoundedOutlineRect(stack, x + 1f, y + 1f, w - 2f, h - 2f, radius - 1f, OUTLINE,
                CosmeticsTokens.alpha(new Color(119, 82, 214, 10), fade));
    }

    private void drawModalGradientRing(Matrix3x2fStack stack, float x, float y, float w, float h, float radius, float fade) {
        if (fade <= 0.01f) {
            return;
        }
        WindowGuiRender.drawRoundedOutlineRect(stack, x - 1f, y - 1f, w + 2f, h + 2f,
                radius + 1f, 1f, CosmeticsTokens.alpha(new Color(119, 82, 214, 71), fade * 0.55f));
    }

    private void drawModalBoxShadow(Matrix3x2fStack stack, float x, float y, float w, float h, float radius, float fade) {
        if (fade <= 0.01f) {
            return;
        }
        int layers = 5;
        float spread = 14f;
        float peak = 0.12f * fade;
        for (int i = layers; i >= 1; i--) {
            float f = i / (float) layers;
            float e = spread * f;
            float a = peak * (1f - f) * (1f - f);
            int alpha = Math.round(a * 255f);
            if (alpha <= 0) {
                continue;
            }
            WindowGuiRender.drawRoundedRect(stack, x - e, y - e + 3f, w + e * 2f, h + e * 2f, radius + e,
                    new Color(0, 0, 0, alpha));
        }
    }

    private void drawModalCornerWashes(Matrix3x2fStack stack, float x, float y, float w, float h, float fade) {
        float extent = Math.min(280f, Math.min(w, h) * 0.5f);
        washCorner(stack, x, y, extent, fade * 0.9f);
        washCorner(stack, x + w - extent, y, extent, fade * 0.7f);
    }

    private void washCorner(Matrix3x2fStack stack, float cx, float cy, float extent, float fade) {
        if (fade <= 0.01f) {
            return;
        }
        Color c = new Color(119, 82, 214, Math.round(18f * fade));
        Render2D.drawHorizontalGradient(stack, cx, cy, cx + extent, cy + 1f, c, TRANSPARENT);
        Render2D.drawVerticalGradient(stack, cx, cy, cx + 1f, cy + extent, c, TRANSPARENT);
    }

    private void drawCardCornerSheen(Matrix3x2fStack stack, float x, float y, float w, float h,
                                     boolean selected, float fade) {
        Color c1 = selected
                ? new Color(188, 146, 255, Math.round(36f * fade))
                : new Color(119, 82, 214, Math.round(15f * fade));
        Color c2 = selected
                ? new Color(119, 82, 214, Math.round(26f * fade))
                : new Color(104, 77, 180, Math.round(13f * fade));
        Render2D.drawHorizontalGradient(stack, x + 2f, y + 2f, x + w * 0.45f, y + h * 0.35f, c1, TRANSPARENT);
        Render2D.drawHorizontalGradient(stack, x + w * 0.55f, y + h * 0.65f, x + w - 2f, y + h - 2f,
                TRANSPARENT, c2);
    }
}
