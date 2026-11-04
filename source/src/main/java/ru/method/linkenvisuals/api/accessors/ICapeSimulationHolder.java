package ru.method.linkenvisuals.api.accessors;

import ru.method.linkenvisuals.cosmetics.wave.CapeSimulation;

/** Игрок хранит состояние физики своего плаща (см. PlayerEntityCapeMixin). */
public interface ICapeSimulationHolder {

    CapeSimulation linkenvisuals$capeSimulation();
}
