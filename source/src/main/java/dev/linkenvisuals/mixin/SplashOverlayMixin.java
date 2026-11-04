package dev.linkenvisuals.mixin;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.render.Pipelines;
import dev.linkenvisuals.ui.editor.MainMenuConfig;
import dev.linkenvisuals.ui.linkmenu.LinkenFonts;
import dev.linkenvisuals.ui.linkmenu.LinkenLoadingScreen;
import dev.linkenvisuals.ui.linkmenu.SplashProgress;
import dev.linkenvisuals.ui.style.MenuStyle;
import dev.linkenvisuals.ui.style.StyleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Replaces the Mojang splash with the LINKEN loading scene for the LINKEN
 * style. The first frames (before our shaders/fonts exist) use a vanilla-only
 * bootstrap scene, so the Mojang logo never appears in LINKEN mode.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private Consumer<Optional<Throwable>> exceptionHandler;
    @Shadow @Final private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    @Unique
    private static boolean linkenvisuals$failureLogged;
    @Unique
    private long linkenvisuals$lastFrameMs;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$renderLinkenSplash(DrawContext context, int mouseX, int mouseY,
                                                  float delta, CallbackInfo ci) {
        if (StyleManager.getStyle() != MenuStyle.LINKEN || !MainMenuConfig.get().customLoadingScreen) {
            return;
        }
        try {
            long now = Util.getMeasuringTimeMs();
            if (reloading && reloadStartTime <= 0L) {
                reloadStartTime = now;
            }
            float sinceStart = reloadStartTime > 0L
                ? (now - reloadStartTime) / 500.0f : -1.0f;

            float dt = linkenvisuals$lastFrameMs == 0L ? 0.016f
                : Math.min(0.1f, (now - linkenvisuals$lastFrameMs) / 1000.0f);
            linkenvisuals$lastFrameMs = now;
            float rate = reloadCompleteTime > 0L || reload.isComplete() ? 10.0f : 2.2f;
            float k = 1.0f - (float) Math.exp(-rate * dt);
            progress = MathHelper.clamp(progress + (reload.getProgress() - progress) * k, 0.0f, 1.0f);

            if (reloadCompleteTime == -1L && reload.isComplete()
                && (!reloading || sinceStart >= 2.0f)) {
                try {
                    reload.throwException();
                    exceptionHandler.accept(Optional.empty());
                } catch (Throwable failure) {
                    exceptionHandler.accept(Optional.of(failure));
                }
                reloadCompleteTime = Util.getMeasuringTimeMs();
                if (client.currentScreen != null) {
                    client.currentScreen.init(context.getScaledWindowWidth(),
                        context.getScaledWindowHeight());
                }
            }

            if (SplashProgress.finished(reloadCompleteTime, now)) {
                LinkenLoadingScreen.dispose();
                client.setOverlay(null);
                ci.cancel();
                return;
            }

            boolean complete = reloadCompleteTime > 0L;
            if (complete) {
                LinkenFonts.retryIfUnavailable();
            }
            float alpha = complete
                ? SplashProgress.fadeOut(reloadCompleteTime, now)
                : (reloading ? SplashProgress.fadeIn(reloadStartTime, now) : 1.0f);
            float exit = SplashProgress.exit(reloadCompleteTime, now);
            float clamped = SplashProgress.displayProgress(progress);
            String title = I18n.get("loading.splash.title");
            String subtitle = I18n.get(SplashProgress.stageKey(clamped));

            // Render current screen (e.g. main menu) underneath the sliding exit splash
            if (exit > 0.0f && client.currentScreen != null) {
                client.currentScreen.render(context, mouseX, mouseY, delta);
            } else if (reloading && client.currentScreen != null && sinceStart < 1.0f) {
                client.currentScreen.render(context, mouseX, mouseY, delta);
            }

            LinkenLoadingScreen.renderBootstrap(context, context.getScaledWindowWidth(),
                context.getScaledWindowHeight(), clamped, alpha, exit, title, subtitle);
            ci.cancel();
        } catch (Throwable failure) {
            if (!linkenvisuals$failureLogged) {
                linkenvisuals$failureLogged = true;
                LinkenVisuals.LOGGER.warn("[Visuals Modification] Splash failed, falling back to vanilla: {}",
                    failure.toString());
            }
        }
    }
}
