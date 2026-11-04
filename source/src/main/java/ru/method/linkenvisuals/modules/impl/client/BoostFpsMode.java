package ru.method.linkenvisuals.modules.impl.client;

import ru.method.linkenvisuals.modules.settings.api.Nameable;

/**
 * Режим оптимизации фона при открытом Link GUI.
 * DEFAULT — ничего не меняется, мир рендерится как обычно (без оптимизации).
 * MIDDLE  — мир замораживается в размытый снимок (виден как фон) → высокий FPS.
 */
public enum BoostFpsMode implements Nameable {
    DEFAULT("Default"),
    MIDDLE("Middle");

    private final String name;

    BoostFpsMode(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
