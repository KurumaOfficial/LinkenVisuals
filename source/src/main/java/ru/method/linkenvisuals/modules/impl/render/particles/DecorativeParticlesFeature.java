package ru.method.linkenvisuals.modules.impl.render.particles;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class DecorativeParticlesFeature implements Wrapper {
    private static final Identifier BLOOM_TEXTURE = LinkenVisuals.id("client/xuynya/ghost_bloom.png");
    private static final Identifier STAR_TEXTURE = LinkenVisuals.id("client/particles/star.png");
    private static final Identifier SNOW_TEXTURE = LinkenVisuals.id("client/particles/snow.png");
    private static final Identifier FIREFLY_TEXTURE = LinkenVisuals.id("client/particles/firefly.png");
    private static final Identifier DOLLAR_TEXTURE = LinkenVisuals.id("client/particles/dollar.png");
    private static final long TARGET_LIFESPAN_MS = 2000L;
    private static final long THROW_LIFESPAN_MS = 2000L;
    private static final long TARGET_FADE_IN_MS = 350L;
    private static final long TARGET_FADE_OUT_START_MS = 650L;
    private static final long THROW_FADE_IN_MS = 650L;
    private static final long THROW_FADE_OUT_START_MS = 1200L;
    private static final long FADE_DURATION_MS = 450L;
    private static final int MAX_TARGET_PARTICLES = 420;
    private static final int MAX_THROW_PARTICLES = 480;
    private static final int ATTACK_BURST_COUNT = 35;
    private static final int THROW_BURST_COUNT = 4;

    private final BooleanSetting spawnOnAttack = new BooleanSetting("settings.particles.spawn_on.attack", true);
    private final BooleanSetting spawnOnThrow = new BooleanSetting("settings.particles.spawn_on.throw", true);
    private final EnumSetting<ParticleMode> mode = new EnumSetting<>("settings.particles.mode", ParticleMode.Bloom);
    private final NumberSetting particleSize = new NumberSetting("settings.particles.size", 0.5f, 0.0f, 1.0f, 0.1f);

    private final List<Particle> attackParticles = new ArrayList<>();
    private final List<Particle> throwParticles = new ArrayList<>();
    private final Supplier<Color> colorSupplier;
    private long lastUpdateTime = System.nanoTime();

    public DecorativeParticlesFeature() {
        this(ColorUtils::getGlobalColor);
    }

    public DecorativeParticlesFeature(Supplier<Color> colorSupplier) {
        this.colorSupplier = colorSupplier == null ? ColorUtils::getGlobalColor : colorSupplier;
    }

    public List<Setting<?>> getSettings() {
        return List.of(spawnOnAttack, spawnOnThrow, mode, particleSize);
    }

    public void reset() {
        attackParticles.clear();
        throwParticles.clear();
        lastUpdateTime = System.nanoTime();
    }

    public void onAttack(Entity target) {
        if (!spawnOnAttack.getValue() || target == null) return;

        for (int i = 0; i < ATTACK_BURST_COUNT; i++) {
            Vec3d spawnPos = new Vec3d(
                    target.getX(),
                    target.getY() + randomBetween(0.0, target.getHeight()),
                    target.getZ()
            );
            Vec3d velocity = new Vec3d(
                    randomBetween(-6.0, 6.0),
                    randomBetween(-6.0, 6.0),
                    randomBetween(-6.0, 6.0)
            );
            spawnParticle(attackParticles, spawnPos, velocity);
        }
        trimToLimit(attackParticles, MAX_TARGET_PARTICLES);
    }

    public void onTick() {
        removeExpiredParticles(attackParticles, TARGET_LIFESPAN_MS);
        removeExpiredParticles(throwParticles, THROW_LIFESPAN_MS);

        if (!spawnOnThrow.getValue() || mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!isProjectile(entity)) continue;
            boolean moving = entity.lastX != entity.getX() || entity.lastY != entity.getY() || entity.lastZ != entity.getZ();
            if (!moving) continue;

            Vec3d pos = entity.getEntityPos();
            for (int i = 0; i < THROW_BURST_COUNT; i++) {
                Vec3d spawnPos = new Vec3d(
                        pos.x + randomBetween(-0.2, 0.2),
                        pos.y + randomBetween(-0.2, 0.2),
                        pos.z + randomBetween(-0.2, 0.2)
                );
                Vec3d velocity = new Vec3d(
                        randomBetween(-1.0, 1.0),
                        randomBetween(-0.3, 0.3),
                        randomBetween(-1.0, 1.0)
                );
                spawnParticle(throwParticles, spawnPos, velocity);
            }
        }
        trimToLimit(throwParticles, MAX_THROW_PARTICLES);
    }

    public void render(MatrixStack matrices) {
        if (mc.world == null || mc.player == null) return;

        long now = System.nanoTime();
        double deltaTime = (now - lastUpdateTime) / 1.0E9;
        lastUpdateTime = now;
        if (!Double.isFinite(deltaTime) || deltaTime <= 0.0) {
            deltaTime = 0.0;
        } else {
            deltaTime = Math.min(deltaTime, 0.2);
        }

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        renderParticles(matrices, cameraPos, attackParticles, TARGET_FADE_IN_MS, TARGET_FADE_OUT_START_MS, TARGET_LIFESPAN_MS, deltaTime);
        renderParticles(matrices, cameraPos, throwParticles, THROW_FADE_IN_MS, THROW_FADE_OUT_START_MS, THROW_LIFESPAN_MS, deltaTime);
    }

    private void renderParticles(
            MatrixStack matrices,
            Vec3d cameraPos,
            List<Particle> particles,
            long fadeInMs,
            long fadeOutStartMs,
            long lifespanMs,
            double deltaTime
    ) {
        if (particles.isEmpty()) return;

        long nowMs = System.currentTimeMillis();
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.update(deltaTime);

            long age = nowMs - particle.spawnTime;
            if (age > lifespanMs) {
                iterator.remove();
                continue;
            }

            float alpha = computeAlpha(age, fadeInMs, fadeOutStartMs);
            if (alpha <= 0.0f) continue;
            // DDA-трейс до 512 getBlockState на частицу каждый кадр — кэшируем LOS на ~100 мс.
            if (nowMs - particle.losCheckedAt >= 100L) {
                particle.losResult = ParticleVisibilityHelper.hasLineOfSight(cameraPos, particle.position);
                particle.losCheckedAt = nowMs;
            }
            if (!particle.losResult) continue;

            int rgb = particle.color & 0x00FFFFFF;
            int a = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
            int drawColor = (a << 24) | rgb;

            if (particle.type == ParticleMode.Cube) {
                float cubeSize = Math.max(0.04f, particle.size * 1.8f);
                Color fillColor = new Color(drawColor, true);
                Color outlineColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), Math.min(255, a + 24));
                Render3D.renderCube(matrices, particle.position, cubeSize, true, fillColor, true, outlineColor);
                continue;
            }

            float halfSize = particle.size;
            matrices.push();
            matrices.translate(
                    particle.position.x - cameraPos.x,
                    particle.position.y - cameraPos.y,
                    particle.position.z - cameraPos.z
            );
            matrices.multiply(mc.getEntityRenderDispatcher().camera.getRotation());
            if (particle.type.rotatable) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(particle.rotate));
            }

            MatrixStack.Entry entry = matrices.peek().copy();
            Render3D.drawTexture(
                    entry,
                    particle.type.texture,
                    -halfSize,
                    -halfSize,
                    halfSize * 2.0f,
                    halfSize * 2.0f,
                    drawColor,
                    true,
                    particle.type.additive
            );
            if (particle.type == ParticleMode.Bloom || particle.type == ParticleMode.Firefly) {
                Render3D.drawTexture(
                        entry,
                        particle.type.texture,
                        -halfSize / 2.0f,
                        -halfSize / 2.0f,
                        halfSize,
                        halfSize,
                        drawColor,
                        true,
                        true
                );
            }
            matrices.pop();
        }
    }

    private void spawnParticle(List<Particle> list, Vec3d position, Vec3d velocity) {
        ParticleMode selectedMode = mode.getValue();
        float baseSize = 0.05f + particleSize.getValue() * 0.2f;
        int color = getParticleColor().getRGB();
        int rotation = Math.round((float) randomBetween(0.0, 360.0) / 15.0f) * 15;

        list.add(new Particle(
                selectedMode,
                position.add(0.0, baseSize, 0.0),
                velocity,
                rotation,
                color,
                baseSize,
                0.2
        ));
    }

    private Color getParticleColor() {
        Color color = colorSupplier.get();
        return color == null ? ColorUtils.getGlobalColor() : color;
    }

    private void trimToLimit(List<Particle> particles, int maxSize) {
        int overflow = particles.size() - Math.max(0, maxSize);
        if (overflow > 0) {
            particles.subList(0, overflow).clear();
        }
    }

    private void removeExpiredParticles(List<Particle> particles, long lifespanMs) {
        long now = System.currentTimeMillis();
        particles.removeIf(particle -> (now - particle.spawnTime) > lifespanMs);
    }

    private boolean isProjectile(Entity entity) {
        if (entity instanceof TridentEntity trident) {
            return !trident.isOnGround();
        }
        return entity instanceof EnderPearlEntity || entity instanceof ArrowEntity;
    }

    private float computeAlpha(long ageMs, long fadeInMs, long fadeOutStartMs) {
        if (ageMs < 0) return 0.0f;
        if (ageMs < fadeInMs) {
            return easeOutQuad((float) ageMs / (float) fadeInMs);
        }
        if (ageMs >= fadeOutStartMs) {
            float t = (float) (ageMs - fadeOutStartMs) / (float) FADE_DURATION_MS;
            return 1.0f - easeOutQuad(t);
        }
        return 1.0f;
    }

    private static float easeOutQuad(float t) {
        float clamped = MathHelper.clamp(t, 0.0f, 1.0f);
        return clamped * (2.0f - clamped);
    }

    private static double randomBetween(double min, double max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private final class Particle {
        private final ParticleMode type;
        private Vec3d position;
        private Vec3d velocity;
        private final int rotate;
        private final int color;
        private final float size;
        private final double speedMultiplier;
        private final long spawnTime;
        private boolean losResult;
        private long losCheckedAt;
        private BlockPos lastBlockPos;

        private Particle(ParticleMode type, Vec3d position, Vec3d velocity, int rotate, int color, float size, double speedMultiplier) {
            this.type = type;
            this.position = position;
            this.velocity = velocity.multiply(0.05);
            this.rotate = rotate;
            this.color = color;
            this.size = size;
            this.speedMultiplier = speedMultiplier;
            this.spawnTime = System.currentTimeMillis();
        }

        private void update(double deltaTime) {
            if (mc.world != null) {
                double velMagSq = velocity.lengthSquared();
                if (velMagSq > 1.0E-4) {
                    // Гейт по LOOK-AHEAD блоку (position+velocity), а не по блоку самой частицы:
                    // тестовые точки входят в твёрдый сосед РАНЬШЕ, чем меняется блок частицы, и с
                    // гейтом по позиции отскок срабатывал уже изнутри стены (реверс всех трёх осей).
                    BlockPos blockPos = BlockPos.ofFloored(
                            position.x + velocity.x, position.y + velocity.y, position.z + velocity.z);
                    if (!blockPos.equals(lastBlockPos)) {
                        lastBlockPos = blockPos;
                        if (isSolid(position.x, position.y, position.z + velocity.z)) {
                            velocity = new Vec3d(velocity.x * 1.35, velocity.y * 1.35, velocity.z * -1.1);
                        }
                        if (isSolid(position.x, position.y + velocity.y, position.z)) {
                            velocity = new Vec3d(velocity.x * 1.35, velocity.y * -1.1, velocity.z * 1.35);
                        }
                        if (isSolid(position.x + velocity.x, position.y, position.z)) {
                            velocity = new Vec3d(velocity.x * -1.1, velocity.y * 1.35, velocity.z * 1.35);
                        }
                    }
                }

                double friction = Math.pow(0.999, deltaTime * 60.0);
                velocity = velocity.multiply(friction).subtract(0.0, 2.0E-5, 0.0);
            }

            double deltaMultiplier = deltaTime * 60.0 * speedMultiplier;
            position = position.add(velocity.multiply(deltaMultiplier));
        }

        private boolean isSolid(double x, double y, double z) {
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
        }
    }

    private enum ParticleMode implements Nameable {
        Bloom("settings.particles.mode.bloom", BLOOM_TEXTURE, true, true),
        Star("settings.particles.mode.star", STAR_TEXTURE, false, true),
        Snow("settings.particles.mode.snow", SNOW_TEXTURE, false, true),
        Firefly("settings.particles.mode.firefly", FIREFLY_TEXTURE, true, true),
        Dollar("settings.particles.mode.dollar", DOLLAR_TEXTURE, false, true),
        Cube("settings.particles.mode.cube", null, false, false);

        private final String name;
        private final Identifier texture;
        private final boolean additive;
        private final boolean rotatable;

        ParticleMode(String name, Identifier texture, boolean additive, boolean rotatable) {
            this.name = name;
            this.texture = texture;
            this.additive = additive;
            this.rotatable = rotatable;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
