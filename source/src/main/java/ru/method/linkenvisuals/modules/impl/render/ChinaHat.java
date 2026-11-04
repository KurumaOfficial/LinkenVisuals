package ru.method.linkenvisuals.modules.impl.render;


import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.managers.misc.FriendManager;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;

import java.awt.Color;


public class ChinaHat extends Module {

    private static final int SEGMENTS = 96;
    private static final float DEFAULT_RADIUS = 0.62f;
    private static final float DEFAULT_HEIGHT = 0.35f;
    private static final float TIP_ALPHA = 1f;
    private static final float BASE_ALPHA_FACTOR = 0.7f;
    private static final float SPIN_SPEED_DEGREES = 50f;

    private final BooleanSetting showFriends = new BooleanSetting("settings.chinahat.show_friends", true);
    private final BooleanSetting rainbow = new BooleanSetting("settings.chinahat.rainbow", false);
    private final EnumSetting<HatStyle> style = new EnumSetting<>("settings.chinahat.style", HatStyle.Classic);

    public ChinaHat() {
        super("ChinaHat", Category.Visuals);
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;

        float tickDelta = event.getTickCounter().getTickProgress(true);
        MatrixStack matrices = event.getMatrixStack();
        FriendManager friendManager = LinkenVisuals.getInstance().getFriendManager();

        for (PlayerEntity player : mc.world.getPlayers()) {
            boolean isSelf = player == mc.player;
            if (isSelf && mc.options.getPerspective().isFirstPerson()) continue;

            if (!isSelf) {
                if (!showFriends.getValue()) continue;
                if (friendManager == null || !friendManager.isFriend(player.getGameProfile().name())) {
                    continue;
                }
            }
            renderHatForPlayer(matrices, player, tickDelta);
        }

        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        immediate.draw(ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_TRIANGLES_DEPTH);
        immediate.draw(ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_LINES_DEPTH);
    }

    private void renderHatForPlayer(MatrixStack matrices, PlayerEntity player, float tickDelta) {
        Vec3d playerPos = player.getLerpedPos(tickDelta);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        matrices.push();
        try {
            matrices.translate(playerPos.x - cameraPos.x, playerPos.y - cameraPos.y, playerPos.z - cameraPos.z);

            float bodyYaw = MathHelper.lerp(tickDelta, player.lastBodyYaw, player.bodyYaw);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

            // Наруто-наклон корпуса: шляпа должна следовать за визуально наклонённой головой. После поворота
            // по bodyYaw локальная ось +Z = «вперёд», и POSITIVE_X.rotation(+lean) кренит верх вперёд — как у тела
            // (совпадает с дугой нимба). Только свой игрок и только 3-е лицо (для self ChinaHat и так рисуется
            // только в 3-м лице — см. проверку firstPerson выше).
            if (player == mc.player
                    && ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfActive) {
                float pY = ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfPivotY;
                float ln = ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfLeanRad;
                matrices.translate(0.0, pY, 0.0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotation(ln));
                matrices.translate(0.0, -pY, 0.0);
            }

            float eyeHeight = player.getEyeHeight(player.getPose());
            matrices.translate(0.0, eyeHeight - 0.15f, 0.0);

            float headYaw = MathHelper.lerp(tickDelta, player.lastHeadYaw, player.headYaw);
            float netHeadYaw = headYaw - bodyYaw;
            float headPitch = MathHelper.lerp(tickDelta, player.lastPitch, player.getPitch());

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-netHeadYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(headPitch));
            // Наруто-доворот головы: модель головы наклонена ещё на selfHeadDeltaPitch (headDown), а headPitch
            // выше — только look-угол камеры. Докручиваем на ту же дельту, чтобы шляпа следовала за головой.
            if (player == mc.player
                    && ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfActive
                    && Math.abs(ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfHeadDeltaPitch) > 0.0001f) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotation(
                        ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfHeadDeltaPitch));
            }

            float pitchRad = (float) Math.toRadians(MathHelper.clamp(headPitch, -90.0f, 90.0f));
            float cosPitch = MathHelper.clamp((float) Math.cos(pitchRad), 0.0f, 1.0f);
            float tiltFactor = 1.0f - cosPitch;
            float upMultiplier = headPitch < 0.0f ? 1.6f : 1.0f;

            float clearance = 0.03f * cosPitch + (headPitch < 0.0f ? 0.008f * tiltFactor : 0.0f);
            float dynamicOffset = 0.04f + 0.08f * tiltFactor * upMultiplier - 0.05f;
            float forwardOffset = -0.06f * tiltFactor * Math.signum(headPitch) * upMultiplier;
            matrices.translate(0.0, (player.isInSneakingPose() ? 0.305f : 0.265f) + clearance + dynamicOffset, forwardOffset);

            float spin = (float) (((System.currentTimeMillis() - LinkenVisuals.getInstance().getInitTime()) / 1000.0) * SPIN_SPEED_DEGREES);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));

            Color color = resolveHatColor(player);
            HatStyle activeStyle = style.getValue();
            float finalRadius = MathHelper.clamp(DEFAULT_RADIUS * activeStyle.radiusMultiplier, 0.25f, 1.25f);
            float finalHeight = MathHelper.clamp(DEFAULT_HEIGHT * activeStyle.heightMultiplier, 0.1f, 0.85f);
            int styleSegments = SEGMENTS;
            float finalTipAlpha = MathHelper.clamp(TIP_ALPHA * activeStyle.tipAlphaMultiplier, 0.05f, 1.0f);
            float finalBaseAlpha = MathHelper.clamp(BASE_ALPHA_FACTOR * activeStyle.baseAlphaMultiplier, 0.05f, 1.0f);

            switch (activeStyle) {
                case Classic -> renderConeHat(matrices, finalRadius, finalHeight, color, styleSegments, finalTipAlpha, finalBaseAlpha);
                case Layered -> {
                    renderConeHat(matrices, finalRadius, finalHeight * 0.92f, color, styleSegments, finalTipAlpha * 0.95f, finalBaseAlpha);
                    matrices.push();
                    matrices.translate(0.0f, finalHeight * 0.32f, 0.0f);
                    renderConeHat(matrices, finalRadius * 0.62f, finalHeight * 0.68f, ColorUtils.fade(color, Color.WHITE, 0.24f), Math.max(24, styleSegments - 8), finalTipAlpha, finalBaseAlpha * 0.7f);
                    matrices.pop();
                }
                case Halo -> {
                    renderRing(matrices, finalRadius * 0.74f, finalRadius, color, styleSegments, finalBaseAlpha, finalHeight * 0.58f);
                    renderRing(matrices, finalRadius * 0.42f, finalRadius * 0.56f, ColorUtils.fade(color, Color.WHITE, 0.4f), styleSegments, finalTipAlpha, finalHeight * 0.58f);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    private Color resolveHatColor(PlayerEntity player) {
        if (rainbow.getValue()) {
            float offset = ((System.currentTimeMillis() - LinkenVisuals.getInstance().getInitTime()) % 6000L) / 6000f;
            offset += (player.getId() & 31) / 31f;
            return ColorUtils.getGlobalColor(offset);
        }
        return ColorUtils.getGlobalColor();
    }

    private void renderConeHat(MatrixStack matrices, float radius, float height, Color color, int segments, float tipAlphaValue, float baseAlphaValue) {
        Matrix4f pose = matrices.peek().getPositionMatrix();
        float tipAlpha = MathHelper.clamp(tipAlphaValue, 0.0f, 1.0f);
        float baseAlpha = MathHelper.clamp(baseAlphaValue, 0.0f, 1.0f);

        int baseColor = (Math.round(baseAlpha * 255) << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        int tipColor = (Math.round(tipAlpha * 255) << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        int outlineColor = (255 << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();

        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer triangles = immediate.getBuffer(ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_TRIANGLES_DEPTH);

        // 1) Base triangles (center -> (x0, 0, z0) -> (x1, 0, z1))
        for (int i = 0; i < segments; i++) {
            double a0 = (i / (double) segments) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segments) * Math.PI * 2.0;
            float x0 = (float) (Math.cos(a0) * radius);
            float z0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);

            triangles.vertex(pose, 0.0f, 0.0f, 0.0f).color(baseColor);
            triangles.vertex(pose, x0, 0.0f, z0).color(baseColor);
            triangles.vertex(pose, x1, 0.0f, z1).color(baseColor);
        }

        // 2) Side surface of the cone: (x0, 0, z0) -> (x1, 0, z1) -> (0, height, 0)
        for (int i = 0; i < segments; i++) {
            double a0 = (i / (double) segments) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segments) * Math.PI * 2.0;
            float x0 = (float) (Math.cos(a0) * radius);
            float z0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);

            triangles.vertex(pose, x0, 0.0f, z0).color(baseColor);
            triangles.vertex(pose, x1, 0.0f, z1).color(baseColor);
            triangles.vertex(pose, 0.0f, height, 0.0f).color(tipColor);
        }

        VertexConsumer lines = immediate.getBuffer(ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_LINES_DEPTH);
        for (int i = 0; i < segments; i++) {
            double a0 = (i / (double) segments) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segments) * Math.PI * 2.0;
            float x0 = (float) (Math.cos(a0) * radius);
            float z0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);
            float dx = x1 - x0;
            float dz = z1 - z0;
            float length = (float) Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0e-5f) {
                dx = 0.0f;
                dz = 1.0f;
                length = 1.0f;
            }
            float nx = dx / length;
            float nz = dz / length;
            lines.vertex(pose, x0, 0.0f, z0).color(outlineColor)
                .normal(nx, 0.0f, nz).lineWidth(2.5f);
            lines.vertex(pose, x1, 0.0f, z1).color(outlineColor)
                .normal(nx, 0.0f, nz).lineWidth(2.5f);
        }
    }

    private void renderRing(MatrixStack matrices, float innerRadius, float outerRadius, Color color, int segments, float alpha, float y) {
        Matrix4f pose = matrices.peek().getPositionMatrix();
        float ringAlpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        int argb = (Math.round(ringAlpha * 255) << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();

        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer triangles = immediate.getBuffer(ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_TRIANGLES_DEPTH);
        for (int i = 0; i < segments; i++) {
            double a0 = (i / (double) segments) * Math.PI * 2.0;
            double a1 = ((i + 1) / (double) segments) * Math.PI * 2.0;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

            float xO0 = c0 * outerRadius, zO0 = s0 * outerRadius;
            float xI0 = c0 * innerRadius, zI0 = s0 * innerRadius;
            float xO1 = c1 * outerRadius, zO1 = s1 * outerRadius;
            float xI1 = c1 * innerRadius, zI1 = s1 * innerRadius;

            triangles.vertex(pose, xO0, y, zO0).color(argb);
            triangles.vertex(pose, xO1, y, zO1).color(argb);
            triangles.vertex(pose, xI0, y, zI0).color(argb);

            triangles.vertex(pose, xO1, y, zO1).color(argb);
            triangles.vertex(pose, xI1, y, zI1).color(argb);
            triangles.vertex(pose, xI0, y, zI0).color(argb);
        }
    }

    private enum HatStyle implements Nameable {
        Classic("settings.chinahat.style.classic", 1.0f, 1.0f, 1.0f, 1.0f),
        Layered("settings.chinahat.style.layered", 1.0f, 1.0f, 1.0f, 1.0f),
        Halo("settings.chinahat.style.halo", 1.0f, 1.0f, 1.0f, 0.95f);

        private final String name;
        private final float radiusMultiplier;
        private final float heightMultiplier;
        private final float tipAlphaMultiplier;
        private final float baseAlphaMultiplier;

        HatStyle(String name, float radiusMultiplier, float heightMultiplier, float tipAlphaMultiplier, float baseAlphaMultiplier) {
            this.name = name;
            this.radiusMultiplier = radiusMultiplier;
            this.heightMultiplier = heightMultiplier;
            this.tipAlphaMultiplier = tipAlphaMultiplier;
            this.baseAlphaMultiplier = baseAlphaMultiplier;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}

