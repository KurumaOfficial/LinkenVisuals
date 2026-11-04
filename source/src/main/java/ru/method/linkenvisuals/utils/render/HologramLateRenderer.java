package ru.method.linkenvisuals.utils.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import ru.method.linkenvisuals.api.events.impl.EventRenderWorld;

/**
 * 1.21.11 port shim. The donor cancelled vanilla text-display rendering in a mixin and
 * replayed the renderer after the world pass because the old immediate buffer did not sort
 * translucency. The 1.21.11 {@code OrderedRenderCommandQueue} batches and sorts translucent
 * geometry itself, so the vanilla pass is correct now. Queue/flush stay as safe no-ops for
 * compatibility with the (not yet ported) mixin call sites.
 */
public final class HologramLateRenderer {

    public static volatile boolean replaying;

    public static void queue(DisplayEntityRenderer.TextDisplayEntityRenderer renderer,
                             TextDisplayEntityRenderState source, MatrixStack matrices, int light, float progress) {
        // Vanilla queue sorting handles text displays; nothing to defer.
    }

    @EventHandler
    public void onRenderWorld(EventRenderWorld event) {
        // no-op
    }
}
