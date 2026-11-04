package ru.method.linkenvisuals.screen.cosmetics;



import lombok.experimental.UtilityClass;



import java.awt.Color;



/**

 * Эталон mockup / HTML prototype — единый источник цветов и типографики (1120×700).

 * Значения из tools/cosmetics-prototype/tokens.css (sampling cosmetics-mockup.png).

 */

@UtilityClass

public final class CosmeticsReferenceSpec {

    // ── Palette ──

    public static final Color PURPLE = new Color(0x7752D6);

    public static final Color PURPLE_BRIGHT = new Color(0xBC92FF);

    public static final Color PURPLE_SECTION = new Color(0x9B87F5);

    public static final Color PURPLE_TAB_TOP = new Color(0x684DB4);

    public static final Color PURPLE_TAB_MID = new Color(0x6848B5);

    public static final Color PURPLE_TAB_BOTTOM = new Color(0x6246AD);
    /** Horizontal active-tab gradient end (purple → indigo-blue). */
    public static final Color PURPLE_TAB_END = new Color(0x4B6FD6);

    public static final Color PURPLE_TAB_BORDER = new Color(188, 146, 255, 56);



    public static final Color TEXT_TITLE = new Color(0xC49AFF);

    public static final Color TEXT_SUBTITLE = new Color(0x686679);

    public static final Color TEXT_PRIMARY = Color.WHITE;

    public static final Color TEXT_SECONDARY = new Color(0x9CA3AF);

    public static final Color TEXT_MUTED = new Color(0x6B7280);



    public static final Color BG_MODAL = new Color(26, 22, 37, 235);

    public static final Color BG_PANEL = new Color(33, 31, 47, 184);

    public static final Color BG_CARD = new Color(0x1B1B26);

    public static final Color BG_CARD_SELECTED = new Color(0x13101C);

    public static final Color BG_VIEWPORT = new Color(0x080612);

    public static final Color BG_TAB_IDLE = new Color(0x272535);

    public static final Color BG_ICON_WELL = new Color(0x0C0A14);



    public static final Color BORDER_PURPLE = new Color(119, 82, 214, 66);

    public static final Color BORDER_SUBTLE = new Color(255, 255, 255, 13);

    public static final Color CHIP_GRADIENT_TOP = new Color(0xBC92FF);

    public static final Color CHIP_GRADIENT_BOTTOM = new Color(0x6246AD);



    // ── Typography (design px @ 1120×700) ──

    public static final float FONT_TITLE = 17f;

    public static final float FONT_SUBTITLE = 11f;

    public static final float FONT_SECTION = 13f;

    public static final float FONT_TAB = 12f;

    public static final float FONT_CARD_TITLE = 12f;

    public static final float FONT_CARD_DESC = 10f;

    public static final float FONT_BADGE_NAME = 11f;

    public static final float FONT_BADGE_TIME = 9f;

    public static final float FONT_WINGS_TINT = 10f;

    public static final float FONT_CLOSE_ICON = 11f;



    // ── Chrome sizes ──

    public static final float TAB_CHECK_SIZE = 22f;

    public static final float SECTION_CHIP = 16f;

    public static final float HEADER_CHIP = 30f;

    public static final float TAB_ICON = 20f;

    public static final float TAB_ICON_BOX = 28f;

    /** Preview viewport — sync с cosmetics.css .viewport / .badge / .preview-stage */
    public static final float VIEWPORT_INSET = 8f;
    /** Preview badge — room for name row + muted timestamp subtitle. */
    public static final float BADGE_H = 40f;
    public static final float WINGS_TINT_PAD = 8f;
}


