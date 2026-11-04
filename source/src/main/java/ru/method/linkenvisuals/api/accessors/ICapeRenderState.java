package ru.method.linkenvisuals.api.accessors;

import ru.method.linkenvisuals.cosmetics.wave.CapeSimulation;

/**
 * Снимок физики плаща в стейте рендера игрока. Фича-рендерер плаща в 1.21.4 получает только
 * {@code PlayerEntityRenderState} и не видит сущность, поэтому симуляцию (и то, что от игрока
 * нужно для позы) прокидываем сюда в {@code updateRenderState}.
 */
public interface ICapeRenderState {

    CapeSimulation linkenvisuals$capeSimulation();

    void linkenvisuals$setCapeSimulation(CapeSimulation simulation);

    float linkenvisuals$capeTickDelta();

    void linkenvisuals$setCapeTickDelta(float tickDelta);

    boolean linkenvisuals$capeCrouching();

    void linkenvisuals$setCapeCrouching(boolean crouching);
}
