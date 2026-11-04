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
 * Roblox Bacon Hair 3D character: renders a classic R6 blocky avatar with 3D Pal Bacon hair,
 * blue motorcycle t-shirt, open black jacket, dark denim jeans, white sneakers and iconic
 * C-clamp hands. Features authentic rigid Roblox limb locomotion and jumping animations.
 */
public final class RobloxBaconCharacter {
    private static final Identifier MODEL = LinkenVisuals.id("characters/roblox_bacon.mesh");
    private static final Identifier TEXTURE = LinkenVisuals.id("characters/roblox_bacon.png");
    private static RudyMesh.Part[] parts;
    private static boolean failed;
    private static boolean textureRegistered;

    private RobloxBaconCharacter() {
    }

    public static boolean active(Object state) {
        return state instanceof RudyRenderState custom && "Roblox Bacon".equals(custom.linkenvisuals$getCustomCharacter());
    }

    public static boolean available() {
        if (failed) {
            return false;
        }
        if (parts != null && textureRegistered) {
            return true;
        }
        try {
            if (parts == null) {
                try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(MODEL);
                     DataInputStream input = new DataInputStream(stream)) {
                    parts = RudyMesh.parse(input, 1);
                }
            }
            registerTexture();
            return true;
        } catch (IOException | RuntimeException exception) {
            failed = true;
            LinkenVisuals.LOGGER.error("Could not load Roblox Bacon character; using vanilla player model", exception);
            return false;
        }
    }

    private static void registerTexture() throws IOException {
        if (textureRegistered) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        try (InputStream stream = client.getResourceManager().open(TEXTURE)) {
            NativeImage image = NativeImage.read(stream);
            client.getTextureManager().registerTexture(TEXTURE, new NativeImageBackedTexture(TEXTURE::toString, image));
        }
        textureRegistered = true;
    }

    public static void submit(OrderedRenderCommandQueue queue, MatrixStack matrices, PlayerEntityModel model,
                              PlayerEntityRenderState state, int light, int overlay, int color) {
        RudyMesh.Part[] loaded = parts;
        if (loaded == null) {
            return;
        }
        MatrixStack.Entry snapshot = matrices.peek().copy();
        boolean translucent = state.invisible && !state.invisibleToPlayer;
        RenderLayer layer = translucent
                ? RenderLayers.entityTranslucent(TEXTURE)
                : RenderLayers.entityCutoutNoCull(TEXTURE);

        queue.submitCustom(matrices, layer,
                (entry, consumer) -> emit(model, state, loaded, snapshot, consumer, light, overlay, color));
    }

    private static final ThreadLocal<MatrixStack> LOCAL_MATRICES = ThreadLocal.withInitial(MatrixStack::new);

    private static void emit(PlayerEntityModel model, PlayerEntityRenderState state, RudyMesh.Part[] loaded,
                             MatrixStack.Entry snapshot, VertexConsumer consumer,
                             int light, int overlay, int color) {
        model.setAngles(state);
        pose(model, state);

        MatrixStack local = LOCAL_MATRICES.get();
        local.push();
        local.peek().copy(snapshot);
        for (RudyMesh.Part mesh : loaded) {
            local.push();
            partOf(model, mesh.part()).applyTransform(local);
            MatrixStack.Entry entry = local.peek();
            float[] data = mesh.vertices();
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
     * Classic Roblox R6 blocky animation:
     * - Perfectly rigid limb swings without elbow/knee bend
     * - Authentic vertical torso bobbing during stride
     * - Classic Roblox idle arm splay
     * - Iconic arms-up jumping posture
     */
    public static void pose(PlayerEntityModel model, PlayerEntityRenderState state) {
        float crouch = state.sneaking ? 0.18f : 0f;
        pivot(model.head, 0f, 0.05f + crouch, 0f);
        pivot(model.body, 0f, 0.05f + crouch, 0f);
        pivot(model.rightArm, -0.3125f, 0.125f + crouch, 0f);
        pivot(model.leftArm, 0.3125f, 0.125f + crouch, 0f);
        pivot(model.rightLeg, -0.125f, 0.75f, 0f);
        pivot(model.leftLeg, 0.125f, 0.75f, 0f);

        // Reset rolls & yaws for rigid R6 movement
        model.rightArm.roll = -0.10f;
        model.leftArm.roll = 0.10f;
        model.rightArm.yaw = 0f;
        model.leftArm.yaw = 0f;
        model.rightLeg.roll = 0f;
        model.leftLeg.roll = 0f;
        model.rightLeg.yaw = 0f;
        model.leftLeg.yaw = 0f;

        boolean inAir = state instanceof RudyRenderState r && r.linkenvisuals$isAirborne();
        if (inAir) {
            // Iconic Roblox jump: arms raised straight up, legs slightly spread
            model.rightArm.pitch = -2.6f;
            model.rightArm.roll = -0.22f;
            model.leftArm.pitch = -2.6f;
            model.leftArm.roll = 0.22f;
            model.rightLeg.pitch = 0.24f;
            model.leftLeg.pitch = -0.18f;
        } else if (state.limbSwingAmplitude > 0.04f) {
            // Classic R6 rigid swing locomotion
            float progress = state.limbSwingAnimationProgress * 0.6662f;
            float amp = Math.min(1.0f, state.limbSwingAmplitude);
            float swing = (float) Math.cos(progress) * 0.95f * amp;

            model.rightLeg.pitch = swing;
            model.leftLeg.pitch = -swing;
            model.rightArm.pitch = -swing;
            model.leftArm.pitch = swing;

            // Roblox character bounce/bobbing
            float bob = Math.abs((float) Math.sin(progress * 2.0f)) * 0.045f * amp;
            model.body.setOrigin(0f, (0.05f + crouch + bob) * 16f, 0f);
            model.head.setOrigin(0f, (0.05f + crouch + bob) * 16f, 0f);
        } else {
            // Idle stance: gentle robotic Roblox breathing
            float breathe = (float) Math.sin(state.age * 0.08f) * 0.02f;
            model.rightArm.pitch = 0.08f + breathe;
            model.leftArm.pitch = 0.08f + breathe;
        }
    }

    private static void pivot(ModelPart part, float x, float y, float z) {
        part.setOrigin(x * 16f, y * 16f, z * 16f);
    }

    public static void armGrip(Arm arm, MatrixStack matrices) {
        matrices.translate(arm == Arm.RIGHT ? 0.02f : -0.02f, -0.06f, 0f);
    }
}
