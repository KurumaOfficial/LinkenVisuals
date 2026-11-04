package ru.method.linkenvisuals.modules.impl.misc;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.api.accessors.IItemPhysicsRenderState;
import ru.method.linkenvisuals.api.mixins.accessors.IEntityMovementMultiplier;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

public class ItemPhysics extends Module {
    public static final float RANDOM_Y_OFFSET_SCALE = (float) (0.05D / (Math.PI * 2.0D));

    private final NumberSetting rotationSpeed = new NumberSetting("settings.itemphysics.rotation_speed", 1.0f, 0f, 8f, 0.1f);
    private final NumberSetting scale = new NumberSetting("settings.itemphysics.scale", 1.0f, 0.5f, 1.5f, 0.05f);

    public ItemPhysics() {
        super("ItemPhysics", Category.Visuals);
    }

    public void updateRenderState(ItemEntity itemEntity, ItemEntityRenderState state, IItemPhysicsRenderState physicsState) {
        float rotateBy = getRotateBy();
        Vec3d movementMultiplier = ((IEntityMovementMultiplier) itemEntity).linkenvisuals$getMovementMultiplier();
        if (movementMultiplier != null && movementMultiplier.lengthSquared() > 0.0D) {
            rotateBy *= (float) movementMultiplier.x * 0.2F;
        }

        boolean onGround = itemEntity.isOnGround();
        if (!onGround) {
            rotateBy *= 2.0F;
            if (itemEntity.isSubmergedInWater()) {
                rotateBy *= 0.35F;
            } else if (itemEntity.isInLava()) {
                rotateBy *= 0.22F;
            }
        }

        boolean hasDepth = state.itemRenderState.isSideLit();
        float xRot = itemEntity.getPitch();
        if (hasDepth) {
            if (!onGround) {
                xRot += rotateBy;
            }
        } else {
            if (onGround) {
                xRot = 0.0F;
            } else {
                xRot += rotateBy;
            }
        }

        itemEntity.setPitch(xRot);
        physicsState.linkenvisuals$setXRotation(xRot);
        physicsState.linkenvisuals$setYRotation((float) Math.toRadians(itemEntity.getYaw()));
        physicsState.linkenvisuals$setAdditionalOffset(requiresAdditionalOffset(itemEntity));
    }

    public float getScale() {
        return scale.getValue();
    }

    public void applyTiltAndScale(MatrixStack matrices, ItemEntityRenderState state, IItemPhysicsRenderState physicsState) {
        float itemScale = scale.getValue();
        matrices.scale(itemScale, itemScale, itemScale);
    }

    public float getGroundOffsetZ(ItemEntityRenderState state, IItemPhysicsRenderState physicsState) {
        if (state.itemRenderState.isSideLit()) return -0.08F;
        float base = physicsState.linkenvisuals$hasAdditionalOffset() ? -0.14F : -0.04F;
        return base - state.uniqueOffset * RANDOM_Y_OFFSET_SCALE;
    }

    public float getGroundOffsetY(ItemEntityRenderState state) {
        return state.itemRenderState.isSideLit() ? -0.2F : 0.0F;
    }

    private float getRotateBy() {
        if (mc == null || mc.isPaused()) {
            return 0.0F;
        }
        return (float) (mc.getRenderTime() / 200_000_000.0D) * rotationSpeed.getValue();
    }

    private boolean requiresAdditionalOffset(ItemEntity itemEntity) {
        BlockState current = itemEntity.getEntityWorld().getBlockState(itemEntity.getBlockPos());
        if (needsAdditionalOffset(current)) {
            return true;
        }
        BlockState below = itemEntity.getEntityWorld().getBlockState(itemEntity.getBlockPos().down());
        return needsAdditionalOffset(below);
    }

    private boolean needsAdditionalOffset(BlockState state) {
        return state.isOf(Blocks.SNOW) || state.isOf(Blocks.SOUL_SAND) || state.isOf(Blocks.MUD);
    }

    public float getShadowRadius(float originalRadius) {
        return isToggled() ? 0.0f : originalRadius;
    }

    public float getShadowOpacity(float originalOpacity) {
        return isToggled() ? 0.0f : originalOpacity;
    }
}
