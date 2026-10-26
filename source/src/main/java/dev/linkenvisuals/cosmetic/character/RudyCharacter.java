package dev.linkenvisuals.cosmetic.character;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.render.state.RudyRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Rudy character (Phase 5.6): the donor's rigid player-character meshes replace the vanilla
 * player model for the local player. Mesh parts follow the vanilla arm/leg/head poses but use
 * Rudy's own attachment pivots.
 *
 * <p>1.21.11 adaptation: the entity pipeline submits models as deferred commands, so instead of
 * wrapping a direct {@code model.render} call the meshes are re-submitted as per-material custom
 * commands ({@link OrderedRenderCommandQueue#submitCustom}) and the shared model is re-posed
 * inside the draw lambda (vanilla re-poses it per model command the same way).</p>
 */
public final class RudyCharacter {
    private static final Identifier MODEL = LinkenVisuals.id("characters/rudy.mesh");
    private static final Identifier[] TEXTURES = {
            LinkenVisuals.id("characters/rudy_0.png"), LinkenVisuals.id("characters/rudy_1.png")};
    private static RudyMesh.Part[] parts;
    private static boolean failed;
    private static boolean texturesRegistered;

    private RudyCharacter() {
    }

    /** True when the given render state asked for Rudy (set per frame by {@code PlayerEntityRendererMixin}). */
    public static boolean active(Object state) {
        return state instanceof RudyRenderState rudy && rudy.linkenvisuals$isRudy();
    }

    /** Loads the mesh and registers the textures on first use. Render thread only. */
    public static boolean available() {
        if (failed) {
            return false;
        }
        if (parts != null && texturesRegistered) {
            return true;
        }
        try {
            if (parts == null) {
                try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(MODEL);
                     DataInputStream input = new DataInputStream(stream)) {
                    parts = RudyMesh.parse(input, TEXTURES.length);
                }
            }
            registerTextures();
            return true;
        } catch (IOException | RuntimeException exception) {
            failed = true;
            LinkenVisuals.LOGGER.error("Could not load Rudy character; using the vanilla player model", exception);
            return false;
        }
    }

    private static void registerTextures() throws IOException {
        if (texturesRegistered) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier texture : TEXTURES) {
            try (InputStream stream = client.getResourceManager().open(texture)) {
                NativeImage image = NativeImage.read(stream);
                client.getTextureManager().registerTexture(texture, new NativeImageBackedTexture(texture::toString, image));
            }
        }
        texturesRegistered = true;
    }

    /**
     * Replaces the vanilla player-model submission with Rudy's meshes. One custom command per
     * material because a render layer binds exactly one texture.
     */
    public static void submit(OrderedRenderCommandQueue queue, MatrixStack matrices, PlayerEntityModel model,
                              PlayerEntityRenderState state, int light, int overlay, int color) {
        RudyMesh.Part[] loaded = parts;
        if (loaded == null) {
            return;
        }
        MatrixStack.Entry snapshot = matrices.peek().copy();
        boolean translucent = state.invisible && !state.invisibleToPlayer;
        for (int material = 0; material < TEXTURES.length; material++) {
            RenderLayer layer = translucent
                    ? RenderLayers.entityTranslucent(TEXTURES[material])
                    : RenderLayers.entityCutoutNoCull(TEXTURES[material]);
            int index = material;
            queue.submitCustom(matrices, layer,
                    (entry, consumer) -> emit(model, state, loaded, index, snapshot, consumer, light, overlay, color));
        }
    }

    private static final ThreadLocal<MatrixStack> LOCAL_MATRICES = ThreadLocal.withInitial(MatrixStack::new);

    private static void emit(PlayerEntityModel model, PlayerEntityRenderState state, RudyMesh.Part[] loaded,
                             int material, MatrixStack.Entry snapshot, VertexConsumer consumer,
                             int light, int overlay, int color) {
        // The draw lambda runs after other entities may have re-posed the shared model: re-apply
        // this state's pose so the attachment pivots match the meshes that are about to be drawn.
        model.setAngles(state);
        MatrixStack local = LOCAL_MATRICES.get();
        local.push();
        local.peek().copy(snapshot);
        for (RudyMesh.Part mesh : loaded) {
            if (mesh.material() != material) {
                continue;
            }
            local.push();
            partOf(model, mesh.part()).applyTransform(local);
            MatrixStack.Entry entry = local.peek();
            float[] data = mesh.vertices();
            // Entity layers use QUADS: repeat the last vertex of each triangle to make a degenerate quad.
            for (int v = 0; v < data.length; v += RudyMesh.TRIANGLE_FLOATS) {
                vertex(consumer, entry, data, v, light, overlay, color);
                vertex(consumer, entry, data, v + RudyMesh.VERTEX_FLOATS, light, overlay, color);
                vertex(consumer, entry, data, v + 2 * RudyMesh.VERTEX_FLOATS, light, overlay, color);
                vertex(consumer, entry, data, v + 2 * RudyMesh.VERTEX_FLOATS, light, overlay, color);
            }
            local.pop();
        }
        local.pop();
    }

    private static ModelPart partOf(PlayerEntityModel model, int part) {
        return switch (part) {
            case 0 -> model.head;
            case 1 -> model.body;
            case 2 -> model.rightArm;
            case 3 -> model.leftArm;
            case 4 -> model.rightLeg;
            default -> model.leftLeg;
        };
    }

    private static void vertex(VertexConsumer out, MatrixStack.Entry entry, float[] data, int i,
                               int light, int overlay, int color) {
        out.vertex(entry, data[i], data[i + 1], data[i + 2])
                .color(color)
                .texture(data[i + 6], data[i + 7])
                .overlay(overlay)
                .light(light)
                .normal(entry, data[i + 3], data[i + 4], data[i + 5]);
    }

    /**
     * Rudy's attachment pivots (donor values, block units; the model stores 1/16 units). Runs at
     * the tail of {@code PlayerEntityModel.setAngles} so the meshes attach where the donor model
     * expects them.
     */
    public static void pose(PlayerEntityModel model, boolean sneaking) {
        float crouch = sneaking ? .16f : 0f;
        pivot(model.head, 0f, .20f + crouch, 0f);
        pivot(model.body, 0f, .65f + crouch, 0f);
        pivot(model.rightArm, -.59f, .52f + crouch, -.03f);
        pivot(model.leftArm, .59f, .52f + crouch, -.03f);
        pivot(model.rightLeg, -.23f, 1.30f, -.027f);
        pivot(model.leftLeg, .16f, 1.30f, -.027f);
    }

    private static void pivot(ModelPart part, float x, float y, float z) {
        part.setOrigin(x * 16f, y * 16f, z * 16f);
    }

    /** Vanilla grips 10 px below the shoulder; Rudy's wing is only about 6 px long (donor fix). */
    public static void armGrip(Arm arm, MatrixStack matrices) {
        matrices.translate(arm == Arm.RIGHT ? .0625f : -.0625f, -.25f, -.10f);
    }
}
