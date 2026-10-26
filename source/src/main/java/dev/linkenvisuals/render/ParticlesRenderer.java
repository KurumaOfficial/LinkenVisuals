package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.ParticlesModule;
import dev.linkenvisuals.util.particles.ParticleVisibilityHelper;
import dev.linkenvisuals.util.particles.ParticlesRules;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;

/**
 * Donor Particles renderer: ambient billboards or wireframe cubes, LOS-clipped
 * and drawn at END_MAIN. One textured pipeline for billboards and the proven
 * flat pipeline for cube edges.
 */
public final class ParticlesRenderer {
    private static final float SPAWN_RADIUS = 18.0f;
    private static final float PARTICLE_SPEED = 1.0f;
    private static final float PARTICLE_SIZE = 0.22f;
    private static final double DRAG = 0.99;
    private static final int MAX_SPAWN_ATTEMPTS = 12;
    private static final float CUBE_SIZE_MULTIPLIER = 1.45f;
    private static final float CUBE_LINE_WIDTH = 2.0f;
    private static final int BILLBOARD_BYTES = 24;

    private static final Identifier BLOOM = LinkenVisuals.id("textures/esp/ghost_bloom.png");
    private static final Identifier STAR = LinkenVisuals.id("textures/particles/star.png");
    private static final Identifier SNOW = LinkenVisuals.id("textures/particles/snow.png");
    private static final Identifier FIREFLY = LinkenVisuals.id("textures/particles/firefly.png");
    private static final Identifier DOLLAR = LinkenVisuals.id("textures/particles/dollar.png");

    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static double spawnAccumulator;
    private static int tickCounter;
    private static boolean lastRenderVisible;

    private ParticlesRenderer() {
    }

    public static void clear() {
        PARTICLES.clear();
        spawnAccumulator = 0.0;
        tickCounter = 0;
        lastRenderVisible = false;
    }

    /** Autotest probe: particles currently alive. */
    public static int liveCount() {
        return PARTICLES.size();
    }

    /** Autotest probe: last frame submitted visible particle geometry. */
    public static boolean lastRenderVisible() {
        return lastRenderVisible;
    }

    public static void tick() {
        ParticlesModule module = Modules.particles();
        if (module == null || !module.enabled()) {
            if (!PARTICLES.isEmpty()) {
                clear();
            }
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        tickCounter++;
        spawnAccumulator = ParticlesRules.nextAccumulator(spawnAccumulator);
        int spawnCount = ParticlesRules.spawnCount(spawnAccumulator);
        if (spawnCount > 0) {
            spawnAccumulator -= spawnCount;
        }

        int maxCount = ParticlesRules.maxCount(module.maxParticles.value());
        Vec3d playerPos = client.player.getEntityPos();
        int color = 0xFF000000 | (module.particleColor.rgb() & 0xFFFFFF);
        float rotateSpeed = Math.max(0.0f, module.cubeRotateSpeed.value());

        if (PARTICLES.size() > maxCount) {
            PARTICLES.subList(0, PARTICLES.size() - maxCount).clear();
        }
        if (spawnCount > 0 && PARTICLES.size() < maxCount) {
            int available = maxCount - PARTICLES.size();
            int toAdd = Math.min(spawnCount, Math.min(available, ParticlesRules.MAX_ADD_PER_TICK));
            for (int i = 0; i < toAdd; i++) {
                Vec3d spawnPos = randomSpawnPos(client, playerPos);
                PARTICLES.add(new Particle(spawnPos, color, rotateSpeed));
            }
        }

        double maxDistanceSq = 3600.0;
        Iterator<Particle> iterator = PARTICLES.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.tick();
            if (particle.isExpired()
                || client.player.getEntityPos().squaredDistanceTo(particle.pos) > maxDistanceSq
                || !isSpaceStillClear(client, particle)
                || isInsidePlayer(client, particle.pos)) {
                iterator.remove();
            }
        }
    }

    public static void render(WorldRenderContext context) {
        lastRenderVisible = false;
        ParticlesModule module = Modules.particles();
        if (module == null || !module.enabled() || PARTICLES.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        Framebuffer main = client.getFramebuffer();
        if (client.player == null || client.world == null || camera == null || main == null
            || main.getColorAttachmentView() == null || main.getDepthAttachmentView() == null) {
            return;
        }

        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        Vec3d camPos = camera.getCameraPos();
        Vector3fc right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camera.getRotation());
        Vector3fc up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camera.getRotation());
        Frustum frustum = client.worldRenderer.getCapturedFrustum();
        String mode = module.mode.current();
        boolean cube = "Cube".equals(mode);
        float settingSize = Math.max(0.0f, module.particleSize.value());

        if (cube) {
            renderCubes(client, main, module, tickDelta, camPos, frustum);
        } else {
            renderBillboards(client, main, module, mode, tickDelta, camPos, right, up, settingSize, frustum);
        }
    }

    private static void renderBillboards(MinecraftClient client, Framebuffer main,
                                         ParticlesModule module, String mode, float tickDelta,
                                         Vec3d camPos, Vector3fc right, Vector3fc up,
                                         float settingSize, Frustum frustum) {
        Identifier textureId = texture(mode);
        AbstractTexture texture = client.getTextureManager().getTexture(textureId);
        if (texture == null || texture.getGlTextureView() == null) {
            return;
        }
        ByteBuffer data = MemoryUtil.memAlloc(PARTICLES.size() * 6 * BILLBOARD_BYTES);
        int vertices = 0;
        try {
            for (Particle particle : PARTICLES) {
                Vec3d renderPos = particle.getRenderPos(tickDelta);
                if (!isInView(frustum, renderPos)
                    || !hasLineOfSight(client, particle, renderPos, camPos)) {
                    continue;
                }
                int alpha = ParticlesRules.alpha(particle.life, particle.maxLife,
                    particle.color >>> 24, tickDelta);
                if (alpha <= 3) {
                    continue;
                }
                float progress = ParticlesRules.lifeProgress(particle.life, particle.maxLife, tickDelta);
                float size = ParticlesRules.billboardSize(settingSize, particle.size, progress);
                float half = size * 0.5f;
                float x = (float) (renderPos.x - camPos.x);
                float y = (float) (renderPos.y - camPos.y);
                float z = (float) (renderPos.z - camPos.z);
                int color = alpha << 24 | (particle.color & 0xFFFFFF);
                float rx = right.x() * half;
                float ry = right.y() * half;
                float rz = right.z() * half;
                float ux = up.x() * half;
                float uy = up.y() * half;
                float uz = up.z() * half;

                putTextured(data, x - rx - ux, y - ry - uy, z - rz - uz, 0.0f, 1.0f, color);
                putTextured(data, x - rx + ux, y - ry + uy, z - rz + uz, 0.0f, 0.0f, color);
                putTextured(data, x + rx + ux, y + ry + uy, z + rz + uz, 1.0f, 0.0f, color);
                putTextured(data, x - rx - ux, y - ry - uy, z - rz - uz, 0.0f, 1.0f, color);
                putTextured(data, x + rx + ux, y + ry + uy, z + rz + uz, 1.0f, 0.0f, color);
                putTextured(data, x + rx - ux, y + ry - uy, z + rz - uz, 1.0f, 1.0f, color);
                vertices += 6;
            }
            data.flip();
            if (vertices == 0) {
                return;
            }
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:particles_billboards", GpuBuffer.USAGE_VERTEX, data);
            GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuSampler sampler = texture.getSampler() != null
                ? texture.getSampler()
                : RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:particles",
                main.getColorAttachmentView(), OptionalInt.empty(),
                main.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(ParticlesPipelines.BILLBOARD);
                Pipelines.bindWorldUniforms(pass, transforms);
                pass.bindTexture("Sampler0", texture.getGlTextureView(), sampler);
                pass.setVertexBuffer(0, buffer);
                pass.draw(0, vertices);
            } finally {
                buffer.close();
            }
            lastRenderVisible = true;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void renderCubes(MinecraftClient client, Framebuffer main,
                                    ParticlesModule module, float tickDelta, Vec3d camPos,
                                    Frustum frustum) {
        boolean glow = module.cubeGlow.value();
        float glowScale = Math.max(0.0f, module.cubeGlowSize.value());
        List<CubeDraw> draws = new ArrayList<>();
        for (Particle particle : PARTICLES) {
            Vec3d renderPos = particle.getRenderPos(tickDelta);
            if (!isInView(frustum, renderPos)
                || !hasLineOfSight(client, particle, renderPos, camPos)) {
                continue;
            }
            int alpha = ParticlesRules.alpha(particle.life, particle.maxLife,
                particle.color >>> 24, tickDelta);
            if (alpha <= 3) {
                continue;
            }
            float progress = ParticlesRules.lifeProgress(particle.life, particle.maxLife, tickDelta);
            float finalSize = ParticlesRules.billboardSize(module.particleSize.value(),
                particle.size, progress) * CUBE_SIZE_MULTIPLIER;
            draws.add(new CubeDraw(particle, renderPos, finalSize, alpha, glow, glowScale));
        }
        if (draws.isEmpty()) {
            return;
        }

        int vertsPerCube = glow ? 288 : 144;
        ByteBuffer data = MemoryUtil.memAlloc(draws.size() * vertsPerCube * 16);
        try {
            for (CubeDraw draw : draws) {
                Particle particle = draw.particle;
                Matrix4f rotation = new Matrix4f()
                    .rotateX((float) Math.toRadians(particle.getRotX(tickDelta)))
                    .rotateY((float) Math.toRadians(particle.getRotY(tickDelta)))
                    .rotateZ((float) Math.toRadians(particle.getRotZ(tickDelta)));
                int color = draw.alpha << 24 | (particle.color & 0xFFFFFF);
                float half = ParticlesRules.cubeHalfSize(draw.size, draw.alpha);
                float thickness = Math.max(0.004f, draw.size * 0.03f * CUBE_LINE_WIDTH);
                if (draw.glow) {
                    int glowAlpha = Math.max(0, Math.round(draw.alpha * 0.32f));
                    int glowColor = glowAlpha << 24 | (particle.color & 0xFFFFFF);
                    appendWireCube(data, draw.pos, camPos, rotation,
                        half * draw.glowScale, thickness * 1.35f, glowColor);
                }
                appendWireCube(data, draw.pos, camPos, rotation, half, thickness, color);
            }
            data.flip();
            int vertices = data.remaining() / 16;
            if (vertices == 0) {
                return;
            }
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:particles_cubes", GpuBuffer.USAGE_VERTEX, data);
            GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:particles_cubes",
                main.getColorAttachmentView(), OptionalInt.empty(),
                main.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(BlockOverlayPipelines.FLAT);
                Pipelines.bindWorldUniforms(pass, transforms);
                pass.setVertexBuffer(0, buffer);
                pass.draw(0, vertices);
            } finally {
                buffer.close();
            }
            lastRenderVisible = true;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void appendWireCube(ByteBuffer data, Vec3d origin, Vec3d camPos,
                                       Matrix4f rotation, float half, float thickness, int argb) {
        Vector3f[] corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? -half : half;
            float y = (i & 2) == 0 ? -half : half;
            float z = (i & 4) == 0 ? -half : half;
            Vector3f corner = new Vector3f(x, y, z);
            rotation.transformPosition(corner);
            corners[i] = corner;
        }
        int[][] edges = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        float ox = (float) (origin.x - camPos.x);
        float oy = (float) (origin.y - camPos.y);
        float oz = (float) (origin.z - camPos.z);
        for (int[] edge : edges) {
            Vector3f from = corners[edge[0]];
            Vector3f to = corners[edge[1]];
            appendEdge(data, ox, oy, oz, from, to, thickness, argb);
        }
    }

    private static void appendEdge(ByteBuffer data, float ox, float oy, float oz,
                                   Vector3f from, Vector3f to, float thickness, int argb) {
        float half = thickness * 0.5f;
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dz = to.z - from.z;
        if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz)) {
            putEdgeQuad(data, ox, oy, oz, from, to, 0.0f, half, 0.0f, argb);
            putEdgeQuad(data, ox, oy, oz, from, to, 0.0f, 0.0f, half, argb);
        } else if (Math.abs(dy) >= Math.abs(dz)) {
            putEdgeQuad(data, ox, oy, oz, from, to, half, 0.0f, 0.0f, argb);
            putEdgeQuad(data, ox, oy, oz, from, to, 0.0f, 0.0f, half, argb);
        } else {
            putEdgeQuad(data, ox, oy, oz, from, to, half, 0.0f, 0.0f, argb);
            putEdgeQuad(data, ox, oy, oz, from, to, 0.0f, half, 0.0f, argb);
        }
    }

    private static void putEdgeQuad(ByteBuffer data, float ox, float oy, float oz,
                                    Vector3f from, Vector3f to,
                                    float nx, float ny, float nz, int argb) {
        float ax = ox + from.x - nx;
        float ay = oy + from.y - ny;
        float az = oz + from.z - nz;
        float bx = ox + to.x - nx;
        float by = oy + to.y - ny;
        float bz = oz + to.z - nz;
        float cx = ox + to.x + nx;
        float cy = oy + to.y + ny;
        float cz = oz + to.z + nz;
        float dx = ox + from.x + nx;
        float dy = oy + from.y + ny;
        float dz = oz + from.z + nz;
        putColored(data, argb, ax, ay, az);
        putColored(data, argb, bx, by, bz);
        putColored(data, argb, cx, cy, cz);
        putColored(data, argb, ax, ay, az);
        putColored(data, argb, cx, cy, cz);
        putColored(data, argb, dx, dy, dz);
    }

    private static void putTextured(ByteBuffer data, float x, float y, float z,
                                    float u, float v, int argb) {
        data.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
        data.put((byte) (argb >> 16 & 0xFF)).put((byte) (argb >> 8 & 0xFF))
            .put((byte) (argb & 0xFF)).put((byte) (argb >>> 24));
    }

    private static void putColored(ByteBuffer data, int argb, float x, float y, float z) {
        data.putFloat(x).putFloat(y).putFloat(z);
        data.put((byte) (argb >> 16 & 0xFF)).put((byte) (argb >> 8 & 0xFF))
            .put((byte) (argb & 0xFF)).put((byte) (argb >>> 24));
    }

    private static boolean hasLineOfSight(MinecraftClient client, Particle particle,
                                          Vec3d renderPos, Vec3d camPos) {
        if (tickCounter - particle.losTick >= 2) {
            particle.losResult = ParticleVisibilityHelper.hasLineOfSight(camPos, renderPos);
            particle.losTick = tickCounter;
        }
        return particle.losResult;
    }

    private static boolean isInView(Frustum frustum, Vec3d pos) {
        if (frustum == null) {
            return true;
        }
        return frustum.isVisible(new Box(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5,
            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5));
    }

    private static Vec3d randomSpawnPos(MinecraftClient client, Vec3d center) {
        ClientWorld world = client.world;
        if (world == null) {
            return center;
        }
        int bottomY = world.getBottomY();
        int topY = bottomY + world.getHeight();
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double x = center.x + (RANDOM.nextDouble() - 0.5) * (SPAWN_RADIUS * 2.0);
            double y = center.y + (RANDOM.nextDouble() - 0.5) * SPAWN_RADIUS;
            double z = center.z + (RANDOM.nextDouble() - 0.5) * (SPAWN_RADIUS * 2.0);
            Vec3d candidate = new Vec3d(x,
                MathHelper.clamp(y, bottomY + 1.0, topY - 2.0), z);
            if (isSpawnSpaceClear(client, candidate) && !isInsidePlayer(client, candidate)) {
                return candidate;
            }
        }
        double fallbackY = MathHelper.clamp(center.y + 1.5, bottomY + 1.0, topY - 2.0);
        Vec3d fallback = new Vec3d(center.x, fallbackY, center.z);
        if (isSpawnSpaceClear(client, fallback) && !isInsidePlayer(client, fallback)) {
            return fallback;
        }
        return center;
    }

    private static boolean isSpaceStillClear(MinecraftClient client, Particle particle) {
        BlockPos corner = BlockPos.ofFloored(particle.pos.x - 0.15,
            particle.pos.y - 0.15, particle.pos.z - 0.15);
        if (corner.equals(particle.lastBlockPos) && particle.life % 10 != 0) {
            return true;
        }
        if (isSpawnSpaceClear(client, particle.pos)) {
            particle.lastBlockPos = corner;
            return true;
        }
        return false;
    }

    private static boolean isSpawnSpaceClear(MinecraftClient client, Vec3d pos) {
        ClientWorld world = client.world;
        if (world == null) {
            return false;
        }
        Box box = new Box(pos.x - 0.15, pos.y - 0.15, pos.z - 0.15,
            pos.x + 0.15, pos.y + 0.15, pos.z + 0.15);
        BlockPos blockPos = BlockPos.ofFloored(pos.x, pos.y, pos.z);
        return world.isSpaceEmpty(box) && world.getBlockState(blockPos).isAir();
    }

    private static boolean isInsidePlayer(MinecraftClient client, Vec3d pos) {
        return client.player != null && client.player.getBoundingBox().expand(0.05).contains(pos);
    }

    private static Identifier texture(String mode) {
        return switch (mode) {
            case "Star" -> STAR;
            case "Snow" -> SNOW;
            case "Firefly" -> FIREFLY;
            case "Dollar" -> DOLLAR;
            default -> BLOOM;
        };
    }

    public static void init() {
    }

    private static final class Particle {
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

        private Particle(Vec3d spawnPos, int color, float rotateSpeed) {
            this.prevPos = spawnPos;
            this.pos = spawnPos;
            this.color = color;
            this.size = PARTICLE_SIZE;
            this.life = this.maxLife = ParticlesRules.MIN_LIFE_TICKS
                + RANDOM.nextInt(Math.max(1, ParticlesRules.MAX_LIFE_TICKS
                - ParticlesRules.MIN_LIFE_TICKS + 1));
            double velX = (RANDOM.nextDouble() - 0.5) * 0.02 * PARTICLE_SPEED;
            double velY = (RANDOM.nextDouble() * 0.03 + 0.01) * PARTICLE_SPEED;
            double velZ = (RANDOM.nextDouble() - 0.5) * 0.02 * PARTICLE_SPEED;
            this.velocity = new Vec3d(velX, velY, velZ);
            this.rotX = RANDOM.nextFloat() * 360.0f - 180.0f;
            this.rotY = RANDOM.nextFloat() * 360.0f - 180.0f;
            this.rotZ = RANDOM.nextFloat() * 360.0f - 180.0f;
            this.prevRotX = rotX;
            this.prevRotY = rotY;
            this.prevRotZ = rotZ;
            float speed = Math.max(0.0f, rotateSpeed);
            this.rotSpeedX = (RANDOM.nextFloat() * 2.0f - 1.0f) * speed;
            this.rotSpeedY = (RANDOM.nextFloat() * 2.0f - 1.0f) * speed;
            this.rotSpeedZ = (RANDOM.nextFloat() * 2.0f - 1.0f) * speed;
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

    private record CubeDraw(Particle particle, Vec3d pos, float size, int alpha,
                            boolean glow, float glowScale) {
    }
}
