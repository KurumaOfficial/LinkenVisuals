package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.render.GlyphAtlas;
import net.minecraft.util.Identifier;

/**
 * MSDF atlases used only by the LINKEN visual layer.
 *
 * <p>The atlases are copied from the donor (Golos, Unbounded, IBM Plex Mono)
 * and are loaded lazily once per process. A broken or missing atlas must never
 * break the menu: every accessor degrades to the existing SF atlases, and the
 * caller then falls back to vanilla text.
 */
public final class LinkenFonts {
    private static final String ROOT = "fonts/link/";

    private static boolean attempted;
    private static GlyphAtlas body;
    private static GlyphAtlas bodyMedium;
    private static GlyphAtlas heading;
    private static GlyphAtlas headingLight;
    private static GlyphAtlas mono;
    private static GlyphAtlas icons;
    private static GlyphAtlas iconsNew;
    private static GlyphAtlas icons2;
    private static GlyphAtlas iconsFa;
    private static GlyphAtlas brands;
    private static GlyphAtlas mediaPlayer;

    private LinkenFonts() {
    }

    public static GlyphAtlas body() {
        ensure();
        return body != null ? body : Fonts.textFont();
    }

    public static GlyphAtlas bodyMedium() {
        ensure();
        return bodyMedium != null ? bodyMedium : heading();
    }

    public static GlyphAtlas heading() {
        ensure();
        return heading != null ? heading : Fonts.titleFont();
    }

    public static GlyphAtlas headingLight() {
        ensure();
        return headingLight != null ? headingLight : heading();
    }

    public static GlyphAtlas mono() {
        ensure();
        return mono != null ? mono : Fonts.textFont();
    }

    /** Shared {@code wtmico} icon atlas (person, people, gear, power). */
    public static GlyphAtlas icons() {
        ensure();
        return icons;
    }

    /** Donor {@code iconsnew} atlas (category glyphs P/S/R/V/H). */
    public static GlyphAtlas iconsNew() {
        ensure();
        if (iconsNew == null) {
            iconsNew = loadShared("fonts/msdf/iconsnew.json", "fonts/msdf/iconsnew.png", "iconsnew");
            if (iconsNew == null) {
                iconsNew = loadShared("fonts/iconsnew.json", "fonts/iconsnew.png", "iconsnew");
            }
        }
        return iconsNew;
    }

    /** Donor {@code icons2} atlas (A person, B gear, C globe, D power, E TG, F Discord, H code). */
    public static GlyphAtlas icons2() {
        ensure();
        return icons2;
    }

    /** Donor {@code iconsfa} atlas (FontAwesome UI glyphs). */
    public static GlyphAtlas iconsFa() {
        ensure();
        if (iconsFa == null) {
            iconsFa = loadShared("fonts/msdf/iconsfa.json", "fonts/msdf/iconsfa.png", "iconsfa");
            if (iconsFa == null) {
                iconsFa = loadShared("fonts/iconsfa.json", "fonts/iconsfa.png", "iconsfa");
            }
        }
        return iconsFa;
    }

    /** Shared {@code brands} atlas (Telegram, Discord). */
    public static GlyphAtlas brands() {
        ensure();
        return brands;
    }

    /** Donor {@code media_player} atlas (A prev, B next, C play, D pause). */
    public static GlyphAtlas mediaPlayer() {
        ensure();
        if (mediaPlayer == null) {
            mediaPlayer = loadShared("fonts/msdf/media_player.json", "fonts/msdf/media_player.png", "media_player");
            if (mediaPlayer == null) {
                mediaPlayer = loadShared("fonts/media_player.json", "fonts/media_player.png", "media_player");
            }
        }
        return mediaPlayer;
    }

    /**
     * The splash overlay may ask for fonts before the initial resource reload
     * has finished. If that load failed, retry once the reload completes
     * instead of falling back to the SF atlas for the rest of the session.
     */
    public static void retryIfUnavailable() {
        if (attempted && (body == null || heading == null || iconsFa == null || mediaPlayer == null || iconsNew == null)) {
            attempted = false;
        }
    }

    private static void ensure() {
        if (attempted) {
            return;
        }
        attempted = true;
        body = load("golos");
        bodyMedium = load("golos_semi");
        heading = load("unbounded");
        headingLight = load("unbounded_light");
        mono = load("plexmono");
        icons = loadShared("fonts/msdf/wtmico.json", "fonts/msdf/wtmico.png", "wtmico");
        iconsNew = loadShared("fonts/msdf/iconsnew.json", "fonts/msdf/iconsnew.png", "iconsnew");
        icons2 = loadShared("fonts/msdf/icons2.json", "fonts/msdf/icons2.png", "icons2");
        iconsFa = loadShared("fonts/msdf/iconsfa.json", "fonts/msdf/iconsfa.png", "iconsfa");
        brands = loadShared("fonts/msdf/brands.json", "fonts/msdf/brands.png", "brands");
        mediaPlayer = loadShared("fonts/msdf/media_player.json", "fonts/msdf/media_player.png", "media_player");
    }

    private static GlyphAtlas load(String name) {
        return loadShared(ROOT + name + ".json", ROOT + name + ".png", name);
    }

    private static GlyphAtlas loadShared(String dataPath, String atlasPath, String label) {
        try {
            return GlyphAtlas.load(
                Identifier.of(LinkenVisuals.MOD_ID, dataPath),
                Identifier.of(LinkenVisuals.MOD_ID, atlasPath)
            );
        } catch (RuntimeException failure) {
            if (dataPath.startsWith("fonts/msdf/")) {
                try {
                    return GlyphAtlas.load(
                        Identifier.of(LinkenVisuals.MOD_ID, dataPath.replace("fonts/msdf/", "fonts/")),
                        Identifier.of(LinkenVisuals.MOD_ID, atlasPath.replace("fonts/msdf/", "fonts/"))
                    );
                } catch (RuntimeException ignored) {
                }
            } else if (dataPath.startsWith("fonts/")) {
                try {
                    return GlyphAtlas.load(
                        Identifier.of(LinkenVisuals.MOD_ID, dataPath.replace("fonts/", "fonts/msdf/")),
                        Identifier.of(LinkenVisuals.MOD_ID, atlasPath.replace("fonts/", "fonts/msdf/"))
                    );
                } catch (RuntimeException ignored) {
                }
            }
            LinkenVisuals.LOGGER.warn("[Visuals Modification] Failed to load font '{}': {}", label, failure.getMessage());
            return null;
        }
    }
}
