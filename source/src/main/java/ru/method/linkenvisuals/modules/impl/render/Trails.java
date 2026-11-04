package ru.method.linkenvisuals.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.math.TimerUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class Trails extends Module {

    private static final long FADE_WINDOW_MS = 250L;
    private static final double MAX_DISTANCE_FROM_PLAYER = 100.0;

    private final NumberSetting delay = new NumberSetting("settings.trails.length", 1500f, 500f, 3000f, 1f);
    private final BooleanSetting firstPerson = new BooleanSetting("settings.trails.show_first_person", true);

    private final List<Particle3D> particles = new ArrayList<>();
    private final Identifier bloomTexture = LinkenVisuals.id("client/xuynya/ghost_bloom.png");

    private double lastPlayerX;
    private double lastPlayerY;
    private double lastPlayerZ;

    public Trails() {
        super("Trails", Category.Visuals);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        clear();
        if (!fullNullCheck()) {
            lastPlayerX = mc.player.getX();
            lastPlayerY = mc.player.getY();
            lastPlayerZ = mc.player.getZ();
        }
    }

    @Override
    public void onDisable() {
        clear();
        super.onDisable();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            clear();
            return;
        }

        if (lastPlayerX != mc.player.getX() || lastPlayerY != mc.player.getY() || lastPlayerZ != mc.player.getZ()) {
            double distance = -(mc.player.getWidth() / 2.0f);
            double yawRad = Math.toRadians(mc.player.bodyYaw);
            double xOffset = -Math.sin(yawRad) * distance;
            double zOffset = Math.cos(yawRad) * distance;

            Vec3d spawnPos = new Vec3d(
                    mc.player.getX() + xOffset,
                    mc.player.getY() + (mc.player.getHeight() * 0.4f),
                    mc.player.getZ() + zOffset
            );

            Vec3d playerVelocity = mc.player.getVelocity();
            Vec3d velocity = new Vec3d(playerVelocity.x, 0.0, playerVelocity.z).multiply(1.5f + Math.random());
            particles.add(new Particle3D(spawnPos, velocity));
        }

        lastPlayerX = mc.player.getX();
        lastPlayerY = mc.player.getY();
        lastPlayerZ = mc.player.getZ();

        long delayMs = Math.max(1L, delay.getValue().longValue());
        MutableVec3d playerPos = new MutableVec3d(mc.player.getEntityPos());
        for (Particle3D particle : particles) {
            particle.update();
            particle.updateAlpha(delayMs);
        }

        particles.removeIf(particle -> particle.isExpired(delayMs) || particle.position.distance(playerPos) >= MAX_DISTANCE_FROM_PLAYER);
    }

    @EventHandler
    private void onRender3D(EventRender3D.Game event) {
        if (!firstPerson.getValue() && mc.options.getPerspective().isFirstPerson()) {
            return;
        }
        if (particles.isEmpty()) return;

        int baseColor = ColorUtils.getGlobalColor().getRGB();
        for (int i = 0; i < particles.size(); i++) {
            Particle3D particle = particles.get(i);
            float alpha = particle.alpha;
            if (alpha <= 0.0f) continue;

            // ВАЖНО: рендер НЕ мутирует позиции частиц. Раньше здесь каждый кадр прежняя частица
            // лерпилась к текущей (новее) → след схлопывался к голове и выглядел «сломанной линией».
            MutableVec3d prevPos = i > 0 ? particles.get(i - 1).position : particle.position;
            float bloomSize = (float) clamp(0.25, 1.0, particle.position.distance(prevPos) * 4.0);
            int color = withAlpha(baseColor, Math.round(alpha * 255.0f));
            Render3D.renderBillboardTexture(particle.position.toVec3d(), bloomSize * 2.0f, bloomTexture, color, true, true);
        }
    }

    private void clear() {
        particles.clear();
    }

    private static int withAlpha(int color, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (clamped << 24);
    }

    private static double lerp(double start, double end, double factor) {
        return start + factor * (end - start);
    }

    private static double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class MutableVec3d {
        private double x;
        private double y;
        private double z;

        private MutableVec3d(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private MutableVec3d(Vec3d vec) {
            this(vec.x, vec.y, vec.z);
        }

        private void set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private double distance(MutableVec3d other) {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private Vec3d toVec3d() {
            return new Vec3d(x, y, z);
        }
    }

    private static final class Particle3D {
        private final TimerUtils timer = new TimerUtils();
        private final MutableVec3d position;
        private final MutableVec3d delta;
        private float alpha;

        private Particle3D(Vec3d position, Vec3d velocity) {
            this.position = new MutableVec3d(position);
            this.delta = new MutableVec3d(velocity.x * 0.01, velocity.y * 0.01, velocity.z * 0.01);
            this.alpha = 0.0f;
        }

        private void update() {
            if (mc.world == null) return;

            Block blockZ = getBlock(position.x, position.y, position.z + delta.z);
            if (isSolid(blockZ)) {
                delta.z *= -0.8;
            }

            Block blockY = getBlock(position.x, position.y + delta.y, position.z);
            if (isSolid(blockY)) {
                delta.x *= 0.999f;
                delta.z *= 0.999f;
                delta.y *= -0.7;
            }

            Block blockX = getBlock(position.x + delta.x, position.y, position.z);
            if (isSolid(blockX)) {
                delta.x *= -0.8;
            }

            updateWithoutPhysics();
        }

        private void updateAlpha(long delayMs) {
            long age = timer.getElapsed();
            if (age < FADE_WINDOW_MS) {
                alpha = age / (float) FADE_WINDOW_MS;
                return;
            }
            long fadeStart = Math.max(0L, delayMs - FADE_WINDOW_MS);
            if (age > fadeStart) {
                alpha = (delayMs - age) / (float) FADE_WINDOW_MS;
            } else {
                alpha = 1.0f;
            }
            alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        }

        private boolean isExpired(long delayMs) {
            return timer.passedMs(delayMs);
        }

        private Block getBlock(double x, double y, double z) {
            if (mc.world == null) return Blocks.AIR;
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            return mc.world.getBlockState(pos).getBlock();
        }

        private boolean isSolid(Block block) {
            return !(block instanceof AirBlock)
                    && !(block instanceof PlantBlock)
                    && !(block instanceof ButtonBlock)
                    && !(block instanceof TorchBlock)
                    && !(block instanceof LeverBlock)
                    && !(block instanceof PressurePlateBlock)
                    && !(block instanceof CarpetBlock)
                    && !(block instanceof FluidBlock);
        }

        private void updateWithoutPhysics() {
            position.x += delta.x;
            position.y += delta.y;
            position.z += delta.z;
            delta.x /= 0.999999f;
            delta.y = 0.0;
            delta.z /= 0.999999f;
        }
    }
}
