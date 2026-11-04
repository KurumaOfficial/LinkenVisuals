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
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class Nimb {

    private final CosmeticEffects p;

    private final TorusCache torusCache = new TorusCache();

    private static final int STRIDE = 16;
    private static final RenderPipeline HALO_BASE = Pipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(LinkenVisuals.id("pipeline/cosmetic_halo"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());
    private static final RenderPipeline HALO_GLOW = Pipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(LinkenVisuals.id("pipeline/cosmetic_halo_glow"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private final ByteBuffer baseBuffer = MemoryUtil.memAlloc(4 * 1024 * 1024);
    private final ByteBuffer glowBuffer = MemoryUtil.memAlloc(8 * 1024 * 1024);
    private int baseVertices;
    private int glowVertices;
    private final Vector3f transformed = new Vector3f();
    private final Matrix4f model = new Matrix4f();

    public Nimb(CosmeticEffects parent) {
        this.p = parent;
    }

    public void deactivate() {
        torusCache.reset();
    }

    public void onWorldRender(EventRender3D.Game e) {
        if (p.mc.world == null || p.mc.player == null) return;
        if (!p.haloOn()) return;

        MatrixStack ms = e.getMatrixStack();
        float pt = e.getTickCounter().getTickProgress(false);
        Camera cam = p.mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getCameraPos();
        boolean camFirst = p.mc.options.getPerspective().isFirstPerson();

        boolean allowOthers = CosmeticEffects.bool(p.haloOthers);
        boolean allowFirst = CosmeticEffects.bool(p.haloFirstPerson);

        double max = Math.max(8.0, Math.min(192.0, p.haloDistance.getValue()));
        double maxSq = max * max;

        ru.method.linkenvisuals.managers.misc.UserSyncManager syncManager =
                LinkenVisuals.getInstance() == null ? null : LinkenVisuals.getInstance().getUserSyncManager();

        for (PlayerEntity pl : p.mc.world.getPlayers()) {
            if (pl == null) continue;
            if (pl.isInvisible()) continue;

            boolean isSelf = pl == p.mc.player;

            if (!isSelf && !allowOthers) continue;
            if (isSelf && camFirst && !allowFirst) continue;

            // У синхронизированного игрока свой нимб (его цвет/размер) — рисует RemotePets,
            // здесь пропускаем, чтобы не было двойного нимба.
            if (!isSelf && syncManager != null && pl.getGameProfile() != null
                    && syncManager.getRemoteHalo(pl.getGameProfile().id(), pl.getGameProfile().name()) != null) {
                continue;
            }

            Vec3d lPos = pl.getLerpedPos(pt);
            double px = lPos.x;
            double py = lPos.y;
            double pz = lPos.z;

            double hy = py + pl.getHeight() + 0.12 + p.haloYOffset.getValue();

            double dx = camPos.x - px;
            double dy = camPos.y - hy;
            double dz = camPos.z - pz;

            if ((dx * dx + dy * dy + dz * dz) > maxSq) continue;

            float bodyYawDeg = MathHelper.lerpAngleDegrees(pt, pl.lastBodyYaw, pl.bodyYaw);
            double bodyYawRad = Math.toRadians(bodyYawDeg);

            renderHalo(ms, cam, pl, px, py, pz, bodyYawRad,
                    p.haloColor.getValue().getRGB(),
                    p.haloRadius.getValue(),
                    p.haloThickness.getValue(),
                    p.haloYOffset.getValue(),
                    CosmeticEffects.bool(p.haloAnimate));
        }
    }

    /**
     * Нимб другого игрока с ЕГО синхронизированными параметрами (цвет/размер/анимация).
     * Вызывается из {@link RemotePets}; качество сетки — по локальной настройке зрителя.
     */
    public void renderRemoteHalo(MatrixStack ms, Camera cam, PlayerEntity player, float pt,
                                 ru.method.linkenvisuals.managers.misc.UserSyncManager.RemoteHaloInfo halo) {
        if (player == null || halo == null) return;
        Vec3d lPos = player.getLerpedPos(pt);
        double px = lPos.x;
        double py = lPos.y;
        double pz = lPos.z;
        float bodyYawDeg = MathHelper.lerpAngleDegrees(pt, player.lastBodyYaw, player.bodyYaw);
        renderHalo(ms, cam, player, px, py, pz, Math.toRadians(bodyYawDeg),
                halo.color, halo.radius, halo.thickness, halo.yOffset, halo.animate);
    }

    private void renderHalo(MatrixStack ms, Camera cam, PlayerEntity player,
                            double px, double py, double pz, double bodyYawRad,
                            int col, float majorRadius, float thickness, float yOffset, boolean animate) {

        double hy = py + player.getHeight() + 0.12 + yOffset;

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000.0f;
        float bobAmp = MathHelper.clamp(thickness * 0.8f, 0.0f, 0.12f);
        if (animate) hy += Math.sin(t * 2.8f) * bobAmp;

        // При «Наруто» корпус своего игрока наклоняется — двигаем якорь нимба вперёд+вниз по тому же
        // наклону вокруг пояса, чтобы нимб следовал за наклонённой головой (а не висел на месте).
        // ТОЛЬКО в 3-м лице (F5): в 1-м лице модель не наклоняется визуально, и сдвиг нимба смотрелся косо —
        // там оставляем нимб ровно над головой.
        if (player == p.mc.player
                && !p.mc.options.getPerspective().isFirstPerson()
                && ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfActive) {
            double pivot = py + ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfPivotY;
            double dh = hy - pivot;
            double th = ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch.selfLeanRad;
            double s = Math.sin(th);
            double c = Math.cos(th);
            double fwdX = -Math.sin(bodyYawRad);
            double fwdZ = Math.cos(bodyYawRad);
            px += fwdX * dh * s;
            pz += fwdZ * dh * s;
            hy = pivot + dh * c;
        }

        float majorR = majorRadius;
        float tubeR = Math.max(0.010f, thickness * 0.5f);

        Vec3d viewDir = cam.getCameraPos().subtract(px, hy, pz);
        double vlen = viewDir.length();
        if (vlen > 1.0E-6) viewDir = viewDir.multiply(1.0 / vlen);
        else viewDir = new Vec3d(0, 0, 1);

        Vec3d lightDir = new Vec3d(0.35, 1.0, 0.25);
        double llen = lightDir.length();
        if (llen > 1.0E-6) lightDir = lightDir.multiply(1.0 / llen);
        else lightDir = new Vec3d(0, 1, 0);

        int majorSeg;
        int minorSeg;
        int glowPasses;

        if (p.haloQuality.getValue() == CosmeticEnums.HaloQuality.LOW) {
            majorSeg = 64;
            minorSeg = 18;
            glowPasses = 1;
        } else if (p.haloQuality.getValue() == CosmeticEnums.HaloQuality.HIGH) {
            majorSeg = 128;
            minorSeg = 30;
            glowPasses = 3;
        } else if (p.haloQuality.getValue() == CosmeticEnums.HaloQuality.ULTRA) {
            majorSeg = 160;
            minorSeg = 36;
            glowPasses = 3;
        } else {
            majorSeg = 96;
            minorSeg = 24;
            glowPasses = 2;
        }

        // Дистанционный LOD: на расстоянии нимб ~0.36м занимает единицы пикселей — плотную сетку и
        // многослойный глоу там не видно, но CPU тратится. vlen (дистанция камера→нимб) уже посчитана
        // выше для viewDir. Модулируем выбор качества по ступеням; ступени дискретны, чтобы torusCache
        // не пересобирался каждый кадр при колебаниях дистанции у границы.
        // Ближе ~12м — полная сетка выбранного качества; 12-32м — ~55% сегментов и ровно 1 глоу-проход;
        // дальше 32м — минимальная сетка (32×10) без глоу.
        if (vlen >= 32.0) {
            majorSeg = 32;
            minorSeg = 10;
            glowPasses = 0;
        } else if (vlen >= 12.0) {
            majorSeg = Math.max(24, (majorSeg * 55) / 100);
            minorSeg = Math.max(10, (minorSeg * 55) / 100);
            glowPasses = Math.min(glowPasses, 1);
        }

        torusCache.ensure(majorSeg, minorSeg);
        // Освещение зависит только от нормалей узлов сетки (i,j) и направлений камеры/света —
        // они одинаковы для базового тора и всех глоу-проходов. Считаем один раз на узел,
        // а не заново на каждый угол каждого квада каждого прохода (было ×16 лишних вызовов).
        computeNodeLights(viewDir, lightDir);

        Vec3d camPos = cam.getCameraPos();
        baseBuffer.clear();
        glowBuffer.clear();
        baseVertices = 0;
        glowVertices = 0;
        model.identity()
                .translate((float) (px - camPos.x), (float) (hy - camPos.y), (float) (pz - camPos.z))
                .rotateY((float) bodyYawRad);

        drawHaloTorusFast(model, majorR, tubeR, col, false);

        if (glowPasses >= 1) {
            int glowA = withAlphaMul(col, 0.34f);
            drawHaloTorusFast(model, majorR, tubeR * 1.55f, glowA, true);
        }
        if (glowPasses >= 2) {
            int glowB = withAlphaMul(col, 0.16f);
            drawHaloTorusFast(model, majorR, tubeR * 2.35f, glowB, true);
        }
        if (glowPasses >= 3) {
            int glowC = withAlphaMul(col, 0.08f);
            drawHaloTorusFast(model, majorR, tubeR * 3.25f, glowC, true);
        }

        flushHalo();
    }

    private void flushHalo() {
        MinecraftClient mc = p.mc;
        if (mc == null) return;
        Framebuffer main = mc.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null || main.getDepthAttachmentView() == null) {
            return;
        }
        if (baseVertices == 0 && glowVertices == 0) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice transforms = Pipelines.writeWorldTransforms();
        GpuBuffer base = null;
        GpuBuffer glow = null;
        try (RenderPass pass = encoder.createRenderPass(() -> "linkenvisuals:cosmetic_halo",
                main.getColorAttachmentView(), OptionalInt.empty(),
                main.getDepthAttachmentView(), OptionalDouble.empty())) {
            Pipelines.bindWorldUniforms(pass, transforms);
            if (baseVertices > 0) {
                baseBuffer.flip();
                base = RenderSystem.getDevice().createBuffer(
                        () -> "linkenvisuals:cosmetic_halo_base", GpuBuffer.USAGE_VERTEX, baseBuffer);
                pass.setPipeline(HALO_BASE);
                pass.setVertexBuffer(0, base);
                pass.draw(0, baseVertices);
            }
            if (glowVertices > 0) {
                glowBuffer.flip();
                glow = RenderSystem.getDevice().createBuffer(
                        () -> "linkenvisuals:cosmetic_halo_glow", GpuBuffer.USAGE_VERTEX, glowBuffer);
                pass.setPipeline(HALO_GLOW);
                pass.setVertexBuffer(0, glow);
                pass.draw(0, glowVertices);
            }
        } finally {
            if (base != null) base.close();
            if (glow != null) glow.close();
            baseBuffer.clear();
            glowBuffer.clear();
            baseVertices = 0;
            glowVertices = 0;
        }
    }

    /**
     * Считает множитель освещения для каждого узла сетки тора (нормаль узла не зависит от tubeR,
     * поэтому результат общий для базового прохода и всех глоу-проходов). Та же математика, что была
     * в shadeHalo, — просто один раз на узел вместо четырёх углов на квад в каждом проходе.
     */
    private void computeNodeLights(Vec3d viewDir, Vec3d lightDir) {
        int majorSeg = torusCache.majorSeg;
        int minorSeg = torusCache.minorSeg;
        float[] majorCos = torusCache.majorCos;
        float[] majorSin = torusCache.majorSin;
        float[] minorCos = torusCache.minorCos;
        float[] minorSin = torusCache.minorSin;
        float[] nodeLight = torusCache.nodeLight;

        float vx = (float) viewDir.x, vy = (float) viewDir.y, vz = (float) viewDir.z;
        float lx = (float) lightDir.x, ly = (float) lightDir.y, lz = (float) lightDir.z;

        int idx = 0;
        for (int i = 0; i < majorSeg; i++) {
            float ca = majorCos[i];
            float sa = majorSin[i];
            for (int j = 0; j < minorSeg; j++, idx++) {
                float cb = minorCos[j];
                float sb = minorSin[j];

                float nx = cb * ca;
                float nz = cb * sa;

                float dv = nx * vx + sb * vy + nz * vz;
                if (dv < 0.0f) dv = 0.0f;
                if (dv > 1.0f) dv = 1.0f;

                float dl = nx * lx + sb * ly + nz * lz;
                dl = (dl + 1.0f) * 0.5f;
                if (dl < 0.0f) dl = 0.0f;
                if (dl > 1.0f) dl = 1.0f;

                float rim = 1.0f - dv;
                rim = rim * rim;

                float dv2 = dv * dv;
                float dv4 = dv2 * dv2;
                float dv8 = dv4 * dv4;
                float dv10 = dv8 * dv2;

                float light = 0.22f + 0.68f * dl + rim * 0.30f + dv10 * 0.20f;
                nodeLight[idx] = light > 1.0f ? 1.0f : light;
            }
        }
    }

    private void drawHaloTorusFast(Matrix4f model, float majorR, float tubeR, int argb, boolean glow) {
        int a0 = (argb >>> 24) & 0xFF;
        int r0 = (argb >>> 16) & 0xFF;
        int g0 = (argb >>> 8) & 0xFF;
        int b0 = argb & 0xFF;

        int majorSeg = torusCache.majorSeg;
        int minorSeg = torusCache.minorSeg;

        float[] majorCos = torusCache.majorCos;
        float[] majorSin = torusCache.majorSin;
        float[] minorCos = torusCache.minorCos;
        float[] minorSin = torusCache.minorSin;
        float[] nodeLight = torusCache.nodeLight;
        float[] nodeX = torusCache.nodeX;
        float[] nodeY = torusCache.nodeY;
        float[] nodeZ = torusCache.nodeZ;
        int[] nodeColor = torusCache.nodeColor;

        int idx = 0;
        int aShift = (a0 & 0xFF) << 24;
        for (int i = 0; i < majorSeg; i++) {
            float ca = majorCos[i];
            float sa = majorSin[i];
            for (int j = 0; j < minorSeg; j++, idx++) {
                float cb = minorCos[j];
                float ring = majorR + tubeR * cb;
                float lx = ring * ca;
                float ly = tubeR * minorSin[j];
                float lz = ring * sa;

                transformed.set(lx, ly, lz);
                model.transformPosition(transformed);
                nodeX[idx] = transformed.x;
                nodeY[idx] = transformed.y;
                nodeZ[idx] = transformed.z;

                float light = nodeLight[idx];
                int r = clamp255((int) (r0 * light));
                int g = clamp255((int) (g0 * light));
                int b = clamp255((int) (b0 * light));
                nodeColor[idx] = aShift | (r << 16) | (g << 8) | b;
            }
        }

        ByteBuffer buffer = glow ? glowBuffer : baseBuffer;
        for (int i = 0; i < majorSeg; i++) {
            int i1 = (i + 1 == majorSeg) ? 0 : i + 1;
            int row0 = i * minorSeg;
            int row1 = i1 * minorSeg;

            for (int j = 0; j < minorSeg; j++) {
                int j1 = (j + 1 == minorSeg) ? 0 : j + 1;

                int iA = row0 + j;
                int iB = row1 + j;
                int iC = row1 + j1;
                int iD = row0 + j1;

                if (!putHalo(buffer, nodeX, nodeY, nodeZ, nodeColor, iA)) return;
                if (!putHalo(buffer, nodeX, nodeY, nodeZ, nodeColor, iB)) return;
                if (!putHalo(buffer, nodeX, nodeY, nodeZ, nodeColor, iC)) return;

                if (!putHalo(buffer, nodeX, nodeY, nodeZ, nodeColor, iA)) return;
                if (!putHalo(buffer, nodeX, nodeY, nodeZ, nodeColor, iC)) return;
                if (!putHalo(buffer, nodeX, nodeY, nodeZ, nodeColor, iD)) return;

                if (glow) glowVertices += 6;
                else baseVertices += 6;
            }
        }
    }

    private boolean putHalo(ByteBuffer buffer, float[] xs, float[] ys, float[] zs, int[] colors, int index) {
        if (buffer.remaining() < STRIDE) return false;
        int argb = colors[index];
        buffer.putFloat(xs[index]).putFloat(ys[index]).putFloat(zs[index]);
        buffer.put((byte) ((argb >> 16) & 0xFF));
        buffer.put((byte) ((argb >> 8) & 0xFF));
        buffer.put((byte) (argb & 0xFF));
        buffer.put((byte) ((argb >>> 24) & 0xFF));
        return true;
    }

    private int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private int withAlphaMul(int argb, float mul) {
        int a0 = (argb >>> 24) & 0xFF;
        int a = MathHelper.clamp((int) (a0 * mul), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static final class TorusCache {
        int majorSeg;
        int minorSeg;
        float[] majorCos;
        float[] majorSin;
        float[] minorCos;
        float[] minorSin;
        // Пер-узловые рабочие буферы (переиспользуются между кадрами — без аллокаций в рендере).
        float[] nodeLight;
        float[] nodeX;
        float[] nodeY;
        float[] nodeZ;
        int[] nodeColor;

        void reset() {
            majorSeg = 0;
            minorSeg = 0;
            majorCos = null;
            majorSin = null;
            minorCos = null;
            minorSin = null;
            nodeLight = null;
            nodeX = null;
            nodeY = null;
            nodeZ = null;
            nodeColor = null;
        }

        void ensure(int major, int minor) {
            if (major == majorSeg && minor == minorSeg && majorCos != null && minorCos != null) return;

            majorSeg = Math.max(12, major);
            minorSeg = Math.max(8, minor);

            majorCos = new float[majorSeg];
            majorSin = new float[majorSeg];
            minorCos = new float[minorSeg];
            minorSin = new float[minorSeg];

            int nodes = majorSeg * minorSeg;
            nodeLight = new float[nodes];
            nodeX = new float[nodes];
            nodeY = new float[nodes];
            nodeZ = new float[nodes];
            nodeColor = new int[nodes];

            for (int i = 0; i < majorSeg; i++) {
                double a = (i / (double) majorSeg) * (Math.PI * 2.0);
                majorCos[i] = (float) Math.cos(a);
                majorSin[i] = (float) Math.sin(a);
            }

            for (int j = 0; j < minorSeg; j++) {
                double b = (j / (double) minorSeg) * (Math.PI * 2.0);
                minorCos[j] = (float) Math.cos(b);
                minorSin[j] = (float) Math.sin(b);
            }
        }
    }
}

