package ru.method.linkenvisuals.api.mixins;


import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.method.linkenvisuals.api.accessors.IHitColorRenderState;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements IHitColorRenderState {

    @Unique
    private boolean linkenvisuals$isPlayer;

    @Unique
    private float linkenvisuals$hitColorStrength;

    @Override
    public boolean linkenvisuals$isPlayer() {
        return linkenvisuals$isPlayer;
    }

    @Override
    public void linkenvisuals$setPlayer(boolean player) {
        this.linkenvisuals$isPlayer = player;
    }

    @Override
    public float linkenvisuals$getHitColorStrength() {
        return linkenvisuals$hitColorStrength;
    }

    @Override
    public void linkenvisuals$setHitColorStrength(float strength) {
        this.linkenvisuals$hitColorStrength = strength;
    }
}
