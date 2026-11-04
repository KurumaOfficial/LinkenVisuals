package ru.method.linkenvisuals.screen.cosmetics.widget;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.catalog.CosmeticCatalog;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.cosmetics.model.CosmeticEffectType;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEnums.PetSkin;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEffects;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticPlayerPreview;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsLayout;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsPetIconRenderer;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsReferenceSpec;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTab;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTokens;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsUi;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.util.List;
import java.util.function.Consumer;

/** Right column — 2-column scrollable item grid with matrix scissor. */
public final class CosmeticsItemGridWidget {
    private static final int COLUMNS = 2;

    private final CosmeticsItemCardWidget cardWidget = new CosmeticsItemCardWidget();

    public GridMetrics render(DrawContext context, Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                              CosmeticsTab activeTab, CosmeticPlayerPreview preview,
                              float scroll, float canvasMx, float canvasMy,
                              CosmeticEffects effects, Consumer<String> hoverCallback) {
        float chip = CosmeticsReferenceSpec.SECTION_CHIP;
        CosmeticsUi.drawTabSectionHeader(stack, layout, activeTab, layout.gridX + layout.colPanelPad,
                layout.panelY() + layout.colPanelPad, chip);

        CosmeticsBounds viewport = gridViewport(layout);
        float cellW = cellWidth(layout);
        float cellH = layout.cardH;

        Render2D.startMatrixScissor(context, viewport.x(), viewport.y(), viewport.w(), viewport.h());
        try {
            return switch (activeTab) {
                case CHARACTER, CAPE, HAT, HEAD, BODY, SHIELD, WINGS -> renderWardrobe(context, stack, layout, activeTab, preview,
                        scroll, cellW, cellH, canvasMx, canvasMy, hoverCallback);
                case EFFECT -> renderEffect(context, stack, layout, cellW, cellH, effects, canvasMx, canvasMy);
                case PET -> renderPet(context, stack, layout, effects, scroll, cellW, cellH, canvasMx, canvasMy);
                default -> new GridMetrics(viewport, scroll, 0f, null);
            };
        } finally {
            Render2D.stopScissor(context);
        }
    }

    private GridMetrics renderWardrobe(DrawContext context, Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                                       CosmeticsTab tab, CosmeticPlayerPreview preview, float scroll,
                                       float cellW, float cellH, float canvasMx, float canvasMy,
                                       Consumer<String> hoverCallback) {
        CosmeticSlot slot = tab.wardrobeSlot();
        String selectedId = LinkenVisuals.getInstance().getCosmeticsService().getLocalProfile().get(slot);
        List<CosmeticDefinition> items = CosmeticCatalog.forSlot(slot);

        float top = layout.panelY() + layout.sectionTop;
        float viewportH = gridViewport(layout).h();
        int rows = (items.size() + COLUMNS - 1) / COLUMNS;
        float maxScroll = Math.max(0f, rows * (cellH + layout.gridGap) - viewportH);
        scroll = MathHelper.clamp(scroll, 0f, maxScroll);

        String hovered = null;
        float startY = top - scroll;
        for (int i = 0; i < items.size(); i++) {
            CosmeticDefinition item = items.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            float cellX = layout.gridX + layout.colPanelPad + col * (cellW + layout.gridGap);
            float cellY = startY + row * (cellH + layout.gridGap);
            boolean selected = item.id().equals(selectedId);
            cardWidget.renderWardrobe(context, stack, layout, cellX, cellY, cellW, cellH,
                    item, slot, selected, preview, canvasMx, canvasMy);
            if (layout.isHovered(cellX, cellY, cellW, cellH, canvasMx, canvasMy)) {
                hovered = item.id();
            }
        }
        if (hoverCallback != null) {
            hoverCallback.accept(hovered);
        }
        return new GridMetrics(gridViewport(layout), scroll, maxScroll, hovered);
    }

    private GridMetrics renderEffect(DrawContext context, Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                                     float cellW, float cellH, CosmeticEffects effects,
                                     float canvasMx, float canvasMy) {
        boolean halo = CosmeticEffects.bool(effects.haloEnabledSetting());
        boolean spheres = CosmeticEffects.bool(effects.spheresEnabledSetting());
        float x = layout.gridX + layout.colPanelPad;
        float y = layout.panelY() + layout.sectionTop;

        cardWidget.renderEffect(context, stack, layout, x, y, cellW, cellH,
                "cosmetics.effect.none", "cosmetics.effect.none.desc", null, true, !halo && !spheres, canvasMx, canvasMy);
        cardWidget.renderEffect(context, stack, layout, x + cellW + layout.gridGap, y, cellW, cellH,
                "cosmetics.effect.halo", "cosmetics.effect.halo.desc", CosmeticEffectType.HALO, false, halo, canvasMx, canvasMy);
        cardWidget.renderEffect(context, stack, layout, x, y + cellH + layout.gridGap, cellW, cellH,
                "cosmetics.effect.spheres", "cosmetics.effect.spheres.desc", CosmeticEffectType.SPHERES, false, spheres, canvasMx, canvasMy);
        return new GridMetrics(gridViewport(layout), 0f, 0f, null);
    }

    private GridMetrics renderPet(DrawContext context, Matrix3x2fStack stack, CosmeticsLayout.Layout layout,
                                  CosmeticEffects effects, float scroll, float cellW, float cellH,
                                  float canvasMx, float canvasMy) {
        boolean petOn = CosmeticEffects.bool(effects.petEnabledSetting());
        PetSkin selected = effects.petSkinSetting().getValue();
        PetSkin[] skins = PetSkin.values();
        float top = layout.panelY() + layout.sectionTop;
        float viewportH = gridViewport(layout).h();
        int rows = (skins.length + COLUMNS - 1) / COLUMNS;
        float maxScroll = Math.max(0f, rows * (cellH + layout.gridGap) - viewportH);
        scroll = MathHelper.clamp(scroll, 0f, maxScroll);

        float startY = top - scroll;
        for (int i = 0; i < skins.length; i++) {
            PetSkin skin = skins[i];
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            float cellX = layout.gridX + layout.colPanelPad + col * (cellW + layout.gridGap);
            float cellY = startY + row * (cellH + layout.gridGap);
            boolean selectedSkin = petOn && skin == selected;
            boolean hovered = layout.isHovered(cellX, cellY, cellW, cellH, canvasMx, canvasMy);
            CosmeticsUi.drawWardrobeCard(stack, cellX, cellY, cellW, cellH, layout.cardRadius, selectedSkin, hovered);

            float iconX = cellX + (cellW - layout.iconWell) * 0.5f;
            float iconY = cellY + CosmeticsTokens.CARD_PAD;
            CosmeticsUi.drawIconWell(stack, iconX, iconY, layout.iconWell, layout.insetRadius, selectedSkin);
            CosmeticsPetIconRenderer.render(context, effects, skin, Math.round(iconX), Math.round(iconY), Math.round(layout.iconWell));

            float titleY = cellY + layout.iconWell + layout.s(20f);
            WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, skin.getName(), cellX + layout.s(12f), titleY,
                    selectedSkin ? CosmeticsTokens.TEXT : CosmeticsTokens.TEXT_SECONDARY,
                    layout.font(CosmeticsReferenceSpec.FONT_CARD_TITLE));
            CosmeticsUi.drawCardToggle(stack, cellX, cellY, cellW, cellH, selectedSkin, 1f);
        }
        return new GridMetrics(gridViewport(layout), scroll, maxScroll, null);
    }

    public static CosmeticsBounds gridViewport(CosmeticsLayout.Layout layout) {
        float top = layout.panelY() + layout.sectionTop;
        return new CosmeticsBounds(layout.gridX, top, layout.gridW,
                layout.panelH - layout.sectionTop - layout.colPanelPad);
    }

    public static float cellWidth(CosmeticsLayout.Layout layout) {
        return (layout.gridW - layout.colPanelPad * 2f - layout.gridGap) / COLUMNS;
    }

    public String hitWardrobeItem(CosmeticsLayout.Layout layout, CosmeticsTab tab, float scroll,
                                  float mx, float my) {
        if (!tab.isWardrobe()) {
            return null;
        }
        CosmeticSlot slot = tab.wardrobeSlot();
        List<CosmeticDefinition> items = CosmeticCatalog.forSlot(slot);
        float cellW = cellWidth(layout);
        float cellH = layout.cardH;
        float top = layout.panelY() + layout.sectionTop - scroll;
        for (int i = 0; i < items.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            float cellX = layout.gridX + layout.colPanelPad + col * (cellW + layout.gridGap);
            float cellY = top + row * (cellH + layout.gridGap);
            if (mx >= cellX && mx <= cellX + cellW && my >= cellY && my <= cellY + cellH) {
                return items.get(i).id();
            }
        }
        return null;
    }

    public boolean clickWardrobe(CosmeticsLayout.Layout layout, CosmeticsTab tab, float scroll, float mx, float my) {
        String hit = hitWardrobeItem(layout, tab, scroll, mx, my);
        if (hit == null) {
            return false;
        }
        CosmeticSlot slot = tab.wardrobeSlot();
        CosmeticsService service = LinkenVisuals.getInstance().getCosmeticsService();
        String selectedId = service.getLocalProfile().get(slot);
        if (hit.equals(selectedId)) {
            service.applyLocalSelection(slot, CosmeticCatalog.none(slot).id());
        } else {
            service.applyLocalSelection(slot, hit);
        }
        return true;
    }

    public boolean clickEffect(CosmeticsLayout.Layout layout, float mx, float my, CosmeticEffects effects) {
        float x = layout.gridX + layout.colPanelPad;
        float y = layout.panelY() + layout.sectionTop;
        float cellW = cellWidth(layout);
        float cellH = layout.cardH;

        if (layout.isHovered(x, y, cellW, cellH, mx, my)) {
            effects.haloEnabledSetting().setValue(false);
            effects.spheresEnabledSetting().setValue(false);
            return true;
        }
        if (layout.isHovered(x + cellW + layout.gridGap, y, cellW, cellH, mx, my)) {
            effects.haloEnabledSetting().setValue(true);
            effects.spheresEnabledSetting().setValue(false);
            return true;
        }
        if (layout.isHovered(x, y + cellH + layout.gridGap, cellW, cellH, mx, my)) {
            effects.spheresEnabledSetting().setValue(true);
            effects.haloEnabledSetting().setValue(false);
            return true;
        }
        return false;
    }

    public boolean clickPet(CosmeticsLayout.Layout layout, float scroll, float mx, float my, CosmeticEffects effects) {
        boolean petOn = CosmeticEffects.bool(effects.petEnabledSetting());
        PetSkin selected = effects.petSkinSetting().getValue();
        PetSkin[] skins = PetSkin.values();
        float cellW = cellWidth(layout);
        float cellH = layout.cardH;
        float top = layout.panelY() + layout.sectionTop - scroll;

        for (int i = 0; i < skins.length; i++) {
            PetSkin skin = skins[i];
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            float cellX = layout.gridX + layout.colPanelPad + col * (cellW + layout.gridGap);
            float cellY = top + row * (cellH + layout.gridGap);
            if (!layout.isHovered(cellX, cellY, cellW, cellH, mx, my)) {
                continue;
            }
            if (petOn && skin == selected) {
                effects.petEnabledSetting().setValue(false);
            } else {
                effects.petSkinSetting().setValue(skin);
                effects.petEnabledSetting().setValue(true);
            }
            return true;
        }
        return false;
    }

    public float clampScroll(CosmeticsLayout.Layout layout, CosmeticsTab tab, float scroll) {
        float cellH = layout.cardH;
        float viewportH = gridViewport(layout).h();
        int itemCount = switch (tab) {
            case CHARACTER, CAPE, HAT, HEAD, BODY, SHIELD, WINGS -> CosmeticCatalog.forSlot(tab.wardrobeSlot()).size();
            case PET -> PetSkin.values().length;
            default -> 0;
        };
        if (itemCount <= 0) {
            return 0f;
        }
        int rows = (itemCount + COLUMNS - 1) / COLUMNS;
        float maxScroll = Math.max(0f, rows * (cellH + layout.gridGap) - viewportH);
        return MathHelper.clamp(scroll, 0f, maxScroll);
    }

    public record GridMetrics(CosmeticsBounds viewport, float scroll, float maxScroll, String hoveredId) {
    }
}
