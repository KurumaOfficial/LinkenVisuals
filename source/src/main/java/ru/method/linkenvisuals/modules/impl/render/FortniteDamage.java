package ru.method.linkenvisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Всплывающие цифры урона в стиле Fortnite (порт floatdlc.tech → ru.method.linkenvisuals).
 * При <b>критическом</b> ударе вокруг цифры добавляется жёлтый аутлайн.
 */
public class FortniteDamage extends Module {

    public enum Style implements Nameable {
        FORTNITE("Фортнайт");
        private final String display;
        Style(String display) { this.display = display; }
        @Override public String getName() { return display; }
    }

    private final EnumSetting<Style> style = new EnumSetting<>("Стиль", Style.FORTNITE);
    private final NumberSetting size = new NumberSetting("Размер", 0.032f, 0.012f, 0.08f, 0.001f);
    private final NumberSetting lifetime = new NumberSetting("Время показа", 850f, 300f, 1800f, 50f);
    private final NumberSetting rise = new NumberSetting("Подъём", 0.42f, 0.0f, 1.5f, 0.05f);
    private final BooleanSetting themeColor = new BooleanSetting("Цвет темы", false);
    // Свой цвет цифр из колорпикера (виден, когда «Цвет темы» выключен). При крите перебивается жёлтым.
    private final ColorSetting customColor = new ColorSetting("Цвет", new Color(255, 255, 255), () -> !themeColor.getValue());
    private final BooleanSetting critColor = new BooleanSetting("Жёлтая цифра при крите", true);

    private final List<PendingHit> pendingHits = new ArrayList<>();
    private final List<DamageNumber> numbers = new ArrayList<>();

    public FortniteDamage() {
        super("FortniteDamage", Category.Visuals);
        getSettings().add(style);
        getSettings().add(size);
        getSettings().add(lifetime);
        getSettings().add(rise);
        getSettings().add(themeColor);
        getSettings().add(customColor);
        getSettings().add(critColor);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        synchronized (pendingHits) {
            pendingHits.clear();
        }
        synchronized (numbers) {
            numbers.clear();
        }
    }

    @EventHandler
    public void onAttack(EventAttackEntity e) {
        if (mc.player == null || mc.world == null) return;
        Entity entity = e.getTarget();
        if (!(entity instanceof LivingEntity target) || target == mc.player) return;

        synchronized (pendingHits) {
            pendingHits.add(new PendingHit(
                    target,
                    getHealthValue(target),
                    resolveHitPosition(mc.player, target),
                    isCriticalHit(mc.player, target),
                    System.currentTimeMillis()
            ));
            pendingHits.removeIf(PendingHit::isExpired);
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        List<DamageNumber> spawned = new ArrayList<>();
        java.util.HashSet<Integer> deathHandled = new java.util.HashSet<>();
        synchronized (pendingHits) {
            pendingHits.removeIf(hit -> {
                if (hit.isExpired()) return true;
                if (hit.target.isRemoved() || !hit.target.isAlive()) {
                    // Урон убийства: реальную разницу HP не взять, если сервер не синкает HP чужих игроков
                    // (клиент видит полное HP=20) — раньше на килл выводилось «20». Теперь показываем ОЦЕНКУ
                    // урона игрока (атрибут атаки + крит) — «обычный последний нанесённый». Если HP синкается,
                    // берём МЕНЬШЕЕ из healthBefore и оценки (реальный оверкилл). Один номер на цель.
                    if (!deathHandled.add(hit.target.getId())) return true;
                    float est = estimateAttackDamage(hit.crit);
                    float damage = Math.max(0.0f, hit.healthBefore);
                    if (est > 0.05f) damage = Math.min(damage, est);
                    if (damage > 0.05f) spawned.add(createNumber(hit, damage));
                    return true;
                }

                float damage = hit.healthBefore - getHealthValue(hit.target);
                if (damage > 0.05f) {
                    spawned.add(createNumber(hit, damage));
                    return true;
                }
                return false;
            });
        }

        synchronized (numbers) {
            if (!spawned.isEmpty()) numbers.addAll(spawned);
            numbers.removeIf(DamageNumber::isDead);
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (mc.player == null || mc.world == null) return;

        List<DamageNumber> renderList;
        synchronized (numbers) {
            if (numbers.isEmpty()) return;
            renderList = new ArrayList<>(numbers);
        }


        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        MatrixStack matrices = event.getMatrixStack();
        float fontSize = 10.8f; // на 40% меньше прежних 18.0f

        for (DamageNumber number : renderList) {
            float alpha = number.getAlpha();
            if (alpha <= 0.01f) continue;

            Vec3d pos = number.getAnimatedPosition();
            matrices.push();
            matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            float scale = size.getValue();
            if (style.getValue() == Style.FORTNITE) scale *= number.getScale();
            matrices.scale(-scale, -scale, scale);

            String text = number.text;
            boolean crit = number.crit && critColor.getValue();
            Color textColor = getTextColor(crit, alpha);
            int outlineAlpha = Math.round(alpha * 255f);
            int outlineColor = (outlineAlpha << 24);
            float textWidth = mc.textRenderer.getWidth(text);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
            mc.textRenderer.drawWithOutline(
                    net.minecraft.text.Text.literal(text).asOrderedText(),
                    -textWidth / 2f,
                    -mc.textRenderer.fontHeight / 2f,
                    textColor.getRGB(),
                    outlineColor,
                    matrix,
                    immediate,
                    net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE
            );

            matrices.pop();
        }

        mc.getBufferBuilders().getEntityVertexConsumers().draw();
    }

    private DamageNumber createNumber(PendingHit hit, float damage) {
        return new DamageNumber(
                hit.position,
                Math.round(damage),
                damage,
                hit.crit,
                System.currentTimeMillis(),
                (long) lifetime.getValue().floatValue(),
                rise.getValue()
        );
    }

    private float getHealthValue(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    /** Оценка урона игрока по атрибуту атаки (+1.5× при крите) — «обычный последний нанесённый»,
     *  когда реальную разницу HP не взять (HP цели не синкается сервером). */
    private float estimateAttackDamage(boolean crit) {
        if (mc.player == null) return 0f;
        double dmg = mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
        if (crit) dmg *= 1.5;
        return (float) dmg;
    }

    /**
     * Крит — атака в прыжке/падении (читается в момент атаки, до её обработки).
     * Ванильные гейты (в воздухе, не на лестнице/в воде/в транспорте, не спринт) сохранены,
     * но «падение» определяется как fallDistance>0 ЛИБО скорость вниз — иначе в креативе при
     * полёте fallDistance всегда 0 и крит был бы невозможен. Жёсткое требование полного кулдауна
     * убрано, чтобы цвет надёжно срабатывал на прыжковом ударе.
     */
    private boolean isCriticalHit(PlayerEntity p, Entity target) {
        if (!(target instanceof LivingEntity)) return false;
        if (p.isOnGround() || p.isClimbing() || p.isTouchingWater() || p.hasVehicle() || p.isSprinting()) return false;
        if (p.hasStatusEffect(StatusEffects.BLINDNESS)) return false;
        return p.fallDistance > 0.0f || p.getVelocity().y < -0.06;
    }

    private Color getTextColor(boolean crit, float alpha) {
        int a = Math.round(alpha * 255f);
        if (crit) {
            return new Color(0xFC, 0xFE, 0x3C, a); // #FCFE3C — жёлтая цифра при крите
        }
        if (themeColor.getValue()) {
            Color primary = ColorUtils.getPrimaryColor();
            return new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), a);
        }
        Color c = customColor.getValue();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private Vec3d resolveHitPosition(Entity attacker, Entity target) {
        Vec3d fallback = new Vec3d(
                target.getX(),
                target.getY() + target.getHeight() / 2.0,
                target.getZ()
        );
        if (attacker == null) return fallback;

        Vec3d eyePos = attacker.getCameraPosVec(1.0F);
        Vec3d lookVec = attacker.getRotationVec(1.0F);
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        double distance = Math.max(eyePos.distanceTo(targetCenter) + 1.0, 6.0);
        Vec3d reachPos = eyePos.add(lookVec.multiply(distance));

        Optional<Vec3d> hitPos = target.getBoundingBox().raycast(eyePos, reachPos);
        return hitPos.orElseGet(() -> eyePos.add(lookVec.multiply(eyePos.distanceTo(targetCenter))));
    }

    private static float easeOutCubic(float x) {
        return 1.0f - (float) Math.pow(1.0f - x, 3);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(x - 1.0f, 3) + c1 * (float) Math.pow(x - 1.0f, 2);
    }

    private static final class PendingHit {
        private static final long MAX_WAIT_MS = 750L;

        private final LivingEntity target;
        private final float healthBefore;
        private final Vec3d position;
        private final boolean crit;
        private final long birthTime;

        private PendingHit(LivingEntity target, float healthBefore, Vec3d position, boolean crit, long birthTime) {
            this.target = target;
            this.healthBefore = healthBefore;
            this.position = position;
            this.crit = crit;
            this.birthTime = birthTime;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - birthTime > MAX_WAIT_MS;
        }
    }

    private static final class DamageNumber {
        private final Vec3d position;
        private final String text;
        private final float damage;
        private final boolean crit;
        private final long birthTime;
        private final long lifetimeMs;
        private final float riseAmount;

        private DamageNumber(Vec3d position, int displayDamage, float damage, boolean crit, long birthTime, long lifetimeMs, float riseAmount) {
            this.position = position;
            this.text = String.valueOf(Math.max(1, displayDamage));
            this.damage = damage;
            this.crit = crit;
            this.birthTime = birthTime;
            this.lifetimeMs = lifetimeMs;
            this.riseAmount = riseAmount;
        }

        private boolean isDead() {
            return System.currentTimeMillis() - birthTime >= lifetimeMs;
        }

        private float progress() {
            return Math.min(1.0f, (System.currentTimeMillis() - birthTime) / (float) lifetimeMs);
        }

        private float getAlpha() {
            float progress = progress();
            if (progress < 0.12f) {
                return easeOutCubic(progress / 0.12f);
            }
            if (progress > 0.72f) {
                return 1.0f - easeOutCubic((progress - 0.72f) / 0.28f);
            }
            return 1.0f;
        }

        private float getScale() {
            float progress = progress();
            if (progress < 0.18f) {
                return 0.45f + 0.55f * easeOutBack(progress / 0.18f);
            }
            return 1.0f;
        }

        private Vec3d getAnimatedPosition() {
            float progress = progress();
            double lift = riseAmount * easeOutCubic(progress);
            return position.add(0.0, lift, 0.0);
        }
    }
}
