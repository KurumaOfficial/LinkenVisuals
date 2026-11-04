package ru.method.linkenvisuals.utils.render.fonts;

public final class Fonts {
    public static final Font BOLD = new Font("sf_bold");
    public static final Font MEDIUM = new Font("sf_medium");
    public static final Font REGULAR = new Font("sf_regular");
    public static final Font SEMIBOLD = new Font("sf_semibold");
    public static final Font ROUNDBOLD = new Font("round");
    public static final Font MEDIA_PLAYER = new Font("media_player");
    public static final Font ICONSNEW = new Font("iconsnew");
    public static final Font ICONS2 = new Font("icons2");
    public static final Font ICONSLINK = new Font("iconslink");
    public static final Font FORT = new Font("fort");
    public static final Font MODICONS = new Font("modicons");
    public static final Font ICONSFA = new Font("iconsfa");

    public static final Font UNBOUNDED = new Font("unbounded");
    public static final Font UNBOUNDED_L = new Font("unbounded_light");
    public static final Font GOLOS = new Font("golos");
    public static final Font GOLOS_SEMI = new Font("golos_semi");
    public static final Font PLEXMONO = new Font("plexmono");

    private Fonts() {}

    /** Loads every atlas before the first GUI frame (lazy loading used to cache
     *  fallback text widths on the first ClickGUI render). */
    public static void warmup() {
        for (Font font : new Font[]{BOLD, MEDIUM, REGULAR, SEMIBOLD, ROUNDBOLD, MEDIA_PLAYER,
                ICONSNEW, ICONS2, ICONSLINK, FORT, MODICONS, ICONSFA, UNBOUNDED, UNBOUNDED_L,
                GOLOS, GOLOS_SEMI, PLEXMONO}) {
            font.atlas();
        }
    }
}
