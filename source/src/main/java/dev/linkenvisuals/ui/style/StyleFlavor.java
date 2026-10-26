package dev.linkenvisuals.ui.style;

import dev.linkenvisuals.BuildFlavor;

import java.util.List;

/**
 * Compile-time style family lock. A normal build exposes only the default
 * visuals modification family; {@code -Pstrange} exposes only the alternative visuals
 * modification family. The two families never mix in one jar, so a user cannot
 * switch into the other design through settings, hotkeys, configs or editor buttons.
 */
public final class StyleFlavor {
    public enum Family {
        STRANGE,
        LINKEN
    }

    public record Rules(List<String> guiStyles, String defaultGuiStyle,
                        List<MenuStyle> menuStyles, MenuStyle defaultMenuStyle,
                        List<String> hudStyles, String defaultHudStyle,
                        List<String> hudShapes, String defaultHudShape) {
        public boolean allowsGuiStyle(String style) {
            return canonical(guiStyles, style, null) != null;
        }

        public String sanitizeGuiStyle(String style) {
            return canonical(guiStyles, style, defaultGuiStyle);
        }

        public boolean allowsMenuStyle(MenuStyle style) {
            return style != null && menuStyles.contains(style);
        }

        public MenuStyle sanitizeMenuStyle(MenuStyle style) {
            return allowsMenuStyle(style) ? style : defaultMenuStyle;
        }

        public MenuStyle parseMenuStyle(String name) {
            if (name != null && !name.isBlank()) {
                String trimmed = name.trim();
                for (MenuStyle style : menuStyles) {
                    if (style.name().equalsIgnoreCase(trimmed)
                        || style.displayName().equalsIgnoreCase(trimmed)) {
                        return style;
                    }
                }
            }
            return defaultMenuStyle;
        }

        public MenuStyle nextMenuStyle(MenuStyle current) {
            int index = menuStyles.indexOf(current);
            if (index < 0) {
                return defaultMenuStyle;
            }
            return menuStyles.get((index + 1) % menuStyles.size());
        }

        public boolean allowsHudStyle(String style) {
            return canonical(hudStyles, style, null) != null;
        }

        public String sanitizeHudStyle(String style) {
            return canonical(hudStyles, style, defaultHudStyle);
        }

        public boolean allowsHudShape(String shape) {
            return canonical(hudShapes, shape, null) != null;
        }

        public String sanitizeHudShape(String shape) {
            return canonical(hudShapes, shape, defaultHudShape);
        }

        private static String canonical(List<String> options, String value, String fallback) {
            if (value != null) {
                String trimmed = value.trim();
                for (String option : options) {
                    if (option.equalsIgnoreCase(trimmed)) {
                        return option;
                    }
                }
            }
            return fallback;
        }
    }

    private static final Rules STRANGE = new Rules(
        List.of("Beta", "Classic", "Linken"), "Beta",
        List.of(MenuStyle.BETA, MenuStyle.VANILLA, MenuStyle.LINKEN), MenuStyle.BETA,
        List.of("Strange", "Linken"), "Strange",
        List.of("Rounded"), "Rounded");

    private static final Rules LINKEN = new Rules(
        List.of("Linken"), "Linken",
        List.of(MenuStyle.LINKEN), MenuStyle.LINKEN,
        List.of("Card", "Minimal", "Neon", "Aurora", "Glass", "Solid", "Gradient", "Outline"), "Card",
        List.of("Rounded", "Pill", "Sharp"), "Rounded");

    public static final Family ACTIVE = BuildFlavor.STRANGE ? Family.STRANGE : Family.LINKEN;

    private StyleFlavor() {
    }

    public static boolean isStrange() {
        return ACTIVE == Family.STRANGE;
    }

    public static Family family() {
        return ACTIVE;
    }

    public static String flavorName() {
        return BuildFlavor.NAME;
    }

    public static List<String> guiStyleOptions() {
        return rules().guiStyles();
    }

    public static String defaultGuiStyle() {
        return rules().defaultGuiStyle();
    }

    public static boolean allowsGuiStyle(String style) {
        return rules().allowsGuiStyle(style);
    }

    public static String sanitizeGuiStyle(String style) {
        return rules().sanitizeGuiStyle(style);
    }

    public static boolean isGuiStyleLocked() {
        return rules().guiStyles().size() <= 1;
    }

    public static List<MenuStyle> menuStyleOptions() {
        return rules().menuStyles();
    }

    public static MenuStyle defaultMenuStyle() {
        return rules().defaultMenuStyle();
    }

    public static boolean allowsMenuStyle(MenuStyle style) {
        return rules().allowsMenuStyle(style);
    }

    public static MenuStyle sanitizeMenuStyle(MenuStyle style) {
        return rules().sanitizeMenuStyle(style);
    }

    public static MenuStyle parseMenuStyle(String name) {
        return rules().parseMenuStyle(name);
    }

    public static boolean isMenuStyleLocked() {
        return rules().menuStyles().size() <= 1;
    }

    public static MenuStyle nextMenuStyle(MenuStyle current) {
        return rules().nextMenuStyle(current);
    }

    public static List<String> hudStyleOptions() {
        return rules().hudStyles();
    }

    public static String defaultHudStyle() {
        return rules().defaultHudStyle();
    }

    public static boolean allowsHudStyle(String style) {
        return rules().allowsHudStyle(style);
    }

    public static String sanitizeHudStyle(String style) {
        return rules().sanitizeHudStyle(style);
    }

    public static boolean isHudStyleLocked() {
        return rules().hudStyles().size() <= 1;
    }

    public static List<String> hudShapeOptions() {
        return rules().hudShapes();
    }

    public static String defaultHudShape() {
        return rules().defaultHudShape();
    }

    public static boolean allowsHudShape(String shape) {
        return rules().allowsHudShape(shape);
    }

    public static String sanitizeHudShape(String shape) {
        return rules().sanitizeHudShape(shape);
    }

    public static boolean isHudShapeLocked() {
        return rules().hudShapes().size() <= 1;
    }

    /** Package-private so both flavors can be unit-tested from one build. */
    static Rules rulesFor(Family family) {
        return family == Family.STRANGE ? STRANGE : LINKEN;
    }

    private static Rules rules() {
        return rulesFor(ACTIVE);
    }
}
