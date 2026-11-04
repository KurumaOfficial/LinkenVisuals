package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.modules.impl.render.SelfNametags;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererLabelMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void linkenvisuals$stripPreviewLabel(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (CosmeticPreviewState.suppressesEntityLabel() && state != null) {
            state.displayName = null;
        }
    }

    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/Entity;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void linkenvisuals$suppressPreviewHasLabel(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        if (CosmeticPreviewState.suppressesEntityLabel()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void linkenvisuals$skipCosmeticPreviewLabel(CallbackInfo ci) {
        if (CosmeticPreviewState.suppressesEntityLabel()) {
            ci.cancel();
        }
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$plainPreviewDisplayName(Entity entity, CallbackInfoReturnable<Text> cir) {
        if (CosmeticPreviewState.suppressesEntityLabel()) {
            cir.setReturnValue(null);
        }
    }
}
