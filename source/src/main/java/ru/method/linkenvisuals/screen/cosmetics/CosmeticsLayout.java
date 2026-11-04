package ru.method.linkenvisuals.screen.cosmetics;

import lombok.experimental.UtilityClass;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiCanvasTransform;

/**
 * Layout в design-пространстве HTML mockup 1120×700.
 * Масштаб/центрирование — {@link LinkGuiCanvasTransform}.
 */
@UtilityClass
public final class CosmeticsLayout {
    public static final float CANVAS_WIDTH = CosmeticsTokens.DESIGN_W;
    public static final float CANVAS_HEIGHT = CosmeticsTokens.DESIGN_H;
    public static final float CANVAS_CENTER_X = CANVAS_WIDTH * 0.5f;
    public static final float CANVAS_CENTER_Y = CANVAS_HEIGHT * 0.5f;

    /** HTML `.stage { padding: 16px }`. */
    private static final float FIT_SCREEN_PAD = 16f;

    public static final class Layout {
        public final int screenW;
        public final int screenH;
        public final float scale;

        public final float outerPad;
        public final float headerH;
        public final float sidebarX;
        public final float sidebarW;
        public final float contentY;
        public final float contentH;
        public final float previewX;
        public final float previewW;
        public final float gridX;
        public final float gridW;
        public final float colGap;
        public final float columnsTopPad;
        public final float panelH;
        public final float colPanelPad;
        public final float colPanelRadius;
        public final float tabH;
        public final float tabGap;
        public final float tabRadius;
        public final float tabInset;
        public final float sectionTop;
        public final float gridGap;
        public final float cardH;
        public final float cardRadius;
        public final float iconWell;
        public final float insetRadius;
        public final float closeSize;
        public final float outerRadius;

        public Layout(int screenW, int screenH, float scale) {
            this.screenW = screenW;
            this.screenH = screenH;
            this.scale = scale;

            this.outerPad = CosmeticsTokens.OUTER_PAD;
            this.headerH = CosmeticsTokens.HEADER_H;
            this.sidebarW = CosmeticsTokens.SIDEBAR_W;
            this.previewW = CosmeticsTokens.PREVIEW_W;
            this.gridW = CosmeticsTokens.GRID_W;
            this.colGap = CosmeticsTokens.COL_GAP;
            this.columnsTopPad = CosmeticsTokens.COLUMNS_TOP_PAD;
            this.colPanelPad = CosmeticsTokens.COL_PANEL_PAD;
            this.colPanelRadius = CosmeticsTokens.COL_PANEL_RADIUS;

            this.sidebarX = outerPad;
            this.contentY = headerH;
            this.contentH = CosmeticsTokens.DESIGN_H - headerH - CosmeticsTokens.CONTENT_BOTTOM_PAD;
            this.panelH = contentH - columnsTopPad;
            this.previewX = sidebarX + sidebarW + colGap;
            this.gridX = previewX + previewW + colGap;

            this.tabH = CosmeticsTokens.TAB_H;
            this.tabGap = CosmeticsTokens.TAB_GAP;
            this.tabRadius = CosmeticsTokens.TAB_RADIUS;
            this.tabInset = CosmeticsTokens.TAB_INSET;
            this.sectionTop = CosmeticsTokens.SECTION_TOP;
            this.gridGap = CosmeticsTokens.GRID_GAP;
            this.cardH = CosmeticsTokens.CARD_H;
            this.cardRadius = CosmeticsTokens.CARD_RADIUS;
            this.iconWell = CosmeticsTokens.ICON_WELL;
            this.insetRadius = CosmeticsTokens.INSET_RADIUS;
            this.closeSize = CosmeticsTokens.CLOSE_SIZE;
            this.outerRadius = CosmeticsTokens.OUTER_RADIUS;
        }

        public float panelY() {
            return contentY + columnsTopPad;
        }

        public float designW() {
            return CANVAS_WIDTH;
        }

        public float designH() {
            return CANVAS_HEIGHT;
        }

        public float renderScale() {
            return Math.max(0.01f, scale);
        }

        public float s(float designPx) {
            return designPx;
        }

        public float font(float designPt) {
            return designPt;
        }

        public boolean isHovered(float dx, float dy, float w, float h, float canvasMouseX, float canvasMouseY) {
            return canvasMouseX >= dx && canvasMouseX <= dx + w
                    && canvasMouseY >= dy && canvasMouseY <= dy + h;
        }
    }

    public Layout compute(int screenW, int screenH) {
        screenW = Math.max(1, screenW);
        screenH = Math.max(1, screenH);
        float scale = LinkGuiCanvasTransform.layoutScale(
                screenW, screenH, CANVAS_WIDTH, CANVAS_HEIGHT, FIT_SCREEN_PAD);
        return new Layout(screenW, screenH, scale);
    }
}
