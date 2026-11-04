package ru.method.linkenvisuals.utils.inv;

import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

@UtilityClass
public class InventoryCursorLock {
    private int frozenSlotId = -1;

    public boolean isFrozen() {
        return frozenSlotId != -1;
    }

    public void freeze(HandledScreen<?> screen, int slotId) {
        frozenSlotId = slotId;
        maintain(screen);
    }

    public void maintain(HandledScreen<?> screen) {
        if (frozenSlotId == -1 || screen == null) return;
        InventoryUtils.hoverSlot(screen, frozenSlotId);
    }

    public void unfreeze() {
        frozenSlotId = -1;
    }
}
