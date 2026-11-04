package dev.linkenvisuals.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.linkenvisuals.hands.HandEditor;
import dev.linkenvisuals.hands.HandPose;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Strange/HandEditor first-person pose. Kept in the dev mixin set for the
 * future non-legacy renderer; the legacy bridge uses the same logic in
 * {@code ru.method.linkenvisuals.api.mixins.HeldItemRendererMixin}.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private static final float PIVOT_X = HandEditor.PIVOT_X;
    @Unique
    private static final float PIVOT_Y = HandEditor.PIVOT_Y;
    @Unique
    private static final float PIVOT_Z = HandEditor.PIVOT_Z;

    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices,
                                                 OrderedRenderCommandQueue queue, int light,
                                                 float equipProgress, float swingProgress, Arm arm);

    @ModifyExpressionValue(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;"
        + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
        + "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer$HandRenderType;"
                + "renderMainHand:Z"))
    private boolean linkenvisuals$alwaysMainHand(boolean original) {
        return original || HandEditor.active();
    }

    @ModifyExpressionValue(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;"
        + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
        + "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer$HandRenderType;"
                + "renderOffHand:Z"))
    private boolean linkenvisuals$alwaysOffHand(boolean original) {
        return original || HandEditor.active();
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$placeHand(AbstractClientPlayerEntity player, float tickProgress,
                                          float pitch, Hand hand, float swingProgress,
                                          ItemStack stack, float equipProgress, MatrixStack matrices,
                                          OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == Arm.RIGHT ? 1.0f : -1.0f;
        float pivotX = side * PIVOT_X;

        matrices.push();
        float scaleX = HandPose.scaleX(arm);
        float scaleY = HandPose.scaleY(arm);
        float scaleZ = HandPose.scaleZ(arm);
        matrices.translate(pivotX + HandPose.x(arm), PIVOT_Y + HandPose.y(arm),
            PIVOT_Z + HandPose.z(arm));
        if (Math.abs(scaleX - 1.0f) > 0.0001f || Math.abs(scaleY - 1.0f) > 0.0001f
            || Math.abs(scaleZ - 1.0f) > 0.0001f) {
            matrices.scale(scaleX, scaleY, scaleZ);
        }
        matrices.translate(-pivotX, -PIVOT_Y, -PIVOT_Z);

        if (HandEditor.active() && stack.isEmpty() && hand == Hand.OFF_HAND && !player.isInvisible()) {
            renderArmHoldingItem(matrices, queue, light, equipProgress, swingProgress, arm);
            matrices.pop();
            ci.cancel();
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void linkenvisuals$releaseHand(AbstractClientPlayerEntity player, float tickProgress,
                                            float pitch, Hand hand, float swingProgress,
                                            ItemStack stack, float equipProgress, MatrixStack matrices,
                                            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        matrices.pop();
    }
}
