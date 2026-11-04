package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.hud.impl.LowArmorNotificationsHud;
import ru.method.linkenvisuals.managers.system.HudManager;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

public class LowArmorNotify extends Module {
    private final NumberSetting threshold = new NumberSetting("modules.settings.low_armor_notify.durability_threshold", 50f, 1f, 70f, 1f);
    private final NumberSetting interval = new NumberSetting("modules.settings.low_armor_notify.interval", 15f, 1f, 120f, 1f);
    private final BooleanSetting showOnce = new BooleanSetting("modules.settings.low_armor_notify.show_once", false);
    private final BooleanSetting helmet = new BooleanSetting("modules.settings.low_armor_notify.helmet", true);
    private final BooleanSetting chestplate = new BooleanSetting("modules.settings.low_armor_notify.chestplate", true);
    private final BooleanSetting leggings = new BooleanSetting("modules.settings.low_armor_notify.leggings", true);
    private final BooleanSetting boots = new BooleanSetting("modules.settings.low_armor_notify.boots", true);
    private final BooleanSetting shield = new BooleanSetting("modules.settings.low_armor_notify.shield", true);
    private final long[] lastNotifyMs = new long[5];
    private final String[] lastItemKeys = new String[5];
    private final boolean[] notifiedOnce = new boolean[5];
    private int tickCounter;

    public LowArmorNotify() {
        super("LowArmorNotify", Category.Player);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        syncHudElement(true);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        syncHudElement(false);
    }

    /**
     * Двусторонняя связь модуля и HUD-элемента оповещений: включение модуля включает и ставит элемент
     * (на место по умолчанию/по центру), выключение — гасит. Обратная сторона (элемент -> модуль) —
     * в {@link HudManager#handleElementToggle}. Меняем только при реальном рассинхроне (защита от петли).
     */
    private void syncHudElement(boolean on) {
        HudManager hudManager = LinkenVisuals.getInstance().getHudManager();
        if (hudManager == null) return;
        LowArmorNotificationsHud element = hudManager.getHudElement(LowArmorNotificationsHud.class);
        if (element == null) return;
        BooleanSetting setting = hudManager.getElementSetting(element);
        if (setting == null || setting.getValue() == on) return;
        setting.setValue(on);
        hudManager.handleElementToggle(setting, on, 0f, 0f);
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck() || ++tickCounter % 20 != 0) return;
        checkArmorSlot(3, helmet.getValue(), "шлем");
        checkArmorSlot(2, chestplate.getValue(), "нагрудник");
        checkArmorSlot(1, leggings.getValue(), "поножи");
        checkArmorSlot(0, boots.getValue(), "ботинки");
        checkShield();
    }

    private void checkArmorSlot(int armorSlot, boolean enabled, String label) {
        if (!enabled) return;
        EquipmentSlot eqSlot = switch (armorSlot) {
            case 3 -> EquipmentSlot.HEAD;
            case 2 -> EquipmentSlot.CHEST;
            case 1 -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
        ItemStack stack = mc.player.getEquippedStack(eqSlot);
        if (armorSlot == 2 && stack.isOf(Items.ELYTRA)) return;
        checkDurability(stack, armorSlot, label);
    }

    private void checkShield() {
        if (!shield.getValue()) return;

        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();
        if (mainHand.isOf(Items.SHIELD)) {
            checkDurability(mainHand, 4, "щит");
        } else if (offHand.isOf(Items.SHIELD)) {
            checkDurability(offHand, 4, "щит");
        }
    }

    private void checkDurability(ItemStack stack, int notifyIndex, String label) {
        if (stack.isEmpty() || !stack.isDamageable()) return;
        updateItemState(stack, notifyIndex);

        int max = stack.getMaxDamage();
        if (max <= 0) return;

        int percent = Math.round((max - stack.getDamage()) * 100f / max);
        if (percent > threshold.getValue()) return;
        if (showOnce.getValue() && notifiedOnce[notifyIndex]) return;

        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1L, Math.round(interval.getValue())) * 1000L;
        if (!showOnce.getValue() && now - lastNotifyMs[notifyIndex] < cooldownMs) return;
        lastNotifyMs[notifyIndex] = now;
        notifiedOnce[notifyIndex] = true;
        LowArmorNotificationsHud.push("Предмет скоро сломается: " + label + " (" + percent + "%)");
    }

    private void updateItemState(ItemStack stack, int notifyIndex) {
        String key = stack.getItem() + "|" + stack.getMaxDamage() + "|" + stack.getName().getString();
        if (!key.equals(lastItemKeys[notifyIndex])) {
            lastItemKeys[notifyIndex] = key;
            lastNotifyMs[notifyIndex] = 0L;
            notifiedOnce[notifyIndex] = false;
        }
    }
}
