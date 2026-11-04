package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.linkenvisuals.hands.HandEditor;
import dev.linkenvisuals.hands.HandPose;
import net.minecraft.client.MinecraftClient;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.SwingAnimation;
import ru.method.linkenvisuals.utils.Wrapper;

/**
 * First-person hand pose.
 *
 * <p>This is the donor/Strange {@code HandEditor} path: the free-form
 * {@link HandPose} offsets and scale are applied around the vanilla hand
 * anchor. The legacy twelve-slider ViewModel module is intentionally not
 * consulted any more; it produced wildly different axes and scales from the
 * Strange client and is hidden from both module lists.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin implements Wrapper {
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

    @Shadow
    protected abstract void swingArm(float swingProgress, MatrixStack matrices, int armX, Arm arm);

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

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V",
                    ordinal = 2
            )
    )
    private void linkenvisuals$applySwingAnimation(HeldItemRenderer instance, float swingProgress,
                                                   MatrixStack matrices, int armX, Arm arm) {
        SwingAnimation swingAnimation = LinkenVisuals.getInstance().getModuleManager()
            .getModule(SwingAnimation.class);
        MinecraftClient client = MinecraftClient.getInstance();
        Arm mainArm = client.player != null ? client.player.getMainArm() : Arm.RIGHT;
        if (swingAnimation != null && swingAnimation.isToggled() && arm == mainArm) {
            swingAnimation.renderSwordAnimation(matrices, swingProgress, 1.0f, arm);
        } else {
            this.swingArm(swingProgress, matrices, armX, arm);
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
