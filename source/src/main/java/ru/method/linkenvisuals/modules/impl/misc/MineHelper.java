package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import ru.method.linkenvisuals.api.events.impl.EventStartBreakBlock;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;

public class MineHelper extends Module {
    private final BooleanSetting saveTools = new BooleanSetting("modules.settings.minehelper.save_tools", true);
    private final NumberSetting percent = new NumberSetting("modules.settings.minehelper.percent", 5f, 1f, 100f, 1f, saveTools::getValue);
    private long lastWarnMs;

    public MineHelper() {
        super("MineHelper", Category.Player);
    }

    @EventHandler
    private void onStartBreakBlock(EventStartBreakBlock event) {
        if (fullNullCheck() || !saveTools.getValue()) return;

        ItemStack stack = mc.player.getMainHandStack();
        if (!isMiningTool(stack)) return;

        double durability = getDurabilityPercent(stack);
        if (durability >= percent.getValue()) return;

        event.cancel();
        long now = System.currentTimeMillis();
        if (now - lastWarnMs > 1500L) {
            ChatUtils.sendMessage("MineHelper: инструмент почти сломан (" + Math.round(durability) + "%)");
            lastWarnMs = now;
        }
    }

    private boolean isMiningTool(ItemStack stack) {
        return stack != null && stack.isDamageable() && stack.get(DataComponentTypes.TOOL) != null;
    }

    private double getDurabilityPercent(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) return 100.0;
        return (max - stack.getDamage()) * 100.0 / max;
    }
}
