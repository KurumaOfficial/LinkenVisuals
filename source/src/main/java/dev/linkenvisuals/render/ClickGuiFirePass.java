package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.LinkenVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Full-resolution shader fire around the ClickGUI panels.
 *
 * <p>The GUI renderer clamps custom vertex attributes (normals are bytes, z is
 * used as a sort key), which made the old per-quad fire either circular or
 * pixelated. This pass instead renders one full-screen triangle with a UBO
 * containing every panel rectangle in physical pixels. The shader draws fire
 * only outside those rectangles, so every halo follows its panel exactly and
 * stays smooth at any resolution/UI scale.
 */
public final class ClickGuiFirePass {

    public static final int MAX_PANELS = 32;
    /** One fixed halo width in design units; no per-panel size tweaking. */
    private static final float HALO_MARGIN = 20.0f;
    public static final int UNIFORM_SIZE = (4 + 4 + MAX_PANELS * 4 + MAX_PANELS * 4) * 4;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder()
            .withLocation(LinkenVisuals.id("pipeline/clickgui_fire_pass"))
            .withVertexShader(LinkenVisuals.id("core/clickgui_fire_pass"))
            .withFragmentShader(LinkenVisuals.id("core/clickgui_fire_pass"))
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .withUniform("FireData", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private static final ByteBuffer UNIFORM_DATA =
        ByteBuffer.allocateDirect(UNIFORM_SIZE).order(ByteOrder.nativeOrder());
    private static final List<Panel> PANELS = new ArrayList<>(MAX_PANELS);
    private static final long STARTED_AT = System.nanoTime();

    private static float preparedFade;
    private static int preparedAccent;
    private static boolean pending;

    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;

    private ClickGuiFirePass() {
    }

    public static void begin() {
        PANELS.clear();
        pending = false;
    }

    /**
     * Registers a panel rectangle (design coordinates) for the current frame.
     * Call it while the panel's matrix is active.
     */
    public static void addPanel(DrawContext context, float x, float y, float width,
                                float height, float radius, float visibility) {
        if (context == null || width <= 0.0f || height <= 0.0f
            || visibility <= 0.02f || PANELS.size() >= MAX_PANELS) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        Vector2f p0 = pose.transformPosition(x, y, new Vector2f());
        Vector2f p1 = pose.transformPosition(x + width, y + height, new Vector2f());
        float gui = (float) client.getWindow().getScaleFactor();
        float w = Math.abs(p1.x - p0.x);
        if (w <= 0.001f) {
            return;
        }
        float unit = w / width;

        PANELS.add(new Panel(
            (p0.x + p1.x) * 0.5f * gui,
            (p0.y + p1.y) * 0.5f * gui,
            w * 0.5f * gui,
            Math.abs(p1.y - p0.y) * 0.5f * gui,
            Math.max(0.0f, radius * unit * gui),
            Math.max(1.0f, HALO_MARGIN * unit * gui),
            Math.max(0.0f, Math.min(1.0f, visibility))));
    }

    /** Queues the accumulated fire for the post-GUI render pass. */
    public static void prepare(float fade, int accent) {
        preparedFade = Math.max(0.0f, Math.min(1.0f, fade));
        preparedAccent = accent;
        pending = !PANELS.isEmpty() && preparedFade > 0.01f;
    }

    /** Drawn by GuiRendererMixin after the GUI batch has been submitted. */
    public static void render() {
        if (!pending || PANELS.isEmpty() || preparedFade <= 0.01f) {
            return;
        }
        pending = false;
        float fade = preparedFade;
        int accent = preparedAccent;
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer target = client == null ? null : client.getFramebuffer();
        if (target == null || target.getColorAttachmentView() == null) {
            return;
        }

        ensureBuffers();
        float time = (System.nanoTime() - STARTED_AT) / 1_000_000_000.0f;

        UNIFORM_DATA.clear();
        UNIFORM_DATA.putFloat((float) target.textureWidth).putFloat((float) target.textureHeight)
            .putFloat(time).putFloat(PANELS.size());
        UNIFORM_DATA.putFloat((accent >> 16 & 0xFF) / 255.0f)
            .putFloat((accent >> 8 & 0xFF) / 255.0f)
            .putFloat((accent & 0xFF) / 255.0f)
            .putFloat(Math.max(0.0f, Math.min(1.0f, fade)));

        for (int i = 0; i < MAX_PANELS; i++) {
            if (i < PANELS.size()) {
                Panel p = PANELS.get(i);
                UNIFORM_DATA.putFloat(p.centerX).putFloat(p.centerY)
                    .putFloat(p.halfW).putFloat(p.halfH);
            } else {
                UNIFORM_DATA.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
            }
        }
        for (int i = 0; i < MAX_PANELS; i++) {
            if (i < PANELS.size()) {
                Panel p = PANELS.get(i);
                UNIFORM_DATA.putFloat(p.radius).putFloat(p.spread)
                    .putFloat(p.visibility).putFloat(0.0f);
            } else {
                UNIFORM_DATA.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
            }
        }
        UNIFORM_DATA.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), UNIFORM_DATA);

        try (RenderPass pass = encoder.createRenderPass(
            () -> "linkenvisuals:clickgui_fire",
            target.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.setUniform("FireData", uniformBuffer);
            pass.draw(0, 3);
        }
    }

    private static void ensureBuffers() {
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:clickgui_fire_uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:clickgui_fire_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummy);
            MemoryUtil.memFree(dummy);
        }
    }

    private record Panel(float centerX, float centerY, float halfW, float halfH,
                         float radius, float spread, float visibility) {
    }
}
