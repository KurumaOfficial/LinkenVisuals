package ru.method.linkenvisuals.modules.impl.render.particles;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public final class LegacyImpactParticlesFeature implements Wrapper {
    private static final Identifier CORE_TEXTURE = LinkenVisuals.id("client/particles/star.png");
    private static final Identifier GLOW_TEXTURE = LinkenVisuals.id("client/particles/firefly.png");
    private static final long COLOR_CACHE_TIME = 50L;
    private static final double MAX_RENDER_DISTANCE_SQ = 100 * 100;
    private static final long MAX_LIFETIME_MS = 2800L;
    private static final double GRAVITY = 0.04;
    private static final double AIR_FRICTION = 0.92;
    private static final double GROUND_FRICTION = 0.65;
    private static final double BOUNCE_DAMPING = 0.55;
    private static final double SIDE_BOUNCE_DAMPING = 0.45;
    private static final double MIN_VELOCITY = 0.005;
    private static final double COLLISION_MARGIN = 0.12;

    private final NumberSetting count = new NumberSetting("settings.damageparticles.count", 30f, 10f, 60f, 1f);
    private final NumberSetting size = new NumberSetting("settings.damageparticles.size", 30f, 10f, 55f, 1f);
    private final List<Particle> particles = new ArrayList<>();
    private final Object particleLock = new Object();
    private final Supplier<Color> colorSupplier;

    private Color cachedGlobalColor;
    private long lastColorUpdate;

    public LegacyImpactParticlesFeature() {
        this(ColorUtils::getGlobalColor);
    }

    public LegacyImpactParticlesFeature(Supplier<Color> colorSupplier) {
        this.colorSupplier = colorSupplier == null ? ColorUtils::getGlobalColor : colorSupplier;
    }

    public List<Setting<?>> getSettings() {
        return List.of(count, size);
    }

    public void clear() {
        synchronized (particleLock) {
            particles.clear();
        }
    }

    public void spawn(Entity target) {
        if (!(target instanceof LivingEntity entity)) return;
        if (mc.player == null || entity == mc.player || !entity.isAlive()) return;

        Vec3d spawnPos = entity.getEntityPos().add(0.0, entity.getHeight() * 0.5f, 0.0);
        synchronized (particleLock) {
            int particleCount = count.getValue().intValue();
            for (int i = 0; i < particleCount; i++) {
                particles.add(new Particle(spawnPos));
            }
        }
    }

    public void render() {
        if (mc.player == null || mc.world == null) return;

        synchronized (particleLock) {
            Iterator<Particle> iterator = particles.iterator();
            Color globalColor = getParticleColor();
            long currentTime = System.currentTimeMillis();

            while (iterator.hasNext()) {
                Particle particle = iterator.next();
                if (particle.isExpired(currentTime, MAX_LIFETIME_MS)
                        || mc.player.getEntityPos().squaredDistanceTo(particle.pos) > MAX_RENDER_DISTANCE_SQ) {
                    iterator.remove();
                    continue;
                }

                particle.update(currentTime);
                if (!isInView(particle.pos)) continue;
                // DDA-трейс до 512 getBlockState на частицу каждый кадр — кэшируем LOS на ~100 мс.
                if (currentTime - particle.losCheckedAt >= 100L) {
                    particle.losResult = ParticleVisibilityHelper.hasLineOfSight(mc.gameRenderer.getCamera().getCameraPos(), particle.pos);
                    particle.losCheckedAt = currentTime;
                }
                if (!particle.losResult) continue;

                float lifeProgress = particle.getLifeProgress(currentTime, MAX_LIFETIME_MS);
                float alphaFactor = particle.getCurrentAlpha(currentTime, MAX_LIFETIME_MS);
                if (alphaFactor <= 0.01f) {
                    iterator.remove();
                    continue;
                }

                renderParticle(particle, globalColor, alphaFactor, lifeProgress);
            }
        }
    }

    private boolean isInView(Vec3d pos) {
        return ru.method.linkenvisuals.utils.world.WorldUtils.isBoxVisible(
                new Box(pos.subtract(0.5, 0.5, 0.5), pos.add(0.5, 0.5, 0.5))
        );
    }

    private Color getCachedGlobalColor() {
        long currentTime = System.currentTimeMillis();
        if (cachedGlobalColor == null || currentTime - lastColorUpdate > COLOR_CACHE_TIME) {
            cachedGlobalColor = ColorUtils.getGlobalColor();
            lastColorUpdate = currentTime;
        }
        return cachedGlobalColor;
    }

    private Color getParticleColor() {
        Color color = colorSupplier.get();
        return color == null ? getCachedGlobalColor() : color;
    }

    private void renderParticle(Particle particle, Color globalColor, float alphaFactor, float lifeProgress) {
        int alpha = MathHelper.clamp((int) (alphaFactor * 255f), 0, 255);
        if (alpha <= 3) return;

        float baseSize = MathHelper.clamp(size.getValue() / 55f, 0.15f, 1.2f);
        float finalSize = Math.max(0.04f, baseSize * particle.sizeMultiplier * (1.0f - lifeProgress * 0.58f));

        Color baseSource = new Color(globalColor.getRed(), globalColor.getGreen(), globalColor.getBlue(), alpha);
        Color glowColor = ColorUtils.fade(baseSource, new Color(255, 255, 255, alpha), 0.2f);
        Color coreColor = ColorUtils.fade(baseSource, new Color(255, 255, 255, alpha), 0.48f);
        int sparkAlpha = MathHelper.clamp((int) (alpha * (0.55f + particle.sparkle * 0.35f)), 0, 255);
        Color sparkleColor = new Color(255, 255, 255, sparkAlpha);

        Render3D.renderBillboardTexture(particle.pos, finalSize * (1.85f + particle.sparkle * 0.35f), GLOW_TEXTURE, glowColor.getRGB(), true, true);
        Render3D.renderBillboardTexture(particle.pos, finalSize * (1.02f + particle.sparkle * 0.08f), particle.texture, coreColor.getRGB(), true, true);
        Render3D.renderBillboardTexture(particle.pos, finalSize * 0.52f, CORE_TEXTURE, sparkleColor.getRGB(), true, true);
    }

    private final class Particle {
        private Vec3d pos;
        private Vec3d velocity;
        private final long spawnTime;
        private long lastUpdateTime;
        private final Identifier texture;
        private final float baseAlpha;
        private float alphaMultiplier = 1.0f;
        private boolean onGround;
        private final float sizeMultiplier;
        private final long lifespan;
        private final Vec3d origin;
        private final double targetRadius;
        private final float sparkle;
        private boolean losResult;
        private long losCheckedAt;

        private Particle(Vec3d playerPos) {
            this.origin = playerPos;
            double angle = MathUtils.randomFloat(0f, (float) (Math.PI * 2));

            double outwardSpeed = MathUtils.randomFloat(0.55f, 0.85f);
            double verticalSpeed = MathUtils.randomFloat(0.41f, 0.58f);
            double angleScatter = MathUtils.randomFloat(-0.2f, 0.2f);
            double upScatter = MathUtils.randomFloat(-0.12f, 0.16f);

            double finalX = Math.cos(angle + angleScatter) * outwardSpeed;
            double finalZ = Math.sin(angle + angleScatter) * outwardSpeed;
            double finalY = verticalSpeed + upScatter;

            this.pos = playerPos;
            this.velocity = new Vec3d(
                    finalX,
                    finalY,
                    finalZ
            ).add(
                    MathUtils.randomFloat(-0.04f, 0.04f),
                    0.0,
                    MathUtils.randomFloat(-0.04f, 0.04f)
            );

            this.spawnTime = System.currentTimeMillis();
            this.lastUpdateTime = this.spawnTime;
            this.texture = randomTexture();
            this.baseAlpha = MathUtils.randomFloat(0.75f, 1.0f);
            this.sizeMultiplier = MathUtils.randomFloat(0.85f, 1.15f);
            this.lifespan = MathUtils.randomInt(2100, 2800);
            this.targetRadius = MathUtils.randomFloat(2.2f, 3.15f);
            this.sparkle = MathUtils.randomFloat(0.0f, 1.0f);
        }

        private void update(long currentTime) {
            double deltaTicks = Math.max(0.01, (currentTime - lastUpdateTime) / 50.0);
            lastUpdateTime = currentTime;

            if (!onGround || velocity.y > 0.0) {
                velocity = velocity.add(0.0, -GRAVITY * deltaTicks, 0.0);
            }

            Vec3d motion = velocity.multiply(deltaTicks);
            Vec3d candidatePos = pos.add(motion);
            pos = handleCollisions(candidatePos);

            double drag = onGround ? GROUND_FRICTION : AIR_FRICTION;
            velocity = velocity.multiply(drag, drag, drag);

            if (!onGround) {
                Vec3d horizontal = pos.subtract(origin);
                Vec3d flat = new Vec3d(horizontal.x, 0.0, horizontal.z);
                double distance = flat.length();
                if (distance > 1.0E-3) {
                    double desired = Math.min(targetRadius, distance + 0.01);
                    double accelMag = MathHelper.clamp((desired - distance) * 0.08, -0.06, 0.06);
                    Vec3d accel = flat.normalize().multiply(accelMag).add(
                            MathUtils.randomFloat(-0.005f, 0.005f),
                            0.0,
                            MathUtils.randomFloat(-0.005f, 0.005f)
                    );
                    velocity = velocity.add(accel);
                } else {
                    velocity = velocity.add(
                            MathUtils.randomFloat(-0.02f, 0.02f),
                            0.0,
                            MathUtils.randomFloat(-0.02f, 0.02f)
                    );
                }
            } else {
                velocity = velocity.add(
                        MathUtils.randomFloat(-0.004f, 0.004f),
                        0.0,
                        MathUtils.randomFloat(-0.004f, 0.004f)
                );
            }

            if (onGround && velocity.lengthSquared() < MIN_VELOCITY * MIN_VELOCITY) {
                velocity = Vec3d.ZERO;
                alphaMultiplier *= 0.92f;
            } else if (!onGround) {
                alphaMultiplier = Math.min(1.0f, alphaMultiplier + 0.01f);
            }
        }

        private Vec3d handleCollisions(Vec3d newPos) {
            Vec3d updatedPos = newPos;

            BlockPos groundPos = BlockPos.ofFloored(updatedPos.x, updatedPos.y - COLLISION_MARGIN, updatedPos.z);
            BlockState groundState = mc.world.getBlockState(groundPos);
            if (!groundState.isAir()) {
                updatedPos = new Vec3d(updatedPos.x, groundPos.getY() + 1.0 + COLLISION_MARGIN, updatedPos.z);
                velocity = new Vec3d(velocity.x * GROUND_FRICTION, -velocity.y * BOUNCE_DAMPING, velocity.z * GROUND_FRICTION);
                onGround = true;
            } else {
                onGround = false;
            }

            if (Math.abs(velocity.x) > MIN_VELOCITY) {
                BlockPos sideX = BlockPos.ofFloored(updatedPos.x + Math.signum(velocity.x) * COLLISION_MARGIN, updatedPos.y, updatedPos.z);
                if (!mc.world.getBlockState(sideX).isAir()) {
                    updatedPos = new Vec3d(pos.x, updatedPos.y, updatedPos.z);
                    velocity = new Vec3d(-velocity.x * SIDE_BOUNCE_DAMPING, velocity.y, velocity.z);
                }
            }

            if (Math.abs(velocity.z) > MIN_VELOCITY) {
                BlockPos sideZ = BlockPos.ofFloored(updatedPos.x, updatedPos.y, updatedPos.z + Math.signum(velocity.z) * COLLISION_MARGIN);
                if (!mc.world.getBlockState(sideZ).isAir()) {
                    updatedPos = new Vec3d(updatedPos.x, updatedPos.y, pos.z);
                    velocity = new Vec3d(velocity.x, velocity.y, -velocity.z * SIDE_BOUNCE_DAMPING);
                }
            }

            return updatedPos;
        }

        private float getLifeProgress(long currentTime, long maxLifetime) {
            long lifetime = Math.min(maxLifetime, lifespan);
            return MathHelper.clamp((currentTime - spawnTime) / (float) lifetime, 0f, 1f);
        }

        private float getCurrentAlpha(long currentTime, long maxLifetime) {
            float life = getLifeProgress(currentTime, maxLifetime);
            float fade = 1.0f - life;
            if (onGround) fade *= 0.85f;
            return MathHelper.clamp(fade * baseAlpha * alphaMultiplier, 0f, 1f);
        }

        private boolean isExpired(long currentTime, long maxLifetime) {
            return currentTime - spawnTime > Math.min(maxLifetime, lifespan) || alphaMultiplier <= 0.02f;
        }

        private Identifier randomTexture() {
            return MathUtils.randomInt(0, 1) == 0 ? CORE_TEXTURE : GLOW_TEXTURE;
        }
    }
}
