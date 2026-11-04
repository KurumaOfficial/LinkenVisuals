package ru.method.linkenvisuals.modules.impl.misc;


import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;
import ru.method.linkenvisuals.api.events.impl.EventPopTotem;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.chat.ChatUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class TotemTracker extends Module {

    private final Map<UUID, Boolean> totemGlintState = new HashMap<>();
    private final Map<UUID, Long> lastPopTick = new HashMap<>();

    public TotemTracker() {
        super("TotemTracker", Category.Player);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        totemGlintState.clear();
        lastPopTick.clear();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            ItemStack stack = findTotem(player);
            if (stack != null) {
                totemGlintState.put(player.getUuid(), stack.hasGlint());
            }
        }
    }

    @EventHandler
    private void onTotem(EventPopTotem event) {
        if (fullNullCheck()) return;

        PlayerEntity player = event.getPlayer();
        if (player == null) return;

        long currentTick = mc.world.getTime();
        Long lastTick = lastPopTick.get(player.getUuid());
        if (lastTick != null && currentTick - lastTick <= 1) return;
        lastPopTick.put(player.getUuid(), currentTick);

        boolean enchanted = isEnchanted(player);
        String enchantedMark = (enchanted ? Formatting.GREEN + "✔" : Formatting.RED + "✘") + Formatting.WHITE;
        ChatUtils.sendMessage(
                I18n.translate("modules.totemtracker.message",
                        Formatting.GOLD + player.getName().getString() + Formatting.WHITE,
                        enchantedMark
                )
        );
    }

    private boolean isEnchanted(PlayerEntity player) {
        ItemStack stack = findTotem(player);
        if (stack != null) return stack.hasGlint();

        Boolean stored = totemGlintState.get(player.getUuid());
        return stored != null && stored;
    }

    private ItemStack findTotem(PlayerEntity player) {
        ItemStack offHand = player.getOffHandStack();
        if (offHand.isOf(Items.TOTEM_OF_UNDYING)) return offHand;

        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.isOf(Items.TOTEM_OF_UNDYING)) return mainHand;

        return null;
    }
}
