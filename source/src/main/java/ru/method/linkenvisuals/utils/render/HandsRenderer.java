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
import ru.method.linkenvisuals.modules.impl.render.Hands;
import ru.method.linkenvisuals.utils.Wrapper;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;

/**
 * Hands (donor {@code HandsRenderer}) rebuilt on the 1.21.11 render graph.
 *
 * <p>The donor pass sampled the hand colour/depth through a raw fullscreen
 * shader program. In 1.21.11 the hand is submitted to a deferred command queue
 * and the raw path either ran before the hand existed or sampled a cleared
 * depth buffer; the reversed {@code smoothstep(0.99, 0.98)} mask is undefined
 * in GLSL and on this driver covered the entire frame. This implementation
 * renders the queued hand first, copies the final frame with {@link BlitPipeline}
 * and composites a well-defined depth-masked effect through {@link RenderPass}.
 */
public final class HandsRenderer implements Wrapper {
    private static final long TIME_EPOCH = System.nanoTime();

    private static SimpleFramebuffer sceneCopy;
    private static SimpleFramebuffer beforeHand;
    private static GpuBuffer uniforms;
    private static GpuBuffer dummyVertex;
    private static final ByteBuffer DATA =
        ByteBuffer.allocateDirect(HandsPipelines.UNIFORM_SIZE).order(ByteOrder.nativeOrder());

    private static boolean initialized;
    private static boolean initAttempted;
    private static boolean capturedThisFrame;

    private HandsRenderer() {
    }

    /** Called at the beginning of every world frame. */
    public static void beginFrame() {
        capturedThisFrame = false;
    }

    /**
     * Captures the frame immediately before the hand is submitted. The hand
     * effect mask compares this snapshot with the post-hand frame, which works
     * even when 1.21.11's first-person hand pass does not write depth.
     */
    public static void captureBeforeHand() {
        if (!isActive()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client == null ? null : client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null) {
            return;
        }
        int width = Math.max(1, main.textureWidth);
        int height = Math.max(1, main.textureHeight);
        ensureFramebuffer(width, height);
        if (beforeHand == null) {
            return;
        }
        BlitPipeline.run(main.getColorAttachmentView(), beforeHand.getColorAttachmentView());
        capturedThisFrame = true;
    }

    public static void render(float farPlane) {
        if (!isActive() || !capturedThisFrame) {
            return;
        }
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
        ensureFramebuffer(width, height);
        if (sceneCopy == null) {
            return;
        }

        int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            BlitPipeline.run(main.getColorAttachmentView(), sceneCopy.getColorAttachmentView());
            writeUniforms(encoder, width, height, farPlane);

            GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            Hands hands = resolveModule();
            Hands.Mode mode = hands == null ? Hands.Mode.OwnColor : hands.mode.getValue();
            try (RenderPass pass = encoder.createRenderPass(
                () -> "linkenvisuals:hands_effect",
                main.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(HandsPipelines.pipeline(mode));
                pass.bindTexture("ColorTexture", sceneCopy.getColorAttachmentView(), linear);
                pass.bindTexture("DepthTexture", main.getDepthAttachmentView(), nearest);
                pass.bindTexture("BeforeColorTexture",
                    beforeHand != null ? beforeHand.getColorAttachmentView() : sceneCopy.getColorAttachmentView(),
                    linear);
                pass.setUniform("HandsData", uniforms);
                pass.setVertexBuffer(0, dummyVertex);
                pass.draw(0, 3);
            }
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.warn("[LinkenVisuals] Hands shader pass failed.", throwable);
            release();
        } finally {
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
                () -> "linkenvisuals:hands_dummy_vertex", GpuBuffer.USAGE_VERTEX, 4);
            uniforms = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:hands_uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                HandsPipelines.UNIFORM_SIZE);
            initialized = true;
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.error("[LinkenVisuals] Failed to initialize Hands buffers.", throwable);
            release();
            return false;
        }
        return true;
    }

    private static void ensureFramebuffer(int width, int height) {
        if (sceneCopy == null || sceneCopy.textureWidth != width || sceneCopy.textureHeight != height) {
            releaseFramebuffer(sceneCopy);
            releaseFramebuffer(beforeHand);
            sceneCopy = new SimpleFramebuffer("linkenvisuals:hands_scene", width, height, false);
            beforeHand = new SimpleFramebuffer("linkenvisuals:hands_before", width, height, false);
        } else if (beforeHand == null) {
            beforeHand = new SimpleFramebuffer("linkenvisuals:hands_before", width, height, false);
        }
    }

    private static void releaseFramebuffer(SimpleFramebuffer framebuffer) {
        if (framebuffer == null) {
            return;
        }
        try {
            framebuffer.delete();
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.warn("[LinkenVisuals] Hands framebuffer release failed: {}",
                throwable.getMessage());
        }
    }

    private static void writeUniforms(CommandEncoder encoder, int width, int height, float farPlane) {
        Hands hands = resolveModule();
        Hands.Mode mode = hands == null ? Hands.Mode.OwnColor : hands.mode.getValue();
        Color solid = hands == null ? new Color(0, 255, 0, 255) : hands.solidColor.getValue();
        Color solid2 = hands == null ? new Color(255, 0, 0, 255) : hands.solidColor2.getValue();
        Color shader = hands == null ? new Color(151, 71, 255, 255) : hands.getResolvedShaderColor();
        float speed = hands == null ? 0.5f : hands.gradientSpeed.getValue();
        float blur = hands == null ? 10.0f : hands.blurStrength.getValue();
        float time = (System.nanoTime() - TIME_EPOCH) / 1_000_000_000.0f * speed;
        boolean own = mode == Hands.Mode.OwnColor;
        boolean transparent = mode == Hands.Mode.Transparent;

        Color c1 = own ? solid : shader;
        Color c2 = own ? solid2 : shader;
        float effectAlpha = own ? Math.max(solid.getAlpha(), solid2.getAlpha()) / 255.0f : 1.0f;
        float quality = transparent ? 5.0f : 4.0f;
        float direction = transparent ? 6.0f : 5.0f;
        float refraction = transparent ? 0.025f : 0.015f;
        float brightness = transparent ? 1.08f : 1.0f;
        float backgroundBlur = transparent ? blur : 0.12f;

        DATA.clear();
        // colorA: customColor1.rgb, effectAlpha
        DATA.putFloat(c1.getRed() / 255.0f)
            .putFloat(c1.getGreen() / 255.0f)
            .putFloat(c1.getBlue() / 255.0f)
            .putFloat(effectAlpha);
        // colorB: customColor2.rgb
        DATA.putFloat(c2.getRed() / 255.0f)
            .putFloat(c2.getGreen() / 255.0f)
            .putFloat(c2.getBlue() / 255.0f)
            .putFloat(0.0f);
        // params0: time, blurSize, quality, direction
        DATA.putFloat(time).putFloat(blur).putFloat(quality).putFloat(direction);
        // params1: refraction, brightness, backgroundBlur, unused
        DATA.putFloat(refraction).putFloat(brightness).putFloat(backgroundBlur).putFloat(0.0f);
        // screenSize: width, height, far, near
        DATA.putFloat(width).putFloat(height).putFloat(farPlane).putFloat(0.05f);
        // flags: enableChromatic, enableDistortion, hideHand, fillGlow
        DATA.putFloat(transparent ? 1.0f : 0.0f)
            .putFloat(transparent ? 1.0f : 0.0f)
            .putFloat(transparent ? 1.0f : 0.0f)
            .putFloat(1.0f);
        DATA.flip();
        encoder.writeToBuffer(uniforms.slice(), DATA);
    }

    public static void release() {
        releaseFramebuffer(sceneCopy);
        releaseFramebuffer(beforeHand);
        sceneCopy = null;
        beforeHand = null;
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
        if (dummyVertex != null) {
            dummyVertex.close();
            dummyVertex = null;
        }
        initialized = false;
        initAttempted = false;
    }

    public static boolean isActive() {
        if (mc == null || mc.player == null || mc.world == null) return false;
        if (mc.gameRenderer == null || mc.gameRenderer.isRenderingPanorama()) return false;
        // The first-person hand only exists in first person; in F5 the colour
        // delta mask has no hand to cover.
        if (!mc.options.getPerspective().isFirstPerson()) return false;
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.isPanic() || instance.getModuleManager() == null) return false;
        Hands hands = instance.getModuleManager().getModule(Hands.class);
        return hands != null && hands.isToggled();
    }

    private static Hands resolveModule() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        return instance == null || instance.getModuleManager() == null
            ? null : instance.getModuleManager().getModule(Hands.class);
    }
}
