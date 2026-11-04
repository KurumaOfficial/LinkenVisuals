package ru.method.linkenvisuals.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.render.particles.ParticleVisibilityHelper;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class Particles extends Module {
    private static final Identifier BLOOM_TEXTURE = LinkenVisuals.id("client/xuynya/ghost_bloom.png");
    private static final Identifier STAR_TEXTURE = LinkenVisuals.id("client/particles/star.png");
    private static final Identifier SNOW_TEXTURE = LinkenVisuals.id("client/particles/snow.png");
    private static final Identifier FIREFLY_TEXTURE = LinkenVisuals.id("client/particles/firefly.png");
    private static final Identifier DOLLAR_TEXTURE = LinkenVisuals.id("client/particles/dollar.png");
    private static final long COLOR_CACHE_TIME = 50L;
    private static final int MAX_ADD_PER_TICK = 36;
    private static final int MAX_SPAWN_ATTEMPTS = 12;
    private static final int MIN_LIFE_TICKS = 50;
    private static final int MAX_LIFE_TICKS = 150;
    private static final double DRAG = 0.99;
    private static final float SPAWN_RATE = 42.0f;
    private static final double SPAWN_RADIUS = 18.0;
    private static final float PARTICLE_SPEED = 1.0f;
    private static final float PARTICLE_SIZE = 0.22f;
    private static final float CUBE_SIZE_MULTIPLIER = 1.45f;
    private static final int CUBE_GLOW_SEGMENTS = 16;
    private static final float CUBE_LINE_WIDTH = 2.0f;

    private final NumberSetting maxParticles = new NumberSetting("settings.particles.max", 600.0f, 0.0f, 15000.0f, 50.0f);
    private final NumberSetting particleSize = new NumberSetting("settings.particles.size", 0.25f, 0.04f, 2.5f, 0.01f);
    private final ColorSetting particleColor = new ColorSetting("settings.particles.color", ColorUtils.getGlobalColor());
    private final EnumSetting<ParticleMode> mode = new EnumSetting<>("settings.particles.mode", ParticleMode.Bloom);
    private final BooleanSetting cubeGlow = new BooleanSetting("settings.particles.cube.glow", true, () -> mode.getValue() == ParticleMode.Cube);
    private final NumberSetting cubeGlowSize = new NumberSetting(
            "settings.particles.cube.glow_size",
            2.0f,
            0.5f,
            4.0f,
            0.1f,
            () -> mode.getValue() == ParticleMode.Cube && cubeGlow.getValue()
    );
    private final NumberSetting cubeRotateSpeed = new NumberSetting(
            "settings.particles.cube.rotate_speed",
            2.0f,
            0.5f,
            6.0f,
            0.1f,
            () -> mode.getValue() == ParticleMode.Cube
    );

    private final List<Particle> particles = new ArrayList<>();
    private final Object particleLock = new Object();
    private final Random random = new Random();

    private double spawnAccumulator;
    private Color cachedGlobalColor;
    private long lastColorUpdate;
    private int tickCounter;

    public Particles() {
        super("Particles", Category.Visuals);
        getSettings().add(maxParticles);
        getSettings().add(particleSize);
        getSettings().add(particleColor);
        getSettings().add(mode);
        getSettings().add(cubeGlow);
        getSettings().add(cubeGlowSize);
        getSettings().add(cubeRotateSpeed);
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;

        tickCounter++;
        spawnAccumulator += SPAWN_RATE / 20.0;
        int spawnCount = (int) spawnAccumulator;
        if (spawnCount > 0) {
            spawnAccumulator -= spawnCount;
        }

        int maxCount = Math.max(0, Math.round(maxParticles.getValue()));
        Vec3d playerPos = mc.player.getEntityPos();
        int spawnColor = getSpawnColor();
        float rotateSpeed = Math.max(0.0f, cubeRotateSpeed.getValue());

        synchronized (particleLock) {
            if (particles.size() > maxCount) {
                particles.subList(0, particles.size() - maxCount).clear();
            }

            if (spawnCount > 0 && particles.size() < maxCount) {
                int available = maxCount - particles.size();
                spawnCount = Math.min(spawnCount, Math.min(available, MAX_ADD_PER_TICK));
                for (int i = 0; i < spawnCount; i++) {
                    Vec3d spawnPos = randomSpawnPos(playerPos, SPAWN_RADIUS);
                    particles.add(new Particle(spawnPos, PARTICLE_SPEED, PARTICLE_SIZE, spawnColor, rotateSpeed));
                }
            }

            double maxDistance = Math.max(60.0, SPAWN_RADIUS * 3.0);
            double maxDistanceSq = maxDistance * maxDistance;
            Iterator<Particle> iterator = particles.iterator();
            while (iterator.hasNext()) {
                Particle particle = iterator.next();
                particle.tick();
                if (particle.isExpired()
                        || mc.player.getEntityPos().squaredDistanceTo(particle.pos) > maxDistanceSq
                        || !isSpaceStillClear(particle)
                        || isInsidePlayer(particle.pos)) {
                    iterator.remove();
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;

        float tickDelta = event.getTickCounter().getTickProgress(true);
        float resolvedParticleSize = Math.max(0.0f, particleSize.getValue());
        ParticleMode currentMode = mode.getValue();
        boolean cubeMode = currentMode == ParticleMode.Cube;
        boolean glowEnabled = cubeMode && cubeGlow.getValue();
        float glowScale = cubeMode ? Math.max(0.0f, cubeGlowSize.getValue()) : 0.0f;
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = event.getMatrixStack();

        synchronized (particleLock) {
            for (Particle particle : particles) {
                Vec3d renderPos = particle.getRenderPos(tickDelta);
                if (!isInView(renderPos)) {
                    continue;
                }
                // DDA-трейс до 512 getBlockState — слишком дорого на каждую частицу каждый кадр;
                // кэшируем результат на 2 тика (частицы медленные, задержка незаметна).
                if (tickCounter - particle.losTick >= 2) {
                    particle.losResult = ParticleVisibilityHelper.hasLineOfSight(cameraPos, renderPos);
                    particle.losTick = tickCounter;
                }
                if (!particle.losResult) {
                    continue;
                }

                int alpha = particle.getAlpha(tickDelta);
                if (alpha <= 3) {
                    continue;
                }

                float lifeProgress = particle.getLifeProgress(tickDelta);
                float finalSize = Math.max(0.04f, resolvedParticleSize * particle.size * (1.0f - lifeProgress * 0.25f));
                if (cubeMode) {
                    finalSize *= CUBE_SIZE_MULTIPLIER;
                }
                int finalColor = (alpha << 24) | (particle.color & 0x00FFFFFF);

                if (cubeMode) {
                    renderCubeParticle(matrices, particle, renderPos, tickDelta, finalSize, finalColor, glowEnabled, glowScale);
                    continue;
                }

                Render3D.renderBillboardTexture(renderPos, finalSize, currentMode.getTexture(), finalColor, true, true);
            }
        }
    }

    @Override
    public void onDisable() {
        synchronized (particleLock) {
            particles.clear();
        }
        spawnAccumulator = 0.0;
        super.onDisable();
    }

    private boolean isInView(Vec3d pos) {
        // Box строим напрямую по координатам — без двух промежуточных Vec3d (subtract/add) на каждую частицу.
        return ru.method.linkenvisuals.utils.world.WorldUtils.isBoxVisible(
                new Box(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
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

    private int getSpawnColor() {
        Color color = particleColor.getValue();
        if (color == null) {
            color = getCachedGlobalColor();
        }
        int alpha = Math.max(0, Math.min(255, color.getAlpha()));
        return (alpha << 24) | (color.getRGB() & 0x00FFFFFF);
    }

    private Vec3d randomSpawnPos(Vec3d center, double spawnRadius) {
        int bottomY = mc.world.getBottomY();
        int topY = bottomY + mc.world.getHeight();
        double xzHalfRange = spawnRadius;
        double yHalfRange = spawnRadius * 0.5;

        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double x = center.x + (random.nextDouble() - 0.5) * (xzHalfRange * 2.0);
            double y = center.y + (random.nextDouble() - 0.5) * (yHalfRange * 2.0);
            double z = center.z + (random.nextDouble() - 0.5) * (xzHalfRange * 2.0);
            Vec3d candidate = new Vec3d(x, MathHelper.clamp(y, bottomY + 1.0, topY - 2.0), z);
            if (isSpawnSpaceClear(candidate) && !isInsidePlayer(candidate)) {
                return candidate;
            }
        }

        double fallbackY = MathHelper.clamp(center.y + 1.5, bottomY + 1.0, topY - 2.0);
        Vec3d fallback = new Vec3d(center.x, fallbackY, center.z);
        if (isSpawnSpaceClear(fallback) && !isInsidePlayer(fallback)) {
            return fallback;
        }
        return center;
    }

    // Полная проверка (isSpaceEmpty + getBlockState) — только при смене блока частицы:
    // скорость ~0.02/тик, блок меняется раз в десятки тиков.
    private boolean isSpaceStillClear(Particle particle) {
        // Ключ кэша — по МИНИМАЛЬНОМУ углу бокса ±0.15, а не по блоку центра: бокс пересекает
        // соседние блоки, и с ключом по центру частица могла клипаться в стену, пока её floored-блок
        // не менялся. Плюс принудительная перепроверка раз в 10 тиков — реакция на постройку блока.
        BlockPos cornerPos = BlockPos.ofFloored(particle.pos.subtract(0.15, 0.15, 0.15));
        if (cornerPos.equals(particle.lastBlockPos) && (particle.life % 10) != 0) {
            return true;
        }
        if (isSpawnSpaceClear(particle.pos)) {
            particle.lastBlockPos = cornerPos;
            return true;
        }
        return false;
    }

    private boolean isSpawnSpaceClear(Vec3d pos) {
        if (mc.world == null) return false;

        Box box = new Box(
                pos.x - 0.15, pos.y - 0.15, pos.z - 0.15,
                pos.x + 0.15, pos.y + 0.15, pos.z + 0.15
        );
        BlockPos blockPos = BlockPos.ofFloored(pos);
        return mc.world.isSpaceEmpty(box) && mc.world.getBlockState(blockPos).isAir();
    }

    private boolean isInsidePlayer(Vec3d pos) {
        if (mc.player == null) return false;
        return mc.player.getBoundingBox().expand(0.05).contains(pos);
    }

    private void renderCubeParticle(MatrixStack matrices, Particle particle, Vec3d renderPos, float tickDelta, float size, int color, boolean glowEnabled, float glowScale) {
        float alphaFactor = ((color >>> 24) & 0xFF) / 255.0f;
        float halfSize = Math.max(0.01f, size * 0.5f * alphaFactor);
        if (halfSize <= 0.0f) return;

        Color renderColor = new Color(color, true);
        if (glowEnabled) {
            renderCubeGlow(matrices, particle, renderPos, tickDelta, halfSize * glowScale, renderColor);
        }
        renderWireCube(matrices, particle, renderPos, tickDelta, halfSize, renderColor);
    }

    private void renderCubeGlow(MatrixStack matrices, Particle particle, Vec3d renderPos, float tickDelta, float size, Color color) {
        if (size <= 0.0f) return;

        Color glowColor = new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                MathHelper.clamp(Math.round(color.getAlpha() * 0.32f), 0, 255)
        );
        renderWireCube(matrices, particle, renderPos, tickDelta, size, glowColor, CUBE_LINE_WIDTH + 1.0f);
    }

    private void renderWireCube(MatrixStack matrices, Particle particle, Vec3d renderPos, float tickDelta, float size, Color color) {
        renderWireCube(matrices, particle, renderPos, tickDelta, size, color, CUBE_LINE_WIDTH);
    }

    private void renderWireCube(MatrixStack matrices, Particle particle, Vec3d renderPos, float tickDelta, float size, Color color, float lineWidth) {
        float s = size;
        // Поворот одинаков для всех 8 вершин куба — строим матрицу один раз на куб
        // (раньше cubeVertex строил новый Matrix4f + 3 тригонометрии на КАЖДУЮ вершину = 24 на куб).
        Matrix4f rot = new Matrix4f()
                .rotateX((float) Math.toRadians(particle.getRotX(tickDelta)))
                .rotateY((float) Math.toRadians(particle.getRotY(tickDelta)))
                .rotateZ((float) Math.toRadians(particle.getRotZ(tickDelta)));
        Vec3d v000 = cubeCorner(rot, renderPos, -s, -s, -s);
        Vec3d v100 = cubeCorner(rot, renderPos, s, -s, -s);
        Vec3d v101 = cubeCorner(rot, renderPos, s, -s, s);
        Vec3d v001 = cubeCorner(rot, renderPos, -s, -s, s);
        Vec3d v010 = cubeCorner(rot, renderPos, -s, s, -s);
        Vec3d v110 = cubeCorner(rot, renderPos, s, s, -s);
        Vec3d v111 = cubeCorner(rot, renderPos, s, s, s);
        Vec3d v011 = cubeCorner(rot, renderPos, -s, s, s);

        Render3D.drawLine(matrices, v000, v100, color, lineWidth, true);
        Render3D.drawLine(matrices, v100, v101, color, lineWidth, true);
        Render3D.drawLine(matrices, v101, v001, color, lineWidth, true);
        Render3D.drawLine(matrices, v001, v000, color, lineWidth, true);

        Render3D.drawLine(matrices, v010, v110, color, lineWidth, true);
        Render3D.drawLine(matrices, v110, v111, color, lineWidth, true);
        Render3D.drawLine(matrices, v111, v011, color, lineWidth, true);
        Render3D.drawLine(matrices, v011, v010, color, lineWidth, true);

        Render3D.drawLine(matrices, v000, v010, color, lineWidth, true);
        Render3D.drawLine(matrices, v100, v110, color, lineWidth, true);
        Render3D.drawLine(matrices, v101, v111, color, lineWidth, true);
        Render3D.drawLine(matrices, v001, v011, color, lineWidth, true);

        int diagAlpha = MathHelper.clamp(color.getAlpha() / 2, 0, 255);
        Color diagColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), diagAlpha);
        Render3D.drawLine(matrices, v000, v111, diagColor, lineWidth, true);
        Render3D.drawLine(matrices, v100, v011, diagColor, lineWidth, true);
        Render3D.drawLine(matrices, v001, v110, diagColor, lineWidth, true);
        Render3D.drawLine(matrices, v101, v010, diagColor, lineWidth, true);
    }

    private Vec3d cubeCorner(Matrix4f rotation, Vec3d origin, float x, float y, float z) {
        Vector3f vec = new Vector3f(x, y, z);
        rotation.transformPosition(vec);
        return origin.add(vec.x, vec.y, vec.z);
    }

    private enum ParticleMode implements Nameable {
        Bloom("settings.particles.mode.bloom", BLOOM_TEXTURE),
        Star("settings.particles.mode.star", STAR_TEXTURE),
        Snow("settings.particles.mode.snow", SNOW_TEXTURE),
        Firefly("settings.particles.mode.firefly", FIREFLY_TEXTURE),
        Dollar("settings.particles.mode.dollar", DOLLAR_TEXTURE),
        Cube("settings.particles.mode.cube", BLOOM_TEXTURE);

        private final String name;
        private final Identifier texture;

        ParticleMode(String name, Identifier texture) {
            this.name = name;
            this.texture = texture;
        }

        @Override
        public String getName() {
            return name;
        }

        public Identifier getTexture() {
            return texture;
        }
    }

    private final class Particle {
        private Vec3d prevPos;
        private Vec3d pos;
        private Vec3d velocity;
        private final int color;
        private final float size;
        private final int maxLife;
        private int life;
        private float prevRotX;
        private float prevRotY;
        private float prevRotZ;
        private float rotX;
        private float rotY;
        private float rotZ;
        private final float rotSpeedX;
        private final float rotSpeedY;
        private final float rotSpeedZ;
        private boolean losResult;
        private int losTick = Integer.MIN_VALUE / 2;
        private BlockPos lastBlockPos;

        private Particle(Vec3d spawnPos, float particleSpeed, float particleSize, int color, float rotateSpeed) {
            this.prevPos = spawnPos;
            this.pos = spawnPos;
            this.color = color;
            this.size = particleSize;
            this.life = this.maxLife = MIN_LIFE_TICKS + random.nextInt(Math.max(1, MAX_LIFE_TICKS - MIN_LIFE_TICKS + 1));

            double velX = (random.nextDouble() - 0.5) * 0.02 * particleSpeed;
            double velY = (random.nextDouble() * 0.03 + 0.01) * particleSpeed;
            double velZ = (random.nextDouble() - 0.5) * 0.02 * particleSpeed;
            this.velocity = new Vec3d(velX, velY, velZ);

            this.rotX = random.nextFloat() * 360.0f - 180.0f;
            this.rotY = random.nextFloat() * 360.0f - 180.0f;
            this.rotZ = random.nextFloat() * 360.0f - 180.0f;
            this.prevRotX = rotX;
            this.prevRotY = rotY;
            this.prevRotZ = rotZ;

            float speed = Math.max(0.0f, rotateSpeed);
            this.rotSpeedX = (random.nextFloat() * 2.0f - 1.0f) * speed;
            this.rotSpeedY = (random.nextFloat() * 2.0f - 1.0f) * speed;
            this.rotSpeedZ = (random.nextFloat() * 2.0f - 1.0f) * speed;
        }

        private void tick() {
            prevPos = pos;
            pos = pos.add(velocity);
            velocity = velocity.multiply(DRAG);
            life--;

            prevRotX = rotX;
            prevRotY = rotY;
            prevRotZ = rotZ;
            rotX += rotSpeedX;
            rotY += rotSpeedY;
            rotZ += rotSpeedZ;
        }

        private boolean isExpired() {
            return life <= 0;
        }

        private int getAlpha(float tickDelta) {
            float ratio = MathHelper.clamp((life - tickDelta) / (float) maxLife, 0.0f, 1.0f);
            int baseAlpha = (color >>> 24) & 0xFF;
            return MathHelper.clamp((int) Math.round(baseAlpha * Math.sin(ratio * Math.PI)), 0, 255);
        }

        private float getLifeProgress(float tickDelta) {
            return 1.0f - MathHelper.clamp((life - tickDelta) / (float) maxLife, 0.0f, 1.0f);
        }

        private Vec3d getRenderPos(float tickDelta) {
            double t = MathHelper.clamp(tickDelta, 0.0f, 1.0f);
            return prevPos.lerp(pos, t);
        }

        private float getRotX(float tickDelta) {
            return MathHelper.lerp(tickDelta, prevRotX, rotX);
        }

        private float getRotY(float tickDelta) {
            return MathHelper.lerp(tickDelta, prevRotY, rotY);
        }

        private float getRotZ(float tickDelta) {
            return MathHelper.lerp(tickDelta, prevRotZ, rotZ);
        }
    }
}
