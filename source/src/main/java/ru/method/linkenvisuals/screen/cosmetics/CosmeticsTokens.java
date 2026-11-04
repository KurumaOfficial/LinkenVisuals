package ru.method.linkenvisuals.screen.cosmetics;



import lombok.experimental.UtilityClass;



import java.awt.Color;



/** Абсолютные пиксели референса COSMETICS GUI (1120×700) — sync с tokens.css. */

@UtilityClass

public final class CosmeticsTokens {

    public static final int DESIGN_W = 1120;

    public static final int DESIGN_H = 700;



    public static final float OUTER_RADIUS = 24f;

    public static final float OUTER_PAD = 34f;

    public static final float HEADER_H = 76f;

    public static final float CONTENT_BOTTOM_PAD = 28f;

    public static final float COLUMNS_TOP_PAD = 8f;



    public static final float SIDEBAR_W = 255f;

    public static final float PREVIEW_W = 357f;

    public static final float GRID_W = 408f;

    public static final float COL_GAP = 16f;



    public static final float COL_PANEL_PAD = 12f;

    public static final float COL_PANEL_RADIUS = 10f;

    public static final float SECTION_GAP = 10f;

    public static final float SECTION_HEAD_H = 16f;

    /** От верха col-panel до первого tab / viewport / card row. */

    public static final float SECTION_TOP = COL_PANEL_PAD + SECTION_HEAD_H + SECTION_GAP;



    public static final float TAB_H = 64f;

    public static final float TAB_GAP = 14f;

    public static final float TAB_RADIUS = 6f;

    public static final float TAB_INSET = 18f;



    public static final float CARD_PAD = 12f;

    public static final float GRID_GAP = 14f;

    public static final float CARD_RADIUS = 8f;

    public static final float CARD_H = 178f;

    public static final float ICON_WELL = 52f;



    public static final float INSET_RADIUS = 8f;

    public static final float CLOSE_SIZE = 30f;



    public static final float TOGGLE_W = 34f;

    public static final float TOGGLE_H = 18f;



    public static final Color PURPLE = CosmeticsReferenceSpec.PURPLE;

    public static final Color PURPLE_SECTION = CosmeticsReferenceSpec.PURPLE_SECTION;

    public static final Color TEXT = CosmeticsReferenceSpec.TEXT_PRIMARY;

    public static final Color TEXT_TITLE = CosmeticsReferenceSpec.TEXT_TITLE;

    public static final Color TEXT_SUBTITLE = CosmeticsReferenceSpec.TEXT_SUBTITLE;

    public static final Color TEXT_SECONDARY = CosmeticsReferenceSpec.TEXT_SECONDARY;

    public static final Color TEXT_MUTED = CosmeticsReferenceSpec.TEXT_MUTED;

    public static final Color BORDER = CosmeticsReferenceSpec.BORDER_PURPLE;



    public static Color tabIdle(float fade) {

        return alpha(CosmeticsReferenceSpec.BG_TAB_IDLE, fade);

    }



    public static Color tabIdleHover(float fade) {

        return alpha(new Color(22, 18, 34), 0.82f * fade);

    }



    public static Color alpha(Color c, float a) {

        return new Color(c.getRed(), c.getGreen(), c.getBlue(),

                Math.max(0, Math.min(255, Math.round(255f * a))));

    }

}


