package dev.linkenvisuals.cosmetic;

import dev.linkenvisuals.clickgui.ModuleRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class Cosmetics {
    private static final float CROWN_CHASE = 7.5f;

    private static final CosmeticContext CONTEXT = new CosmeticContext();

    private static float crownYaw;
    private static float clock;
    private static long lastFrame = System.nanoTime();

    private Cosmetics() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ModuleRegistry.cosmetics().enabled() || client.world == null || client.player == null) {
            return;
        }
        if (client.options != null && client.options.getPerspective() == Perspective.FIRST_PERSON) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrices();
        Camera camera = client.gameRenderer.getCamera();
        if (consumers == null || matrices == null || camera == null) {
            return;
        }

        long now = System.nanoTime();
        float delta = (float) Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
        lastFrame = now;
        clock += delta * ModuleRegistry.cosmeticSpeed().value();

        AbstractClientPlayerEntity player = client.player;
        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        Vec3d at = player.getLerpedPos(tickDelta);
        Vec3d eye = camera.getCameraPos();

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);

        crownYaw += MathHelper.wrapDegrees(bodyYaw - crownYaw) * Math.min(1.0f, delta * CROWN_CHASE);

        Vec3d here = player.getEntityPos();
        double dx = here.x - player.lastX;
        double dz = here.z - player.lastZ;
        float speed = (float) Math.sqrt(dx * dx + dz * dz) * 20.0f;
        float hurt = player.hurtTime / (float) Math.max(1, player.maxHurtTime);

        float size = ModuleRegistry.cosmeticSize().value();
        int color = ModuleRegistry.cosmeticColor().rgb();

        String back = ModuleRegistry.cosmeticSlot("back");
        String aura = ModuleRegistry.cosmeticSlot("aura");
        String crown = ModuleRegistry.cosmeticSlot("crown");

        matrices.push();
        matrices.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);

        for (int pass = 0; pass < 3; pass++) {
            VertexConsumer target = consumers.getBuffer(pass == CosmeticContext.SOLID
                ? Material.SOLID : pass == CosmeticContext.SHEET ? Material.BODY : Material.LIGHT);

            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
            CosmeticContext worn = context(target, pass, matrices, player, size, color, speed, hurt);
            switch (back) {
                case "WaveCape" -> WaveCape.render(worn);
                default -> { }
            }
            switch (aura) {
                case "Spheres" -> Spheres.render(worn);
                default -> { }
            }
            matrices.pop();

            if (!"None".equals(crown)) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-crownYaw));
                CosmeticContext head = context(target, pass, matrices, player, size, color, speed, hurt);
                switch (crown) {
                    case "Nimb" -> Nimb.render(head);
                    default -> { }
                }
                matrices.pop();
            }
        }

        matrices.pop();

        if (consumers instanceof net.minecraft.client.render.VertexConsumerProvider.Immediate immediate) {
            immediate.draw(Material.SOLID);
            immediate.draw(Material.BODY);
            immediate.draw(Material.LIGHT);
        }
    }

    private static CosmeticContext context(VertexConsumer target, int pass, MatrixStack matrices,
                                           AbstractClientPlayerEntity player, float size, int color,
                                           float speed, float hurt) {
        // Shared, re-set on every call: valid until the next context(...) in the pass loop.
        CONTEXT.set(target, pass, matrices.peek().getPositionMatrix(), player,
            clock, size, color, speed, hurt, player.getHeight());
        return CONTEXT;
    }
}
