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
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import ru.method.linkenvisuals.LinkenVisuals;

import java.util.List;

/**
 * Batched soft circles for the cursor trail. The donor drew every trail dot
 * with its own draw call; at 2560x1440 that alone halved ClickGUI FPS. All
 * dots now go through one GUI element / one pipeline draw.
 */
public final class CircleBatchPipeline {

    private static final Identifier WHITE = LinkenVisuals.id("textures/gui/white.png");

    private static final RenderPipeline PIPELINE = Pipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(LinkenVisuals.id("pipeline/circle_batch"))
                    .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
                    .withFragmentShader(LinkenVisuals.id("core/circle"))
                    .withSampler("Sampler0")
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private CircleBatchPipeline() {
    }

    public record Dot(float x, float y, float radius, int argb) {
    }

    public static void draw(DrawContext context, List<Dot> dots) {
        if (context == null || dots == null || dots.isEmpty()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        AbstractTexture texture = mc != null ? mc.getTextureManager().getTexture(WHITE) : null;
        if (texture == null || texture.getGlTextureView() == null) {
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect scissor = context.scissorStack != null ? context.scissorStack.peekLast() : null;

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Dot dot : dots) {
            minX = Math.min(minX, dot.x - dot.radius);
            minY = Math.min(minY, dot.y - dot.radius);
            maxX = Math.max(maxX, dot.x + dot.radius);
            maxY = Math.max(maxY, dot.y + dot.radius);
        }
        ScreenRect bounds = new ScreenRect(
                (int) Math.floor(minX) - 1,
                (int) Math.floor(minY) - 1,
                (int) Math.ceil(maxX - minX) + 2,
                (int) Math.ceil(maxY - minY) + 2
        ).transformEachVertex(pose);

        TextureSetup textureSetup = TextureSetup.of(texture.getGlTextureView(), texture.getSampler());

        context.state.addSimpleElement(new SimpleGuiElementRenderState() {
            @Override
            public void setupVertices(VertexConsumer consumer) {
                for (Dot dot : dots) {
                    Vector2f p0 = pose.transformPosition(dot.x - dot.radius, dot.y - dot.radius, new Vector2f());
                    Vector2f p1 = pose.transformPosition(dot.x - dot.radius, dot.y + dot.radius, new Vector2f());
                    Vector2f p2 = pose.transformPosition(dot.x + dot.radius, dot.y + dot.radius, new Vector2f());
                    Vector2f p3 = pose.transformPosition(dot.x + dot.radius, dot.y - dot.radius, new Vector2f());

                    consumer.vertex(p0.x, p0.y, 0f).texture(0f, 0f).color(dot.argb);
                    consumer.vertex(p1.x, p1.y, 0f).texture(0f, 1f).color(dot.argb);
                    consumer.vertex(p2.x, p2.y, 0f).texture(1f, 1f).color(dot.argb);
                    consumer.vertex(p3.x, p3.y, 0f).texture(1f, 0f).color(dot.argb);
                }
            }

            @Override
            public RenderPipeline pipeline() {
                return PIPELINE;
            }

            @Override
            public TextureSetup textureSetup() {
                return textureSetup;
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
