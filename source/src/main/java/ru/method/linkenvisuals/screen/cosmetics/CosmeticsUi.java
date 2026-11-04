package ru.method.linkenvisuals.screen.cosmetics;

import lombok.experimental.UtilityClass;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.Locale;

@UtilityClass
public final class CosmeticsUi {

    public enum SectionIcon {
        LAYERS,
        EYE,
        CAPE
    }

    public void drawTitle(Matrix3x2fStack stack, float x, float y, String title, float fade) {
        float size = CosmeticsReferenceSpec.FONT_TITLE;
        Color lavender = CosmeticsTokens.alpha(CosmeticsReferenceSpec.TEXT_TITLE, fade);
        Color glow = CosmeticsTokens.alpha(new Color(196, 154, 255, 72), fade);
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, title, x, y + 1f, glow, size);
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, title, x, y, lavender, size);
    }

    public void drawSectionHeader(Matrix3x2fStack stack, CosmeticsLayout.Layout layout, SectionIcon icon,
                                  String title, float x, float y, float chipSize) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float cx = x + chipSize * 0.5f;
        float cy = y + chipSize * 0.5f;
        Color accent = CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_SECTION, fade);

        switch (icon) {
            case LAYERS -> CosmeticsTabIcons.drawLayers(stack, cx, cy, chipSize * 0.88f, accent);
            case EYE -> CosmeticsTabIcons.drawEye(stack, cx, cy, chipSize * 0.88f, accent);
            case CAPE -> CosmeticsTabIcons.drawCape(stack, cx, cy, chipSize * 0.88f, accent);
        }

        String upper = title.toUpperCase(Locale.ROOT);
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, upper, x + chipSize + 8f, y + 1f,
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_SECTION, fade),
                CosmeticsReferenceSpec.FONT_SECTION);
    }

    public void drawCategoryTab(Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                                float x, float y, float w, CosmeticsTab tab,
                                boolean active, boolean hovered) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float h = layout.tabH;

        if (active) {
            CosmeticsChrome.drawActiveTabPill(stack, x, y, w, h);
        }
        // Inactive: без фоновой box — только icon + label (mockup spec).

        float iconSize = CosmeticsReferenceSpec.TAB_ICON;
        float iconCx = x + layout.tabInset + CosmeticsReferenceSpec.TAB_ICON_BOX * 0.5f;
        float iconCy = y + h * 0.5f;
        Color iconColor = active ? Color.WHITE
                : CosmeticsTokens.alpha(CosmeticsReferenceSpec.TEXT_SECONDARY, fade * (hovered ? 1f : 0.75f));
        drawTabIcon(stack, tab, iconCx, iconCy, iconSize, iconColor);

        float labelX = x + layout.tabInset + CosmeticsReferenceSpec.TAB_ICON_BOX + 14f;
        Color textColor = active ? Color.WHITE
                : CosmeticsTokens.alpha(hovered ? new Color(0xB8BCC8) : CosmeticsReferenceSpec.TEXT_SECONDARY,
                fade * (hovered ? 1f : 0.75f));
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, tab.label(), labelX,
                y + (h - CosmeticsReferenceSpec.FONT_TAB) * 0.5f, textColor, CosmeticsReferenceSpec.FONT_TAB);

        if (active) {
            float mark = CosmeticsReferenceSpec.TAB_CHECK_SIZE;
            float markX = x + w - mark - layout.tabInset;
            float markY = y + (h - mark) * 0.5f;
            float cx = markX + mark * 0.5f;
            WindowGuiRender.drawRoundedRect(stack, markX, markY, mark, mark, mark * 0.5f,
                    CosmeticsTokens.alpha(new Color(98, 70, 173, 140), fade));
            WindowGuiRender.drawRoundedOutlineRect(stack, markX, markY, mark, mark, mark * 0.5f, 1f,
                    CosmeticsTokens.alpha(new Color(255, 255, 255, 140), fade));
            Render2D.drawVerticalGradient(stack, markX + 1f, markY + 1f, markX + mark - 1f, markY + 3f,
                    new Color(0, 0, 0, Math.round(64f * fade)), new Color(0, 0, 0, 0));
            WindowGuiRender.drawCenteredText(stack, Fonts.ROUNDBOLD, "\u2713", cx,
                    markY + mark * 0.22f, CosmeticsTokens.alpha(Color.WHITE, fade), mark * 0.52f);
        }
    }

    private void drawTabIcon(Matrix3x2fStack stack, CosmeticsTab tab, float cx, float cy, float size, Color color) {
        switch (tab.iconKind()) {
            case TEXTURE -> {
                if (tab.iconTexture() != null) {
                    float half = size * 0.5f;
                    Render2D.drawTexture(stack, cx - half, cy - half, size, size, 4f, tab.iconTexture(), color);
                }
            }
            case CAPE -> CosmeticsTabIcons.drawCape(stack, cx, cy, size, color);
            case HAT -> CosmeticsTabIcons.drawHat(stack, cx, cy, size, color);
            case WINGS -> CosmeticsTabIcons.drawWings(stack, cx, cy, size, color);
            case SPARKLE -> CosmeticsTabIcons.drawSparkle(stack, cx, cy, size, color);
            case PAW -> CosmeticsTabIcons.drawPaw(stack, cx, cy, size, color);
            case RUN -> CosmeticsTabIcons.drawRun(stack, cx, cy, size, color);
            case WAND -> CosmeticsTabIcons.drawWand(stack, cx, cy, size, color);
        }
    }

    public void drawWardrobeCard(Matrix3x2fStack stack, float x, float y, float w, float h,
                                 float radius, boolean selected, boolean hovered) {
        CosmeticsChrome.drawCard(stack, x, y, w, h, radius, selected, hovered);
    }

    public void drawCardToggle(Matrix3x2fStack stack, float x, float y, float w, float h, boolean on, float scale) {
        float tw = CosmeticsTokens.TOGGLE_W * scale;
        float th = CosmeticsTokens.TOGGLE_H * scale;
        float pad = 14f * scale;
        CosmeticsChrome.drawToggle(stack, x + w - tw - pad, y + h - th - pad, tw, th, on);
    }

    public void drawTabSectionHeader(Matrix3x2fStack stack, CosmeticsLayout.Layout layout, CosmeticsTab tab,
                                     float x, float y, float chipSize) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        float cx = x + chipSize * 0.5f;
        float cy = y + chipSize * 0.5f;
        Color accent = CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_SECTION, fade);
        drawTabIcon(stack, tab, cx, cy, chipSize * 0.88f, accent);

        String upper = tab.label().toUpperCase(Locale.ROOT);
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, upper, x + chipSize + 8f, y + 1f,
                CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_SECTION, fade),
                CosmeticsReferenceSpec.FONT_SECTION);
    }

    public void drawIconWell(Matrix3x2fStack stack, float x, float y, float size, float radius) {
        CosmeticsChrome.drawIconWell(stack, x, y, size, radius, false);
    }

    public void drawIconWell(Matrix3x2fStack stack, float x, float y, float size, float radius, boolean selected) {
        CosmeticsChrome.drawIconWell(stack, x, y, size, radius, selected);
    }

    public void drawPlayerBadgeOverlay(Matrix3x2fStack stack, float x, float y, float w, float barH,
                                       Identifier skin, String name, String meta, CosmeticsLayout.Layout layout) {
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        CosmeticsChrome.drawBadgeBar(stack, x, y, w, barH);

        float head = 22f;
        float headX = x + 10f;
        float headY = y + (barH - head) * 0.5f;
        if (skin != null) {
            Render2D.drawTextureNearest(stack, headX, headY, head, head, 0f,
                    0.125f, 0.125f, 0.25f, 0.25f, skin, Color.WHITE);
        }

        float nameSize = CosmeticsReferenceSpec.FONT_BADGE_NAME;
        float metaSize = CosmeticsReferenceSpec.FONT_BADGE_TIME;
        float textX = headX + head + 6f;
        float nameY = y + 7f;
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, name, textX, nameY,
                CosmeticsTokens.alpha(CosmeticsTokens.TEXT, fade), nameSize);

        float chip = 14f;
        float chipX = textX + Fonts.MEDIUM.getWidth(name, nameSize) + 5f;
        float chipY = nameY + (nameSize - chip) * 0.5f;
        CosmeticsChrome.drawAccentChip(stack, chipX, chipY, chip, chip, 3f);
        CosmeticsTabIcons.drawDiamondStar(stack, chipX + chip * 0.5f, chipY + chip * 0.5f,
                chip * 0.55f, CosmeticsTokens.alpha(CosmeticsReferenceSpec.PURPLE_SECTION, fade));

        if (meta != null && !meta.isBlank()) {
            WindowGuiRender.drawText(stack, Fonts.MEDIUM, meta, textX, nameY + nameSize + 2f,
                    CosmeticsTokens.alpha(CosmeticsTokens.TEXT_MUTED, fade * 0.92f), metaSize);
        }
    }
}
