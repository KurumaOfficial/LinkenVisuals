package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.chat.ChatUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ItemPickup extends Module {
    private static final String[] DONATE_MARKERS = {
            "крушител", "молот тора", "сфера", "талисман", "ловушка", "тёмный пульс", "темный пульс"
    };
    private static final long DUPLICATE_WINDOW_MS = 750L;
    /** Сколько держим стак подобранного предмета в кэше (сущность к тому моменту уже удалена). */
    private static final long STACK_CACHE_MS = 3_000L;

    private final Map<Integer, Long> recentPickupEntities = new HashMap<>();
    // ConcurrentHashMap обязателен: onTick пишет на клиентском треде, а onPacketReceive читает на
    // netty-треде — на HashMap чтение во время rehash отдавало null/мусор, и сообщение о подборе
    // молча пропадало.
    private final Map<Integer, CachedStack> nearbyStacks = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedStack(ItemStack stack, long at) {}

    public ItemPickup() {
        super("ItemPickup", Category.Player);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        recentPickupEntities.clear();
        nearbyStacks.clear();
    }

    /**
     * Кэш предметов рядом с игроком. Сервер удаляет {@code ItemEntity} в тот же момент, что и шлёт
     * пакет подбора, поэтому {@code getEntityById} в обработчике часто возвращает null — и сообщение
     * не выводилось вообще. Держим последний известный стак, чтобы было что показать.
     */
    @EventHandler
    private void onTick(ru.method.linkenvisuals.api.events.impl.EventTick event) {
        if (fullNullCheck()) return;
        long now = System.currentTimeMillis();
        nearbyStacks.entrySet().removeIf(entry -> now - entry.getValue().at() > STACK_CACHE_MS);
        for (Entity ent : mc.world.getEntities()) {
            if (!(ent instanceof ItemEntity item)) continue;
            if (item.squaredDistanceTo(mc.player) > 64.0) continue; // ~8 блоков — дальше подобрать нельзя
            // copy() тянет NBT/компоненты — дорого делать на каждый предмет каждый тик.
            // Если стак не изменился, обновляем только таймштамп, переиспользуя старую копию.
            ItemStack live = item.getStack();
            CachedStack old = nearbyStacks.get(item.getId());
            if (old != null && ItemStack.areEqual(old.stack(), live)) {
                nearbyStacks.put(item.getId(), new CachedStack(old.stack(), now));
            } else {
                nearbyStacks.put(item.getId(), new CachedStack(live.copy(), now));
            }
        }
    }

    @EventHandler
    private void onPacketReceive(EventPacket.Receive event) {
        if (fullNullCheck() || !(event.getPacket() instanceof ItemPickupAnimationS2CPacket packet)) return;
        if (packet.getCollectorEntityId() != mc.player.getId()) return;

        long now = System.currentTimeMillis();
        recentPickupEntities.entrySet().removeIf(entry -> now - entry.getValue() > DUPLICATE_WINDOW_MS);

        int entityId = packet.getEntityId();
        if (recentPickupEntities.containsKey(entityId)) return;
        recentPickupEntities.put(entityId, now);

        // Живая сущность, если ещё есть; иначе — последний известный стак из кэша (её уже удалили).
        Entity entity = mc.world.getEntityById(entityId);
        ItemStack stack = entity instanceof ItemEntity itemEntity ? itemEntity.getStack() : null;
        if (stack == null || stack.isEmpty()) {
            CachedStack cached = nearbyStacks.get(entityId);
            stack = cached == null ? null : cached.stack();
        }
        if (stack == null || stack.isEmpty()) return;

        String cleanName = stripPlain(stack.getName().getString());
        if (!isDonateLike(cleanName)) return;

        sendPickupMessage(stack, cleanName);
    }

    private boolean isDonateLike(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String marker : DONATE_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    private String stripPlain(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("(?i)(§|&)[0-9A-FK-OR]", "").trim();
        cleaned = cleaned.replaceAll("^(?:123\\s*)+", "").replaceAll("(?:\\s*123)+$", "").trim();
        return cleaned;
    }

    private void sendPickupMessage(ItemStack stack, String cleanName) {
        Text displayName = stack.getName();
        Style style = displayName.getStyle() == null || displayName.getStyle().isEmpty()
                ? Style.EMPTY.withColor(TextColor.fromFormatting(Formatting.GOLD))
                : displayName.getStyle();

        MutableText message = Text.literal("Подобран предмет: ")
                .setStyle(Style.EMPTY.withColor(TextColor.fromFormatting(Formatting.WHITE)))
                .append(Text.literal(cleanName).setStyle(style));
        ChatUtils.sendMessage(message);
    }
}
