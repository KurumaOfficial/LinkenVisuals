package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Render3DPipelines {
    // 1. Quads with depth test (HitRange, CustomHitBox, etc.)
    public static final RenderPipeline QUADS_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/render3d_quads_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(false)
            .build());
    public static final RenderLayer LAYER_QUADS_DEPTH = RenderLayer.of("linkenvisuals_quads_depth",
        RenderSetup.builder(QUADS_DEPTH).translucent().build());

    // 2. Quads without depth test
    public static final RenderPipeline QUADS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/render3d_quads_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());
    public static final RenderLayer LAYER_QUADS_NO_DEPTH = RenderLayer.of("linkenvisuals_quads_no_depth",
        RenderSetup.builder(QUADS_NO_DEPTH).translucent().build());

    // 3. Additive Quads (Shine) with depth test
    public static final RenderPipeline QUADS_SHINE_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/render3d_quads_shine_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());
    public static final RenderLayer LAYER_QUADS_SHINE_DEPTH = RenderLayer.of("linkenvisuals_quads_shine_depth",
        RenderSetup.builder(QUADS_SHINE_DEPTH).translucent().build());

    // 4. Additive Quads (Shine) without depth test
    public static final RenderPipeline QUADS_SHINE_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/render3d_quads_shine_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());
    public static final RenderLayer LAYER_QUADS_SHINE_NO_DEPTH = RenderLayer.of("linkenvisuals_quads_shine_no_depth",
        RenderSetup.builder(QUADS_SHINE_NO_DEPTH).translucent().build());

    // 5. Triangles with depth test (ChinaHat, TargetEsp crystals, etc.)
    public static final RenderPipeline TRIANGLES_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/render3d_triangles_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(false)
            .build());
    public static final RenderLayer LAYER_TRIANGLES_DEPTH = RenderLayer.of("linkenvisuals_triangles_depth",
        RenderSetup.builder(TRIANGLES_DEPTH).translucent().build());

    // 6. Lines with depth test
    public static final RenderPipeline LINES_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/render3d_lines_depth"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .build());
    public static final RenderLayer LAYER_LINES_DEPTH = RenderLayer.of("linkenvisuals_lines_depth",
        RenderSetup.builder(LINES_DEPTH).translucent().build());

    // 7. Lines without depth test
    public static final RenderPipeline LINES_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/render3d_lines_no_depth"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build());
    public static final RenderLayer LAYER_LINES_NO_DEPTH = RenderLayer.of("linkenvisuals_lines_no_depth",
        RenderSetup.builder(LINES_NO_DEPTH).translucent().build());

    // 8. Textured Billboards (Trails, JumpCircles, Particles, TargetEsp)
    public static final RenderPipeline TEXTURED_ADDITIVE_DEPTH = dev.linkenvisuals.render.ParticlesPipelines.BILLBOARD;

    public static final RenderPipeline TEXTURED_TRANSLUCENT_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/textured_translucent_depth"))
            .withVertexShader(Identifier.of("linkenvisuals", "core/particle_billboard"))
            .withFragmentShader(Identifier.of("linkenvisuals", "core/particle_billboard"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    public static final RenderPipeline TEXTURED_ADDITIVE_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/textured_additive_no_depth"))
            .withVertexShader(Identifier.of("linkenvisuals", "core/particle_billboard"))
            .withFragmentShader(Identifier.of("linkenvisuals", "core/particle_billboard"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    public static final RenderPipeline TEXTURED_TRANSLUCENT_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(Identifier.of("linkenvisuals", "pipeline/textured_translucent_no_depth"))
            .withVertexShader(Identifier.of("linkenvisuals", "core/particle_billboard"))
            .withFragmentShader(Identifier.of("linkenvisuals", "core/particle_billboard"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private static final Map<Identifier, RenderLayer> TEXTURED_ADDITIVE_DEPTH_LAYERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderLayer> TEXTURED_TRANSLUCENT_DEPTH_LAYERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderLayer> TEXTURED_ADDITIVE_NO_DEPTH_LAYERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderLayer> TEXTURED_TRANSLUCENT_NO_DEPTH_LAYERS = new ConcurrentHashMap<>();

    public static RenderLayer getTexturedLayer(Identifier texture, boolean depthTest, boolean additive) {
        // World billboards (JumpCircles/Trails/particles/TargetEsp) are almost
        // always minified. The default NEAREST minification snapped the circle
        // and bloom textures to texels and made them look pixelated; use the
        // engine's bilinear sampler for every world-space sprite.
        java.util.function.Supplier<net.minecraft.client.gl.GpuSampler> linear =
            () -> RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        if (depthTest) {
            if (additive) {
                return TEXTURED_ADDITIVE_DEPTH_LAYERS.computeIfAbsent(texture,
                    tex -> RenderLayer.of("linken_tex_add_depth_" + tex.getNamespace() + "_" + tex.getPath().replace('/', '_'),
                        RenderSetup.builder(TEXTURED_ADDITIVE_DEPTH).texture("Sampler0", tex, linear).build()));
            } else {
                return TEXTURED_TRANSLUCENT_DEPTH_LAYERS.computeIfAbsent(texture,
                    tex -> RenderLayer.of("linken_tex_trans_depth_" + tex.getNamespace() + "_" + tex.getPath().replace('/', '_'),
                        RenderSetup.builder(TEXTURED_TRANSLUCENT_DEPTH).texture("Sampler0", tex, linear).build()));
            }
        } else {
            if (additive) {
                return TEXTURED_ADDITIVE_NO_DEPTH_LAYERS.computeIfAbsent(texture,
                    tex -> RenderLayer.of("linken_tex_add_nodepth_" + tex.getNamespace() + "_" + tex.getPath().replace('/', '_'),
                        RenderSetup.builder(TEXTURED_ADDITIVE_NO_DEPTH).texture("Sampler0", tex, linear).translucent().build()));
            } else {
                return TEXTURED_TRANSLUCENT_NO_DEPTH_LAYERS.computeIfAbsent(texture,
                    tex -> RenderLayer.of("linken_tex_trans_nodepth_" + tex.getNamespace() + "_" + tex.getPath().replace('/', '_'),
                        RenderSetup.builder(TEXTURED_TRANSLUCENT_NO_DEPTH).texture("Sampler0", tex, linear).translucent().build()));
            }
        }
    }

    private Render3DPipelines() {
    }

    public static void init() {
    }
}
