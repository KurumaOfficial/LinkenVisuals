package ru.method.linkenvisuals.api.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.method.linkenvisuals.api.events.Event;

@AllArgsConstructor
@Getter
public class EventMouseScroll extends Event {
    private double horizontalAmount;
    private double verticalAmount;

    public void consume() {
        cancel();
    }
}
