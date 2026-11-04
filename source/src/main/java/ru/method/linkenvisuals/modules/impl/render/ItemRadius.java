package ru.method.linkenvisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.Render3D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.*;

public class ItemRadius extends Module {
    private final BooleanSetting enderEye = new BooleanSetting("modules.settings.itemradius.ender_eye", true);
    private final ColorSetting enderEyeColor = new ColorSetting("modules.settings.itemradius.ender_eye_color", Color.WHITE, enderEye::getValue);
    private final BooleanSetting sugar = new BooleanSetting("modules.settings.itemradius.sugar", true);
    private final ColorSetting sugarColor = new ColorSetting("modules.settings.itemradius.sugar_color", Color.WHITE, sugar::getValue);
    private final BooleanSetting netheriteScrap = new BooleanSetting("modules.settings.itemradius.netherite_scrap", true);
    private final ColorSetting netheriteScrapColor = new ColorSetting("modules.settings.itemradius.netherite_scrap_color", Color.WHITE, netheriteScrap::getValue);
    private final BooleanSetting dragonTrap = new BooleanSetting("modules.settings.itemradius.dragon_trap", false, netheriteScrap::getValue);
    private final BooleanSetting driedKelp = new BooleanSetting("modules.settings.itemradius.dried_kelp", true);
    private final ColorSetting driedKelpColor = new ColorSetting("modules.settings.itemradius.dried_kelp_color", Color.WHITE, driedKelp::getValue);
    private final BooleanSetting snowball = new BooleanSetting("modules.settings.itemradius.snowball", true);
    private final ColorSetting snowballColor = new ColorSetting("modules.settings.itemradius.snowball_color", Color.WHITE, snowball::getValue);
    private final BooleanSetting explosivePotion = new BooleanSetting("modules.settings.itemradius.explosive_potion", true);
    // 4.0 — ванильный горизонтальный радиус действия сплэша (эффект спадает до 0% на краю). Слайдер
    // остаётся калибровочным на случай модифицированных серверов.
    private final NumberSetting explosivePotionRadius = new NumberSetting("modules.settings.itemradius.explosive_potion_radius", 4f, 1f, 30f, 0.5f, explosivePotion::getValue);
    private final ColorSetting explosivePotionColor = new ColorSetting("modules.settings.itemradius.explosive_potion_color", Color.WHITE, explosivePotion::getValue);
    private final BooleanSetting proximityColor = new BooleanSetting("modules.settings.itemradius.proximity_color", true);
    private final ColorSetting proximityColorSetting = new ColorSetting("modules.settings.itemradius.proximity_color_setting", new Color(255, 0, 255, 255), proximityColor::getValue);

    // Кэш симуляции падения зелья: тяжёлый (до 200 raycast'ов), пересчёт раз в тик либо при повороте > 0.5°.
    private Vec3d cachedPotionImpact;
    private int potionCacheAge = -1;
    private float potionCacheYaw = Float.NaN;
    private float potionCachePitch = Float.NaN;

    public ItemRadius() {
        super("ItemRadius", Category.Visuals);
    }

    @EventHandler
    private void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;

        Vec3d playerPos = mc.player.getEntityPos().add(0.0, -0.1, 0.0);

        if (enderEye.getValue() && isHolding(Items.ENDER_EYE)) {
            renderCircle(event, playerPos, 14.0, enderEyeColor.getValue());
        }
        if (sugar.getValue() && isHolding(Items.SUGAR)) {
            renderCircle(event, playerPos, 14.0, sugarColor.getValue());
        }
        if (netheriteScrap.getValue() && isHolding(Items.NETHERITE_SCRAP)) {
            // Драконья трапка: 7×7×7 (half 3.5) вместо обычной 5×5×5 (half 2.5). Низ куба — у ног.
            double h = dragonTrap.getValue() ? 3.5 : 2.5;
            Vec3d center = blockCenter(mc.player.getEntityPos()).add(0.0, h - 0.5, 0.0);
            renderBox(event, center, h, h, h, netheriteScrapColor.getValue(), 2.5);
        }
        if (driedKelp.getValue() && isHolding(Items.DRIED_KELP)) {
            renderDriedKelp(event, driedKelpColor.getValue());
        }
        if (snowball.getValue() && isHolding(Items.SNOWBALL)) {
            Vec3d center = projectedSnowballCenter();
            renderCircle(event, center, 12.0, snowballColor.getValue());
        }
        if (explosivePotion.getValue() && isHolding(Items.SPLASH_POTION)) {
            Vec3d impact = potionImpact();
            if (impact != null) {
                double r = explosivePotionRadius.getValue();
                renderCircle(event, impact, r, explosivePotionColor.getValue());
                renderPotionProximity(event, impact, r);
            }
        }
    }

    /** Точка падения сплэш-зелья (кэш на тик). null — если улетело за пределы симуляции. */
    private Vec3d potionImpact() {
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        if (cachedPotionImpact != null
                && mc.player.age == potionCacheAge
                && Math.abs(MathHelper.wrapDegrees(yaw - potionCacheYaw)) <= 0.5f
                && Math.abs(pitch - potionCachePitch) <= 0.5f) {
            return cachedPotionImpact;
        }
        potionCacheAge = mc.player.age;
        potionCacheYaw = yaw;
        potionCachePitch = pitch;
        cachedPotionImpact = simulatePotionImpact(yaw, pitch);
        return cachedPotionImpact;
    }

    private Vec3d simulatePotionImpact(float yaw, float pitch) {
        if (mc.world == null) return null;
        final double RAD = 0.017453292519943295;

        // Бросок зелья: скорость 0.5, вертикаль со сдвигом roll=-20° (ванильный метательный снаряд).
        double mx = -Math.sin(yaw * RAD) * Math.cos(pitch * RAD);
        double my = -Math.sin((pitch - 20.0) * RAD);
        double mz = Math.cos(yaw * RAD) * Math.cos(pitch * RAD);
        double norm = Math.sqrt(mx * mx + my * my + mz * mz);
        if (norm == 0) return null;
        double speed = 0.5;
        mx = mx / norm * speed;
        my = my / norm * speed;
        mz = mz / norm * speed;

        Vec3d pv = mc.player.getVelocity();
        mx += pv.x;
        mz += pv.z;
        if (!mc.player.isOnGround()) my += pv.y;

        double x = mc.player.getX();
        double y = mc.player.getEyeY() - 0.1;
        double z = mc.player.getZ();

        for (int i = 0; i < 200; i++) {
            // Порядок как в ThrownEntity.tick(): гравитация (0.03) → сопротивление → перемещение.
            my -= 0.03;
            boolean water = mc.world.getBlockState(BlockPos.ofFloored(x, y, z)).isOf(Blocks.WATER);
            double drag = water ? 0.8 : 0.99;
            mx *= drag;
            my *= drag;
            mz *= drag;

            Vec3d cur = new Vec3d(x, y, z);
            Vec3d next = cur.add(mx, my, mz);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    cur, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                return hit.getPos();
            }
            x = next.x;
            y = next.y;
            z = next.z;
            if (y < mc.world.getBottomY()) return null;
        }
        return null;
    }

    /** «NN%» над каждым игроком в радиусе сплэша: доля длительности эффекта = 1 − dist/radius (ванильная
     *  формула близости, на краю круга — 0%). */
    private void renderPotionProximity(EventRender3D.Game event, Vec3d impact, double radius) {
        if (radius <= 0) return;
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        MatrixStack matrices = event.getMatrixStack();
        float fontSize = 10.8f;

        boolean opened = false;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            double dist = Math.sqrt(player.squaredDistanceTo(impact));
            if (dist > radius) continue;
            int pct = (int) Math.round(Math.max(0.0, 1.0 - dist / radius) * 100.0);
            if (pct <= 0) continue;

            if (!opened) {
                opened = true;
            }

            Vec3d pos = new Vec3d(player.getX(), player.getY() + player.getHeight() + 0.35, player.getZ());
            matrices.push();
            matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.scale(-0.025f, -0.025f, 0.025f);

            String text = pct + "%";
            float textWidth = mc.textRenderer.getWidth(text);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
            mc.textRenderer.draw(text, -textWidth / 2f, -mc.textRenderer.fontHeight / 2f, -1, true,
                    matrix, immediate, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0,
                    net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
            immediate.draw();
            matrices.pop();
        }

        if (opened) {
        }
    }

    private void renderDriedKelp(EventRender3D.Game event, Color color) {
        // «Пласт» НЕ должен плавно крутиться за взглядом: снапаем yaw к ближайшим 45° (8 фиксированных
        // направлений по сетке) — стена встаёт ФИКСИРОВАННО, как реально ставит сервер, и не дёргается от
        // малейшего доворота мыши. Точный yaw давал непрерывное вращение стены — это и просили убрать.
        float yaw = Math.round(mc.player.getYaw(1.0f) / 45.0f) * 45.0f;
        double dx = -Math.sin(Math.toRadians(yaw));
        double dz = Math.cos(Math.toRadians(yaw));
        float pitch = mc.player.getPitch();          // +вниз, -вверх
        Vec3d playerBlock = blockCenter(mc.player.getEntityPos());
        if (pitch > 45.0f) {
            // Смотрит ПОД себя — горизонтальный пласт под ногами: 5×5, толщина 2.
            renderBox(event, playerBlock.add(0.0, -1.5, 0.0), 2.5, 1.0, 2.5, color, 2.5);
            return;
        }
        if (pitch < -45.0f) {
            // Смотрит НАД собой — так же как под себя, но над головой: 5×5, толщина 2.
            renderBox(event, playerBlock.add(0.0, 2.5, 0.0), 2.5, 1.0, 2.5, color, 2.5);
            return;
        }
        // Взгляд в БОК — СТЕНА как её реально ставит сервер: диагональная «лесенка» из блоков вдоль
        // перпендикуляра к взгляду (по ТОЧНОМУ yaw). Спереди выглядит гофрированной, сверху — лесенкой.
        // Длина 5 блоков вдоль перпендикуляра, высота 4.
        double px = dz, pz = -dx;                                   // перпендикуляр к взгляду (единичный)
        Vec3d wallOrigin = playerBlock.add(dx * 3.0, 0.0, dz * 3.0); // центр стены — 3 блока вперёд
        renderStaircaseWall(event, wallOrigin, px, pz, 2.5, 4.0, color);
    }

    /** «Пласт»-стена как её ставит сервер: диагональная лесенка из блоков вдоль перпендикуляра (px,pz) к
     *  взгляду. Рисуем ЧИСТЫЙ контур (без сетки на каждом блоке): непрерывные верх/низ периметра +
     *  вертикали ТОЛЬКО в углах поворота стены. Прямой прогон схлопывается в один бокс. */
    private void renderStaircaseWall(EventRender3D.Game event, Vec3d origin, double px, double pz,
                                     double halfLen, double height, Color color) {
        Color fin = resolveColor(origin, halfLen, color);
        double baseY = Math.floor(mc.player.getY());
        double topY = baseY + height;
        java.util.HashSet<Long> cells = new java.util.HashSet<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (double t = -halfLen; t <= halfLen + 1e-6; t += 0.05) {
            int bx = (int) Math.floor(origin.x + px * t);
            int bz = (int) Math.floor(origin.z + pz * t);
            cells.add(cellKey(bx, bz));
            minX = Math.min(minX, bx); maxX = Math.max(maxX, bx);
            minZ = Math.min(minZ, bz); maxZ = Math.max(maxZ, bz);
        }
        // Внешние горизонтальные рёбра (низ+верх): грань клетки рисуем только если соседа с этой стороны нет.
        // Коллинеарные unit-рёбра визуально сливаются — внутренних «слоёв» в стене не остаётся.
        for (long k : cells) {
            int bx = (int) (k >> 32), bz = (int) (k & 0xFFFFFFFFL);
            if (!cells.contains(cellKey(bx - 1, bz))) { wallEdge(event, fin, bx, bz, bx, bz + 1, baseY); wallEdge(event, fin, bx, bz, bx, bz + 1, topY); }
            if (!cells.contains(cellKey(bx + 1, bz))) { wallEdge(event, fin, bx + 1, bz, bx + 1, bz + 1, baseY); wallEdge(event, fin, bx + 1, bz, bx + 1, bz + 1, topY); }
            if (!cells.contains(cellKey(bx, bz - 1))) { wallEdge(event, fin, bx, bz, bx + 1, bz, baseY); wallEdge(event, fin, bx, bz, bx + 1, bz, topY); }
            if (!cells.contains(cellKey(bx, bz + 1))) { wallEdge(event, fin, bx, bz + 1, bx + 1, bz + 1, baseY); wallEdge(event, fin, bx, bz + 1, bx + 1, bz + 1, topY); }
        }
        // Вертикали — только в УГЛАХ периметра (там, где стена поворачивает), не вдоль прямых прогонов.
        for (int vx = minX; vx <= maxX + 1; vx++) {
            for (int vz = minZ; vz <= maxZ + 1; vz++) {
                boolean c00 = cells.contains(cellKey(vx - 1, vz - 1));
                boolean c10 = cells.contains(cellKey(vx, vz - 1));
                boolean c01 = cells.contains(cellKey(vx - 1, vz));
                boolean c11 = cells.contains(cellKey(vx, vz));
                int cnt = (c00 ? 1 : 0) + (c10 ? 1 : 0) + (c01 ? 1 : 0) + (c11 ? 1 : 0);
                if (cnt == 1 || cnt == 3 || (cnt == 2 && ((c00 && c11) || (c10 && c01))))
                    Render3D.drawLine(event.getMatrixStack(), new Vec3d(vx, baseY, vz), new Vec3d(vx, topY, vz), fin, 8.0f, false);
            }
        }
    }

    private static long cellKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private void wallEdge(EventRender3D.Game event, Color c, int x0, int z0, int x1, int z1, double y) {
        Render3D.drawLine(event.getMatrixStack(), new Vec3d(x0, y, z0), new Vec3d(x1, y, z1), c, 8.0f, false);
    }

    private void renderCircle(EventRender3D.Game event, Vec3d center, double radius, Color color) {
        Color finalColor = resolveColor(center, radius, color);
        renderThickCircle(event, center, radius, finalColor, 4.0f);
    }

    private void renderThickCircle(EventRender3D.Game event, Vec3d center, double radius, Color color, float width) {
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            double angle1 = 2.0 * Math.PI * i / segments;
            double angle2 = 2.0 * Math.PI * (i + 1) / segments;
            Vec3d from = new Vec3d(center.x + radius * Math.cos(angle1), center.y, center.z + radius * Math.sin(angle1));
            Vec3d to = new Vec3d(center.x + radius * Math.cos(angle2), center.y, center.z + radius * Math.sin(angle2));
            Render3D.drawLine(event.getMatrixStack(), from, to, color, width, false);
        }
    }

    private void renderBox(EventRender3D.Game event, Vec3d center, double halfX, double halfY, double halfZ, Color color, double proximityRadius) {
        Color finalColor = resolveColor(center, proximityRadius, color);
        Box box = new Box(
                center.x - halfX, center.y - halfY, center.z - halfZ,
                center.x + halfX, center.y + halfY, center.z + halfZ
        );
        Render3D.drawBox(event.getMatrixStack(), box, finalColor, 8.0f, true, false, false);
    }

    private Vec3d projectedSnowballCenter() {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d rotation = mc.player.getRotationVec(1.0f);
        float pitch = mc.player.getPitch(1.0f);
        double pitchRadians = Math.toRadians(pitch);
        double distanceScale = 1.0 + Math.abs(Math.sin(pitchRadians));
        double distance = Math.max(10.0, Math.min(50.0, 20.0 * distanceScale));
        Vec3d projected = eyePos.add(rotation.multiply(distance));
        double y = mc.player.getY();
        if (mc.world != null) {
            y = mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) projected.x, (int) projected.z);
        }
        return new Vec3d(projected.x, y, projected.z);
    }

    private Vec3d blockCenter(Vec3d pos) {
        return new Vec3d(Math.floor(pos.x) + 0.5, Math.floor(pos.y) + 0.5, Math.floor(pos.z) + 0.5);
    }

    private boolean isHolding(Item item) {
        return mc.player.getMainHandStack().isOf(item) || mc.player.getOffHandStack().isOf(item);
    }

    private Color resolveColor(Vec3d center, double radius, Color base) {
        if (proximityColor.getValue() && hasPlayerInRadius(center, radius)) {
            return visibleColor(proximityColorSetting.getValue());
        }
        return visibleColor(base);
    }

    private boolean hasPlayerInRadius(Vec3d center, double radius) {
        double squared = radius * radius;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.squaredDistanceTo(center) <= squared) return true;
        }
        return false;
    }

    private Color visibleColor(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(color.getAlpha(), 210));
    }
}
