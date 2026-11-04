package dev.linkenvisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.linkenvisuals.LinkenVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
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

public final class SdfPipeline {
    private static final RenderPipeline PIPELINE = Pipelines.register(
        RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/sdf_glyph"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(LinkenVisuals.id("core/icon"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build());

    private SdfPipeline() {
    }

    public static void init() {
    }

    public static void draw(DrawContext context, GlyphAtlas atlas, GlyphAtlas.Glyph glyph,
                            float x, float y, float width, float height, int argb) {
        if (!glyph.visible || glyph.regionWidth <= 0 || glyph.regionHeight <= 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        AbstractTexture texture = client.getTextureManager().getTexture(atlas.texture());
        if (texture == null || texture.getGlTextureView() == null) {
            return;
        }

        // Font atlases default to NEAREST minification. MSDF glyphs are almost
        // always drawn smaller than their atlas region, so nearest sampling
        // snapped the distance field to texels and made every label look
        // pixelated. Sample the glyph atlas with bilinear filtering; the SDF
        // shader still produces a crisp edge from the interpolated field.
        GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        TextureSetup textureSetup = TextureSetup.of(texture.getGlTextureView(), linear);

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect bounds = new ScreenRect(
            (int) Math.floor(x),
            (int) Math.floor(y),
            Math.max(1, (int) Math.ceil(width)),
            Math.max(1, (int) Math.ceil(height))
        ).transformEachVertex(pose);
        ScreenRect scissor = context.scissorStack != null ? context.scissorStack.peekLast() : null;

        float u0 = glyph.u / atlas.atlasWidth();
        float v0 = glyph.v / atlas.atlasHeight();
        float u1 = (glyph.u + glyph.regionWidth) / atlas.atlasWidth();
        float v1 = (glyph.v + glyph.regionHeight) / atlas.atlasHeight();

        context.state.addSimpleElement(new SimpleGuiElementRenderState() {
            @Override
            public void setupVertices(VertexConsumer consumer) {
                Vector2f p0 = pose.transformPosition(x, y, new Vector2f());
                Vector2f p1 = pose.transformPosition(x, y + height, new Vector2f());
                Vector2f p2 = pose.transformPosition(x + width, y + height, new Vector2f());
                Vector2f p3 = pose.transformPosition(x + width, y, new Vector2f());

                consumer.vertex(p0.x, p0.y, 0f).texture(u0, v0).color(argb);
                consumer.vertex(p1.x, p1.y, 0f).texture(u0, v1).color(argb);
                consumer.vertex(p2.x, p2.y, 0f).texture(u1, v1).color(argb);
                consumer.vertex(p3.x, p3.y, 0f).texture(u1, v0).color(argb);
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
