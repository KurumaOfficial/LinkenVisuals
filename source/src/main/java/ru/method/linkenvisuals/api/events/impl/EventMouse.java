package ru.method.linkenvisuals.api.events.impl;

import ru.method.linkenvisuals.api.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter
public class EventMouse extends Event {
    private int button, action;

    public void consume() {
        cancel();
    }
}
