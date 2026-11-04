package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
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

/**
 * Rounded textured GUI quad. The donor drew images through its rounded SDF
 * texture shader; 1.21.11's {@code DrawContext.drawTexture} has no radius, so
 * this pipeline restores the exact rounded mask while keeping the GUI render
 * state (ordering, scissor and transforms) intact.
 */
public final class RoundedTexturePipeline {

    private static final RenderPipeline PIPELINE = Pipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(LinkenVisuals.id("pipeline/rounded_texture"))
                    .withVertexShader(LinkenVisuals.id("core/rounded_texture"))
                    .withFragmentShader(LinkenVisuals.id("core/rounded_texture"))
                    .withSampler("Sampler0")
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,
                            com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private RoundedTexturePipeline() {
    }

    public static void init() {
    }

    public static void draw(DrawContext context, AbstractTexture texture, float x, float y,
                            float width, float height, float radius,
                            float u0, float v0, float u1, float v1, int argb) {
        draw(context, texture, x, y, width, height, radius, u0, v0, u1, v1, argb, true);
    }

    /** Pixel-art images (player skins/heads) must stay crisp: nearest filtering. */
    public static void drawNearest(DrawContext context, AbstractTexture texture, float x, float y,
                                   float width, float height, float radius,
                                   float u0, float v0, float u1, float v1, int argb) {
        draw(context, texture, x, y, width, height, radius, u0, v0, u1, v1, argb, false);
    }

    private static void draw(DrawContext context, AbstractTexture texture, float x, float y,
                             float width, float height, float radius,
                             float u0, float v0, float u1, float v1, int argb, boolean linear) {
        if (context == null || texture == null || width <= 0f || height <= 0f) {
            return;
        }
        var view = texture.getGlTextureView();
        if (view == null) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        float scale = dev.linkenvisuals.render.RoundedRectPipeline.matrixScale(pose);
        float radiusPx = Math.max(0f, radius) * scale;
        float z = radiusPx;

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
                Vector2f p0 = pose.transformPosition(x, y, new Vector2f());
                Vector2f p1 = pose.transformPosition(x, y + height, new Vector2f());
                Vector2f p2 = pose.transformPosition(x + width, y + height, new Vector2f());
                Vector2f p3 = pose.transformPosition(x + width, y, new Vector2f());

                consumer.vertex(p0.x, p0.y, z).texture(u0, v0).color(argb).normal(0f, 0f, 0f);
                consumer.vertex(p1.x, p1.y, z).texture(u0, v1).color(argb).normal(0f, 1f, 0f);
                consumer.vertex(p2.x, p2.y, z).texture(u1, v1).color(argb).normal(1f, 1f, 0f);
                consumer.vertex(p3.x, p3.y, z).texture(u1, v0).color(argb).normal(1f, 0f, 0f);
            }

            @Override
            public RenderPipeline pipeline() {
                return PIPELINE;
            }

            @Override
            public TextureSetup textureSetup() {
                // World markers/bloom images use bilinear filtering, but player
                // heads are 8x8 pixel-art regions and must stay sharp.
                return TextureSetup.of(view, RenderSystem.getSamplerCache()
                    .get(linear ? FilterMode.LINEAR : FilterMode.NEAREST));
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

    public static void draw(DrawContext context, Identifier texture, float x, float y,
                            float width, float height, float radius,
                            float u0, float v0, float u1, float v1, int argb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        AbstractTexture abstractTexture = mc.getTextureManager().getTexture(texture);
        draw(context, abstractTexture, x, y, width, height, radius, u0, v0, u1, v1, argb);
    }

    public static void drawNearest(DrawContext context, Identifier texture, float x, float y,
                                   float width, float height, float radius,
                                   float u0, float v0, float u1, float v1, int argb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        AbstractTexture abstractTexture = mc.getTextureManager().getTexture(texture);
        drawNearest(context, abstractTexture, x, y, width, height, radius, u0, v0, u1, v1, argb);
    }
}
