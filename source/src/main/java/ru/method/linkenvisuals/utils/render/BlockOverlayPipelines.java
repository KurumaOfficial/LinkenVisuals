package ru.method.linkenvisuals.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.render.Pipelines;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.BlockOverlay;

import java.util.EnumMap;
import java.util.Map;

/**
 * 1.21.11 RenderPipeline definitions for the donor BlockOverlay shaders.
 * The GLSL sources are the original donor effects; the JSON programs are not
 * used by the new renderer.
 *
 * <p><b>TEMPORARILY DISABLED / NEEDS FIXING.</b> The module is hidden from
 * the GUI; keep these pipelines and shaders for the eventual fix.
 */
public final class BlockOverlayPipelines {

    /** std140 {@code BlockOverlay} block: time@0, screenSize@8, baseColor@16, alpha@32, zoom@36. */
    public static final int UNIFORM_SIZE = 48;

    private static final Map<BlockOverlay.FillMode, RenderPipeline> SHADERS =
            new EnumMap<>(BlockOverlay.FillMode.class);

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

    static {
        for (BlockOverlay.FillMode mode : BlockOverlay.FillMode.values()) {
            String name = shaderName(mode);
            SHADERS.put(mode, Pipelines.register(
                    RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                            .withLocation(LinkenVisuals.id("pipeline/block_overlay_" + name))
                            .withVertexShader(LinkenVisuals.id("core/block_overlay"))
                            .withFragmentShader(LinkenVisuals.id("core/block_overlay_" + name))
                            // Raw encoder draws do not have the sequential
                            // index buffer that turns QUADS into triangles, so
                            // the fill must stay TRIANGLES (two triangles per
                            // face, six vertices).
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

    public static RenderPipeline shader(BlockOverlay.FillMode mode) {
        return SHADERS.get(mode);
    }

    private static String shaderName(BlockOverlay.FillMode mode) {
        return switch (mode) {
            case Aurora -> "aurora";
            case Plasma -> "plasma";
            case Cosmos -> "cosmos";
            case Cobweb -> "cobweb";
            case Fireworks -> "fireworks";
            case Galaxy -> "galaxy";
            case Nebula -> "nebula";
            case Starfield -> "starfield";
            case Stars -> "stars";
        };
    }
}
