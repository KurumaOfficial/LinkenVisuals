package ru.method.linkenvisuals.cosmetics.wave;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Отрисовка колышущегося плаща по {@link CapeSimulation}: 16 звеньев сшиваются в единый меш без щелей
 * между сегментами (каждое звено делит верхнее ребро с предыдущим — вершины считаются от ДВУХ матриц,
 * своей и предыдущей). Геометрия ровно ванильная: 10×16×1 px, поэтому UV совпадают с обычной
 * текстурой плаща 64×32 (и с нашими HD-версиями — координаты нормированные).
 */
public final class WaveCapeRenderer {

    private static final int SEGMENTS = CapeSimulation.SEGMENTS;

    // Размеры ванильного кубоида плаща в блоках: cuboid(-5, 0, -1, 10, 16, 1).
    private static final float HALF_W = 5f / 16f;
    private static final float SEG_H = 1f / 16f;
    private static final float THICK = 1f / 16f;
    private static final float PIVOT = SEG_H / 2f;   // ось поворота — в центре звена
    private static final float CAPE_Z = 2f / 16f;    // ModelTransform.of(0, 0, 2, ...) у ванильной модели

    // UV граней в нормированных координатах (сходятся и для 64×32, и для HD-версий).
    private static final float BACK_MIN_U = 1f / 64f, BACK_MAX_U = 11f / 64f;
    private static final float FRONT_MIN_U = 12f / 64f, FRONT_MAX_U = 22f / 64f;
    private static final float LEFT_MIN_U = 0f, LEFT_MAX_U = 1f / 64f;
    private static final float RIGHT_MIN_U = 11f / 64f, RIGHT_MAX_U = 12f / 64f;
    private static final float BOTTOM_MIN_U = 11f / 64f, BOTTOM_MAX_U = 21f / 64f;
    private static final float FACE_MIN_V = 1f / 32f, FACE_MAX_V = 17f / 32f;
    private static final float EDGE_MIN_V = 0f, EDGE_MAX_V = 1f / 32f;

    // Ветер: лёгкая бегущая волна, чтобы плащ жил даже когда игрок стоит на месте.
    private static final float WIND_DEG = 3f;
    private static final long WIND_PERIOD_MS = 360L * 3L;

    private static final Matrix4f[] POSES = new Matrix4f[SEGMENTS];
    private static final Vector3f NORMAL = new Vector3f();

    static {
        for (int i = 0; i < SEGMENTS; i++) {
            POSES[i] = new Matrix4f();
        }
    }

    private WaveCapeRenderer() {
    }

    /**
     * Нарисовать плащ вместо ванильного.
     *
     * @param simulation готовая (построенная) физика плаща этого игрока
     * @param tickDelta  дробная часть тика для сглаживания между шагами симуляции
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                              Identifier texture, CapeSimulation simulation, float tickDelta, boolean crouching) {
        buildPoses(matrices, simulation, tickDelta, crouching);
        VertexConsumer consumer = vertexConsumers.getBuffer(net.minecraft.client.render.RenderLayers.entitySolid(texture));
        emit(consumer, light);
    }

    /**
     * Deferred variant: 1.21.11 feature renderers only receive an ordered command queue. The wave
     * pose is evaluated inside the draw lambda so the captured segment matrices stay valid.
     */
    public static void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                              Identifier texture, CapeSimulation simulation, float tickDelta, boolean crouching) {
        if (queue == null || texture == null) return;
        queue.submitCustom(matrices, net.minecraft.client.render.RenderLayers.entitySolid(texture), (entry, consumer) -> {
            MatrixStack local = new MatrixStack();
            local.peek().copy(entry);
            buildPoses(local, simulation, tickDelta, crouching);
            emit(consumer, light);
        });
    }

    private static void buildPoses(MatrixStack matrices, CapeSimulation simulation, float tickDelta, boolean crouching) {
        float windPhase = (System.currentTimeMillis() % WIND_PERIOD_MS) / (float) WIND_PERIOD_MS;
        for (int part = 0; part < SEGMENTS; part++) {
            matrices.push();
            applyPose(matrices, simulation, tickDelta, crouching, part, windPhase);
            POSES[part].set(matrices.peek().getPositionMatrix());
            matrices.pop();
        }
    }

    private static void emit(VertexConsumer consumer, int light) {
        for (int part = 0; part < SEGMENTS; part++) {
            Matrix4f current = POSES[part];
            Matrix4f previous = POSES[part == 0 ? 0 : part - 1];
            float top = part * SEG_H;
            float bottom = (part + 1) * SEG_H;
            float minV = FACE_MIN_V + (FACE_MAX_V - FACE_MIN_V) * part / SEGMENTS;
            float maxV = FACE_MIN_V + (FACE_MAX_V - FACE_MIN_V) * (part + 1) / SEGMENTS;

            back(consumer, light, current, previous, top, bottom, minV, maxV);
            front(consumer, light, current, previous, top, bottom, minV, maxV);
            left(consumer, light, current, previous, top, bottom, minV, maxV);
            right(consumer, light, current, previous, top, bottom, minV, maxV);
            if (part == 0) {
                top(consumer, light, current);
            }
            if (part == SEGMENTS - 1) {
                bottom(consumer, light, current, bottom);
            }
        }
    }

    /**
     * Поза одного звена. Костяк — как у ванильного плаща (сдвиг по Z, наклон 6°, разворот на 180°),
     * поверх — смещение из симуляции и доворот звена на угол, под которым верёвка реально висит.
     */
    private static void applyPose(MatrixStack matrices, CapeSimulation simulation, float tickDelta,
                                  boolean crouching, int part, float windPhase) {
        matrices.translate(0f, 0f, CAPE_Z);

        // Смещение звена относительно корня в единицах симуляции (1 единица = 1 звено = 1 px плаща).
        float offsetZ = simulation.lerpX(part, tickDelta) - simulation.lerpX(0, tickDelta);
        if (offsetZ > 0f) {
            offsetZ = 0f;                       // плащ не заезжает игроку в спину
        }
        float offsetY = simulation.lerpY(0, tickDelta) - part - simulation.lerpY(part, tickDelta);

        // Угол наклона звена = наклон хорды «корень → это звено». Удвоение — чтобы изгиб читался.
        float partRotation = -(float) MathHelper.atan2(offsetY, offsetZ);
        partRotation = Math.max(partRotation, 0f);
        if (partRotation != 0f) {
            partRotation = (float) Math.PI - partRotation;
        }
        partRotation *= 2f * MathHelper.DEGREES_PER_RADIAN;

        float pitch = 6f;
        if (crouching) {
            pitch += 25f;
            matrices.translate(0f, 0.15f, 0f);
        }
        pitch += wind(part, windPhase);

        // Крутим НАПРЯМУЮ матрицу позиции (rotateX/rotateY), а не matrices.multiply(Quaternion): последний
        // в 1.21.4 аллоцирует new Quaternionf на каждый вызов (48 шт/игрока/кадр — а плащ надет у всех).
        // Нормаль-матрицу MatrixStack пропускаем осознанно: normal() трансформит через саму матрицу позиции.
        Matrix4f pm = matrices.peek().getPositionMatrix();
        pm.rotateX((float) Math.toRadians(pitch));
        pm.rotateY((float) Math.PI);
        matrices.translate(0f, offsetY / SEGMENTS, offsetZ / SEGMENTS);

        // Доворот вокруг центра звена: сдвигаемся в его ось, крутим, возвращаемся.
        matrices.translate(0f, PIVOT + part * SEG_H, -PIVOT);
        pm.rotateX((float) Math.toRadians(-partRotation));
        matrices.translate(0f, -PIVOT - part * SEG_H, PIVOT);
    }

    /** Бегущая по плащу волна ветра (±WIND_DEG градусов), сдвинутая по фазе для каждого звена. */
    private static float wind(int part, float phase) {
        float relative = (float) (part + 1) / SEGMENTS;
        return MathHelper.sin((relative - phase) * MathHelper.TAU) * WIND_DEG;
    }

    // ---- грани ----------------------------------------------------------------------------------
    // Верхнее ребро каждого звена считается по матрице ПРЕДЫДУЩЕГО звена — так соседние звенья
    // делят общее ребро и шва между ними не видно.

    private static void back(VertexConsumer vc, int light, Matrix4f cur, Matrix4f prev,
                             float top, float bottom, float minV, float maxV) {
        normal(cur, 0f, 0f, -1f);
        vertex(vc, light, prev, HALF_W, top, -THICK, BACK_MAX_U, minV);
        vertex(vc, light, prev, -HALF_W, top, -THICK, BACK_MIN_U, minV);
        vertex(vc, light, cur, -HALF_W, bottom, -THICK, BACK_MIN_U, maxV);
        vertex(vc, light, cur, HALF_W, bottom, -THICK, BACK_MAX_U, maxV);
    }

    private static void front(VertexConsumer vc, int light, Matrix4f cur, Matrix4f prev,
                              float top, float bottom, float minV, float maxV) {
        normal(cur, 0f, 0f, 1f);
        vertex(vc, light, cur, HALF_W, bottom, 0f, FRONT_MAX_U, maxV);
        vertex(vc, light, cur, -HALF_W, bottom, 0f, FRONT_MIN_U, maxV);
        vertex(vc, light, prev, -HALF_W, top, 0f, FRONT_MIN_U, minV);
        vertex(vc, light, prev, HALF_W, top, 0f, FRONT_MAX_U, minV);
    }

    private static void left(VertexConsumer vc, int light, Matrix4f cur, Matrix4f prev,
                             float top, float bottom, float minV, float maxV) {
        normal(cur, -1f, 0f, 0f);
        vertex(vc, light, cur, -HALF_W, bottom, 0f, LEFT_MAX_U, maxV);
        vertex(vc, light, cur, -HALF_W, bottom, -THICK, LEFT_MIN_U, maxV);
        vertex(vc, light, prev, -HALF_W, top, -THICK, LEFT_MIN_U, minV);
        vertex(vc, light, prev, -HALF_W, top, 0f, LEFT_MAX_U, minV);
    }

    private static void right(VertexConsumer vc, int light, Matrix4f cur, Matrix4f prev,
                              float top, float bottom, float minV, float maxV) {
        normal(cur, 1f, 0f, 0f);
        vertex(vc, light, cur, HALF_W, bottom, -THICK, RIGHT_MIN_U, maxV);
        vertex(vc, light, cur, HALF_W, bottom, 0f, RIGHT_MAX_U, maxV);
        vertex(vc, light, prev, HALF_W, top, 0f, RIGHT_MAX_U, minV);
        vertex(vc, light, prev, HALF_W, top, -THICK, RIGHT_MIN_U, minV);
    }

    // Верхний торец плаща (1px, смотрит вверх → освещён ярче граней). Берём НЕ отдельную v0..1-полосу
    // (у HD/фото-плащей там сжатая верхушка картинки → «серая полоса как в паинте»), а самый верхний ряд
    // грани (FACE_MIN_V) — торец совпадает по цвету с верхом плаща, стыка нет.
    private static void top(VertexConsumer vc, int light, Matrix4f cur) {
        normal(cur, 0f, -1f, 0f);
        vertex(vc, light, cur, HALF_W, 0f, 0f, BACK_MAX_U, FACE_MIN_V);
        vertex(vc, light, cur, -HALF_W, 0f, 0f, BACK_MIN_U, FACE_MIN_V);
        vertex(vc, light, cur, -HALF_W, 0f, -THICK, BACK_MIN_U, FACE_MIN_V);
        vertex(vc, light, cur, HALF_W, 0f, -THICK, BACK_MAX_U, FACE_MIN_V);
    }

    // Нижний торец: тем же приёмом берём самый нижний ряд грани (FACE_MAX_V), а не v0..1 — без тёмной полосы.
    private static void bottom(VertexConsumer vc, int light, Matrix4f cur, float y) {
        normal(cur, 0f, 1f, 0f);
        vertex(vc, light, cur, HALF_W, y, -THICK, BACK_MAX_U, FACE_MAX_V);
        vertex(vc, light, cur, -HALF_W, y, -THICK, BACK_MIN_U, FACE_MAX_V);
        vertex(vc, light, cur, -HALF_W, y, 0f, BACK_MIN_U, FACE_MAX_V);
        vertex(vc, light, cur, HALF_W, y, 0f, BACK_MAX_U, FACE_MAX_V);
    }

    /** Нормаль грани в пространстве звена — крутим той же матрицей, что и вершины. */
    private static void normal(Matrix4f matrix, float x, float y, float z) {
        NORMAL.set(x, y, z);
        matrix.transformDirection(NORMAL);
        NORMAL.normalize();
    }

    private static void vertex(VertexConsumer vc, int light, Matrix4f matrix,
                               float x, float y, float z, float u, float v) {
        vc.vertex(matrix, x, y, z)
                .color(1f, 1f, 1f, 1f)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(NORMAL.x, NORMAL.y, NORMAL.z);
    }
}
