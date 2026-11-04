package ru.method.linkenvisuals.screen.cosmetics.widget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticPlayerPreview;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsChrome;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsLayout;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsReferenceSpec;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTab;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsUi;

/** Center column — viewport shell, player badge, stage floor, optional tint slider. */
public final class CosmeticsPreviewPanelWidget {
    private final CosmeticPlayerPreview playerPreview;
    private final CosmeticsColorTintWidget tintWidget;

    public CosmeticsPreviewPanelWidget(CosmeticPlayerPreview playerPreview, CosmeticsColorTintWidget tintWidget) {
        this.playerPreview = playerPreview;
        this.tintWidget = tintWidget;
    }

    public PreviewLayout render(DrawContext context, Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                                CosmeticsTab activeTab, String hoveredCosmeticId,
                                float canvasMx, float canvasMy,
                                Identifier skin, String playerName, String badgeTime) {
        float x = layout.previewX;
        float panelY = layout.panelY();
        CosmeticsUi.drawSectionHeader(stack, layout, CosmeticsUi.SectionIcon.EYE,
                I18n.translate("cosmetics.preview"), x + layout.colPanelPad, panelY + layout.colPanelPad,
                CosmeticsReferenceSpec.SECTION_CHIP);

        CosmeticsBounds viewport = viewportBounds(layout);
        CosmeticsChrome.drawViewport(stack, viewport.x(), viewport.y(), viewport.w(), viewport.h(), layout.insetRadius);

        float margin = CosmeticsReferenceSpec.VIEWPORT_INSET;
        float badgeH = CosmeticsReferenceSpec.BADGE_H;
        float badgeX = viewport.x() + margin;
        float badgeY = viewport.y() + margin;
        float badgeW = viewport.w() - margin * 2f;

        boolean showTint = activeTab == CosmeticsTab.WINGS;
        if (showTint) {
            float tintBlockH = tintWidget.referenceHeight(1f) + CosmeticsReferenceSpec.WINGS_TINT_PAD;
            float tintY = viewport.y() + viewport.h() - margin - tintBlockH + CosmeticsReferenceSpec.WINGS_TINT_PAD;
            tintWidget.updateDrag((int) canvasMx, (int) canvasMy, badgeX, tintY, badgeW, 1f);
        }

        if (skin != null) {
            CosmeticsUi.drawPlayerBadgeOverlay(stack, badgeX, badgeY, badgeW, badgeH, skin, playerName, badgeTime, layout);
        }

        CosmeticsBounds stage = stageBounds(viewport, badgeY, badgeH, margin, showTint);
        CosmeticsChrome.drawPreviewStageFrame(stack, stage.x(), stage.y(), stage.w(), stage.h());
        CosmeticsChrome.drawPreviewStage(stack, stage.x(), stage.y(), stage.w(), stage.h());

        CosmeticSlot slot = activeTab.isWardrobe() ? activeTab.wardrobeSlot() : CosmeticSlot.WINGS;
        CosmeticsService service = LinkenVisuals.getInstance().getCosmeticsService();
        CosmeticProfile profile = service.getLocalProfile();
        String previewId = hoveredCosmeticId != null && activeTab.isWardrobe()
                ? hoveredCosmeticId
                : profile.get(slot);
        preparePreview(profile, slot, previewId, activeTab, showTint);

        if (showTint) {
            float tintBlockH = tintWidget.referenceHeight(1f) + CosmeticsReferenceSpec.WINGS_TINT_PAD;
            float tintY = viewport.y() + viewport.h() - margin - tintBlockH + CosmeticsReferenceSpec.WINGS_TINT_PAD;
            tintWidget.render(context, badgeX, tintY, badgeW, 1f, (int) canvasMx, (int) canvasMy, 255);
            if (tintWidget.consumeDirty()) {
                service.applyLocalTint(slot, tintWidget.colorArgb());
            }
        }

        return new PreviewLayout(viewport, stage, showTint ? tintBounds(viewport, margin) : null);
    }

    public CosmeticPlayerPreview playerPreview() {
        return playerPreview;
    }

    private void preparePreview(CosmeticProfile profile, CosmeticSlot slot, String previewId,
                                CosmeticsTab activeTab, boolean showTint) {
        playerPreview.setSlot(slot);
        CosmeticProfile previewProfile = profile;
        if (activeTab.isWardrobe()) {
            previewProfile = profile.with(slot, previewId);
        }
        if (showTint) {
            int tint = tintWidget.isAdjusting() ? tintWidget.colorArgb() : profile.getTint(slot);
            if (!tintWidget.isAdjusting()) {
                tintWidget.setColor(tint);
            }
            previewProfile = previewProfile.withTint(slot, tint);
        }
        playerPreview.setPreviewProfile(previewProfile);
    }

    public static CosmeticsBounds viewportBounds(CosmeticsLayout.Layout layout) {
        float top = layout.panelY() + layout.sectionTop;
        return new CosmeticsBounds(
                layout.previewX + layout.colPanelPad,
                top,
                layout.previewW - layout.colPanelPad * 2f,
                layout.panelH - layout.sectionTop - layout.colPanelPad
        );
    }

    public static CosmeticsBounds stageBounds(CosmeticsBounds viewport, float badgeY, float badgeH,
                                              float margin, boolean showTint) {
        float stageX = viewport.x() + margin;
        float stageY = badgeY + badgeH + margin;
        float stageW = viewport.w() - margin * 2f;
        float tintReserve = showTint
                ? 38f + CosmeticsReferenceSpec.WINGS_TINT_PAD * 2f
                : margin;
        float stageH = Math.max(48f, viewport.y() + viewport.h() - margin - tintReserve - stageY);
        return new CosmeticsBounds(stageX, stageY, stageW, stageH);
    }

    public static CosmeticsBounds tintBounds(CosmeticsBounds viewport, float margin) {
        float tintBlockH = 38f + CosmeticsReferenceSpec.WINGS_TINT_PAD;
        float tintY = viewport.y() + viewport.h() - margin - tintBlockH + CosmeticsReferenceSpec.WINGS_TINT_PAD;
        return new CosmeticsBounds(viewport.x() + margin, tintY, viewport.w() - margin * 2f, tintBlockH);
    }

    public record PreviewLayout(CosmeticsBounds viewport, CosmeticsBounds stage, CosmeticsBounds tint) {
    }
}
