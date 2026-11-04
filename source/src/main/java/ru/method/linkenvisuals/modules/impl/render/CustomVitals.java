package ru.method.linkenvisuals.modules.impl.render;

import ru.method.linkenvisuals.hud.CustomVitalsBar;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

/**
 * Кастомные полосы HP/голода/опыта (+ броня/воздух/маунт) в стиле Link GUI вместо ванильных, над
 * хотбаром. Включается/выключается как обычный модуль. Сама отрисовка — в {@link CustomVitalsBar},
 * вызывается из {@code InGameHudMixin} (до flush DrawContext). В креативе не показывается.
 */
public class CustomVitals extends Module {

    private final EnumSetting<CustomVitalsBar.VitalsStyle> style =
            new EnumSetting<>("Style", CustomVitalsBar.VitalsStyle.Segments);
    private final NumberSetting segments = new NumberSetting("Segments", 8f, 3f, 10f, 1f,
            () -> style.getValue() == CustomVitalsBar.VitalsStyle.Segments);
    private final BooleanSetting armorRow = new BooleanSetting("Armor row", true,
            () -> style.getValue() == CustomVitalsBar.VitalsStyle.Segments);

    public CustomVitals() {
        super("CustomVitals", Category.Visuals);
        getSettings().add(style);
        getSettings().add(segments);
        getSettings().add(armorRow);
    }

    public CustomVitalsBar.VitalsStyle getStyle() {
        return style.getValue();
    }

    public int getSegmentCount() {
        return Math.round(segments.getValue());
    }

    public boolean showArmorRow() {
        return armorRow.getValue();
    }
}
