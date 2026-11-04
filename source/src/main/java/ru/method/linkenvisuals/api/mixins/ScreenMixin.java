package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.NoRender;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.utils.debug.RenderProfiler;
import ru.method.linkenvisuals.utils.render.LiquidGlassReflectionCache;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Shadow
    protected MinecraftClient client;

    @Inject(method = "setInitialFocus", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$guardNullClient(CallbackInfo ci) {
        if (client == null) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void linkenvisuals$captureLiquidGlassBackdrop(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof ClickGui) {
            LiquidGlassReflectionCache.captureClickGuiBackdrop(true);
        }
    }

    /**
     * Продолжение кастомного окна подключения на ОСТАЛЬНЫХ экранах входа на сервер.
     *
     * <p>Вход — это цепочка ванильных экранов: {@code ConnectScreen} → {@code ReconfiguringScreen} →
     * {@code DownloadingTerrainScreen} («Загрузка территории»), плюс промежуточные {@code MessageScreen}.
     * Рескин был только у первого, поэтому после него вылезала ваниль. Перехват сделан здесь, на базовом
     * {@code Screen}, а не отдельными миксинами: {@code ReconfiguringScreen} и {@code MessageScreen}
     * вообще НЕ переопределяют {@code render}, и инжектить в них напрямую нечего.
     *
     * <p>Отменяем только отрисовку — тик, ввод и условия закрытия остаются ванильными, поэтому
     * жизненный цикл подключения не трогается.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$connectFlowSkin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!ru.method.linkenvisuals.screen.overlay.LinkenConnectRenderer.covers(self)) return;
        ci.cancel();
        ru.method.linkenvisuals.screen.overlay.LinkenConnectRenderer.render(
                context, mouseX, mouseY,
                ru.method.linkenvisuals.screen.overlay.LinkenConnectRenderer.stageOf(self),
                ru.method.linkenvisuals.screen.overlay.LinkenConnectRenderer.targetName(),
                ru.method.linkenvisuals.screen.overlay.LinkenConnectRenderer.targetAddress(),
                false);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void linkenvisuals$renderProfilerOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        RenderProfiler.renderOverlay(context);
    }


    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$hideInventoryBackground(DrawContext context, CallbackInfo ci) {
        if (!((Object) this instanceof HandledScreen<?>)) {
            return;
        }

        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return;
        }

        NoRender noRender = instance.getModuleManager().getModule(NoRender.class);
        if (noRender != null && noRender.isToggled() && noRender.inventoryBackground.getValue()) {
            ci.cancel();
        }
    }
}
