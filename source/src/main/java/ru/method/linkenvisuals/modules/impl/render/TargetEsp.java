package ru.method.linkenvisuals.modules.impl.render;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventRenderWorld;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.debug.TargetEspProfiler;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.Render3D;
import ru.method.linkenvisuals.utils.server.Server;
import ru.method.linkenvisuals.utils.world.WorldUtils;
import lombok.AllArgsConstructor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.RaycastContext;
import ru.method.linkenvisuals.utils.render.DepthRenderScope;

import java.awt.*;
import java.util.UUID;


public class TargetEsp extends Module {

    private static final Identifier GHOST_TEXTURE = LinkenVisuals.id("client/xuynya/ghost_bloom.png");
    private static final Identifier MARKER_TEXTURE = LinkenVisuals.id("client/xuynya/marker.png");
    private final EnumSetting<ESPMode> espMode = new EnumSetting<>("settings.targetesp.mode", ESPMode.Original);
    private final BooleanSetting showNpcs = new BooleanSetting("settings.targetesp.npc", false);
    private final NumberSetting size = new NumberSetting(I18n.translate("settings.targetesp.size"), 15f, 10f, 25f, 1f, () -> espMode.getValue() == ESPMode.Original);
    private final NumberSetting ghostSpeed = new NumberSetting(
            "settings.targetesp.ghost_speed",
            1.0f,
            0.2f,
            3.0f,
            0.05f,
            () -> {
                ESPMode mode = espMode.getValue();
                return mode == ESPMode.Ghosts || mode == ESPMode.Circles;
            }
    );

    private final NumberSetting crystalCount = new NumberSetting(
            "settings.targetesp.crystals.count", 14f, 4f, 40f, 1f, () -> espMode.getValue() == ESPMode.Crystals);

    public TargetEsp() {
        super("TargetEsp", Category.Visuals);
    }

    private final Animation animation = new Animation(300, 1f, true, Easing.BOTH_SINE);

    private LivingEntity lastTarget = null;
    private LivingEntity renderTarget = null;
    private long lastTargetTime = 0;
    private final long TARGET_DELAY = 1000;
    private final double MAX_RANGE = 30.0;
    private static final long VISIBILITY_CACHE_NANOS = 50_000_000L;

    // Тела кристаллов (режим Crystals) рисуются ПОСЛЕ FogBlur (в onRenderWorld) — иначе мылятся блюром.
    private boolean crystalsPending = false;

    private double ghostRotationPhase = 0.0;
    private double ghostWavePhase = 0.0;
    private long lastGhostUpdateTime = 0L;
    private LivingEntity cachedVisibilityTarget = null;
    private long cachedVisibilityTime = 0L;
    private boolean cachedVisibilityResult = false;

    private Color resolveBaseColor() {
        Color color = ColorUtils.getGlobalColor();
        if (color == null) return Color.WHITE;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private LivingEntity getCurrentTargetedEntity() {
        if (fullNullCheck()) return null;
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult) hitResult;
            Entity entity = entityHitResult.getEntity();
            if (entity instanceof LivingEntity && entity != mc.player) {
                if (isNpcEntity((LivingEntity) entity) && !showNpcs.getValue()) return null;
                if (entity instanceof PlayerEntity && shouldHidePlayer((PlayerEntity) entity)) return null;
                if (entity instanceof PlayerEntity && isFriendPlayer((PlayerEntity) entity)) return null;
                if (!(entity instanceof PlayerEntity) && entity.isInvisible()) return null;
                if (!(entity instanceof PlayerEntity) && entity instanceof LivingEntity living && living.hasStatusEffect(StatusEffects.INVISIBILITY)) return null;
                double distance = mc.player.distanceTo(entity);
                if (distance <= MAX_RANGE) return (LivingEntity) entity;
            }
        }
        return null;
    }

    private boolean isEntityValid(LivingEntity e) {
        if (e == null) return false;
        if (e.isRemoved() || !e.isAlive()) return false;
        if (isNpcEntity(e) && !showNpcs.getValue()) return false;
        if (mc.player == null) return false;
        if (e instanceof PlayerEntity && shouldHidePlayer((PlayerEntity) e)) return false;
        if (!(e instanceof PlayerEntity) && e.isInvisible()) return false;
        if (!(e instanceof PlayerEntity) && e.hasStatusEffect(StatusEffects.INVISIBILITY)) return false;
        if (e instanceof PlayerEntity && isFriendPlayer((PlayerEntity) e)) return false;
        return mc.player.distanceTo(e) <= MAX_RANGE;
    }

    private boolean shouldHidePlayer(PlayerEntity player) {
        if (player == null) return true;
        if (player == mc.player) return false;
        if (!showNpcs.getValue() && isZeroPingZeroUuidNpc(player)) return true;
        boolean invisible = player.isInvisible() || player.hasStatusEffect(StatusEffects.INVISIBILITY);
        return invisible && !hasVisibleEquipment(player);
    }

    private boolean hasVisibleEquipment(PlayerEntity player) {
        if (player == null) return false;
        if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
            return true;
        }
        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) continue;
            ItemStack stack = player.getEquippedStack(slot);
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isNpcEntity(LivingEntity entity) {
        if (entity == null) return false;
        if (entity instanceof ArmorStandEntity) return true;
        if (entity instanceof PlayerEntity player) {
            return isZeroPingZeroUuidNpc(player);
        }
        return false;
    }

    private boolean isZeroPingZeroUuidNpc(PlayerEntity player) {
        if (player == null || player == mc.player || mc.getNetworkHandler() == null) return false;
        if (Server.getPing(player) != 0) return false;
        UUID profileUuid = player.getGameProfile() != null ? player.getGameProfile().id() : null;
        return isZeroUuid(profileUuid) || isZeroUuid(player.getUuid());
    }

    private boolean isZeroUuid(UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L;
    }

    private boolean isFriendPlayer(PlayerEntity player) {
        if (player == null) return false;
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getFriendManager() == null) return false;
        var profile = player.getGameProfile();
        if (profile == null || profile.name() == null) return false;
        return instance.getFriendManager().isFriend(profile.name());
    }

    private boolean isVisibleToPlayer(LivingEntity target, float tickDelta) {
        if (fullNullCheck() || target == null) return false;
        long now = System.nanoTime();
        if (target == cachedVisibilityTarget && now - cachedVisibilityTime <= VISIBILITY_CACHE_NANOS) {
            return cachedVisibilityResult;
        }
        Vec3d playerEye = mc.player.getCameraPosVec(tickDelta);
        Vec3d targetCenter = target.getLerpedPos(tickDelta).add(0, target.getHeight() * 0.5, 0);
        RaycastContext context = new RaycastContext(
                playerEye,
                targetCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        BlockHitResult bhr = mc.world.raycast(context);
        boolean result;
        if (bhr == null) {
            result = true;
        } else if (bhr.getType() == HitResult.Type.MISS) {
            result = true;
        } else if (bhr.getType() == HitResult.Type.BLOCK) {
            double hitDist = bhr.getPos().squaredDistanceTo(playerEye);
            double tgtDist = targetCenter.squaredDistanceTo(playerEye);
            result = hitDist >= tgtDist - 1e-3;
        } else {
            result = true;
        }
        cachedVisibilityTarget = target;
        cachedVisibilityTime = now;
        cachedVisibilityResult = result;
        return result;
    }

    private boolean updateTargetRetention() {
        LivingEntity currentTarget = getCurrentTargetedEntity();
        long now = System.currentTimeMillis();

        if (currentTarget != null) {
            lastTarget = currentTarget;
            renderTarget = currentTarget;
            lastTargetTime = now;
            return true;
        }

        if (lastTarget != null) {
            if ((now - lastTargetTime) < TARGET_DELAY && isEntityValid(lastTarget)) {
                renderTarget = lastTarget;
                return true;
            } else {
                lastTarget = null;
            }
        }

        return false;
    }

    private void resetGhostAnimationState() {
        ghostRotationPhase = 0.0;
        ghostWavePhase = 0.0;
        lastGhostUpdateTime = 0L;
        cachedVisibilityTarget = null;
        cachedVisibilityTime = 0L;
        cachedVisibilityResult = false;
    }

    private void updateGhostAnimationState(double speed) {
        long now = System.nanoTime();
        if (lastGhostUpdateTime == 0L) {
            lastGhostUpdateTime = now;
            return;
        }

        double deltaSeconds = (now - lastGhostUpdateTime) / 1_000_000_000.0;
        lastGhostUpdateTime = now;

        ghostRotationPhase = (ghostRotationPhase + deltaSeconds * speed * 0.45) % 1.0;
        ghostWavePhase = (ghostWavePhase + deltaSeconds * speed * 0.7) % 1.0;

        if (ghostRotationPhase < 0.0) ghostRotationPhase += 1.0;
        if (ghostWavePhase < 0.0) ghostWavePhase += 1.0;
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck()) return;
        if (espMode.getValue() != ESPMode.Original) {
            return;
        }
        TargetEspProfiler.beginFrame();
        try {
            long retentionStart = TargetEspProfiler.startSection();
            boolean retained = updateTargetRetention();
            TargetEspProfiler.endSection("targetRetention", retentionStart);
            animation.update(retained);
            if (animation.getValue() <= 0) return;
            if (!retained && renderTarget == null) return;
            if (retained && renderTarget != null && !isEntityValid(renderTarget)) return;
            if (retained && renderTarget != null) {
                float tickDelta = e.getTickCounter().getTickProgress(true);
                long visibilityStart = TargetEspProfiler.startSection();
                boolean visible = isVisibleToPlayer(renderTarget, tickDelta);
                TargetEspProfiler.endSection("visibilityCheck", visibilityStart);
                if (!visible) return;
            }
            long renderStart = TargetEspProfiler.startSection();
            renderOriginalESP(e);
            TargetEspProfiler.endSection("renderOriginal", renderStart);
        } finally {
            TargetEspProfiler.endFrame();
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game e) {
        if (fullNullCheck()) {
            resetGhostAnimationState();
            return;
        }
        if (espMode.getValue() == ESPMode.Original) {
            resetGhostAnimationState();
            return;
        }
        TargetEspProfiler.beginFrame();
        try {
            long retentionStart = TargetEspProfiler.startSection();
            boolean retained = updateTargetRetention();
            TargetEspProfiler.endSection("targetRetention", retentionStart);
            animation.update(retained);

            if (animation.getValue() <= 0) {
                resetGhostAnimationState();
                return;
            }
            if (!retained && renderTarget == null) {
                resetGhostAnimationState();
                return;
            }
            if (retained && renderTarget != null && !isEntityValid(renderTarget)) {
                resetGhostAnimationState();
                return;
            }
            if (retained && renderTarget != null) {
                float tickDelta = e.getTickCounter().getTickProgress(true);
                long visibilityStart = TargetEspProfiler.startSection();
                boolean visible = isVisibleToPlayer(renderTarget, tickDelta);
                TargetEspProfiler.endSection("visibilityCheck", visibilityStart);
                if (!visible) {
                    resetGhostAnimationState();
                    return;
                }
            }

            switch (espMode.getValue()) {
                case Ghosts -> {
                    long renderStart = TargetEspProfiler.startSection();
                    renderGhostsESP(e);
                    TargetEspProfiler.endSection("renderGhosts", renderStart);
                }
                case Circles -> {
                    long renderStart = TargetEspProfiler.startSection();
                    renderCirclesESP(e);
                    TargetEspProfiler.endSection("renderCircles", renderStart);
                }
                case Crystals -> {
                    long renderStart = TargetEspProfiler.startSection();
                    renderCrystalsESP(e);
                    TargetEspProfiler.endSection("renderCrystals", renderStart);
                }
                default -> resetGhostAnimationState();
            }
        } finally {
            TargetEspProfiler.endFrame();
        }
    }

    private void renderOriginalESP(EventRender2D e) {
        float tickDelta = e.getTickCounter().getTickProgress(true);
        Vec3d smoothPos = renderTarget.getLerpedPos(tickDelta);
        double deltaX = smoothPos.x - mc.player.getX();
        double deltaZ = smoothPos.z - mc.player.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float maxSize = (float) WorldUtils.getScale(smoothPos, size.getValue() * animation.getValue());
        float finalSize = Math.max(maxSize - (float) distance * 0.1f, size.getValue() * 0.3f);
        Vec3d renderPos = WorldUtils.getPosition(new Vec3d(smoothPos.x, smoothPos.y + renderTarget.getHeight() / 2f, smoothPos.z));
        if (!(renderPos.z > 0) || !(renderPos.z < 1)) return;
        double time = System.currentTimeMillis() / 1000.0;
        float rotation = (float) (Math.sin(time) * 180);
        Color sourceColor = resolveBaseColor();
        int globalAlpha = (int) (255 * animation.getValue() * 0.85f);
        Color baseColor = new Color(sourceColor.getRed(), sourceColor.getGreen(), sourceColor.getBlue(), globalAlpha);
        Color hurtColor = new Color(255, 0, 0, globalAlpha);
        float hurtAlpha = 0f;
        if (renderTarget.hurtTime > 0) {
            hurtAlpha = (float) Math.abs(Math.sin(renderTarget.hurtTime * 0.5));
        }
        Color finalColor = ColorUtils.fade(baseColor, hurtColor, hurtAlpha);
        try (DepthRenderScope ignored = DepthRenderScope.lequal()) {
            e.getContext().getMatrices().pushMatrix();
            e.getContext().getMatrices().translate((float) renderPos.getX(), (float) renderPos.getY());
            e.getContext().getMatrices().rotate((float) Math.toRadians(rotation));
            // A tiny radius routes the marker through the bilinear-sampled
            // textured pipeline. The marker atlas is 1000 px and is usually
            // minified to 15-30 px, so GUI_TEXTURED's nearest minification made
            // the ring look stair-stepped.
            Render2D.drawTexture(e.getContext().getMatrices(), -finalSize / 2f, -finalSize / 2f, finalSize, finalSize, 0.01f,
                    MARKER_TEXTURE,
                    finalColor
            );
            e.getContext().getMatrices().popMatrix();
        }
    }

    private void renderGhostsESP(EventRender3D.Game e) {
        if (renderTarget == null || mc.player == null) {
            resetGhostAnimationState();
            return;
        }

        float tickDelta = e.getTickCounter().getTickProgress(true);
        Vec3d targetPos = renderTarget.getLerpedPos(tickDelta);
        boolean canSee = isVisibleToPlayer(renderTarget, tickDelta);
        float animationValue = MathHelper.clamp((float) animation.getValue(), 0.0f, 1.0f);
        if (animationValue <= 0.0f) {
            resetGhostAnimationState();
            return;
        }

        float hurtFactor = MathHelper.clamp((renderTarget.hurtTime - tickDelta) / 10.0f, 0.0f, 1.0f);
        float halfHeight = renderTarget.getHeight() / 2f + 0.1f;
        float ghostWidth = Math.max(renderTarget.getWidth(), 0.55f);
        double speed = MathHelper.clamp(ghostSpeed.getValue(), 0.2f, 3.0f);
        updateGhostAnimationState(speed);

        double rotationBase = ghostRotationPhase;
        double waveBase = ghostWavePhase;
        int segments = 20;
        int renderedSprites = 0;

        Color baseSource = resolveBaseColor();
        Color hurtColor = new Color(255, 64, 64);

        for (int layer = 0; layer < 3; layer++) {
            double layerOffset = layer / 3.0;
            double waveLayerOffset = layer * 0.22;
            for (int i = 0; i < segments; i++) {
                float offset = (float) (i + segments) / (segments * 2.0f);
                float alphaProgress = offset * animationValue;
                if (alphaProgress <= 0.01f) continue;

                double segmentFraction = i / (double) segments;
                double angleRadians = (rotationBase + segmentFraction + layerOffset) % 1.0;
                angleRadians *= MathHelper.TAU;

                double wavePhase = (waveBase + segmentFraction * 0.75 + waveLayerOffset) % 1.0;
                double verticalWave = Math.sin(wavePhase * MathHelper.TAU) * 0.45 * animationValue;

                double radius = ghostWidth * MathHelper.lerp(offset, 0.6f, 1.0f) * animationValue;
                Vec3d swirlPos = new Vec3d(
                        targetPos.x + Math.cos(angleRadians) * radius,
                        targetPos.y + halfHeight + verticalWave,
                        targetPos.z + Math.sin(angleRadians) * radius
                );

                float scale = 0.5f * offset * animationValue;
                if (scale <= 0.01f) continue;

                int alpha = MathHelper.clamp(Math.round(alphaProgress * 255.0f), 0, 255);
                Color gradientColor = ColorUtils.fade(
                        new Color(baseSource.getRed(), baseSource.getGreen(), baseSource.getBlue(), alpha),
                        new Color(255, 255, 255, alpha),
                        0.35f
                );
                Color finalColor = ColorUtils.fade(
                        gradientColor,
                        new Color(hurtColor.getRed(), hurtColor.getGreen(), hurtColor.getBlue(), alpha),
                        hurtFactor
                );

                Render3D.renderBillboardTexture(swirlPos, Math.max(scale, 0.02f), GHOST_TEXTURE, finalColor.getRGB(), canSee, true);
                renderedSprites++;
            }
        }
        TargetEspProfiler.incrementCounter("ghostSprites", renderedSprites);
    }

    private void renderCirclesESP(EventRender3D.Game e) {
        if (renderTarget == null || mc.player == null) {
            resetGhostAnimationState();
            return;
        }

        float tickDelta = e.getTickCounter().getTickProgress(true);
        Vec3d targetPos = renderTarget.getLerpedPos(tickDelta);
        boolean canSee = isVisibleToPlayer(renderTarget, tickDelta);
        float animationValue = MathHelper.clamp((float) animation.getValue(), 0.0f, 1.0f);
        if (animationValue <= 0.0f) {
            resetGhostAnimationState();
            return;
        }

        double speed = MathHelper.clamp(ghostSpeed.getValue(), 0.2f, 3.0f);
        updateGhostAnimationState(speed);

        float hurtFactor = MathHelper.clamp((renderTarget.hurtTime - tickDelta) / 10.0f, 0.0f, 1.0f);
        float radius = Math.max(renderTarget.getWidth() * 0.8f, 0.4f);
        double rotationBase = ghostRotationPhase;
        double waveBase = ghostWavePhase;
        double bob = Math.sin(waveBase * MathHelper.TAU) * 0.3 * animationValue;
        double lift = 0.25 * animationValue;
        double ringY = targetPos.y + (renderTarget.getHeight() * 0.6f) + bob + lift;

        Color baseSource = resolveBaseColor();
        Color hurtColor = new Color(255, 64, 64);

        int clusters = 3;
        int dotsPerCluster = 6;
        double clusterSpread = 0.2;
        float spriteScale = Math.max(0.35f * animationValue, 0.04f);
        int alpha = MathHelper.clamp(Math.round(animationValue * 240.0f), 0, 255);
        int renderedSprites = 0;

        for (int cluster = 0; cluster < clusters; cluster++) {
            double clusterCenter = cluster / (double) clusters;
            for (int dot = 0; dot < dotsPerCluster; dot++) {
                double normalized = dotsPerCluster == 1 ? 0.0 : (dot / (double) (dotsPerCluster - 1)) - 0.5;
                double spreadOffset = normalized * clusterSpread;
                double fraction = (rotationBase + clusterCenter + spreadOffset) % 1.0;
                if (fraction < 0.0) fraction += 1.0;
                double angle = fraction * MathHelper.TAU;
                double radialOffset = 1.0 + normalized * 0.25;
                Vec3d circlePos = new Vec3d(
                        targetPos.x + Math.cos(angle) * radius * radialOffset,
                        ringY,
                        targetPos.z + Math.sin(angle) * radius * radialOffset
                );

                Color gradientColor = ColorUtils.fade(
                        new Color(baseSource.getRed(), baseSource.getGreen(), baseSource.getBlue(), alpha),
                        new Color(255, 255, 255, alpha),
                        0.25f
                );
                Color finalColor = ColorUtils.fade(
                        gradientColor,
                        new Color(hurtColor.getRed(), hurtColor.getGreen(), hurtColor.getBlue(), alpha),
                        hurtFactor
                );

                Render3D.renderBillboardTexture(circlePos, spriteScale, GHOST_TEXTURE, finalColor.getRGB(), canSee, true);
                renderedSprites++;
            }
        }
        TargetEspProfiler.incrementCounter("circleSprites", renderedSprites);
    }

    // ===== Кристаллы вокруг цели (режим Crystals) =====

    private void renderCrystalsESP(EventRender3D.Game e) {
        if (renderTarget == null || mc.player == null) return;
        float tickDelta = e.getTickCounter().getTickProgress(true);
        float anim = (float) animation.getValue();
        if (anim <= 0.01f) return;
        float eased = crystalEaseOutCubic(anim);

        Vec3d targetPos = renderTarget.getLerpedPos(tickDelta);
        float time = (mc.player.age + tickDelta) * 6.0f;
        float entityHeight = renderTarget.getHeight();
        float halfWidth = renderTarget.getWidth() * 0.5f;
        int count = Math.max(1, Math.round(crystalCount.getValue()));
        int themeColor = crystalThemeColor();

        // Свечение — через буфер Render3D (он рисуется ПОСЛЕ FogBlur, поэтому не мылится).
        for (int i = 0; i < count; i++) {
            float[] p = crystalPos(i, count, time, halfWidth, entityHeight);
            float scale = 0.18f * eased;
            int glow = crystalMultAlpha(themeColor, anim * 0.3f);
            Render3D.renderBillboardTexture(targetPos.add(p[0], p[1], p[2]), scale * 3.6f, GHOST_TEXTURE, glow, true, true);
        }
        // Тела кристаллов рисуем немедленно, но ПОСЛЕ прохода FogBlur — в onRenderWorld (иначе блюрятся).
        crystalsPending = true;
    }

    // Тела кристаллов рисуем здесь — EventRenderWorld постится ПОСЛЕ FogBlur, поэтому они не мылятся.
    @EventHandler
    public void onRenderWorld(EventRenderWorld e) {
        if (!crystalsPending) return;
        crystalsPending = false;
        if (fullNullCheck() || renderTarget == null || espMode.getValue() != ESPMode.Crystals) return;
        renderCrystalBodies(e.getMatrixStack(), e.getTickCounter().getTickProgress(true));
    }

    private void renderCrystalBodies(MatrixStack matrices, float tickDelta) {
        float anim = (float) animation.getValue();
        if (anim <= 0.01f) return;
        float eased = crystalEaseOutCubic(anim);

        net.minecraft.client.render.Camera camera = mc.gameRenderer.getCamera();
        Vec3d targetPos = renderTarget.getLerpedPos(tickDelta);
        Vec3d camPos = camera.getCameraPos();
        Vec3d renderPos = targetPos.subtract(camPos);

        float time = (mc.player.age + tickDelta) * 6.0f;
        float entityHeight = renderTarget.getHeight();
        float halfWidth = renderTarget.getWidth() * 0.5f;
        int count = Math.max(1, Math.round(crystalCount.getValue()));
        int themeColor = crystalThemeColor();

        matrices.push();
        matrices.translate(renderPos.x, renderPos.y, renderPos.z);

        // 1.21.11: ручной immediate-mode удалён — рисуем через общий батч сущностей
        // и debug-слой (POSITION_COLOR, без текстуры). Каждый треугольник разворачиваем
        // в quad вырожденной четвёртой вершиной, чтобы уложиться в QUADS-пайплайн.
        net.minecraft.client.render.VertexConsumerProvider.Immediate immediate =
                mc.getBufferBuilders().getEntityVertexConsumers();
        net.minecraft.client.render.RenderLayer layer = ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_TRIANGLES_DEPTH;
        net.minecraft.client.render.VertexConsumer buffer = immediate.getBuffer(layer);
        for (int i = 0; i < count; i++) {
            float[] p = crystalPos(i, count, time, halfWidth, entityHeight);
            float scale = 0.18f * eased;
            crystalBody(buffer, matrices, p[0], p[1], p[2], scale, p[3], themeColor, anim * 0.7f);
        }
        immediate.draw(layer);

        matrices.pop();
    }

    private float[] crystalPos(int i, int count, float time, float halfWidth, float entityHeight) {
        float seed1 = (float) Math.sin(i * 1.7f + 0.3f) * 0.5f + 0.5f;
        float seed2 = (float) Math.cos(i * 2.3f + 0.7f) * 0.5f + 0.5f;
        float seed3 = (float) Math.sin(i * 3.1f + 1.1f) * 0.5f + 0.5f;
        float angle = time + i * (360f / count) + seed1 * 12f;
        float radius = halfWidth + 0.25f + seed3 * 0.15f;
        float x = radius * (float) Math.cos(Math.toRadians(angle));
        float z = radius * (float) Math.sin(Math.toRadians(angle));
        float y = seed2 * entityHeight;
        return new float[]{x, y, z, angle};
    }

    private void crystalBody(net.minecraft.client.render.VertexConsumer buffer, MatrixStack matrices,
                             float x, float y, float z, float scale, float yaw, int color, float alpha) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-yaw + 90f));
        matrices.scale(scale, scale, scale);
        MatrixStack.Entry entry = matrices.peek();
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int a = (int) (180 * alpha);
        int rL = Math.min(255, (int) (r * 1.3f)), gL = Math.min(255, (int) (g * 1.3f)), bL = Math.min(255, (int) (b * 1.3f));
        int rD = (int) (r * 0.6f), gD = (int) (g * 0.6f), bD = (int) (b * 0.6f);
        float w = 0.5f, h = 1.0f;
        crystalTri(buffer, entry, 0, 0, h, -w, 0, 0, 0, w, 0, rL, gL, bL, a);
        crystalTri(buffer, entry, 0, 0, h, 0, w, 0, w, 0, 0, rL, gL, bL, a);
        crystalTri(buffer, entry, 0, 0, h, w, 0, 0, 0, -w, 0, r, g, b, a);
        crystalTri(buffer, entry, 0, 0, h, 0, -w, 0, -w, 0, 0, r, g, b, a);
        crystalTri(buffer, entry, 0, 0, -h, 0, w, 0, -w, 0, 0, rD, gD, bD, a);
        crystalTri(buffer, entry, 0, 0, -h, w, 0, 0, 0, w, 0, rD, gD, bD, a);
        crystalTri(buffer, entry, 0, 0, -h, 0, -w, 0, w, 0, 0, rD, gD, bD, a);
        crystalTri(buffer, entry, 0, 0, -h, -w, 0, 0, 0, -w, 0, rD, gD, bD, a);
        matrices.pop();
    }

    private void crystalTri(net.minecraft.client.render.VertexConsumer buffer, MatrixStack.Entry entry,
                            float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                            int r, int g, int b, int a) {
        buffer.vertex(entry, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(entry, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(entry, x3, y3, z3).color(r, g, b, a);
    }

    private static float crystalEaseOutCubic(float t) {
        float c = 1f - t;
        return 1f - c * c * c;
    }

    /** Цвет кристаллов = цвет темы клиента (синхронизирован), без радужного сдвига. */
    private int crystalThemeColor() {
        Color base = resolveBaseColor();
        return base.getRGB();
    }

    private static int crystalMultAlpha(int rgb, float factor) {
        int a = Math.max(0, Math.min(255, (int) (255 * factor)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    @AllArgsConstructor
    public enum ESPMode implements Nameable {
        Original("settings.targetesp.mode.original"),
        Ghosts("settings.targetesp.mode.ghosts"),
        Circles("settings.targetesp.mode.circles"),
        Crystals("settings.targetesp.mode.crystals");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }
}
