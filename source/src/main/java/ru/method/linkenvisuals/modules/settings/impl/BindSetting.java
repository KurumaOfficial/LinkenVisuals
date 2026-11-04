package ru.method.linkenvisuals.modules.settings.impl;


import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Bind;

import java.util.function.Supplier;


public class BindSetting extends Setting<Bind> {

    public BindSetting(String name, Bind defaultValue) {
        super(name, defaultValue);
    }

    public BindSetting(String name, Bind defaultValue, Supplier<Boolean> visible) {
        super(name, defaultValue, visible);
    }
}
