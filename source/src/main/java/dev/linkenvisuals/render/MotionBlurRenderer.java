package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.MotionBlurModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;

/**
 * Motion blur (Phase 5.4): frame blending (persistence of vision, like OptiFine).
 * Each frame mixes the current world frame with the accumulated previous one:
 * {@code display = mix(current, prev, strength)}. Runs at {@code END_MAIN}
 * (after world, before GUI) so only the world smears; HUD stays sharp.
 *
 * <p>No raw GL: uses {@link BlitPipeline} for copies and a RenderPipeline for
 * the blend. Two framebuffers (accum + temp) because one FBO cannot be read
 * and written simultaneously.
 */
public final class MotionBlurRenderer {
    private static SimpleFramebuffer accum;
    private static SimpleFramebuffer temp;
    private static boolean primed;

    private static GpuBuffer uniforms;
    private static GpuBuffer fullscreenTriangle;
    private static final ByteBuffer UNIFORM_DATA =
        ByteBuffer.allocateDirect(MotionBlurPipeline.UNIFORM_SIZE).order(ByteOrder.nativeOrder());

    private MotionBlurRenderer() {
    }

    public static void onEndMain(WorldRenderContext context) {
        MotionBlurModule module = Modules.motionBlur();
        if (module == null || !module.enabled()) {
            return;
        }
        float strength = module.strength.value() / 100.0f;
        if (strength <= 0.001f) {
            return;
        }
        render(Math.min(0.95f, strength));
    }

    private static void render(float strength) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null) {
            return;
        }
        ensureFramebuffers(main);
        if (accum == null || temp == null) {
            return;
        }

        // First frame (or after resize): prime the accumulator with the current
        // frame, otherwise the first blend would mix the world with black.
        if (!primed) {
            BlitPipeline.run(main.getColorAttachmentView(), accum.getColorAttachmentView());
            primed = true;
        }

        ensureUniforms();
        ensureTriangle();
        writeUniforms(strength);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:motion_blur",
            temp.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(MotionBlurPipeline.PIPELINE);
            pass.setUniform("MotionBlur", uniforms);
            pass.bindTexture("Sampler0", main.getColorAttachmentView(),
                RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.bindTexture("Sampler1", accum.getColorAttachmentView(),
                RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.setVertexBuffer(0, fullscreenTriangle);
            pass.draw(0, 3);
        }

        // temp -> main (display) and temp -> accum (save as "previous" for next frame).
        BlitPipeline.run(temp.getColorAttachmentView(), main.getColorAttachmentView());
        BlitPipeline.run(temp.getColorAttachmentView(), accum.getColorAttachmentView());
    }

    private static void ensureFramebuffers(Framebuffer main) {
        int width = Math.max(1, main.textureWidth);
        int height = Math.max(1, main.textureHeight);
        if (accum == null || accum.textureWidth != width || accum.textureHeight != height) {
            delete(accum);
            delete(temp);
            accum = new SimpleFramebuffer("linkenvisuals_motion_blur_accum", width, height, false);
            temp = new SimpleFramebuffer("linkenvisuals_motion_blur_temp", width, height, false);
            primed = false;
        }
    }

    private static void ensureUniforms() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(() -> "linkenvisuals:motion_blur_uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, MotionBlurPipeline.UNIFORM_SIZE);
        }
    }

    private static void ensureTriangle() {
        if (fullscreenTriangle != null) {
            return;
        }
        // Fullscreen triangle: positions in NDC, UVs 0..1.
        ByteBuffer data = MemoryUtil.memAlloc(3 * 20);
        try {
            put(data, -1.0f, -1.0f, 0.0f, 0.0f);
            put(data, 3.0f, -1.0f, 2.0f, 0.0f);
            put(data, -1.0f, 3.0f, 0.0f, 2.0f);
            data.flip();
            fullscreenTriangle = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:motion_blur_triangle", GpuBuffer.USAGE_VERTEX, data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void put(ByteBuffer data, float x, float y, float u, float v) {
        data.putFloat(x).putFloat(y).putFloat(0.0f);
        data.putFloat(u).putFloat(v);
    }

    private static void writeUniforms(float strength) {
        UNIFORM_DATA.clear();
        UNIFORM_DATA.putFloat(strength); // @0
        UNIFORM_DATA.putFloat(0.0f); // @4 pad
        UNIFORM_DATA.putFloat(0.0f); // @8 pad
        UNIFORM_DATA.putFloat(0.0f); // @12 pad
        UNIFORM_DATA.flip();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniforms.slice(), UNIFORM_DATA);
    }

    private static void delete(SimpleFramebuffer fb) {
        if (fb != null) {
            try {
                fb.delete();
            } catch (Throwable t) {
                dev.linkenvisuals.LinkenVisuals.LOGGER.warn(
                    "[Visuals Modification] MotionBlur framebuffer release failed: {}", t.getMessage());
            }
        }
    }

    /** Frees framebuffers and buffers. Idempotent. */
    public static void shutdown() {
        delete(accum);
        delete(temp);
        accum = null;
        temp = null;
        primed = false;
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
        if (fullscreenTriangle != null) {
            fullscreenTriangle.close();
            fullscreenTriangle = null;
        }
    }
}
