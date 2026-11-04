package dev.linkenvisuals.mixin;

import dev.linkenvisuals.render.state.RudyRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerEntityRenderStateMixin implements RudyRenderState {
    @Unique
    private boolean linkenvisuals$rudy;

    @Unique
    private String linkenvisuals$customCharacter = "Default";

    @Unique
    private boolean linkenvisuals$airborne;

    @Override
    public boolean linkenvisuals$isRudy() {
        return linkenvisuals$rudy;
    }

    @Override
    public void linkenvisuals$setRudy(boolean rudy) {
        linkenvisuals$rudy = rudy;
    }

    @Override
    public String linkenvisuals$getCustomCharacter() {
        return linkenvisuals$customCharacter;
    }

    @Override
    public void linkenvisuals$setCustomCharacter(String character) {
        linkenvisuals$customCharacter = character != null ? character : "Default";
    }

    @Override
    public boolean linkenvisuals$isAirborne() {
        return linkenvisuals$airborne;
    }

    @Override
    public void linkenvisuals$setAirborne(boolean airborne) {
        linkenvisuals$airborne = airborne;
    }
}
