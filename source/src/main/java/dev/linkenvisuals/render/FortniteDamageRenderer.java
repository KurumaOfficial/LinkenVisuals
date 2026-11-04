package dev.linkenvisuals.render;

import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.FortniteDamageModule;
import dev.linkenvisuals.ui.Theme;
import dev.linkenvisuals.util.ColorMath;
import dev.linkenvisuals.util.particles.FortniteDamageRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Donor FortniteDamage: floating damage numbers at the hit point. The donor
 * draws MSDF quads in a world-space billboard; this port projects the same
 * number to screen space and uses the proven GUI MSDF pipeline, which keeps the
 * digits crisp and independent of terrain depth.
 */
public final class FortniteDamageRenderer {
    private static final float FONT_SIZE = 10.8f;
    private static final float[][] OUTLINE_OFFSETS = {
        {-1.0f, 0.0f}, {1.0f, 0.0f}, {0.0f, -1.0f}, {0.0f, 1.0f},
        {-0.7f, -0.7f}, {-0.7f, 0.7f}, {0.7f, -0.7f}, {0.7f, 0.7f}
    };

    private static final List<PendingHit> PENDING = new ArrayList<>();
    private static final List<DamageNumber> NUMBERS = new ArrayList<>();
    private static boolean lastRenderVisible;

    private FortniteDamageRenderer() {
    }

    public static void clear() {
        PENDING.clear();
        NUMBERS.clear();
        lastRenderVisible = false;
    }

    public static int liveCount() {
        return NUMBERS.size();
    }

    public static boolean lastRenderVisible() {
        return lastRenderVisible;
    }

    /** Deterministic visual probe used by the in-game autotest. */
    public static void debugSpawn(MinecraftClient client, int value) {
        if (client.player == null || client.gameRenderer.getCamera() == null) {
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        // Camera.horizontalPlane is the forward vector; right = forward x up.
        Vector3fc forwardAxis = camera.getHorizontalPlane();
        Vector3fc up = camera.getVerticalPlane();
        Vec3d forward = new Vec3d(forwardAxis.x(), forwardAxis.y(), forwardAxis.z());
        Vec3d upVec = new Vec3d(up.x(), up.y(), up.z());
        Vec3d position = camera.getCameraPos()
            .add(forward.multiply(4.0))
            .add(upVec.multiply(0.2));
        NUMBERS.add(new DamageNumber(position, String.valueOf(Math.max(1, value)), false,
            System.currentTimeMillis(), 5_000L, 0.0f));
    }

    /** Called from the attack hook; {@code crit} follows the same gate as HitSound. */
    public static void onAttack(Entity target, boolean crit) {
        FortniteDamageModule module = Modules.fortniteDamage();
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.enabled() || client.player == null || client.world == null) {
            return;
        }
        if (!(target instanceof LivingEntity living) || target == client.player || !living.isAlive()) {
            return;
        }
        Vec3d position = resolveHitPosition(client.player, living);
        PENDING.add(new PendingHit(living, health(living), position, crit, System.currentTimeMillis()));
    }

    /** Advances pending hits and drops expired numbers; called once per client tick. */
    public static void tick() {
        FortniteDamageModule module = Modules.fortniteDamage();
        if (module == null || !module.enabled()) {
            if (!PENDING.isEmpty() || !NUMBERS.isEmpty()) {
                clear();
            }
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        PENDING.removeIf(hit -> {
            if (now - hit.birthMs > FortniteDamageRules.MAX_WAIT_MS) {
                return true;
            }
            if (hit.target.isRemoved() || !hit.target.isAlive()) {
                float estimate = estimateAttackDamage(hit.crit);
                float damage = Math.max(0.0f, hit.healthBefore);
                if (estimate > 0.05f) {
                    damage = Math.min(damage, estimate);
                }
                if (damage > 0.05f) {
                    NUMBERS.add(createNumber(module, hit, damage));
                }
                return true;
            }
            float damage = hit.healthBefore - health(hit.target);
            if (damage > 0.05f) {
                NUMBERS.add(createNumber(module, hit, damage));
                return true;
            }
            return false;
        });
        NUMBERS.removeIf(number -> now - number.birthMs >= number.lifetimeMs);
    }

    /** HUD pass: project each world point through the camera and draw MSDF digits. */
    public static void render(DrawContext context) {
        lastRenderVisible = false;
        FortniteDamageModule module = Modules.fortniteDamage();
        if (module == null || !module.enabled() || NUMBERS.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        GlyphAtlas font = Fonts.titleFont();
        if (client.player == null || client.world == null || camera == null || font == null) {
            return;
        }
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float fov = (float) Math.toRadians(Math.max(30, Math.min(110,
            client.options.getFov().getValue())));
        float focal = (float) (height / (2.0 * Math.tan(fov * 0.5)));
        Vector3fc forwardAxis = camera.getHorizontalPlane();
        Vector3fc up = camera.getVerticalPlane();
        Vec3d forwardVec = new Vec3d(forwardAxis.x(), forwardAxis.y(), forwardAxis.z());
        Vec3d upVec = new Vec3d(up.x(), up.y(), up.z());
        Vec3d rightVec = forwardVec.crossProduct(upVec);
        Vec3d cam = camera.getCameraPos();
        long now = System.currentTimeMillis();
        boolean visible = false;

        for (DamageNumber number : NUMBERS) {
            float progress = MathHelper.clamp(
                (now - number.birthMs) / (float) number.lifetimeMs, 0.0f, 1.0f);
            float alpha = FortniteDamageRules.alpha(progress);
            if (alpha <= 0.01f) {
                continue;
            }
            float scale = module.size.value();
            if ("Fortnite".equals(module.style.current())) {
                scale *= FortniteDamageRules.scale(progress);
            }
            Vec3d rel = number.position.add(0.0,
                FortniteDamageRules.rise(progress, number.rise), 0.0).subtract(cam);
            float z = (float) rel.dotProduct(forwardVec);
            if (z <= 0.05f) {
                continue;
            }
            float sx = width * 0.5f + (float) rel.dotProduct(rightVec) * focal / z;
            float sy = height * 0.5f - (float) rel.dotProduct(upVec) * focal / z;
            float textSize = Math.max(1.0f, scale * FONT_SIZE * focal / z);
            if (sx < -textSize * 4 || sx > width + textSize * 4
                || sy < -textSize * 4 || sy > height + textSize * 4) {
                continue;
            }
            int alphaByte = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
            int fill = color(module, number.crit, alphaByte);
            int outline = ColorMath.withAlpha(0x000000, alphaByte);
            float textWidth = font.width(number.text, textSize);
            float baseX = sx - textWidth * 0.5f;
            for (float[] offset : OUTLINE_OFFSETS) {
                MsdfText.drawOn(context, font, number.text,
                    baseX + offset[0] * 1.5f, sy + offset[1] * 1.5f, textSize, outline);
            }
            MsdfText.drawOn(context, font, number.text, baseX, sy, textSize, fill);
            visible = true;
        }
        lastRenderVisible = visible;
    }

    private static int color(FortniteDamageModule module, boolean crit, int alpha) {
        int rgb;
        if (crit && module.critColor.value()) {
            rgb = 0xFCFE3C;
        } else if (module.themeColor.value()) {
            rgb = Theme.accent();
        } else {
            rgb = module.color.rgb();
        }
        return ColorMath.withAlpha(rgb, alpha);
    }

    private static DamageNumber createNumber(FortniteDamageModule module, PendingHit hit, float damage) {
        return new DamageNumber(hit.position, FortniteDamageRules.text(damage), hit.crit,
            System.currentTimeMillis(), (long) module.lifetime.value(), module.rise.value());
    }

    private static float health(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private static float estimateAttackDamage(boolean crit) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0.0f;
        }
        double damage = client.player.getAttributeValue(
            net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
        if (crit) {
            damage *= 1.5;
        }
        return (float) damage;
    }

    private static Vec3d resolveHitPosition(PlayerEntity attacker, Entity target) {
        Vec3d fallback = target.getBoundingBox().getCenter();
        Vec3d eye = attacker.getEyePos();
        Vec3d look = attacker.getRotationVec(1.0f);
        double distance = Math.max(eye.distanceTo(fallback) + 1.0, 6.0);
        Vec3d reach = eye.add(look.multiply(distance));
        Optional<Vec3d> hit = target.getBoundingBox().raycast(eye, reach);
        return hit.orElseGet(() -> eye.add(look.multiply(eye.distanceTo(fallback))));
    }

    private record PendingHit(LivingEntity target, float healthBefore, Vec3d position,
                              boolean crit, long birthMs) {
    }

    private record DamageNumber(Vec3d position, String text, boolean crit,
                                long birthMs, long lifetimeMs, float rise) {
    }
}
