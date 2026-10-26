package dev.linkenvisuals.mixin;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.hands.HandEditor;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.render.BackgroundPipeline;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.VanillaStylePipeline;
import dev.linkenvisuals.ui.Theme;
import dev.linkenvisuals.ui.editor.CustomTextureLoader;
import dev.linkenvisuals.ui.editor.MainMenuConfig;
import dev.linkenvisuals.ui.style.StyleRouting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "init()V", at = @At("HEAD"))
    private void linkenvisuals$playOpenSound(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof OptionsScreen || screen instanceof GameOptionsScreen
                || screen instanceof PackScreen || screen instanceof SelectWorldScreen
                || screen instanceof GameMenuScreen
                || screen instanceof dev.linkenvisuals.ui.AltManagerScreen) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getSoundManager() != null) {
                client.getSoundManager().play(PositionedSoundInstance.ui(LinkenVisuals.OPEN_SOUND, 0.9f, 1.0f));
            }
        }
    }

    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$clearViewWhilePlacing(DrawContext context, CallbackInfo ci) {
        if (HandEditor.holding()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$customBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = (Screen) (Object) this;
        var noRender = Modules.noRender();
        if (screen instanceof HandledScreen && noRender != null && noRender.enabled()
            && noRender.inventoryBackground.value()) {
            ci.cancel();
            return;
        }

        if (screen instanceof SelectWorldScreen
            || screen instanceof net.minecraft.client.gui.screen.world.CreateWorldScreen
            || screen instanceof net.minecraft.client.gui.screen.world.EditWorldScreen) {
            return;
        }

        if (StyleRouting.usesVanillaChrome(dev.linkenvisuals.ui.style.StyleManager.getStyle())) {
            if (screen instanceof GameMenuScreen) {
                context.fillGradient(0, 0, width, height, 0x88000000, 0xAA000000);
                ci.cancel();
                return;
            }
            if (screen instanceof OptionsScreen || screen instanceof GameOptionsScreen
                    || screen instanceof PackScreen || screen instanceof SelectWorldScreen
                    || screen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
                    || screen instanceof dev.linkenvisuals.ui.AltManagerScreen
                    || screen instanceof net.minecraft.client.gui.screen.world.CreateWorldScreen
                    || screen instanceof net.minecraft.client.gui.screen.world.EditWorldScreen
                    || screen instanceof net.minecraft.client.gui.screen.multiplayer.AddServerScreen
                    || screen instanceof net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen) {
                if (client.world != null) {
                    context.fillGradient(0, 0, width, height, 0x99000000, 0xCC000000);
                } else {
                    VanillaStylePipeline.renderVanillaPanorama(context, width, height);
                    context.fillGradient(0, 0, width, height, 0x1A000000, 0x30000000);
                }
                ci.cancel();
                return;
            }
            return;
        }

        if (screen instanceof GameMenuScreen) {
            if (!MainMenuConfig.get().customPauseScreen) {
                return;
            }
            // In-game pause menu: frosted glass blur & dynamic theme ambient glow over the world
            context.fillGradient(0, 0, width, height, 0x8805070B, 0xAA080B12);
            GlowPipeline.radial(context, width * 0.5f, height * 0.5f, width * 0.45f, (0x25 << 24) | (Theme.accent() & 0xFFFFFF));
            ci.cancel();
            return;
        }

        if (screen instanceof OptionsScreen || screen instanceof GameOptionsScreen
                || screen instanceof PackScreen || screen instanceof SelectWorldScreen
                || screen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
                || screen instanceof dev.linkenvisuals.ui.AltManagerScreen) {
            if (!MainMenuConfig.get().customSettingsScreen) {
                return;
            }
            if (client.world != null) {
                // In-game settings: world blur & dark glass overlay with theme accent glow
                context.fillGradient(0, 0, width, height, 0x9905070B, 0xCC080B12);
                GlowPipeline.radial(context, width * 0.5f, height * 0.5f, width * 0.45f, (0x22 << 24) | (Theme.accent() & 0xFFFFFF));
            } else {
                // Title screen / main menu settings: animated shader or custom photo
                MainMenuConfig menuCfg = MainMenuConfig.get();
                if (menuCfg.backgroundMode == MainMenuConfig.BackgroundMode.CUSTOM_IMAGE) {
                    Identifier customTex = CustomTextureLoader.getCustomTexture();
                    if (customTex != null) {
                        context.drawTexture(RenderPipelines.GUI_TEXTURED, customTex, 0, 0, 0.0f, 0.0f, width, height, width, height, width, height, 0xFFFFFFFF);
                    } else {
                        BackgroundPipeline.draw(1.0f);
                    }
                    int alpha = Math.round(menuCfg.imageDarkening * 255);
                    context.fillGradient(0, 0, width, height, (alpha << 24), (alpha << 24) | 0x05070A);
                } else {
                    BackgroundPipeline.draw(1.0f);
                }

                if (menuCfg.shaderOverlayEnabled && menuCfg.shaderEffect != MainMenuConfig.ShaderEffect.NONE) {
                    int glowAlpha = Math.round(menuCfg.shaderIntensity * 60);
                    GlowPipeline.radial(context, width * 0.5f, height * 0.5f, width * 0.45f, (glowAlpha << 24) | (Theme.accent() & 0xFFFFFF));
                }
                context.fillGradient(0, 0, width, height, 0x44000000, 0x88020406);
            }
            ci.cancel();
            return;
        }
    }

}
