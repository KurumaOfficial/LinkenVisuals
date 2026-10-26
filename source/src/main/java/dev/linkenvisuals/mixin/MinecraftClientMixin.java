package dev.linkenvisuals.mixin;

import dev.linkenvisuals.ui.AltManagerScreen;
import dev.linkenvisuals.ui.CustomMultiplayerScreen;
import dev.linkenvisuals.ui.MainMenuScreen;
import dev.linkenvisuals.ui.linkmenu.LinkenAltManagerScreen;
import dev.linkenvisuals.ui.linkmenu.LinkenMainMenuScreen;
import dev.linkenvisuals.ui.linkmenu.LinkenMultiplayerScreen;
import dev.linkenvisuals.ui.linkmenu.LinkenPauseScreen;
import dev.linkenvisuals.ui.linkmenu.LinkenResourcePacksScreen;
import dev.linkenvisuals.ui.linkmenu.LinkenSettingsScreen;
import dev.linkenvisuals.ui.linkmenu.LinkenSkinScreen;
import dev.linkenvisuals.ui.style.MenuStyle;
import dev.linkenvisuals.ui.style.StyleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.SkinOptionsScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen linkenvisuals$redirectTitleScreen(Screen screen) {
        MenuStyle style = StyleManager.getStyle();
        if (screen == null) {
            if (MinecraftClient.getInstance().world == null) {
                return switch (style) {
                    case VANILLA -> new TitleScreen();
                    case BETA -> new MainMenuScreen();
                    case LINKEN -> new LinkenMainMenuScreen();
                };
            }
            return null;
        }
        return switch (style) {
            case VANILLA -> linkenvisuals$vanilla(screen);
            case BETA -> linkenvisuals$beta(screen);
            case LINKEN -> linkenvisuals$linken(screen);
        };
    }

    private static Screen linkenvisuals$vanilla(Screen screen) {
        if (screen instanceof MainMenuScreen || screen instanceof LinkenMainMenuScreen) {
            return new TitleScreen();
        }
        if (screen instanceof CustomMultiplayerScreen custom) {
            return new MultiplayerScreen(custom.getParentScreen() != null
                ? custom.getParentScreen() : new TitleScreen());
        }
        return screen;
    }

    private static Screen linkenvisuals$beta(Screen screen) {
        if (screen instanceof LinkenMainMenuScreen) {
            return new MainMenuScreen();
        }
        if (screen instanceof TitleScreen && !(screen instanceof MainMenuScreen)) {
            return new MainMenuScreen();
        }
        return screen;
    }

    private static Screen linkenvisuals$linken(Screen screen) {
        if (screen instanceof MainMenuScreen) {
            return new LinkenMainMenuScreen();
        }
        if (screen instanceof TitleScreen) {
            return new LinkenMainMenuScreen();
        }
        if (screen instanceof GameMenuScreen) {
            return new LinkenPauseScreen();
        }
        if (screen instanceof OptionsScreen optionsScreen) {
            Screen parent = null;
            try {
                parent = ((OptionsScreenAccessor) optionsScreen).linkenvisuals$getParent();
            } catch (Throwable ignored) {
            }
            if (parent == null || parent == optionsScreen || parent instanceof OptionsScreen || parent instanceof LinkenSettingsScreen) {
                parent = MinecraftClient.getInstance().currentScreen;
            }
            if (parent == null || parent == optionsScreen || parent instanceof OptionsScreen || parent instanceof LinkenSettingsScreen) {
                parent = MinecraftClient.getInstance().world != null
                    ? new LinkenPauseScreen()
                    : new LinkenMainMenuScreen();
            }
            return new LinkenSettingsScreen(parent);
        }
        if (screen instanceof SkinOptionsScreen skinOptions) {
            Screen parent = null;
            try {
                parent = ((GameOptionsScreenAccessor) skinOptions).linkenvisuals$getParent();
            } catch (Throwable ignored) {
            }
            if (parent == null || parent == skinOptions) {
                parent = MinecraftClient.getInstance().currentScreen;
            }
            if (parent == null || parent == skinOptions) {
                parent = new LinkenSettingsScreen(null);
            }
            return new LinkenSkinScreen(parent);
        }
        if (screen instanceof PackScreen) {
            Screen parent = MinecraftClient.getInstance().currentScreen;
            if (parent == null || parent instanceof PackScreen) {
                parent = new LinkenSettingsScreen(null);
            }
            return new LinkenResourcePacksScreen(parent);
        }
        if (screen instanceof AltManagerScreen) {
            return new LinkenAltManagerScreen(MinecraftClient.getInstance().currentScreen);
        }
        if (screen instanceof CustomMultiplayerScreen custom) {
            Screen parent = custom.getParentScreen();
            return new LinkenMultiplayerScreen(parent != null
                ? parent : MinecraftClient.getInstance().currentScreen);
        }
        if (screen instanceof MultiplayerScreen) {
            return new LinkenMultiplayerScreen(MinecraftClient.getInstance().currentScreen);
        }
        return screen;
    }
}
