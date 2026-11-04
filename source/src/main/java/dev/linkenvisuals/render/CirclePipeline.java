package dev.linkenvisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.linkenvisuals.LinkenVisuals;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public final class CirclePipeline {
    private static final Identifier WHITE = LinkenVisuals.id("textures/gui/white.png");

    private static final RenderPipeline PIPELINE = Pipelines.register(
        RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/circle"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(LinkenVisuals.id("core/circle"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build());

    private CirclePipeline() {
    }

    public static void init() {
    }

    public static void draw(DrawContext context, float centerX, float centerY, float radius, int argb) {
        if (context == null || radius <= 0.0f) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        AbstractTexture texture = mc != null ? mc.getTextureManager().getTexture(WHITE) : null;
        if (texture == null || texture.getGlTextureView() == null) {
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect scissor = context.scissorStack != null ? context.scissorStack.peekLast() : null;

        float x0 = centerX - radius;
        float y0 = centerY - radius;
        float size = radius * 2.0f;

        ScreenRect bounds = new ScreenRect(
            (int) Math.floor(x0),
            (int) Math.floor(y0),
            (int) Math.ceil(size) + 1,
            (int) Math.ceil(size) + 1
        ).transformEachVertex(pose);

        TextureSetup textureSetup = TextureSetup.of(texture.getGlTextureView(), texture.getSampler());

        context.state.addSimpleElement(new SimpleGuiElementRenderState() {
            @Override
            public void setupVertices(VertexConsumer consumer) {
                Vector2f p0 = pose.transformPosition(x0, y0, new Vector2f());
                Vector2f p1 = pose.transformPosition(x0, y0 + size, new Vector2f());
                Vector2f p2 = pose.transformPosition(x0 + size, y0 + size, new Vector2f());
                Vector2f p3 = pose.transformPosition(x0 + size, y0, new Vector2f());

                consumer.vertex(p0.x, p0.y, 0f).texture(0f, 0f).color(argb);
                consumer.vertex(p1.x, p1.y, 0f).texture(0f, 1f).color(argb);
                consumer.vertex(p2.x, p2.y, 0f).texture(1f, 1f).color(argb);
                consumer.vertex(p3.x, p3.y, 0f).texture(1f, 0f).color(argb);
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
