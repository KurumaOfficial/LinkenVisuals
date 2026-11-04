package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.utils.client.MainMenuGreeting;
import ru.method.linkenvisuals.utils.math.Counter;
import ru.method.linkenvisuals.utils.render.LiquidGlassReflectionCache;
import ru.method.linkenvisuals.utils.render.RenderFrameTracker;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void linkenvisuals$beginFrame(boolean tick, CallbackInfo ci) {
        RenderFrameTracker.beginFrame();
    }

    // Ванильные пути (выход с сервера, «Список серверов» на экране дисконнекта) открывают
    // ванильный MultiplayerScreen напрямую, минуя главное меню — подменяем его нашим экраном
    // «Сетевая игра». CustomMultiplayerScreen наследует Screen (не MultiplayerScreen), рекурсии нет.
    /**
     * Конец цепочки входа на сервер. {@code screen == null} — игрок уже в мире; титульный экран или
     * экран отключения — вход сорвался. В обоих случаях кастомное окно подключения дальше не рисуем.
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void linkenvisuals$endJoinFlow(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        if (screen == null
                || screen instanceof net.minecraft.client.gui.screen.TitleScreen
                || screen instanceof net.minecraft.client.gui.screen.DisconnectedScreen) {
            ru.method.linkenvisuals.screen.overlay.LinkenConnectRenderer.endJoin();
        }
    }

    /**
     * Кастомный экран «Соединение потеряно» (хендофф disconnect). Подменяем ванильный DisconnectedScreen,
     * доставая из него причину кика (info.reason()) и заголовок; primary-ветка (мультиплеер/одиночка) и
     * возможность переподключения выводятся из isMultiplayerEnabled()/getCurrentServerEntry().
     * Наш экран наследует Screen (не DisconnectedScreen) — повторного захода нет.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$redirectDisconnected(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        if (screen instanceof net.minecraft.client.gui.screen.DisconnectedScreen dc) {
            MinecraftClient self = (MinecraftClient) (Object) this;
            net.minecraft.text.Text reason;
            try {
                reason = ((ru.method.linkenvisuals.api.mixins.DisconnectedScreenAccessor) dc)
                        .linkenvisuals$getInfo().reason();
            } catch (Throwable t) {
                reason = net.minecraft.text.Text.empty();
            }
            boolean multiplayer = self.isMultiplayerEnabled();
            net.minecraft.client.network.ServerInfo last = self.getCurrentServerEntry();
            ci.cancel();
            self.setScreen(new ru.method.linkenvisuals.screen.disconnect.LinkenDisconnectScreen(
                    dc.getTitle(), reason, multiplayer, last));
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$redirectMultiplayer(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        if (screen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen) {
            ci.cancel();
            ((MinecraftClient) (Object) this).setScreen(
                    new ru.method.linkenvisuals.screen.multiplayer.CustomMultiplayerScreen(null));
        }
    }

    /**
     * Подмена ВСЕХ ванильных окон настроек нашими (спека 10c). Перехват сделан здесь, в единственной
     * точке входа, а не правкой каждого вызывающего места: настройки открываются из главного меню,
     * из игрового ESC-меню, из экрана дисконнекта и из ванильных под-экранов между собой — залатать
     * все эти пути по отдельности невозможно, и хотя бы один ванильный экран всегда бы прорывался.
     *
     * <p>Наши экраны наследуют {@code Screen}, а не ванильные {@code GameOptionsScreen}, поэтому
     * повторный заход в этот инжект их не ловит и рекурсии нет.
     */
    /**
     * Кастомное меню паузы (спека 11a). {@code shouldShowMenu()} — фильтр тихой паузы F3+Esc:
     * там ваниль показывает голую надпись без меню, подменять её нечем и не нужно.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$redirectPauseMenu(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        if (screen instanceof net.minecraft.client.gui.screen.GameMenuScreen menu && menu.shouldShowMenu()) {
            ci.cancel();
            ((MinecraftClient) (Object) this).setScreen(
                    new ru.method.linkenvisuals.screen.settings.LinkenPauseScreen());
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$redirectOptions(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        net.minecraft.client.gui.screen.Screen replacement =
                ru.method.linkenvisuals.screen.settings.SettingsRouter.replace(screen);
        if (replacement == null) return;
        ci.cancel();
        ((MinecraftClient) (Object) this).setScreen(replacement);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        EventTick event = new EventTick();
        LinkenVisuals.getInstance().getEventHandler().post(event);
        Counter.updateFPS();
    }

    @ModifyExpressionValue(method = "handleBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    public boolean handleBlockBreaking(boolean original) {
        
        return original;
    }

    @ModifyExpressionValue(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;isBreakingBlock()Z"))
    public boolean doItemUse(boolean original) {
        
        return original;
    }

    @Inject(method = "setOverlay", at = @At("TAIL"))
    private void linkenvisuals$trackOverlay(Overlay overlay, CallbackInfo ci) {
        if (overlay instanceof SplashOverlay) {
            ru.method.linkenvisuals.utils.client.MainMenuGreeting.onReloadBegin();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void linkenvisuals$releaseRenderResources(CallbackInfo ci) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && instance.getConfigManager() != null) {
            instance.getConfigManager().saveNow();
        }
        // Гасим сетевые HttpClient'ы — их selector-потоки НЕ daemon и иначе держат JVM живой
        // (процесс javaw не завершается после закрытия игры).
        if (instance != null) {
            try {
                if (instance.getUsageStatsManager() != null) instance.getUsageStatsManager().shutdown();
                if (instance.getUserSyncManager() != null) instance.getUserSyncManager().shutdown();
                if (instance.getCrashLogTelegramManager() != null) instance.getCrashLogTelegramManager().shutdown();
            } catch (Throwable ignored) {
            }
        }
        LiquidGlassReflectionCache.clear();
        ru.method.linkenvisuals.utils.render.WorldSnapshotCache.release();
    }
}
