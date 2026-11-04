package ru.method.linkenvisuals.modules.impl.render;


import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class HitColor extends Module {

    // Сущности, которых УДАРИЛ локальный игрок (id → время удара). Красим только их, а не всех,
    // кто получил урон (иначе тинт срабатывал «просто так» — на чужих ударах/мобах/падении).
    private static final Map<Integer, Long> RECENT_HITS = new ConcurrentHashMap<>();

    // Плавная сила тинта ПО id сущности. КРИТИЧНО хранить здесь, а не на LivingEntityRenderState:
    // в 1.21.x у рендерера ОДИН переиспользуемый render-state на все сущности своего типа, поэтому
    // сглаживание, лежавшее на нём, «протекало» — сила ударенного игрока переносилась через lerp на
    // следующего отрисованного рядом, и краснели оба. По id каждая сущность считается отдельно.
    private static final Map<Integer, Float> STRENGTH = new ConcurrentHashMap<>();

    public static void markHit(int entityId) {
        RECENT_HITS.put(entityId, System.currentTimeMillis());
    }

    public static boolean wasHitRecently(int entityId, long windowMs) {
        Long t = RECENT_HITS.get(entityId);
        return t != null && System.currentTimeMillis() - t <= windowMs;
    }

    /** Продвигает силу тинта для КОНКРЕТНОЙ сущности: быстрый вспых при ударе + плавное затухание.
     *  Горячий путь (зовётся per-сущность per-кадр в updateRenderState): для небитых и не тлеющих —
     *  один get, мапу не трогаем. put/remove только когда реально есть тинт. */
    public static float advanceStrength(int entityId, boolean hitByMe) {
        Float cur = STRENGTH.get(entityId);
        if (!hitByMe && cur == null) return 0.0f; // общий случай: ни удара, ни остаточного тинта
        float current = cur == null ? 0.0f : cur;
        float target = hitByMe ? 1.0f : 0.0f;
        float speed = target > current ? 0.85f : 0.18f;
        float next = current + (target - current) * speed;
        if (next <= 0.001f) {
            if (cur != null) STRENGTH.remove(entityId); // не копим мёртвые записи
            return 0.0f;
        }
        STRENGTH.put(entityId, next);
        return next;
    }

    @Override
    public void onDisable() {
        RECENT_HITS.clear();
        STRENGTH.clear();
        super.onDisable();
    }

    public final ColorSetting color = new ColorSetting("settings.hitcolor.color", new Color(
            ColorUtils.getGlobalColor().getRed(),
            ColorUtils.getGlobalColor().getGreen(),
            ColorUtils.getGlobalColor().getBlue(),
            255
    ));
    public final NumberSetting opacity = new NumberSetting("settings.hitcolor.opacity", 140.0f, 0.0f, 1000.0f, 1.0f);
    public final BooleanSetting colorArmor = new BooleanSetting("settings.hitcolor.colorarmor", false);

    public HitColor() {
        super("HitColor", Category.Visuals);
        getSettings().add(color);
        getSettings().add(opacity);
        getSettings().add(colorArmor);
    }

    @EventHandler
    public void onAttack(EventAttackEntity event) {
        Entity target = event.getTarget();
        if (target != null) {
            markHit(target.getId());
        }
    }
}
