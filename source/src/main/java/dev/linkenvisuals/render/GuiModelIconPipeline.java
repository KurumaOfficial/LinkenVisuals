package dev.linkenvisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.LinkenVisuals;
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

import java.util.List;

/**
 * Plain textured GUI quads used by the software-projected 3D cosmetic icons. One element holds all
 * quads of a model, already sorted back-to-front by the icon renderer.
 */
public final class GuiModelIconPipeline {

    public record Quad(float[] xy, float[] uv, int[] colors, float depth) {
    }

    private static final RenderPipeline PIPELINE = Pipelines.register(
        RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/gui_model_icon"))
            .withVertexShader(LinkenVisuals.id("core/gui_model_icon"))
            .withFragmentShader(LinkenVisuals.id("core/gui_model_icon"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build());

    private GuiModelIconPipeline() {
    }

    public static void init() {
    }

    public static void draw(DrawContext context, Identifier textureId, float x, float y, float width, float height,
                            List<Quad> quads) {
        if (context == null || textureId == null || quads.isEmpty() || width <= 0f || height <= 0f) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
        var view = texture.getGlTextureView();
        if (view == null) {
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect bounds = new ScreenRect(
            (int) Math.floor(x),
            (int) Math.floor(y),
            (int) Math.ceil(width),
            (int) Math.ceil(height)
        ).transformEachVertex(pose);
        ScreenRect scissor = context.scissorStack != null ? context.scissorStack.peekLast() : null;

        context.state.addSimpleElement(new SimpleGuiElementRenderState() {
            @Override
            public void setupVertices(VertexConsumer consumer) {
                for (Quad quad : quads) {
                    for (int i = 0; i < 4; i++) {
                        Vector2f p = pose.transformPosition(quad.xy()[i * 2], quad.xy()[i * 2 + 1], new Vector2f());
                        consumer.vertex(p.x, p.y, 0f)
                            .texture(quad.uv()[i * 2], quad.uv()[i * 2 + 1])
                            .color(quad.colors()[i]);
                    }
                }
            }

            @Override
            public RenderPipeline pipeline() {
                return PIPELINE;
            }

            @Override
            public TextureSetup textureSetup() {
                return TextureSetup.of(view, texture.getSampler());
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
