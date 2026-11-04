package ru.method.linkenvisuals.screen.linkgui.theme;

import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.utils.render.ColorUtils;

import java.awt.Color;

public enum LinkGuiThemePreset implements Nameable {
    // Акцентные цвета (primary/точка чипа) — из дизайн-споки ClickGUI 3a; фон/текст сохранены.
    MIDNIGHT("Midnight", new Color(126, 223, 180), new Color(15, 18, 23), new Color(244, 247, 250)),
    GRAPHITE("Graphite", new Color(154, 164, 173), new Color(18, 19, 23), new Color(240, 242, 247)),
    AMETHYST("Amethyst", new Color(177, 138, 224), new Color(20, 16, 28), new Color(247, 242, 255)),
    OCEAN("Ocean", new Color(77, 163, 232), new Color(12, 18, 28), new Color(232, 246, 255)),
    ROSE("Rose", new Color(224, 92, 116), new Color(26, 14, 22), new Color(255, 239, 246)),
    EMERALD("Emerald", new Color(62, 207, 122), new Color(12, 22, 18), new Color(236, 255, 242)),
    // Светлые темы (Light Mode): светлый фон + тёмный текст.
    LIGHT("Light", new Color(77, 163, 232), new Color(236, 238, 243), new Color(28, 30, 38)),
    FROST("Purple", new Color(138, 99, 232), new Color(244, 246, 250), new Color(30, 31, 42));

    private final String name;
    private final Color primary;
    private final Color background;
    private final Color text;

    LinkGuiThemePreset(String name, Color primary, Color background, Color text) {
        this.name = name;
        this.primary = primary;
        this.background = background;
        this.text = text;
    }

    @Override
    public String getName() {
        return name;
    }

    public Color primary() {
        return primary;
    }

    public Color background() {
        return background;
    }

    public Color text() {
        return text;
    }

    public Color secondaryAccent() {
        return new Color(
                Math.round((primary.getRed() + text.getRed()) * 0.5f),
                Math.round((primary.getGreen() + text.getGreen()) * 0.5f),
                Math.round((primary.getBlue() + text.getBlue()) * 0.5f)
        );
    }

    public static LinkGuiThemePreset byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (LinkGuiThemePreset preset : values()) {
            if (preset.name.equalsIgnoreCase(name.trim())) {
                return preset;
            }
        }
        return null;
    }

    /** Same slot as Panel GUI background color — drives glass tint via {@link ColorUtils#getGlassBase()}. */
    public Color colorUtilsBackground() {
        Color lightNeutral = new Color(220, 220, 220);
        Color tinted = ColorUtils.mix(lightNeutral, primary, 0.14f);
        return ColorUtils.mix(tinted, background, 0.06f);
    }

    /** Applies full theme palette to {@link ColorUtils} — same flow as Panel GUI color pickers. */
    public void applyToColorUtils() {
        ColorUtils.setPrimaryColor(primary);
        ColorUtils.setSecondaryColor(secondaryAccent());
        ColorUtils.setBackgroundColor(colorUtilsBackground());
        ColorUtils.setTextColor(text);
    }

    @Deprecated
    public void applyHudAccent() {
        applyToColorUtils();
    }

    @Deprecated
    public void apply() {
        applyToColorUtils();
    }
}
