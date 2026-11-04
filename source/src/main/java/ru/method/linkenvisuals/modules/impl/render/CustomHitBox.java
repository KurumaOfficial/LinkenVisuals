package ru.method.linkenvisuals.modules.impl.render;


import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;


public class CustomHitBox extends Module {

    // 128, а не 64: на серверах игроки видны и на 70–120 блоках, обрыв хитбоксов на 64 был бы заметен.
    // Дальние сущности уже дёшевы: фрустум-отсечка и 120мс-кэш видимости стоят до рейкастов.
    private static final double MAX_RENDER_DISTANCE_SQ = 128.0 * 128.0;
    // LOS-кэш ~2-3 тика: до 11 raycast'ов на скрытую сущность каждый кадр слишком дорого,
    // а видимость за 120 мс заметно не меняется.
    private static final long VISIBILITY_CACHE_MS = 120;
    private final Map<Integer, long[]> visibilityCache = new HashMap<>(); // entityId -> {expiresMs, visible 0/1}

    private final BooleanSetting fill = new BooleanSetting("settings.customhitbox.fill", true);
    private final NumberSetting fillAlpha = new NumberSetting("settings.customhitbox.fill_alpha", 90f, 0f, 255f, 1f, () -> fill.getValue());
    private final NumberSetting lineWidth = new NumberSetting("settings.customhitbox.line_width", 1.5f, 1f, 8f, 0.5f);
    private final ColorSetting color = new ColorSetting("settings.customhitbox.color", ColorUtils.getGlobalColor());
    private final BooleanSetting showSelf = new BooleanSetting("settings.customhitbox.self", false);
    private final BooleanSetting showItems = new BooleanSetting("settings.customhitbox.items", false);

    public CustomHitBox() {
        super("CustomHitBox", Category.Visuals);
        getSettings().add(fill);
        getSettings().add(fillAlpha);
        getSettings().add(lineWidth);
        getSettings().add(color);
        getSettings().add(showSelf);
        getSettings().add(showItems);
    }

    public boolean shouldRenderSelf() {
        return showSelf.getValue();
    }

    @EventHandler
    private void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;

        float tickDelta = event.getTickCounter().getTickProgress(true);
        MatrixStack stableMatrices = new MatrixStack();
        renderCustomHitBoxes(stableMatrices, tickDelta);
    }

    private void renderCustomHitBoxes(MatrixStack matrices, float tickDelta) {
        Color baseTheme = color.getValue();

        for (Entity entity : mc.world.getEntities()) {
            boolean isSelf = mc.player != null && entity.getUuid().equals(mc.player.getUuid());
            boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();
            if (isSelf && (!showSelf.getValue() || isFirstPerson)) continue;
            if (!showItems.getValue() && entity instanceof ItemEntity) continue;
            // Сферы опыта не подсвечиваем: их много и хитбоксы вокруг них — визуальный мусор.
            if (entity instanceof net.minecraft.entity.ExperienceOrbEntity) continue;
            if (!entity.isAlive() || entity.isRemoved()) continue;
            if (entity.isInvisible()) continue;
            if (entity instanceof LivingEntity living && living.hasStatusEffect(StatusEffects.INVISIBILITY)) continue;

            // Дистанция и фрустум — до рейкастов: обе проверки на порядки дешевле raycast'ов.
            if (mc.player != null && mc.player.squaredDistanceTo(entity) > MAX_RENDER_DISTANCE_SQ) continue;
            Box hitbox = buildRenderHitbox(entity, tickDelta);
            if (!ru.method.linkenvisuals.utils.world.WorldUtils.isBoxVisible(hitbox)) continue;
            if (!isVisibleToPlayer(entity, hitbox, tickDelta)) continue;

            int fillAlphaValue = MathHelper.clamp(Math.round(fillAlpha.getValue()), 0, 255);
            float baseAlphaScale = baseTheme.getAlpha() / 255f;
            int combinedFillAlpha = MathHelper.clamp(Math.round(fillAlphaValue * baseAlphaScale), 0, 255);

            if (fill.getValue() && combinedFillAlpha > 0) {
                Color fillColor = new Color(baseTheme.getRed(), baseTheme.getGreen(), baseTheme.getBlue(), combinedFillAlpha);
                renderFilledBoxDepth(matrices, hitbox, fillColor);
            }

            Color outlineColor = new Color(baseTheme.getRed(), baseTheme.getGreen(), baseTheme.getBlue(), baseTheme.getAlpha());
            renderOutlineDepth(matrices, hitbox, outlineColor, lineWidth.getValue());
        }
    }

    private Box buildRenderHitbox(Entity entity, float tickDelta) {
        Vec3d lerpedPos = entity.getLerpedPos(tickDelta);
        Vec3d pos = entity.getEntityPos();
        Vec3d offset = lerpedPos.subtract(pos);
        return entity.getBoundingBox().offset(offset);
    }

    private boolean isVisibleToPlayer(Entity entity, Box hitbox, float tickDelta) {
        if (mc.player == null || mc.world == null) return false;

        long now = System.currentTimeMillis();
        long[] cached = visibilityCache.get(entity.getId());
        if (cached != null && now < cached[0]) return cached[1] != 0;

        Vec3d playerEye = mc.player.getCameraPosVec(tickDelta);
        boolean visible = computeVisibility(playerEye, hitbox);

        if (visibilityCache.size() > 512) visibilityCache.clear(); // ponytail: тотальная эвикция вместо LRU, id сущностей копятся всю сессию
        visibilityCache.put(entity.getId(), new long[]{now + VISIBILITY_CACHE_MS, visible ? 1 : 0});
        return visible;
    }

    private boolean computeVisibility(Vec3d playerEye, Box hitbox) {
        // Сначала только центр: для видимой сущности достаточно одного рейкаста.
        if (hasLineOfSight(playerEye, hitbox.getCenter())) return true;
        for (Vec3d point : getVisibilityPoints(hitbox)) {
            if (hasLineOfSight(playerEye, point)) {
                return true;
            }
        }
        return false;
    }

    private Vec3d[] getVisibilityPoints(Box hitbox) {
        double centerX = (hitbox.minX + hitbox.maxX) * 0.5;
        double centerY = (hitbox.minY + hitbox.maxY) * 0.5;
        double centerZ = (hitbox.minZ + hitbox.maxZ) * 0.5;
        double insetX = Math.max(0.0, (hitbox.maxX - hitbox.minX) * 0.08);
        double insetZ = Math.max(0.0, (hitbox.maxZ - hitbox.minZ) * 0.08);
        double minX = hitbox.minX + insetX;
        double maxX = hitbox.maxX - insetX;
        double minZ = hitbox.minZ + insetZ;
        double maxZ = hitbox.maxZ - insetZ;
        double minY = hitbox.minY + 0.05;
        double maxY = hitbox.maxY - 0.05;

        // Центр проверяется отдельно в computeVisibility — сюда только запасные точки.
        return new Vec3d[]{
                new Vec3d(centerX, maxY, centerZ),
                new Vec3d(centerX, minY, centerZ),
                new Vec3d(minX, maxY, minZ),
                new Vec3d(minX, maxY, maxZ),
                new Vec3d(maxX, maxY, minZ),
                new Vec3d(maxX, maxY, maxZ),
                new Vec3d(minX, centerY, centerZ),
                new Vec3d(maxX, centerY, centerZ),
                new Vec3d(centerX, centerY, minZ),
                new Vec3d(centerX, centerY, maxZ)
        };
    }

    private boolean hasLineOfSight(Vec3d playerEye, Vec3d targetPoint) {
        RaycastContext context = new RaycastContext(
                playerEye,
                targetPoint,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        BlockHitResult bhr = mc.world.raycast(context);
        if (bhr == null) return true;
        if (bhr.getType() == HitResult.Type.MISS) return true;
        if (bhr.getType() == HitResult.Type.BLOCK) {
            double hitDist = bhr.getPos().squaredDistanceTo(playerEye);
            double tgtDist = targetPoint.squaredDistanceTo(playerEye);
            return hitDist >= tgtDist - 1e-3;
        }
        return true;
    }

    private void renderFilledBoxDepth(MatrixStack matrices, Box box, Color color) {
        Vec3d min = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d max = new Vec3d(box.maxX, box.maxY, box.maxZ);
        Vec3d v000 = new Vec3d(min.x, min.y, min.z);
        Vec3d v001 = new Vec3d(min.x, min.y, max.z);
        Vec3d v010 = new Vec3d(min.x, max.y, min.z);
        Vec3d v011 = new Vec3d(min.x, max.y, max.z);
        Vec3d v100 = new Vec3d(max.x, min.y, min.z);
        Vec3d v101 = new Vec3d(max.x, min.y, max.z);
        Vec3d v110 = new Vec3d(max.x, max.y, min.z);
        Vec3d v111 = new Vec3d(max.x, max.y, max.z);

        Render3D.renderTriangle(matrices, v010, v011, v111, color, true);
        Render3D.renderTriangle(matrices, v010, v111, v110, color, true);

        Render3D.renderTriangle(matrices, v100, v101, v001, color, true);
        Render3D.renderTriangle(matrices, v100, v001, v000, color, true);

        Render3D.renderTriangle(matrices, v000, v001, v011, color, true);
        Render3D.renderTriangle(matrices, v000, v011, v010, color, true);

        Render3D.renderTriangle(matrices, v110, v111, v101, color, true);
        Render3D.renderTriangle(matrices, v110, v101, v100, color, true);

        Render3D.renderTriangle(matrices, v100, v110, v010, color, true);
        Render3D.renderTriangle(matrices, v100, v010, v000, color, true);

        Render3D.renderTriangle(matrices, v001, v011, v111, color, true);
        Render3D.renderTriangle(matrices, v001, v111, v101, color, true);
    }

    private void renderOutlineDepth(MatrixStack matrices, Box box, Color color, float lineWidth) {
        Vec3d min = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d max = new Vec3d(box.maxX, box.maxY, box.maxZ);
        Vec3d v000 = new Vec3d(min.x, min.y, min.z);
        Vec3d v001 = new Vec3d(min.x, min.y, max.z);
        Vec3d v010 = new Vec3d(min.x, max.y, min.z);
        Vec3d v011 = new Vec3d(min.x, max.y, max.z);
        Vec3d v100 = new Vec3d(max.x, min.y, min.z);
        Vec3d v101 = new Vec3d(max.x, min.y, max.z);
        Vec3d v110 = new Vec3d(max.x, max.y, min.z);
        Vec3d v111 = new Vec3d(max.x, max.y, max.z);

        double thickness = Math.max(0.0035, lineWidth * 0.006);
        renderThickEdgeDepth(matrices, v000, v001, thickness, color);
        renderThickEdgeDepth(matrices, v001, v011, thickness, color);
        renderThickEdgeDepth(matrices, v011, v010, thickness, color);
        renderThickEdgeDepth(matrices, v010, v000, thickness, color);

        renderThickEdgeDepth(matrices, v100, v101, thickness, color);
        renderThickEdgeDepth(matrices, v101, v111, thickness, color);
        renderThickEdgeDepth(matrices, v111, v110, thickness, color);
        renderThickEdgeDepth(matrices, v110, v100, thickness, color);

        renderThickEdgeDepth(matrices, v000, v100, thickness, color);
        renderThickEdgeDepth(matrices, v001, v101, thickness, color);
        renderThickEdgeDepth(matrices, v011, v111, thickness, color);
        renderThickEdgeDepth(matrices, v010, v110, thickness, color);
    }

    private void renderThickEdgeDepth(MatrixStack matrices, Vec3d from, Vec3d to, double thickness, Color color) {
        // Ребро — 2 перекрещенных квада (4 треугольника) вместо filled-box (12):
        // втрое меньше геометрии, cull при отрисовке выключен, так что видно с любой стороны.
        double half = thickness * 0.5;
        Vec3d n1;
        Vec3d n2;
        if (Math.abs(to.x - from.x) > 1.0E-6) {          // ребро вдоль X
            n1 = new Vec3d(0, half, 0);
            n2 = new Vec3d(0, 0, half);
        } else if (Math.abs(to.y - from.y) > 1.0E-6) {   // вдоль Y
            n1 = new Vec3d(half, 0, 0);
            n2 = new Vec3d(0, 0, half);
        } else {                                         // вдоль Z
            n1 = new Vec3d(half, 0, 0);
            n2 = new Vec3d(0, half, 0);
        }
        renderEdgeQuad(matrices, from, to, n1, color);
        renderEdgeQuad(matrices, from, to, n2, color);
    }

    private void renderEdgeQuad(MatrixStack matrices, Vec3d from, Vec3d to, Vec3d n, Color color) {
        Vec3d a = from.subtract(n);
        Vec3d b = to.subtract(n);
        Vec3d c = to.add(n);
        Vec3d d = from.add(n);
        Render3D.renderTriangle(matrices, a, b, c, color, true);
        Render3D.renderTriangle(matrices, a, c, d, color, true);
    }
}
