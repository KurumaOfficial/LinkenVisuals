package ru.method.linkenvisuals.cosmetics.character;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.accessors.ICharacterRenderState;

import java.io.DataInputStream;
import java.io.IOException;

/** Rigid mesh parts follow vanilla arm/use-item poses, with Rudy's own attachment pivots. */
public final class RudyCharacter {
    private static final Identifier MODEL = LinkenVisuals.id("characters/rudy.mesh");
    private static final Identifier[] TEXTURES = {
            LinkenVisuals.id("characters/rudy_0.png"), LinkenVisuals.id("characters/rudy_1.png")};
    private static Mesh[] meshes;
    private static boolean failed;
    private record Mesh(int part, int material, float[] vertices) {}

    private RudyCharacter() {}

    public static boolean active(Object state) {
        return state instanceof ICharacterRenderState character && character.linkenvisuals$isRudy();
    }

    public static void reset() { meshes = null; failed = false; }

    public static boolean available() {
        if (meshes != null) return true;
        if (failed) return false;
        try (var stream = MinecraftClient.getInstance().getResourceManager().open(MODEL);
             var input = new DataInputStream(stream)) {
            if (input.readInt() != 0x52554459) throw new IOException("Invalid Rudy mesh header");
            int count = input.readInt();
            if (count < 1 || count > 64) throw new IOException("Invalid mesh count");
            Mesh[] loaded = new Mesh[count];
            for (int i = 0; i < count; i++) {
                int part = input.readInt(), material = input.readInt(), vertices = input.readInt();
                if (part < 0 || part > 5 || material < 0 || material >= TEXTURES.length
                        || vertices < 3 || vertices > 100_000 || vertices % 3 != 0)
                    throw new IOException("Invalid Rudy mesh part");
                float[] data = new float[vertices * 8];
                for (int v = 0; v < data.length; v++) {
                    data[v] = input.readFloat();
                    if (!Float.isFinite(data[v])) throw new IOException("Non-finite vertex");
                }
                loaded[i] = new Mesh(part, material, data);
            }
            meshes = loaded;
            return true;
        } catch (IOException | RuntimeException exception) {
            failed = true;
            LinkenVisuals.LOGGER.error("Could not load Rudy character; using player model", exception);
            return false;
        }
    }

    public static void pose(PlayerEntityModel model, PlayerEntityRenderState state) {
        float crouch = state.isInSneakingPose ? .16f : 0f;
        pivot(model.head, 0, .20f + crouch, 0);
        pivot(model.body, 0, .65f + crouch, 0);
        pivot(model.rightArm, -.59f, .52f + crouch, -.03f);
        pivot(model.leftArm, .59f, .52f + crouch, -.03f);
        pivot(model.rightLeg, -.23f, 1.30f, -.027f);
        pivot(model.leftLeg, .16f, 1.30f, -.027f);
        model.hat.setTransform(model.head.getTransform());
        model.jacket.setTransform(model.body.getTransform());
        model.rightSleeve.setTransform(model.rightArm.getTransform());
        model.leftSleeve.setTransform(model.leftArm.getTransform());
        model.rightPants.setTransform(model.rightLeg.getTransform());
        model.leftPants.setTransform(model.leftLeg.getTransform());
    }

    private static void pivot(ModelPart part, float x, float y, float z) {
        part.setOrigin(x * 16, y * 16, z * 16);
    }

    public static void render(PlayerEntityModel model, MatrixStack matrices, VertexConsumerProvider buffers,
                              int light, int overlay, int color, boolean translucent, boolean outline) {
        if (!available()) return;
        for (Mesh mesh : meshes) {
            ModelPart part = partFor(model, mesh.part);
            Identifier texture = TEXTURES[mesh.material];
            RenderLayer layer = outline ? net.minecraft.client.render.RenderLayers.outlineNoCull(texture)
                    : translucent ? net.minecraft.client.render.RenderLayers.entityTranslucent(texture)
                    : net.minecraft.client.render.RenderLayers.entityCutoutNoCull(texture);
            VertexConsumer consumer = buffers.getBuffer(layer);
            matrices.push();
            try {
                applyPartRotation(matrices, part);
                writeMesh(matrices, consumer, mesh.vertices, light, overlay, color);
            } finally { matrices.pop(); }
        }
    }

    /**
     * 1.21.11 deferred entity rendering: submit the Rudy mesh into the ordered queue and replace the
     * vanilla player model (the caller skips EntityModel.render when this returns true).
     */
    public static boolean submit(OrderedRenderCommandQueue queue, MatrixStack matrices, PlayerEntityModel model,
                                 PlayerEntityRenderState state, int light, int overlay, int color) {
        if (!available() || queue == null) return false;
        for (Mesh mesh : meshes) {
            ModelPart part = partFor(model, mesh.part);
            RenderLayer layer = RenderLayers.entityCutoutNoCull(TEXTURES[mesh.material]);
            MatrixStack snapshot = new MatrixStack();
            snapshot.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
            snapshot.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
            snapshot.push();
            applyPartRotation(snapshot, part);
            queue.submitCustom(matrices, layer, (entry, consumer) ->
                    writeMesh(snapshot, consumer, mesh.vertices, light, overlay, color));
        }
        return true;
    }

    private static void applyPartRotation(MatrixStack matrices, ModelPart part) {
        if (part.roll != 0f) matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(part.roll));
        if (part.yaw != 0f) matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation(part.yaw));
        if (part.pitch != 0f) matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotation(part.pitch));
    }

    private static ModelPart partFor(PlayerEntityModel model, int part) {
        return switch (part) {
            case 0 -> model.head;
            case 1 -> model.body;
            case 2 -> model.rightArm;
            case 3 -> model.leftArm;
            case 4 -> model.rightLeg;
            default -> model.leftLeg;
        };
    }

    private static void writeMesh(MatrixStack matrices, VertexConsumer consumer, float[] data,
                                  int light, int overlay, int color) {
        MatrixStack.Entry entry = matrices.peek();
        // Entity layers use QUADS. Repeat the last vertex of each triangle to make a degenerate quad.
        for (int v = 0; v < data.length; v += 24) {
            vertex(consumer, entry, data, v, light, overlay, color);
            vertex(consumer, entry, data, v + 8, light, overlay, color);
            vertex(consumer, entry, data, v + 16, light, overlay, color);
            vertex(consumer, entry, data, v + 16, light, overlay, color);
        }
    }

    private static void vertex(VertexConsumer out, MatrixStack.Entry entry, float[] v, int i,
                               int light, int overlay, int color) {
        out.vertex(entry.getPositionMatrix(), v[i], v[i + 1], v[i + 2]).color(color)
                .texture(v[i + 6], v[i + 7]).overlay(overlay).light(light)
                .normal(entry, v[i + 3], v[i + 4], v[i + 5]);
    }
}
