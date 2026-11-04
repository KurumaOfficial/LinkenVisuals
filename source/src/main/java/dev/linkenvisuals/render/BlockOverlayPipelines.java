package dev.linkenvisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.module.impl.BlockOverlayModule;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pipelines for the BlockOverlay module (Phase 5.2). Nine animated shader
 * fills — GLSL taken as text from the donor, JSON programs are dead on
 * 1.21.11 — plus a flat fill and a line outline. All drawn via an explicit
 * command-encoder pass at {@code END_MAIN}, like the other UBO renderers.
 *
 * <p><b>TEMPORARILY DISABLED / NEEDS FIXING.</b> The module is hidden from
 * the GUI; keep these pipelines and shaders for the eventual fix.
 */
public final class BlockOverlayPipelines {
    /**
     * std140 {@code BlockOverlay} block: time@0, screenSize@8, baseColor@16,
     * alpha@32, zoomFactor@36 (rounded up to 48).
     */
    public static final int UNIFORM_SIZE = 48;

    private static final Map<BlockOverlayModule.FillMode, RenderPipeline> SHADERS =
        new EnumMap<>(BlockOverlayModule.FillMode.class);

    public static final RenderPipeline FLAT = Pipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/block_overlay_flat"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    public static final RenderPipeline LINES = Pipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/block_overlay_lines"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.LINES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    /**
     * HitRange with its donor "Depth Test" switch off. A separate pipeline
     * keeps the world pass free of global depth-state mutations.
     */
    public static final RenderPipeline FLAT_NO_DEPTH = Pipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/hit_range_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    static {
        for (BlockOverlayModule.FillMode mode : BlockOverlayModule.FillMode.values()) {
            String name = mode.shaderName();
            SHADERS.put(mode, Pipelines.register(
                RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(LinkenVisuals.id("pipeline/block_overlay_" + name))
                    .withVertexShader(LinkenVisuals.id("core/block_overlay"))
                    .withFragmentShader(LinkenVisuals.id("core/block_overlay_" + name))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BlockOverlay", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()));
        }
    }

    private BlockOverlayPipelines() {
    }

    public static void init() {
    }

    public static RenderPipeline shader(BlockOverlayModule.FillMode mode) {
        return SHADERS.get(mode);
    }
}
