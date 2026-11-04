package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.render.Pipelines;
import dev.linkenvisuals.render.RoundedRectPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import ru.method.linkenvisuals.LinkenVisuals;

/**
 * Soft rounded glow / box-shadow, matching the donor's halo around HUD
 * surfaces and ClickGUI panels. One draw call per glow, with a smooth SDF
 * falloff - no stacked thin outlines.
 */
public final class RoundedGlowPipeline {

    private static final RenderPipeline PIPELINE = Pipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(LinkenVisuals.id("pipeline/rounded_glow"))
                    .withVertexShader(LinkenVisuals.id("core/rounded_glow"))
                    .withFragmentShader(LinkenVisuals.id("core/rounded_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,
                            com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private RoundedGlowPipeline() {
    }

    public static void init() {
    }

    public static void draw(DrawContext context, float x, float y, float width, float height,
                            float radius, float spread, int argb) {
        if (context == null || width <= 0f || height <= 0f || spread <= 0f) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        float visualScale = RoundedRectPipeline.matrixScale(pose);
        float radiusPx = Math.max(0f, radius) * visualScale;
        float spreadPx = Math.max(1.0f, spread * visualScale);
        // The radius (the value that positions the rounded corner) goes through
        // Position.z, which is a full float and is ignored by the glow shader as
        // depth. The old normal-based radius ratio was quantized to 1/127: on
        // wide panels that is up to ~2px of error, which pushed the halo away
        // from the corner and left a dark crescent between fill and glow.
        // The spread only controls the soft falloff width, so its 8-bit ratio
        // is accurate enough (reference 96px -> ~0.38px error).
        float spreadRatio = Math.max(0f, Math.min(1f, spreadPx / 96.0f));

        float ex = spread;
        float ey = spread;
        float ew = width + spread * 2f;
        float eh = height + spread * 2f;
        float z = radiusPx;

        // Glow halos extend outside the source rectangle. A per-element bounds
        // rect built from the padded shape clipped the visible outside part in
        // the 1.21.11 GUI batch, so keep a full-screen bounds and let the
        // scissor (when present) do the clipping.
        ScreenRect bounds = new ScreenRect(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight());

        ScreenRect scissor = context.scissorStack != null ? context.scissorStack.peekLast() : null;
        context.state.addSimpleElement(new SimpleGuiElementRenderState() {
            @Override
            public void setupVertices(VertexConsumer consumer) {
                Vector2f p0 = pose.transformPosition(x - ex, y - ey, new Vector2f());
                Vector2f p1 = pose.transformPosition(x - ex, y - ey + eh, new Vector2f());
                Vector2f p2 = pose.transformPosition(x - ex + ew, y - ey + eh, new Vector2f());
                Vector2f p3 = pose.transformPosition(x - ex + ew, y - ey, new Vector2f());

                consumer.vertex(p0.x, p0.y, z).texture(0f, 0f).color(argb).normal(spreadRatio, 0f, 0f);
                consumer.vertex(p1.x, p1.y, z).texture(0f, 1f).color(argb).normal(spreadRatio, 0f, 0f);
                consumer.vertex(p2.x, p2.y, z).texture(1f, 1f).color(argb).normal(spreadRatio, 0f, 0f);
                consumer.vertex(p3.x, p3.y, z).texture(1f, 0f).color(argb).normal(spreadRatio, 0f, 0f);
            }

            @Override
            public RenderPipeline pipeline() {
                return PIPELINE;
            }

            @Override
            public TextureSetup textureSetup() {
                return TextureSetup.empty();
            }

            @Override
            public ScreenRect scissorArea() {
                return scissor;
            }

            @Override
            public ScreenRect bounds() {
                return bounds;
            }
        });
    }
}
