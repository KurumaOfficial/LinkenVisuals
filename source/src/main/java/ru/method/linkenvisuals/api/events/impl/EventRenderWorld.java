package ru.method.linkenvisuals.api.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.method.linkenvisuals.api.events.Event;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;

@AllArgsConstructor
@Getter
public class EventRenderWorld extends Event {
    private final RenderTickCounter tickCounter;
    // Мировая матрица (камера в начале координат). Постится ПОСЛЕ прохода FogBlur, поэтому то, что
    // рисуется здесь (косметик-эффекты: нимб/сферы/шлейф), не попадает под блюр.
    private final MatrixStack matrixStack;
}

