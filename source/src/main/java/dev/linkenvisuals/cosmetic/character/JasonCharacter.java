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
 * Jason (GTA 6 protagonist) 3D character: renders an athletic muscular physique with chest hair,
 * 3D silver curb chain necklace, backward camo cap with 3D visor, tactical sunglasses with
 * polarized emerald lenses, camo cargo pants with 3D utility pockets, tactical belt, striped athletic
 * socks and sneakers. Features realistic weighted locomotion kinematics and athletic breathing idle.
 */
public final class JasonCharacter {
    private static final Identifier MODEL = LinkenVisuals.id("characters/jason_gta6.mesh");
    private static final Identifier TEXTURE = LinkenVisuals.id("characters/jason_gta6.png");
    private static RudyMesh.Part[] parts;
    private static boolean failed;
    private static boolean textureRegistered;

    private JasonCharacter() {
    }

    public static boolean active(Object state) {
        return state instanceof RudyRenderState custom && "Jason GTA 6".equals(custom.linkenvisuals$getCustomCharacter());
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
            LinkenVisuals.LOGGER.error("Could not load Jason GTA 6 character; using vanilla player model", exception);
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
     * GTA 6 weighted realistic kinematics:
     * - Subtle natural shoulder roll and athletic outward arm flare
     * - Weighted torso sway and pelvis counter-rotation during movement
     * - Deep athletic diaphragmatic breathing idle
     * - Tactical low-center-of-gravity crouch posture
     */
    public static void pose(PlayerEntityModel model, PlayerEntityRenderState state) {
        float crouch = state.sneaking ? 0.20f : 0f;
        pivot(model.head, 0f, 0.0f + crouch, 0f);
        pivot(model.body, 0f, 0.0f + crouch, 0f);
        pivot(model.rightArm, -0.32f, 0.12f + crouch, 0f);
        pivot(model.leftArm, 0.32f, 0.12f + crouch, 0f);
        pivot(model.rightLeg, -0.125f, 0.75f, 0f);
        pivot(model.leftLeg, 0.125f, 0.75f, 0f);

        // Athletic upper body build: natural relaxed outward arm angle
        model.rightArm.roll = -0.08f;
        model.leftArm.roll = 0.08f;

        boolean inAir = state instanceof RudyRenderState r && r.linkenvisuals$isAirborne();
        if (inAir) {
            // Athletic jump dynamics: arms braced, one leg trailing
            model.rightArm.pitch = -0.85f;
            model.leftArm.pitch = -0.75f;
            model.rightLeg.pitch = 0.35f;
            model.leftLeg.pitch = -0.25f;
        } else if (state.limbSwingAmplitude > 0.04f) {
            // Realistic weighted locomotion: smooth counter-balancing torso roll and arm swing
            float progress = state.limbSwingAnimationProgress * 0.6662f;
            float amp = Math.min(1.0f, state.limbSwingAmplitude);
            float swing = (float) Math.cos(progress) * 0.75f * amp;

            model.rightLeg.pitch = swing;
            model.leftLeg.pitch = -swing;
            model.rightArm.pitch = -swing * 0.85f;
            model.leftArm.pitch = swing * 0.85f;

            // Torso weight shift & natural pelvic sway
            float sway = (float) Math.sin(progress) * 0.045f * amp;
            model.body.roll = sway;
            model.body.yaw = -sway * 0.5f;

            // Subtle vertical weight transfer bobbing
            float bob = Math.abs((float) Math.sin(progress * 2.0f)) * 0.025f * amp;
            model.body.setOrigin(0f, (crouch + bob) * 16f, 0f);
            model.head.setOrigin(0f, (crouch + bob) * 16f, 0f);
        } else {
            // Athletic breathing idle: deep chest expansion and subtle head compensation
            float breathe = (float) Math.sin(state.age * 0.06f) * 0.022f;
            model.body.pitch = breathe;
            model.head.pitch = -breathe * 0.5f;
            model.rightArm.pitch = 0.05f - breathe * 0.3f;
            model.leftArm.pitch = 0.05f - breathe * 0.3f;
            model.body.roll = 0f;
            model.body.yaw = 0f;
        }
    }

    private static void pivot(ModelPart part, float x, float y, float z) {
        part.setOrigin(x * 16f, y * 16f, z * 16f);
    }

    public static void armGrip(Arm arm, MatrixStack matrices) {
        matrices.translate(arm == Arm.RIGHT ? 0.015f : -0.015f, -0.04f, 0f);
    }
}
