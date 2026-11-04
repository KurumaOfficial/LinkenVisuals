package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.SubmittableBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.NoRender;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    private static boolean shouldSkipParticles() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return false;
        }
        NoRender noRender = instance.getModuleManager().getModule(NoRender.class);
        return noRender != null && noRender.isToggled() && noRender.particles.getValue();
    }

    /**
     * 1.21.11 batches particles through {@code ParticleManager#addToBatch}
     * instead of the removed {@code renderParticles} entry points.
     */
    @Inject(method = "addToBatch", at = @At("HEAD"), cancellable = true, require = 0)
    private void linkenvisuals$skipParticles(SubmittableBatch batch, Frustum frustum, Camera camera, float tickProgress, CallbackInfo ci) {
        if (shouldSkipParticles()) {
            ci.cancel();
        }
    }
}
