package unkw.visualtest.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.render.Pipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import ru.method.linkenvisuals.LinkenVisuals;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MSDF text used by the visual-test HUD (watermark, coordinates, target,
 * effects, ...). Ported 1:1 from the 1.21.4 donor; the only adaptation is the
 * rendering backend: 1.21.11 exposes GUI primitives through pipelines and
 * {@link DrawContext} render states instead of {@code RenderLayer}.
 */
public final class RoundFontRenderer {
    private static final Identifier METRICS = Identifier.of("visual-test", "fontdata/round.json");
    private static final Identifier ATLAS = Identifier.of("visual-test", "textures/font/round.png");

    private static final RenderPipeline PIPELINE = Pipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(LinkenVisuals.id("pipeline/msdf_text"))
                    .withVertexShader(LinkenVisuals.id("core/msdf_text"))
                    .withFragmentShader(LinkenVisuals.id("core/msdf_text"))
                    .withSampler("Sampler0")
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,
                            VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private static final List<TextCommand> COMMANDS = new ArrayList<>();
    private static FontData data;

    private RoundFontRenderer() {
    }

    public static void beginFrame() {
        COMMANDS.clear();
    }

    public static void queue(String value, float x, float y, float size, int color) {
        if (value != null && !value.isEmpty() && size > 0.0F) {
            COMMANDS.add(new TextCommand(value, x, y, size, color));
        }
    }

    public static float width(String value, float size) {
        if (value == null || value.isEmpty() || size <= 0.0F) {
            return 0.0F;
        }
        FontData font = data();
        float lineWidth = 0.0F;
        float maxWidth = 0.0F;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                maxWidth = Math.max(maxWidth, lineWidth);
                lineWidth = 0.0F;
            } else if (codePoint != '\r') {
                lineWidth += font.glyph(codePoint).advance() * size;
            }
        }
        return Math.max(maxWidth, lineWidth);
    }

    public static void flush(DrawContext context) {
        if (COMMANDS.isEmpty() || context == null) {
            return;
        }
        FontData font = data();
        MinecraftClient mc = MinecraftClient.getInstance();
        AbstractTexture atlas = mc != null ? mc.getTextureManager().getTexture(ATLAS) : null;
        if (atlas == null || atlas.getGlTextureView() == null) {
            COMMANDS.clear();
            return;
        }

        List<Quad> quads = new ArrayList<>();
        for (TextCommand command : COMMANDS) {
            collectCommand(quads, font, command);
        }
        COMMANDS.clear();
        if (quads.isEmpty()) {
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect scissor = context.scissorStack != null ? context.scissorStack.peekLast() : null;

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Quad quad : quads) {
            minX = Math.min(minX, Math.min(quad.left, quad.right));
            minY = Math.min(minY, Math.min(quad.top, quad.bottom));
            maxX = Math.max(maxX, Math.max(quad.left, quad.right));
            maxY = Math.max(maxY, Math.max(quad.top, quad.bottom));
        }
        ScreenRect bounds = new ScreenRect(
                (int) Math.floor(minX) - 1,
                (int) Math.floor(minY) - 1,
                (int) Math.ceil(maxX - minX) + 2,
                (int) Math.ceil(maxY - minY) + 2
        ).transformEachVertex(pose);

        TextureSetup textureSetup = TextureSetup.of(atlas.getGlTextureView(), atlas.getSampler());

        context.state.addSimpleElement(new SimpleGuiElementRenderState() {
            @Override
            public void setupVertices(VertexConsumer consumer) {
                for (Quad quad : quads) {
                    Vector2f p0 = pose.transformPosition(quad.left, quad.top, new Vector2f());
                    Vector2f p1 = pose.transformPosition(quad.left, quad.bottom, new Vector2f());
                    Vector2f p2 = pose.transformPosition(quad.right, quad.bottom, new Vector2f());
                    Vector2f p3 = pose.transformPosition(quad.right, quad.top, new Vector2f());

                    consumer.vertex(p0.x, p0.y, 0.0f).texture(quad.u0, quad.v0).color(quad.color).normal(0f, 0f, 0f);
                    consumer.vertex(p1.x, p1.y, 0.0f).texture(quad.u0, quad.v1).color(quad.color).normal(0f, 1f, 0f);
                    consumer.vertex(p2.x, p2.y, 0.0f).texture(quad.u1, quad.v1).color(quad.color).normal(1f, 1f, 0f);
                    consumer.vertex(p3.x, p3.y, 0.0f).texture(quad.u1, quad.v0).color(quad.color).normal(1f, 0f, 0f);
                }
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

    private static void collectCommand(List<Quad> quads, FontData font, TextCommand command) {
        float penX = command.x();
        float baseline = command.y() + font.ascender() * command.size();
        for (int offset = 0; offset < command.value().length(); ) {
            int codePoint = command.value().codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                penX = command.x();
                baseline += font.lineHeight() * command.size();
                continue;
            }
            if (codePoint == '\r') {
                continue;
            }
            Glyph glyph = font.glyph(codePoint);
            if (glyph.plane() != null && glyph.atlas() != null) {
                float left = penX + glyph.plane().left() * command.size();
                float right = penX + glyph.plane().right() * command.size();
                float top = baseline - glyph.plane().top() * command.size();
                float bottom = baseline - glyph.plane().bottom() * command.size();
                float uLeft = glyph.atlas().left() / font.atlasWidth();
                float uRight = glyph.atlas().right() / font.atlasWidth();
                float vTop = (font.atlasHeight() - glyph.atlas().top()) / font.atlasHeight();
                float vBottom = (font.atlasHeight() - glyph.atlas().bottom()) / font.atlasHeight();
                quads.add(new Quad(left, top, right, bottom, uLeft, vTop, uRight, vBottom, command.color()));
            }
            penX += glyph.advance() * command.size();
        }
    }

    private static FontData data() {
        if (data == null) {
            data = loadData();
        }
        return data;
    }

    private static FontData loadData() {
        Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(METRICS)
                .orElseThrow(() -> new IllegalStateException("Missing round font metrics"));
        try (Reader reader = resource.getReader()) {
            return FontData.read(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read round font metrics", exception);
        }
    }

    private record TextCommand(String value, float x, float y, float size, int color) {
    }

    private record Quad(float left, float top, float right, float bottom,
                        float u0, float v0, float u1, float v1, int color) {
    }

    private record Bounds(float left, float bottom, float right, float top) {
    }

    private record Glyph(float advance, Bounds plane, Bounds atlas) {
    }

    private record FontData(float atlasWidth, float atlasHeight, float ascender, float lineHeight,
                            Map<Integer, Glyph> glyphs, Glyph fallback) {
        private static FontData read(Reader reader) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject atlas = root.getAsJsonObject("atlas");
            if (!"mtsdf".equals(atlas.get("type").getAsString()) || !"bottom".equals(atlas.get("yOrigin").getAsString())) {
                throw new IllegalStateException("Unsupported round font atlas");
            }
            float atlasWidth = atlas.get("width").getAsFloat();
            float atlasHeight = atlas.get("height").getAsFloat();
            JsonObject metrics = root.getAsJsonObject("metrics");
            float ascender = metrics.get("ascender").getAsFloat();
            float lineHeight = metrics.get("lineHeight").getAsFloat();
            Map<Integer, Glyph> glyphs = new HashMap<>();
            JsonArray entries = root.getAsJsonArray("glyphs");
            entries.forEach(element -> {
                JsonObject entry = element.getAsJsonObject();
                int codePoint = entry.get("unicode").getAsInt();
                float advance = entry.get("advance").getAsFloat();
                glyphs.put(codePoint, new Glyph(advance, bounds(entry, "planeBounds"), bounds(entry, "atlasBounds")));
            });
            Glyph fallback = glyphs.get((int) '?');
            if (fallback == null) {
                throw new IllegalStateException("Round font has no fallback glyph");
            }
            return new FontData(atlasWidth, atlasHeight, ascender, lineHeight, Map.copyOf(glyphs), fallback);
        }

        private static Bounds bounds(JsonObject entry, String name) {
            if (!entry.has(name)) {
                return null;
            }
            JsonObject value = entry.getAsJsonObject(name);
            return new Bounds(
                    value.get("left").getAsFloat(),
                    value.get("bottom").getAsFloat(),
                    value.get("right").getAsFloat(),
                    value.get("top").getAsFloat()
            );
        }

        private Glyph glyph(int codePoint) {
            return glyphs.getOrDefault(codePoint, fallback);
        }
    }
}
