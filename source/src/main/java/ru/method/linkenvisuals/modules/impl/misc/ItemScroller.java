package ru.method.linkenvisuals.modules.impl.misc;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ItemScroller extends Module {
    // Анти-кик: даже при «задержке 0» держим безопасный МИНИМУМ между перемещениями и жёсткий потолок за один
    // вызов dragOver. Раньше при delay=0 бюджет был Integer.MAX_VALUE → быстрый драг по инвентарю слал десятки
    // clickSlot за тик, и сервер кикал за «подозрительные пакеты». 50 мс ≈ 1 перемещение за тик (≤~3 в редкой
    // пачке после паузы) — визуально быстро, но безопасно для анти-читов.
    private static final long MIN_DELAY_MS = 50L;
    private static final int MAX_MOVES_PER_CALL = 3;

    private final NumberSetting delay = new NumberSetting("settings.itemscroller.delay", 0f, 0f, 250f, 5f);
    private final Set<Integer> draggedSlots = new HashSet<>();
    private boolean dragging;
    private DragMode dragMode = DragMode.NONE;
    private ItemStack dragReferenceStack = ItemStack.EMPTY;
    private double lastMouseX;
    private double lastMouseY;
    private long nextMoveAt;

    public ItemScroller() {
        super("ItemScroller", Category.Player);
    }

    @Override
    public void onDisable() {
        stopDragging();
        super.onDisable();
    }

    public DragMode resolveDragMode(int button, boolean shiftDown, boolean ctrlDown, boolean altDown) {
        if (button != 0) return DragMode.NONE;
        if (altDown && shiftDown) return DragMode.MOVE_STACKS;
        if (altDown) return DragMode.MOVE_MATCHING;
        if (ctrlDown) return DragMode.MOVE_ONE;
        if (shiftDown) return DragMode.MOVE_STACKS;
        return DragMode.NONE;
    }

    public boolean beginDragging(HandledScreen<?> screen, Slot slot, double mouseX, double mouseY, DragMode mode) {
        if (mode == DragMode.NONE || !canStartDrag(screen, slot, mode)) return false;

        dragging = true;
        dragMode = mode;
        draggedSlots.clear();
        dragReferenceStack = slot.getStack().copy();
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        nextMoveAt = 0L;

        tryDragSlot(screen, slot);
        scheduleNextMove();
        return true;
    }

    public void stopDragging() {
        dragging = false;
        dragMode = DragMode.NONE;
        draggedSlots.clear();
        dragReferenceStack = ItemStack.EMPTY;
        nextMoveAt = 0L;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean dragOver(HandledScreen<?> screen, double mouseX, double mouseY, Slot slotAtMouse) {
        if (!dragging || screen == null) return false;

        long delayMs = effectiveDelayMs();
        // Потолок за один вызов — не даём «догоняющему» бюджету после паузы вылиться в пачку пакетов.
        int moveBudget = Math.min(MAX_MOVES_PER_CALL, getMoveBudget(delayMs));
        if (moveBudget <= 0) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return false;
        }

        boolean moved = false;
        int currentX = (int) Math.round(mouseX);
        int currentY = (int) Math.round(mouseY);
        int startX = (int) Math.round(lastMouseX);
        int startY = (int) Math.round(lastMouseY);

        int deltaX = currentX - startX;
        int deltaY = currentY - startY;
        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaY));
        Set<Slot> sampledSlots = new LinkedHashSet<>();

        if (steps <= 0) {
            if (slotAtMouse != null) sampledSlots.add(slotAtMouse);
        } else {
            for (int step = 0; step <= steps; step++) {
                double sampleX = startX + deltaX * (step / (double) steps);
                double sampleY = startY + deltaY * (step / (double) steps);
                Slot sampledSlot = getSlotAt(screen, sampleX, sampleY);
                if (sampledSlot != null) sampledSlots.add(sampledSlot);
            }
        }

        if (slotAtMouse != null) sampledSlots.add(slotAtMouse);

        for (Slot sampledSlot : sampledSlots) {
            if (moveBudget <= 0) break;
            if (tryDragSlot(screen, sampledSlot)) {
                moved = true;
                moveBudget--;
                scheduleNextMove(delayMs); // delayMs всегда ≥ MIN_DELAY_MS → троттлинг активен и при «задержке 0»
            }
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return moved;
    }

    public boolean handleScroll(
            HandledScreen<?> screen,
            Slot slot,
            boolean scrollingUp,
            boolean shiftDown,
            boolean ctrlDown,
            boolean altDown
    ) {
        if (!isToggled() || fullNullCheck() || mc.interactionManager == null) return false;
        if (screen == null || slot == null) return false;
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) return false;

        boolean matchingModifier = altDown || ctrlDown;
        boolean everythingModifier = matchingModifier && shiftDown;

        if (everythingModifier) {
            return moveStacksByScroll(screen, slot, scrollingUp, false, false);
        }
        if (matchingModifier) {
            return moveStacksByScroll(screen, slot, scrollingUp, true, false);
        }
        if (shiftDown) {
            return moveStacksByScroll(screen, slot, scrollingUp, true, true);
        }

        if (scrollingUp) {
            if (!slot.hasStack() || !slot.canTakeItems(mc.player)) return false;
            return tryMoveSingleItemToOtherInventory(screen, slot);
        }

        if (!slot.hasStack()) return false;
        if (slot.getStack().getCount() >= slot.getStack().getMaxCount()) return false;
        return tryMoveSingleItemToThisInventory(screen, slot);
    }

    private boolean moveStacksByScroll(
            HandledScreen<?> screen,
            Slot hoveredSlot,
            boolean moveToOtherInventory,
            boolean matchingOnly,
            boolean singleStackOnly
    ) {
        ScreenHandler handler = screen.getScreenHandler();
        ItemStack referenceStack = hoveredSlot.getStack();
        if (matchingOnly && referenceStack.isEmpty()) return false;

        boolean moved = false;
        Slot hoveredDeferred = null;

        for (int i = handler.slots.size() - 1; i >= 0; i--) {
            Slot sourceSlot = handler.slots.get(i);
            if (!isSourceSlotForScrollMove(sourceSlot, hoveredSlot, referenceStack, moveToOtherInventory, matchingOnly)) continue;

            if (moveToOtherInventory && sourceSlot.id == hoveredSlot.id && matchingOnly) {
                hoveredDeferred = sourceSlot;
                continue;
            }

            if (quickMoveStack(screen, sourceSlot)) {
                moved = true;
                if (singleStackOnly) return true;
            }
        }

        if (hoveredDeferred != null && quickMoveStack(screen, hoveredDeferred)) {
            moved = true;
        }

        return moved;
    }

    private boolean isSourceSlotForScrollMove(
            Slot sourceSlot,
            Slot hoveredSlot,
            ItemStack referenceStack,
            boolean moveToOtherInventory,
            boolean matchingOnly
    ) {
        if (sourceSlot == null || !sourceSlot.hasStack()) return false;
        if (!sourceSlot.canTakeItems(mc.player)) return false;

        boolean sameInventoryAsHovered = areSlotsInSameInventory(sourceSlot, hoveredSlot);
        if (moveToOtherInventory != sameInventoryAsHovered) return false;

        if (!matchingOnly) return true;
        return ItemStack.areItemsAndComponentsEqual(sourceSlot.getStack(), referenceStack);
    }

    private boolean tryMoveSingleItemToOtherInventory(HandledScreen<?> screen, Slot slot) {
        if (!slot.hasStack() || !slot.canTakeItems(mc.player)) return false;
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) return false;

        ItemStack stack = slot.getStack();
        if (stack.getCount() > 1 && !slot.canInsert(stack)) {
            return false;
        }
        return clickSlotsToMoveSingleItemByShiftClick(screen, slot.id);
    }

    private boolean tryMoveSingleItemToThisInventory(HandledScreen<?> screen, Slot targetSlot) {
        if (!targetSlot.hasStack()) return false;
        if (!targetSlot.canInsert(targetSlot.getStack())) return false;

        ScreenHandler handler = screen.getScreenHandler();
        ItemStack targetStack = targetSlot.getStack();

        for (int i = handler.slots.size() - 1; i >= 0; i--) {
            Slot fromSlot = handler.slots.get(i);
            if (fromSlot.id == targetSlot.id) continue;
            if (areSlotsInSameInventory(fromSlot, targetSlot)) continue;
            if (!fromSlot.hasStack() || !fromSlot.canTakeItems(mc.player)) continue;

            ItemStack sourceStack = fromSlot.getStack();
            if (sourceStack.getCount() > 1 && !fromSlot.canInsert(sourceStack)) continue;
            if (!ItemStack.areItemsAndComponentsEqual(sourceStack, targetStack)) continue;

            return clickSlotsToMoveSingleItem(screen, fromSlot.id, targetSlot.id);
        }

        return false;
    }

    private boolean clickSlotsToMoveSingleItemByShiftClick(HandledScreen<?> screen, int slotFromId) {
        ScreenHandler handler = screen.getScreenHandler();
        if (slotFromId < 0 || slotFromId >= handler.slots.size()) return false;
        Slot slot = handler.slots.get(slotFromId);
        if (!slot.hasStack()) return false;

        ItemStack stack = slot.getStack();
        if (stack.getCount() > 1) {
            performClick(screen, slotFromId, 0, SlotActionType.PICKUP);
            if (slot.hasStack()) {
                performClick(screen, slotFromId, 0, SlotActionType.PICKUP);
                return false;
            }
            performClick(screen, slotFromId, 1, SlotActionType.PICKUP);
        }

        performClick(screen, slotFromId, 0, SlotActionType.QUICK_MOVE);

        if (!handler.getCursorStack().isEmpty()) {
            performClick(screen, slotFromId, 0, SlotActionType.PICKUP);
        }

        return true;
    }

    private boolean clickSlotsToMoveSingleItem(HandledScreen<?> screen, int slotFromId, int slotToId) {
        ScreenHandler handler = screen.getScreenHandler();
        if (slotFromId < 0 || slotFromId >= handler.slots.size()) return false;
        if (slotToId < 0 || slotToId >= handler.slots.size()) return false;

        Slot fromSlot = handler.slots.get(slotFromId);
        if (!fromSlot.hasStack()) return false;

        ItemStack stack = fromSlot.getStack();
        if (stack.getCount() > 1) {
            performClick(screen, slotFromId, 1, SlotActionType.PICKUP);
        } else {
            performClick(screen, slotFromId, 0, SlotActionType.PICKUP);
        }

        performClick(screen, slotToId, 1, SlotActionType.PICKUP);

        if (!handler.getCursorStack().isEmpty()) {
            performClick(screen, slotFromId, 0, SlotActionType.PICKUP);
        }

        return true;
    }

    private boolean quickMoveStack(HandledScreen<?> screen, Slot slot) {
        if (slot == null || !slot.hasStack() || !slot.canTakeItems(mc.player)) return false;

        ItemStack before = slot.getStack().copy();
        performClick(screen, slot.id, 0, SlotActionType.QUICK_MOVE);

        ItemStack after = slot.getStack();
        if (!after.isEmpty() && ItemStack.areItemsAndComponentsEqual(before, after) && after.getCount() == before.getCount()) {
            return false;
        }
        return true;
    }

    private boolean canStartDrag(HandledScreen<?> screen, Slot slot, DragMode mode) {
        if (!isToggled() || fullNullCheck() || mc.interactionManager == null) return false;
        if (mode == DragMode.NONE) return false;
        if (screen == null || slot == null || !slot.hasStack()) return false;
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) return false;
        if (!slot.canTakeItems(mc.player)) return false;
        if (mode == DragMode.MOVE_ONE && slot.getStack().getCount() > 1 && !slot.canInsert(slot.getStack())) return false;
        return true;
    }

    private boolean tryDragSlot(HandledScreen<?> screen, Slot slot) {
        if (!canStartDrag(screen, slot, dragMode)) return false;
        if (draggedSlots.contains(slot.id)) return false;

        boolean moved = switch (dragMode) {
            case MOVE_STACKS -> quickMoveStack(screen, slot);
            case MOVE_ONE -> tryMoveSingleItemToOtherInventory(screen, slot);
            case MOVE_MATCHING -> !dragReferenceStack.isEmpty()
                    && ItemStack.areItemsAndComponentsEqual(slot.getStack(), dragReferenceStack)
                    && quickMoveStack(screen, slot);
            case NONE -> false;
        };

        if (moved) {
            draggedSlots.add(slot.id);
        }
        return moved;
    }

    private boolean areSlotsInSameInventory(Slot slot1, Slot slot2) {
        return slot1.inventory == slot2.inventory;
    }

    private void performClick(HandledScreen<?> screen, int slotId, int button, SlotActionType actionType) {
        mc.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slotId,
                button,
                actionType,
                mc.player
        );
    }

    private int getMoveBudget(long delayMs) {
        long now = System.currentTimeMillis();
        if (now < nextMoveAt) return 0;
        return 1 + (int) ((now - nextMoveAt) / delayMs);
    }

    private void scheduleNextMove() {
        scheduleNextMove(effectiveDelayMs());
    }

    /** Фактическая задержка между перемещениями: не ниже безопасного минимума (анти-кик даже при «задержке 0»). */
    private long effectiveDelayMs() {
        return Math.max(delay.getValue().longValue(), MIN_DELAY_MS);
    }

    private void scheduleNextMove(long delayMs) {
        if (delayMs <= 0L) {
            nextMoveAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (nextMoveAt <= now) {
            nextMoveAt = now + delayMs;
            return;
        }

        nextMoveAt += delayMs;
    }

    private Slot getSlotAt(HandledScreen<?> screen, double mouseX, double mouseY) {
        return ((ru.method.linkenvisuals.api.mixins.accessors.IHandledScreen) screen).invokeGetSlotAt(mouseX, mouseY);
    }

    public enum DragMode {
        NONE,
        MOVE_STACKS,
        MOVE_ONE,
        MOVE_MATCHING
    }
}
