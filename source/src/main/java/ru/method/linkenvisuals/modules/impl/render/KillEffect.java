package ru.method.linkenvisuals.modules.impl.render;


import lombok.AllArgsConstructor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class KillEffect extends Module {

    private static final long DAMAGE_RETENTION_MS = 4_500L;
    private static final Identifier BLOOM_TEXTURE = LinkenVisuals.id("client/xuynya/ghost_bloom.png");
    private static final double MAX_RENDER_DISTANCE_SQ = 110 * 110;

    private static final float BURST_RADIUS = 1.65f;
    private static final float BURST_SIZE = 0.85f;
    private static final int BURST_PARTICLES = 48;
    private static final float BURST_DURATION_TICKS = 18.0f;

    private static final float SPIRAL_HEIGHT = 15.0f;
    private static final float SPIRAL_RADIUS = 0.1f;
    private static final float SPIRAL_SIZE = 0.96f;
    private static final int SPIRAL_SEGMENTS = 107;
    private static final float SPIRAL_DURATION_TICKS = 53.0f;
    private static final float SPIRAL_TURNS = 3.5f;

    private final Map<Integer, TrackedTarget> trackedTargets = new HashMap<>();

    public final EnumSetting<EffectMode> effectMode = new EnumSetting<>("settings.killeffect.mode", EffectMode.Lightning);

    private final List<SpiralColumn> activeSpirals = new ArrayList<>();
    private final List<BloomBurst> activeBursts = new ArrayList<>();
    private final List<BloomBurst> activeShockwaves = new ArrayList<>();
    private final List<BloomBurst> activeAscensions = new ArrayList<>();
    private final List<LightningBolt> activeBolts = new ArrayList<>();

    private static final float SHOCK_DURATION_TICKS = 16.0f;
    private static final float ASCEND_DURATION_TICKS = 34.0f;
    private static final int BOLT_DURATION_TICKS = 8;
    private static final float BOLT_HEIGHT = 9.0f;
    private static final int BOLT_SEGMENTS = 9;

    private static final int STORM_DURATION_TICKS = 16;
    private static final float STORM_HEIGHT = 120.0f;

    public KillEffect() {
        super("KillEffect", Category.Visuals);
        getSettings().add(effectMode);
    }

    @Override
    public void onDisable() {
        trackedTargets.clear();
        activeSpirals.clear();
        activeBursts.clear();
        activeShockwaves.clear();
        activeAscensions.clear();
        activeBolts.clear();
        super.onDisable();
    }

    @EventHandler
    private void onAttack(EventAttackEntity event) {
        if (fullNullCheck()) return;
        if (event.getPlayer() != mc.player) return;

        if (!(event.getTarget() instanceof LivingEntity living)) return;
        if (living == mc.player) return;

        long now = System.currentTimeMillis();
        trackedTargets.put(living.getId(), new TrackedTarget(now, living.getEntityPos()));
    }

    @EventHandler
    private void onPacketReceive(EventPacket.Receive event) {
        if (fullNullCheck()) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof EntityDamageS2CPacket damagePacket) {
            handleDamagePacket(damagePacket);
        } else if (packet instanceof EntityStatusS2CPacket statusPacket && statusPacket.getStatus() == 3) {
            handleDeathStatus(statusPacket);
        }
    }

    // Надёжный сигнал смерти на клиенте: EntityStatus со статусом 3 (анимация смерти). Сервер шлёт его в
    // onDeath и для мобов (LivingEntity), и для игроков (ServerPlayerEntity), ПОКА сущность ещё существует.
    // В отличие от «сущность исчезла с клиента» (деспаун/выгрузка чанка/выход за дальность — тоже удаляется
    // с RemovalReason.DISCARDED), статус 3 приходит только на настоящую смерть → нет ложных срабатываний.
    private void handleDeathStatus(EntityStatusS2CPacket packet) {
        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof LivingEntity living)) return;
        TrackedTarget target = trackedTargets.remove(living.getId());
        if (target == null) return; // цель мы не били — не наш килл
        Vec3d strikePos = target.lastPos != null ? target.lastPos : living.getEntityPos();
        triggerKillEffect(strikePos);
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            trackedTargets.clear();
            activeSpirals.clear();
            activeBursts.clear();
            activeShockwaves.clear();
            activeAscensions.clear();
            activeBolts.clear();
            return;
        }

        tickSpirals();
        tickBursts();
        tickTimed(activeShockwaves, SHOCK_DURATION_TICKS);
        tickTimed(activeAscensions, ASCEND_DURATION_TICKS);
        if (!activeBolts.isEmpty()) {
            long wt = mc.world.getTime();
            activeBolts.removeIf(b -> (wt - b.startTick) > b.durationTicks + 1);
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, TrackedTarget>> iterator = trackedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrackedTarget> entry = iterator.next();
            TrackedTarget target = entry.getValue();

            if (now - target.lastDamageMs > DAMAGE_RETENTION_MS) {
                iterator.remove();
                continue;
            }

            Entity entity = mc.world.getEntityById(entry.getKey());
            if (entity instanceof LivingEntity living) {
                if (living.isAlive() && living.getHealth() > 0.0f) {
                    target.lastPos = living.getEntityPos();
                    continue;
                }

                // Здоровье ≤0 у ещё присутствующей сущности — реальная смерть (резерв, если статус 3 упустили).
                Vec3d strikePos = target.lastPos != null ? target.lastPos : living.getEntityPos();
                triggerKillEffect(strikePos);
                iterator.remove();
                continue;
            }

            if (now - target.lastDamageMs < 3500L && target.lastPos != null) {
                triggerKillEffect(target.lastPos);
            }
            iterator.remove();
        }
    }

    @EventHandler
    private void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;
        if (activeSpirals.isEmpty() && activeBursts.isEmpty()
                && activeShockwaves.isEmpty() && activeAscensions.isEmpty() && activeBolts.isEmpty()) return;

        RenderTickCounter tickCounter = event.getTickCounter();
        float tickDelta = tickCounter.getTickProgress(true);
        long worldTime = mc.world.getTime();

        int baseRgb = ColorUtils.getGlobalColor().getRGB() & 0x00FFFFFF;

        if (!activeBolts.isEmpty()) {
            MatrixStack matrices = event.getMatrixStack();
            Iterator<LightningBolt> it = activeBolts.iterator();
            while (it.hasNext()) {
                LightningBolt bolt = it.next();
                float age = (float) (worldTime - bolt.startTick) + tickDelta;
                if (age >= bolt.durationTicks) {
                    it.remove();
                    continue;
                }
                renderLightningBolt(matrices, bolt, age, baseRgb);
            }
        }

        if (!activeSpirals.isEmpty()) {
            int segments = SPIRAL_SEGMENTS;
            float height = SPIRAL_HEIGHT;
            float radius = SPIRAL_RADIUS;
            float size = SPIRAL_SIZE;
            float turns = SPIRAL_TURNS;
            float duration = SPIRAL_DURATION_TICKS;

            Iterator<SpiralColumn> iterator = activeSpirals.iterator();
            while (iterator.hasNext()) {
                SpiralColumn spiral = iterator.next();

                float age = (float) (worldTime - spiral.startTick) + tickDelta;
                if (age >= duration) {
                    iterator.remove();
                    continue;
                }

                float timeProgress = age / duration;
                float fadeIn = smootherStep01(MathHelper.clamp(timeProgress / 0.08f, 0.0f, 1.0f));
                float fadeOut = 1.0f - smootherStep01(MathHelper.clamp((timeProgress - 0.7f) / 0.3f, 0.0f, 1.0f));
                float fade = fadeIn * fadeOut;

                float build = smootherStep01(MathHelper.clamp(timeProgress / 0.35f, 0.0f, 1.0f));
                float spin = age * 0.28f * (0.2f + 0.8f * build);

                float maxP = build >= 0.999f ? 1.0f : build;
                float sizeScale = 0.85f + 0.15f * fadeIn;

                renderSpiralStrand(spiral.origin, spiral.baseAngle, spin, turns, radius, height, size * sizeScale, segments, fade, baseRgb, 0.0f, maxP);
                renderSpiralStrand(spiral.origin, spiral.baseAngle, -spin * 0.85f, turns, radius * 0.92f, height, size * 0.95f * sizeScale, segments, fade, baseRgb, (float) Math.PI, maxP);
            }
        }

        if (!activeBursts.isEmpty()) {
            float duration = BURST_DURATION_TICKS;
            float radius = BURST_RADIUS;
            float size = BURST_SIZE;
            int particles = BURST_PARTICLES;

            Iterator<BloomBurst> iterator = activeBursts.iterator();
            while (iterator.hasNext()) {
                BloomBurst burst = iterator.next();

                float age = (float) (worldTime - burst.startTick) + tickDelta;
                if (age >= duration) {
                    iterator.remove();
                    continue;
                }

                float progress = age / duration;
                float fadeIn = smootherStep01(MathHelper.clamp(progress / 0.12f, 0.0f, 1.0f));
                float fadeOut = 1.0f - smootherStep01(MathHelper.clamp((progress - 0.55f) / 0.45f, 0.0f, 1.0f));
                float fade = fadeIn * fadeOut;
                if (fade <= 0.001f) continue;

                float expansion = smootherStep01(MathHelper.clamp(progress / 0.55f, 0.0f, 1.0f));
                float currentRadius = radius * (0.3f + 0.7f * expansion);
                float currentSize = Math.max(0.05f, size * (0.9f + 0.35f * (1.0f - progress)));

                renderBloomBurst(burst.origin, burst.seed, particles, currentRadius, currentSize, fade, baseRgb);
            }
        }

        if (!activeShockwaves.isEmpty()) {
            Iterator<BloomBurst> it = activeShockwaves.iterator();
            while (it.hasNext()) {
                BloomBurst sw = it.next();
                float age = (float) (worldTime - sw.startTick) + tickDelta;
                if (age >= SHOCK_DURATION_TICKS) {
                    it.remove();
                    continue;
                }
                float prog = age / SHOCK_DURATION_TICKS;
                float radius = 3.2f * smootherStep01(prog);
                float fade = smootherStep01(MathHelper.clamp(prog / 0.12f, 0f, 1f)) * (1f - smootherStep01(prog));
                float size = 0.72f * (1.05f - 0.55f * prog);
                renderShockwave(sw.origin, radius, size, fade, baseRgb);
            }
        }

        if (!activeAscensions.isEmpty()) {
            Iterator<BloomBurst> it = activeAscensions.iterator();
            while (it.hasNext()) {
                BloomBurst as = it.next();
                float age = (float) (worldTime - as.startTick) + tickDelta;
                if (age >= ASCEND_DURATION_TICKS) {
                    it.remove();
                    continue;
                }
                float prog = age / ASCEND_DURATION_TICKS;
                float fadeIn = smootherStep01(MathHelper.clamp(prog / 0.12f, 0f, 1f));
                float fadeOut = 1f - smootherStep01(MathHelper.clamp((prog - 0.6f) / 0.4f, 0f, 1f));
                renderAscension(as.origin, as.seed, prog, fadeIn * fadeOut, baseRgb);
            }
        }
    }

    private void handleDamagePacket(EntityDamageS2CPacket packet) {
        if (mc.world == null || mc.player == null) return;

        int targetId = packet.entityId();
        if (targetId == mc.player.getId()) return;

        int playerId = mc.player.getId();
        boolean causedByPlayer = packet.sourceCauseId() == playerId || packet.sourceDirectId() == playerId;

        if (!causedByPlayer) {
            Entity direct = mc.world.getEntityById(packet.sourceDirectId());
            if (!(direct instanceof ProjectileEntity projectile) || !isOwnedByPlayer(projectile)) {
                return;
            }
        }

        Entity targetEntity = mc.world.getEntityById(targetId);
        if (!(targetEntity instanceof LivingEntity living) || targetEntity == mc.player) return;

        long now = System.currentTimeMillis();
        trackedTargets.put(targetId, new TrackedTarget(now, living.getEntityPos()));
    }

    private void triggerKillEffect(Vec3d pos) {
        if (pos == null || mc.world == null) return;

        switch (effectMode.getValue()) {
            case Lightning -> spawnLightning(pos);
            case Storm -> spawnStorm(pos);
            case Spiral -> spawnSpiralColumn(pos);
            case Burst -> spawnBloomBurst(pos);
            case Shockwave -> spawnTimed(activeShockwaves, pos);
            case Ascension -> spawnTimed(activeAscensions, pos);
        }
    }

    private void spawnTimed(List<BloomBurst> list, Vec3d pos) {
        if (mc.world == null || pos == null) return;
        long seed = Double.doubleToLongBits(pos.x) ^ Long.rotateLeft(Double.doubleToLongBits(pos.z), 19) ^ mc.world.getTime();
        list.add(new BloomBurst(mc.world.getTime(), pos, seed));
    }

    // Раньше спавнили ванильный LightningEntity — но он рисуется в мировом проходе и ПИШЕТ near-глубину,
    // поэтому FogBlur освобождал его от размытия (резкий столб поверх размытого мира). Теперь молния —
    // своя процедурная аддитивная геометрия, которую GameRendererMixin рисует ДО блюра без записи
    // глубины, и туман размывает её вместе с окружением.
    private void spawnLightning(Vec3d pos) {
        if (mc.world == null || pos == null) return;
        long seed = Double.doubleToLongBits(pos.x) ^ Long.rotateLeft(Double.doubleToLongBits(pos.z), 21) ^ mc.world.getTime();
        activeBolts.add(new LightningBolt(mc.world.getTime(), pos, seed, false, BOLT_DURATION_TICKS));
    }

    // Порт эффекта из wyvern: разряд бьёт с неба в точку смерти + раскат грома.
    private void spawnStorm(Vec3d pos) {
        if (mc.world == null || pos == null) return;
        long seed = Double.doubleToLongBits(pos.x) ^ Long.rotateLeft(Double.doubleToLongBits(pos.z), 23) ^ mc.world.getTime();
        activeBolts.add(new LightningBolt(mc.world.getTime(), pos, seed, true, STORM_DURATION_TICKS));
        mc.world.playSoundClient(pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.0f, 1.0f, false);
    }

    private void renderLightningBolt(MatrixStack matrices, LightningBolt bolt, float age, int baseRgb) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;
        if (mc.player.getEntityPos().squaredDistanceTo(bolt.origin) > MAX_RENDER_DISTANCE_SQ) return;

        // Мерцание + затухание: ярко в первые тики, несколько вспышек, затем гаснет.
        float life = age / bolt.durationTicks;
        float flicker = 0.55f + 0.45f * (float) Math.sin(age * 7.3);
        float fade = (1.0f - smootherStep01(MathHelper.clamp((life - 0.35f) / 0.65f, 0f, 1f))) * flicker;
        if (age < 1.5f) fade = Math.max(fade, 0.9f);
        fade = MathHelper.clamp(fade, 0f, 1f);
        if (fade <= 0.02f) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        // Детерминированная ломаная от точки удара (форма стабильна по кадрам — seed фиксирован).
        Random rnd = new Random(bolt.seed);
        Vec3d[] pts = bolt.sky ? buildSkyStrike(rnd, bolt.origin) : buildGroundBolt(rnd, bolt.origin);
        int segs = pts.length - 1;

        int coreColor = (MathHelper.clamp((int) (fade * 255f), 0, 255) << 24) | 0xFFFFFF;   // бело-горячее ядро
        int glowColor = (MathHelper.clamp((int) (fade * 120f), 0, 255) << 24) | baseRgb;    // акцентное свечение
        int haloColor = (MathHelper.clamp((int) (fade * 45f), 0, 255) << 24) | baseRgb;     // широкий ореол (только Storm)

        for (int i = 0; i < segs; i++) {
            if (bolt.sky) emitBoltSegment(matrices, camPos, pts[i], pts[i + 1], 0.6f, haloColor);
            emitBoltSegment(matrices, camPos, pts[i], pts[i + 1], 0.26f, glowColor);
            emitBoltSegment(matrices, camPos, pts[i], pts[i + 1], 0.075f, coreColor);
        }

        // Короткие ветви от середины канала.
        for (int b = 0; b < (bolt.sky ? 5 : 2); b++) {
            int idx = 1 + rnd.nextInt(Math.max(1, segs - 1));
            Vec3d start = pts[idx];
            Vec3d dir = new Vec3d(rnd.nextDouble() - 0.5, -0.15 - rnd.nextDouble() * 0.35, rnd.nextDouble() - 0.5);
            if (dir.lengthSquared() < 1e-6) continue;
            Vec3d end = start.add(dir.normalize().multiply(1.2 + rnd.nextDouble() * 1.6));
            emitBoltSegment(matrices, camPos, start, end, 0.15f, glowColor);
            emitBoltSegment(matrices, camPos, start, end, 0.05f, coreColor);
        }
    }

    // Камера-обращённый билборд-сегмент молнии (аддитивный квад в буфер молнии Render3D).
    private void emitBoltSegment(MatrixStack matrices, Vec3d camPos, Vec3d a, Vec3d b, float halfWidth, int color) {
        Vec3d dir = b.subtract(a);
        if (dir.lengthSquared() < 1e-9) return;
        Vec3d toCam = camPos.subtract(a.add(b).multiply(0.5));
        Vec3d perp = dir.crossProduct(toCam);
        if (perp.lengthSquared() < 1e-9) return;
        perp = perp.normalize().multiply(halfWidth);
        Render3D.drawLightningQuad(matrices, a.add(perp), b.add(perp), b.subtract(perp), a.subtract(perp), color);
    }

    private Vec3d[] buildGroundBolt(Random rnd, Vec3d origin) {
        float height = BOLT_HEIGHT + rnd.nextFloat() * 4.0f;
        double jitter = 0.55;
        Vec3d[] pts = new Vec3d[BOLT_SEGMENTS + 1];
        pts[0] = origin;
        for (int i = 1; i <= BOLT_SEGMENTS; i++) {
            double frac = i / (double) BOLT_SEGMENTS;
            double spread = i < BOLT_SEGMENTS ? 1.0 : 0.4;
            double px = origin.x + (rnd.nextDouble() - 0.5) * jitter * 2.0 * spread;
            double pz = origin.z + (rnd.nextDouble() - 0.5) * jitter * 2.0 * spread;
            pts[i] = new Vec3d(px, origin.y + frac * height, pz);
        }
        return pts;
    }

    // Ломаная сверху вниз (как в wyvern): шаг по Y 6..16 блоков со случайным сносом по XZ,
    // последняя точка приколачивается ровно к origin. Порядок точек — от неба к цели.
    private Vec3d[] buildSkyStrike(Random rnd, Vec3d origin) {
        List<Vec3d> pts = new ArrayList<>();
        Vec3d current = origin.add(0, STORM_HEIGHT, 0);
        pts.add(current);
        while (current.y > origin.y) {
            double dx = (rnd.nextDouble() - 0.5) * 0.8;
            double dy = -(6.0 + rnd.nextDouble() * 10.0);
            double dz = (rnd.nextDouble() - 0.5) * 0.8;
            current = current.add(dx, dy, dz);
            if (current.y <= origin.y) current = origin;
            pts.add(current);
        }
        return pts.toArray(new Vec3d[0]);
    }

    // sky = удар с неба (режим Storm), иначе — столб вверх из точки смерти (режим Lightning).
    private record LightningBolt(long startTick, Vec3d origin, long seed, boolean sky, int durationTicks) {
    }

    private boolean isOwnedByPlayer(ProjectileEntity projectile) {
        Entity owner = projectile.getOwner();
        return owner instanceof PlayerEntity player && player.getUuid().equals(mc.player.getUuid());
    }

    private void spawnSpiralColumn(Vec3d pos) {
        if (mc.world == null) return;
        if (pos == null) return;
        activeSpirals.add(new SpiralColumn(mc.world.getTime(), pos, (float) (Math.random() * Math.PI * 2.0)));
    }

    private void tickSpirals() {
        if (mc.world == null || activeSpirals.isEmpty()) return;
        long worldTime = mc.world.getTime();
        float duration = SPIRAL_DURATION_TICKS;
        activeSpirals.removeIf(spiral -> (worldTime - spiral.startTick) > duration + 2);
    }

    private void spawnBloomBurst(Vec3d pos) {
        if (mc.world == null) return;
        if (pos == null) return;
        long seed = Double.doubleToLongBits(pos.x) ^ Long.rotateLeft(Double.doubleToLongBits(pos.z), 19) ^ mc.world.getTime();
        activeBursts.add(new BloomBurst(mc.world.getTime(), pos, seed));
    }

    private void tickBursts() {
        if (mc.world == null || activeBursts.isEmpty()) return;
        long worldTime = mc.world.getTime();
        float duration = BURST_DURATION_TICKS;
        activeBursts.removeIf(burst -> (worldTime - burst.startTick) > duration + 2);
    }

    private void tickTimed(List<BloomBurst> list, float duration) {
        if (mc.world == null || list.isEmpty()) return;
        long worldTime = mc.world.getTime();
        list.removeIf(b -> (worldTime - b.startTick) > duration + 2);
    }

    private boolean isInView(Vec3d pos) {
        return ru.method.linkenvisuals.utils.world.WorldUtils.isBoxVisible(
                new Box(pos.subtract(0.5, 0.5, 0.5), pos.add(0.5, 0.5, 0.5))
        );
    }

    private void renderBloomBurst(Vec3d origin, long seed, int particles, float radius, float size, float fade, int baseRgb) {
        if (particles <= 0 || radius <= 0.0f || size <= 0.0f) return;
        if (mc.player.getEntityPos().squaredDistanceTo(origin) > MAX_RENDER_DISTANCE_SQ) return;

        for (int i = 0; i < particles; i++) {
            long s0 = seed + (i * 0x9E3779B97F4A7C15L);
            float u = hashToUnitFloat(s0);
            float v = hashToUnitFloat(s0 ^ 0xD1B54A32D192ED03L);
            float w = hashToUnitFloat(s0 ^ 0x94D049BB133111EBL);

            float theta = u * MathHelper.TAU;
            float z = 1.0f - 2.0f * v;
            float r = MathHelper.sqrt(Math.max(0.0f, 1.0f - z * z));

            float dirX = MathHelper.cos(theta) * r;
            float dirY = z * 0.85f + 0.15f;
            float dirZ = MathHelper.sin(theta) * r;

            float jitter = 0.75f + 0.5f * w;
            Vec3d pos = origin.add(dirX * radius * jitter, dirY * radius * jitter, dirZ * radius * jitter);
            if (!isInView(pos)) continue;

            float alphaF = MathHelper.clamp(fade * (0.55f + 0.45f * w), 0.0f, 1.0f);
            int alpha = MathHelper.clamp((int) (alphaF * 255.0f), 0, 255);
            if (alpha <= 3) continue;

            int color = (alpha << 24) | baseRgb;
            float localSize = Math.max(0.04f, size * (0.65f + 0.55f * w));
            Render3D.renderBillboardTexture(pos, localSize, BLOOM_TEXTURE, color, true, true);
        }
    }

    // Расходящееся кольцо на земле (ориентированные вверх билборды по окружности растущего радиуса).
    private void renderShockwave(Vec3d origin, float radius, float size, float fade, int baseRgb) {
        if (mc.player.getEntityPos().squaredDistanceTo(origin) > MAX_RENDER_DISTANCE_SQ) return;
        if (fade <= 0.003f || size <= 0.0f) return;
        int alpha = MathHelper.clamp((int) (fade * 0.85f * 255.0f), 0, 255);
        if (alpha <= 3) return;
        int color = (alpha << 24) | baseRgb;
        int seg = 30;
        for (int i = 0; i < seg; i++) {
            float ang = (float) (i / (float) seg * Math.PI * 2.0);
            Vec3d p = new Vec3d(origin.x + Math.cos(ang) * radius, origin.y + 0.06, origin.z + Math.sin(ang) * radius);
            if (!isInView(p)) continue;
            Render3D.renderOrientedTexture(p, size, BLOOM_TEXTURE, color, Direction.UP, true, true);
        }
    }

    // Восходящие «души»: билборды поднимаются от точки смерти по слегка колеблющимся траекториям.
    private void renderAscension(Vec3d origin, long seed, float progress, float fade, int baseRgb) {
        if (mc.player.getEntityPos().squaredDistanceTo(origin) > MAX_RENDER_DISTANCE_SQ) return;
        if (fade <= 0.003f) return;
        int souls = 9;
        for (int i = 0; i < souls; i++) {
            long s0 = seed + (i * 0x9E3779B97F4A7C15L);
            float u = hashToUnitFloat(s0);
            float v = hashToUnitFloat(s0 ^ 0xD1B54A32D192ED03L);
            float w = hashToUnitFloat(s0 ^ 0x94D049BB133111EBL);

            float ang = u * MathHelper.TAU;
            float rad = 0.2f + v * 0.55f;
            float rise = progress * (2.1f + w * 1.0f);
            float wob = MathHelper.sin(progress * 9.0f + u * 12.0f) * 0.14f;

            Vec3d p = origin.add(Math.cos(ang) * rad + wob, rise + 0.1, Math.sin(ang) * rad);
            if (!isInView(p)) continue;

            float aF = MathHelper.clamp(fade * (0.55f + 0.45f * w), 0.0f, 1.0f);
            int alpha = MathHelper.clamp((int) (aF * 255.0f), 0, 255);
            if (alpha <= 3) continue;

            int color = (alpha << 24) | baseRgb;
            float size = 0.42f * (0.7f + 0.5f * v);
            Render3D.renderBillboardTexture(p, size, BLOOM_TEXTURE, color, true, true);
        }
    }

    private static float hashToUnitFloat(long x) {
        long z = x;
        z ^= z >>> 33;
        z *= 0xff51afd7ed558ccdL;
        z ^= z >>> 33;
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= z >>> 33;
        return (z & 0xFFFFFFL) / (float) 0x1000000;
    }

    private void renderSpiralStrand(
            Vec3d origin,
            float baseAngle,
            float spin,
            float turns,
            float radius,
            float height,
            float size,
            int segments,
            float fade,
            int baseRgb,
            float phaseOffset,
            float maxP
    ) {
        if (height <= 0.0f || size <= 0.0f || segments <= 0) return;
        if (mc.player.getEntityPos().squaredDistanceTo(origin) > MAX_RENDER_DISTANCE_SQ) return;

        for (int i = 0; i < segments; i++) {
            float p = segments == 1 ? 0.0f : (float) i / (segments - 1);
            float angle = baseAngle + phaseOffset + (p * turns * (float) (Math.PI * 2.0)) + spin;

            float taper = 0.35f + (1.0f - p) * 0.65f;
            float localRadius = radius * taper;
            double x = origin.x + Math.cos(angle) * localRadius;
            double y = origin.y + p * height;
            double z = origin.z + Math.sin(angle) * localRadius;

            float wave = MathHelper.sin(p * (float) Math.PI);

            float headFade = 1.0f;
            if (maxP < 0.999f) {
                headFade = 1.0f - smootherStep01(MathHelper.clamp((p - maxP) / 0.06f, 0.0f, 1.0f));
            }

            float alphaF = MathHelper.clamp(fade * headFade * (0.35f + 0.65f * wave), 0.0f, 1.0f);
            int alpha = MathHelper.clamp((int) (alphaF * 255.0f), 0, 255);
            if (alpha <= 3) continue;

            Vec3d renderPos = new Vec3d(x, y, z);
            if (!isInView(renderPos)) continue;

            float localSize = Math.max(0.04f, size * (0.75f + wave * 0.35f));
            int color = (alpha << 24) | baseRgb;
            Render3D.renderBillboardTexture(renderPos, localSize, BLOOM_TEXTURE, color, true, true);
        }
    }

    private static float smootherStep01(float x) {
        float t = MathHelper.clamp(x, 0.0f, 1.0f);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static class TrackedTarget {
        private long lastDamageMs;
        private Vec3d lastPos;

        private TrackedTarget(long lastDamageMs, Vec3d lastPos) {
            this.lastDamageMs = lastDamageMs;
            this.lastPos = lastPos;
        }
    }

    @AllArgsConstructor
    private static class SpiralColumn {
        private final long startTick;
        private final Vec3d origin;
        private final float baseAngle;
    }

    @AllArgsConstructor
    private static class BloomBurst {
        private final long startTick;
        private final Vec3d origin;
        private final long seed;
    }

    @AllArgsConstructor
    private enum EffectMode implements Nameable {
        Lightning("settings.killeffect.mode.lightning"),
        Storm("settings.killeffect.mode.storm"),
        Spiral("settings.killeffect.mode.spiral"),
        Burst("settings.killeffect.mode.burst"),
        Shockwave("settings.killeffect.mode.shockwave"),
        Ascension("settings.killeffect.mode.ascension");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }
}
