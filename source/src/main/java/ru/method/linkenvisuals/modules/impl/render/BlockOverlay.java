package ru.method.linkenvisuals.modules.impl.render;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

/**
 * <b>TEMPORARILY DISABLED / NEEDS FIXING.</b> Hidden from both GUIs; the
 * module is intentionally not added to {@code ModuleManager}. Keep this class
 * and {@code utils/render/BlockOverlayRenderer} in the tree, re-add
 * {@code new BlockOverlay()} after the half-face fill / outline mask bugs are
 * fixed and verified.
 */
public class BlockOverlay extends Module {

    public final BooleanSetting fill = new BooleanSetting("settings.blockoverlay.fill", true);
    public final BooleanSetting useShader = new BooleanSetting("modules.settings.blockoverlay.use_shader", true, () -> fill.getValue());
    public final EnumSetting<FillMode> fillMode = new EnumSetting<>("settings.blockoverlay.fill_mode", FillMode.Aurora, () -> fill.getValue());
    public final NumberSetting fillAlpha = new NumberSetting("settings.blockoverlay.fill_alpha", 70f, 0f, 255f, 1f, () -> fill.getValue());
    public final NumberSetting outlineAlpha = new NumberSetting("settings.blockoverlay.outline_alpha", 255f, 0f, 255f, 1f);
    public final NumberSetting shaderSpeed = new NumberSetting("modules.settings.blockoverlay.shader_speed", 1f, 0.1f, 5f, 0.1f, () -> fill.getValue() && useShader.getValue());

    public BlockOverlay() {
        super("BlockOverlay", Category.Visuals);
    }

    public enum FillMode implements Nameable {
        Aurora("settings.blockoverlay.fill_mode.aurora"),
        Plasma("settings.blockoverlay.fill_mode.plasma"),
        Cosmos("settings.blockoverlay.fill_mode.cosmos"),
        Cobweb("settings.blockoverlay.fill_mode.cobweb"),
        Fireworks("settings.blockoverlay.fill_mode.fireworks"),
        Galaxy("settings.blockoverlay.fill_mode.galaxy"),
        Nebula("settings.blockoverlay.fill_mode.nebula"),
        Starfield("settings.blockoverlay.fill_mode.starfield"),
        Stars("settings.blockoverlay.fill_mode.stars");

        private final String name;

        FillMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
