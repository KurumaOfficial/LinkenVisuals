package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.SkyChanger;

/**
 * 1.21.11 computes the sky colours inside {@link SkyRendering} instead of the
 * removed {@code ClientWorld#getSkyColor}. The state fields are exactly what
 * the sky pass consumes, so tinting them keeps SkyChanger 1:1 with the donor.
 */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {

    @Inject(method = "updateRenderState", at = @At("RETURN"), require = 0)
    private void linkenvisuals$skyChanger(ClientWorld world, float tickDelta, Camera camera, SkyRenderState state, CallbackInfo ci) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null || state == null) {
            return;
        }
        SkyChanger skyChanger = instance.getModuleManager().getModule(SkyChanger.class);
        if (skyChanger == null || !skyChanger.isToggled()) {
            return;
        }
        state.skyColor = skyChanger.applySkyColor(state.skyColor);
        state.sunriseAndSunsetColor = skyChanger.applySkyColor(state.sunriseAndSunsetColor);
    }
}
