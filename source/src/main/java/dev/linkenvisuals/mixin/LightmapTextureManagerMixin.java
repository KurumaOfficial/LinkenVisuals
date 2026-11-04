package dev.linkenvisuals.mixin;

import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.FullbrightModule;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright (Phase 5.7): skips lightmap update when enabled, keeping full brightness.
 * Ported from the donor's {@code LightmapTextureManagerMixin} (adapted: clear() not in 1.21.11 API).
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$onLightmapUpdate(float delta, CallbackInfo ci) {
        // Let LightmapTextureManager calculate lightmap with 1.0f night vision strength
        // provided by GameRendererMixin#linkenvisuals$fullbrightNightVision.
    }
}
