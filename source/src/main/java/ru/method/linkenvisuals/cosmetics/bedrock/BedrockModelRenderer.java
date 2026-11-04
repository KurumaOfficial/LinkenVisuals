package ru.method.linkenvisuals.cosmetics.bedrock;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoBuiltBone;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoBuiltCube;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoQuad;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoVertex;

/**
 * Renders GeckoLib-built cosmetic meshes using the same transform order as
 * MoreCosmetics {@code MatrixStackWrapper} + {@code GeoModelRenderer}.
 */
public final class BedrockModelRenderer {
    private static final float UNIT = 1f / 16f;

    // Переиспользуемые scratch-объекты: рендер косметики идёт на игроке каждый кадр и только в render-потоке,
    // поэтому держим единичные экземпляры вместо new на каждую вершину/куб (убирает GC-давление).
    private static final Vector4f SCRATCH_POSITION = new Vector4f();
    private static final Vector3f SCRATCH_NORMAL = new Vector3f();
    private static final Quaternionf SCRATCH_QUATERNION = new Quaternionf();

    private BedrockModelRenderer() {
    }

    public static void render(
            BedrockModel model,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int color,
            BedrockBonePose bonePose
    ) {
        if (model == null || model.isEmpty() || model.texture() == null) {
            return;
        }

        // Перекраска — вертексным цветом по базовой текстуре (система с origin/master): рабочий вариант,
        // т.к. текстуры косметики серые/белые и vertex-color даёт нужный оттенок.
        VertexConsumer consumer = vertexConsumers.getBuffer(net.minecraft.client.render.RenderLayers.entityCutoutNoCull(model.texture()));
        for (GeckoBuiltBone root : model.geckoRoots()) {
            renderBone(root, matrices, consumer, light, color, bonePose);
        }
    }

    /**
     * 1.21.11 entity rendering is deferred: feature renderers must submit their geometry into the
     * ordered command queue instead of writing into the old entity VertexConsumerProvider.
     */
    public static void render(
            BedrockModel model,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            int color,
            BedrockBonePose bonePose
    ) {
        if (model == null || model.isEmpty() || model.texture() == null || queue == null) {
            return;
        }

        RenderLayer layer = RenderLayers.entityCutoutNoCull(model.texture());
        MatrixStack snapshot = new MatrixStack();
        snapshot.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
        snapshot.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
        queue.submitCustom(matrices, layer, (entry, consumer) ->
                renderModel(model, snapshot, consumer, light, color, bonePose));
    }

    private static void renderModel(
            BedrockModel model,
            MatrixStack matrices,
            VertexConsumer consumer,
            int light,
            int color,
            BedrockBonePose bonePose
    ) {
        for (GeckoBuiltBone root : model.geckoRoots()) {
            renderBone(root, matrices, consumer, light, color, bonePose);
        }
    }

    private static void renderBone(
            GeckoBuiltBone bone,
            MatrixStack matrices,
            VertexConsumer consumer,
            int light,
            int color,
            BedrockBonePose bonePose
    ) {
        matrices.push();

        if (bonePose != null && bonePose.hasPosition(bone.name)) {
            matrices.translate(
                    -bonePose.positionX(bone.name) * UNIT,
                    bonePose.positionY(bone.name) * UNIT,
                    bonePose.positionZ(bone.name) * UNIT
            );
        }

        translatePivot(matrices, bone.rotationPointX, bone.rotationPointY, bone.rotationPointZ);

        float rotationX = bone.rotationX;
        float rotationY = bone.rotationY;
        float rotationZ = bone.rotationZ;
        if (bonePose != null) {
            rotationX += (float) Math.toRadians(bonePose.rotationX(bone.name));
            rotationY += (float) Math.toRadians(bonePose.rotationY(bone.name));
            rotationZ += (float) Math.toRadians(bonePose.rotationZ(bone.name));
        }
        rotateBoneRadians(matrices, rotationX, rotationY, rotationZ);

        if (bonePose != null && bonePose.hasScale(bone.name)) {
            matrices.scale(
                    bonePose.scaleX(bone.name),
                    bonePose.scaleY(bone.name),
                    bonePose.scaleZ(bone.name)
            );
        }

        translatePivot(matrices, -bone.rotationPointX, -bone.rotationPointY, -bone.rotationPointZ);

        for (GeckoBuiltCube cube : bone.cubes) {
            matrices.push();
            renderCube(cube, matrices, consumer, light, color);
            matrices.pop();
        }

        for (GeckoBuiltBone child : bone.children) {
            renderBone(child, matrices, consumer, light, color, bonePose);
        }

        matrices.pop();
    }

    private static void renderCube(
            GeckoBuiltCube cube,
            MatrixStack matrices,
            VertexConsumer consumer,
            int light,
            int color
    ) {
        translatePivot(matrices, cube.pivotX, cube.pivotY, cube.pivotZ);
        if (cube.rotationZ != 0f || cube.rotationY != 0f || cube.rotationX != 0f) {
            matrices.multiply(SCRATCH_QUATERNION.rotationZYX(cube.rotationZ, cube.rotationY, cube.rotationX));
        }
        translatePivot(matrices, -cube.pivotX, -cube.pivotY, -cube.pivotZ);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();

        // Явная распаковка ARGB и ТОЧНЫЙ тинт без HSB-искажений. Текстуры крыльев — светлая мембрана
        // (почти белая) с тёмными деталями, поэтому умножение vertex-color × текстура даёт ровно
        // выбранный оттенок (белое×цвет = цвет), а тёмные перья остаются деталью. Прежний HSB-буст
        // (насыщенность к максимуму + подъём яркости) искажал выбранный цвет — поэтому он «менялся плохо».
        int cA = (color >> 24) & 0xFF;
        int cR = (color >> 16) & 0xFF;
        int cG = (color >> 8) & 0xFF;
        int cB = color & 0xFF;

        for (GeckoQuad quad : cube.quads) {
            Vector3f normal = SCRATCH_NORMAL.set(quad.normalX, quad.normalY, quad.normalZ);
            normal.mul(normalMatrix);
            if ((cube.sizeY == 0f || cube.sizeZ == 0f) && normal.x() < 0f) {
                normal.mul(-1f, 1f, 1f);
            }
            if ((cube.sizeX == 0f || cube.sizeZ == 0f) && normal.y() < 0f) {
                normal.mul(1f, -1f, 1f);
            }
            if ((cube.sizeX == 0f || cube.sizeY == 0f) && normal.z() < 0f) {
                normal.mul(1f, 1f, -1f);
            }

            for (GeckoVertex vertex : quad.vertices) {
                Vector4f position = SCRATCH_POSITION.set(vertex.x, vertex.y, vertex.z, 1f);
                position.mul(matrix);
                consumer.vertex(position.x(), position.y(), position.z())
                        .color(cR, cG, cB, cA)
                        .texture(vertex.u, vertex.v)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(light)
                        .normal(normal.x(), normal.y(), normal.z());
            }
        }
    }

    private static void translatePivot(MatrixStack matrices, float x, float y, float z) {
        matrices.translate(x * UNIT, y * UNIT, z * UNIT);
    }

    private static void rotateBoneRadians(MatrixStack matrices, float rotationX, float rotationY, float rotationZ) {
        if (rotationZ != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(rotationZ));
        }
        if (rotationY != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(rotationY));
        }
        if (rotationX != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(rotationX));
        }
    }
}
