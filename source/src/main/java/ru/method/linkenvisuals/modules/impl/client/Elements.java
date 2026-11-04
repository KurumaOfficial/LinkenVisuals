package ru.method.linkenvisuals.modules.impl.client;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.screen.hud.HudEditScreen;
import ru.method.linkenvisuals.utils.notify.Notify;
import ru.method.linkenvisuals.utils.notify.NotifyIcons;

/**
 * Редактор расположения HUD. Это action-модуль: у него нет состояния «вкл/выкл» и нельзя забиндить —
 * клик по карточке в GUI просто открывает прозрачный {@link HudEditScreen}, где элементы перетаскиваются
 * мышью. Доступен только при GUI = Link. Настройки (ПКМ по карточке) — переключатели HUD-элементов.
 */
public class Elements extends Module {

    public Elements() {
        super("Elements", Category.Other);
    }

    @Override
    public boolean isActionModule() {
        return true;
    }

    // Состояния вкл/выкл нет — тогл игнорируем (модуль не включается/выключается и не биндится).
    @Override
    public void setToggled(boolean toggled) {
    }

    @Override
    public void activate() {
        if (mc == null) return;
        if (!UI.isLinkGuiStyle()) {
            LinkenVisuals.getInstance().getNotifyManager()
                    .add(new Notify(NotifyIcons.failIcon, "Редактор HUD доступен только при GUI = Link", 1800));
            return;
        }
        if (!(mc.currentScreen instanceof HudEditScreen)) {
            mc.setScreen(new HudEditScreen());
        }
    }
}
