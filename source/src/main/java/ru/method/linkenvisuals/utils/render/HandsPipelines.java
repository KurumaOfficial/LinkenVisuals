package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.Hands;

/** 1.21.11 pipelines for the donor Hands post effects. */
public final class HandsPipelines {
    /**
     * std140 {@code HandsData}: colorA@0, colorB@16, params0@32, params1@48,
     * screenSize@64, flags@80 (96 bytes).
     */
    public static final int UNIFORM_SIZE = 96;

    public static final RenderPipeline SOLID = build("hands_solid");
    public static final RenderPipeline PLASMA = build("hands_plasma");
    public static final RenderPipeline NEBULA = build("hands_nebula");
    public static final RenderPipeline COBWEB = build("hands_cobweb");
    public static final RenderPipeline GLASS = build("hands_glass");

    private HandsPipelines() {
    }

    private static RenderPipeline build(String shader) {
        return RenderPipelines.register(
            RenderPipeline.builder()
                .withLocation(LinkenVisuals.id("pipeline/" + shader))
                .withVertexShader(LinkenVisuals.id("core/downsample"))
                .withFragmentShader(LinkenVisuals.id("core/" + shader))
                .withSampler("ColorTexture")
                .withSampler("DepthTexture")
                .withSampler("BeforeColorTexture")
                .withUniform("HandsData", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build());
    }

    public static RenderPipeline pipeline(Hands.Mode mode) {
        return switch (mode) {
            case Transparent -> GLASS;
            case Plasma -> PLASMA;
            case Nebula -> NEBULA;
            case Cobweb -> COBWEB;
            default -> SOLID;
        };
    }

    public static void init() {
    }
}
