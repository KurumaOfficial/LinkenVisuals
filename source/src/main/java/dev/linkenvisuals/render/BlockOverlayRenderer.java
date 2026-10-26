package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.BlockOverlayModule;
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
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * BlockOverlay (Phase 5.2): replaces the vanilla block outline with an
 * animated shader fill (nine donor effects, GLSL taken as text) plus a glow
 * outline. Drawn via an explicit command-encoder pass at {@code END_MAIN} —
 * the same pattern as the other UBO renderers — so no nested-pass risk
 * inside the outline event.
 */
public final class BlockOverlayRenderer {
    /** Monotonic animation epoch: shader time never wraps, no pop on cycle. */
    private static final long ANIM_EPOCH_NANOS = System.nanoTime();

    /** Box captured at outline time (camera-relative), drawn at END_MAIN. Render thread only. */
    private static Box pendingBox;

    /**
     * Block-relative shape cache: avoids {@code getOutlineShape()} +
     * {@code getBoundingBoxes()} when aiming at the same block across frames.
     * The camera-relative offset is still recomputed every frame.
     */
    private static BlockPos cachedPos;
    private static BlockState cachedState;
    private static Box cachedLocalBox;

    private static GpuBuffer uniforms;
    private static final ByteBuffer UNIFORM_DATA =
        ByteBuffer.allocateDirect(BlockOverlayPipelines.UNIFORM_SIZE).order(ByteOrder.nativeOrder());
    private static final ByteBuffer REUSABLE_BUFFER =
        ByteBuffer.allocateDirect(16384).order(ByteOrder.nativeOrder());

    private BlockOverlayRenderer() {
    }

    /**
     * Cancels the vanilla outline when the module is enabled and captures the
     * target box for the END_MAIN pass.
     */
    public static boolean onBeforeBlockOutline(WorldRenderContext context, OutlineRenderState state) {
        BlockOverlayModule module = Modules.blockOverlay();
        pendingBox = null;
        if (module == null || !module.enabled()) {
            return true;
        }
        pendingBox = captureBox();
        return pendingBox == null;
    }

    public static void onEndMain(WorldRenderContext context) {
        Box box = pendingBox;
        pendingBox = null;
        BlockOverlayModule module = Modules.blockOverlay();
        if (module == null || !module.enabled()) {
            return;
        }
        if (box == null) {
            box = captureBox();
        }
        if (box == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();
        if (main.getColorAttachmentView() == null || main.getDepthAttachmentView() == null) {
            return;
        }

        float animation = (float) ((System.nanoTime() - ANIM_EPOCH_NANOS) / 4_000_000_000.0)
            * module.shaderSpeed.value();
        int fillAlpha = clamp255(module.fillAlpha.value());
        int outlineAlpha = clamp255(module.outlineAlpha.value());
        BlockOverlayModule.FillMode mode = module.mode();
        Palette palette = Palette.of(mode);

        ensureUniforms();
        int fbWidth = Math.max(1, main.textureWidth);
        int fbHeight = Math.max(1, main.textureHeight);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        boolean hasFill = module.fill.value() && fillAlpha > 0;
        boolean hasOutline = outlineAlpha > 0;
        if (!hasFill && !hasOutline) {
            return;
        }

        if (module.useShader.value() && hasFill) {
            int shaderAlpha = Math.max(fillAlpha, Math.round(outlineAlpha * 0.72f));
            int base = palette.sample(animation + (float) ((box.minX + box.minY + box.minZ) * 0.05f));
            writeUniforms(encoder, animation * 4.0f, fbWidth, fbHeight, base, shaderAlpha);
            drawTexturedBox(encoder, main, BlockOverlayPipelines.shader(mode), box.expand(0.003));
        } else if (hasFill) {
            drawGradientFill(encoder, main, box, fillAlpha, animation, palette, mode);
        }

        if (hasOutline) {
            drawOutline(encoder, main, box, outlineAlpha, animation, palette);
        }
    }

    private static Box captureBox() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.crosshairTarget == null
            || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            cachedPos = null;
            cachedLocalBox = null;
            return null;
        }
        BlockPos pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            cachedPos = null;
            cachedState = null;
            cachedLocalBox = null;
            return null;
        }
        Box local;
        if (pos.equals(cachedPos) && state == cachedState && cachedLocalBox != null) {
            local = cachedLocalBox;
        } else {
            Entity entity = mc.getCameraEntity();
            ShapeContext shapeContext = entity != null ? ShapeContext.of(entity) : ShapeContext.absent();
            VoxelShape shape = state.getOutlineShape(mc.world, pos, shapeContext);
            List<Box> boxes = shape.getBoundingBoxes();
            if (boxes.isEmpty()) {
                cachedPos = null;
                cachedState = null;
                cachedLocalBox = null;
                return null;
            }
            local = boxes.size() > 1 ? shape.getBoundingBox() : boxes.get(0);
            cachedPos = pos.toImmutable();
            cachedState = state;
            cachedLocalBox = local;
        }
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        return local.offset(pos).offset(-cam.x, -cam.y, -cam.z);
    }

    private static void writeUniforms(CommandEncoder encoder, float time, int fbWidth, int fbHeight,
                                      int baseRgb, int alpha) {
        float shaderAlpha = Math.max(0.52f, Math.min(1.0f, alpha / 255.0f));
        UNIFORM_DATA.clear();
        // uParams: x=time, y=alpha, z=zoomFactor, w=shaderAlpha
        UNIFORM_DATA.putFloat(time);
        UNIFORM_DATA.putFloat(shaderAlpha);
        UNIFORM_DATA.putFloat(1.0f);
        UNIFORM_DATA.putFloat(shaderAlpha);
        // uScreen: xy=screenSize, zw=unused
        UNIFORM_DATA.putFloat(fbWidth);
        UNIFORM_DATA.putFloat(fbHeight);
        UNIFORM_DATA.putFloat(0.0f);
        UNIFORM_DATA.putFloat(0.0f);
        // uColor: rgba=baseColor
        UNIFORM_DATA.putFloat((baseRgb >> 16 & 0xFF) / 255.0f);
        UNIFORM_DATA.putFloat((baseRgb >> 8 & 0xFF) / 255.0f);
        UNIFORM_DATA.putFloat((baseRgb & 0xFF) / 255.0f);
        UNIFORM_DATA.putFloat(shaderAlpha);
        UNIFORM_DATA.flip();
        encoder.writeToBuffer(uniforms.slice(), UNIFORM_DATA);
    }

    private static void drawTexturedBox(CommandEncoder encoder, Framebuffer main,
                                        RenderPipeline pipeline, Box box) {
        ByteBuffer data = REUSABLE_BUFFER;
        data.clear();
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
        data.flip();
        GpuBuffer verts = RenderSystem.getDevice().createBuffer(
            () -> "linkenvisuals:block_overlay_fill", GpuBuffer.USAGE_VERTEX, data);
        GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
        try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:block_overlay",
            main.getColorAttachmentView(), OptionalInt.empty(),
            main.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            Pipelines.bindWorldUniforms(pass, transforms);
            pass.setUniform("BlockOverlay", uniforms);
            pass.setVertexBuffer(0, verts);
            pass.draw(0, 36);
        } finally {
            verts.close();
        }
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

    private static void drawGradientFill(CommandEncoder encoder, Framebuffer main, Box box,
                                         int alpha, float animation, Palette palette,
                                         BlockOverlayModule.FillMode mode) {
        if (mode == BlockOverlayModule.FillMode.COSMOS) {
            float phase = animation + (float) ((box.minX + box.minY + box.minZ) * 0.04f);
            int base = palette.sample(phase);
            int baseAlpha = Math.max(10, Math.round(alpha * 0.7f));
            ByteBuffer data = REUSABLE_BUFFER;
            data.clear();
            putColoredBox(data, box, ColorMath.withAlpha(base, baseAlpha));
            for (int glowLayer = 0; glowLayer < 3; glowLayer++) {
                double glowExpand = 0.0018 * (glowLayer + 1);
                float glowFactor = 1.0f - glowLayer / 3.0f;
                int glowAlpha = Math.round(alpha * (palette.glowMinAlpha + palette.glowAlphaRange * glowFactor));
                putColoredBox(data, box.expand(glowExpand), ColorMath.withAlpha(base, glowAlpha));
            }
            data.flip();
            drawColored(encoder, main, BlockOverlayPipelines.FLAT, data);
            return;
        }

        double height = Math.max(0.001, box.maxY - box.minY);
        int segments = Math.max(4, Math.min(8, (int) Math.ceil(height * 6.0)));
        ByteBuffer data = REUSABLE_BUFFER;
        data.clear();

        for (int i = 0; i < segments; i++) {
            float progress = i / (float) segments;
            double segmentMinY = MathHelper.lerp(progress, box.minY, box.maxY);
            double segmentMaxY = MathHelper.lerp((i + 1f) / segments, box.minY, box.maxY);
            int color = palette.sample(animation + progress * 0.45f + (float) ((box.minX + box.minZ) * 0.035f));
            int segmentAlpha = Math.max(10, Math.round(alpha * (palette.baseMinAlpha + palette.baseAlphaRange * (1.0f - progress))));
            putColoredBox(data, new Box(box.minX, segmentMinY, box.minZ, box.maxX, segmentMaxY, box.maxZ),
                ColorMath.withAlpha(color, segmentAlpha));
        }

        for (int glowLayer = 0; glowLayer < 3; glowLayer++) {
            double glowExpand = 0.0018 * (glowLayer + 1);
            float glowFactor = 1.0f - glowLayer / 3.0f;
            int color = palette.sample(animation + 0.16f * glowLayer + (float) (box.minY * 0.05f));
            int glowAlpha = Math.round(alpha * (palette.glowMinAlpha + palette.glowAlphaRange * glowFactor));
            putColoredBox(data, box.expand(glowExpand), ColorMath.withAlpha(color, glowAlpha));
        }

        data.flip();
        drawColored(encoder, main, BlockOverlayPipelines.FLAT, data);
    }

    private static void drawOutline(CommandEncoder encoder, Framebuffer main, Box box,
                                    int alpha, float animation, Palette palette) {
        int base = palette.sample(animation + (float) ((box.minX + box.minZ) * 0.05f));
        int glowRgb = ColorMath.mix(base, 0xFFFFFF, 0.35f);
        ByteBuffer data = REUSABLE_BUFFER;
        data.clear();
        for (int i = 0; i < 2; i++) {
            float strength = 1.0f - i * 0.45f;
            int glowAlpha = Math.max(8, Math.round(alpha * 0.24f * strength));
            putLineBox(data, box.expand(0.0012 * (i + 1)), ColorMath.withAlpha(glowRgb, glowAlpha));
        }
        putLineBox(data, box, ColorMath.withAlpha(base, alpha));
        data.flip();
        drawColored(encoder, main, BlockOverlayPipelines.LINES, data);
    }

    private static void drawColored(CommandEncoder encoder, Framebuffer main,
                                    RenderPipeline pipeline, ByteBuffer data) {
        int vertices = data.remaining() / 16;
        GpuBuffer verts = RenderSystem.getDevice().createBuffer(
            () -> "linkenvisuals:block_overlay_color", GpuBuffer.USAGE_VERTEX, data);
        GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
        try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:block_overlay_color",
            main.getColorAttachmentView(), OptionalInt.empty(),
            main.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            Pipelines.bindWorldUniforms(pass, transforms);
            pass.setVertexBuffer(0, verts);
            pass.draw(0, vertices);
        } finally {
            verts.close();
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
        pendingBox = null;
        cachedPos = null;
        cachedState = null;
        cachedLocalBox = null;
    }

    private static int clamp255(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
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

        static Palette of(BlockOverlayModule.FillMode mode) {
            return switch (mode) {
                case PLASMA -> new Palette(0x372AB4, 0xE036FF, 0x36DCFF, 0.24f, 0.56f, 0.12f, 0.09f);
                case COSMOS -> new Palette(0x223480, 0x5672E0, 0xB0D2FF, 0.20f, 0.52f, 0.10f, 0.08f);
                case COBWEB -> new Palette(0xD2DEE8, 0xFFFFFF, 0x8096AA, 0.18f, 0.45f, 0.08f, 0.06f);
                case FIREWORKS -> new Palette(0xFF4850, 0xFFDC48, 0x48ACFF, 0.26f, 0.58f, 0.14f, 0.12f);
                case GALAXY -> new Palette(0x181048, 0x6A40DC, 0xFFA6E8, 0.22f, 0.54f, 0.12f, 0.10f);
                case NEBULA -> new Palette(0x22084C, 0xBA2CCE, 0x48BAFF, 0.22f, 0.56f, 0.12f, 0.10f);
                case STARFIELD -> new Palette(0x08102C, 0x2C5CA4, 0xEBF6FF, 0.18f, 0.48f, 0.10f, 0.10f);
                case STARS -> new Palette(0x241E48, 0xFFD25C, 0xFFFFDC, 0.20f, 0.52f, 0.12f, 0.10f);
                case AURORA -> new Palette(0x2DE1A8, 0x54C8FF, 0xB46BFF, 0.22f, 0.55f, 0.12f, 0.07f);
            };
        }
    }
}
