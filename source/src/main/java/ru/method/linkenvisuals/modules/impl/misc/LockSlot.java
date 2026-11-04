package ru.method.linkenvisuals.modules.impl.misc;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;

/**
 * Запрещает ВЫБРАСЫВАТЬ (Q / Ctrl+Q) предметы из выбранных слотов хотбара. Остальные действия
 * (использование предмета, перемещение в инвентаре) не затрагиваются. Сама блокировка — в
 * {@code ClientPlayerDropMixin} (отмена {@code dropSelectedItem} для залоченного выбранного слота).
 */
public class LockSlot extends Module {
    private final BooleanSetting slot1 = new BooleanSetting("modules.settings.lockslot.slot_1", false);
    private final BooleanSetting slot2 = new BooleanSetting("modules.settings.lockslot.slot_2", false);
    private final BooleanSetting slot3 = new BooleanSetting("modules.settings.lockslot.slot_3", false);
    private final BooleanSetting slot4 = new BooleanSetting("modules.settings.lockslot.slot_4", false);
    private final BooleanSetting slot5 = new BooleanSetting("modules.settings.lockslot.slot_5", false);
    private final BooleanSetting slot6 = new BooleanSetting("modules.settings.lockslot.slot_6", false);
    private final BooleanSetting slot7 = new BooleanSetting("modules.settings.lockslot.slot_7", false);
    private final BooleanSetting slot8 = new BooleanSetting("modules.settings.lockslot.slot_8", false);
    private final BooleanSetting slot9 = new BooleanSetting("modules.settings.lockslot.slot_9", false);

    public LockSlot() {
        super("LockSlot", Category.Player);
    }

    /** Запрещён ли выброс предмета из слота хотбара 0..8. */
    public boolean isDropLocked(int hotbarSlot) {
        return switch (hotbarSlot) {
            case 0 -> slot1.getValue();
            case 1 -> slot2.getValue();
            case 2 -> slot3.getValue();
            case 3 -> slot4.getValue();
            case 4 -> slot5.getValue();
            case 5 -> slot6.getValue();
            case 6 -> slot7.getValue();
            case 7 -> slot8.getValue();
            case 8 -> slot9.getValue();
            default -> false;
        };
    }
}
