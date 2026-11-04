package ru.method.linkenvisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

import java.awt.Color;

/**
 * Кольцо радиуса удара вокруг игрока: вращается, краснеет когда рядом противник.
 * Рисуется плоской ЛЕНТОЙ (TRIANGLE_STRIP с внутренним/внешним радиусом), а не линией —
 * иначе толщина не работает: GL-линии драйвер клампит в 1px.
 */
public class HitRange extends Module {
    private static final int SIDES = 72;
    private static final float HIT_RADIUS = 3.32f;
    private static final float Y_OFFSET = 0.02f;

    private final ColorSetting color = new ColorSetting("settings.hitrange.color", new Color(80, 180, 255, 200));
    private final NumberSetting thickness = new NumberSetting("settings.hitrange.thickness", 2.0f, 0.5f, 8.0f, 0.1f);
    private final NumberSetting rotSpeed = new NumberSetting("settings.hitrange.rotspeed", 0.4f, 0.05f, 2.0f, 0.05f);
    private final BooleanSetting depthTest = new BooleanSetting("settings.hitrange.depth", true);

    private float rotationAngle = 0f;

    public HitRange() {
        super("HitRange", Category.Visuals);
        getSettings().add(color);
        getSettings().add(thickness);
        getSettings().add(rotSpeed);
        getSettings().add(depthTest);
    }

    @EventHandler
    private void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;

        boolean enemyNear = false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            // Невидимых (зелье инвиза) и спектаторов игнорируем: иначе покраснение кольца выдавало
            // инвиза, которого игрок видеть не должен.
            if (p.isInvisible() || p.isSpectator()) continue;
            if (p.distanceTo(mc.player) <= HIT_RADIUS) {
                enemyNear = true;
                break;
            }
        }

        rotationAngle = (rotationAngle + rotSpeed.getValue()) % 360f;

        float tickDelta = event.getTickCounter().getTickProgress(true);
        Vec3d origin = mc.player.getLerpedPos(tickDelta);
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        float cx = (float) (origin.x - cam.x);
        float cy = (float) (origin.y + Y_OFFSET - cam.y);
        float cz = (float) (origin.z - cam.z);

        Color base = enemyNear ? new Color(255, 45, 45, 220) : color.getValue();
        // Толщина (0.5..8) → ширина ленты в блоках. Реальная геометрическая ширина, не GL-линия.
        float halfW = thickness.getValue() * 0.045f;
        boolean depth = depthTest.getValue();

        MatrixStack ms = event.getMatrixStack();
        Matrix4f m = ms.peek().getPositionMatrix();

        Color glow = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.round(base.getAlpha() * 0.22f)));
        drawBand(m, cx, cy, cz, HIT_RADIUS, halfW * 2.6f, glow, depth);
        drawBand(m, cx, cy, cz, HIT_RADIUS, halfW, base, depth);
    }

    private void drawBand(Matrix4f m, float cx, float cy, float cz, float radius, float halfW, Color c, boolean depth) {
        double rot = Math.toRadians(rotationAngle);
        int argb = c.getRGB();
        float outer = radius + halfW;
        float inner = Math.max(0.01f, radius - halfW);
        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        var layer = depth ? ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_QUADS_DEPTH : ru.method.linkenvisuals.utils.render.Render3DPipelines.LAYER_QUADS_NO_DEPTH;
        VertexConsumer vc = immediate.getBuffer(layer);
        for (int i = 0; i < SIDES; i++) {
            double a0 = rot + 2.0 * Math.PI * i / SIDES;
            double a1 = rot + 2.0 * Math.PI * (i + 1) / SIDES;
            float ca0 = (float) Math.cos(a0), sa0 = (float) Math.sin(a0);
            float ca1 = (float) Math.cos(a1), sa1 = (float) Math.sin(a1);

            vc.vertex(m, cx + ca0 * outer, cy, cz + sa0 * outer).color(argb);
            vc.vertex(m, cx + ca0 * inner, cy, cz + sa0 * inner).color(argb);
            vc.vertex(m, cx + ca1 * inner, cy, cz + sa1 * inner).color(argb);
            vc.vertex(m, cx + ca1 * outer, cy, cz + sa1 * outer).color(argb);
        }
        immediate.draw(layer);
    }
}
