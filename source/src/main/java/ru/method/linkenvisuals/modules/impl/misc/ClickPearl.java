package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.api.mixins.accessors.IClientPlayerInteractionManager;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.inv.InventoryUtils;

public class ClickPearl extends Module {
    private static final int INVENTORY_VISIBLE_TICKS = 3;

    private final BindSetting pearlBind = new BindSetting("modules.settings.clickpearl.bind", new Bind(-1, false));
    private final NumberSetting targetSlot = new NumberSetting("modules.settings.clickpearl.slot", 9f, 1f, 9f, 1f);

    private boolean bindHeld;
    private int previousSlot = -1;
    private int pearlThrowSlot = -1;
    private int pendingThrowTicks;
    private int restoreSlotTicks;
    private int closeInventoryTicks;
    private boolean temporaryInventoryOpened;
    // Отложенный своп жемчуга из инвентаря: клик делаем ЧЕРЕЗ тик после открытия экрана (легитимно).
    private int pendingMoveTicks;
    private int pendingMovePearlSlot = -1;
    private int pendingMoveTarget = -1;

    public ClickPearl() {
        super("ClickPearl", Category.Player);
        getSettings().add(pearlBind);
        getSettings().add(targetSlot);
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    @EventHandler
    private void onKey(EventKey event) {
        if (fullNullCheck()) return;
        if (!pearlBind.getValue().matchesKey(event.getKey())) return;
        handleBind(event.getAction() != 0);
    }

    @EventHandler
    private void onMouse(EventMouse event) {
        if (fullNullCheck()) return;
        if (!pearlBind.getValue().matchesMouse(event.getButton())) return;
        if (mc.currentScreen != null) return;
        event.consume();
        handleBind(event.getAction() != 0);
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            resetState();
            return;
        }

        if (closeInventoryTicks > 0) {
            closeInventoryTicks--;
            if (closeInventoryTicks <= 0) {
                closeTemporaryInventory();
            }
            return;
        }

        if (restoreSlotTicks > 0) {
            restoreSlotTicks--;
            if (restoreSlotTicks <= 0) {
                restorePreviousSlot();
            }
            return;
        }

        if (pendingMoveTicks > 0) {
            pendingMoveTicks--;
            if (pendingMoveTicks <= 0) {
                // Инвентарь был открыт тик (легитимно) — клик-своп, сразу закрываем и кидаем следующим
                // тиком. Быстро для ПВП, но с реальным открытием инвентаря.
                movePearlNow();
                closeTemporaryInventory();
                pendingThrowTicks = 1;
            }
            return;
        }

        if (pendingThrowTicks > 0) {
            pendingThrowTicks--;
            if (pendingThrowTicks <= 0) {
                throwPearlAndScheduleRestore();
            }
        }
    }

    private void handleBind(boolean pressed) {
        if (pressed) {
            if (bindHeld || mc.currentScreen != null || mc.interactionManager == null) return;
            // Не перезапускаем бросок, пока предыдущий ещё в процессе (курсор мог уйти с экрана после
            // закрытия инвентаря, а фазы throw/restore ещё считаются) — иначе портим слоты в полёте.
            if (throwInProgress()) return;
            bindHeld = true;
            startPearlThrow();
        } else {
            bindHeld = false;
        }
    }

    private boolean throwInProgress() {
        return pendingMoveTicks > 0 || closeInventoryTicks > 0 || pendingThrowTicks > 0 || restoreSlotTicks > 0;
    }

    private void startPearlThrow() {
        int hotbarPearlSlot = InventoryUtils.findHotbar(Items.ENDER_PEARL);
        int pearlSlot = hotbarPearlSlot != -1 ? hotbarPearlSlot : InventoryUtils.findInventory(Items.ENDER_PEARL);
        if (pearlSlot == -1) {
            ChatUtils.sendMessage(Text.literal("ClickPearl: нет эндер перлов").formatted(Formatting.RED));
            return;
        }

        previousSlot = mc.player.getInventory().getSelectedSlot();
        if (pearlSlot <= 8) {
            pearlThrowSlot = pearlSlot;
            pendingThrowTicks = 1;
            return;
        }

        // Жемчуг в основном инвентаре (>8): открываем инвентарь СЕЙЧАС, а клик-своп делаем через пару
        // тиков (когда экран уже зарегистрирован) — это легитимный клик по открытому контейнеру, а не
        // «клик по syncId=0 без открытого экрана» (что и палилось). Так же делал старый автосвап.
        int targetHotbarSlot = getTargetHotbarSlot();
        pearlThrowSlot = targetHotbarSlot;
        pendingMovePearlSlot = pearlSlot;
        pendingMoveTarget = targetHotbarSlot;
        openTemporaryInventory();
        if (temporaryInventoryOpened) {
            pendingMoveTicks = 1;
        } else {
            // Не удалось открыть (креатив/экран) — клик сразу, как раньше.
            movePearlNow();
            pendingThrowTicks = 1;
        }
    }

    private void movePearlNow() {
        if (pendingMovePearlSlot < 0) return;
        int slotId = InventoryUtils.indexToSlot(pendingMovePearlSlot);
        InventoryUtils.clickPlayerHandlerSlot(slotId, pendingMoveTarget, SlotActionType.SWAP);
        pendingMovePearlSlot = -1;
        pendingMoveTarget = -1;
    }

    private void throwPearlAndScheduleRestore() {
        if (pearlThrowSlot < 0 || pearlThrowSlot > 8 || !mc.player.getInventory().getStack(pearlThrowSlot).isOf(Items.ENDER_PEARL)) {
            ChatUtils.sendMessage(Text.literal("ClickPearl: нет эндер перлов").formatted(Formatting.RED));
            resetState();
            return;
        }

        switchSlot(pearlThrowSlot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        restoreSlotTicks = 2;
    }

    private void restorePreviousSlot() {
        if (previousSlot >= 0 && previousSlot <= 8) {
            switchSlot(previousSlot);
        }
        resetState();
    }

    private int getTargetHotbarSlot() {
        return Math.max(0, Math.min(8, Math.round(targetSlot.getValue()) - 1));
    }

    private void switchSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        ((IClientPlayerInteractionManager) mc.interactionManager).syncSelectedSlot$drug();
    }

    private void openTemporaryInventory() {
        temporaryInventoryOpened = false;
        if (mc.player.isCreative()) return;
        if (mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            temporaryInventoryOpened = true;
        }
    }

    private void closeTemporaryInventory() {
        if (mc.currentScreen instanceof InventoryScreen && mc.player != null) {
            mc.player.closeHandledScreen();
            mc.setScreen(null);
        }
        temporaryInventoryOpened = false;
    }

    private void resetState() {
        // Закрываем свой временный инвентарь, если он остался открыт (например, выключили модуль
        // посреди последовательности) — иначе игрок застрянет в GUI, который сам не открывал.
        if (temporaryInventoryOpened) {
            closeTemporaryInventory();
        }
        pendingThrowTicks = 0;
        restoreSlotTicks = 0;
        closeInventoryTicks = 0;
        pendingMoveTicks = 0;
        pendingMovePearlSlot = -1;
        pendingMoveTarget = -1;
        previousSlot = -1;
        pearlThrowSlot = -1;
        temporaryInventoryOpened = false;
        bindHeld = false;
    }
}
