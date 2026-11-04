package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.modules.impl.render.Optimization;
import ru.method.linkenvisuals.utils.optimization.OcclusionCuller;

@Mixin(WorldRenderer.class)
public abstract class OptimizationWorldRendererMixin {

    // Раз в кадр сбрасываем бюджет проверок окклюзии + чистим кеш куллера.
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void linkenvisuals$beginCullFrame(CallbackInfo ci) {
        OcclusionCuller.beginFrame();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void linkenvisuals$cullClouds(CallbackInfo ci) {
        if (Optimization.cullClouds()) {
            ci.cancel();
        }
    }
}
