package ru.method.linkenvisuals.modules.settings.impl;

import ru.method.linkenvisuals.modules.settings.Setting;

import java.util.function.Supplier;

public class ActionSetting extends Setting<Runnable> {
    private final String labelKey;

    public ActionSetting(String name, String labelKey, Runnable action) {
        super(name, action);
        this.labelKey = labelKey;
    }

    public ActionSetting(String name, String labelKey, Runnable action, Supplier<Boolean> visible) {
        super(name, action, visible);
        this.labelKey = labelKey;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public void run() {
        Runnable action = getValue();
        if (action != null) {
            action.run();
        }
    }
}
