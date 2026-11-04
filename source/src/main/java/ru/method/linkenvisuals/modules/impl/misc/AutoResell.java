package ru.method.linkenvisuals.modules.impl.misc;

import lombok.AllArgsConstructor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;

/**
 * Перевыставление предметов на аукционе (перенос AutoResell из Kimiko).
 * Command — раз в 65с шлёт «/ah resell». Auction — открывает «/ah», кликает слот-хранилище (46) и
 * слот перевыставления (52), закрывает. Слоты специфичны для FunTime.
 */
public class AutoResell extends Module {

    private static final long CYCLE_MS = 65_000L;
    private static final long STEP_MS = 500L;
    private static final int STORAGE_SLOT = 46;
    private static final int RESELL_SLOT = 52;

    private final EnumSetting<Mode> mode = new EnumSetting<>("settings.autoresell.mode", Mode.Command);

    private long cycleTimer;
    private long stepTimer;
    private State state = State.IDLE;

    public AutoResell() {
        super("AutoResell", Category.Other);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        state = State.IDLE;
        // Первый цикл — сразу (как в оригинале: cycleTimer выставлен «просроченным»).
        cycleTimer = System.currentTimeMillis() - CYCLE_MS;
        stepTimer = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        state = State.IDLE;
        super.onDisable();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck() || mc.player.networkHandler == null || mc.interactionManager == null) {
            state = State.IDLE;
            return;
        }
        if (mode.getValue() == Mode.Command) {
            tickCommand();
        } else {
            tickAuction();
        }
    }

    private void tickCommand() {
        if (System.currentTimeMillis() - cycleTimer < CYCLE_MS) return;
        mc.player.networkHandler.sendChatCommand("ah resell");
        cycleTimer = System.currentTimeMillis();
    }

    private boolean isMenuOpen() {
        return mc.currentScreen != null && mc.player.currentScreenHandler != null;
    }

    private void clickSlot(int slot) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null || slot < 0 || slot >= handler.slots.size()) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void tickAuction() {
        switch (state) {
            case IDLE -> {
                if (System.currentTimeMillis() - cycleTimer < CYCLE_MS) return;
                mc.player.networkHandler.sendChatCommand("ah");
                state = State.WAIT_MENU;
                stepTimer = System.currentTimeMillis();
            }
            case WAIT_MENU -> {
                if (!isMenuOpen()) {
                    if (System.currentTimeMillis() - stepTimer >= 3000L) endCycle();
                    return;
                }
                if (System.currentTimeMillis() - stepTimer < STEP_MS) return;
                clickSlot(STORAGE_SLOT);
                state = State.WAIT_STORAGE;
                stepTimer = System.currentTimeMillis();
            }
            case WAIT_STORAGE -> {
                if (!isMenuOpen()) { endCycle(); return; }
                if (System.currentTimeMillis() - stepTimer < STEP_MS) return;
                clickSlot(RESELL_SLOT);
                state = State.WAIT_RESELL;
                stepTimer = System.currentTimeMillis();
            }
            case WAIT_RESELL -> {
                if (System.currentTimeMillis() - stepTimer < STEP_MS) return;
                mc.player.closeHandledScreen();
                endCycle();
            }
        }
    }

    private void endCycle() {
        state = State.IDLE;
        cycleTimer = System.currentTimeMillis();
    }

    private enum State { IDLE, WAIT_MENU, WAIT_STORAGE, WAIT_RESELL }

    @AllArgsConstructor
    public enum Mode implements Nameable {
        Command("settings.autoresell.mode.command"),
        Auction("settings.autoresell.mode.auction");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }
}
