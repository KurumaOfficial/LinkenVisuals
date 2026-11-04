package ru.method.linkenvisuals.cosmetics.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRenderWorld;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.cosmetics.model.CosmeticEffectType;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.utils.Wrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Рендер эффект-косметик вокруг игрока (нимб, орбитальные сферы, шлейф) в мировом пространстве.
 * Источник выбора и цвета — {@link CosmeticsService} (локальный профиль + синхронизированные игроки).
 * Порт визуалов из набора эффект-косметик, адаптирован под камеру-relative матрицу мода (как ChinaHat).
 */
public final class CosmeticEffectRenderer implements Wrapper {

    private static final CosmeticEffectRenderer INSTANCE = new CosmeticEffectRenderer();

    private static final int HALO_MAJOR_SEG = 96;
    private static final int HALO_MINOR_SEG = 20;
    private static final int SPHERE_COUNT = 4;
    private static final int TRAIL_MAX_POINTS = 24;

    private final TorusGeometry torus = new TorusGeometry();
    private final Quaternionf scratchQuat = new Quaternionf();
    private final Map<UUID, Deque<Vec3d>> trailHistory = new HashMap<>();

    private CosmeticEffectRenderer() {
    }

    public static CosmeticEffectRenderer getInstance() {
        return INSTANCE;
    }

    // Рисуем в EventRenderWorld (постится ПОСЛЕ прохода FogBlur), иначе нимб/сферы/шлейф размывались блюром.
    @EventHandler
    public void onRenderWorld(EventRenderWorld event) {
        // Старая серверная косметика слота EFFECT (нимб/шары/шлейф) ОТКЛЮЧЕНА: вкладку EFFECT убрали из
        // меню, а сами эффекты теперь живут в движке модуля «Косметика» (CosmeticEffects). Без этого
        // ранее надетый EFFECT (например «шары») продолжал рисоваться, и выключить его было негде.
        if (true) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        CosmeticsService service = instance == null ? null : instance.getCosmeticsService();
        if (service == null || !service.isEnabled()) {
            return;
        }

        float tickDelta = event.getTickCounter().getTickProgress(false);
        MatrixStack matrices = event.getMatrixStack();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player.isInvisible()) {
                continue;
            }
            UUID uuid = player.getGameProfile() == null ? null : player.getGameProfile().id();
            String name = player.getGameProfile() == null ? null : player.getGameProfile().name();
            if (!service.shouldRender(uuid, name)) {
                continue;
            }
            boolean isSelf = player == mc.player;

            CosmeticDefinition def = service.resolveDefinition(CosmeticSlot.EFFECT, uuid, name);
            if (!def.hasEffect()) {
                if (uuid != null) {
                    trailHistory.remove(uuid);
                }
                continue;
            }
            int color = service.resolveTint(CosmeticSlot.EFFECT, uuid, name);
            // Дефолтный белый тинт смотрится бледно — по умолчанию красим эффект в акцент темы.
            if ((color & 0x00FFFFFF) == 0x00FFFFFF) {
                color = 0xFF000000 | (ru.method.linkenvisuals.utils.render.ColorUtils.getPrimaryColor().getRGB() & 0x00FFFFFF);
            }

            switch (def.effectType()) {
                case HALO -> {
                    if (isSelf && firstPerson) break;
                    renderHalo(matrices, camPos, player, tickDelta, color);
                }
                case SPHERES -> {
                    if (isSelf && firstPerson) break;
                    renderSpheres(matrices, camera, camPos, player, tickDelta, color);
                }
                case TRAIL -> renderTrail(matrices, camera, camPos, player, tickDelta, color, uuid);
                default -> {
                }
            }
        }
    }

    // ============================ HALO ============================

    private void renderHalo(MatrixStack matrices, Vec3d camPos, PlayerEntity player, float tickDelta, int color) {
        Vec3d pos = player.getLerpedPos(tickDelta);
        double headTop = player.getHeight() + 0.30;
        float t = (System.currentTimeMillis() % 1_000_000L) / 1000.0f;
        double bob = Math.sin(t * 2.6f) * 0.03;
        float bodyYaw = MathHelper.lerp(tickDelta, player.lastBodyYaw, player.bodyYaw);

        float majorR = 0.32f;
        float tubeR = 0.045f;

        Vec3d center = new Vec3d(pos.x, pos.y + headTop + bob, pos.z);
        Vec3d viewDir = camPos.subtract(center);
        double vlen = viewDir.length();
        viewDir = vlen > 1.0E-6 ? viewDir.multiply(1.0 / vlen) : new Vec3d(0, 0, 1);
        Vec3d lightDir = new Vec3d(0.35, 1.0, 0.25).normalize();

        torus.ensure(HALO_MAJOR_SEG, HALO_MINOR_SEG);

        matrices.push();
        matrices.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
        matrices.multiply(scratchQuat.identity().rotateY((float) Math.toRadians(-bodyYaw)));

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        drawHaloTorus(matrices, majorR, tubeR, color, viewDir, lightDir);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        drawHaloTorus(matrices, majorR, tubeR * 1.55f, withAlphaMul(color, 0.34f), viewDir, lightDir);
        drawHaloTorus(matrices, majorR, tubeR * 2.35f, withAlphaMul(color, 0.16f), viewDir, lightDir);

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        matrices.pop();
    }

    private void drawHaloTorus(MatrixStack matrices, float majorR, float tubeR, int argb, Vec3d viewDir, Vec3d lightDir) {
        int a0 = (argb >>> 24) & 0xFF;
        int r0 = (argb >>> 16) & 0xFF;
        int g0 = (argb >>> 8) & 0xFF;
        int b0 = argb & 0xFF;

        int majorSeg = torus.majorSeg;
        int minorSeg = torus.minorSeg;
        float[] mc = torus.majorCos, ms = torus.majorSin, nc = torus.minorCos, ns = torus.minorSin;

        Matrix4f m = matrices.peek().getPositionMatrix();
        var immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        var layer = ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_QUADS_DEPTH;
        VertexConsumer bb = immediate.getBuffer(layer);

        for (int i = 0; i < majorSeg; i++) {
            int i1 = (i + 1) % majorSeg;
            float ca0 = mc[i], sa0 = ms[i], ca1 = mc[i1], sa1 = ms[i1];
            for (int j = 0; j < minorSeg; j++) {
                int j1 = (j + 1) % minorSeg;
                float cb0 = nc[j], sb0 = ns[j], cb1 = nc[j1], sb1 = ns[j1];

                float ring0 = majorR + tubeR * cb0;
                float ring1 = majorR + tubeR * cb1;

                float xA = ring0 * ca0, yA = tubeR * sb0, zA = ring0 * sa0;
                float xB = ring0 * ca1, yB = tubeR * sb0, zB = ring0 * sa1;
                float xC = ring1 * ca1, yC = tubeR * sb1, zC = ring1 * sa1;
                float xD = ring1 * ca0, yD = tubeR * sb1, zD = ring1 * sa0;

                int cA = shade(cb0 * ca0, sb0, cb0 * sa0, viewDir, lightDir, r0, g0, b0, a0);
                int cB = shade(cb0 * ca1, sb0, cb0 * sa1, viewDir, lightDir, r0, g0, b0, a0);
                int cC = shade(cb1 * ca1, sb1, cb1 * sa1, viewDir, lightDir, r0, g0, b0, a0);
                int cD = shade(cb1 * ca0, sb1, cb1 * sa0, viewDir, lightDir, r0, g0, b0, a0);

                bb.vertex(m, xA, yA, zA).color(cA);
                bb.vertex(m, xB, yB, zB).color(cB);
                bb.vertex(m, xC, yC, zC).color(cC);
                bb.vertex(m, xD, yD, zD).color(cD);
            }
        }
        immediate.draw(layer);
    }

    private int shade(float nx, float ny, float nz, Vec3d viewDir, Vec3d lightDir, int r0, int g0, int b0, int a0) {
        float dv = clamp01((float) (nx * viewDir.x + ny * viewDir.y + nz * viewDir.z));
        float dl = clamp01(((float) (nx * lightDir.x + ny * lightDir.y + nz * lightDir.z) + 1.0f) * 0.5f);
        float rim = (1.0f - dv) * (1.0f - dv);
        float dv2 = dv * dv, dv4 = dv2 * dv2, dv8 = dv4 * dv4, dv10 = dv8 * dv2;
        float light = Math.min(1.0f, 0.22f + 0.68f * dl + rim * 0.30f + dv10 * 0.20f);
        int r = clamp255((int) (r0 * light));
        int g = clamp255((int) (g0 * light));
        int b = clamp255((int) (b0 * light));
        return (a0 << 24) | (r << 16) | (g << 8) | b;
    }

    // ============================ SPHERES ============================

    private void renderSpheres(MatrixStack matrices, Camera camera, Vec3d camPos, PlayerEntity player, float tickDelta, int color) {
        Vec3d pos = player.getLerpedPos(tickDelta);
        double cy = pos.y + player.getHeight() * 0.55;
        float spin = (float) (((System.currentTimeMillis() % 100_000L) / 1000.0) * 1.4);
        float orbitR = 0.7f, size = 0.10f;

        // Биллборд: правый/верхний вектор камеры — чтобы сферы-квадраты всегда смотрели на камеру.
        Quaternionf rot = camera.getRotation();
        Vector3f right = rot.transform(new Vector3f(1f, 0f, 0f), new Vector3f());
        Vector3f up = rot.transform(new Vector3f(0f, 1f, 0f), new Vector3f());

        int a = (color >>> 24) & 0xFF;
        float r = ((color >>> 16) & 0xFF) / 255f, g = ((color >>> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;

        matrices.push();
        matrices.translate(pos.x - camPos.x, cy - camPos.y, pos.z - camPos.z);
        Matrix4f m = matrices.peek().getPositionMatrix();
        var immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        var sphereLayer = ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_QUADS_SHINE_DEPTH;
        VertexConsumer bb = immediate.getBuffer(sphereLayer);

        for (int s = 0; s < SPHERE_COUNT; s++) {
            double ang = spin + s * (Math.PI * 2.0 / SPHERE_COUNT);
            float ox = (float) (Math.cos(ang) * orbitR);
            float oz = (float) (Math.sin(ang) * orbitR);
            float oy = (float) (Math.sin(spin * 1.7 + s) * 0.14);
            // мягкое внешнее свечение → среднее → яркое ядро = округлая светящаяся сфера.
            billboardCircle(bb, m, ox, oy, oz, right, up, size * 2.2f, r, g, b, Math.round(a * 0.14f), 14);
            billboardCircle(bb, m, ox, oy, oz, right, up, size * 1.4f, r, g, b, Math.round(a * 0.30f), 14);
            billboardCircle(bb, m, ox, oy, oz, right, up, size, r, g, b, a, 14);
            billboardCircle(bb, m, ox, oy, oz, right, up, size * 0.45f, 1f, 1f, 1f, Math.round(a * 0.85f), 12);
        }
        immediate.draw(sphereLayer);

        matrices.pop();
    }

    private void billboardCircle(VertexConsumer bb, Matrix4f m, float cx, float cy, float cz,
                                 Vector3f right, Vector3f up, float radius, float r, float g, float b, int a, int segments) {
        float af = Math.max(0, Math.min(255, a)) / 255f;
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0) * i / segments;
            double a1 = (Math.PI * 2.0) * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            bb.vertex(m, cx, cy, cz).color(r, g, b, af);
            bb.vertex(m, cx + (right.x * c0 + up.x * s0) * radius, cy + (right.y * c0 + up.y * s0) * radius, cz + (right.z * c0 + up.z * s0) * radius).color(r, g, b, af);
            bb.vertex(m, cx + (right.x * c1 + up.x * s1) * radius, cy + (right.y * c1 + up.y * s1) * radius, cz + (right.z * c1 + up.z * s1) * radius).color(r, g, b, af);
            bb.vertex(m, cx, cy, cz).color(r, g, b, af);
        }
    }

    // ============================ TRAIL ============================

    private void renderTrail(MatrixStack matrices, Camera camera, Vec3d camPos, PlayerEntity player, float tickDelta, int color, UUID uuid) {
        if (uuid == null) {
            return;
        }
        Vec3d pos = player.getLerpedPos(tickDelta);
        Vec3d head = new Vec3d(pos.x, pos.y + player.getHeight() * 0.5, pos.z);
        Deque<Vec3d> history = trailHistory.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        Vec3d last = history.peekFirst();
        if (last == null || last.squaredDistanceTo(head) > 0.0009) {
            history.addFirst(head);
            while (history.size() > TRAIL_MAX_POINTS) {
                history.removeLast();
            }
        }
        if (history.size() < 2) {
            return;
        }

        Quaternionf rot = camera.getRotation();
        Vector3f up = rot.transform(new Vector3f(0f, 1f, 0f), new Vector3f());

        int baseR = (color >>> 16) & 0xFF, baseG = (color >>> 8) & 0xFF, baseB = color & 0xFF;
        int baseA = (color >>> 24) & 0xFF;
        float r = baseR / 255f, g = baseG / 255f, b = baseB / 255f;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f m = matrices.peek().getPositionMatrix();
        var immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer bb = immediate.getBuffer(RenderLayers.lines());

        int n = history.size();
        int idx = 0;
        Vec3d prev = null;
        for (Vec3d p : history) {
            float frac = 1.0f - (idx / (float) (n - 1));
            float a = (baseA / 255f) * frac * frac;
            if (prev != null) {
                float dx = (float) (p.x - prev.x);
                float dy = (float) (p.y - prev.y);
                float dz = (float) (p.z - prev.z);
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 1.0e-5f) {
                    dx = 0.0f;
                    dy = 1.0f;
                    dz = 0.0f;
                    length = 1.0f;
                }
                float nx = dx / length;
                float ny = dy / length;
                float nz = dz / length;
                bb.vertex(m, (float) prev.x, (float) prev.y, (float) prev.z).color(r, g, b, a)
                    .normal(nx, ny, nz).lineWidth(1.0f);
                bb.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a)
                    .normal(nx, ny, nz).lineWidth(1.0f);
            }
            prev = p;
            idx++;
        }
        immediate.draw();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        matrices.pop();
    }

    // ============================ helpers ============================

    private static int withAlphaMul(int argb, float mul) {
        int a0 = (argb >>> 24) & 0xFF;
        int a = MathHelper.clamp((int) (a0 * mul), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static final class TorusGeometry {
        int majorSeg, minorSeg;
        float[] majorCos, majorSin, minorCos, minorSin;

        void ensure(int major, int minor) {
            if (major == majorSeg && minor == minorSeg && majorCos != null) {
                return;
            }
            majorSeg = Math.max(12, major);
            minorSeg = Math.max(8, minor);
            majorCos = new float[majorSeg];
            majorSin = new float[majorSeg];
            minorCos = new float[minorSeg];
            minorSin = new float[minorSeg];
            for (int i = 0; i < majorSeg; i++) {
                double a = (i / (double) majorSeg) * Math.PI * 2.0;
                majorCos[i] = (float) Math.cos(a);
                majorSin[i] = (float) Math.sin(a);
            }
            for (int j = 0; j < minorSeg; j++) {
                double b = (j / (double) minorSeg) * Math.PI * 2.0;
                minorCos[j] = (float) Math.cos(b);
                minorSin[j] = (float) Math.sin(b);
            }
        }
    }
}
