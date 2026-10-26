package dev.linkenvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.DamageParticlesModule;
import dev.linkenvisuals.util.ColorMath;
import dev.linkenvisuals.util.particles.DamageParticlesRules;
import dev.linkenvisuals.util.particles.ParticleVisibilityHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Donor DamageParticles renderer. The legacy impact particles follow the donor
 * bounce/friction simulation and three-layer billboard; the decorative feature
 * adds the attack burst and projectile trail modes on the same END_MAIN pass.
 */
public final class DamageParticlesRenderer {
    private static final Identifier BLOOM = LinkenVisuals.id("textures/esp/ghost_bloom.png");
    private static final Identifier STAR = LinkenVisuals.id("textures/particles/star.png");
    private static final Identifier SNOW = LinkenVisuals.id("textures/particles/snow.png");
    private static final Identifier FIREFLY = LinkenVisuals.id("textures/particles/firefly.png");
    private static final Identifier DOLLAR = LinkenVisuals.id("textures/particles/dollar.png");

    private static final double MAX_DISTANCE_SQ = 100.0 * 100.0;
    private static final double GRAVITY = 0.04;
    private static final double AIR_FRICTION = 0.92;
    private static final double GROUND_FRICTION = 0.65;
    private static final double BOUNCE_DAMPING = 0.55;
    private static final double SIDE_BOUNCE_DAMPING = 0.45;
    private static final double MIN_VELOCITY = 0.005;
    private static final double COLLISION_MARGIN = 0.12;

    private static final List<LegacyParticle> LEGACY = new ArrayList<>();
    private static final List<DecorativeParticle> ATTACK = new ArrayList<>();
    private static final List<DecorativeParticle> THROW = new ArrayList<>();
    private static long lastUpdateNanos = System.nanoTime();
    private static boolean lastRenderVisible;

    private DamageParticlesRenderer() {
    }

    public static void clear() {
        LEGACY.clear();
        ATTACK.clear();
        THROW.clear();
        lastUpdateNanos = System.nanoTime();
        lastRenderVisible = false;
    }

    public static int liveCount() {
        return LEGACY.size() + ATTACK.size() + THROW.size();
    }

    public static boolean lastRenderVisible() {
        return lastRenderVisible;
    }

    public static void onAttack(Entity target) {
        DamageParticlesModule module = Modules.damageParticles();
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.enabled() || client.player == null || client.world == null
            || !(target instanceof LivingEntity living) || target == client.player || !living.isAlive()) {
            return;
        }
        Vec3d center = living.getBoundingBox().getCenter();
        int count = Math.max(0, Math.round(module.count.value()));
        for (int i = 0; i < count; i++) {
            LEGACY.add(new LegacyParticle(center));
        }
        if (module.spawnOnAttack.value()) {
            for (int i = 0; i < DamageParticlesRules.ATTACK_BURST; i++) {
                spawnDecorative(ATTACK, center, 6.0, module);
            }
            trim(ATTACK, DamageParticlesRules.MAX_ATTACK_PARTICLES);
        }
    }

    public static void tick() {
        DamageParticlesModule module = Modules.damageParticles();
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.enabled()) {
            if (!LEGACY.isEmpty() || !ATTACK.isEmpty() || !THROW.isEmpty()) {
                clear();
            }
            return;
        }
        if (client.player == null || client.world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        ATTACK.removeIf(particle -> now - particle.spawnMs > DamageParticlesRules.DECORATIVE_LIFETIME_MS);
        THROW.removeIf(particle -> now - particle.spawnMs > DamageParticlesRules.DECORATIVE_LIFETIME_MS);
        LEGACY.removeIf(particle -> particle.isExpired(now));
        if (module.spawnOnThrow.value()) {
            for (Entity entity : client.world.getEntities()) {
                if (!isProjectile(entity) || entity.getVelocity().lengthSquared() < 0.0001) {
                    continue;
                }
                Vec3d pos = entity.getEntityPos();
                for (int i = 0; i < DamageParticlesRules.THROW_BURST; i++) {
                    spawnDecorative(THROW, pos, 1.0, module);
                }
            }
            trim(THROW, DamageParticlesRules.MAX_THROW_PARTICLES);
        }
    }

    public static void render(WorldRenderContext context) {
        lastRenderVisible = false;
        DamageParticlesModule module = Modules.damageParticles();
        if (module == null || !module.enabled()
            || (LEGACY.isEmpty() && ATTACK.isEmpty() && THROW.isEmpty())) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        Framebuffer main = client.getFramebuffer();
        if (client.player == null || client.world == null || camera == null || main == null
            || main.getColorAttachmentView() == null || main.getDepthAttachmentView() == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long nowNs = System.nanoTime();
        double deltaSeconds = Math.min(0.2, Math.max(0.0, (nowNs - lastUpdateNanos) / 1.0E9));
        lastUpdateNanos = nowNs;
        Vec3d cam = camera.getCameraPos();
        Vector3fc right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camera.getRotation());
        Vector3fc up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camera.getRotation());
        Frustum frustum = client.worldRenderer.getCapturedFrustum();

        if (!LEGACY.isEmpty()) {
            renderLegacy(client, main, module, cam, right, up, nowMs, frustum);
        }
        if (!ATTACK.isEmpty() || !THROW.isEmpty()) {
            renderDecorative(client, main, module, cam, right, up, nowMs, deltaSeconds, frustum);
        }
    }

    private static void renderLegacy(MinecraftClient client, Framebuffer main,
                                     DamageParticlesModule module, Vec3d cam,
                                     Vector3fc right, Vector3fc up, long nowMs,
                                     Frustum frustum) {
        List<Billboard> glow = new ArrayList<>();
        List<Billboard> coreStar = new ArrayList<>();
        List<Billboard> coreFirefly = new ArrayList<>();
        List<Billboard> sparkle = new ArrayList<>();
        int baseRgb = module.color.rgb();
        int glowRgb = ColorMath.mix(baseRgb, 0xFFFFFF, 0.2f);
        int coreRgb = ColorMath.mix(baseRgb, 0xFFFFFF, 0.48f);
        Iterator<LegacyParticle> iterator = LEGACY.iterator();
        while (iterator.hasNext()) {
            LegacyParticle particle = iterator.next();
            if (particle.isExpired(nowMs)
                || client.player.getEntityPos().squaredDistanceTo(particle.pos) > MAX_DISTANCE_SQ) {
                iterator.remove();
                continue;
            }
            particle.update(nowMs, client);
            if (!isInView(frustum, particle.pos)) {
                continue;
            }
            if (nowMs - particle.losCheckedMs >= 100L) {
                particle.los = ParticleVisibilityHelper.hasLineOfSight(cam, particle.pos);
                particle.losCheckedMs = nowMs;
            }
            if (!particle.los) {
                continue;
            }
            float progress = particle.progress(nowMs);
            float alpha = particle.alpha(nowMs);
            if (alpha <= 0.01f) {
                continue;
            }
            int a = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
            float size = DamageParticlesRules.legacySize(module.size.value(),
                particle.sizeMultiplier, progress);
            int glowColor = ColorMath.withAlpha(glowRgb, a);
            int coreColor = ColorMath.withAlpha(coreRgb, a);
            int sparkColor = ColorMath.withAlpha(0xFFFFFF, MathHelper.clamp(
                Math.round(a * (0.55f + particle.sparkle * 0.35f)), 0, 255));
            glow.add(new Billboard(particle.pos, size * (1.85f + particle.sparkle * 0.35f), glowColor));
            if (particle.texture == STAR) {
                coreStar.add(new Billboard(particle.pos, size * (1.02f + particle.sparkle * 0.08f), coreColor));
            } else {
                coreFirefly.add(new Billboard(particle.pos, size * (1.02f + particle.sparkle * 0.08f), coreColor));
            }
            sparkle.add(new Billboard(particle.pos, size * 0.52f, sparkColor));
        }
        drawBillboards(client, main, FIREFLY, glow, cam, right, up);
        drawBillboards(client, main, STAR, coreStar, cam, right, up);
        drawBillboards(client, main, FIREFLY, coreFirefly, cam, right, up);
        drawBillboards(client, main, STAR, sparkle, cam, right, up);
    }

    private static void renderDecorative(MinecraftClient client, Framebuffer main,
                                         DamageParticlesModule module, Vec3d cam,
                                         Vector3fc right, Vector3fc up,
                                         long nowMs, double deltaSeconds,
                                         Frustum frustum) {
        String mode = module.mode.current();
        boolean cube = "Cube".equals(mode);
        List<Billboard> billboards = new ArrayList<>();
        List<CubeDraw> cubes = new ArrayList<>();
        renderDecorativeList(client, ATTACK, module, cam, nowMs, deltaSeconds,
            cube, billboards, cubes, frustum);
        renderDecorativeList(client, THROW, module, cam, nowMs, deltaSeconds,
            cube, billboards, cubes, frustum);
        if (!billboards.isEmpty()) {
            drawBillboards(client, main, texture(mode), billboards, cam, right, up);
        }
        if (!cubes.isEmpty()) {
            drawCubes(client, main, cubes, cam);
        }
    }

    private static void renderDecorativeList(MinecraftClient client, List<DecorativeParticle> particles,
                                             DamageParticlesModule module, Vec3d cam, long nowMs,
                                             double deltaSeconds, boolean cube,
                                             List<Billboard> billboards, List<CubeDraw> cubes,
                                             Frustum frustum) {
        for (DecorativeParticle particle : particles) {
            particle.update(deltaSeconds);
            long age = nowMs - particle.spawnMs;
            if (age > DamageParticlesRules.DECORATIVE_LIFETIME_MS) {
                continue;
            }
            if (!isInView(frustum, particle.position)) {
                continue;
            }
            boolean attack = particles == ATTACK;
            float alpha = DamageParticlesRules.decorativeAlpha(age,
                attack ? 350L : 650L, attack ? 650L : 1200L);
            if (alpha <= 0.01f) {
                continue;
            }
            if (nowMs - particle.losCheckedMs >= 100L) {
                particle.los = ParticleVisibilityHelper.hasLineOfSight(cam, particle.position);
                particle.losCheckedMs = nowMs;
            }
            if (!particle.los) {
                continue;
            }
            int a = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
            int argb = ColorMath.withAlpha(particle.color, a);
            if (cube) {
                cubes.add(new CubeDraw(particle.position, particle.size * 1.8f, argb));
            } else {
                billboards.add(new Billboard(particle.position, particle.size * 2.0f, argb));
            }
            if (!cube && ("Bloom".equals(module.mode.current()) || "Firefly".equals(module.mode.current()))) {
                billboards.add(new Billboard(particle.position, particle.size, argb));
            }
        }
    }

    private static void drawBillboards(MinecraftClient client, Framebuffer main, Identifier textureId,
                                       List<Billboard> billboards, Vec3d cam,
                                       Vector3fc right, Vector3fc up) {
        if (billboards.isEmpty()) {
            return;
        }
        AbstractTexture texture = client.getTextureManager().getTexture(textureId);
        if (texture == null || texture.getGlTextureView() == null) {
            return;
        }
        ByteBuffer data = MemoryUtil.memAlloc(billboards.size() * 6 * 24);
        int vertices = 0;
        try {
            for (Billboard billboard : billboards) {
                float half = billboard.size * 0.5f;
                float x = (float) (billboard.pos.x - cam.x);
                float y = (float) (billboard.pos.y - cam.y);
                float z = (float) (billboard.pos.z - cam.z);
                float rx = right.x() * half;
                float ry = right.y() * half;
                float rz = right.z() * half;
                float ux = up.x() * half;
                float uy = up.y() * half;
                float uz = up.z() * half;
                putTextured(data, x - rx - ux, y - ry - uy, z - rz - uz, 0.0f, 1.0f, billboard.argb);
                putTextured(data, x - rx + ux, y - ry + uy, z - rz + uz, 0.0f, 0.0f, billboard.argb);
                putTextured(data, x + rx + ux, y + ry + uy, z + rz + uz, 1.0f, 0.0f, billboard.argb);
                putTextured(data, x - rx - ux, y - ry - uy, z - rz - uz, 0.0f, 1.0f, billboard.argb);
                putTextured(data, x + rx + ux, y + ry + uy, z + rz + uz, 1.0f, 0.0f, billboard.argb);
                putTextured(data, x + rx - ux, y + ry - uy, z + rz - uz, 1.0f, 1.0f, billboard.argb);
                vertices += 6;
            }
            data.flip();
            if (vertices == 0) {
                return;
            }
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:damage_particles", GpuBuffer.USAGE_VERTEX, data);
            GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuSampler sampler = texture.getSampler() != null
                ? texture.getSampler()
                : RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:damage_particles",
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

    private static void drawCubes(MinecraftClient client, Framebuffer main, List<CubeDraw> cubes, Vec3d cam) {
        ByteBuffer data = MemoryUtil.memAlloc(cubes.size() * 144 * 16);
        try {
            for (CubeDraw cube : cubes) {
                appendWireCube(data, cube.pos, cam, cube.size, cube.argb);
            }
            data.flip();
            int vertices = data.remaining() / 16;
            if (vertices == 0) {
                return;
            }
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:damage_particles_cubes", GpuBuffer.USAGE_VERTEX, data);
            GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:damage_particles_cubes",
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

    private static void appendWireCube(ByteBuffer data, Vec3d origin, Vec3d cam, float half, int argb) {
        Vector3f[] corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f((i & 1) == 0 ? -half : half,
                (i & 2) == 0 ? -half : half, (i & 4) == 0 ? -half : half);
        }
        int[][] edges = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        float ox = (float) (origin.x - cam.x);
        float oy = (float) (origin.y - cam.y);
        float oz = (float) (origin.z - cam.z);
        for (int[] edge : edges) {
            Vector3f from = corners[edge[0]];
            Vector3f to = corners[edge[1]];
            appendEdge(data, ox, oy, oz, from, to, Math.max(0.006f, half * 0.12f), argb);
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

    private static void spawnDecorative(List<DecorativeParticle> list, Vec3d origin,
                                        double scatter, DamageParticlesModule module) {
        Vec3d pos = origin.add(randomBetween(-0.2, 0.2), randomBetween(-0.2, 0.2),
            randomBetween(-0.2, 0.2));
        Vec3d velocity = new Vec3d(randomBetween(-scatter, scatter),
            randomBetween(-scatter, scatter), randomBetween(-scatter, scatter));
        list.add(new DecorativeParticle(pos, velocity, module.color.rgb(),
            DamageParticlesRules.decorativeSize(module.particleSize.value())));
    }

    private static void trim(List<?> list, int max) {
        int overflow = list.size() - Math.max(0, max);
        if (overflow > 0) {
            list.subList(0, overflow).clear();
        }
    }

    private static boolean isProjectile(Entity entity) {
        if (entity instanceof TridentEntity trident) {
            return !trident.isOnGround();
        }
        return entity instanceof EnderPearlEntity || entity instanceof ArrowEntity;
    }

    private static boolean isInView(Frustum frustum, Vec3d pos) {
        return frustum == null || frustum.isVisible(new Box(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5,
            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5));
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

    private static double randomBetween(double min, double max) {
        if (min >= max) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private static final class Billboard {
        private final Vec3d pos;
        private final float size;
        private final int argb;

        private Billboard(Vec3d pos, float size, int argb) {
            this.pos = pos;
            this.size = size;
            this.argb = argb;
        }
    }

    private record CubeDraw(Vec3d pos, float size, int argb) {
    }

    private static final class LegacyParticle {
        private Vec3d pos;
        private Vec3d velocity;
        private final Vec3d origin;
        private final long spawnMs;
        private long lastUpdateMs;
        private final Identifier texture;
        private final float baseAlpha;
        private float alphaMultiplier = 1.0f;
        private boolean onGround;
        private final float sizeMultiplier;
        private final long lifespan;
        private final double targetRadius;
        private final float sparkle;
        private boolean los;
        private long losCheckedMs;

        private LegacyParticle(Vec3d origin) {
            this.origin = origin;
            this.pos = origin;
            double angle = randomBetween(0.0, Math.PI * 2.0);
            double outward = randomBetween(0.55, 0.85);
            double vertical = randomBetween(0.41, 0.58);
            double angleScatter = randomBetween(-0.2, 0.2);
            double upScatter = randomBetween(-0.12, 0.16);
            this.velocity = new Vec3d(Math.cos(angle + angleScatter) * outward,
                vertical + upScatter, Math.sin(angle + angleScatter) * outward)
                .add(randomBetween(-0.04, 0.04), 0.0, randomBetween(-0.04, 0.04));
            this.spawnMs = System.currentTimeMillis();
            this.lastUpdateMs = spawnMs;
            this.texture = randomBetween(0.0, 1.0) < 0.5 ? STAR : FIREFLY;
            this.baseAlpha = (float) randomBetween(0.75, 1.0);
            this.sizeMultiplier = (float) randomBetween(0.85, 1.15);
            this.lifespan = (long) randomBetween(2100, 2800);
            this.targetRadius = randomBetween(2.2, 3.15);
            this.sparkle = (float) randomBetween(0.0, 1.0);
        }

        private void update(long nowMs, MinecraftClient client) {
            if (client.world == null) {
                return;
            }
            double deltaTicks = Math.max(0.01, Math.min(4.0, (nowMs - lastUpdateMs) / 50.0));
            lastUpdateMs = nowMs;
            if (!onGround || velocity.y > 0.0) {
                velocity = velocity.add(0.0, -GRAVITY * deltaTicks, 0.0);
            }
            Vec3d candidate = pos.add(velocity.multiply(deltaTicks));
            pos = handleCollisions(candidate, client);
            double drag = onGround ? GROUND_FRICTION : AIR_FRICTION;
            velocity = velocity.multiply(drag, drag, drag);
            if (!onGround) {
                Vec3d horizontal = pos.subtract(origin);
                Vec3d flat = new Vec3d(horizontal.x, 0.0, horizontal.z);
                double distance = flat.length();
                if (distance > 1.0E-3) {
                    double desired = Math.min(targetRadius, distance + 0.01);
                    double acceleration = MathHelper.clamp((desired - distance) * 0.08, -0.06, 0.06);
                    velocity = velocity.add(flat.normalize().multiply(acceleration).add(
                        randomBetween(-0.005, 0.005), 0.0, randomBetween(-0.005, 0.005)));
                } else {
                    velocity = velocity.add(randomBetween(-0.02, 0.02), 0.0,
                        randomBetween(-0.02, 0.02));
                }
            } else {
                velocity = velocity.add(randomBetween(-0.004, 0.004), 0.0,
                    randomBetween(-0.004, 0.004));
            }
            if (onGround && velocity.lengthSquared() < MIN_VELOCITY * MIN_VELOCITY) {
                velocity = Vec3d.ZERO;
                alphaMultiplier *= 0.92f;
            } else if (!onGround) {
                alphaMultiplier = Math.min(1.0f, alphaMultiplier + 0.01f);
            }
        }

        private Vec3d handleCollisions(Vec3d candidate, MinecraftClient client) {
            if (client.world == null) {
                return candidate;
            }
            Vec3d updated = candidate;
            BlockPos groundPos = BlockPos.ofFloored(updated.x, updated.y - COLLISION_MARGIN, updated.z);
            if (!client.world.getBlockState(groundPos).isAir()) {
                updated = new Vec3d(updated.x, groundPos.getY() + 1.0 + COLLISION_MARGIN, updated.z);
                velocity = new Vec3d(velocity.x * GROUND_FRICTION, -velocity.y * BOUNCE_DAMPING,
                    velocity.z * GROUND_FRICTION);
                onGround = true;
            } else {
                onGround = false;
            }
            if (Math.abs(velocity.x) > MIN_VELOCITY) {
                BlockPos sideX = BlockPos.ofFloored(updated.x + Math.signum(velocity.x) * COLLISION_MARGIN,
                    updated.y, updated.z);
                if (!client.world.getBlockState(sideX).isAir()) {
                    updated = new Vec3d(pos.x, updated.y, updated.z);
                    velocity = new Vec3d(-velocity.x * SIDE_BOUNCE_DAMPING, velocity.y, velocity.z);
                }
            }
            if (Math.abs(velocity.z) > MIN_VELOCITY) {
                BlockPos sideZ = BlockPos.ofFloored(updated.x, updated.y,
                    updated.z + Math.signum(velocity.z) * COLLISION_MARGIN);
                if (!client.world.getBlockState(sideZ).isAir()) {
                    updated = new Vec3d(updated.x, updated.y, pos.z);
                    velocity = new Vec3d(velocity.x, velocity.y, -velocity.z * SIDE_BOUNCE_DAMPING);
                }
            }
            return updated;
        }

        private float progress(long nowMs) {
            long lifetime = Math.min(DamageParticlesRules.LEGACY_LIFETIME_MS, lifespan);
            return MathHelper.clamp((nowMs - spawnMs) / (float) lifetime, 0.0f, 1.0f);
        }

        private float alpha(long nowMs) {
            return DamageParticlesRules.legacyAlpha(progress(nowMs), baseAlpha, alphaMultiplier, onGround);
        }

        private boolean isExpired(long nowMs) {
            return nowMs - spawnMs > Math.min(DamageParticlesRules.LEGACY_LIFETIME_MS, lifespan)
                || alphaMultiplier <= 0.02f;
        }
    }

    private static final class DecorativeParticle {
        private Vec3d position;
        private Vec3d velocity;
        private final int color;
        private final float size;
        private final long spawnMs = System.currentTimeMillis();
        private boolean los;
        private long losCheckedMs;
        private BlockPos lastBlockPos;

        private DecorativeParticle(Vec3d position, Vec3d velocity, int color, float size) {
            this.position = position.add(0.0, size, 0.0);
            this.velocity = velocity.multiply(0.05);
            this.color = color;
            this.size = size;
        }

        private void update(double deltaSeconds) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null && velocity.lengthSquared() > 1.0E-4) {
                BlockPos lookAhead = BlockPos.ofFloored(position.x + velocity.x,
                    position.y + velocity.y, position.z + velocity.z);
                if (!lookAhead.equals(lastBlockPos)) {
                    lastBlockPos = lookAhead;
                    if (isSolid(client, position.x, position.y, position.z + velocity.z)) {
                        velocity = new Vec3d(velocity.x * 1.35, velocity.y * 1.35, velocity.z * -1.1);
                    }
                    if (isSolid(client, position.x, position.y + velocity.y, position.z)) {
                        velocity = new Vec3d(velocity.x * 1.35, velocity.y * -1.1, velocity.z * 1.35);
                    }
                    if (isSolid(client, position.x + velocity.x, position.y, position.z)) {
                        velocity = new Vec3d(velocity.x * -1.1, velocity.y * 1.35, velocity.z * 1.35);
                    }
                }
            }
            double friction = Math.pow(0.999, deltaSeconds * 60.0);
            velocity = velocity.multiply(friction).subtract(0.0, 2.0E-5, 0.0);
            position = position.add(velocity.multiply(deltaSeconds * 60.0 * 0.2));
        }

        private boolean isSolid(MinecraftClient client, double x, double y, double z) {
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            return client.world != null && client.world.getBlockState(pos).isFullCube(client.world, pos);
        }
    }
}
