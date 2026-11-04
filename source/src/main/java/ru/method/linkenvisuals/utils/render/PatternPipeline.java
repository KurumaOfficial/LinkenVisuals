package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.render.Pipelines;
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
 * Batched procedural GUI patterns (donor dot-grid and diagonal hatch).
 * Both are drawn as one quad / one draw call instead of a draw call per dot
 * or per line.
 */
public final class PatternPipeline {

    private static RenderPipeline pipeline(String name) {
        return Pipelines.register(
                RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                        .withLocation(LinkenVisuals.id("pipeline/" + name))
                        .withVertexShader(LinkenVisuals.id("core/pattern"))
                        .withFragmentShader(LinkenVisuals.id("core/" + name))
                        .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,
                                com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withCull(false)
                        .build());
    }

    private static final RenderPipeline DOT_GRID = pipeline("dot_grid");
    private static final RenderPipeline HATCH = pipeline("hatch");

    private PatternPipeline() {
    }

    public static void init() {
    }

    public static void dotGrid(DrawContext context, float x, float y, float width, float height,
                               float step, float inset, int argb) {
        draw(context, DOT_GRID, x, y, width, height, step, inset, 0f, argb);
    }

    public static void hatch(DrawContext context, float x, float y, float width, float height,
                             float step, float thickness, int argb) {
        draw(context, HATCH, x, y, width, height, step, thickness, 0f, argb);
    }

    private static void draw(DrawContext context, RenderPipeline pipeline,
                             float x, float y, float width, float height,
                             float step, float second, float third, int argb) {
        if (context == null || width <= 0f || height <= 0f || step <= 0f) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        float guiScale = mc != null && mc.getWindow() != null ? (float) mc.getWindow().getScaleFactor() : 1.0f;

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect bounds = new ScreenRect(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.ceil(width),
                (int) Math.ceil(height)
        ).transformEachVertex(pose);
        ScreenRect scissor = context.scissorStack != null ? context.scissorStack.peekLast() : null;

        float stepPx = step * guiScale;
        float secondPx = second * guiScale;
        float thirdPx = third * guiScale;

        context.state.addSimpleElement(new SimpleGuiElementRenderState() {
            @Override
            public void setupVertices(VertexConsumer consumer) {
                Vector2f p0 = pose.transformPosition(x, y, new Vector2f());
                Vector2f p1 = pose.transformPosition(x, y + height, new Vector2f());
                Vector2f p2 = pose.transformPosition(x + width, y + height, new Vector2f());
                Vector2f p3 = pose.transformPosition(x + width, y, new Vector2f());

                consumer.vertex(p0.x, p0.y, 0f).texture(0f, 0f).color(argb)
                        .normal(stepPx, secondPx, thirdPx);
                consumer.vertex(p1.x, p1.y, 0f).texture(0f, 1f).color(argb)
                        .normal(stepPx, secondPx, thirdPx);
                consumer.vertex(p2.x, p2.y, 0f).texture(1f, 1f).color(argb)
                        .normal(stepPx, secondPx, thirdPx);
                consumer.vertex(p3.x, p3.y, 0f).texture(1f, 0f).color(argb)
                        .normal(stepPx, secondPx, thirdPx);
            }

            @Override
            public RenderPipeline pipeline() {
                return pipeline;
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
