package ru.method.linkenvisuals.cosmetics.render;

import dev.linkenvisuals.render.GuiModelIconPipeline;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ru.method.linkenvisuals.cosmetics.bedrock.BedrockModel;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoBuiltBone;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoBuiltCube;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoQuad;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoVertex;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 3D cosmetic icon rendered into the deferred 1.21.11 GUI. The donor submitted the Gecko model
 * through the immediate entity vertex provider; that path is invisible in the new GUI renderer,
 * so the same model transform chain is evaluated on the CPU, projected orthographically and
 * emitted as textured GUI quads (painter-sorted).
 */
public final class CosmeticModelIconRenderer {

    private static final float UNIT = 1f / 16f;
    private static final float MORECOSMETICS_SIZE = 32f;
    private static final Vector3f LIGHT = new Vector3f(0.35f, 0.62f, 0.70f).normalize();

    private CosmeticModelIconRenderer() {
    }

    public static void render(DrawContext context, int x, int y, int size, BedrockModel model,
                              CosmeticSlot slot, int tintArgb) {
        if (model == null || model.isEmpty() || model.texture() == null || size <= 0) {
            return;
        }

        float centerX = x + size * 0.5f;
        float centerY = y + size * 0.5f + (slot == CosmeticSlot.WINGS ? size * 0.24f : 0f);
        float scaleFactor = Math.max(0.75f, size / 46f);
        float previewSize = MORECOSMETICS_SIZE * scaleFactor;

        float rotX = slot == CosmeticSlot.WINGS ? 180f : 190f;
        float rotY = slot == CosmeticSlot.WINGS ? 6f : -4f;

        Matrix4f base = new Matrix4f();
        base.translate(centerX, centerY, 100f);
        base.scale(-(previewSize + 10f), previewSize + 10f, previewSize + 10f);
        base.rotateZ((float) Math.toRadians(180f));
        base.rotateY((float) Math.toRadians(135f));
        base.rotateY((float) Math.toRadians(-135f + rotX));
        base.rotateX((float) Math.toRadians(rotY));
        base.scale(-0.5f, 0.5f, 0.5f);

        float previewScale = model.previewScale();
        if (slot == CosmeticSlot.WINGS) {
            previewScale = Math.max(previewScale * 1.85f, 0.7f);
        } else if (slot == CosmeticSlot.HAT || slot == CosmeticSlot.HEAD) {
            previewScale = Math.max(previewScale, 1.15f);
        }

        base.translate(-0.05f, model.previewY(), 0f);
        base.scale(previewScale, previewScale, previewScale);

        if (model.yaw() != 0f) {
            base.rotateY((float) Math.toRadians(model.yaw()));
        }
        if (model.pitch() != 0f) {
            base.rotateX((float) Math.toRadians(model.pitch()));
        }
        if (model.roll() != 0f) {
            base.rotateZ((float) Math.toRadians(model.roll()));
        }
        base.translate(model.offsetX() * UNIT, model.offsetY() * UNIT, model.offsetZ() * UNIT);
        float modelScale = model.scale();
        base.scale(modelScale, modelScale, modelScale);

        List<GuiModelIconPipeline.Quad> quads = new ArrayList<>();
        for (GeckoBuiltBone root : model.geckoRoots()) {
            collect(root, base, quads, tintArgb);
        }
        if (quads.isEmpty()) {
            return;
        }
        quads.sort(Comparator.comparingDouble(GuiModelIconPipeline.Quad::depth));

        GuiModelIconPipeline.draw(context, model.texture(), x, y, size, size, quads);
    }

    private static void collect(GeckoBuiltBone bone, Matrix4f parent,
                                List<GuiModelIconPipeline.Quad> out, int tint) {
        Matrix4f boneMatrix = new Matrix4f(parent);
        boneMatrix.translate(bone.rotationPointX * UNIT, bone.rotationPointY * UNIT, bone.rotationPointZ * UNIT);
        boneMatrix.rotateZ(bone.rotationZ);
        boneMatrix.rotateY(bone.rotationY);
        boneMatrix.rotateX(bone.rotationX);
        boneMatrix.translate(-bone.rotationPointX * UNIT, -bone.rotationPointY * UNIT, -bone.rotationPointZ * UNIT);

        for (GeckoBuiltCube cube : bone.cubes) {
            Matrix4f cubeMatrix = new Matrix4f(boneMatrix);
            cubeMatrix.translate(cube.pivotX * UNIT, cube.pivotY * UNIT, cube.pivotZ * UNIT);
            cubeMatrix.rotateZ(cube.rotationZ);
            cubeMatrix.rotateY(cube.rotationY);
            cubeMatrix.rotateX(cube.rotationX);
            cubeMatrix.translate(-cube.pivotX * UNIT, -cube.pivotY * UNIT, -cube.pivotZ * UNIT);

            for (GeckoQuad quad : cube.quads) {
                float[] xy = new float[8];
                float[] uv = new float[8];
                int[] colors = new int[4];
                float depth = 0f;

                Vector3f normal = new Vector3f(quad.normalX, quad.normalY, quad.normalZ)
                        .mulDirection(cubeMatrix).normalize();
                float shade = 0.62f + 0.38f * Math.max(0f, normal.dot(LIGHT));
                int color = shade(tint, shade);

                for (int i = 0; i < 4; i++) {
                    GeckoVertex vertex = quad.vertices[i];
                    Vector4f p = new Vector4f(vertex.x, vertex.y, vertex.z, 1f).mul(cubeMatrix);
                    xy[i * 2] = p.x;
                    xy[i * 2 + 1] = p.y;
                    uv[i * 2] = vertex.u;
                    uv[i * 2 + 1] = vertex.v;
                    colors[i] = color;
                    depth += p.z;
                }
                out.add(new GuiModelIconPipeline.Quad(xy, uv, colors, depth * 0.25f));
            }
        }

        for (GeckoBuiltBone child : bone.children) {
            collect(child, boneMatrix, out, tint);
        }
    }

    private static int shade(int argb, float shade) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >> 16) & 0xFF) * shade);
        int g = Math.round(((argb >> 8) & 0xFF) * shade);
        int b = Math.round((argb & 0xFF) * shade);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
}
