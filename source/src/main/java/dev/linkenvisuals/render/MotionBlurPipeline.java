package dev.linkenvisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.LinkenVisuals;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;

/**
 * Motion blur pipeline (Phase 5.4): fullscreen blend of current and previous
 * frames in linear space. No raw GL — pure RenderPipeline + UBO.
 *
 * <p>UBO layout (std140, 16 bytes):
 * <pre>
 *   float strength; // @0 persistence factor (0..0.95)
 * </pre>
 */
public final class MotionBlurPipeline {
    static final int UNIFORM_SIZE = 16;

    static final RenderPipeline PIPELINE = Pipelines.register(
        RenderPipeline.builder()
            .withLocation(LinkenVisuals.id("pipeline/motion_blur"))
            .withVertexShader(LinkenVisuals.id("core/motion_blur"))
            .withFragmentShader(LinkenVisuals.id("core/motion_blur"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)
            .withUniform("MotionBlur", UniformType.UNIFORM_BUFFER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private MotionBlurPipeline() {
    }

    public static void init() {
    }
}
