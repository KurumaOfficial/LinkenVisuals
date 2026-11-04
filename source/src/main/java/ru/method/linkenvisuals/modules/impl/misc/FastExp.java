package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.api.mixins.accessors.IMinecraftClient;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

/**
 * Ускоряет броски бутыльков опыта, когда игрок кидает их САМ (зажат ПКМ, а в руке бутылёк):
 * перебивает ванильную задержку использования предмета (обычно 4 тика) на меньшую. Ничего не
 * кидает автоматически — только убирает задержку, пока игрок держит ПКМ.
 */
public class FastExp extends Module {

    // Задержка между бросками в тиках: 0 = максимально быстро, ванильная = 4.
    private final NumberSetting delay = new NumberSetting("settings.fastexp.delay", 0f, 0f, 4f, 1f);

    public FastExp() {
        super("FastExp", Category.Player);
        getSettings().add(delay);
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;
        if (mc.currentScreen != null) return;
        // Только пока игрок реально зажимает ПКМ и держит бутылёк опыта в руке.
        if (!mc.options.useKey.isPressed()) return;
        if (!holdingExpBottle()) return;

        // Понижаем ванильную задержку использования (обычно 4) до нужной — только вниз, чтобы кулдаун
        // естественно доходил до 0 и бросок происходил. Жёсткая перезапись каждый тик при delay>=2
        // не давала бы кулдауну обнулиться и броски бы вовсе прекратились.
        IMinecraftClient client = (IMinecraftClient) mc;
        int target = Math.round(delay.getValue());
        if (client.getItemUseCooldown() > target) {
            client.setItemUseCooldown(target);
        }
    }

    private boolean holdingExpBottle() {
        return mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)
                || mc.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE);
    }
}
