package ru.method.linkenvisuals.api.events.impl;

import ru.method.linkenvisuals.api.events.Event;
import ru.method.linkenvisuals.modules.settings.Setting;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter
public class EventSettingChange extends Event {
    private final Setting<?> setting;
}
