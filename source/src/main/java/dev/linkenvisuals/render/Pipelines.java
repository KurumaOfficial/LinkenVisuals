package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.RenderPipelines;
import ru.method.linkenvisuals.utils.render.RoundedTexturePipeline;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public final class Pipelines {
    private static final List<RenderPipeline> OWNED = new ArrayList<>();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static volatile boolean resourcesReady;
    private static boolean warmed;

    private Pipelines() {
    }

    public static RenderPipeline register(RenderPipeline pipeline) {
        OWNED.add(pipeline);
        return RenderPipelines.register(pipeline);
    }

    /**
     * Writes the {@code DynamicTransforms} slice for the current model view.
     * Must be called BEFORE opening a render pass: the write maps the uniform
     * buffer, and the command encoder rejects buffer writes while a pass is
     * open ("Close the existing render pass before performing additional
     * commands"). Vanilla's {@code RenderLayer.draw} does the same.
     */
    public static GpuBufferSlice writeWorldTransforms() {
        return RenderSystem.getDynamicUniforms().write(
            RenderSystem.getModelViewMatrix(),
            new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
            new Vector3f(),
            IDENTITY);
    }

    /**
     * Binds the default UBOs plus a pre-written {@code DynamicTransforms}
     * slice for a custom world-space encoder pass. Vanilla binds
     * {@code ModelViewMat} for every draw automatically; encoder passes that
     * skip it render degenerate (invisible) geometry without any error in the
     * log.
     */
    public static void bindWorldUniforms(RenderPass pass, GpuBufferSlice transforms) {
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
    }

    /**
     * Called by the client reload listener once the initial shader sources are
     * available. Drawing or precompiling before this point logs
     * "Couldn't find source" for custom core shaders.
     */
    public static void markResourcesReady() {
        resourcesReady = true;
    }

    public static boolean resourcesReady() {
        return resourcesReady;
    }

    public static void warmUp() {
        if (warmed || RenderSystem.getDevice() == null || !resourcesReady) {
            return;
        }
        warmed = true;

        BlitPipeline.init();
        // BlockOverlay still uses the donor renderer/pipelines while the legacy
        // bridge is active. Do not register a second set under the same ids.
        CardPipeline.init();
        CirclePipeline.init();
        LiquidGlassPipeline.init();
        MotionBlurPipeline.init();
        TrailsRenderer.init();
        JumpCirclesRenderer.init();
        ParticlesPipelines.init();
        TrajectoriesRenderer.init();
        WaypointsRenderer.init();
        ColorFieldPipeline.init();
        DownsamplePipeline.init();
        GlassPanelPipeline.init();
        GlassWindowPipeline.init();
        GlowPipeline.init();
        GuiModelIconPipeline.init();
        IconPipeline.init();
        PanelPipeline.init();
        RoundedTexturePipeline.init();
        RoundedRectPipeline.init();
        ru.method.linkenvisuals.utils.render.RoundedGlowPipeline.init();
        SdfPipeline.init();
        TargetEspPipelines.init();

        for (RenderPipeline pipeline : OWNED) {
            try {
                RenderSystem.getDevice().precompilePipeline(pipeline);
            } catch (RuntimeException failed) {
                dev.linkenvisuals.LinkenVisuals.LOGGER.debug("Pipeline precompilation deferred: {}", failed.getMessage());
            }
        }
    }
}
