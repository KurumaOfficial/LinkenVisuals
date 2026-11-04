package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import ru.method.linkenvisuals.LinkenVisuals;

/**
 * 1.21.11 pipeline definitions for the donor FogBlur post effect.
 *
 * <p>The donor used raw {@code ShaderProgram} + FBO state, which is not valid
 * inside the new frame-graph renderer: the shader source loader only accepts
 * core shaders, and a nested raw FBO draw after the world pass left the main
 * target black. These pipelines use the same shader math through the public
 * {@code RenderPass} API instead.
 */
public final class FogBlurPipelines {
    /** std140 {@code BlurData}: params@0, gauss@16, flags@32 (48 bytes). */
    public static final int BLUR_UNIFORM_SIZE = 48;
    /** std140 {@code CompositeData}: depth@0, tint@16, effect@32, view@48 (64 bytes). */
    public static final int COMPOSITE_UNIFORM_SIZE = 64;
    /** std140 {@code TintData}: tint@0, params@16, view@32 (48 bytes). */
    public static final int TINT_UNIFORM_SIZE = 48;

    public static final RenderPipeline TINT = RenderPipelines.register(
        RenderPipeline.builder()
            .withLocation(LinkenVisuals.id("pipeline/fog_tint"))
            .withVertexShader(LinkenVisuals.id("core/downsample"))
            .withFragmentShader(LinkenVisuals.id("core/fog_tint"))
            .withSampler("Sampler0")
            .withUniform("TintData", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    public static final RenderPipeline BLUR = RenderPipelines.register(
        RenderPipeline.builder()
            .withLocation(LinkenVisuals.id("pipeline/fog_blur"))
            .withVertexShader(LinkenVisuals.id("core/downsample"))
            .withFragmentShader(LinkenVisuals.id("core/fog_blur"))
            .withSampler("Sampler0")
            .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    public static final RenderPipeline COMPOSITE = RenderPipelines.register(
        RenderPipeline.builder()
            .withLocation(LinkenVisuals.id("pipeline/fog_composite"))
            .withVertexShader(LinkenVisuals.id("core/downsample"))
            .withFragmentShader(LinkenVisuals.id("core/fog_composite"))
            .withSampler("ColorSampler")
            .withSampler("DepthSampler")
            .withUniform("CompositeData", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private FogBlurPipelines() {
    }

    public static void init() {
    }
}
