package dev.linkenvisuals.render.state;

/**
 * Duck interface implemented on {@code PlayerEntityRenderState} by
 * {@code PlayerEntityRenderStateMixin}: the local player has a custom character selected.
 * The render state is rebuilt every frame, so the character selection lives on the state (not on the entity).
 */
public interface RudyRenderState {
    boolean linkenvisuals$isRudy();

    void linkenvisuals$setRudy(boolean rudy);

    String linkenvisuals$getCustomCharacter();

    void linkenvisuals$setCustomCharacter(String character);

    boolean linkenvisuals$isAirborne();

    void linkenvisuals$setAirborne(boolean airborne);
}
