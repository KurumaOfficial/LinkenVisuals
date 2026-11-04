package ru.method.linkenvisuals.screen.settings;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import ru.method.linkenvisuals.LinkenVisuals;

/** Optional Sodium integration: use its own apply/reload/validation logic, including addon pages. */
public final class GraphicsSettings {
    private GraphicsSettings() {}

    public static Screen create(Screen parent) {
        Screen sodium = sodium(parent);
        if (sodium != null) return sodium;
        MinecraftClient client = MinecraftClient.getInstance();
        return SettingsRouter.graphics(parent, new VideoOptionsScreen(parent, client, client.options));
    }

    public static Screen sodium(Screen parent) {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return null;
        try {
            // Sodium 0.6.x (MC 1.21.4). Factory also handles its first-run options prompt.
            Class<?> gui = Class.forName("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI");
            return (Screen) gui.getMethod("createScreen", Screen.class).invoke(null, parent);
        } catch (ReflectiveOperationException | LinkageError error) {
            LinkenVisuals.LOGGER.warn("Could not open Sodium video settings", error);
            return null;
        }
    }
}
