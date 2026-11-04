package ru.method.linkenvisuals.modules.impl.render;

import lombok.AllArgsConstructor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix3x2fStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.server.Server;

import java.awt.Color;
import java.util.UUID;

public class Crosshair extends Module {

    private final EnumSetting<Style> style = new EnumSetting<>("settings.crosshair.style", Style.Cross);
    private final ColorSetting color = new ColorSetting("settings.crosshair.color", Color.WHITE);
    private final ColorSetting targetColor = new ColorSetting("settings.crosshair.target_color", new Color(255, 70, 70, 255));
    private final NumberSetting size = new NumberSetting("settings.crosshair.size", 6f, 2f, 18f, 0.5f);
    private final NumberSetting gap = new NumberSetting("settings.crosshair.gap", 2f, 0f, 10f, 0.5f, () -> style.getValue() == Style.Cross);
    private final NumberSetting thickness = new NumberSetting("settings.crosshair.thickness", 1.5f, 0.5f, 5f, 0.25f);
    private final BooleanSetting dynamicSize = new BooleanSetting("settings.crosshair.dynamic_size", true);
    private final NumberSetting dynamicScale = new NumberSetting(
            "settings.crosshair.dynamic_scale",
            1.35f,
            1.05f,
            2.5f,
            0.05f,
            dynamicSize::getValue
    );
    private final NumberSetting targetRange = new NumberSetting("settings.crosshair.target_range", 6f, 3f, 12f, 0.5f);
    private final BooleanSetting hideVanilla = new BooleanSetting("settings.crosshair.hide_vanilla", true);

    private final Animation targetAnimation = new Animation(180, 1f, false, Easing.BOTH_SINE);
    private float dynamicValue = 0f;

    public Crosshair() {
        super("Crosshair", Category.Visuals);
    }

    public boolean shouldHideVanillaCrosshair() {
        return isToggled() && hideVanilla.getValue();
    }

    public void renderCrosshair(DrawContext context, RenderTickCounter tickCounter) {
        if (!isToggled() || fullNullCheck() || mc.options.hudHidden) {
            return;
        }
        // Прицел только от первого лица — в третьем лице (F5) не показываем.
        if (!mc.options.getPerspective().isFirstPerson()) {
            return;
        }

        float tickDelta = tickCounter.getTickProgress(false);
        LivingEntity target = getTargetedEnemy(tickDelta);
        targetAnimation.update(target != null);

        float centerX = mc.getWindow().getScaledWidth() / 2f;
        float centerY = mc.getWindow().getScaledHeight() / 2f;

        // Динамика ТОЛЬКО от удара: при ударе прицел раздвигается, по мере перезарядки сужается
        // (сжался → атака заряжена, можно бить критически). Наведение на врага на размер НЕ влияет.
        float dyn = 0f;
        if (dynamicSize.getValue()) {
            dyn = 1f - MathHelper.clamp(mc.player.getAttackCooldownProgress(0f), 0f, 1f);
        }
        // Плавное приближение — динамика не дёргается (раньше менялась резко, «слишком быстро»).
        dynamicValue += (dyn - dynamicValue) * 0.10f;
        float scale = 1f + dynamicValue * (dynamicScale.getValue() - 1f);
        float drawSize = size.getValue() * scale;
        float drawGap = gap.getValue() * scale;
        float drawThickness = thickness.getValue();

        Color activeColor = target != null ? targetColor.getValue() : color.getValue();
        Render2D.prepareHudFrame(context);

        switch (style.getValue()) {
            case Cross -> drawCross(context, centerX, centerY, drawSize, drawGap, drawThickness, activeColor);
            case Circle -> Render2D.drawRing(context, centerX, centerY, drawSize, drawThickness, activeColor);
            case Dot -> {
                // Гладкая точка (SDF-кружок), размер берётся от настройки size (+ динамика).
                float dotRadius = Math.max(1f, drawSize * 0.3f);
                Render2D.drawRoundedRect(context, centerX - dotRadius, centerY - dotRadius,
                        dotRadius * 2f, dotRadius * 2f, dotRadius, activeColor);
            }
        }
    }

    private void drawCross(DrawContext context, float cx, float cy, float armLength, float armGap, float lineThickness, Color color) {
        // Контур/тень для контраста на любом фоне (чёрный полупрозрачный)
        int shadowColor = 0xAA000000;
        float outline = 1.0f;

        drawCrossBar(context, cx - lineThickness / 2f - outline, cy - armGap - armLength - outline, lineThickness + outline * 2f, armLength + outline * 2f, shadowColor);
        drawCrossBar(context, cx - lineThickness / 2f - outline, cy + armGap - outline, lineThickness + outline * 2f, armLength + outline * 2f, shadowColor);
        drawCrossBar(context, cx - armGap - armLength - outline, cy - lineThickness / 2f - outline, armLength + outline * 2f, lineThickness + outline * 2f, shadowColor);
        drawCrossBar(context, cx + armGap - outline, cy - lineThickness / 2f - outline, armLength + outline * 2f, lineThickness + outline * 2f, shadowColor);

        // Основные лучи
        int mainColor = Render2D.toArgb(color);
        drawCrossBar(context, cx - lineThickness / 2f, cy - armGap - armLength, lineThickness, armLength, mainColor);
        drawCrossBar(context, cx - lineThickness / 2f, cy + armGap, lineThickness, armLength, mainColor);
        drawCrossBar(context, cx - armGap - armLength, cy - lineThickness / 2f, armLength, lineThickness, mainColor);
        drawCrossBar(context, cx + armGap, cy - lineThickness / 2f, armLength, lineThickness, mainColor);
    }

    private void drawCrossBar(DrawContext context, float x, float y, float w, float h, int argb) {
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.round(x + w);
        int y2 = Math.round(y + h);
        if (x2 <= x1) x2 = x1 + 1;
        if (y2 <= y1) y2 = y1 + 1;
        context.fill(x1, y1, x2, y2, argb);
    }

    private LivingEntity getTargetedEnemy(float tickDelta) {
        LivingEntity fromCrosshair = getEnemyFromHitResult(mc.crosshairTarget);
        if (fromCrosshair != null) {
            return fromCrosshair;
        }
        return raycastEnemy(tickDelta);
    }

    private LivingEntity getEnemyFromHitResult(HitResult hitResult) {
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        Entity entity = ((EntityHitResult) hitResult).getEntity();
        return resolveEnemy(entity);
    }

    private LivingEntity raycastEnemy(float tickDelta) {
        if (fullNullCheck()) {
            return null;
        }

        double range = targetRange.getValue();
        Vec3d start = mc.player.getCameraPosVec(tickDelta);
        Vec3d direction = mc.player.getRotationVec(tickDelta);
        Vec3d end = start.add(direction.multiply(range));
        Box searchBox = mc.player.getBoundingBox().stretch(direction.multiply(range)).expand(1.0);

        EntityHitResult hit = ProjectileUtil.raycast(
                mc.player,
                start,
                end,
                searchBox,
                this::canTargetEntity,
                range * range
        );
        if (hit == null) {
            return null;
        }
        return resolveEnemy(hit.getEntity());
    }

    private boolean canTargetEntity(Entity entity) {
        return resolveEnemy(entity) != null;
    }

    private LivingEntity resolveEnemy(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living == mc.player) {
            return null;
        }
        if (!living.isAlive() || living.isRemoved()) {
            return null;
        }
        if (living instanceof PlayerEntity player) {
            if (isFriend(player)) {
                return null;
            }
            if (isNpcPlayer(player)) {
                return null;
            }
            if (shouldIgnoreInvisiblePlayer(player)) {
                return null;
            }
        } else if (living.isInvisible() || living.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            return null;
        }
        if (mc.player.distanceTo(living) > targetRange.getValue()) {
            return null;
        }
        return living;
    }

    private boolean shouldIgnoreInvisiblePlayer(PlayerEntity player) {
        if (player == null) {
            return true;
        }
        boolean invisible = player.isInvisible() || player.hasStatusEffect(StatusEffects.INVISIBILITY);
        return invisible && !hasVisibleEquipment(player);
    }

    private boolean hasVisibleEquipment(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
            return true;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) continue;
            ItemStack stack = player.getEquippedStack(slot);
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isFriend(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getFriendManager() == null) {
            return false;
        }
        var profile = player.getGameProfile();
        if (profile == null || profile.name() == null) {
            return false;
        }
        return instance.getFriendManager().isFriend(profile.name());
    }

    private boolean isNpcPlayer(PlayerEntity player) {
        if (player == null || player == mc.player || mc.getNetworkHandler() == null) {
            return false;
        }
        if (Server.getPing(player) != 0) {
            return false;
        }
        UUID profileUuid = player.getGameProfile() != null ? player.getGameProfile().id() : null;
        return isZeroUuid(profileUuid) || isZeroUuid(player.getUuid());
    }

    private boolean isZeroUuid(UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L;
    }

    @AllArgsConstructor
    public enum Style implements Nameable {
        Cross("settings.crosshair.style.cross"),
        Circle("settings.crosshair.style.circle"),
        Dot("settings.crosshair.style.dot");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }
}
