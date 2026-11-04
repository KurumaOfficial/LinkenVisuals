package ru.method.linkenvisuals.api.accessors;

public interface IItemPhysicsRenderState {

    boolean linkenvisuals$isOnGround();

    void linkenvisuals$setOnGround(boolean onGround);

    float linkenvisuals$getHorizontalSpeed();

    void linkenvisuals$setHorizontalSpeed(float horizontalSpeed);

    float linkenvisuals$getVerticalSpeed();

    void linkenvisuals$setVerticalSpeed(float verticalSpeed);

    float linkenvisuals$getXRotation();

    void linkenvisuals$setXRotation(float xRotation);

    float linkenvisuals$getYRotation();

    void linkenvisuals$setYRotation(float yRotation);

    boolean linkenvisuals$hasAdditionalOffset();

    void linkenvisuals$setAdditionalOffset(boolean additionalOffset);
}
