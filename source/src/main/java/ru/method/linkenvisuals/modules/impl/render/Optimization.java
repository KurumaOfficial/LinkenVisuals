package ru.method.linkenvisuals.modules.impl.render;

import lombok.AllArgsConstructor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.utils.optimization.OcclusionCuller;

/**
 * Поднимает FPS: окклюзионный куллинг сущностей, куллинг далёких частиц, ограничение дальности прорисовки,
 * куллинг облаков, отключение дорогой прозрачности (перенос Optimization из Kimiko, безопасное подмножество
 * под рендер 1.21.4 — куллинг блок-сущностей/теней/view-scale из оригинала завязан на render-state 1.21.5+
 * и здесь не переносится). Методы-геттеры дёргаются из Optimization*-миксинов.
 */
public class Optimization extends Module {

    private static final int[] RENDER_DISTANCE_CAP = {0, 12, 8};
    private static final double[] PARTICLE_DIST_SQ = {2304.0, 1024.0, 484.0};

    private static Optimization instance;

    private final EnumSetting<Tier> mode = new EnumSetting<>("settings.optimization.mode", Tier.Medium);
    private final BooleanSetting entityCulling = new BooleanSetting("settings.optimization.entity_culling", true);
    private final BooleanSetting limitRenderDistance =
            new BooleanSetting("settings.optimization.limit_render_distance", true, () -> instance != null && instance.tier() >= 1);

    public Optimization() {
        super("Optimization", Category.Visuals);
        instance = this;
    }

    private int tier() {
        return switch (mode.getValue()) {
            case Ultra -> 2;
            case Medium -> 1;
            default -> 0;
        };
    }

    private static boolean active() {
        return instance != null && instance.isToggle();
    }

    // ── гейты для миксинов ──

    public static int capEffectiveRenderDistance(int original) {
        if (!active() || !instance.limitRenderDistance.getValue()) return original;
        int cap = RENDER_DISTANCE_CAP[instance.tier()];
        return cap > 0 ? Math.min(original, cap) : original;
    }

    public static boolean allowParticle(double x, double y, double z) {
        if (!active()) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null) return true;
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return true;
        Vec3d cam = camera.getCameraPos();
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz <= PARTICLE_DIST_SQ[instance.tier()];
    }

    public static boolean shouldRenderEntity(boolean original, Entity entity, double camX, double camY, double camZ) {
        if (!(original && active() && instance.entityCulling.getValue())) return original;
        return OcclusionCuller.isVisible(entity, camX, camY, camZ, instance.tier());
    }

    /** true → форсить дешёвую (не «улучшенную») прозрачность. Возвращаем инвертированный флаг для ModifyReturnValue. */
    public static boolean shaderTransparency(boolean original) {
        return active() && instance.tier() >= 1 ? false : original;
    }

    public static boolean cullClouds() {
        return active() && instance.tier() == 2;
    }

    @AllArgsConstructor
    public enum Tier implements Nameable {
        Low("settings.optimization.mode.low"),
        Medium("settings.optimization.mode.medium"),
        Ultra("settings.optimization.mode.ultra");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }
}
