package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.GpuSampler;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.JumpCirclesModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * JumpCircles renderer: authentic 1:1 Linken expanding textured rings on jump.
 * Renders glowing textured quads flat on the ground using client/other/circle.png
 * via the proven additive world billboard pipeline.
 */
public final class JumpCirclesRenderer {
    private static final Identifier CIRCLE_TEXTURE = LinkenVisuals.id("client/other/circle.png");
    private static final float GLOW_SCALE = 1.15f;
    private static final long FADE_IN_MS = 500L;
    private static final long FADE_OUT_MS = 500L;
    private static final long MIN_LIFETIME_MS = 600L;

    // 2 quads per circle (inner + glow) × 6 vertices × 24 bytes = 288 bytes per circle.
    // 16KB static direct buffer handles up to 56 simultaneous circles with zero allocations.
    private static final ByteBuffer REUSABLE_BUFFER =
        ByteBuffer.allocateDirect(16384).order(ByteOrder.nativeOrder());

    private record Circle(Vec3d pos, long spawnTime) {}

    private static final List<Circle> circles = new ArrayList<>();
    private static boolean wasOnGround = true;

    private JumpCirclesRenderer() {
    }

    public static void clear() {
        circles.clear();
        wasOnGround = true;
    }

    /** Called each client tick to detect jumps. */
    public static void tick() {
        JumpCirclesModule module = Modules.jumpCircles();
        if (module == null || !module.enabled()) {
            if (!circles.isEmpty()) {
                circles.clear();
            }
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        boolean onGround = client.player.isOnGround();
        boolean jumpPressed = client.options.jumpKey.isPressed();
        if (wasOnGround && !onGround && jumpPressed) {
            Vec3d pos = new Vec3d(client.player.getX(),
                client.player.getY() + 0.015, client.player.getZ());
            circles.add(new Circle(pos, System.currentTimeMillis()));
        }
        wasOnGround = onGround;
    }

    /** Called at END_MAIN to render rings. */
    public static void render(WorldRenderContext context) {
        JumpCirclesModule module = Modules.jumpCircles();
        if (module == null || !module.enabled() || circles.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Camera camera = client.gameRenderer.getCamera();
        if (main == null || main.getColorAttachmentView() == null
            || main.getDepthAttachmentView() == null || camera == null) {
            return;
        }

        AbstractTexture texture = client.getTextureManager().getTexture(CIRCLE_TEXTURE);
        if (texture == null || texture.getGlTextureView() == null) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();
        long now = System.currentTimeMillis();
        long lifetimeMs = Math.max(MIN_LIFETIME_MS, (long) (module.lifetime.value() * 1000f));
        float baseRadius = Math.max(0.05f, module.size.value());

        // Clean expired circles.
        Iterator<Circle> it = circles.iterator();
        while (it.hasNext()) {
            Circle c = it.next();
            long age = now - c.spawnTime();
            if (age >= lifetimeMs) {
                it.remove();
            }
        }
        if (circles.isEmpty()) {
            return;
        }

        int theme = dev.linkenvisuals.ui.Theme.accent();
        int baseR = (theme >> 16) & 0xFF;
        int baseG = (theme >> 8) & 0xFF;
        int baseB = theme & 0xFF;

        int requiredBytes = circles.size() * 2 * 6 * 24;
        boolean heapAlloc = requiredBytes > REUSABLE_BUFFER.capacity();
        ByteBuffer data = heapAlloc ? MemoryUtil.memAlloc(requiredBytes) : REUSABLE_BUFFER;
        try {
            data.clear();
            int vertices = 0;

            for (Circle c : circles) {
                long age = now - c.spawnTime();
                float progress = MathHelper.clamp((float) age / lifetimeMs, 0.0f, 1.0f);
                float radius = baseRadius * (0.6f + progress * 0.9f);

                float alpha;
                if (age < FADE_IN_MS) {
                    alpha = (float) age / FADE_IN_MS;
                } else if (age > lifetimeMs - FADE_OUT_MS) {
                    alpha = (float) (lifetimeMs - age) / FADE_OUT_MS;
                } else {
                    alpha = 1.0f;
                }
                if (alpha <= 0.01f) {
                    continue;
                }

                int a255 = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
                int ringColor = (a255 << 24) | (baseR << 16) | (baseG << 8) | baseB;

                // Glow color: fade toward white like donor
                int glowA = MathHelper.clamp(Math.round(a255 * 0.45f), 0, 255);
                int gr = Math.min(255, Math.round(baseR * 0.65f + 89));
                int gg = Math.min(255, Math.round(baseG * 0.65f + 89));
                int gb = Math.min(255, Math.round(baseB * 0.65f + 89));
                int glowColor = (glowA << 24) | (gr << 16) | (gg << 8) | gb;

                float cx = (float) (c.pos().x - camPos.x);
                float cy = (float) (c.pos().y - camPos.y);
                float cz = (float) (c.pos().z - camPos.z);

                // Inner ring quad
                putTexturedQuad(data, cx, cy, cz, radius, ringColor);
                // Glow outer quad
                putTexturedQuad(data, cx, cy, cz, radius * GLOW_SCALE, glowColor);

                vertices += 12;
            }

            data.flip();
            if (vertices == 0) {
                return;
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuBuffer verts = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:jumpcircles", GpuBuffer.USAGE_VERTEX, data);
            GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
            GpuSampler sampler = texture.getSampler() != null
                ? texture.getSampler()
                : RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

            try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:jumpcircles",
                main.getColorAttachmentView(), OptionalInt.empty(),
                main.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(ParticlesPipelines.BILLBOARD);
                Pipelines.bindWorldUniforms(pass, transforms);
                pass.bindTexture("Sampler0", texture.getGlTextureView(), sampler);
                pass.setVertexBuffer(0, verts);
                pass.draw(0, vertices);
            } finally {
                verts.close();
            }
        } finally {
            if (heapAlloc) {
                MemoryUtil.memFree(data);
            }
        }
    }

    private static void putTexturedQuad(ByteBuffer data, float cx, float cy, float cz,
                                        float radius, int argb) {
        float x0 = cx - radius;
        float z0 = cz - radius;
        float x1 = cx + radius;
        float z1 = cz + radius;

        // Quad in XZ plane (horizontal on ground):
        // (x0, z0) -> UV (0, 0)
        // (x1, z0) -> UV (1, 0)
        // (x1, z1) -> UV (1, 1)
        // (x0, z1) -> UV (0, 1)

        putVertex(data, x0, cy, z0, 0.0f, 0.0f, argb);
        putVertex(data, x1, cy, z0, 1.0f, 0.0f, argb);
        putVertex(data, x1, cy, z1, 1.0f, 1.0f, argb);

        putVertex(data, x0, cy, z0, 0.0f, 0.0f, argb);
        putVertex(data, x1, cy, z1, 1.0f, 1.0f, argb);
        putVertex(data, x0, cy, z1, 0.0f, 1.0f, argb);
    }

    private static void putVertex(ByteBuffer data, float x, float y, float z,
                                  float u, float v, int argb) {
        data.putFloat(x).putFloat(y).putFloat(z);
        data.putFloat(u).putFloat(v);
        data.put((byte) ((argb >> 16) & 0xFF));
        data.put((byte) ((argb >> 8) & 0xFF));
        data.put((byte) (argb & 0xFF));
        data.put((byte) ((argb >>> 24) & 0xFF));
    }

    public static void init() {
    }
}
