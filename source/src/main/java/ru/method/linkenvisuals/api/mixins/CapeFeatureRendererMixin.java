package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.accessors.ICapeRenderState;
import ru.method.linkenvisuals.cosmetics.wave.CapeSimulation;
import ru.method.linkenvisuals.cosmetics.wave.WaveCapeRenderer;

/**
 * Replaces the flat vanilla cape with the 16-segment simulated wave cape. The 1.21.11 feature
 * renderer submits into the ordered command queue instead of the old immediate provider.
 */
@Mixin(CapeFeatureRenderer.class)
public class CapeFeatureRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void linkenvisuals$waveCape(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                        PlayerEntityRenderState state, float limbAngle, float limbDistance,
                                        CallbackInfo ci) {
        if (state == null || state.invisible || !state.capeVisible || state.skinTextures == null) {
            return;
        }
        Identifier cape = state.skinTextures.cape() == null ? null : state.skinTextures.cape().id();
        if (cape == null || !(state instanceof ICapeRenderState capeState)) {
            return;
        }
        CapeSimulation simulation = capeState.linkenvisuals$capeSimulation();
        if (simulation == null || !simulation.isBuilt()) {
            return;
        }
        matrices.push();
        if (ru.method.linkenvisuals.cosmetics.character.RudyCharacter.active(state)) {
            matrices.translate(0f, .28f, .42f);
            matrices.scale(1.2f, .9f, 1f);
        }
        try {
            WaveCapeRenderer.render(matrices, queue, light, cape, simulation,
                    capeState.linkenvisuals$capeTickDelta(), capeState.linkenvisuals$capeCrouching());
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.warn("[WaveCape] render failed: {}", t.toString());
            return;
        } finally {
            matrices.pop();
        }
        ci.cancel();
    }
}
