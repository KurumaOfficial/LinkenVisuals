package ru.method.linkenvisuals.api.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import ru.method.linkenvisuals.api.events.Event;

@AllArgsConstructor
@Getter
public class EventStartBreakBlock extends Event {
    private final BlockPos pos;
    private final Direction direction;
}
