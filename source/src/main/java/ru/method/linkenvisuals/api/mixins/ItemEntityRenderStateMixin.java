package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.method.linkenvisuals.api.accessors.IItemPhysicsRenderState;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements IItemPhysicsRenderState {

    @Unique
    private boolean linkenvisuals$onGround;

    @Unique
    private float linkenvisuals$horizontalSpeed;

    @Unique
    private float linkenvisuals$verticalSpeed;

    @Unique
    private float linkenvisuals$xRotation;

    @Unique
    private float linkenvisuals$yRotation;

    @Unique
    private boolean linkenvisuals$additionalOffset;

    @Override
    public boolean linkenvisuals$isOnGround() {
        return linkenvisuals$onGround;
    }

    @Override
    public void linkenvisuals$setOnGround(boolean onGround) {
        this.linkenvisuals$onGround = onGround;
    }

    @Override
    public float linkenvisuals$getHorizontalSpeed() {
        return linkenvisuals$horizontalSpeed;
    }

    @Override
    public void linkenvisuals$setHorizontalSpeed(float horizontalSpeed) {
        this.linkenvisuals$horizontalSpeed = horizontalSpeed;
    }

    @Override
    public float linkenvisuals$getVerticalSpeed() {
        return linkenvisuals$verticalSpeed;
    }

    @Override
    public void linkenvisuals$setVerticalSpeed(float verticalSpeed) {
        this.linkenvisuals$verticalSpeed = verticalSpeed;
    }

    @Override
    public float linkenvisuals$getXRotation() {
        return linkenvisuals$xRotation;
    }

    @Override
    public void linkenvisuals$setXRotation(float xRotation) {
        this.linkenvisuals$xRotation = xRotation;
    }

    @Override
    public float linkenvisuals$getYRotation() {
        return linkenvisuals$yRotation;
    }

    @Override
    public void linkenvisuals$setYRotation(float yRotation) {
        this.linkenvisuals$yRotation = yRotation;
    }

    @Override
    public boolean linkenvisuals$hasAdditionalOffset() {
        return linkenvisuals$additionalOffset;
    }

    @Override
    public void linkenvisuals$setAdditionalOffset(boolean additionalOffset) {
        this.linkenvisuals$additionalOffset = additionalOffset;
    }
}
