package ru.method.linkenvisuals.utils.render;


import ru.method.linkenvisuals.utils.Wrapper;

import lombok.experimental.UtilityClass;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.util.Identifier;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import net.minecraft.util.math.Direction;

import net.minecraft.util.math.RotationAxis;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import org.joml.Matrix4f;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;


import java.awt.*;

import java.util.ArrayList;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass

public class Render3D implements Wrapper {

    public boolean rendering = false;

    public final List<VertexCollection> QUADS = new ArrayList<>();
    public final List<VertexCollection> DEPTH_QUADS = new ArrayList<>();

    // Молния киллэффекта: аддитивные квады. Рисуются ДО прохода FogBlur, с тестом глубины, но БЕЗ её
    // записи — поэтому туман размывает молнию по глубине фона (столб уходит в небо → мылится ВМЕСТЕ с
    // миром), а не оставляет её резким near-пятном поверх размытого мира. Без тумана рисуется как обычно.
    public final List<VertexCollection> LIGHTNING_QUADS = new ArrayList<>();

    public final List<VertexCollection> DEBUG_LINES = new ArrayList<>();
    public final List<VertexCollection> DEPTH_DEBUG_LINES = new ArrayList<>();

    public final List<VertexCollection> SHINE_QUADS = new ArrayList<>();
    public final List<VertexCollection> DEPTH_SHINE_QUADS = new ArrayList<>();

    public final List<VertexCollection> SHINE_DEBUG_LINES = new ArrayList<>();
    public final List<VertexCollection> DEPTH_SHINE_DEBUG_LINES = new ArrayList<>();

    public final List<TexturedQuad> TEXTURED_QUADS = new ArrayList<>();
    public final List<TexturedQuad> TEXTURED_QUADS_ADDITIVE = new ArrayList<>();

    public final List<TexturedQuad> TEXTURED_QUADS_DEPTH = new ArrayList<>();
    public final List<TexturedQuad> TEXTURED_QUADS_DEPTH_ADDITIVE = new ArrayList<>();
    public final List<VariableLine> VARIABLE_LINES = new ArrayList<>();
    public final List<VariableLine> DEPTH_VARIABLE_LINES = new ArrayList<>();
    private final Map<Identifier, List<TexturedQuad>> textureBatches = new LinkedHashMap<>();
    // LRU с эвикцией: динамические VoxelShape (поршни/сундуки/нестандартные блоки) иначе копятся всю сессию.
    private static final int SHAPE_CACHE_CAPACITY = 256;
    private final Map<VoxelShape, ShapeCache> SHAPE_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<VoxelShape, ShapeCache> eldest) {
            return size() > SHAPE_CACHE_CAPACITY;
        }
    };
    private Matrix4f lastProjectionMatrix = new Matrix4f();
    private Matrix4f lastWorldSpaceMatrix = new Matrix4f();
    // Переиспользуемый стек для биллбордов/ориентированных текстур (render-поток, по одному use за вызов).
    // Каждый вызов начинается с loadIdentity(); итоговый entry копируется в quad, поэтому наложения нет.
    private final MatrixStack billboardScratch = new MatrixStack();
    private static final int MAX_TEXTURE_BATCHES = 256;

    public void setLastProjectionMatrix(Matrix4f matrix) {
        if (matrix == null) return;
        lastProjectionMatrix.set(matrix);
    }

    public Matrix4f getLastProjectionMatrix() {
        return new Matrix4f(lastProjectionMatrix);
    }

    public void setLastWorldSpaceMatrix(MatrixStack.Entry entry) {
        if (entry == null) return;
        lastWorldSpaceMatrix.set(entry.getPositionMatrix());
    }

    public Matrix4f getLastWorldSpaceMatrix() {
        return new Matrix4f(lastWorldSpaceMatrix);
    }


    public void renderCube(MatrixStack matrices, Vec3d vec3d, double size, boolean fill, Color fillColor, boolean outline, Color outlineColor) {

        if (outline) renderBoxOutline(matrices, new Box(vec3d.add(-0.5 * size, -0.5 * size,-0.5 * size), vec3d.add(0.5 * size, 0.5 * size,0.5 * size)), outlineColor);

        if (fill) renderBox(matrices, new Box(vec3d.add(-0.5 * size, -0.5 * size, -0.5 * size), vec3d.add(0.5 * size, 0.5 * size, 0.5 * size)), fillColor);

    }

    public void renderCube(MatrixStack matrices, Box box, boolean fill, Color fillColor, boolean outline, Color outlineColor) {

        if (outline) renderBoxOutline(matrices, box, outlineColor);

        if (fill) renderBox(matrices, box, fillColor);

    }

    public static void drawLine(MatrixStack matrices, Vec3d from, Vec3d to, Color color) {
        drawLine(matrices, from, to, color, false);
    }

    public static void drawLine(MatrixStack matrices, Vec3d from, Vec3d to, Color color, boolean depthTest) {

        if (!rendering) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        float fromX = (float) (from.x - camera.x);

        float fromY = (float) (from.y - camera.y);

        float fromZ = (float) (from.z - camera.z);

        float toX = (float) (to.x - camera.x);

        float toY = (float) (to.y - camera.y);

        float toZ = (float) (to.z - camera.z);

        (depthTest ? DEPTH_DEBUG_LINES : DEBUG_LINES).add(new VertexCollection(

                new Vertex(matrix, fromX, fromY, fromZ, color.getRGB()),

                new Vertex(matrix, toX, toY, toZ, color.getRGB())

        ));

    }

    public static void drawLine(MatrixStack matrices, Vec3d from, Vec3d to, Color colorStart, Color colorEnd, float width, boolean depthTest) {
        if (colorStart == null || colorEnd == null) return;
        drawLine(matrices, from, to, colorStart.getRGB(), colorEnd.getRGB(), width, depthTest);
    }

    public static void drawLine(MatrixStack matrices, Vec3d from, Vec3d to, Color color, float width, boolean depthTest) {
        if (color == null) return;
        int rgb = color.getRGB();
        drawLine(matrices, from, to, rgb, rgb, width, depthTest);
    }

    public static void drawLine(MatrixStack matrices, Vec3d from, Vec3d to, int color, float width, boolean depthTest) {
        drawLine(matrices, from, to, color, color, width, depthTest);
    }

    /** int-ядро: Vertex хранит int argb, поэтому НЕ оборачиваем цвет в java.awt.Color (per-entity per-frame ESP). */
    public static void drawLine(MatrixStack matrices, Vec3d from, Vec3d to, int colorStart, int colorEnd, float width, boolean depthTest) {
        if (!rendering) return;
        if (matrices == null || from == null || to == null) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        float fromX = (float) (from.x - camera.x);
        float fromY = (float) (from.y - camera.y);
        float fromZ = (float) (from.z - camera.z);
        float toX = (float) (to.x - camera.x);
        float toY = (float) (to.y - camera.y);
        float toZ = (float) (to.z - camera.z);

        VariableLine line = new VariableLine(
                new Vertex(matrix, fromX, fromY, fromZ, colorStart),
                new Vertex(matrix, toX, toY, toZ, colorEnd),
                Math.max(1.0f, width)
        );
        (depthTest ? DEPTH_VARIABLE_LINES : VARIABLE_LINES).add(line);
    }

    public void renderBox(MatrixStack matrices, Box box, Color color) {

        renderGradientBox(matrices, box, color, color);

    }

    public void renderBoxOutline(MatrixStack matrices, Box box, Color color) {

        renderGradientBoxOutline(matrices, box, color, color);

    }

    public void renderTriangle(MatrixStack matrices, Vec3d p1, Vec3d p2, Vec3d p3, Color color) {
        renderTriangle(matrices, p1, p2, p3, color, false);
    }

    public void renderTriangle(MatrixStack matrices, Vec3d p1, Vec3d p2, Vec3d p3, Color color, boolean depthTest) {

        if (!rendering) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        float x1 = (float) (p1.x - camera.x);

        float y1 = (float) (p1.y - camera.y);

        float z1 = (float) (p1.z - camera.z);

        float x2 = (float) (p2.x - camera.x);

        float y2 = (float) (p2.y - camera.y);

        float z2 = (float) (p2.z - camera.z);

        float x3 = (float) (p3.x - camera.x);

        float y3 = (float) (p3.y - camera.y);

        float z3 = (float) (p3.z - camera.z);

        (depthTest ? DEPTH_QUADS : QUADS).add(new VertexCollection(

                new Vertex(matrix, x1, y1, z1, color.getRGB()),

                new Vertex(matrix, x2, y2, z2, color.getRGB()),

                new Vertex(matrix, x3, y3, z3, color.getRGB()),

                new Vertex(matrix, x1, y1, z1, color.getRGB())

        ));

    }

    public void renderGradientBox(MatrixStack matrices, Box box, Color startColor, Color endColor) {

        if (!rendering) return;

        if (!isVisible(box)) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        box = cameraTransform(box, matrix);

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB())));

        QUADS.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB())));

    }

    public void renderCircle(MatrixStack matrices, Vec3d center, double radius, Color color) {

        if (!rendering) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        int segments = 64;

        for (int i = 0; i < segments; i++) {

            double angle1 = 2.0 * Math.PI * i / segments;

            double angle2 = 2.0 * Math.PI * (i + 1) / segments;

            double x1 = center.x + radius * Math.cos(angle1) - camera.x;

            double z1 = center.z + radius * Math.sin(angle1) - camera.z;

            double x2 = center.x + radius * Math.cos(angle2) - camera.x;

            double z2 = center.z + radius * Math.sin(angle2) - camera.z;

            double y = center.y - camera.y;

            DEBUG_LINES.add(new VertexCollection(

                    new Vertex(matrix, (float) x1, (float) y, (float) z1, color.getRGB()),

                    new Vertex(matrix, (float) x2, (float) y, (float) z2, color.getRGB())

            ));

        }

    }

    public void renderGradientBoxOutline(MatrixStack matrices, Box box, Color startColor, Color endColor) {

        if (!rendering) return;

        if (!isVisible(box)) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        box = cameraTransform(box, matrix);

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ, startColor.getRGB())));

        DEBUG_LINES.add(new VertexCollection(new Vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ, startColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ, endColor.getRGB()),

                new Vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ, startColor.getRGB())));

    }

    public void drawQuad(MatrixStack matrices, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, Color color, boolean depthTest) {
        if (!rendering) return;
        if (matrices == null || p1 == null || p2 == null || p3 == null || p4 == null || color == null) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Vertex v1 = new Vertex(matrix, (float) (p1.x - camera.x), (float) (p1.y - camera.y), (float) (p1.z - camera.z), color.getRGB());
        Vertex v2 = new Vertex(matrix, (float) (p2.x - camera.x), (float) (p2.y - camera.y), (float) (p2.z - camera.z), color.getRGB());
        Vertex v3 = new Vertex(matrix, (float) (p3.x - camera.x), (float) (p3.y - camera.y), (float) (p3.z - camera.z), color.getRGB());
        Vertex v4 = new Vertex(matrix, (float) (p4.x - camera.x), (float) (p4.y - camera.y), (float) (p4.z - camera.z), color.getRGB());
        (depthTest ? DEPTH_QUADS : QUADS).add(new VertexCollection(v1, v2, v3, v4));
    }

    public void drawQuad(MatrixStack matrices, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, int color, boolean depthTest) {
        drawQuad(matrices, p1, p2, p3, p4, fromArgb(color), depthTest);
    }

    /**
     * Аддитивный камера-относительный квад в отдельный буфер молнии (ARGB-цвет). Конвенция координат
     * та же, что у {@link #drawQuad}: мировая матрица + камера-относительные точки. Буфер рисуется
     * методом {@link #drawLightning()} ДО FogBlur, поэтому туман мылит молнию по глубине фона.
     */
    public void drawLightningQuad(MatrixStack matrices, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, int color) {
        if (!rendering) return;
        if (matrices == null || p1 == null || p2 == null || p3 == null || p4 == null) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Vertex v1 = new Vertex(matrix, (float) (p1.x - camera.x), (float) (p1.y - camera.y), (float) (p1.z - camera.z), color);
        Vertex v2 = new Vertex(matrix, (float) (p2.x - camera.x), (float) (p2.y - camera.y), (float) (p2.z - camera.z), color);
        Vertex v3 = new Vertex(matrix, (float) (p3.x - camera.x), (float) (p3.y - camera.y), (float) (p3.z - camera.z), color);
        Vertex v4 = new Vertex(matrix, (float) (p4.x - camera.x), (float) (p4.y - camera.y), (float) (p4.z - camera.z), color);
        LIGHTNING_QUADS.add(new VertexCollection(v1, v2, v3, v4));
    }

    /**
     * Отрисовать накопленную молнию: аддитивно (shine), с тестом глубины (перекрывается миром), но БЕЗ
     * записи глубины — чтобы FogBlur размывал её по глубине окружения. Вызывается из GameRendererMixin
     * ДО {@code fogBlur.renderCurrent()} (при активном тумане) либо в обычной точке (без тумана).
     */
    public void drawLightning() {
        if (LIGHTNING_QUADS.isEmpty()) return;
        drawDepth(LIGHTNING_QUADS, List.of(), true);
    }

    public void drawGradientQuad(MatrixStack matrices, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, Color c1, Color c2, Color c3, Color c4, boolean depthTest) {
        if (!rendering) return;
        if (matrices == null || p1 == null || p2 == null || p3 == null || p4 == null || c1 == null || c2 == null || c3 == null || c4 == null) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Vertex v1 = new Vertex(matrix, (float) (p1.x - camera.x), (float) (p1.y - camera.y), (float) (p1.z - camera.z), c1.getRGB());
        Vertex v2 = new Vertex(matrix, (float) (p2.x - camera.x), (float) (p2.y - camera.y), (float) (p2.z - camera.z), c2.getRGB());
        Vertex v3 = new Vertex(matrix, (float) (p3.x - camera.x), (float) (p3.y - camera.y), (float) (p3.z - camera.z), c3.getRGB());
        Vertex v4 = new Vertex(matrix, (float) (p4.x - camera.x), (float) (p4.y - camera.y), (float) (p4.z - camera.z), c4.getRGB());
        (depthTest ? DEPTH_QUADS : QUADS).add(new VertexCollection(v1, v2, v3, v4));
    }

    public void drawGradientQuad(MatrixStack matrices, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, int c1, int c2, int c3, int c4, boolean depthTest) {
        drawGradientQuad(matrices, p1, p2, p3, p4, fromArgb(c1), fromArgb(c2), fromArgb(c3), fromArgb(c4), depthTest);
    }

    public void drawBox(MatrixStack matrices, Box box, Color color, float lineWidth, boolean line, boolean fill, boolean depthTest) {
        if (!rendering) return;
        if (matrices == null || box == null || color == null) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Box expanded = box.expand(1.0e-3);
        // Камера-относительные координаты один раз: раньше на каждый бокс аллоцировалось ~48 промежуточных Vec3d за кадр.
        float x1 = (float) (expanded.minX - camera.x);
        float y1 = (float) (expanded.minY - camera.y);
        float z1 = (float) (expanded.minZ - camera.z);
        float x2 = (float) (expanded.maxX - camera.x);
        float y2 = (float) (expanded.maxY - camera.y);
        float z2 = (float) (expanded.maxZ - camera.z);
        int argb = color.getRGB();

        if (fill) {
            int fillArgb = multiplyAlpha(argb, 0.1f);
            List<VertexCollection> target = depthTest ? DEPTH_QUADS : QUADS;
            quad(target, matrix, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, fillArgb);
            quad(target, matrix, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, fillArgb);
            quad(target, matrix, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, fillArgb);
            quad(target, matrix, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, fillArgb);
            quad(target, matrix, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, fillArgb);
            quad(target, matrix, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, fillArgb);
        }

        if (line) {
            float width = Math.max(1.0f, lineWidth);
            List<VariableLine> target = depthTest ? DEPTH_VARIABLE_LINES : VARIABLE_LINES;
            edge(target, matrix, x1, y1, z1, x2, y1, z1, argb, width);
            edge(target, matrix, x2, y1, z1, x2, y1, z2, argb, width);
            edge(target, matrix, x2, y1, z2, x1, y1, z2, argb, width);
            edge(target, matrix, x1, y1, z2, x1, y1, z1, argb, width);
            edge(target, matrix, x1, y1, z2, x1, y2, z2, argb, width);
            edge(target, matrix, x1, y1, z1, x1, y2, z1, argb, width);
            edge(target, matrix, x2, y1, z2, x2, y2, z2, argb, width);
            edge(target, matrix, x2, y1, z1, x2, y2, z1, argb, width);
            edge(target, matrix, x1, y2, z1, x2, y2, z1, argb, width);
            edge(target, matrix, x2, y2, z1, x2, y2, z2, argb, width);
            edge(target, matrix, x2, y2, z2, x1, y2, z2, argb, width);
            edge(target, matrix, x1, y2, z2, x1, y2, z1, argb, width);
        }
    }

    private void quad(List<VertexCollection> target, Matrix4f matrix,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4, int argb) {
        target.add(new VertexCollection(
                new Vertex(matrix, x1, y1, z1, argb),
                new Vertex(matrix, x2, y2, z2, argb),
                new Vertex(matrix, x3, y3, z3, argb),
                new Vertex(matrix, x4, y4, z4, argb)));
    }

    private void edge(List<VariableLine> target, Matrix4f matrix,
                      float x1, float y1, float z1, float x2, float y2, float z2, int argb, float width) {
        target.add(new VariableLine(
                new Vertex(matrix, x1, y1, z1, argb),
                new Vertex(matrix, x2, y2, z2, argb),
                width));
    }

    public void drawBox(MatrixStack matrices, Box box, int color, float lineWidth, boolean line, boolean fill, boolean depthTest) {
        drawBox(matrices, box, fromArgb(color), lineWidth, line, fill, depthTest);
    }

    public void drawShape(MatrixStack matrices, BlockPos blockPos, VoxelShape voxelShape, Color color, float lineWidth, boolean fill, boolean depthTest) {
        if (!rendering) return;
        if (matrices == null || blockPos == null || voxelShape == null || voxelShape.isEmpty() || color == null) return;

        ShapeCache cache = SHAPE_CACHE.computeIfAbsent(voxelShape, Render3D::buildShapeCache);
        double ox = blockPos.getX();
        double oy = blockPos.getY();
        double oz = blockPos.getZ();
        if (fill) {
            for (Box box : cache.boxes()) {
                drawBox(matrices, box.offset(ox, oy, oz), color, lineWidth, false, true, depthTest);
            }
        }
        // Рёбра пишутся напрямую: раньше start().add(offset)/end().add(offset) — 2 Vec3d на ребро за кадр.
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        int argb = color.getRGB();
        float width = Math.max(1.0f, lineWidth);
        List<VariableLine> target = depthTest ? DEPTH_VARIABLE_LINES : VARIABLE_LINES;
        for (ShapeLine line : cache.lines()) {
            edge(target, matrix,
                    (float) (line.start().x + ox - camera.x),
                    (float) (line.start().y + oy - camera.y),
                    (float) (line.start().z + oz - camera.z),
                    (float) (line.end().x + ox - camera.x),
                    (float) (line.end().y + oy - camera.y),
                    (float) (line.end().z + oz - camera.z),
                    argb, width);
        }
    }

    public void drawShape(MatrixStack matrices, BlockPos blockPos, VoxelShape voxelShape, int color, float lineWidth, boolean fill, boolean depthTest) {
        drawShape(matrices, blockPos, voxelShape, fromArgb(color), lineWidth, fill, depthTest);
    }

    private ShapeCache buildShapeCache(VoxelShape voxelShape) {
        List<Box> boxes = voxelShape.getBoundingBoxes();
        List<ShapeLine> lines = new ArrayList<>();
        voxelShape.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) ->
                lines.add(new ShapeLine(new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ))));
        return new ShapeCache(boxes, lines);
    }

    private int multiplyAlpha(int argb, float alpha) {
        int scaled = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * alpha)));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    private Color fromArgb(int color) {
        return new Color(color, true);
    }

    public void prepare() {
        QUADS.clear();
        DEPTH_QUADS.clear();
        LIGHTNING_QUADS.clear();
        DEBUG_LINES.clear();
        DEPTH_DEBUG_LINES.clear();
        SHINE_QUADS.clear();
        DEPTH_SHINE_QUADS.clear();
        SHINE_DEBUG_LINES.clear();
        DEPTH_SHINE_DEBUG_LINES.clear();
        TEXTURED_QUADS.clear();
        TEXTURED_QUADS_ADDITIVE.clear();
        TEXTURED_QUADS_DEPTH.clear();
        TEXTURED_QUADS_DEPTH_ADDITIVE.clear();
        VARIABLE_LINES.clear();
        DEPTH_VARIABLE_LINES.clear();
        rendering = true;
    }

    public void draw(List<VertexCollection> quads, List<VertexCollection> debugLines, boolean shine) {
        if (quads.isEmpty() && debugLines.isEmpty()) return;

        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        if (!quads.isEmpty()) {
            RenderLayer layer = shine ? Render3DPipelines.LAYER_QUADS_SHINE_NO_DEPTH : Render3DPipelines.LAYER_QUADS_NO_DEPTH;
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (VertexCollection collection : quads) collection.vertex(buffer);
            immediate.draw(layer);
        }

        if (!debugLines.isEmpty()) {
            RenderLayer layer = Render3DPipelines.LAYER_LINES_NO_DEPTH;
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (VertexCollection collection : debugLines) collection.lineVertex(buffer);
            immediate.draw(layer);
        }
    }

    public void drawVariableWidthLines(boolean depthTest) {
        List<VariableLine> lines = depthTest ? DEPTH_VARIABLE_LINES : VARIABLE_LINES;
        if (lines.isEmpty()) return;

        drawVariableLines(lines, depthTest);

        lines.clear();
    }

    private static float[] widthScratch = new float[8]; // переиспользуемый буфер уникальных ширин (только рендер-тред)

    private void drawVariableLines(List<VariableLine> lines, boolean depthTest) {
        if (lines.isEmpty()) return;
        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = depthTest ? Render3DPipelines.LAYER_LINES_DEPTH : Render3DPipelines.LAYER_LINES_NO_DEPTH;
        VertexConsumer buffer = immediate.getBuffer(layer);
        for (VariableLine line : lines) {
            Vertex start = line.start();
            Vertex end = line.end();
            float width = Math.max(1.0f, line.width());
            // 1.21.11's line shader expands the segment along Normal in screen
            // space. A constant normal collapsed every non-vertical line to a
            // 1 px/diagonal smear (pixelated circles/outlines); the normal has
            // to be the actual segment direction.
            float dx = end.x - start.x;
            float dy = end.y - start.y;
            float dz = end.z - start.z;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0e-5f) {
                dx = 0.0f;
                dy = 1.0f;
                dz = 0.0f;
                length = 1.0f;
            }
            float nx = dx / length;
            float ny = dy / length;
            float nz = dz / length;
            buffer.vertex(start.matrix, start.x, start.y, start.z).color(start.color)
                .normal(nx, ny, nz).lineWidth(width);
            buffer.vertex(end.matrix, end.x, end.y, end.z).color(end.color)
                .normal(nx, ny, nz).lineWidth(width);
        }
        immediate.draw(layer);
    }

    public void drawDepth(List<VertexCollection> quads, List<VertexCollection> debugLines, boolean shine) {
        if (quads.isEmpty() && debugLines.isEmpty()) return;

        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        if (!quads.isEmpty()) {
            RenderLayer layer = shine ? Render3DPipelines.LAYER_QUADS_SHINE_DEPTH : Render3DPipelines.LAYER_QUADS_DEPTH;
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (VertexCollection collection : quads) collection.vertex(buffer);
            immediate.draw(layer);
        }

        if (!debugLines.isEmpty()) {
            RenderLayer layer = Render3DPipelines.LAYER_LINES_DEPTH;
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (VertexCollection collection : debugLines) collection.lineVertex(buffer);
            immediate.draw(layer);
        }
    }

        public void renderBillboardTexture(Vec3d position, float size, Identifier texture, int color, boolean depthTest) {
        renderBillboardTexture(position, size, texture, color, depthTest, false);
    }

    public void renderBillboardTexture(Vec3d position, float size, Identifier texture, int color, boolean depthTest, boolean additive) {
        if (!rendering) return;

        Camera camera = mc.gameRenderer.getCamera();
        MatrixStack billboardStack = billboardScratch;
        billboardStack.loadIdentity();
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d relative = position.subtract(cameraPos);
        // Build the camera-facing quad directly in world space. The old code
        // baked camera yaw/pitch into the vertex matrix, but 1.21.11's world
        // model-view already contains that rotation, so the matrix was applied
        // twice and every 3D TargetEsp/Trail/Particle sprite landed far away
        // from its entity. Right/up basis from the camera keeps the quad
        // billboarded with a single view rotation.
        // Camera.horizontalPlane is the camera FORWARD vector, not its right
        // vector. Right is forward x up; using horizontalPlane directly made
        // the quads edge-on (TargetEsp Ghosts/Circles disappeared after the
        // first billboard fix).
        Vector3fc forwardAxis = camera.getHorizontalPlane();
        Vector3fc upAxis = camera.getVerticalPlane();
        Vector3f rightAxis = new Vector3f(forwardAxis).cross(upAxis);
        Matrix4f billboardMatrix = new Matrix4f(
            rightAxis.x(), rightAxis.y(), rightAxis.z(), (float) relative.x,
            upAxis.x(), upAxis.y(), upAxis.z(), (float) relative.y,
            forwardAxis.x(), forwardAxis.y(), forwardAxis.z(), (float) relative.z,
            0.0f, 0.0f, 0.0f, 1.0f
        );
        billboardStack.multiplyPositionMatrix(billboardMatrix);
        MatrixStack.Entry entry = billboardStack.peek().copy();

        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;

        TexturedQuad quad = new TexturedQuad(entry, texture, -size / 2f, -size / 2f, size, size, red, green, blue, alpha);

        if (depthTest) {
            if (additive) {
                TEXTURED_QUADS_DEPTH_ADDITIVE.add(quad);
            } else {
                TEXTURED_QUADS_DEPTH.add(quad);
            }
        } else {
            if (additive) {
                TEXTURED_QUADS_ADDITIVE.add(quad);
            } else {
                TEXTURED_QUADS.add(quad);
            }
        }
    }

    public void renderOrientedTexture(Vec3d position, float size, Identifier texture, int color, Direction face, boolean depthTest) {
        renderOrientedTexture(position, size, texture, color, face, depthTest, false);
    }

    public void renderOrientedTexture(Vec3d position, float size, Identifier texture, int color, Direction face, boolean depthTest, boolean additive) {
        if (!rendering) return;

        Camera camera = mc.gameRenderer.getCamera();
        MatrixStack orientedStack = billboardScratch;
        orientedStack.loadIdentity();
        Vec3d cameraPos = camera.getCameraPos();
        orientedStack.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
        applyFaceRotation(orientedStack, face);
        MatrixStack.Entry entry = orientedStack.peek().copy();

        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;

        TexturedQuad quad = new TexturedQuad(entry, texture, -size / 2f, -size / 2f, size, size, red, green, blue, alpha);

        if (depthTest) {
            if (additive) {
                TEXTURED_QUADS_DEPTH_ADDITIVE.add(quad);
            } else {
                TEXTURED_QUADS_DEPTH.add(quad);
            }
        } else {
            if (additive) {
                TEXTURED_QUADS_ADDITIVE.add(quad);
            } else {
                TEXTURED_QUADS.add(quad);
            }
        }
    }

    public void drawTexture(MatrixStack.Entry entry, Identifier texture, float x, float y, float width, float height, int color, boolean depthTest) {
        drawTexture(entry, texture, x, y, width, height, color, depthTest, false);
    }

    public void drawTexture(MatrixStack.Entry entry, Identifier texture, float x, float y, float width, float height, int color, boolean depthTest, boolean additive) {
        if (!rendering || entry == null || texture == null) return;

        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        TexturedQuad quad = new TexturedQuad(entry, texture, x, y, width, height, red, green, blue, alpha);

        if (depthTest) {
            if (additive) TEXTURED_QUADS_DEPTH_ADDITIVE.add(quad);
            else TEXTURED_QUADS_DEPTH.add(quad);
        } else {
            if (additive) TEXTURED_QUADS_ADDITIVE.add(quad);
            else TEXTURED_QUADS.add(quad);
        }
    }

    private void applyFaceRotation(MatrixStack stack, Direction face) {
        switch (face) {
            case UP -> stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
            case DOWN -> stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
            case NORTH -> stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
            case SOUTH -> { }
            case EAST -> stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90f));
            case WEST -> stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f));
        }
    }

    public void drawTextures() {
        drawTexturedQuads(TEXTURED_QUADS, false, false, false);
        drawTexturedQuads(TEXTURED_QUADS_ADDITIVE, false, true, false);
        drawTexturedQuads(TEXTURED_QUADS_DEPTH, true, false, false);
        drawTexturedQuads(TEXTURED_QUADS_DEPTH_ADDITIVE, true, true, false);
    }

    /**
     * Раньше здесь текстурные билборды (партиклы/кристаллы) рисовались ДО прохода FogBlur с ЗАПИСЬЮ ГЛУБИНЫ.
     * Но шейдер POSITION_TEX_COLOR не отбрасывает прозрачные фрагменты, поэтому ПРОЗРАЧНЫЕ пиксели квадрата
     * тоже писали глубину → FogBlur читал её и рисовал тёмные прямоугольники вокруг кристаллов.
     * Теперь текстурные билборды рисуются ПОСЛЕ фога (в {@link #drawTextures()}, без записи глубины) —
     * резко и без тёмных рамок. Метод оставлен no-op (вызывается из GameRendererMixin).
     */
    public void drawDepthTexturesPreFog() {
        // no-op: см. комментарий выше.
    }

    private void drawTexturedQuads(List<TexturedQuad> textures, boolean depthTest, boolean additive, boolean depthWrite) {
        if (textures.isEmpty()) {
            return;
        }

        // 1.21.11: blending/depth are pipeline state. Render layers below pick the correct blend mode.
        textureBatches.values().forEach(List::clear);
        for (TexturedQuad quad : textures) {
            Identifier texture = quad.texture();
            List<TexturedQuad> batch = textureBatches.get(texture);
            if (batch == null) {
                if (textureBatches.size() >= MAX_TEXTURE_BATCHES) {
                    textureBatches.clear();
                }
                batch = new ArrayList<>();
                textureBatches.put(texture, batch);
            }
            batch.add(quad);
        }

        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        for (Map.Entry<Identifier, List<TexturedQuad>> entry : textureBatches.entrySet()) {
            List<TexturedQuad> batch = entry.getValue();
            if (batch.isEmpty()) continue;

            RenderLayer layer = Render3DPipelines.getTexturedLayer(entry.getKey(), depthTest, additive);
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (TexturedQuad quad : batch) {
                emitTexturedQuad(buffer, quad);
            }
            immediate.draw(layer);
        }

        textures.clear();
    }

    private void emitTexturedQuad(VertexConsumer buffer, TexturedQuad quad) {
        Matrix4f matrix = quad.entry().getPositionMatrix();
        float x = quad.x();
        float y = quad.y();
        float width = quad.width();
        float height = quad.height();
        int argb = (quad.alpha() << 24) | (quad.red() << 16) | (quad.green() << 8) | quad.blue();

        // Render3DPipelines textured pipelines use POSITION_TEXTURE_COLOR with TRIANGLES draw mode.
        // Emit 2 triangles (6 vertices) per quad.
        buffer.vertex(matrix, x, y + height, 0.0f).texture(0.0f, 1.0f).color(argb);
        buffer.vertex(matrix, x + width, y + height, 0.0f).texture(1.0f, 1.0f).color(argb);
        buffer.vertex(matrix, x + width, y, 0.0f).texture(1.0f, 0.0f).color(argb);

        buffer.vertex(matrix, x, y + height, 0.0f).texture(0.0f, 1.0f).color(argb);
        buffer.vertex(matrix, x + width, y, 0.0f).texture(1.0f, 0.0f).color(argb);
        buffer.vertex(matrix, x, y, 0.0f).texture(0.0f, 0.0f).color(argb);
    }

    private boolean isVisible(Box box) {

        return ru.method.linkenvisuals.utils.world.WorldUtils.isBoxVisible(box);

    }

    private Vec3d cameraTransform(Vec3d vec3d, Matrix4f matrix) {

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        Vector4f vec = new Vector4f((float) (vec3d.x - camera.x), (float) (vec3d.y - camera.y), (float) (vec3d.z - camera.z), 1.0f);

        vec.mul(matrix);

        return new Vec3d(vec.x(), vec.y(), vec.z());

    }

    private Box cameraTransform(Box box, Matrix4f matrix) {

        Vec3d min = cameraTransform(new Vec3d(box.minX, box.minY, box.minZ), matrix);

        Vec3d max = cameraTransform(new Vec3d(box.maxX, box.maxY, box.maxZ), matrix);

        return new Box(min, max);

    }

    private record TexturedQuad(MatrixStack.Entry entry, Identifier texture, float x, float y, float width, float height, int red, int green, int blue, int alpha) { }

    private record VariableLine(Vertex start, Vertex end, float width) { }

    private record ShapeLine(Vec3d start, Vec3d end) { }

    private record ShapeCache(List<Box> boxes, List<ShapeLine> lines) { }

    private record VertexCollection(Vertex... vertices) {

        public void vertex(VertexConsumer buffer) {

            for (Vertex vertex : vertices) buffer.vertex(vertex.matrix, vertex.x, vertex.y, vertex.z).color(vertex.color);

        }

        /** Line layers in 1.21.11 also need the segment direction + line width. */
        public void lineVertex(VertexConsumer buffer) {
            if (vertices.length == 0) {
                return;
            }
            if (vertices.length == 1) {
                Vertex vertex = vertices[0];
                buffer.vertex(vertex.matrix, vertex.x, vertex.y, vertex.z)
                    .color(vertex.color).normal(0.0f, 1.0f, 0.0f).lineWidth(1.0f);
                return;
            }
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                Vertex start = vertices[i];
                Vertex end = vertices[i + 1];
                float dx = end.x - start.x;
                float dy = end.y - start.y;
                float dz = end.z - start.z;
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 1.0e-5f) {
                    dx = 0.0f;
                    dy = 1.0f;
                    dz = 0.0f;
                    length = 1.0f;
                }
                float nx = dx / length;
                float ny = dy / length;
                float nz = dz / length;
                buffer.vertex(start.matrix, start.x, start.y, start.z)
                    .color(start.color).normal(nx, ny, nz).lineWidth(1.0f);
                buffer.vertex(end.matrix, end.x, end.y, end.z)
                    .color(end.color).normal(nx, ny, nz).lineWidth(1.0f);
            }
            if ((vertices.length & 1) != 0) {
                Vertex last = vertices[vertices.length - 1];
                buffer.vertex(last.matrix, last.x, last.y, last.z)
                    .color(last.color).normal(0.0f, 1.0f, 0.0f).lineWidth(1.0f);
            }
        }

    }

    private record Vertex(Matrix4f matrix, float x, float y, float z, int color) { }

}

