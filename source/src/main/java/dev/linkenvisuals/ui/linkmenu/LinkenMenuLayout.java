package dev.linkenvisuals.ui.linkmenu;

/**
 * Pure geometry for the LINKEN main menu.
 *
 * <p>The values mirror the donor {@code CustomMainMenuScreen} on its
 * 1920x1080 design canvas: the welcome column starts at 56/300, is 340 wide,
 * the primary rows are 50 high with a 9 px gap, and the media block is a
 * 320 wide strip centred above the 44 px bottom margin. The class deliberately
 * has no Minecraft imports so the layout rules can be unit-tested.
 */
public final class LinkenMenuLayout {
    public static final float DESIGN_HEIGHT = 1080.0f;
    public static final float MARGIN = 56.0f;

    public static final float COLUMN_X = 56.0f;
    public static final float COLUMN_TOP = 300.0f;
    public static final float COLUMN_WIDTH = 340.0f;

    /**
     * Donor line boxes, not the nominal font sizes: {@code MsdfFont.getHeight}
     * adds the descender and a 1.05 stroke allowance. The eyebrow is
     * Unbounded-Light 10 (1.24 em -> 13.02) and the subtitle is Golos 14
     * (1.20 em -> 17.64). Using 10/14 here lifted the whole button stack 7 px
     * above the donor.
     */
    public static final float EYEBROW_HEIGHT = 13.02f;
    public static final float HEADLINE_GAP = 14.0f;
    public static final float HEADLINE_SIZE = 38.0f;
    public static final float HEADLINE_LINE = 43.7f;
    public static final float SUBTITLE_SIZE = 14.0f;
    public static final float SUBTITLE_HEIGHT = 17.64f;
    public static final float SUBTITLE_GAP = 28.0f;
    public static final float ACCOUNT_SIZE = 38.0f;
    public static final float ACCOUNT_GAP = 18.0f;

    public static final float BUTTON_HEIGHT = 50.0f;
    public static final float BUTTON_GAP = 9.0f;
    public static final float QUIT_WIDTH = 108.0f;

    public static final float SOCIAL_SIZE = 38.0f;
    public static final float SOCIAL_GAP = 9.0f;
    public static final int SOCIAL_COUNT = 4;

    public static final float MUSIC_WIDTH = 320.0f;
    public static final float MUSIC_HEIGHT = 86.0f;

    public static final float DISCLAIMER_SIZE = 11.0f;
    /** Donor Golos 11 line box (1.20 em * 1.05) * 1.5. */
    public static final float DISCLAIMER_LINE_HEIGHT = 20.79f;
    public static final float BOTTOM_MARGIN = 44.0f;
    public static final float SOCIAL_GAP_ABOVE_DISCLAIMER = 16.0f;

    private LinkenMenuLayout() {
    }

    public record Rect(float x, float y, float width, float height) {
        public float right() {
            return x + width;
        }

        public float bottom() {
            return y + height;
        }

        public boolean contains(float px, float py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }

        public boolean contains(float px, float py, float margin) {
            return px >= x - margin && px <= right() + margin
                && py >= y - margin && py <= bottom() + margin;
        }

        public Rect expand(float margin) {
            return new Rect(x - margin, y - margin, width + margin * 2.0f, height + margin * 2.0f);
        }

        public boolean inside(float screenWidth, float screenHeight) {
            return x >= 0.0f && y >= 0.0f && width > 0.0f && height > 0.0f
                && right() <= screenWidth && bottom() <= screenHeight;
        }

        public boolean intersects(Rect other) {
            return x < other.right() && right() > other.x
                && y < other.bottom() && bottom() > other.y;
        }
    }

    public record Metrics(
        float scale,
        float width,
        float height,
        Rect column,
        Rect wordmark,
        Rect clock,
        Rect eyebrow,
        Rect headline,
        Rect subtitle,
        Rect account,
        Rect multiplayer,
        Rect singleplayer,
        Rect settings,
        Rect quit,
        Rect socials,
        Rect music,
        Rect footer
    ) {
    }

    public static float scaleFor(int screenHeight) {
        if (screenHeight <= 0) {
            return 0.01f;
        }
        return Math.max(0.01f, screenHeight / DESIGN_HEIGHT);
    }

    public static float virtualWidth(int screenWidth, int screenHeight) {
        return Math.max(1.0f, screenWidth / scaleFor(screenHeight));
    }

    public static Metrics compute(int screenWidth, int screenHeight) {
        float scale = scaleFor(screenHeight);
        float width = Math.max(1.0f, screenWidth / scale);
        float height = DESIGN_HEIGHT;

        float x = MARGIN;
        float top = COLUMN_TOP;

        Rect eyebrow = new Rect(x, top, COLUMN_WIDTH, EYEBROW_HEIGHT);
        float headlineY = top + EYEBROW_HEIGHT + HEADLINE_GAP;
        Rect headline = new Rect(x, headlineY, COLUMN_WIDTH, HEADLINE_LINE * 2.0f);
        float subtitleY = headline.bottom() + 8.0f;
        Rect subtitle = new Rect(x, subtitleY, COLUMN_WIDTH, SUBTITLE_HEIGHT);
        float accountY = subtitle.bottom() + SUBTITLE_GAP;
        Rect account = new Rect(x, accountY, COLUMN_WIDTH, ACCOUNT_SIZE);

        float buttonsTop = account.bottom() + ACCOUNT_GAP;
        Rect multiplayer = new Rect(x, buttonsTop, COLUMN_WIDTH, BUTTON_HEIGHT);
        Rect singleplayer = new Rect(x, multiplayer.bottom() + BUTTON_GAP, COLUMN_WIDTH, BUTTON_HEIGHT);
        float thirdTop = singleplayer.bottom() + BUTTON_GAP;
        float settingsWidth = COLUMN_WIDTH - QUIT_WIDTH - BUTTON_GAP;
        Rect settings = new Rect(x, thirdTop, settingsWidth, BUTTON_HEIGHT);
        Rect quit = new Rect(settings.right() + BUTTON_GAP, thirdTop, QUIT_WIDTH, BUTTON_HEIGHT);

        float disclaimerTop = height - BOTTOM_MARGIN - DISCLAIMER_LINE_HEIGHT * 2.0f;
        Rect music = new Rect((width - MUSIC_WIDTH) * 0.5f,
            height - BOTTOM_MARGIN - MUSIC_HEIGHT, MUSIC_WIDTH, MUSIC_HEIGHT);
        float footerWidth = Math.max(120.0f,
            Math.min(560.0f, music.x() - MARGIN - 16.0f));
        Rect footer = new Rect(x, disclaimerTop, footerWidth, DISCLAIMER_LINE_HEIGHT * 2.0f);
        float socialsTop = disclaimerTop - SOCIAL_GAP_ABOVE_DISCLAIMER - SOCIAL_SIZE;
        float socialsWidth = SOCIAL_COUNT * SOCIAL_SIZE + (SOCIAL_COUNT - 1) * SOCIAL_GAP;
        Rect socials = new Rect(x, socialsTop, socialsWidth, SOCIAL_SIZE);

        Rect wordmark = new Rect(0.0f, 44.0f, width, 30.0f);
        Rect clock = new Rect(Math.max(MARGIN, width - MARGIN - 300.0f), 38.0f, 300.0f, 74.0f);

        Rect column = new Rect(x, top, COLUMN_WIDTH, quit.bottom() - top);

        return new Metrics(scale, width, height, column, wordmark, clock, eyebrow, headline,
            subtitle, account, multiplayer, singleplayer, settings, quit, socials, music, footer);
    }
}
