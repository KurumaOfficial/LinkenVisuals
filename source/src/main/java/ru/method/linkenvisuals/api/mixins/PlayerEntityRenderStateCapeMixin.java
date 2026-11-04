package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.method.linkenvisuals.api.accessors.ICapeRenderState;
import ru.method.linkenvisuals.cosmetics.wave.CapeSimulation;

/** Снимок физики плаща для фича-рендерера, который сущность уже не видит (только стейт). */
@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateCapeMixin implements ICapeRenderState {

    @Unique
    private CapeSimulation linkenvisuals$capeSimulation;

    @Unique
    private float linkenvisuals$capeTickDelta;

    @Unique
    private boolean linkenvisuals$capeCrouching;

    @Override
    public CapeSimulation linkenvisuals$capeSimulation() {
        return linkenvisuals$capeSimulation;
    }

    @Override
    public void linkenvisuals$setCapeSimulation(CapeSimulation simulation) {
        this.linkenvisuals$capeSimulation = simulation;
    }

    @Override
    public float linkenvisuals$capeTickDelta() {
        return linkenvisuals$capeTickDelta;
    }

    @Override
    public void linkenvisuals$setCapeTickDelta(float tickDelta) {
        this.linkenvisuals$capeTickDelta = tickDelta;
    }

    @Override
    public boolean linkenvisuals$capeCrouching() {
        return linkenvisuals$capeCrouching;
    }

    @Override
    public void linkenvisuals$setCapeCrouching(boolean crouching) {
        this.linkenvisuals$capeCrouching = crouching;
    }
}
