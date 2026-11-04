package dev.linkenvisuals.render;

import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.TargetEspModule;
import dev.linkenvisuals.ui.Theme;
import dev.linkenvisuals.util.ColorMath;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.UUID;

/**
 * TargetEsp renderer (donor {@code TargetEsp}, 689 lines).
 *
 * <p>Target pick: the entity under the crosshair, valid for 1 s after the
 * crosshair leaves it (donor retention). The effect is only drawn while the
 * target is actually visible: a block raycast from the player's eyes to the
 * target centre is compared with the hit distance, and every billboard uses a
 * depth-tested pipeline. Invisible players without visible equipment are
 * skipped; invisible players holding/wearing something stay visible.
 *
 * <p>Deviation from the donor: the Original marker is a world-space billboard
 * with the donor's screen-size formula converted to world units instead of a
 * 2D pass. The on-screen size matches; depth comes from the world position, so
 * the marker is occluded by walls exactly like the ghost/circle/crystal modes.
 */
public final class TargetEspRenderer {
    private static final long VISIBILITY_CACHE_NANOS = 50_000_000L;
    private static final double TAU = Math.PI * 2.0;

    private static LivingEntity lastTarget;
    private static LivingEntity renderTarget;
    private static long lastTargetMs;
    private static float animation;
    private static long lastAnimationMs;
    private static double ghostRotationPhase;
    private static double ghostWavePhase;
    private static long lastGhostNanos;
    private static LivingEntity cachedVisibilityTarget;
    private static long cachedVisibilityNanos;
    private static boolean cachedVisibility;

    private TargetEspRenderer() {
    }

    /** Drops all state. Called on module disable and on world changes. */
    public static void reset() {
        lastTarget = null;
        renderTarget = null;
        lastTargetMs = 0L;
        animation = 0.0f;
        lastAnimationMs = 0L;
        ghostRotationPhase = 0.0;
        ghostWavePhase = 0.0;
        lastGhostNanos = 0L;
        cachedVisibilityTarget = null;
        cachedVisibilityNanos = 0L;
        cachedVisibility = false;
    }

    public static void render(WorldRenderContext context) {
        TargetEspModule module = Modules.targetEsp();
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.enabled() || client.player == null || client.world == null) {
            reset();
            return;
        }

        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrices();
        if (consumers == null || matrices == null) {
            return;
        }

        boolean showNpcs = module.showNpcs.value();
        boolean retained = updateTargetRetention(client, showNpcs);
        animation = TargetEspRules.fadeStep(animation, retained,
            nextAnimationDeltaMs(), TargetEspRules.FADE_MS);
        float eased = ColorMath.smoothstep(0.0f, 1.0f, animation);

        if (eased <= 0.001f) {
            if (!retained) {
                renderTarget = null;
            }
            if (renderTarget == null) {
                return;
            }
        }
        if (renderTarget == null) {
            return;
        }
        if (renderTarget.isRemoved() || renderTarget.getEntityWorld() != client.world) {
            reset();
            return;
        }
        if (retained && !isEntityValid(client, renderTarget, showNpcs)) {
            return;
        }

        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        if (retained && !isVisibleToPlayer(client, renderTarget, tickDelta)) {
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        // Camera.horizontalPlane is the forward vector, right = forward x up.
        Vector3fc forwardAxis = camera.getHorizontalPlane();
        Vector3fc up = camera.getVerticalPlane();
        Vector3fc right = new Vector3f(forwardAxis).cross(up);
        Vec3d targetPos = renderTarget.getLerpedPos(tickDelta);

        switch (module.mode.current()) {
            case "Ghosts" -> renderGhosts(module, consumers, matrix, right, up, camPos,
                targetPos, tickDelta, eased);
            case "Circles" -> renderCircles(module, consumers, matrix, right, up, camPos,
                targetPos, tickDelta, eased);
            case "Crystals" -> renderCrystals(client, module, consumers, matrix, right, up, camPos,
                targetPos, tickDelta, eased);
            default -> {
                lastGhostNanos = 0L;
                renderOriginal(client, module, consumers, matrix, right, up, camPos,
                    targetPos, eased);
            }
        }
    }

    private static float nextAnimationDeltaMs() {
        long now = System.currentTimeMillis();
        float delta = lastAnimationMs == 0L ? 0.0f : Math.min(100.0f, now - lastAnimationMs);
        lastAnimationMs = now;
        return delta;
    }

    // ------------------------------------------------------------------
    // Target selection
    // ------------------------------------------------------------------

    private static LivingEntity getCurrentTargetedEntity(MinecraftClient client, boolean showNpcs) {
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof LivingEntity living) || entity == client.player) {
            return null;
        }
        if (isNpcEntity(client, living) && !showNpcs) {
            return null;
        }
        if (living instanceof PlayerEntity player && shouldHidePlayer(client, player, showNpcs)) {
            return null;
        }
        if (!(living instanceof PlayerEntity)
            && (living.isInvisible() || living.hasStatusEffect(StatusEffects.INVISIBILITY))) {
            return null;
        }
        return TargetEspRules.withinRange(client.player.distanceTo(living)) ? living : null;
    }

    private static boolean updateTargetRetention(MinecraftClient client, boolean showNpcs) {
        LivingEntity current = getCurrentTargetedEntity(client, showNpcs);
        long now = System.currentTimeMillis();
        if (current != null) {
            lastTarget = current;
            renderTarget = current;
            lastTargetMs = now;
            return true;
        }
        if (lastTarget != null) {
            if ((now - lastTargetMs) < TargetEspRules.TARGET_DELAY_MS
                && isEntityValid(client, lastTarget, showNpcs)) {
                renderTarget = lastTarget;
                return true;
            }
            lastTarget = null;
        }
        return false;
    }

    private static boolean isEntityValid(MinecraftClient client, LivingEntity entity, boolean showNpcs) {
        if (entity.isRemoved() || !entity.isAlive() || client.player == null) {
            return false;
        }
        if (isNpcEntity(client, entity) && !showNpcs) {
            return false;
        }
        if (entity instanceof PlayerEntity player && shouldHidePlayer(client, player, showNpcs)) {
            return false;
        }
        if (!(entity instanceof PlayerEntity)
            && (entity.isInvisible() || entity.hasStatusEffect(StatusEffects.INVISIBILITY))) {
            return false;
        }
        return TargetEspRules.withinRange(client.player.distanceTo(entity));
    }

    private static boolean shouldHidePlayer(MinecraftClient client, PlayerEntity player, boolean showNpcs) {
        if (player == client.player) {
            return false;
        }
        if (!showNpcs && isZeroPingZeroUuidNpc(client, player)) {
            return true;
        }
        boolean invisible = player.isInvisible() || player.hasStatusEffect(StatusEffects.INVISIBILITY);
        return TargetEspRules.hidesInvisiblePlayer(invisible, hasVisibleEquipment(player));
    }

    private static boolean hasVisibleEquipment(PlayerEntity player) {
        if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
            return true;
        }
        return !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private static boolean isNpcEntity(MinecraftClient client, LivingEntity entity) {
        if (entity instanceof ArmorStandEntity) {
            return true;
        }
        return entity instanceof PlayerEntity player && isZeroPingZeroUuidNpc(client, player);
    }

    private static boolean isZeroPingZeroUuidNpc(MinecraftClient client, PlayerEntity player) {
        if (player == client.player || client.getNetworkHandler() == null) {
            return false;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
        int ping = entry == null ? 0 : entry.getLatency();
        if (ping != 0) {
            return false;
        }
        return isZeroUuid(player.getUuid());
    }

    private static boolean isZeroUuid(UUID uuid) {
        return uuid != null
            && uuid.getMostSignificantBits() == 0L
            && uuid.getLeastSignificantBits() == 0L;
    }

    private static boolean isVisibleToPlayer(MinecraftClient client, LivingEntity target, float tickDelta) {
        long now = System.nanoTime();
        if (target == cachedVisibilityTarget && now - cachedVisibilityNanos <= VISIBILITY_CACHE_NANOS) {
            return cachedVisibility;
        }
        Vec3d eye = client.player.getCameraPosVec(tickDelta);
        Vec3d center = target.getLerpedPos(tickDelta).add(0.0, target.getHeight() * 0.5, 0.0);
        BlockHitResult hit = client.world.raycast(new RaycastContext(
            eye, center,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            client.player));
        boolean visible;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            visible = true;
        } else {
            double hitDistance = hit.getPos().squaredDistanceTo(eye);
            double targetDistance = center.squaredDistanceTo(eye);
            visible = hitDistance >= targetDistance - 1.0e-3;
        }
        cachedVisibilityTarget = target;
        cachedVisibilityNanos = now;
        cachedVisibility = visible;
        return visible;
    }

    // ------------------------------------------------------------------
    // Modes
    // ------------------------------------------------------------------

    private static void renderOriginal(MinecraftClient client, TargetEspModule module,
                                       VertexConsumerProvider consumers, Matrix4f matrix,
                                       Vector3fc right, Vector3fc up, Vec3d camPos,
                                       Vec3d targetPos, float eased) {
        double dx = targetPos.x - client.player.getX();
        double dz = targetPos.z - client.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Vec3d center = targetPos.add(0.0, renderTarget.getHeight() / 2.0, 0.0);
        double camDistance = camPos.distanceTo(center);

        float fov = client.options.getFov().getValue();
        float sizeSetting = module.size.value();
        double maxSize = TargetEspRules.scaleAtDistance(camDistance, sizeSetting * eased, fov);
        float pixels = TargetEspRules.markerSizePx(maxSize, horizontal, sizeSetting);
        float worldSize = TargetEspRules.worldSizeForPixels(
            pixels, client.getWindow().getScaledHeight(), camDistance, fov);

        float hurt = renderTarget.hurtTime > 0
            ? Math.abs(MathHelper.sin(renderTarget.hurtTime * 0.5f)) : 0.0f;
        int rgb = ColorMath.mix(Theme.accent(), 0xFF0000, hurt);
        double time = System.currentTimeMillis() / 1000.0;
        float roll = (float) (Math.sin(time) * 180.0);

        VertexConsumer vc = consumers.getBuffer(TargetEspPipelines.MARKER_LAYER);
        billboard(vc, matrix, right, up, camPos, center.x, center.y, center.z,
            worldSize, roll, rgb, eased * 0.85f);
    }

    private static void renderGhosts(TargetEspModule module,
                                     VertexConsumerProvider consumers, Matrix4f matrix,
                                     Vector3fc right, Vector3fc up, Vec3d camPos,
                                     Vec3d targetPos, float tickDelta, float eased) {
        double speed = MathHelper.clamp(module.ghostSpeed.value(), 0.2f, 3.0f);
        updateGhostPhase(speed);

        float hurtFactor = MathHelper.clamp((renderTarget.hurtTime - tickDelta) / 10.0f, 0.0f, 1.0f);
        float halfHeight = renderTarget.getHeight() / 2.0f + 0.1f;
        float ghostWidth = Math.max(renderTarget.getWidth(), 0.55f);
        int base = Theme.accent();
        int gradient = ColorMath.mix(base, 0xFFFFFF, 0.35f);
        int color = ColorMath.mix(gradient, 0xFF4040, hurtFactor);

        VertexConsumer vc = consumers.getBuffer(TargetEspPipelines.GHOST_LAYER);
        int segments = 20;
        for (int layer = 0; layer < 3; layer++) {
            double layerOffset = layer / 3.0;
            double waveLayerOffset = layer * 0.22;
            for (int i = 0; i < segments; i++) {
                float offset = (i + segments) / (segments * 2.0f);
                float alphaProgress = offset * eased;
                if (alphaProgress <= 0.01f) {
                    continue;
                }
                double segmentFraction = i / (double) segments;
                double angle = ((ghostRotationPhase + segmentFraction + layerOffset) % 1.0) * TAU;
                double wavePhase = (ghostWavePhase + segmentFraction * 0.75 + waveLayerOffset) % 1.0;
                double verticalWave = Math.sin(wavePhase * TAU) * 0.45 * eased;
                double radius = ghostWidth * MathHelper.lerp(offset, 0.6f, 1.0f) * eased;

                float scale = 0.5f * offset * eased;
                if (scale <= 0.01f) {
                    continue;
                }
                float alpha = MathHelper.clamp(alphaProgress, 0.0f, 1.0f);

                billboard(vc, matrix, right, up, camPos,
                    targetPos.x + Math.cos(angle) * radius,
                    targetPos.y + halfHeight + verticalWave,
                    targetPos.z + Math.sin(angle) * radius,
                    scale, 0.0f, color, alpha);
            }
        }
    }

    private static void renderCircles(TargetEspModule module,
                                      VertexConsumerProvider consumers, Matrix4f matrix,
                                      Vector3fc right, Vector3fc up, Vec3d camPos,
                                      Vec3d targetPos, float tickDelta, float eased) {
        double speed = MathHelper.clamp(module.ghostSpeed.value(), 0.2f, 3.0f);
        updateGhostPhase(speed);

        float hurtFactor = MathHelper.clamp((renderTarget.hurtTime - tickDelta) / 10.0f, 0.0f, 1.0f);
        float radius = Math.max(renderTarget.getWidth() * 0.8f, 0.4f);
        double bob = Math.sin(ghostWavePhase * TAU) * 0.3 * eased;
        double lift = 0.25 * eased;
        double ringY = targetPos.y + renderTarget.getHeight() * 0.6 + bob + lift;

        int base = Theme.accent();
        int gradient = ColorMath.mix(base, 0xFFFFFF, 0.25f);
        int color = ColorMath.mix(gradient, 0xFF4040, hurtFactor);
        float spriteScale = Math.max(0.35f * eased, 0.04f);
        float alpha = MathHelper.clamp(eased * 240.0f / 255.0f, 0.0f, 1.0f);

        VertexConsumer vc = consumers.getBuffer(TargetEspPipelines.GHOST_LAYER);
        int clusters = 3;
        int dotsPerCluster = 6;
        double clusterSpread = 0.2;
        for (int cluster = 0; cluster < clusters; cluster++) {
            double clusterCenter = cluster / (double) clusters;
            for (int dot = 0; dot < dotsPerCluster; dot++) {
                double normalized = (dot / (double) (dotsPerCluster - 1)) - 0.5;
                double spreadOffset = normalized * clusterSpread;
                double fraction = (ghostRotationPhase + clusterCenter + spreadOffset) % 1.0;
                if (fraction < 0.0) {
                    fraction += 1.0;
                }
                double angle = fraction * TAU;
                double radialOffset = 1.0 + normalized * 0.25;

                billboard(vc, matrix, right, up, camPos,
                    targetPos.x + Math.cos(angle) * radius * radialOffset,
                    ringY,
                    targetPos.z + Math.sin(angle) * radius * radialOffset,
                    spriteScale, 0.0f, color, alpha);
            }
        }
    }

    private static void renderCrystals(MinecraftClient client, TargetEspModule module,
                                       VertexConsumerProvider consumers, Matrix4f matrix,
                                       Vector3fc right, Vector3fc up, Vec3d camPos,
                                       Vec3d targetPos, float tickDelta, float eased) {
        float easedCrystal = TargetEspRules.easeOutCubic(eased);
        if (easedCrystal <= 0.01f) {
            return;
        }
        float time = (client.player.age + tickDelta) * 6.0f;
        float entityHeight = renderTarget.getHeight();
        float halfWidth = renderTarget.getWidth() * 0.5f;
        int count = Math.max(1, Math.round(module.crystalCount.value()));
        int base = Theme.accent();
        float glowAlpha = eased * 0.3f;

        VertexConsumer glow = consumers.getBuffer(TargetEspPipelines.GLOW_LAYER);
        for (int i = 0; i < count; i++) {
            float[] p = crystalPos(i, count, time, halfWidth, entityHeight);
            float scale = 0.18f * easedCrystal;
            billboard(glow, matrix, right, up, camPos,
                targetPos.x + p[0], targetPos.y + p[1], targetPos.z + p[2],
                scale * 3.6f, 0.0f, base, glowAlpha);
        }

        // The immediate provider finalises the previous layer when another one is
        // requested, so every layer is filled in its own pass.
        VertexConsumer body = consumers.getBuffer(TargetEspPipelines.BODY_LAYER);
        for (int i = 0; i < count; i++) {
            float[] p = crystalPos(i, count, time, halfWidth, entityHeight);
            float scale = 0.18f * easedCrystal;
            crystalBody(body, matrix, camPos, targetPos, p, scale, base, eased * 0.7f);
        }
    }

    private static float[] crystalPos(int index, int count, float time, float halfWidth, float entityHeight) {
        float seed1 = (float) Math.sin(index * 1.7f + 0.3f) * 0.5f + 0.5f;
        float seed2 = (float) Math.cos(index * 2.3f + 0.7f) * 0.5f + 0.5f;
        float seed3 = (float) Math.sin(index * 3.1f + 1.1f) * 0.5f + 0.5f;
        float angle = time + index * (360.0f / count) + seed1 * 12.0f;
        float radius = halfWidth + 0.25f + seed3 * 0.15f;
        float x = radius * (float) Math.cos(Math.toRadians(angle));
        float z = radius * (float) Math.sin(Math.toRadians(angle));
        float y = seed2 * entityHeight;
        return new float[]{x, y, z, angle};
    }

    private static void crystalBody(VertexConsumer vc, Matrix4f matrix, Vec3d camPos, Vec3d targetPos,
                                    float[] p, float scale, int rgb, float alphaFactor) {
        float theta = (float) Math.toRadians(-p[3] + 90.0f);
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);
        float ox = (float) (targetPos.x + p[0] - camPos.x);
        float oy = (float) (targetPos.y + p[1] - camPos.y);
        float oz = (float) (targetPos.z + p[2] - camPos.z);

        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int a = Math.max(0, Math.min(255, (int) (180.0f * alphaFactor)));
        int rl = Math.min(255, (int) (r * 1.3f));
        int gl = Math.min(255, (int) (g * 1.3f));
        int bl = Math.min(255, (int) (b * 1.3f));
        int rd = (int) (r * 0.6f);
        int gd = (int) (g * 0.6f);
        int bd = (int) (b * 0.6f);
        float w = 0.5f;
        float h = 1.0f;

        tri(vc, matrix, ox, oy, oz, cos, sin, scale, 0, 0, h, -w, 0, 0, 0, w, 0, rl, gl, bl, a);
        tri(vc, matrix, ox, oy, oz, cos, sin, scale, 0, 0, h, 0, w, 0, w, 0, 0, rl, gl, bl, a);
        tri(vc, matrix, ox, oy, oz, cos, sin, scale, 0, 0, h, w, 0, 0, 0, -w, 0, r, g, b, a);
        tri(vc, matrix, ox, oy, oz, cos, sin, scale, 0, 0, h, 0, -w, 0, -w, 0, 0, r, g, b, a);
        tri(vc, matrix, ox, oy, oz, cos, sin, scale, 0, 0, -h, 0, w, 0, -w, 0, 0, rd, gd, bd, a);
        tri(vc, matrix, ox, oy, oz, cos, sin, scale, 0, 0, -h, w, 0, 0, 0, w, 0, rd, gd, bd, a);
        tri(vc, matrix, ox, oy, oz, cos, sin, scale, 0, 0, -h, 0, -w, 0, w, 0, 0, rd, gd, bd, a);
        tri(vc, matrix, ox, oy, oz, cos, sin, scale, 0, 0, -h, -w, 0, 0, 0, -w, 0, rd, gd, bd, a);
    }

    private static void tri(VertexConsumer vc, Matrix4f matrix, float ox, float oy, float oz,
                            float cos, float sin, float scale,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            int r, int g, int b, int a) {
        vertex(vc, matrix, ox, oy, oz, cos, sin, scale, x1, y1, z1, r, g, b, a);
        vertex(vc, matrix, ox, oy, oz, cos, sin, scale, x2, y2, z2, r, g, b, a);
        vertex(vc, matrix, ox, oy, oz, cos, sin, scale, x3, y3, z3, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, float ox, float oy, float oz,
                               float cos, float sin, float scale,
                               float x, float y, float z, int r, int g, int b, int a) {
        float rx = (x * cos + z * sin) * scale;
        float rz = (-x * sin + z * cos) * scale;
        float ry = y * scale;
        vc.vertex(matrix, ox + rx, oy + ry, oz + rz)
            .color(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
    }

    private static void updateGhostPhase(double speed) {
        long now = System.nanoTime();
        if (lastGhostNanos == 0L) {
            lastGhostNanos = now;
            return;
        }
        double deltaSeconds = (now - lastGhostNanos) / 1_000_000_000.0;
        lastGhostNanos = now;
        ghostRotationPhase = (ghostRotationPhase + deltaSeconds * speed * 0.45) % 1.0;
        ghostWavePhase = (ghostWavePhase + deltaSeconds * speed * 0.7) % 1.0;
        if (ghostRotationPhase < 0.0) {
            ghostRotationPhase += 1.0;
        }
        if (ghostWavePhase < 0.0) {
            ghostWavePhase += 1.0;
        }
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private static void billboard(VertexConsumer vc, Matrix4f matrix, Vector3fc right, Vector3fc up,
                                  Vec3d eye, double x, double y, double z, float size, float rollDeg,
                                  int rgb, float alpha) {
        if (alpha <= 0.0015f || size <= 0.001f) {
            return;
        }
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float half = size * 0.5f;
        float roll = (float) Math.toRadians(rollDeg);
        float cos = (float) Math.cos(roll);
        float sin = (float) Math.sin(roll);
        float cx = (float) (x - eye.x);
        float cy = (float) (y - eye.y);
        float cz = (float) (z - eye.z);

        corner(vc, matrix, right, up, cx, cy, cz, -half, half, cos, sin, 0.0f, 1.0f, r, g, b, alpha);
        corner(vc, matrix, right, up, cx, cy, cz, half, half, cos, sin, 1.0f, 1.0f, r, g, b, alpha);
        corner(vc, matrix, right, up, cx, cy, cz, half, -half, cos, sin, 1.0f, 0.0f, r, g, b, alpha);
        corner(vc, matrix, right, up, cx, cy, cz, -half, -half, cos, sin, 0.0f, 0.0f, r, g, b, alpha);
    }

    private static void corner(VertexConsumer vc, Matrix4f matrix, Vector3fc right, Vector3fc up,
                               float cx, float cy, float cz, float lx, float ly,
                               float cos, float sin, float u, float v,
                               float r, float g, float b, float a) {
        float rx = lx * cos - ly * sin;
        float ry = lx * sin + ly * cos;
        vc.vertex(matrix,
                cx + right.x() * rx + up.x() * ry,
                cy + right.y() * rx + up.y() * ry,
                cz + right.z() * rx + up.z() * ry)
            .texture(u, v)
            .color(r, g, b, a);
    }
}
