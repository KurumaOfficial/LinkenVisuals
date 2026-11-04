package ru.method.linkenvisuals.modules.impl.misc;


import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.inv.InventoryUtils;
import ru.method.linkenvisuals.utils.server.Server;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;


public class HwHelper extends Module {

    private final BindSetting bindStan = new BindSetting("settings.hwhelper.bindstan", new Bind(-1, false));
    private final BindSetting bindTrapka = new BindSetting("settings.hwhelper.bindtrapka", new Bind(-1, false));
    private final BindSetting bindBoomTrapka = new BindSetting("settings.hwhelper.bindboomtrapka", new Bind(-1, false));
    private final BindSetting bindBoomThing = new BindSetting("settings.hwhelper.bindboomthing", new Bind(-1, false));

    public HwHelper() {
        super("HwHelper", Category.Player);
    }

    @Override
    public void onEnable() {
        if (fullNullCheck()) {
            toggled = false;
            return;
        }

        if (Server.isFuntime()) {
            ChatUtils.sendMessage(Formatting.RED + "HwHelper не работает на Funtime!");
            playCriticalSound();
            toggled = false;
            return;
        }

        super.onEnable();
    }

    @EventHandler
    public void onKey(EventKey event) {
        if (fullNullCheck() || mc.currentScreen != null) return;
        if (Server.isFuntime()) {
            ChatUtils.sendMessage(Formatting.RED + "hwhelper не работает на Funtime!");
            playCriticalSound();
            toggle();
            return;
        }

        if (event.getAction() == 1) {
            if (bindStan.getValue().matchesKey(event.getKey())) {
                selectItem(Items.NETHER_STAR, "settings.hwhelper.bindstan");
            }
            if (bindTrapka.getValue().matchesKey(event.getKey())) {
                selectItem(Items.POPPED_CHORUS_FRUIT, "settings.hwhelper.bindtrapka");
            }
            if (bindBoomTrapka.getValue().matchesKey(event.getKey())) {
                selectItem(Items.PRISMARINE_SHARD, "settings.hwhelper.bindboomtrapka");
            }
            if (bindBoomThing.getValue().matchesKey(event.getKey())) {
                selectItem(Items.FIRE_CHARGE, "settings.hwhelper.bindboomthing");
            }
        }
    }

    @EventHandler
    public void onMouse(EventMouse event) {
        if (fullNullCheck() || mc.currentScreen != null) return;
        if (Server.isFuntime()) {
            ChatUtils.sendMessage(Formatting.RED + "HwHelper не работает на Funtime!");
            playCriticalSound();
            toggle();
            return;
        }

        if (event.getAction() == 1) {
            if (bindStan.getValue().matchesMouse(event.getButton())) {
                selectItem(Items.NETHER_STAR, "settings.hwhelper.bindstan");
            }
            if (bindTrapka.getValue().matchesMouse(event.getButton())) {
                selectItem(Items.POPPED_CHORUS_FRUIT, "settings.hwhelper.bindtrapka");
            }
            if (bindBoomTrapka.getValue().matchesMouse(event.getButton())) {
                selectItem(Items.PRISMARINE_SHARD, "settings.hwhelper.bindboomtrapka");
            }
            if (bindBoomThing.getValue().matchesMouse(event.getButton())) {
                selectItem(Items.FIRE_CHARGE, "settings.hwhelper.bindboomthing");
            }
        }
    }

    private void selectItem(Item item, String settingKey) {
        int slot = InventoryUtils.findHotbar(item);
        String settingName = I18n.translate(settingKey);

        if (slot == -1) {
            ChatUtils.sendMessage(
                    Formatting.WHITE + "Предмет " +
                            Formatting.RED + settingName +
                            Formatting.WHITE + " не найден!"
            );
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);

        ChatUtils.sendMessage(
                Formatting.WHITE + "Предмет " +
                        Formatting.GREEN + settingName +
                        Formatting.WHITE + " свапнут!"
        );
    }
}
