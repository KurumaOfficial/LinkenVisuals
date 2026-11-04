package ru.method.linkenvisuals.screen.windowgui;

import ru.method.linkenvisuals.modules.settings.api.Nameable;

public enum WindowGuiStyle implements Nameable {
    PANEL("Panel"),
    LINK("Link");

    private final String name;

    WindowGuiStyle(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
