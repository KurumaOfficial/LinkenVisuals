package ru.method.linkenvisuals.api.events.impl.rotations;

import ru.method.linkenvisuals.api.events.Event;
import lombok.*;

@AllArgsConstructor @Getter @Setter
public class EventTrace extends Event {
    private float yaw, pitch;
}
