package dev.linkenvisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.LinkenVisuals;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;

/** Textured world-space billboard pipeline for the Particles module. */
public final class ParticlesPipelines {
    public static final RenderPipeline BILLBOARD = Pipelines.register(
        RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/particles_billboard"))
            .withVertexShader(LinkenVisuals.id("core/particle_billboard"))
            .withFragmentShader(LinkenVisuals.id("core/particle_billboard"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private ParticlesPipelines() {
    }

    public static void init() {
    }
}
