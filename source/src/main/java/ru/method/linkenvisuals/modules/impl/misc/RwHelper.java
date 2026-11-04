package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.inv.InventoryUtils;
import ru.method.linkenvisuals.utils.render.Render3D;
import ru.method.linkenvisuals.utils.server.Server;

import java.awt.*;

public class RwHelper extends Module {
    private static final String TRAP_DISPLAY_NAME = "Ловушка";
    private static final String DARK_PULSE_NAME = "Тёмный Пульс";

    private final BindSetting bindAntiFly = new BindSetting("settings.rwhelper.bindantifly", new Bind(-1, false));
    private final BindSetting bindExpScroll = new BindSetting("settings.rwhelper.bindexpscroll", new Bind(-1, false));
    private final BindSetting bindTrap = new BindSetting("settings.rwhelper.bindtrap", new Bind(-1, false));
    private final BindSetting bindDarkPulse = new BindSetting("settings.rwhelper.binddarkpulse", new Bind(-1, false));

    public RwHelper() {
        super("RwHelper", Category.Player);
    }

    @Override
    public void onEnable() {
        if (fullNullCheck()) {
            toggled = false;
            return;
        }

        if (Server.isFuntime()) {
            ChatUtils.sendMessage(Formatting.RED + "RwHelper не работает на Funtime сервере!");
            playCriticalSound();
            toggled = false;
            return;
        }

        super.onEnable();
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck() || Server.isFuntime()) return;

        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();
        if (mainHand.getItem() != Items.FIREWORK_STAR && offHand.getItem() != Items.FIREWORK_STAR) return;

        Vec3d playerPos = mc.player.getEntityPos();
        double radius = 16.0;

        boolean playerInRange = false;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.squaredDistanceTo(mc.player) <= radius * radius) {
                playerInRange = true;
                break;
            }
        }

        Color circleColor = playerInRange ? new Color(0, 255, 0, 255) : new Color(255, 0, 0, 255);
        Render3D.renderCircle(event.getMatrixStack(), playerPos, radius, circleColor);
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (fullNullCheck() || mc.currentScreen != null) return;

        if (Server.isFuntime()) {
            ChatUtils.sendMessage(Formatting.RED + "RwHelper не работает на Funtime сервере!");
            playCriticalSound();
            toggle();
            return;
        }

        if (e.getAction() == 1) {
            if (bindAntiFly.getValue().matchesKey(e.getKey())) {
                prekol(Items.FIREWORK_STAR, "settings.rwhelper.bindantifly");
            }
            if (bindExpScroll.getValue().matchesKey(e.getKey())) {
                prekol(Items.FLOWER_BANNER_PATTERN, "settings.rwhelper.bindexpscroll");
            }
            if (bindTrap.getValue().matchesKey(e.getKey())) {
                prekolTrap();
            }
            if (bindDarkPulse.getValue().matchesKey(e.getKey())) {
                prekolNamedHotbar(Items.FIREWORK_STAR, DARK_PULSE_NAME, "settings.rwhelper.binddarkpulse");
            }
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (fullNullCheck() || mc.currentScreen != null) return;

        if (Server.isFuntime()) {
            ChatUtils.sendMessage(Formatting.RED + "RwHelper не работает на Funtime сервере!");
            playCriticalSound();
            toggle();
            return;
        }

        if (e.getAction() == 1) {
            if (bindAntiFly.getValue().matchesMouse(e.getButton())) {
                prekol(Items.FIREWORK_STAR, "settings.rwhelper.bindantifly");
            }
            if (bindExpScroll.getValue().matchesMouse(e.getButton())) {
                prekol(Items.FLOWER_BANNER_PATTERN, "settings.rwhelper.bindexpscroll");
            }
            if (bindTrap.getValue().matchesMouse(e.getButton())) {
                prekolTrap();
            }
            if (bindDarkPulse.getValue().matchesMouse(e.getButton())) {
                prekolNamedHotbar(Items.FIREWORK_STAR, DARK_PULSE_NAME, "settings.rwhelper.binddarkpulse");
            }
        }
    }

    private void prekolTrap() {
        prekolNamedHotbar(Items.HEART_OF_THE_SEA, TRAP_DISPLAY_NAME, "settings.rwhelper.bindtrap");
    }

    private void prekolNamedHotbar(Item item, String itemName, String settingKey) {
        int slot = findNamedHotbarSlot(item, itemName);
        String settingName = I18n.translate(settingKey);

        if (slot == -1) {
            ChatUtils.sendMessage(Formatting.WHITE + "Предмет " + Formatting.RED + settingName + Formatting.WHITE + " не найден!");
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() == slot) {
            ChatUtils.sendMessage(Formatting.WHITE + "Предмет " + Formatting.YELLOW + settingName + Formatting.WHITE + " уже свапнут!");
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);
        ChatUtils.sendMessage(Formatting.WHITE + "Предмет " + Formatting.GREEN + settingName + Formatting.WHITE + " свапнут!");
    }

    private int findNamedHotbarSlot(Item item, String expectedName) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isOf(item)) continue;
            if (expectedName.equals(stripItemName(stack.getName().getString()))) {
                return slot;
            }
        }
        return -1;
    }

    private String stripItemName(String text) {
        if (text == null) return "";
        StringBuilder cleaned = new StringBuilder(text.length());
        boolean skipNext = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if ((current == '\u00a7' || current == '&') && i + 1 < text.length()) {
                char code = Character.toUpperCase(text.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'A' && code <= 'F') || (code >= 'K' && code <= 'O') || code == 'R') {
                    skipNext = true;
                    continue;
                }
            }
            cleaned.append(current);
        }
        return cleaned.toString();
    }

    private void prekol(Item item, String settingKey) {
        int slot = InventoryUtils.findHotbar(item);
        String settingName = I18n.translate(settingKey);

        if (slot == -1) {
            ChatUtils.sendMessage(Formatting.WHITE + "Предмет " + Formatting.RED + settingName + Formatting.WHITE + " не найден!");
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() == slot) {
            ChatUtils.sendMessage(Formatting.WHITE + "Предмет " + Formatting.YELLOW + settingName + Formatting.WHITE + " уже свапнут!");
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);
        ChatUtils.sendMessage(Formatting.WHITE + "Предмет " + Formatting.GREEN + settingName + Formatting.WHITE + " свапнут!");
    }
}
