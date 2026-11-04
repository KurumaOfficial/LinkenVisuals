package dev.linkenvisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.ui.Theme;
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

public final class RoundedRectPipeline {
    private static final Identifier WHITE = LinkenVisuals.id("textures/gui/white.png");

    private static final RenderPipeline FILL = build("rounded_rect", "core/rounded_rect");
    private static final RenderPipeline OUTLINE = build("rounded_rect_outline", "core/rounded_rect_outline");
    private static final RenderPipeline BORDER = buildBorder();
    private static final RenderPipeline COBWEB_FILL = buildCobwebFill();

    private RoundedRectPipeline() {
    }

    public static void init() {
    }

    private static RenderPipeline build(String name, String shader) {
        return Pipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(LinkenVisuals.id("pipeline/" + name))
                .withVertexShader(LinkenVisuals.id("core/rounded_rect"))
                .withFragmentShader(LinkenVisuals.id(shader))
                .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .build());
    }

    /** Panel fill with the donor cobweb shader baked into the same draw call, so the GUI
     *  pipeline batching keeps it behind the content and the dark overlay. */
    private static RenderPipeline buildCobwebFill() {
        return Pipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(LinkenVisuals.id("pipeline/rounded_rect_cobweb"))
                .withVertexShader(LinkenVisuals.id("core/rounded_rect_cobweb"))
                .withFragmentShader(LinkenVisuals.id("core/rounded_rect_cobweb"))
                .withSampler("Sampler0")
                .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .build());
    }

    private static RenderPipeline buildBorder() {
        return Pipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(LinkenVisuals.id("pipeline/rounded_rect_border"))
                .withVertexShader(LinkenVisuals.id("core/rounded_rect_border"))
                .withFragmentShader(LinkenVisuals.id("core/rounded_rect_border"))
                .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,
                    com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .build());
    }

    public static void draw(DrawContext context, float x, float y, float width, float height, int argb) {
        quad(context, FILL, x, y, width, height, (float) Theme.radiusLevel(), 0.0f, argb);
    }

    public static void draw(DrawContext context, float x, float y, float width, float height, float radius, int argb) {
        quad(context, FILL, x, y, width, height, radius, 0.0f, argb);
    }

    /** Dark glass panel + animated cobweb shader (1.21.4 cosmetics/command-delay backdrop). */
    public static void drawPanelBackground(DrawContext context, float x, float y, float width, float height,
                                           float radius, int argb) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        float scale = matrixScale(pose);
        float radiusPx = radius * scale;
        float z = radiusPx;

        ScreenRect bounds = new ScreenRect(
            (int) Math.floor(x),
            (int) Math.floor(y),
            (int) Math.ceil(width),
            (int) Math.ceil(height)
        ).transformEachVertex(pose);
        ScreenRect scissor = context.scissorStack != null ? context.scissorStack.peekLast() : null;
        dev.linkenvisuals.render.CobwebTimeTexture.update();
        var phaseTexture = MinecraftClient.getInstance() == null ? null
                : MinecraftClient.getInstance().getTextureManager()
                        .getTexture(dev.linkenvisuals.render.CobwebTimeTexture.id());
        var phaseView = phaseTexture == null ? null : phaseTexture.getGlTextureView();

        context.state.addSimpleElement(new SimpleGuiElementRenderState() {
            @Override
            public void setupVertices(VertexConsumer consumer) {
                Vector2f p0 = pose.transformPosition(x, y, new Vector2f());
                Vector2f p1 = pose.transformPosition(x, y + height, new Vector2f());
                Vector2f p2 = pose.transformPosition(x + width, y + height, new Vector2f());
                Vector2f p3 = pose.transformPosition(x + width, y, new Vector2f());

                consumer.vertex(p0.x, p0.y, z).texture(0.0f, 0.0f).color(argb).normal(0.0f, 1.0f, 0.0f);
                consumer.vertex(p1.x, p1.y, z).texture(0.0f, 1.0f).color(argb).normal(0.0f, 1.0f, 0.0f);
                consumer.vertex(p2.x, p2.y, z).texture(1.0f, 1.0f).color(argb).normal(0.0f, 1.0f, 0.0f);
                consumer.vertex(p3.x, p3.y, z).texture(1.0f, 0.0f).color(argb).normal(0.0f, 1.0f, 0.0f);
            }

            @Override
            public RenderPipeline pipeline() {
                return COBWEB_FILL;
            }

            @Override
            public TextureSetup textureSetup() {
                return phaseView == null ? TextureSetup.empty()
                        : TextureSetup.of(phaseView, phaseTexture.getSampler());
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

    public static void drawOutline(DrawContext context, float x, float y, float width, float height, int argb) {
        quad(context, OUTLINE, x, y, width, height, (float) Theme.radiusLevel(), 1.0f, argb);
    }

    public static void drawOutline(DrawContext context, float x, float y, float width, float height, float radius, int argb) {
        quad(context, OUTLINE, x, y, width, height, radius, 1.0f, argb);
    }

    public static void drawOutline(DrawContext context, float x, float y, float width, float height, float radius, float thickness, int argb) {
        quad(context, OUTLINE, x, y, width, height, radius, thickness, argb);
    }

    /** Per-corner coloured outline used by the donor's gradient borders. */
    public static void drawOutlineGradient(DrawContext context, float x, float y, float width, float height,
                                           float radius, float thickness,
                                           int topLeft, int bottomLeft, int bottomRight, int topRight) {
        quad(context, OUTLINE, x, y, width, height, radius, thickness,
                topLeft, bottomLeft, bottomRight, topRight);
    }

    public static void drawVerticalGradient(DrawContext context, float x, float y, float width, float height,
                                             float radius, int top, int bottom) {
        quad(context, FILL, x, y, width, height, radius, 0.0f, top, bottom, bottom, top);
    }

    public static void drawHorizontalGradient(DrawContext context, float x, float y, float width, float height,
                                               float radius, int left, int right) {
        quad(context, FILL, x, y, width, height, radius, 0.0f, left, left, right, right);
    }

    /**
     * Donor-compatible soft border: thickness and edge smoothness are passed
     * through vertex normals to the dedicated border shader. A thickness of
     * zero reproduces the donor {@code drawBorder} default.
     */
    public static void drawBorder(DrawContext context, float x, float y, float width, float height,
                                  float radius, float thickness, float internalSmoothness,
                                  float externalSmoothness, int argb) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        float scale = matrixScale(pose);
        float radiusPx = radius * scale;
        float thicknessPx = Math.max(0.0f, thickness * scale);
        float internalPx = Math.max(0.5f, internalSmoothness * scale);
        float externalPx = Math.max(0.5f, externalSmoothness * scale);

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

                consumer.vertex(p0.x, p0.y, z).texture(0.0f, 0.0f).color(argb)
                    .normal(thicknessPx, internalPx, externalPx);
                consumer.vertex(p1.x, p1.y, z).texture(0.0f, 1.0f).color(argb)
                    .normal(thicknessPx, internalPx, externalPx);
                consumer.vertex(p2.x, p2.y, z).texture(1.0f, 1.0f).color(argb)
                    .normal(thicknessPx, internalPx, externalPx);
                consumer.vertex(p3.x, p3.y, z).texture(1.0f, 0.0f).color(argb)
                    .normal(thicknessPx, internalPx, externalPx);
            }

            @Override
            public RenderPipeline pipeline() {
                return BORDER;
            }

            @Override
            public TextureSetup textureSetup() {
                return TextureSetup.empty();
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

    private static void quad(DrawContext context, RenderPipeline pipeline, float x, float y, float width, float height, float radius, float thickness, int argb) {
        quad(context, pipeline, x, y, width, height, radius, thickness, argb, argb, argb, argb);
    }

    private static void quad(DrawContext context, RenderPipeline pipeline, float x, float y, float width, float height,
                             float radius, float thickness, int topLeft, int bottomLeft, int bottomRight, int topRight) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        float scale = matrixScale(pose);
        float radiusPx = radius * scale;
        float thicknessPx = Math.max(0.01f, thickness * scale);
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

                consumer.vertex(p0.x, p0.y, z).texture(0.0f, 0.0f).color(topLeft).normal(thicknessPx, 0.0f, 0.0f);
                consumer.vertex(p1.x, p1.y, z).texture(0.0f, 1.0f).color(bottomLeft).normal(thicknessPx, 0.0f, 0.0f);
                consumer.vertex(p2.x, p2.y, z).texture(1.0f, 1.0f).color(bottomRight).normal(thicknessPx, 0.0f, 0.0f);
                consumer.vertex(p3.x, p3.y, z).texture(1.0f, 0.0f).color(topRight).normal(thicknessPx, 0.0f, 0.0f);
            }

            @Override
            public RenderPipeline pipeline() {
                return pipeline;
            }

            @Override
            public TextureSetup textureSetup() {
                return TextureSetup.empty();
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

    /**
     * Shader radii live in screen pixels (fwidth of the quad UVs), while callers pass design-space
     * radii. The GUI/ClickGUI canvas matrix can have any scale depending on the window size, so use
     * the actual matrix scale instead of the GUI scale factor — otherwise corners change size when
     * the window is resized.
     */
    public static float matrixScale(Matrix3x2f pose) {
        float sx = (float) Math.hypot(pose.m00(), pose.m01());
        float sy = (float) Math.hypot(pose.m10(), pose.m11());
        // Shader distances are in physical framebuffer pixels (fwidth on the
        // final vertex position), while the GUI matrix maps design units to
        // GUI-scaled units. Include the window scale factor so radii, strokes
        // and glows keep their donor size at every GUI scale.
        float guiScale = 1.0f;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            guiScale = (float) client.getWindow().getScaleFactor();
        }
        return Math.max(0.01f, (sx + sy) * 0.5f * guiScale);
    }
}
