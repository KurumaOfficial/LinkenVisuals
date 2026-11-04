package ru.method.linkenvisuals.screen.cosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.cosmetics.render.CosmeticEntityPreviewRenderer;
import ru.method.linkenvisuals.cosmetics.render.CosmeticGuiRenderer;

public final class CosmeticPlayerPreview {
    private static final float PREVIEW_YAW = 195f;

    private CosmeticSlot slot = CosmeticSlot.CAPE;
    private CosmeticProfile previewProfile = new CosmeticProfile();

    public void reset() {
        slot = CosmeticSlot.CAPE;
        previewProfile = new CosmeticProfile();
    }

    public void setSlot(CosmeticSlot slot) {
        if (slot != null) {
            this.slot = slot;
        }
    }

    public void setPreviewProfile(CosmeticProfile previewProfile) {
        this.previewProfile = previewProfile == null ? new CosmeticProfile() : previewProfile;
    }

    public void renderMain(DrawContext context, int x, int y, int width, int height, float alpha) {
        renderMain(context, x, y, width, height, alpha, true);
    }

    public void renderMain(DrawContext context, int x, int y, int width, int height, float alpha, boolean drawStage) {
        if (alpha <= 0.01f || width <= 0 || height <= 0) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        LivingEntity player = client.player;
        PoseSnapshot pose = PoseSnapshot.capture(player);

        CosmeticPreviewState.begin(new CosmeticPreviewState.Snapshot(
                previewProfile,
                slot,
                false,
                0f,
                0f,
                1f / 60f,
                true
        ));

        try {
            applyStudioPose(player, slot);
            CosmeticEntityPreviewRenderer.draw(context, x, y, width, height, player, slot);
        } finally {
            pose.restore(player);
            CosmeticPreviewState.end();
        }
    }

    public void renderIcon(
            DrawContext context,
            int x,
            int y,
            int size,
            CosmeticDefinition definition,
            CosmeticSlot iconSlot,
            float alpha
    ) {
        if (definition == null || definition.isNone() || alpha <= 0.01f || size <= 0) {
            return;
        }

        int tint = CosmeticGuiRenderer.applyAlpha(CosmeticProfile.DEFAULT_TINT, alpha);
        CosmeticGuiRenderer.renderGridIcon(context, x, y, size, definition, iconSlot, tint);
    }

    private static void applyStudioPose(LivingEntity entity, CosmeticSlot slot) {
        float baseYaw = (slot == CosmeticSlot.CAPE || slot == CosmeticSlot.WINGS) ? 15f : PREVIEW_YAW;
        float yaw = baseYaw + CosmeticEntityPreviewRenderer.yawDeg;
        float pitch = CosmeticEntityPreviewRenderer.pitchDeg;
        entity.setYaw(yaw);
        entity.setPitch(pitch);
        entity.lastYaw = yaw;
        entity.lastPitch = pitch;
        entity.setSprinting(false);
        entity.setSneaking(false);
        if (entity instanceof PlayerEntity player) {
            player.bodyYaw = yaw;
            player.lastBodyYaw = yaw;
            player.headYaw = yaw;
            player.lastHeadYaw = yaw;
        }
        if (entity instanceof AbstractClientPlayerEntity clientPlayer) {
            clientPlayer.limbAnimator.reset();
        }
    }

    private record PoseSnapshot(
            float yaw,
            float pitch,
            float prevYaw,
            float prevPrevPitch,
            float bodyYaw,
            float prevBodyYaw,
            float headYaw,
            float prevHeadYaw,
            boolean sprinting,
            boolean sneaking,
            float limbSpeed
    ) {
        static PoseSnapshot capture(LivingEntity entity) {
            float bodyYaw = 0f;
            float prevBodyYaw = 0f;
            float headYaw = 0f;
            float prevHeadYaw = 0f;
            if (entity instanceof PlayerEntity player) {
                bodyYaw = player.bodyYaw;
                prevBodyYaw = player.lastBodyYaw;
                headYaw = player.headYaw;
                prevHeadYaw = player.lastHeadYaw;
            }
            float limbSpeed = entity instanceof AbstractClientPlayerEntity clientPlayer
                    ? clientPlayer.limbAnimator.getSpeed()
                    : 0f;
            return new PoseSnapshot(
                    entity.getYaw(),
                    entity.getPitch(),
                    entity.lastYaw,
                    entity.lastPitch,
                    bodyYaw,
                    prevBodyYaw,
                    headYaw,
                    prevHeadYaw,
                    entity.isSprinting(),
                    entity.isSneaking(),
                    limbSpeed
            );
        }

        void restore(LivingEntity entity) {
            entity.setYaw(yaw);
            entity.setPitch(pitch);
            entity.lastYaw = prevYaw;
            entity.lastPitch = prevPrevPitch;
            entity.setSprinting(sprinting);
            entity.setSneaking(sneaking);
            if (entity instanceof PlayerEntity player) {
                player.bodyYaw = bodyYaw;
                player.lastBodyYaw = prevBodyYaw;
                player.headYaw = headYaw;
                player.lastHeadYaw = prevHeadYaw;
            }
            if (entity instanceof AbstractClientPlayerEntity clientPlayer) {
                clientPlayer.limbAnimator.setSpeed(limbSpeed);
            }
        }
    }
}
