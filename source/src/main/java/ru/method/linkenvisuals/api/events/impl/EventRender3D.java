package ru.method.linkenvisuals.api.events.impl;

import ru.method.linkenvisuals.api.events.Event;
import lombok.Getter;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class EventRender3D extends Event {
    @Getter
    public static class Game extends Event {
        private final RenderTickCounter tickCounter;
        private final MatrixStack matrixStack;
        /** 1.21.11 entity rendering is deferred: pets submit into this queue. */
        private final OrderedRenderCommandQueue commandQueue;
        private final CameraRenderState cameraState;
        private final VertexConsumerProvider consumers;

        public Game(RenderTickCounter tickCounter, MatrixStack matrixStack) {
            this(tickCounter, matrixStack, null, null, null);
        }

        public Game(RenderTickCounter tickCounter, MatrixStack matrixStack,
                    OrderedRenderCommandQueue commandQueue,
                    CameraRenderState cameraState,
                    VertexConsumerProvider consumers) {
            this.tickCounter = tickCounter;
            this.matrixStack = matrixStack;
            this.commandQueue = commandQueue;
            this.cameraState = cameraState;
            this.consumers = consumers;
        }
    }

    @lombok.AllArgsConstructor
    @Getter
    public static class World extends Event {
        private final Camera camera;
        private final Matrix4f positionMatrix;
        private final RenderTickCounter tickCounter;
    }
}
