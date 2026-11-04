package ru.method.linkenvisuals.modules.impl.render.cosmetic;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.render.Pipelines;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

public final class Spheres {

    private final CosmeticEffects p;

    private final Map<UUID, TrailState> sphereTrailMap = new HashMap<>();

    private int lastEditIdx = Integer.MIN_VALUE;
    private float allLR = 0.0f;
    private float allFB = 0.0f;
    private final float[] offLR = new float[]{0.0f, 0.0f, 0.0f};
    private final float[] offFB = new float[]{0.0f, 0.0f, 0.0f};

    // Константы, раньше аллоцировавшиеся каждый кадр в renderSpheres (реально неизменны).
    private static final float[] PHASE = {0.0f, 2.0943951f, 4.1887903f};
    private static final float[] STATIC_OFFSETS_DEG = {180f, -90f, 360f};
    // Переиспользуемый буфер цветов сфер — заполняется каждый кадр (без аллокации массива).
    private final int[] baseCols = new int[3];

    // 1.21.11 world-space batching: opaque-ish sphere mesh and additive bloom/trail
    // billboards. Both are written into reusable native buffers and drawn with a
    // single encoder pass at END_MAIN (same proven path as TrailsRenderer).
    private static final int MESH_STRIDE = 16;
    private static final int BILLBOARD_STRIDE = 24;
    private static final RenderPipeline SPHERE_MESH = Pipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(ru.method.linkenvisuals.LinkenVisuals.id("pipeline/cosmetic_sphere"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withCull(false)
                    .build());
    private static final RenderPipeline SPHERE_BLOOM = Pipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(ru.method.linkenvisuals.LinkenVisuals.id("pipeline/cosmetic_sphere_bloom"))
                    .withVertexShader(ru.method.linkenvisuals.LinkenVisuals.id("core/particle_billboard"))
                    .withFragmentShader(ru.method.linkenvisuals.LinkenVisuals.id("core/sphere_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private final ByteBuffer meshBuffer = MemoryUtil.memAlloc(4 * 1024 * 1024);
    private final ByteBuffer billboardBuffer = MemoryUtil.memAlloc(1024 * 1024);
    private final ByteBuffer bloomBuffer = MemoryUtil.memAlloc(1024 * 1024);
    private int meshVertices;
    private int billboardVertices;
    private int bloomVertices;
    private Vec3d frameCamPos = Vec3d.ZERO;
    private final Vector3f frameRight = new Vector3f();
    private final Vector3f frameUp = new Vector3f();

    // Кэш единичной сферы (тригонометрия + позиции узлов). Общий для всех сфер и всех кадров.
    private final SphereCache sphereCache = new SphereCache();

    public Spheres(CosmeticEffects parent) {
        this.p = parent;
    }

    public void deactivate() {
        sphereTrailMap.clear();
        lastEditIdx = Integer.MIN_VALUE;
        allLR = 0.0f;
        allFB = 0.0f;
        offLR[0] = offLR[1] = offLR[2] = 0.0f;
        offFB[0] = offFB[1] = offFB[2] = 0.0f;
        sphereCache.reset();
    }

    public void onWorldRender(EventRender3D.Game e) {
        if (p.mc.world == null || p.mc.player == null) return;
        if (!p.spheresOn()) return;

        syncOffsetEditor();

        float pt = e.getTickCounter().getTickProgress(false);
        Camera cam = p.mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getCameraPos();
        boolean camFirst = p.mc.options.getPerspective().isFirstPerson();

        boolean drawSelf = CosmeticEffects.bool(p.self);
        boolean drawOthers = CosmeticEffects.bool(p.others);
        boolean drawFirst = CosmeticEffects.bool(p.firstPerson);

        meshBuffer.clear();
        billboardBuffer.clear();
        bloomBuffer.clear();
        meshVertices = 0;
        billboardVertices = 0;
        bloomVertices = 0;
        frameCamPos = camPos;
        frameRight.set(1f, 0f, 0f).rotate(cam.getRotation());
        frameUp.set(0f, 1f, 0f).rotate(cam.getRotation());

        for (PlayerEntity pl : p.mc.world.getPlayers()) {
            if (pl == null) continue;
            if (pl.isInvisible()) continue;

            boolean isSelf = pl == p.mc.player;

            if (isSelf) {
                if (!drawSelf) continue;
                if (camFirst && !drawFirst) continue;
            } else {
                if (!drawOthers) continue;
            }

            renderSpheres(cam, camPos, pl, pt);
        }

        flushBatches();
    }

    private void renderSpheres(Camera cam, Vec3d camPos, PlayerEntity player, float pt) {
        Vec3d lPos = player.getLerpedPos(pt);
        double px = lPos.x;
        double py = lPos.y;
        double pz = lPos.z;

        float bodyYawDeg = MathHelper.lerpAngleDegrees(pt, player.lastBodyYaw, player.bodyYaw);
        double bodyYawRad = Math.toRadians(bodyYawDeg);

        Vec3d forward = new Vec3d(-Math.sin(bodyYawRad), 0.0, Math.cos(bodyYawRad));
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);

        // При «Наруто» корпус своего игрока наклоняется (только 3-е лицо/F5) — поворачиваем позиции сфер
        // вокруг пояса по той же дуге, чтобы они следовали за наклонённым телом, а не висели на месте.
        boolean lean = player == p.mc.player
                && !p.mc.options.getPerspective().isFirstPerson()
                && CosmeticPosePatch.selfActive;
        double leanTheta = lean ? CosmeticPosePatch.selfLeanRad : 0.0;
        double leanPivotY = py + (lean ? CosmeticPosePatch.selfPivotY : 0.0);

        double baseY = py + player.getHeight() * 0.5 + p.height.getValue();
        double headY = py + player.getHeight() + p.height.getValue();

        float baseR = p.radius.getValue();
        float s = p.size.getValue();

        baseCols[0] = p.exort.getValue().getRGB();
        baseCols[1] = p.wex.getValue().getRGB();
        baseCols[2] = p.quas.getValue().getRGB();

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000.0f;
        float bobAmp = MathHelper.clamp(p.size.getValue() * 0.85f, 0.0f, 0.18f);

        float[] phase = PHASE;
        float[] staticOffsetsDeg = STATIC_OFFSETS_DEG;

        boolean doTrail = p.effect.getValue() != CosmeticEnums.SphereEffect.NONE;
        boolean fireTrail = p.effect.getValue() == CosmeticEnums.SphereEffect.FIRE_TRAIL;
        int limit = Math.max(6, Math.min(48, p.trailLen.getValue().intValue()));
        float trailMul = p.trailPower.getValue();

        for (int i = 0; i < 3; i++) {
            float ph = phase[i];

            float breatheK = 1.0f + (float) (Math.sin(t * 2.1f + ph) * p.breathe.getValue());
            float rr = baseR * breatheK;

            double sx;
            double sy;
            double sz;
            float alphaMul = 1.0f;

            if (p.mode.getValue() == CosmeticEnums.SphereMode.ORBIT) {
                float ang = (t * p.speed.getValue()) + ph + (float) bodyYawRad;
                sx = px + Math.cos(ang) * rr;
                sz = pz + Math.sin(ang) * rr;
                sy = baseY + Math.sin(t * 2.8f + ph) * bobAmp;
            } else if (p.mode.getValue() == CosmeticEnums.SphereMode.SPIRAL) {
                double yTop = headY + p.spiralHeight.getValue();
                double yBottom = (py + p.height.getValue()) - p.spiralHeight.getValue();
                double span = Math.max(0.001, yTop - yBottom);

                float prog = frac(t * (0.18f * p.speed.getValue()) + (i * 0.33333334f));
                float ang = (t * (2.10f * p.speed.getValue())) + ph + (float) bodyYawRad;

                sx = px + Math.cos(ang) * rr;
                sz = pz + Math.sin(ang) * rr;
                sy = yTop - (prog * span);
                alphaMul = spiralFade((float) ((sy - yBottom) / span));
            } else if (p.mode.getValue() == CosmeticEnums.SphereMode.SPIRAL_V2) {
                double yTop = headY + p.spiralHeight.getValue();
                double yBottom = (py + p.height.getValue()) - p.spiralHeight.getValue();
                double span = Math.max(0.001, yTop - yBottom);

                float prog = frac(t * (0.22f * p.speed.getValue()) + (i * 0.33333334f));
                float rr2 = rr * (0.80f + 0.20f * (float) Math.sin(t * 1.7f + ph));
                float ang = (t * (2.65f * p.speed.getValue())) + (ph * 1.15f) + (float) bodyYawRad;

                sx = px + Math.cos(ang) * rr2;
                sz = pz + Math.sin(ang) * rr2;

                double base = yTop - (prog * span);
                sy = base + Math.sin(t * 1.35f + ph) * (span * 0.04);
                alphaMul = spiralFade((float) ((sy - yBottom) / span));
            } else if (p.mode.getValue() == CosmeticEnums.SphereMode.WAVES) {
                float ang = (t * p.speed.getValue()) + ph + (float) bodyYawRad;
                float w = (float) Math.sin((ang * p.waveFreq.getValue()) + (t * 1.20f));

                sx = px + Math.cos(ang) * rr;
                sz = pz + Math.sin(ang) * rr;
                sy = headY + w * p.waveAmp.getValue() + Math.sin(t * 2.8f + ph) * bobAmp;
            } else {
                double ang = Math.toRadians(bodyYawDeg + staticOffsetsDeg[i]);
                sx = px + Math.cos(ang) * rr;
                sz = pz + Math.sin(ang) * rr;
                sy = baseY + Math.sin(t * 2.8f + ph) * bobAmp;
            }

            float addLR = allLR + offLR[i];
            float addFB = allFB + offFB[i];

            sx += right.x * addLR + forward.x * addFB;
            sz += right.z * addLR + forward.z * addFB;

            if (lean) {
                double[] r = leanPoint(sx, sy, sz, px, leanPivotY, pz, forward, right, leanTheta);
                sx = r[0];
                sy = r[1];
                sz = r[2];
            }

            Vec3d viewDir = camPos.subtract(sx, sy, sz);
            double len = viewDir.length();
            if (len > 1.0E-6) viewDir = viewDir.multiply(1.0 / len);
            else viewDir = new Vec3d(0, 0, 1);

            int col = withAlphaMul(baseCols[i], alphaMul);

            if (doTrail) {
                pushSphereTrail(player.getUuid(), i, new Vec3d(sx, sy, sz), limit);
                renderSphereTrail(player.getUuid(), i, col, trailMul, fireTrail);
            }

            if (alphaMul > 0.0025f) renderOne(sx, sy, sz, s, col, viewDir);
        }

        if (p.mode.getValue() == CosmeticEnums.SphereMode.SPIRAL_V2) {
            renderSpiralV2ExtraRight(camPos, px, py, pz, headY, bodyYawRad, forward, right, doTrail, fireTrail, limit, trailMul, player.getUuid());
        }
    }

    /**
     * Поворачивает точку вокруг оси «right» (вокруг пояса игрока) на угол theta — наклон корпуса вперёд,
     * как при Наруто. Верх точки уходит вперёд, повторяя наклон модели.
     */
    private static double[] leanPoint(double x, double y, double z,
                                      double px, double pivotY, double pz,
                                      Vec3d forward, Vec3d right, double theta) {
        double relX = x - px;
        double relY = y - pivotY;
        double relZ = z - pz;

        double relF = relX * forward.x + relZ * forward.z; // компонента вдоль «вперёд»
        double relR = relX * right.x + relZ * right.z;     // компонента вдоль «вправо» (ось наклона)

        double c = Math.cos(theta);
        double sn = Math.sin(theta);
        double newF = relF * c + relY * sn;   // верх уходит вперёд
        double newY = relY * c - relF * sn;

        double nx = px + newF * forward.x + relR * right.x;
        double nz = pz + newF * forward.z + relR * right.z;
        double ny = pivotY + newY;
        return new double[]{nx, ny, nz};
    }

    private void renderSpiralV2ExtraRight(Vec3d camPos,
                                          double px, double py, double pz,
                                          double headY,
                                          double bodyYawRad,
                                          Vec3d forward, Vec3d right,
                                          boolean doTrail, boolean fireTrail, int limit, float trailMul,
                                          UUID uuid) {

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000.0f;

        double yTop = headY + p.spiralHeight.getValue();
        double yBottom = (py + p.height.getValue()) - p.spiralHeight.getValue();
        double span = Math.max(0.001, yTop - yBottom);

        float baseR = p.radius.getValue();
        float s = p.size.getValue();

        float[] ph = PHASE;

        // Тот же кадр — baseCols уже заполнен в renderSpheres перед вызовом этого метода.
        int[] cols = baseCols;

        for (int j = 0; j < 3; j++) {
            float phj = ph[j];

            float prog = frac(t * (0.22f * p.speed.getValue()) + (j * 0.33333334f));
            float rr = baseR * (0.80f + 0.20f * (float) Math.sin(t * 1.7f + phj));

            float ang = (t * (2.65f * p.speed.getValue())) + (phj * 1.15f) + (float) bodyYawRad + (float) Math.PI;

            double sx = px + Math.cos(ang) * rr;
            double sz = pz + Math.sin(ang) * rr;

            double base = yTop - (prog * span);
            double sy = base + Math.sin(t * 1.35f + phj) * (span * 0.04);

            sx += right.x * allLR + forward.x * allFB;
            sz += right.z * allLR + forward.z * allFB;

            Vec3d viewDir = camPos.subtract(sx, sy, sz);
            double len = viewDir.length();
            if (len > 1.0E-6) viewDir = viewDir.multiply(1.0 / len);
            else viewDir = new Vec3d(0, 0, 1);

            float alphaMul = spiralFade((float) ((sy - yBottom) / span));
            int col = withAlphaMul(cols[j], alphaMul);
            int idx = 3 + j;

            if (doTrail) {
                pushSphereTrail(uuid, idx, new Vec3d(sx, sy, sz), limit);
                renderSphereTrail(uuid, idx, col, trailMul, fireTrail);
            }

            if (alphaMul > 0.0025f) renderOne(sx, sy, sz, s, col, viewDir);
        }
    }

    private float spiralFade(float norm) {
        float p0 = MathHelper.clamp(norm, 0.0f, 1.0f);
        float edge = 0.14f;
        float f1 = MathHelper.clamp(p0 / edge, 0.0f, 1.0f);
        float f2 = MathHelper.clamp((1.0f - p0) / edge, 0.0f, 1.0f);
        float a = Math.min(f1, f2);
        return a * a * (3.0f - 2.0f * a);
    }

    private int withAlphaMul(int argb, float mul) {
        int a0 = (argb >>> 24) & 0xFF;
        int a = MathHelper.clamp((int) (a0 * mul), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private void renderOne(double x, double y, double z, float r, int argb, Vec3d viewDir) {
        float cx = (float) (x - frameCamPos.x);
        float cy = (float) (y - frameCamPos.y);
        float cz = (float) (z - frameCamPos.z);

        drawSphere(cx, cy, cz, r, argb, viewDir);

        float bloomSize = r * 6.0f;
        int a0 = (argb >>> 24) & 0xFF;
        int bloomA = Math.min(255, (int) (a0 * 0.62f));
        int bloom = (bloomA << 24) | (argb & 0x00FFFFFF);

        addBloom(cx, cy, cz, bloomSize, bloom);
    }

    /** Adds one camera-facing, additively blended bloom quad (2 triangles). */
    private void addBillboard(float cx, float cy, float cz, float size, int argb) {
        addBillboard(billboardBuffer, cx, cy, cz, size, argb);
    }

    private void addBloom(float cx, float cy, float cz, float size, int argb) {
        // Pull the glow a little toward the camera so the sphere's own front
        // surface does not depth-clip the centre of the halo (the donor's
        // depth-tested billboard produced a hollow ring at close range).
        float shift = (size / 6f) * 1.15f;
        float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len > 1.0e-5f) {
            float f = 1f - Math.min(0.75f, shift / len);
            cx *= f;
            cy *= f;
            cz *= f;
        }
        addBillboard(bloomBuffer, cx, cy, cz, size, argb);
    }

    private void addBillboard(ByteBuffer buffer, float cx, float cy, float cz, float size, int argb) {
        float h = size * 0.5f;
        float rx = frameRight.x * h;
        float ry = frameRight.y * h;
        float rz = frameRight.z * h;
        float ux = frameUp.x * h;
        float uy = frameUp.y * h;
        float uz = frameUp.z * h;

        putBillboard(buffer, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0f, 1f, argb);
        putBillboard(buffer, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1f, 1f, argb);
        putBillboard(buffer, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1f, 0f, argb);

        putBillboard(buffer, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0f, 1f, argb);
        putBillboard(buffer, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1f, 0f, argb);
        putBillboard(buffer, cx - rx + ux, cy - ry + uy, cz - rz + uz, 0f, 0f, argb);
    }

    private void putBillboard(ByteBuffer buffer, float x, float y, float z, float u, float v, int argb) {
        if (buffer.remaining() < BILLBOARD_STRIDE) return;
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
        buffer.put((byte) ((argb >> 16) & 0xFF));
        buffer.put((byte) ((argb >> 8) & 0xFF));
        buffer.put((byte) (argb & 0xFF));
        buffer.put((byte) ((argb >>> 24) & 0xFF));
        if (buffer == bloomBuffer) bloomVertices++;
        else billboardVertices++;
    }

    private void drawSphere(float cx, float cy, float cz, float radius, int argb, Vec3d viewDir) {
        int a0 = (argb >>> 24) & 0xFF;
        int r0 = (argb >>> 16) & 0xFF;
        int g0 = (argb >>> 8) & 0xFF;
        int b0 = argb & 0xFF;

        SphereCache c = sphereCache;
        c.ensure();

        int rows = c.rows;   // stacks + 1 узловых широт (19)
        int cols = c.cols;   // slices долгот (28), последний квад замыкается на 0-ю колонку

        float[] ux = c.ux;
        float[] uy = c.uy;
        float[] uz = c.uz;
        int[] nodeColor = c.nodeColor;

        // shade() зависит только от нормали узла (= единичная позиция) и viewDir — общий для всех 4 смежных
        // квадов. Считаем цвет ОДИН раз на узел (rows*cols ≈ 532), а не по 4 угла на каждый квад (было 2016).
        int nodes = rows * cols;
        for (int n = 0; n < nodes; n++) {
            nodeColor[n] = shade(ux[n], uy[n], uz[n], viewDir, r0, g0, b0, a0);
        }

        for (int i = 0; i < rows - 1; i++) {
            int row0 = i * cols;
            int row1 = (i + 1) * cols;

            for (int j = 0; j < cols; j++) {
                int j1 = (j + 1 == cols) ? 0 : j + 1;

                int i00 = row0 + j;
                int i10 = row0 + j1;
                int i01 = row1 + j;
                int i11 = row1 + j1;

                float x00 = ux[i00] * radius, y00 = uy[i00] * radius, z00 = uz[i00] * radius;
                float x10 = ux[i10] * radius, y10 = uy[i10] * radius, z10 = uz[i10] * radius;
                float x01 = ux[i01] * radius, y01 = uy[i01] * radius, z01 = uz[i01] * radius;
                float x11 = ux[i11] * radius, y11 = uy[i11] * radius, z11 = uz[i11] * radius;
                int c00 = nodeColor[i00];
                int c10 = nodeColor[i10];
                int c01 = nodeColor[i01];
                int c11 = nodeColor[i11];

                putMesh(cx + x00, cy + y00, cz + z00, c00);
                putMesh(cx + x10, cy + y10, cz + z10, c10);
                putMesh(cx + x11, cy + y11, cz + z11, c11);

                putMesh(cx + x00, cy + y00, cz + z00, c00);
                putMesh(cx + x11, cy + y11, cz + z11, c11);
                putMesh(cx + x01, cy + y01, cz + z01, c01);
            }
        }
    }

    private void putMesh(float x, float y, float z, int argb) {
        if (meshBuffer.remaining() < MESH_STRIDE) return;
        meshBuffer.putFloat(x).putFloat(y).putFloat(z);
        meshBuffer.put((byte) ((argb >> 16) & 0xFF));
        meshBuffer.put((byte) ((argb >> 8) & 0xFF));
        meshBuffer.put((byte) (argb & 0xFF));
        meshBuffer.put((byte) ((argb >>> 24) & 0xFF));
        meshVertices++;
    }

    private int shade(float nx, float ny, float nz, Vec3d viewDir, int r0, int g0, int b0, int a0) {
        double dot = nx * viewDir.x + ny * viewDir.y + nz * viewDir.z;
        dot = MathHelper.clamp(dot, 0.0, 1.0);

        double rim = 1.0 - dot;
        double rim2 = rim * rim;
        double rim4 = rim2 * rim2;

        double base = 0.40 + 0.60 * dot;
        double add = 0.22 * rim4;

        double light = base + add;
        if (light > 1.0) light = 1.0;

        int r = clamp255((int) (r0 * light));
        int g = clamp255((int) (g0 * light));
        int b = clamp255((int) (b0 * light));

        return ((a0 & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private void pushSphereTrail(UUID uuid, int idx, Vec3d pos, int limit) {
        TrailState st = sphereTrailMap.computeIfAbsent(uuid, k -> new TrailState(6));
        ArrayDeque<TrailPoint> q = st.queues[MathHelper.clamp(idx, 0, st.queues.length - 1)];
        q.addFirst(new TrailPoint(pos, System.currentTimeMillis()));
        while (q.size() > limit) q.removeLast();
    }

    private void renderSphereTrail(UUID uuid, int idx, int argb, float mul, boolean fire) {
        TrailState st = sphereTrailMap.get(uuid);
        if (st == null) return;

        ArrayDeque<TrailPoint> q = st.queues[MathHelper.clamp(idx, 0, st.queues.length - 1)];
        if (q.isEmpty()) return;

        float a0 = ((argb >>> 24) & 0xFF) / 255.0f;
        float r0 = ((argb >>> 16) & 0xFF) / 255.0f;
        float g0 = ((argb >>> 8) & 0xFF) / 255.0f;
        float b0 = (argb & 0xFF) / 255.0f;

        int n = q.size();
        int k = 0;
        for (TrailPoint tp : q) {
            float tt = n <= 1 ? 0.0f : (k / (float) (n - 1));
            float fade = 1.0f - tt;
            fade = fade * fade;

            float a = MathHelper.clamp(a0 * (0.60f * fade) * mul, 0.0f, 1.0f);
            float ss = (p.size.getValue() * 4.8f) * (0.55f + 0.70f * fade);

            float rr = r0, gg = g0, bb = b0;
            if (fire) {
                float heat = MathHelper.clamp(0.22f + 0.78f * fade, 0.0f, 1.0f);
                rr = rr * (1.0f - heat) + 1.0f * heat;
                gg = gg * (1.0f - heat) + 0.78f * heat;
                bb = bb * (1.0f - heat) + 0.32f * heat;
                a = MathHelper.clamp(a * 1.18f, 0.0f, 1.0f);
            }

            int color = (Math.round(a * 255f) << 24)
                    | (Math.round(rr * 255f) << 16)
                    | (Math.round(gg * 255f) << 8)
                    | Math.round(bb * 255f);

            addBillboard((float) (tp.pos.x - frameCamPos.x),
                    (float) (tp.pos.y - frameCamPos.y),
                    (float) (tp.pos.z - frameCamPos.z), ss, color);
            k++;
        }
    }

    private void flushBatches() {
        MinecraftClient mc = p.mc;
        if (mc == null) return;
        Framebuffer main = mc.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null || main.getDepthAttachmentView() == null) {
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice transforms = Pipelines.writeWorldTransforms();

        if (meshVertices > 0) {
            meshBuffer.flip();
            GpuBuffer verts = RenderSystem.getDevice().createBuffer(
                    () -> "linkenvisuals:cosmetic_spheres", GpuBuffer.USAGE_VERTEX, meshBuffer);
            try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:cosmetic_spheres",
                    main.getColorAttachmentView(), OptionalInt.empty(),
                    main.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(SPHERE_MESH);
                Pipelines.bindWorldUniforms(pass, transforms);
                pass.setVertexBuffer(0, verts);
                pass.draw(0, meshVertices);
            } finally {
                verts.close();
                meshBuffer.clear();
                meshVertices = 0;
            }
        }

        if (bloomVertices > 0 || billboardVertices > 0) {
            if (bloomVertices > 0) {
                drawBillboardPass(encoder, main, transforms, bloomBuffer, bloomVertices,
                        SPHERE_BLOOM, "cosmetic_sphere_bloom");
                bloomBuffer.clear();
                bloomVertices = 0;
            }
            if (billboardVertices > 0) {
                // Trails use the same procedural radial glow as the bloom: the old
                // ghost_bloom texture carries an opaque black border that turned every
                // stacked trail billboard into a dark disc around the sphere.
                drawBillboardPass(encoder, main, transforms, billboardBuffer, billboardVertices,
                        SPHERE_BLOOM, "cosmetic_billboards");
                billboardBuffer.clear();
                billboardVertices = 0;
            }
        }
    }

    private void drawBillboardPass(CommandEncoder encoder, Framebuffer main, GpuBufferSlice transforms,
                                   ByteBuffer buffer, int vertices, RenderPipeline pipeline, String name) {
        buffer.flip();
        GpuBuffer verts = RenderSystem.getDevice().createBuffer(
                () -> "linkenvisuals:" + name, GpuBuffer.USAGE_VERTEX, buffer);
        try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:" + name,
                main.getColorAttachmentView(), OptionalInt.empty(),
                main.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            Pipelines.bindWorldUniforms(pass, transforms);
            pass.setVertexBuffer(0, verts);
            pass.draw(0, vertices);
        } finally {
            verts.close();
        }
    }

    private void syncOffsetEditor() {
        int idx = getEditIndex();

        if (idx != lastEditIdx) {
            lastEditIdx = idx;
            if (idx < 0) {
                p.editLR.setValue(allLR);
                p.editFB.setValue(allFB);
            } else {
                p.editLR.setValue(offLR[idx]);
                p.editFB.setValue(offFB[idx]);
            }
            return;
        }

        float vLR = p.editLR.getValue();
        float vFB = p.editFB.getValue();

        if (idx < 0) {
            if (!eq(vLR, allLR) || !eq(vFB, allFB)) {
                allLR = vLR;
                allFB = vFB;
            }
        } else {
            if (!eq(vLR, offLR[idx]) || !eq(vFB, offFB[idx])) {
                offLR[idx] = vLR;
                offFB[idx] = vFB;
            }
        }
    }

    private boolean eq(float a, float b) {
        return Math.abs(a - b) <= 1.0E-4f;
    }

    private int getEditIndex() {
        if (p.sphereEditor.getValue() == CosmeticEnums.SphereEditor.EXORT) return 0;
        if (p.sphereEditor.getValue() == CosmeticEnums.SphereEditor.WEX) return 1;
        if (p.sphereEditor.getValue() == CosmeticEnums.SphereEditor.QUAS) return 2;
        return -1;
    }

    /**
     * Кэш единичной сферы: тригонометрия широт/долгот и позиции узлов сетки считаются один раз
     * (stacks/slices — константы). Позиция узла не зависит от радиуса и цвета, поэтому общая для
     * всех сфер и кадров; при отрисовке позиция вершины = единичная позиция * radius.
     * Аналог TorusCache в Nimb.
     */
    private static final class SphereCache {
        static final int STACKS = 18;
        static final int SLICES = 28;

        int rows; // узловых широт = STACKS + 1 (19)
        int cols; // долгот = SLICES (28); шов замыкается на 0-ю колонку
        // Единичные позиции узлов (они же нормали) — переиспользуются между кадрами, без аллокаций.
        float[] ux;
        float[] uy;
        float[] uz;
        // Пер-узловой буфер цвета (шейд один раз на узел на кадр).
        int[] nodeColor;

        void reset() {
            rows = 0;
            cols = 0;
            ux = null;
            uy = null;
            uz = null;
            nodeColor = null;
        }

        void ensure() {
            if (ux != null) return;

            rows = STACKS + 1;
            cols = SLICES;
            int n = rows * cols;

            ux = new float[n];
            uy = new float[n];
            uz = new float[n];
            nodeColor = new int[n];

            int idx = 0;
            for (int i = 0; i < rows; i++) {
                double phi = ((double) i / (double) STACKS) * Math.PI - (Math.PI / 2.0);
                float sy = (float) Math.sin(phi);
                float sr = (float) Math.cos(phi);
                for (int j = 0; j < cols; j++, idx++) {
                    double th = ((double) j / (double) SLICES) * (Math.PI * 2.0);
                    ux[idx] = (float) (Math.cos(th) * sr);
                    uy[idx] = sy;
                    uz[idx] = (float) (Math.sin(th) * sr);
                }
            }
        }
    }

    private static final class TrailState {
        final ArrayDeque<TrailPoint>[] queues;

        @SuppressWarnings("unchecked")
        TrailState(int count) {
            queues = new ArrayDeque[count];
            for (int i = 0; i < count; i++) queues[i] = new ArrayDeque<>();
        }
    }

    private static final class TrailPoint {
        final Vec3d pos;
        final long time;

        TrailPoint(Vec3d pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }
}

