package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.accessors.IItemPhysicsRenderState;
import ru.method.linkenvisuals.api.mixins.accessors.IEntityRendererShadow;
import ru.method.linkenvisuals.modules.impl.misc.ItemPhysics;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin extends EntityRenderer<ItemEntity, ItemEntityRenderState> {

    @Shadow
    @Final
    private Random random;

    protected ItemEntityRendererMixin(EntityRendererFactory.Context context) {
        super(context);
    }

    @Unique
    private ItemPhysics linkenvisuals$getItemPhysics() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return null;
        }
        return instance.getModuleManager().getModule(ItemPhysics.class);
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void linkenvisuals$capturePhysicsState(ItemEntity itemEntity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        IItemPhysicsRenderState physicsState = (IItemPhysicsRenderState) state;
        ItemPhysics itemPhysics = linkenvisuals$getItemPhysics();

        physicsState.linkenvisuals$setOnGround(itemEntity.isOnGround());
        physicsState.linkenvisuals$setHorizontalSpeed((float) Math.hypot(itemEntity.getVelocity().x, itemEntity.getVelocity().z));
        physicsState.linkenvisuals$setVerticalSpeed((float) itemEntity.getVelocity().y);
        physicsState.linkenvisuals$setYRotation((float) Math.toRadians(itemEntity.getYaw()));
        physicsState.linkenvisuals$setAdditionalOffset(false);

        if (itemPhysics != null && itemPhysics.isToggled()) {
            itemPhysics.updateRenderState(itemEntity, state, physicsState);
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void linkenvisuals$renderItemPhysic(ItemEntityRenderState state, MatrixStack matrices,
                                                net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                                net.minecraft.client.render.state.CameraRenderState cameraState,
                                                CallbackInfo ci) {
        ItemPhysics itemPhysics = linkenvisuals$getItemPhysics();
        if (itemPhysics == null || !itemPhysics.isToggled() || state.itemRenderState.isEmpty() || state.age < 1.0f) {
            return;
        }

        IItemPhysicsRenderState physicsState = (IItemPhysicsRenderState) state;
        matrices.push();
        random.setSeed(state.seed);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) Math.PI / 2.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(physicsState.linkenvisuals$getYRotation()));
        if (state.age != 0.0F) {
            matrices.translate(0.0, itemPhysics.getGroundOffsetY(state),
                    itemPhysics.getGroundOffsetZ(state, physicsState));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(physicsState.linkenvisuals$getXRotation()));
        }
        itemPhysics.applyTiltAndScale(matrices, state, physicsState);

        net.minecraft.util.math.Box box = state.itemRenderState.getModelBoundingBox();
        ItemEntityRenderer.render(matrices, queue, state.light, state, random, box);
        matrices.pop();
        ci.cancel();
    }

    protected float getShadowRadius(ItemEntityRenderState state) {
        ItemPhysics itemPhysics = linkenvisuals$getItemPhysics();
        float originalRadius = ((IEntityRendererShadow) this).getShadowRadiusValue();
        if (itemPhysics == null) {
            return originalRadius;
        }
        return itemPhysics.getShadowRadius(originalRadius);
    }

    protected float getShadowOpacity(ItemEntityRenderState state) {
        ItemPhysics itemPhysics = linkenvisuals$getItemPhysics();
        float originalOpacity = ((IEntityRendererShadow) this).getShadowOpacityValue();
        if (itemPhysics == null) {
            return originalOpacity;
        }
        return itemPhysics.getShadowOpacity(originalOpacity);
    }
}
