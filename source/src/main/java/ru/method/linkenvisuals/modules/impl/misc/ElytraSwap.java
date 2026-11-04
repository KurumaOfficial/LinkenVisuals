package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import lombok.AllArgsConstructor;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventMouseScroll;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.utils.inv.InventoryCursorLock;
import ru.method.linkenvisuals.utils.inv.InventoryUtils;

public class ElytraSwap extends Module {
    private static final int CHESTPLATE_SLOT_ID = 6;
    private static final int FLY_COMMAND_TIMEOUT_TICKS = 30;
    private static final int INVENTORY_CLOSE_TICKS = 2;
    private static final int SWAP_STEP_DELAY_TICKS = 1;
    private static final int WAIT_SCREEN_TIMEOUT_TICKS = 10;

    private final BindSetting swapBind = new BindSetting("settings.elytraswap.bind", new Bind(-1, false));
    // Normal — потиковые задержки между кликами (стабильно). Instant — все клики за один тик
    // (перенос быстрого свапа из Kimiko): быстрее, но чуть агрессивнее к анти-читу.
    private final EnumSetting<SwapSpeed> speed = new EnumSetting<>("settings.elytraswap.speed", SwapSpeed.Normal);
    private final BooleanSetting sendFlyCommand = new BooleanSetting("settings.elytraswap.fly_command", false);

    private int flyCommandTicks;
    private int pendingFlyCommands;
    private int inventoryCloseTicks;
    private boolean temporaryInventoryOpened;

    private SwapPhase swapPhase = SwapPhase.NONE;
    private int swapSourceSlot = -1;
    private int swapStepDelay;
    private int waitScreenTicks;
    private boolean swappingToElytra;

    public ElytraSwap() {
        super("ElytraSwap", Category.Player);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetSwapState();
        inventoryCloseTicks = 0;
        temporaryInventoryOpened = false;
        InventoryCursorLock.unfreeze();
    }

    @EventHandler
    private void onKey(EventKey event) {
        if (mc.currentScreen != null) return;
        if (event.getAction() == 1 && swapBind.getValue().matchesKey(event.getKey())) {
            beginSwap();
        }
    }

    @EventHandler
    private void onMouse(EventMouse event) {
        if (mc.currentScreen != null) return;
        if (event.getAction() == 1 && swapBind.getValue().matchesMouse(event.getButton())) {
            event.consume();
            beginSwap();
        }
    }

    @EventHandler
    private void onMouseScroll(EventMouseScroll event) {
        if (mc.currentScreen != null) return;
        if (swapBind.getValue().matchesMouseScroll(event.getVerticalAmount())) {
            event.consume();
            beginSwap();
        }
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (InventoryCursorLock.isFrozen()) {
            if (mc.currentScreen instanceof HandledScreen<?> screen) {
                InventoryCursorLock.maintain(screen);
            } else {
                InventoryCursorLock.unfreeze();
            }
        }

        tickSwap();
        tickTemporaryInventory();

        if (flyCommandTicks <= 0) return;
        if (fullNullCheck() || mc.player.networkHandler == null) {
            flyCommandTicks = 0;
            pendingFlyCommands = 0;
            return;
        }

        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            if (pendingFlyCommands > 0) {
                mc.player.networkHandler.sendChatCommand("fly");
                pendingFlyCommands--;
            }
            flyCommandTicks = pendingFlyCommands > 0 ? FLY_COMMAND_TIMEOUT_TICKS : 0;
            return;
        }

        flyCommandTicks--;
        if (flyCommandTicks <= 0) {
            pendingFlyCommands = 0;
        }
    }

    private void beginSwap() {
        if (fullNullCheck() || mc.interactionManager == null) return;
        if (swapPhase != SwapPhase.NONE) return;

        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        swappingToElytra = !chest.isOf(Items.ELYTRA);
        int sourceSlot = chest.isOf(Items.ELYTRA)
                ? InventoryUtils.findBestChestplate(0, 35)
                : InventoryUtils.find(Items.ELYTRA, 0, 35);

        if (sourceSlot == -1) return;

        swapSourceSlot = InventoryUtils.indexToSlot(sourceSlot);

        openTemporaryInventory();

        if (temporaryInventoryOpened) {
            swapPhase = SwapPhase.WAIT_SCREEN;
            waitScreenTicks = WAIT_SCREEN_TIMEOUT_TICKS;
            swapStepDelay = 0;
            return;
        }

        swapPhase = SwapPhase.PICK_SOURCE;
        swapStepDelay = 0;
    }


    private void scheduleFlyCommand() {
        if (sendFlyCommand.getValue() && swappingToElytra) {
            pendingFlyCommands++;
            flyCommandTicks = Math.max(flyCommandTicks, FLY_COMMAND_TIMEOUT_TICKS);
        }
    }

    private void tickSwap() {
        if (swapPhase == SwapPhase.NONE) return;

        if (swapStepDelay > 0) {
            swapStepDelay--;
            return;
        }

        if (swapPhase == SwapPhase.WAIT_SCREEN) {
            if (mc.currentScreen instanceof HandledScreen<?>) {
                swapPhase = SwapPhase.PICK_SOURCE;
                swapStepDelay = stepDelay();
                return;
            }
            if (--waitScreenTicks <= 0) {
                cancelSwap();
            }
            return;
        }

        if (fullNullCheck() || mc.interactionManager == null) {
            cancelSwap();
            return;
        }

        int syncId = mc.player.playerScreenHandler.syncId;

        switch (swapPhase) {
            case PICK_SOURCE -> {
                clickLockedSlot(syncId, swapSourceSlot);
                swapPhase = SwapPhase.PICK_CHEST;
                swapStepDelay = stepDelay();
            }
            case PICK_CHEST -> {
                clickLockedSlot(syncId, CHESTPLATE_SLOT_ID);
                swapPhase = SwapPhase.RETURN_SOURCE;
                swapStepDelay = stepDelay();
            }
            case RETURN_SOURCE -> {
                clickLockedSlot(syncId, swapSourceSlot);
                if (hasCursorStack()) {
                    swapPhase = SwapPhase.STASH_CURSOR;
                    swapStepDelay = stepDelay();
                } else {
                    finishSwap();
                }
            }
            case STASH_CURSOR -> {
                stashCursorStack(syncId);
                finishSwap();
            }
            default -> cancelSwap();
        }
    }

    private void clickLockedSlot(int syncId, int slotId) {
        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            InventoryCursorLock.freeze(screen, slotId);
        } else {
            InventoryUtils.hoverSlot(slotId);
        }
        InventoryUtils.clickHandlerSlot(syncId, slotId, 0, SlotActionType.PICKUP);
    }

    private void stashCursorStack(int syncId) {
        if (!hasCursorStack() || mc.player == null) return;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                clickLockedSlot(syncId, InventoryUtils.indexToSlot(i));
                return;
            }
        }
    }

    private void finishSwap() {
        swapPhase = SwapPhase.NONE;
        swapSourceSlot = -1;
        swapStepDelay = 0;
        waitScreenTicks = 0;

        if (temporaryInventoryOpened) {
            inventoryCloseTicks = INVENTORY_CLOSE_TICKS;
        } else {
            InventoryCursorLock.unfreeze();
        }

        scheduleFlyCommand();
    }

    private void cancelSwap() {
        resetSwapState();
        InventoryCursorLock.unfreeze();
    }

    private void resetSwapState() {
        swapPhase = SwapPhase.NONE;
        swapSourceSlot = -1;
        swapStepDelay = 0;
        waitScreenTicks = 0;
    }

    private void openTemporaryInventory() {
        temporaryInventoryOpened = false;
        if (mc.player.isCreative()) {
            inventoryCloseTicks = 0;
            return;
        }
        if (mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            temporaryInventoryOpened = true;
        }
    }

    private void tickTemporaryInventory() {
        if (inventoryCloseTicks <= 0) return;
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            inventoryCloseTicks = 0;
            temporaryInventoryOpened = false;
            return;
        }

        if (hasCursorStack() && mc.player != null) {
            stashCursorStack(mc.player.playerScreenHandler.syncId);
        }
        if (hasCursorStack()) {
            return;
        }

        inventoryCloseTicks--;
        if (inventoryCloseTicks <= 0) {
            closeTemporaryInventory();
        }
    }

    private boolean hasCursorStack() {
        return mc.player != null
                && mc.player.playerScreenHandler != null
                && !mc.player.playerScreenHandler.getCursorStack().isEmpty();
    }

    private void closeTemporaryInventory() {
        InventoryCursorLock.unfreeze();
        if (mc.player != null) {
            mc.player.closeHandledScreen();
        }
        mc.setScreen(null);
        temporaryInventoryOpened = false;
    }

    private int stepDelay() {
        return speed.getValue() == SwapSpeed.Instant ? 0 : SWAP_STEP_DELAY_TICKS;
    }

    private enum SwapPhase {
        NONE,
        WAIT_SCREEN,
        PICK_SOURCE,
        PICK_CHEST,
        RETURN_SOURCE,
        STASH_CURSOR
    }

    @AllArgsConstructor
    private enum SwapSpeed implements Nameable {
        Normal("settings.elytraswap.speed.normal"),
        Instant("settings.elytraswap.speed.instant");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }
}
