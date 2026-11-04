package ru.method.linkenvisuals.utils.inv;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.mixins.accessors.IClientPlayerInteractionManager;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.network.NetworkUtils;
import lombok.AllArgsConstructor;
import lombok.experimental.UtilityClass;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.api.mixins.accessors.IHandledScreen;

@UtilityClass
public class InventoryUtils implements Wrapper {

    @AllArgsConstructor
    public enum Switch implements Nameable {
        Normal("settings.normal"),
        Silent("settings.switch.silent"),
        Alternative("settings.switch.alternative"),
        None("settings.none");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }

    public enum Swap {
        Pickup,
        Swap
    }

    @AllArgsConstructor
    public enum Swing implements Nameable {
        MainHand("settings.swing.mainhand"),
        OffHand("settings.swing.offhand"),
        Packet("settings.packet"),
        None("settings.none");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }

    public int indexToSlot(int index) {
        if (index >= 0 && index <= 8) return 36 + index;
        return index;
    }

    public void switchSlot(Switch mode, int slot, int previousSlot) {
        if (slot == -1 || previousSlot == -1 || slot == LinkenVisuals.getInstance().getServerManager().getServerSlot())
            return;

        switch (mode) {
            case Normal -> {
                mc.player.getInventory().setSelectedSlot(slot);
                ((IClientPlayerInteractionManager) mc.interactionManager).syncSelectedSlot$drug();
            }
            case Silent -> NetworkUtils.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            case Alternative -> swap(Swap.Swap, slot, previousSlot);
        }
    }

    public static void moveItem(int from, int to) {
        clickCurrentHandlerSlot(from, to, SlotActionType.SWAP);
    }

    public static void pickupItem(int slot, int button) {
        mc.interactionManager.clickSlot(0, slot, button, SlotActionType.PICKUP, mc.player);
    }

    public void swap(Swap mode, int slot, int targetSlot) {
        if (slot == -1 || targetSlot == -1) return;
        switch (mode) {
            case Pickup -> {
                clickCurrentHandlerSlot(indexToSlot(slot), 0, SlotActionType.PICKUP);
                clickCurrentHandlerSlot(indexToSlot(targetSlot), 0, SlotActionType.PICKUP);
                clickCurrentHandlerSlot(indexToSlot(slot), 0, SlotActionType.PICKUP);
            }
            case Swap -> clickCurrentHandlerSlot(slot, targetSlot, SlotActionType.SWAP);
        }
    }

    public void bypassSwap(int slot, int targetSlot) {
        if (slot == -1 || targetSlot == -1) return;

        swap(slot, targetSlot);
    }

    public void swap(int slot, int targetSlot) {
        if (slot == -1 || targetSlot == -1) return;
        clickPlayerHandlerSlot(InventoryUtils.indexToSlot(slot), 0, SlotActionType.PICKUP);
        clickPlayerHandlerSlot(targetSlot, 0, SlotActionType.PICKUP);
        clickPlayerHandlerSlot(InventoryUtils.indexToSlot(slot), 0, SlotActionType.PICKUP);
    }

    public void clickCurrentHandlerSlot(int slotId, int button, SlotActionType actionType) {
        if (mc.player == null || mc.interactionManager == null || mc.player.currentScreenHandler == null) return;
        clickHandlerSlot(mc.player.currentScreenHandler.syncId, slotId, button, actionType);
    }

    public void clickPlayerHandlerSlot(int slotId, int button, SlotActionType actionType) {
        if (mc.player == null || mc.interactionManager == null || mc.player.playerScreenHandler == null) return;
        clickHandlerSlot(mc.player.playerScreenHandler.syncId, slotId, button, actionType);
    }

    public void clickHandlerSlot(int syncId, int slotId, int button, SlotActionType actionType) {
        if (mc.player == null || mc.interactionManager == null) return;
        stopSprintForInventory();
        hoverSlot(slotId);
        mc.interactionManager.clickSlot(syncId, slotId, button, actionType, mc.player);
        hoverSlot(slotId);
    }

    /**
     * Гасит спринт ПЕРЕД кликом по слоту. Ванильный клиент физически не может кликать по инвентарю на
     * бегу: открытый GUI забирает ввод и спринт спадает сам. Поэтому пакетная связка «игрок спринтует +
     * клик по слоту» невозможна в ванили и является типовым флагом античита — на неё и ловились
     * ClickPearl / ElytraSwap / AutoSwap (особенно при спринте). Отправляем честный STOP_SPRINTING;
     * спринт возобновится сам, как только игрок продолжит бежать.
     */
    public void stopSprintForInventory() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (!mc.player.isSprinting()) return;
        // STOP обязан уйти РАНЬШЕ ClickSlotC2SPacket. Ванильный sendSprintingPacket выполняется
        // только в КОНЦЕ ClientPlayerEntity.tick, а модуль Sprint возвращает спринт в tickMovement
        // того же тика — к концу тика isSprinting()==lastSprinting, и ваниль не шлёт STOP вообще:
        // сервер видел клик по инвентарю при активном спринте. Поэтому сбрасываем флаг и форсируем
        // ВАНИЛЬНЫЙ sendSprintingPacket: он сам сравнит с lastSprinting, отправит ровно один STOP и
        // выставит lastSprinting=false — дубля в конце тика не будет по построению, а если Sprint
        // вернёт спринт в этом же тике, ваниль в конце тика честно отправит START.
        mc.player.setSprinting(false);
        ((ru.method.linkenvisuals.api.mixins.accessors.IClientPlayerEntity) mc.player).sendSprintingPacket$drug();
    }

    public void hoverSlot(int slotId) {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        hoverSlot(screen, slotId);
    }

    public double[] hoverSlot(HandledScreen<?> screen, int slotId) {
        if (mc == null || mc.getWindow() == null || screen == null) return null;
        Slot slot = findSlot(screen, slotId);
        if (slot == null) return null;

        IHandledScreen accessor = (IHandledScreen) screen;
        double scale = mc.getWindow().getScaleFactor();
        double mouseX = accessor.getX() + slot.x + 8.5;
        double mouseY = accessor.getY() + slot.y + 8.5;
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), mouseX * scale, mouseY * scale);
        accessor.setFocusedSlot(slot);
        return new double[]{mouseX * scale, mouseY * scale};
    }

    public Slot findSlot(HandledScreen<?> screen, int slotId) {
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot != null && slot.id == slotId) return slot;
        }
        if (slotId >= 0 && slotId < screen.getScreenHandler().slots.size()) {
            return screen.getScreenHandler().slots.get(slotId);
        }
        return null;
    }

    public int findBestChestplate(int start, int end) {
        int leatherSlot = -1;
        int chainmail = -1;
        int ironSlot = -1;
        int goldenSlot = -1;
        int diamondSlot = -1;
        int netheriteSlot = -1;

        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.LEATHER_CHESTPLATE) leatherSlot = i;
            else if (stack.getItem() == Items.CHAINMAIL_CHESTPLATE) chainmail = i;
            else if (stack.getItem() == Items.IRON_CHESTPLATE) ironSlot = i;
            else if (stack.getItem() == Items.GOLDEN_CHESTPLATE) goldenSlot = i;
            else if (stack.getItem() == Items.DIAMOND_CHESTPLATE) diamondSlot = i;
            else if (stack.getItem() == Items.NETHERITE_CHESTPLATE) netheriteSlot = i;
        }

        if (chainmail != -1) return chainmail;
        if (ironSlot != -1) return ironSlot;
        if (goldenSlot != -1) return goldenSlot;
        if (diamondSlot != -1) return diamondSlot;
        if (netheriteSlot != -1) return netheriteSlot;

        return leatherSlot;
    }

    public int getArmorColor(PlayerEntity entity, int slot) {
        net.minecraft.entity.EquipmentSlot eq = switch (slot) {
            case 0 -> net.minecraft.entity.EquipmentSlot.FEET;
            case 1 -> net.minecraft.entity.EquipmentSlot.LEGS;
            case 2 -> net.minecraft.entity.EquipmentSlot.CHEST;
            default -> net.minecraft.entity.EquipmentSlot.HEAD;
        };
        ItemStack stack = entity.getEquippedStack(eq);
        if (stack.isIn(ItemTags.DYEABLE)) return DyedColorComponent.getColor(stack, -6265536);
        return -1;
    }

    public int find(Item item) {
        return find(item, 0, 35);
    }

    public int findHotbar(Item item) {
        return find(item, 0, 8);
    }

    public int findInventory(Item item) {
        return find(item, 9, 35);
    }

    public int find(Class<? extends Item> item) {
        return find(item, 0, 35);
    }

    public int findHotbar(Class<? extends Item> item) {
        return find(item, 0, 8);
    }

    public int findInventory(Class<? extends Item> item) {
        return find(item, 9, 35);
    }

    public int find(Item item, int start, int end) {
        for (int i = end; i >= start; i--) if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        return -1;
    }

    public int find(Class<? extends Item> item, int start, int end) {
        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.getItem().getClass().isAssignableFrom(item)) continue;

            return i;
        }

        return -1;
    }

    public int findEmptySlot(int start, int end) {
        for (int i = end; i >= start; i--) if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        return -1;
    }
}
