package dev.linkenvisuals.mixin;

import dev.linkenvisuals.module.impl.OptimizationModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class OptimizationParticleMixin {
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
        at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$cullDistantParticle(Particle particle, CallbackInfo ci) {
        if (particle == null) {
            return;
        }
        Box box = particle.getBoundingBox();
        if (box == null) {
            return;
        }
        double x = (box.minX + box.maxX) * 0.5;
        double y = (box.minY + box.maxY) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        if (!OptimizationModule.allowParticle(x, y, z)) {
            ci.cancel();
        }
    }
}
