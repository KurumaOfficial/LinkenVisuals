package ru.method.linkenvisuals.modules.impl.combat;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;


public class FriendHelper extends Module {

    public final BooleanSetting colorArmor = new BooleanSetting("settings.friendhelper.colorarmor", true);

    private final Map<PlayerEntity, ItemStack[]> originalArmor = new HashMap<>();
    private final ItemStack[] greenLeatherArmor = new ItemStack[4];
    private static final int FRIEND_ARMOR_COLOR = 0x00FF00;

    public FriendHelper() {
        super("FriendHelper", Category.Combat);
        initializeGreenArmor();
    }

    private void initializeGreenArmor() {
        
        greenLeatherArmor[0] = new ItemStack(Items.LEATHER_BOOTS);
        greenLeatherArmor[1] = new ItemStack(Items.LEATHER_LEGGINGS);
        greenLeatherArmor[2] = new ItemStack(Items.LEATHER_CHESTPLATE);
        greenLeatherArmor[3] = new ItemStack(Items.LEATHER_HELMET);

        
        for (ItemStack armor : greenLeatherArmor) {
            if (armor != null) {
                armor.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(FRIEND_ARMOR_COLOR));
            }
        }
    }

    private static net.minecraft.entity.EquipmentSlot getArmorSlot(int i) {
        return switch (i) {
            case 3 -> net.minecraft.entity.EquipmentSlot.HEAD;
            case 2 -> net.minecraft.entity.EquipmentSlot.CHEST;
            case 1 -> net.minecraft.entity.EquipmentSlot.LEGS;
            default -> net.minecraft.entity.EquipmentSlot.FEET;
        };
    }

    // Логика армора не зависит от кадра — раз в тик достаточно (раньше крутилась в onRender3D).
    @EventHandler
    public void onTick(EventTick event) {
        if (fullNullCheck()) return;
        if (!colorArmor.getValue()) {
            restoreAllArmor();
            return;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            String playerName = player.getName().getString();
            boolean isFriend = LinkenVisuals.getInstance().getFriendManager().isFriend(playerName);

            if (isFriend) {
                handleFriendArmor(player);
            } else {
                restorePlayerArmor(player);
            }
        }

        
        originalArmor.entrySet().removeIf(entry -> {
            PlayerEntity player = entry.getKey();
            // getWorld() != mc.world вместо getPlayers().contains() — O(1) вместо линейного поиска.
            return player == null || player.isRemoved() || player.getEntityWorld() != mc.world;
        });
    }

    private void handleFriendArmor(PlayerEntity player) {
        
        ItemStack[] original = originalArmor.computeIfAbsent(player, p -> new ItemStack[] {
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        });

        
        for (int i = 0; i < 4; i++) {
            ItemStack currentArmor = player.getEquippedStack(getArmorSlot(i));
            boolean isReplacement = isFriendArmorReplacement(currentArmor, i);
            if (!isReplacement) {
                original[i] = currentArmor.isEmpty() ? ItemStack.EMPTY : currentArmor.copy();
                ItemStack greenArmor = greenLeatherArmor[i].copy();
                player.equipStack(getArmorSlot(i), greenArmor);
            }
        }
    }

    private boolean isFriendArmorReplacement(ItemStack stack, int slotIndex) {
        if (stack.isEmpty()) return false;
        if (!ItemStack.areItemsEqual(stack, greenLeatherArmor[slotIndex])) return false;

        DyedColorComponent dyedColor = stack.get(DataComponentTypes.DYED_COLOR);
        return dyedColor != null && dyedColor.rgb() == FRIEND_ARMOR_COLOR;
    }

    private void restorePlayerArmor(PlayerEntity player) {
        if (originalArmor.containsKey(player)) {
            ItemStack[] original = originalArmor.get(player);
            for (int i = 0; i < 4; i++) {
                player.equipStack(getArmorSlot(i), original[i]);
            }
            originalArmor.remove(player);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        restoreAllArmor();
    }

    private void restoreAllArmor() {
        if (fullNullCheck()) return;

        for (Map.Entry<PlayerEntity, ItemStack[]> entry : originalArmor.entrySet()) {
            PlayerEntity player = entry.getKey();
            ItemStack[] original = entry.getValue();

            if (player != null && !player.isRemoved()) {
                for (int i = 0; i < 4; i++) {
                    player.equipStack(getArmorSlot(i), original[i]);
                }
            }
        }
        originalArmor.clear();
    }
}
