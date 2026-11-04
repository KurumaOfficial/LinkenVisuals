package ru.method.linkenvisuals.screen.cosmetics.widget;

import net.minecraft.client.resource.language.I18n;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsLayout;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsTab;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsUi;

/** Left column — category tabs with increased vertical spacing. */
public final class CosmeticsCategoryListWidget {

    public void render(Matrix3x2fStack stack, CosmeticsLayout.Layout layout, CosmeticsTab[] tabs,
                       CosmeticsTab active, float canvasMx, float canvasMy) {
        float panelX = layout.sidebarX;
        float panelY = layout.panelY();
        float chip = ru.method.linkenvisuals.screen.cosmetics.CosmeticsReferenceSpec.SECTION_CHIP;
        CosmeticsUi.drawSectionHeader(stack, layout, CosmeticsUi.SectionIcon.LAYERS,
                I18n.translate("cosmetics.categories"), panelX + layout.colPanelPad, panelY + layout.colPanelPad, chip);

        float itemY = panelY + layout.sectionTop;
        float tabX = panelX + layout.colPanelPad;
        float tabW = layout.sidebarW - layout.colPanelPad * 2f;
        for (CosmeticsTab tab : tabs) {
            boolean isActive = tab == active;
            boolean hovered = layout.isHovered(tabX, itemY, tabW, layout.tabH, canvasMx, canvasMy);
            CosmeticsUi.drawCategoryTab(stack, layout, tabX, itemY, tabW, tab, isActive, hovered);
            itemY += layout.tabH + layout.tabGap;
        }
    }

    public CosmeticsTab hitTest(CosmeticsLayout.Layout layout, CosmeticsTab[] tabs, float mx, float my) {
        float tabY = layout.panelY() + layout.sectionTop;
        float tabX = layout.sidebarX + layout.colPanelPad;
        float tabW = layout.sidebarW - layout.colPanelPad * 2f;
        for (CosmeticsTab tab : tabs) {
            if (layout.isHovered(tabX, tabY, tabW, layout.tabH, mx, my)) {
                return tab;
            }
            tabY += layout.tabH + layout.tabGap;
        }
        return null;
    }
}
