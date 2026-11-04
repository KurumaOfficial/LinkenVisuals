package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.BlockOverlayModule;
import dev.linkenvisuals.render.Pipelines;
import dev.linkenvisuals.util.ColorMath;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.BlockOverlay;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * BlockOverlay renderer.
 *
 * <p>Draws the animated shader fill and glow outline for every box of the
 * targeted block's outline shape (stairs, fences, panes, ...), not just the
 * shape's bounding cube. Geometry is captured at {@code BEFORE_BLOCK_OUTLINE}
 * and submitted in an explicit command-encoder pass at {@code END_MAIN}.
 *
 * <p><b>TEMPORARILY DISABLED / NEEDS FIXING.</b> The module is hidden from
 * both GUIs, so {@link #config()} returns a disabled config. Keep the renderer
 * registered (it is a no-op while disabled) and re-enable the module after the
 * half-face solid fill and outline-mask bugs are fixed.
 */
public final class BlockOverlayRenderer {
    /** Monotonic animation epoch: shader time never wraps, no pop on cycle. */
    private static final long ANIM_EPOCH_NANOS = System.nanoTime();

    /**
     * A single block outline can contain dozens of boxes (fences, panes,
     * walls). Cap the number we expand into full cubes; past that the shape's
     * bounding box is a cheaper and still readable fallback.
     */
    private static final int MAX_SHAPE_BOXES = 64;

    /** Fill is pushed slightly out of the block to win the depth test. */
    private static final float FILL_EXPAND = 0.003f;

    /**
     * The outline is drawn at the block surface, so it needs a slightly larger
     * offset than the fill. At 1.21.11 depth precision the previous 0.0 offset
     * was fully occluded by the block itself (the outline-only capture was
     * empty), which is why the line appeared to be missing.
     */
    private static final float OUTLINE_EXPAND = 0.0045f;
    private static final float OUTLINE_GLOW_STEP = 0.0015f;

    private static final Matrix4f IDENTITY = new Matrix4f();

    /** Boxes captured at outline time (camera-relative), drawn at END_MAIN. */
    private static List<Box> pendingBoxes;

    /** Block-relative shape cache: avoids rebuilding multi-box shapes per frame. */
    private static BlockPos cachedPos;
    private static BlockState cachedState;
    private static List<Box> cachedLocalBoxes;
    private static Matrix4f capturedModelView;

    private static GpuBuffer uniforms;
    private static final ByteBuffer UNIFORM_DATA =
        ByteBuffer.allocateDirect(BlockOverlayPipelines.UNIFORM_SIZE).order(ByteOrder.nativeOrder());
    private static ByteBuffer reusableBuffer = ByteBuffer.allocateDirect(65536).order(ByteOrder.nativeOrder());

    private BlockOverlayRenderer() {
    }

    /**
     * Cancels the vanilla outline when the module is enabled and captures the
     * target boxes for the END_MAIN pass.
     */
    public static boolean onBeforeBlockOutline(WorldRenderContext context, OutlineRenderState state) {
        Config config = config();
        pendingBoxes = null;
        capturedModelView = null;
        if (!config.enabled() || !config.visible()) {
            // Do not suppress the vanilla outline when the module would draw
            // nothing (fill and outline both disabled/transparent).
            return true;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (state != null && state.pos() != null && state.shape() != null
            && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            List<Box> boxes = shapeBoxes(state.shape());
            if (!boxes.isEmpty()) {
                Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
                pendingBoxes = offsetBoxes(boxes, state.pos(), cam);
                capturedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            }
        }
        if (pendingBoxes == null) {
            pendingBoxes = captureBoxes();
        }
        if (capturedModelView == null) {
            capturedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        }
        return pendingBoxes == null;
    }

    public static void onEndMain(WorldRenderContext context) {
        List<Box> boxes = pendingBoxes;
        pendingBoxes = null;
        Config config = config();
        if (!config.enabled()) {
            capturedModelView = null;
            return;
        }
        if (boxes == null || boxes.isEmpty()) {
            boxes = captureBoxes();
        }
        if (boxes == null || boxes.isEmpty()) {
            capturedModelView = null;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();
        if (main.getColorAttachmentView() == null || main.getDepthAttachmentView() == null) {
            capturedModelView = null;
            return;
        }

        Matrix4f modelView = resolveModelView(mc);
        float animation = (float) ((System.nanoTime() - ANIM_EPOCH_NANOS) / 4_000_000_000.0)
            * config.speed();
        int fillAlpha = clamp255(config.fillAlpha());
        int outlineAlpha = clamp255(config.outlineAlpha());
        BlockOverlay.FillMode mode = config.mode();
        Palette palette = Palette.of(mode);

        boolean hasFill = config.fill() && fillAlpha > 0;
        boolean hasOutline = outlineAlpha > 0;
        if (!hasFill && !hasOutline) {
            return;
        }

        ensureUniforms();
        int fbWidth = Math.max(1, main.textureWidth);
        int fbHeight = Math.max(1, main.textureHeight);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        if (config.useShader() && hasFill) {
            float shaderAlpha = alphaCurve(fillAlpha);
            int base = palette.sample(animation + (float) ((boxes.get(0).minX + boxes.get(0).minY
                + boxes.get(0).minZ) * 0.05f));
            writeUniforms(encoder, animation * 4.0f, fbWidth, fbHeight, base, shaderAlpha);
            drawTexturedBoxes(encoder, main, BlockOverlayPipelines.shader(mode), boxes, modelView);
        } else if (hasFill) {
            drawGradientFill(encoder, main, boxes, fillAlpha, animation, palette, mode, modelView);
        }

        if (hasOutline) {
            drawOutline(encoder, main, boxes, outlineAlpha, animation, palette, modelView);
        }
    }

    /**
     * Prefer the model view captured at outline time. At END_MAIN the stack can
     * already be identity (or hold the last entity's matrix); using it blindly
     * made the overlay render with the camera rotation missing, so blocks
     * appeared somewhere in the world and only one face looked right.
     */
    private static Matrix4f resolveModelView(MinecraftClient mc) {
        Matrix4f modelView = capturedModelView != null
            ? new Matrix4f(capturedModelView)
            : new Matrix4f(RenderSystem.getModelViewMatrix());
        capturedModelView = null;
        if (modelView.equals(IDENTITY)) {
            Matrix4f current = new Matrix4f(RenderSystem.getModelViewMatrix());
            if (!current.equals(IDENTITY)) {
                modelView.set(current);
            } else if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
                modelView.rotation(mc.gameRenderer.getCamera().getRotation());
            }
        }
        return modelView;
    }

    private static List<Box> captureBoxes() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.crosshairTarget == null
            || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            clearCache();
            return null;
        }
        BlockPos pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            clearCache();
            return null;
        }
        List<Box> local;
        if (pos.equals(cachedPos) && state == cachedState && cachedLocalBoxes != null) {
            local = cachedLocalBoxes;
        } else {
            Entity entity = mc.getCameraEntity();
            ShapeContext shapeContext = entity != null ? ShapeContext.of(entity) : ShapeContext.absent();
            VoxelShape shape = state.getOutlineShape(mc.world, pos, shapeContext);
            List<Box> shapeBoxes = shapeBoxes(shape);
            if (shapeBoxes.isEmpty()) {
                clearCache();
                return null;
            }
            local = new ArrayList<>(shapeBoxes);
            cachedPos = pos.toImmutable();
            cachedState = state;
            cachedLocalBoxes = local;
        }
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        return offsetBoxes(local, pos, cam);
    }

    private static List<Box> shapeBoxes(VoxelShape shape) {
        List<Box> boxes = shape.getBoundingBoxes();
        if (boxes.isEmpty()) {
            return List.of();
        }
        if (boxes.size() > MAX_SHAPE_BOXES) {
            return List.of(shape.getBoundingBox());
        }
        return boxes;
    }

    private static List<Box> offsetBoxes(List<Box> local, BlockPos pos, Vec3d cam) {
        List<Box> result = new ArrayList<>(local.size());
        for (Box box : local) {
            result.add(box.offset(pos).offset(-cam.x, -cam.y, -cam.z));
        }
        return result;
    }

    private static void clearCache() {
        cachedPos = null;
        cachedState = null;
        cachedLocalBoxes = null;
    }

    private static void writeUniforms(CommandEncoder encoder, float time, int fbWidth, int fbHeight,
                                      int baseRgb, float alpha) {
        float a = Math.max(0.02f, Math.min(1.0f, alpha));
        UNIFORM_DATA.clear();
        // uParams: x=time, y=alpha, z=zoomFactor, w=alpha
        UNIFORM_DATA.putFloat(time);
        UNIFORM_DATA.putFloat(a);
        UNIFORM_DATA.putFloat(1.0f);
        UNIFORM_DATA.putFloat(a);
        // uScreen: xy=screenSize, zw=unused
        UNIFORM_DATA.putFloat(fbWidth);
        UNIFORM_DATA.putFloat(fbHeight);
        UNIFORM_DATA.putFloat(0.0f);
        UNIFORM_DATA.putFloat(0.0f);
        // uColor: rgba=baseColor
        UNIFORM_DATA.putFloat((baseRgb >> 16 & 0xFF) / 255.0f);
        UNIFORM_DATA.putFloat((baseRgb >> 8 & 0xFF) / 255.0f);
        UNIFORM_DATA.putFloat((baseRgb & 0xFF) / 255.0f);
        UNIFORM_DATA.putFloat(a);
        UNIFORM_DATA.flip();
        encoder.writeToBuffer(uniforms.slice(), UNIFORM_DATA);
    }

    /**
     * The alpha slider is 0..255, while the shader's final alpha multiplies it
     * by roughly 0.3..0.5. A linear mapping made the default 70 effectively
     * invisible, so use a gentle curve: the slider remains responsive but low
     * values still produce a visible effect.
     */
    private static float alphaCurve(int alpha255) {
        float linear = Math.max(0.0f, Math.min(1.0f, alpha255 / 255.0f));
        return (float) Math.sqrt(linear);
    }

    private static GpuBufferSlice writeTransforms(Matrix4f modelView) {
        return RenderSystem.getDynamicUniforms().write(
            modelView != null ? modelView : RenderSystem.getModelViewMatrix(),
            new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
            new Vector3f(),
            new Matrix4f());
    }

    private static void drawTexturedBoxes(CommandEncoder encoder, Framebuffer main,
                                          RenderPipeline pipeline, List<Box> boxes, Matrix4f modelView) {
        ByteBuffer data = buffer(boxes.size() * 36 * 20);
        for (Box box : boxes) {
            putTexturedBox(data, box.expand(FILL_EXPAND));
        }
        data.flip();
        int vertices = data.remaining() / 20;
        GpuBuffer verts = RenderSystem.getDevice().createBuffer(
            () -> "linkenvisuals:block_overlay_fill", GpuBuffer.USAGE_VERTEX, data);
        GpuBufferSlice transforms = writeTransforms(modelView);
        int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:block_overlay",
            main.getColorAttachmentView(), OptionalInt.empty(),
            main.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            Pipelines.bindWorldUniforms(pass, transforms);
            pass.setUniform("BlockOverlay", uniforms);
            pass.setVertexBuffer(0, verts);
            pass.draw(0, vertices);
        } finally {
            verts.close();
            // The overlay pass is opened from inside the vanilla world pass.
            // Closing a pass always binds framebuffer 0, so restore the pass
            // target that was active before it — otherwise every following
            // vanilla/translucent draw in the frame lands on the window
            // backbuffer instead of the main target.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static void putTexturedBox(ByteBuffer data, Box box) {
        putTexturedFace(data, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
            box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ);
        putTexturedFace(data, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ,
            box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ);
        putTexturedFace(data, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ,
            box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ);
        putTexturedFace(data, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ,
            box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ);
        putTexturedFace(data, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ,
            box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
        putTexturedFace(data, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
            box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ);
    }

    private static void putTexturedFace(ByteBuffer data,
                                        double x1, double y1, double z1, double x2, double y2, double z2,
                                        double x3, double y3, double z3, double x4, double y4, double z4) {
        putTextured(data, x1, y1, z1, 0.0f, 0.0f);
        putTextured(data, x2, y2, z2, 1.0f, 0.0f);
        putTextured(data, x3, y3, z3, 1.0f, 1.0f);
        putTextured(data, x1, y1, z1, 0.0f, 0.0f);
        putTextured(data, x3, y3, z3, 1.0f, 1.0f);
        putTextured(data, x4, y4, z4, 0.0f, 1.0f);
    }

    private static void putTextured(ByteBuffer data, double x, double y, double z, float u, float v) {
        data.putFloat((float) x).putFloat((float) y).putFloat((float) z);
        data.putFloat(u).putFloat(v);
    }

    private static void drawGradientFill(CommandEncoder encoder, Framebuffer main, List<Box> boxes,
                                         int alpha, float animation, Palette palette,
                                         BlockOverlay.FillMode mode, Matrix4f modelView) {
        ByteBuffer data = buffer(boxes.size() * 12 * 36 * 16);
        for (Box box : boxes) {
            if (mode == BlockOverlay.FillMode.Cosmos) {
                float phase = animation + (float) ((box.minX + box.minY + box.minZ) * 0.04f);
                int base = palette.sample(phase);
                int baseAlpha = Math.max(10, Math.round(alpha * 0.7f));
                putColoredBox(data, box, ColorMath.withAlpha(base, baseAlpha));
                for (int glowLayer = 0; glowLayer < 3; glowLayer++) {
                    double glowExpand = 0.0018 * (glowLayer + 1);
                    float glowFactor = 1.0f - glowLayer / 3.0f;
                    int glowAlpha = Math.round(alpha * (palette.glowMinAlpha
                        + palette.glowAlphaRange * glowFactor));
                    putColoredBox(data, box.expand(glowExpand), ColorMath.withAlpha(base, glowAlpha));
                }
                continue;
            }

            double height = Math.max(0.001, box.maxY - box.minY);
            int segments = Math.max(4, Math.min(8, (int) Math.ceil(height * 6.0)));
            for (int i = 0; i < segments; i++) {
                float progress = i / (float) segments;
                double segmentMinY = MathHelper.lerp(progress, box.minY, box.maxY);
                double segmentMaxY = MathHelper.lerp((i + 1f) / segments, box.minY, box.maxY);
                int color = palette.sample(animation + progress * 0.45f
                    + (float) ((box.minX + box.minZ) * 0.035f));
                int segmentAlpha = Math.max(10, Math.round(alpha * (palette.baseMinAlpha
                    + palette.baseAlphaRange * (1.0f - progress))));
                putColoredBox(data, new Box(box.minX, segmentMinY, box.minZ,
                    box.maxX, segmentMaxY, box.maxZ), ColorMath.withAlpha(color, segmentAlpha));
            }

            for (int glowLayer = 0; glowLayer < 3; glowLayer++) {
                double glowExpand = 0.0018 * (glowLayer + 1);
                float glowFactor = 1.0f - glowLayer / 3.0f;
                int color = palette.sample(animation + 0.16f * glowLayer + (float) (box.minY * 0.05f));
                int glowAlpha = Math.round(alpha * (palette.glowMinAlpha
                    + palette.glowAlphaRange * glowFactor));
                putColoredBox(data, box.expand(glowExpand), ColorMath.withAlpha(color, glowAlpha));
            }
        }
        data.flip();
        drawColored(encoder, main, BlockOverlayPipelines.FLAT, data, modelView);
    }

    private static void drawOutline(CommandEncoder encoder, Framebuffer main, List<Box> boxes,
                                    int alpha, float animation, Palette palette, Matrix4f modelView) {
        ByteBuffer data = buffer(boxes.size() * 3 * 24 * 16);
        int base = palette.sample(animation + (float) ((boxes.get(0).minX + boxes.get(0).minZ) * 0.05f));
        int glowRgb = ColorMath.mix(base, 0xFFFFFF, 0.35f);
        for (Box box : boxes) {
            for (int i = 0; i < 2; i++) {
                float strength = 1.0f - i * 0.45f;
                int glowAlpha = Math.max(8, Math.round(alpha * 0.24f * strength));
                putLineBox(data, box.expand(OUTLINE_EXPAND + OUTLINE_GLOW_STEP * (i + 1)),
                    ColorMath.withAlpha(glowRgb, glowAlpha));
            }
            putLineBox(data, box.expand(OUTLINE_EXPAND), ColorMath.withAlpha(base, alpha));
        }
        data.flip();
        drawColored(encoder, main, BlockOverlayPipelines.LINES, data, modelView);
    }

    private static void drawColored(CommandEncoder encoder, Framebuffer main,
                                    RenderPipeline pipeline, ByteBuffer data, Matrix4f modelView) {
        int vertices = data.remaining() / 16;
        GpuBuffer verts = RenderSystem.getDevice().createBuffer(
            () -> "linkenvisuals:block_overlay_color", GpuBuffer.USAGE_VERTEX, data);
        GpuBufferSlice transforms = writeTransforms(modelView);
        int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:block_overlay_color",
            main.getColorAttachmentView(), OptionalInt.empty(),
            main.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            Pipelines.bindWorldUniforms(pass, transforms);
            pass.setVertexBuffer(0, verts);
            pass.draw(0, vertices);
        } finally {
            verts.close();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static void putColoredBox(ByteBuffer data, Box box, int argb) {
        putColoredFace(data, argb, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
            box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ);
        putColoredFace(data, argb, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ,
            box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ);
        putColoredFace(data, argb, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ,
            box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ);
        putColoredFace(data, argb, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ,
            box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ);
        putColoredFace(data, argb, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ,
            box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
        putColoredFace(data, argb, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
            box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ);
    }

    private static void putColoredFace(ByteBuffer data, int argb,
                                       double x1, double y1, double z1, double x2, double y2, double z2,
                                       double x3, double y3, double z3, double x4, double y4, double z4) {
        putColored(data, argb, x1, y1, z1);
        putColored(data, argb, x2, y2, z2);
        putColored(data, argb, x3, y3, z3);
        putColored(data, argb, x1, y1, z1);
        putColored(data, argb, x3, y3, z3);
        putColored(data, argb, x4, y4, z4);
    }

    private static void putColored(ByteBuffer data, int argb, double x, double y, double z) {
        data.putFloat((float) x).putFloat((float) y).putFloat((float) z);
        data.put((byte) (argb >> 16 & 0xFF)).put((byte) (argb >> 8 & 0xFF))
            .put((byte) (argb & 0xFF)).put((byte) (argb >>> 24));
    }

    private static void putLineBox(ByteBuffer data, Box box, int argb) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        // 12 edges
        edge(data, argb, x1, y1, z1, x2, y1, z1);
        edge(data, argb, x1, y1, z1, x1, y2, z1);
        edge(data, argb, x1, y1, z1, x1, y1, z2);
        edge(data, argb, x2, y1, z1, x2, y2, z1);
        edge(data, argb, x2, y1, z1, x2, y1, z2);
        edge(data, argb, x1, y2, z1, x2, y2, z1);
        edge(data, argb, x1, y2, z1, x1, y2, z2);
        edge(data, argb, x1, y1, z2, x2, y1, z2);
        edge(data, argb, x1, y1, z2, x1, y2, z2);
        edge(data, argb, x2, y2, z1, x2, y2, z2);
        edge(data, argb, x2, y1, z2, x2, y2, z2);
        edge(data, argb, x1, y2, z2, x2, y2, z2);
    }

    private static void edge(ByteBuffer data, int argb,
                             double x1, double y1, double z1, double x2, double y2, double z2) {
        putColored(data, argb, x1, y1, z1);
        putColored(data, argb, x2, y2, z2);
    }

    private static ByteBuffer buffer(int required) {
        if (reusableBuffer.capacity() < required) {
            reusableBuffer = ByteBuffer.allocateDirect(required).order(ByteOrder.nativeOrder());
        }
        reusableBuffer.clear();
        return reusableBuffer;
    }

    private static void ensureUniforms() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(() -> "linkenvisuals:block_overlay_uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BlockOverlayPipelines.UNIFORM_SIZE);
        }
    }

    /** Frees the uniform buffer. Safe to call when already released. */
    public static void shutdown() {
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
        pendingBoxes = null;
        capturedModelView = null;
        clearCache();
    }

    private static int clamp255(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private record Config(boolean enabled, boolean fill, boolean useShader,
                          BlockOverlay.FillMode mode, float fillAlpha, float outlineAlpha, float speed) {
        private static final Config DISABLED =
            new Config(false, false, false, BlockOverlay.FillMode.Aurora, 0f, 0f, 1f);

        /** Whether this config produces at least one visible layer. */
        private boolean visible() {
            return (fill && Math.round(fillAlpha) > 0) || Math.round(outlineAlpha) > 0;
        }
    }

    /**
     * The new object module is the source of truth in the GUI. The legacy
     * module is kept as a fallback for older configs/entry points, but the
     * renderer must read the same settings the user edits.
     */
    private static Config config() {
        BlockOverlayModule modern = Modules.blockOverlay();
        if (modern != null) {
            return new Config(
                modern.enabled(),
                modern.fill.value(),
                modern.useShader.value(),
                legacyMode(modern.mode()),
                modern.fillAlpha.value(),
                modern.outlineAlpha.value(),
                modern.shaderSpeed.value());
        }
        BlockOverlay legacy = legacyBlockOverlay();
        if (legacy == null) {
            return Config.DISABLED;
        }
        return new Config(
            legacy.isToggled(),
            legacy.fill.getValue(),
            legacy.useShader.getValue(),
            legacy.fillMode.getValue(),
            legacy.fillAlpha.getValue(),
            legacy.outlineAlpha.getValue(),
            legacy.shaderSpeed.getValue());
    }

    private static BlockOverlay.FillMode legacyMode(BlockOverlayModule.FillMode mode) {
        return switch (mode) {
            case AURORA -> BlockOverlay.FillMode.Aurora;
            case PLASMA -> BlockOverlay.FillMode.Plasma;
            case COSMOS -> BlockOverlay.FillMode.Cosmos;
            case COBWEB -> BlockOverlay.FillMode.Cobweb;
            case FIREWORKS -> BlockOverlay.FillMode.Fireworks;
            case GALAXY -> BlockOverlay.FillMode.Galaxy;
            case NEBULA -> BlockOverlay.FillMode.Nebula;
            case STARFIELD -> BlockOverlay.FillMode.Starfield;
            case STARS -> BlockOverlay.FillMode.Stars;
        };
    }

    private static BlockOverlay legacyBlockOverlay() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return null;
        }
        return instance.getModuleManager().getModule(BlockOverlay.class);
    }

    /** Donor FillPalette, int-packed (no java.awt). */
    private static final class Palette {
        final int a;
        final int b;
        final int c;
        final float baseMinAlpha;
        final float baseAlphaRange;
        final float glowMinAlpha;
        final float glowAlphaRange;

        private Palette(int a, int b, int c, float baseMinAlpha, float baseAlphaRange,
                        float glowMinAlpha, float glowAlphaRange) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.baseMinAlpha = baseMinAlpha;
            this.baseAlphaRange = baseAlphaRange;
            this.glowMinAlpha = glowMinAlpha;
            this.glowAlphaRange = glowAlphaRange;
        }

        int sample(float t) {
            float wrapped = t - (float) Math.floor(t);
            if (wrapped < 0.5f) {
                return ColorMath.mix(a, b, wrapped * 2.0f);
            }
            return ColorMath.mix(b, c, (wrapped - 0.5f) * 2.0f);
        }

        static Palette of(BlockOverlay.FillMode mode) {
            return switch (mode) {
                case Plasma -> new Palette(0x372AB4, 0xE036FF, 0x36DCFF, 0.24f, 0.56f, 0.12f, 0.09f);
                case Cosmos -> new Palette(0x223480, 0x5672E0, 0xB0D2FF, 0.20f, 0.52f, 0.10f, 0.08f);
                case Cobweb -> new Palette(0xD2DEE8, 0xFFFFFF, 0x8096AA, 0.18f, 0.45f, 0.08f, 0.06f);
                case Fireworks -> new Palette(0xFF4850, 0xFFDC48, 0x48ACFF, 0.26f, 0.58f, 0.14f, 0.12f);
                case Galaxy -> new Palette(0x181048, 0x6A40DC, 0xFFA6E8, 0.22f, 0.54f, 0.12f, 0.10f);
                case Nebula -> new Palette(0x22084C, 0xBA2CCE, 0x48BAFF, 0.22f, 0.56f, 0.12f, 0.10f);
                case Starfield -> new Palette(0x08102C, 0x2C5CA4, 0xEBF6FF, 0.18f, 0.48f, 0.10f, 0.10f);
                case Stars -> new Palette(0x241E48, 0xFFD25C, 0xFFFFDC, 0.20f, 0.52f, 0.12f, 0.10f);
                case Aurora -> new Palette(0x2DE1A8, 0x54C8FF, 0xB46BFF, 0.22f, 0.55f, 0.12f, 0.07f);
            };
        }
    }
}
