package ru.method.linkenvisuals.screen.cosmetics.widget;

import net.minecraft.client.resource.language.I18n;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsChrome;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsLayout;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsReferenceSpec;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTokens;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsUi;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.util.Locale;

/** Header: star chip, lavender title, muted subtitle, circular close. */
public final class CosmeticsHeaderWidget {

    public CosmeticsBounds render(Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                                  float canvasMx, float canvasMy) {
        float chip = CosmeticsReferenceSpec.HEADER_CHIP;
        float iconX = layout.outerPad;
        float iconY = 14f + (layout.headerH - 14f - chip) * 0.5f;
        CosmeticsChrome.drawHeaderIcon(stack, iconX, iconY, chip);

        float textX = iconX + chip + 12f;
        float fade = LinkGuiRenderContext.getFadeMultiplier();
        String title = I18n.translate("cosmetics.title").toUpperCase(Locale.ROOT);
        CosmeticsUi.drawTitle(stack, textX, 16f, title, fade);

        float subtitleAlpha = fade * 0.72f;
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, I18n.translate("cosmetics.subtitle"),
                textX, 34f, CosmeticsTokens.alpha(CosmeticsReferenceSpec.TEXT_SUBTITLE, subtitleAlpha),
                CosmeticsReferenceSpec.FONT_SUBTITLE);

        float closeX = layout.designW() - layout.outerPad - layout.closeSize;
        float closeY = 12f + (layout.headerH - layout.closeSize) * 0.5f;
        boolean closeHover = layout.isHovered(closeX, closeY, layout.closeSize, layout.closeSize, canvasMx, canvasMy);
        CosmeticsChrome.drawCloseButton(stack, closeX, closeY, layout.closeSize, closeHover);
        return new CosmeticsBounds(closeX, closeY, layout.closeSize, layout.closeSize);
    }
}
