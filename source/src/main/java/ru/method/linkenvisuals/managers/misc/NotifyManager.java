package ru.method.linkenvisuals.managers.misc;


import com.google.common.collect.Lists;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.notify.Notify;
import meteordevelopment.orbit.EventHandler;

import java.util.*;


public class NotifyManager implements Wrapper {

    public NotifyManager() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
    }

    /** Отступ от низа экрана: хотбар (22) + полоски здоровья/брони над ним + воздух. */
    private static final float HOTBAR_OFFSET = 58f;
    /** Шаг между уведомлениями (высота плашки 15 + зазор). */
    private static final float LINE_HEIGHT = 17f;

    private final List<Notify> notifies = new ArrayList<>();

    public void add(Notify notify) {
        notifies.add(notify);
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (Module.fullNullCheck() || LinkenVisuals.getInstance().isPanic()) return;
        if (notifies.isEmpty()) return;
        if (notifies.size() > 10) notifies.removeFirst();
        notifies.removeIf(Notify::expired);

        // Снизу по центру, НАД хотбаром: новейшее — самое нижнее, старые уезжают вверх.
        // Каждое уведомление лерпит свой y к цели, поэтому появление нового плавно толкает стопку вверх.
        List<Notify> list = Lists.newArrayList(notifies);
        float baseY = mc.getWindow().getScaledHeight() - HOTBAR_OFFSET;
        for (int i = 0; i < list.size(); i++) {
            int fromBottom = list.size() - 1 - i; // 0 — новейшее (нижнее)
            list.get(i).render(e, baseY - fromBottom * LINE_HEIGHT);
        }
    }
}
