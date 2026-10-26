package dev.linkenvisuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.linkenvisuals.cosmetic.character.RudyCharacter;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.HitColorModule;
import dev.linkenvisuals.render.state.HitColorRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HitColor: advances the per-render-state tint strength and feeds the tint into
 * vanilla's {@code getMixColor} return value. Vanilla already mixes that value
 * into the model color ({@code ColorHelper.mix(base, mixColor)}), so no
 * {@code submitModel} redirection is needed for the body.
 *
 * <p>Also hosts the Rudy character substitution (Phase 5.6): the player-model submission is
 * replaced with the character meshes when the render state asked for Rudy.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;"
        + "Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL"))
    private void linkenvisuals$advanceHitColor(LivingEntity entity, LivingEntityRenderState state,
                                               float tickDelta, CallbackInfo ci) {
        HitColorRenderState hitState = (HitColorRenderState) state;
        hitState.linkenvisuals$setHitColorPlayer(entity instanceof PlayerEntity);

        HitColorModule module = Modules.hitColor();
        if (module == null || !module.enabled()) {
            hitState.linkenvisuals$setHitColorStrength(0.0f);
            return;
        }
        boolean hitByMe = HitColorModule.wasHitByMe(entity.getId());
        hitState.linkenvisuals$setHitColorStrength(
            HitColorModule.advanceStrength(entity.getId(), hitByMe));
    }

    @Inject(method = "getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I",
        at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$hitColorTint(LivingEntityRenderState state,
                                            CallbackInfoReturnable<Integer> cir) {
        HitColorModule module = Modules.hitColor();
        if (module == null || !module.enabled()) {
            return;
        }
        HitColorRenderState hitState = (HitColorRenderState) state;
        if (!hitState.linkenvisuals$isHitColorPlayer()) {
            return;
        }
        float strength = hitState.linkenvisuals$getHitColorStrength();
        if (strength <= 0.001f) {
            return;
        }
        int tinted = HitColorModule.tint(cir.getReturnValue(), module.color.rgb(),
            strength, module.opacity.value() / 255.0f);
        cir.setReturnValue(tinted);
    }

    /**
     * Rudy (Phase 5.6): replaces the deferred player-model command with the character meshes.
     * The donor wrapped the direct {@code model.render} call; on 1.21.11 the model is submitted
     * to the ordered command queue, so the substitution happens at the submit site and
     * {@link RudyCharacter#submit} re-poses the shared model inside the draw lambda.
     */
    @WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;"
        + "Lnet/minecraft/client/util/math/MatrixStack;"
        + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
        + "Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel("
                + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                + "Lnet/minecraft/client/util/math/MatrixStack;"
                + "Lnet/minecraft/client/render/RenderLayer;III"
                + "Lnet/minecraft/client/texture/Sprite;I"
                + "Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    private void linkenvisuals$rudyCharacter(OrderedRenderCommandQueue queue, Model<?> model, Object state,
                                             MatrixStack matrices, RenderLayer layer, int light, int overlay,
                                             int color, Sprite sprite, int outlineColor,
                                             ModelCommandRenderer.CrumblingOverlayCommand crumbling,
                                             Operation<Void> original) {
        if (state instanceof PlayerEntityRenderState playerState && model instanceof PlayerEntityModel playerModel) {
            if (RudyCharacter.active(playerState) && RudyCharacter.available()) {
                RudyCharacter.submit(queue, matrices, playerModel, playerState, light, overlay, color);
                return;
            }
            if (dev.linkenvisuals.cosmetic.character.RobloxBaconCharacter.active(playerState)
                    && dev.linkenvisuals.cosmetic.character.RobloxBaconCharacter.available()) {
                dev.linkenvisuals.cosmetic.character.RobloxBaconCharacter.submit(queue, matrices, playerModel, playerState, light, overlay, color);
                return;
            }
            if (dev.linkenvisuals.cosmetic.character.JasonCharacter.active(playerState)
                    && dev.linkenvisuals.cosmetic.character.JasonCharacter.available()) {
                dev.linkenvisuals.cosmetic.character.JasonCharacter.submit(queue, matrices, playerModel, playerState, light, overlay, color);
                return;
            }
            if (dev.linkenvisuals.cosmetic.character.AstolfoMesh.active(playerState)
                    && dev.linkenvisuals.cosmetic.character.AstolfoMesh.available()) {
                dev.linkenvisuals.cosmetic.character.AstolfoMesh.submit(queue, matrices, playerModel, playerState, light, overlay, color);
                return;
            }
        }
        original.call(queue, model, state, matrices, layer, light, overlay, color, sprite, outlineColor, crumbling);
    }
}
