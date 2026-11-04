package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.linkenvisuals.render.BlitPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.Wrapper;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;

/**
 * FogBlur (donor {@code FogBlurRenderer}) rebuilt on the 1.21.11 render graph.
 *
 * <p>The donor implementation compiled its own GL programs and attached
 * render-target textures to a hand-made FBO. In 1.21.11 the world is drawn by a
 * frame graph; opening raw FBOs after the main pass and sampling their
 * attachments through custom GLSL left every attachment black, and the final
 * composite covered the world with black (only the HUD survived). This version
 * uses the supported path: {@link BlitPipeline} for copies, {@link RenderPass}
 * for the separable Gaussian and the depth-aware composite, and {@link SimpleFramebuffer}
 * for fullscreen targets. The shader math is the donor one.
 */
public final class FogBlurRenderer implements Wrapper {
    private static final float SQRT_2PI = 2.50662f;
    private static final long TIME_EPOCH = System.nanoTime();

    private static SimpleFramebuffer copy;
    private static SimpleFramebuffer tint;
    private static SimpleFramebuffer blurPing;
    private static SimpleFramebuffer blurPong;

    private static GpuBuffer blurUniforms;
    private static GpuBuffer compositeUniforms;
    private static GpuBuffer tintUniforms;
    private static GpuBuffer dummyVertex;

    private static final ByteBuffer BLUR_DATA =
        ByteBuffer.allocateDirect(FogBlurPipelines.BLUR_UNIFORM_SIZE).order(ByteOrder.nativeOrder());
    private static final ByteBuffer COMPOSITE_DATA =
        ByteBuffer.allocateDirect(FogBlurPipelines.COMPOSITE_UNIFORM_SIZE).order(ByteOrder.nativeOrder());
    private static final ByteBuffer TINT_DATA =
        ByteBuffer.allocateDirect(FogBlurPipelines.TINT_UNIFORM_SIZE).order(ByteOrder.nativeOrder());

    private static boolean initialized;
    private static boolean initAttempted;

    private FogBlurRenderer() {
    }

    public static void render(
            float strength,
            float distance,
            boolean linearSampling,
            boolean rainbow,
            float rainbowOpacity,
            float rainbowSaturation,
            float rainbowBrightness,
            Color fogColor
    ) {
        RenderSystem.assertOnRenderThread();
        if (!ensureInitialized()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client == null ? null : client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null || main.getDepthAttachmentView() == null) {
            return;
        }

        int width = Math.max(1, main.textureWidth);
        int height = Math.max(1, main.textureHeight);
        float safeStrength = Math.max(1.0f, Math.min(14.0f, strength));
        float blurScale = resolveBlurResolutionScale(safeStrength);
        ensureFramebuffers(width, height, blurScale);
        if (copy == null || blurPing == null || blurPong == null) {
            return;
        }

        int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            BlitPipeline.run(main.getColorAttachmentView(), copy.getColorAttachmentView());

            // Donor order: tint (rainbow / custom colour) FIRST, then blur the
            // tinted frame. Tinting after the blur left the rainbow noise sharp
            // and produced ugly hard-edged blobs across the sky/clouds.
            com.mojang.blaze3d.textures.GpuTextureView blurSource = copy.getColorAttachmentView();
            boolean tintEnabled = rainbow || (fogColor != null && fogColor.getAlpha() > 0);
            if (tintEnabled && tint != null) {
                writeTintUniforms(encoder, rainbow, rainbowOpacity, rainbowSaturation,
                    rainbowBrightness, fogColor, client);
                runTintPass(encoder, copy.getColorAttachmentView(), tint.getColorAttachmentView());
                blurSource = tint.getColorAttachmentView();
            }

            float gaussianScale = Math.min(1.0f, blurPing.textureWidth / (float) Math.max(1, width));
            float sigma = Math.max(1.0f, safeStrength * gaussianScale + (1.0f - gaussianScale) * 4.0f);
            float[] gaussian = calculateGaussian(sigma);
            int support = Math.max(1, Math.min(20, (int) Math.ceil(sigma * 2.2f)));

            GpuSampler sourceSampler = RenderSystem.getSamplerCache()
                .get(linearSampling ? FilterMode.LINEAR : FilterMode.NEAREST);
            writeBlurUniforms(encoder, 1.0f, 0.0f,
                1.0f / blurPing.textureWidth, 1.0f / blurPing.textureHeight,
                gaussian, support, linearSampling);
            runBlurPass(encoder, blurSource, blurPing.getColorAttachmentView(), sourceSampler);

            writeBlurUniforms(encoder, 0.0f, 1.0f,
                1.0f / blurPing.textureWidth, 1.0f / blurPing.textureHeight,
                gaussian, support, linearSampling);
            runBlurPass(encoder, blurPing.getColorAttachmentView(), blurPong.getColorAttachmentView(), sourceSampler);

            float near = 0.05f;
            float far = Math.max(1.0f, client.gameRenderer.getFarPlaneDistance());
            float minThreshold = Math.max(0.0f, 0.10f * distance / 100.0f);
            float maxThreshold = Math.max(minThreshold + 1.0e-4f, 0.28f * distance / 100.0f);
            // The tint is already baked into the blurred image; keep the
            // composite pass as a pure depth-masked blit.
            writeCompositeUniforms(encoder, near, far, minThreshold, maxThreshold,
                null, false, 0.0f, 0.0f, 0.0f, client);

            GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            try (RenderPass pass = encoder.createRenderPass(
                () -> "linkenvisuals:fog_blur_composite",
                main.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(FogBlurPipelines.COMPOSITE);
                pass.bindTexture("ColorSampler", blurPong.getColorAttachmentView(), linear);
                pass.bindTexture("DepthSampler", main.getDepthAttachmentView(), nearest);
                pass.setUniform("CompositeData", compositeUniforms);
                pass.setVertexBuffer(0, dummyVertex);
                pass.draw(0, 3);
            }
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.warn("[LinkenVisuals] FogBlur pass failed, releasing resources.", throwable);
            release();
        } finally {
            // RenderPass.close() always binds framebuffer 0. Restore whatever
            // target the caller (hand pass / GUI) was using before the effect.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static boolean ensureInitialized() {
        if (initialized) {
            return true;
        }
        if (initAttempted) {
            return false;
        }
        initAttempted = true;
        try {
            dummyVertex = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:fog_dummy_vertex", GpuBuffer.USAGE_VERTEX, 4);
            blurUniforms = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:fog_blur_uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                FogBlurPipelines.BLUR_UNIFORM_SIZE);
            compositeUniforms = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:fog_composite_uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                FogBlurPipelines.COMPOSITE_UNIFORM_SIZE);
            tintUniforms = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:fog_tint_uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                FogBlurPipelines.TINT_UNIFORM_SIZE);
            initialized = true;
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.error("[LinkenVisuals] Failed to initialize FogBlur buffers.", throwable);
            release();
            return false;
        }
        return true;
    }

    private static void ensureFramebuffers(int width, int height, float blurScale) {
        int blurWidth = Math.max(1, Math.round(width * blurScale));
        int blurHeight = Math.max(1, Math.round(height * blurScale));

        if (copy == null || copy.textureWidth != width || copy.textureHeight != height) {
            delete(copy);
            delete(tint);
            copy = new SimpleFramebuffer("linkenvisuals:fog_copy", width, height, false);
            tint = new SimpleFramebuffer("linkenvisuals:fog_tint", width, height, false);
        } else if (tint == null) {
            tint = new SimpleFramebuffer("linkenvisuals:fog_tint", width, height, false);
        }

        if (blurPing == null || blurPing.textureWidth != blurWidth || blurPing.textureHeight != blurHeight) {
            delete(blurPing);
            delete(blurPong);
            blurPing = new SimpleFramebuffer("linkenvisuals:fog_ping", blurWidth, blurHeight, false);
            blurPong = new SimpleFramebuffer("linkenvisuals:fog_pong", blurWidth, blurHeight, false);
        }
    }

    private static void runBlurPass(CommandEncoder encoder,
                                    com.mojang.blaze3d.textures.GpuTextureView source,
                                    com.mojang.blaze3d.textures.GpuTextureView target,
                                    GpuSampler sampler) {
        try (RenderPass pass = encoder.createRenderPass(
            () -> "linkenvisuals:fog_blur_pass", target, OptionalInt.empty())) {
            pass.setPipeline(FogBlurPipelines.BLUR);
            pass.bindTexture("Sampler0", source, sampler);
            pass.setUniform("BlurData", blurUniforms);
            pass.setVertexBuffer(0, dummyVertex);
            pass.draw(0, 3);
        }
    }

    private static void runTintPass(CommandEncoder encoder,
                                    com.mojang.blaze3d.textures.GpuTextureView source,
                                    com.mojang.blaze3d.textures.GpuTextureView target) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(
            () -> "linkenvisuals:fog_tint_pass", target, OptionalInt.empty())) {
            pass.setPipeline(FogBlurPipelines.TINT);
            pass.bindTexture("Sampler0", source, sampler);
            pass.setUniform("TintData", tintUniforms);
            pass.setVertexBuffer(0, dummyVertex);
            pass.draw(0, 3);
        }
    }

    private static void writeTintUniforms(CommandEncoder encoder,
                                          boolean rainbow,
                                          float rainbowOpacity,
                                          float rainbowSaturation,
                                          float rainbowBrightness,
                                          Color fogColor,
                                          MinecraftClient client) {
        float time = (System.nanoTime() - TIME_EPOCH) / 1_000_000_000.0f;
        float yaw = client.player == null ? 0.0f : client.player.getYaw();
        float pitch = client.player == null ? 0.0f : client.player.getPitch();
        int rgb = fogColor == null ? 0 : fogColor.getRGB();
        float opacity = rainbow ? Math.max(0.0f, Math.min(1.0f, rainbowOpacity))
            : (fogColor == null ? 0.0f : fogColor.getAlpha() / 255.0f);

        TINT_DATA.clear();
        // tintColor: rgb, opacity
        TINT_DATA.putFloat((rgb >> 16 & 0xFF) / 255.0f)
            .putFloat((rgb >> 8 & 0xFF) / 255.0f)
            .putFloat((rgb & 0xFF) / 255.0f)
            .putFloat(opacity);
        // params: rainbow, saturation, brightness, time
        TINT_DATA.putFloat(rainbow ? 1.0f : 0.0f)
            .putFloat(Math.max(0.0f, Math.min(1.0f, rainbowSaturation)))
            .putFloat(Math.max(0.0f, Math.min(1.0f, rainbowBrightness)))
            .putFloat(time);
        // view: yaw, pitch, unused, unused
        TINT_DATA.putFloat(yaw).putFloat(pitch).putFloat(0.0f).putFloat(0.0f);
        TINT_DATA.flip();
        encoder.writeToBuffer(tintUniforms.slice(), TINT_DATA);
    }

    private static void writeBlurUniforms(CommandEncoder encoder,
                                          float dirX, float dirY,
                                          float texelX, float texelY,
                                          float[] gaussian, int support,
                                          boolean linearSampling) {
        BLUR_DATA.clear();
        // params: direction.xy, texelSize.xy
        BLUR_DATA.putFloat(dirX).putFloat(dirY).putFloat(texelX).putFloat(texelY);
        // gauss: g0, g1, g2, support
        BLUR_DATA.putFloat(gaussian[0]).putFloat(gaussian[1]).putFloat(gaussian[2]).putFloat(support);
        // flags: linearSampling, alpha, unused, unused
        BLUR_DATA.putFloat(linearSampling ? 1.0f : 0.0f).putFloat(1.0f).putFloat(0.0f).putFloat(0.0f);
        BLUR_DATA.flip();
        encoder.writeToBuffer(blurUniforms.slice(), BLUR_DATA);
    }

    private static void writeCompositeUniforms(CommandEncoder encoder,
                                               float near, float far,
                                               float minThreshold, float maxThreshold,
                                               Color fogColor,
                                               boolean rainbow,
                                               float rainbowOpacity,
                                               float rainbowSaturation,
                                               float rainbowBrightness,
                                               MinecraftClient client) {
        float time = (System.nanoTime() - TIME_EPOCH) / 1_000_000_000.0f;
        float yaw = client.player == null ? 0.0f : client.player.getYaw();
        float pitch = client.player == null ? 0.0f : client.player.getPitch();
        int rgb = fogColor == null ? 0x78AAFF : fogColor.getRGB();
        float opacity = Math.max(0.0f, Math.min(1.0f, rainbow ? rainbowOpacity
            : (fogColor == null ? 0.0f : fogColor.getAlpha() / 255.0f)));

        COMPOSITE_DATA.clear();
        // depthParams: near, far, minThreshold, maxThreshold
        COMPOSITE_DATA.putFloat(near).putFloat(far).putFloat(minThreshold).putFloat(maxThreshold);
        // tint: rgb, opacity
        COMPOSITE_DATA.putFloat((rgb >> 16 & 0xFF) / 255.0f)
            .putFloat((rgb >> 8 & 0xFF) / 255.0f)
            .putFloat((rgb & 0xFF) / 255.0f)
            .putFloat(opacity);
        // effect: rainbow, saturation, brightness, time
        COMPOSITE_DATA.putFloat(rainbow ? 1.0f : 0.0f)
            .putFloat(Math.max(0.0f, Math.min(1.0f, rainbowSaturation)))
            .putFloat(Math.max(0.0f, Math.min(1.0f, rainbowBrightness)))
            .putFloat(time);
        // view: yaw, pitch, globalAlpha, unused
        COMPOSITE_DATA.putFloat(yaw).putFloat(pitch).putFloat(1.0f).putFloat(0.0f);
        COMPOSITE_DATA.flip();
        encoder.writeToBuffer(compositeUniforms.slice(), COMPOSITE_DATA);
    }

    private static float[] calculateGaussian(float sigma) {
        float safeSigma = Math.max(0.2f, sigma);
        float y = (float) Math.exp(-0.5f / (safeSigma * safeSigma));
        return new float[]{
            1.0f / (SQRT_2PI * safeSigma),
            y,
            y * y
        };
    }

    private static float resolveBlurResolutionScale(float strength) {
        if (strength >= 14.0f) return 0.45f;
        if (strength >= 10.0f) return 0.55f;
        if (strength >= 6.0f) return 0.7f;
        return 0.85f;
    }

    private static void delete(SimpleFramebuffer framebuffer) {
        if (framebuffer == null) {
            return;
        }
        try {
            framebuffer.delete();
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.warn("[LinkenVisuals] FogBlur framebuffer release failed: {}",
                throwable.getMessage());
        }
    }

    public static void release() {
        delete(copy);
        delete(tint);
        delete(blurPing);
        delete(blurPong);
        copy = null;
        tint = null;
        blurPing = null;
        blurPong = null;

        closeBuffer(blurUniforms);
        closeBuffer(compositeUniforms);
        closeBuffer(tintUniforms);
        closeBuffer(dummyVertex);
        blurUniforms = null;
        compositeUniforms = null;
        tintUniforms = null;
        dummyVertex = null;

        initialized = false;
        initAttempted = false;
    }

    private static void closeBuffer(GpuBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
    }
}
