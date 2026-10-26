package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.WaypointsModule;
import dev.linkenvisuals.waypoint.Waypoint;
import dev.linkenvisuals.waypoint.WaypointStorage;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Waypoints renderer (Phase 5.7): draws markers at waypoint positions.
 * Storage lives in {@link WaypointStorage} and is managed by the client
 * {@code /way} command (donor {@code WaypointCommand} semantics).
 */
public final class WaypointsRenderer {
    private WaypointsRenderer() {
    }

    /** Called at END_MAIN to render markers. */
    public static void render(WorldRenderContext context) {
        WaypointsModule module = Modules.waypoints();
        if (module == null || !module.enabled()) {
            return;
        }
        List<Waypoint> waypoints = WaypointStorage.store().view();
        if (waypoints.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Camera camera = client.gameRenderer.getCamera();
        if (main == null || main.getColorAttachmentView() == null
            || main.getDepthAttachmentView() == null || camera == null) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();
        int totalVerts = waypoints.size() * 2; // vertical line per waypoint
        ByteBuffer data = MemoryUtil.memAlloc(totalVerts * 16);
        try {
            int cyanArgb = 0xE600FFFF; // 0.9 alpha, cyan
            for (Waypoint w : waypoints) {
                float x = (float) (w.x() - camPos.x);
                float y0 = (float) (w.y() - camPos.y);
                float y1 = y0 + 3.0f; // 3-block tall marker
                float z = (float) (w.z() - camPos.z);
                // Cyan marker.
                putVertex(data, x, y0, z, cyanArgb);
                putVertex(data, x, y1, z, cyanArgb);
            }
            data.flip();
            if (data.remaining() == 0) {
                return;
            }

            int vertices = data.remaining() / 16;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuBuffer verts = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:waypoints", GpuBuffer.USAGE_VERTEX, data);
            GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
            try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:waypoints",
                main.getColorAttachmentView(), OptionalInt.empty(),
                main.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(BlockOverlayPipelines.LINES);
                Pipelines.bindWorldUniforms(pass, transforms);
                pass.setVertexBuffer(0, verts);
                pass.draw(0, vertices);
            } finally {
                verts.close();
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void putVertex(ByteBuffer data, float x, float y, float z, int argb) {
        data.putFloat(x).putFloat(y).putFloat(z);
        data.put((byte) ((argb >> 16) & 0xFF));
        data.put((byte) ((argb >> 8) & 0xFF));
        data.put((byte) (argb & 0xFF));
        data.put((byte) ((argb >>> 24) & 0xFF));
    }

    public static void init() {
    }
}
