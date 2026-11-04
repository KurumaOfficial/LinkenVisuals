package ru.method.linkenvisuals.screen.cosmetics.widget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.cosmetics.model.CosmeticEffectType;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.render.CosmeticGuiRenderer;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticPlayerPreview;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsLayout;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsReferenceSpec;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTabIcons;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTokens;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsUi;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiSettingStyle;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

/** Single item card — icon well, title, description, toggle. */
public final class CosmeticsItemCardWidget {

    public void renderWardrobe(DrawContext context, Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                               float x, float y, float w, float h,
                               CosmeticDefinition item, CosmeticSlot slot, boolean selected,
                               CosmeticPlayerPreview preview, float canvasMx, float canvasMy) {
        boolean hovered = layout.isHovered(x, y, w, h, canvasMx, canvasMy);
        drawChrome(stack, x, y, w, h, layout.cardRadius, selected, hovered);

        float pad = CosmeticsTokens.CARD_PAD;
        float iconX = x + (w - layout.iconWell) * 0.5f;
        float iconY = y + pad;
        CosmeticsUi.drawIconWell(stack, iconX, iconY, layout.iconWell, layout.insetRadius, selected);

        if (item.isNone()) {
            CosmeticsTabIcons.drawNoneSlash(stack, iconX + layout.iconWell * 0.5f, iconY + layout.iconWell * 0.5f,
                    layout.iconWell * 0.72f, CosmeticsTokens.TEXT_MUTED);
        } else {
            int iconSize = Math.round(layout.iconWell * 0.78f);
            preview.renderIcon(context,
                    Math.round(iconX + (layout.iconWell - iconSize) * 0.5f),
                    Math.round(iconY + (layout.iconWell - iconSize) * 0.5f),
                    iconSize, item, slot, 1f);
        }

        drawTextBlock(stack, x, y, w, h, layout, pad, item.nameKey(), selected);
        CosmeticsUi.drawCardToggle(stack, x, y, w, h, selected, 1f);
    }

    public void renderEffect(DrawContext context, Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                             float x, float y, float w, float h,
                             String titleKey, String descKey, CosmeticEffectType effectType,
                             boolean none, boolean on, float canvasMx, float canvasMy) {
        boolean hovered = layout.isHovered(x, y, w, h, canvasMx, canvasMy);
        drawChrome(stack, x, y, w, h, layout.cardRadius, on, hovered);

        float pad = CosmeticsTokens.CARD_PAD;
        float iconX = x + (w - layout.iconWell) * 0.5f;
        float iconY = y + pad;
        CosmeticsUi.drawIconWell(stack, iconX, iconY, layout.iconWell, layout.insetRadius, on);

        int iconSize = Math.round(layout.iconWell * 0.78f);
        CosmeticGuiRenderer.renderCosmeticsEffectCardIcon(context,
                Math.round(iconX + (layout.iconWell - iconSize) * 0.5f),
                Math.round(iconY + (layout.iconWell - iconSize) * 0.5f),
                iconSize, effectType, none, on);

        drawTextBlock(stack, x, y, w, h, layout, pad, titleKey, on);
        CosmeticsUi.drawCardToggle(stack, x, y, w, h, on, 1f);
    }

    private void drawChrome(Matrix3x2fStack stack, float x, float y, float w, float h, float radius,
                            boolean selected, boolean hovered) {
        CosmeticsUi.drawWardrobeCard(stack, x, y, w, h, radius, selected, hovered);
    }

    private void drawTextBlock(Matrix3x2fStack stack, float x, float y, float w, float h,
                               CosmeticsLayout.Layout layout, float pad, String titleKey, boolean active) {
        float titleY = y + pad + layout.iconWell + pad;
        float titleFont = layout.font(CosmeticsReferenceSpec.FONT_CARD_TITLE);
        float descFont = layout.font(CosmeticsReferenceSpec.FONT_CARD_DESC);
        float textW = w - pad * 2f - 44f;
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, I18n.translate(titleKey), x + pad, titleY,
                active ? CosmeticsTokens.TEXT : CosmeticsReferenceSpec.TEXT_SECONDARY, titleFont);
        String descKey = titleKey + ".desc";
        String desc = I18n.translate(descKey);
        if (!desc.equals(descKey)) {
            WindowGuiRender.drawText(stack, Fonts.MEDIUM, LinkGuiSettingStyle.clipText(desc, textW, descFont),
                    x + pad, titleY + layout.s(15f), CosmeticsTokens.TEXT_MUTED, descFont);
        }
    }
}
