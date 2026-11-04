package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.TrailsModule;
import dev.linkenvisuals.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Trails renderer: 1:1 port of Linken 1.21.4 Trails module.
 * Spawns particles on player movement, simulates bounce physics,
 * fades alpha over length, and renders soft bloom billboards using
 * {@code client/xuynya/ghost_bloom.png} with additive blend.
 */
public final class TrailsRenderer {
    private static final long FADE_WINDOW_MS = 250L;
    private static final double MAX_DISTANCE = 100.0;
    private static final Identifier BLOOM_TEXTURE = LinkenVisuals.id("client/xuynya/ghost_bloom.png");

    private static final int REUSABLE_CAPACITY_BYTES = 16384 * 24;
    private static final ByteBuffer REUSABLE_BUFFER = MemoryUtil.memAlloc(REUSABLE_CAPACITY_BYTES);

    public static final class MutableVec3d {
        public double x;
        public double y;
        public double z;

        public MutableVec3d(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public MutableVec3d(Vec3d vec) {
            this(vec.x, vec.y, vec.z);
        }

        public double distance(MutableVec3d other) {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public Vec3d toVec3d() {
            return new Vec3d(x, y, z);
        }
    }

    public static final class Particle3D {
        private final long spawnTime;
        public final MutableVec3d position;
        public final MutableVec3d delta;
        public float alpha;

        public Particle3D(Vec3d pos, Vec3d velocity) {
            this.spawnTime = System.currentTimeMillis();
            this.position = new MutableVec3d(pos);
            this.delta = new MutableVec3d(velocity.x * 0.01, velocity.y * 0.01, velocity.z * 0.01);
            this.alpha = 0.0f;
        }

        public void update(MinecraftClient client) {
            if (client.world == null) return;

            Block blockZ = getBlock(client, position.x, position.y, position.z + delta.z);
            if (isSolid(blockZ)) {
                delta.z *= -0.8;
            }

            Block blockY = getBlock(client, position.x, position.y + delta.y, position.z);
            if (isSolid(blockY)) {
                delta.x *= 0.999f;
                delta.z *= 0.999f;
                delta.y *= -0.7;
            }

            Block blockX = getBlock(client, position.x + delta.x, position.y, position.z);
            if (isSolid(blockX)) {
                delta.x *= -0.8;
            }

            updateWithoutPhysics();
        }

        public void updateAlpha(long now, long delayMs) {
            long age = now - spawnTime;
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

        public boolean isExpired(long now, long delayMs) {
            return (now - spawnTime) >= delayMs;
        }

        private static Block getBlock(MinecraftClient client, double x, double y, double z) {
            if (client.world == null) return Blocks.AIR;
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            return client.world.getBlockState(pos).getBlock();
        }

        private static boolean isSolid(Block block) {
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

    private static final List<Particle3D> particles = new ArrayList<>();
    private static double lastX, lastY, lastZ;
    private static boolean hasLast;

    private TrailsRenderer() {
    }

    public static void clear() {
        particles.clear();
        hasLast = false;
    }

    /** Called each client tick to simulate and spawn particles. */
    public static void tick() {
        TrailsModule module = Modules.trails();
        if (module == null || !module.enabled()) {
            if (!particles.isEmpty()) {
                particles.clear();
            }
            hasLast = false;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            clear();
            return;
        }

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        if (hasLast && (px != lastX || py != lastY || pz != lastZ)) {
            double distance = -(client.player.getWidth() / 2.0f);
            double yawRad = Math.toRadians(client.player.getBodyYaw());
            double xOffset = -Math.sin(yawRad) * distance;
            double zOffset = Math.cos(yawRad) * distance;

            Vec3d spawnPos = new Vec3d(
                px + xOffset,
                py + (client.player.getHeight() * 0.4f),
                pz + zOffset
            );

            Vec3d playerVel = client.player.getVelocity();
            Vec3d velocity = new Vec3d(playerVel.x, 0.0, playerVel.z).multiply(1.5f + Math.random());
            particles.add(new Particle3D(spawnPos, velocity));
        }

        lastX = px;
        lastY = py;
        lastZ = pz;
        hasLast = true;

        long delayMs = Math.max(1L, (long) module.length.value());
        long now = System.currentTimeMillis();
        MutableVec3d playerPos = new MutableVec3d(px, py, pz);

        for (Particle3D particle : particles) {
            particle.update(client);
            particle.updateAlpha(now, delayMs);
        }

        particles.removeIf(p -> p.isExpired(now, delayMs)
            || p.position.distance(playerPos) >= MAX_DISTANCE);
    }

    /** Called at END_MAIN to render soft bloom billboard particles. */
    public static void render(WorldRenderContext context) {
        TrailsModule module = Modules.trails();
        if (module == null || !module.enabled() || particles.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.getPerspective().isFirstPerson() && !module.showInFirstPerson.value()) {
            return;
        }
        Framebuffer main = client.getFramebuffer();
        Camera camera = client.gameRenderer.getCamera();
        if (main == null || main.getColorAttachmentView() == null
            || main.getDepthAttachmentView() == null || camera == null) {
            return;
        }

        AbstractTexture texture = client.getTextureManager().getTexture(BLOOM_TEXTURE);
        if (texture == null || texture.getGlTextureView() == null) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();
        Vector3fc right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camera.getRotation());
        Vector3fc up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camera.getRotation());

        int count = particles.size();
        int requiredBytes = count * 6 * 24;
        boolean heapAlloc = requiredBytes > REUSABLE_BUFFER.capacity();
        ByteBuffer data = heapAlloc ? MemoryUtil.memAlloc(requiredBytes) : REUSABLE_BUFFER;
        try {
            data.clear();
            int baseColor = Theme.accent();
            int rgb = baseColor & 0x00FFFFFF;
            int vertices = 0;

            for (int i = 0; i < count; i++) {
                Particle3D particle = particles.get(i);
                float alpha = particle.alpha;
                if (alpha <= 0.0f) {
                    continue;
                }

                MutableVec3d prevPos = i > 0 ? particles.get(i - 1).position : particle.position;
                float bloomSize = (float) Math.max(0.25, Math.min(1.0, particle.position.distance(prevPos) * 4.0));
                int a255 = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
                int color = (a255 << 24) | rgb;

                float half = bloomSize;
                float rx = right.x() * half;
                float ry = right.y() * half;
                float rz = right.z() * half;
                float ux = up.x() * half;
                float uy = up.y() * half;
                float uz = up.z() * half;

                float cx = (float) (particle.position.x - camPos.x);
                float cy = (float) (particle.position.y - camPos.y);
                float cz = (float) (particle.position.z - camPos.z);

                putTextured(data, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0.0f, 1.0f, color);
                putTextured(data, cx - rx + ux, cy - ry + uy, cz - rz + uz, 0.0f, 0.0f, color);
                putTextured(data, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1.0f, 0.0f, color);

                putTextured(data, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0.0f, 1.0f, color);
                putTextured(data, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1.0f, 0.0f, color);
                putTextured(data, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1.0f, 1.0f, color);
                vertices += 6;
            }
            data.flip();
            if (vertices == 0) {
                return;
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuBuffer verts = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:trails", GpuBuffer.USAGE_VERTEX, data);
            GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
            GpuSampler sampler = texture.getSampler() != null
                ? texture.getSampler()
                : RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

            try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:trails",
                main.getColorAttachmentView(), OptionalInt.empty(),
                main.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(ParticlesPipelines.BILLBOARD);
                Pipelines.bindWorldUniforms(pass, transforms);
                pass.bindTexture("Sampler0", texture.getGlTextureView(), sampler);
                pass.setVertexBuffer(0, verts);
                pass.draw(0, vertices);
            } finally {
                verts.close();
            }
        } finally {
            if (heapAlloc) {
                MemoryUtil.memFree(data);
            }
        }
    }

    private static void putTextured(ByteBuffer data, float x, float y, float z,
                                    float u, float v, int argb) {
        data.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
        data.put((byte) ((argb >> 16) & 0xFF));
        data.put((byte) ((argb >> 8) & 0xFF));
        data.put((byte) (argb & 0xFF));
        data.put((byte) ((argb >>> 24) & 0xFF));
    }

    public static void init() {
    }
}
