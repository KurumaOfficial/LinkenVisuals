package dev.linkenvisuals.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the vanilla item cooldown manager without depending on its private
 * implementation at compile time. 1.21.11 stores cooldowns as
 * {@code Identifier -> Entry(startTick, endTick)}; older donor code used an
 * item map, so this adapter also falls back to scanning the inventory when the
 * reflective path is unavailable.
 */
public final class CooldownTracker {
    private static final Field ENTRIES_FIELD;
    private static final Field TICK_FIELD;
    private static final Method START_METHOD;
    private static final Method END_METHOD;

    static {
        ENTRIES_FIELD = field(ItemCooldownManager.class, "entries");
        TICK_FIELD = field(ItemCooldownManager.class, "tick");
        Class<?> entryClass = null;
        try {
            entryClass = Class.forName("net.minecraft.entity.player.ItemCooldownManager$Entry");
        } catch (ClassNotFoundException ignored) {
            try {
                entryClass = Class.forName("net.minecraft.class_1796$class_1797");
            } catch (ClassNotFoundException ignoredAgain) {
                entryClass = null;
            }
        }
        START_METHOD = method(entryClass, "startTick", "startTime", "comp_3083");
        END_METHOD = method(entryClass, "endTick", "endTime", "comp_3084");
    }

    private CooldownTracker() {
    }

    public record Entry(ItemStack stack, String displayName, float progress, float remainingTicks) {
        public String formattedTime() {
            return formatTime(remainingTicks);
        }
    }

    public static List<Entry> collect(MinecraftClient client, float tickDelta) {
        if (client == null || client.player == null) {
            return List.of();
        }
        PlayerEntity player = client.player;
        ItemCooldownManager manager = player.getItemCooldownManager();
        List<Entry> entries = collectFromManager(player, manager, tickDelta);
        if (entries.isEmpty()) {
            entries = collectFromInventory(player, manager, tickDelta);
        }
        entries.sort(Comparator.comparingDouble(Entry::remainingTicks).reversed());
        return entries;
    }

    public static List<Entry> activeCooldowns(MinecraftClient client) {
        return collect(client, 0.0f);
    }

    public static List<Entry> preview() {
        return List.of(
            sample(Items.ENDER_PEARL, 16 * 20, 0.85f),
            sample(Items.CHORUS_FRUIT, 20 * 20, 0.55f),
            sample(Items.GOLDEN_APPLE, 4 * 20, 0.25f)
        );
    }

    private static List<Entry> collectFromManager(PlayerEntity player, ItemCooldownManager manager,
                                                  float tickDelta) {
        if (ENTRIES_FIELD == null || TICK_FIELD == null
            || START_METHOD == null || END_METHOD == null) {
            return List.of();
        }
        try {
            Object raw = ENTRIES_FIELD.get(manager);
            if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
                return List.of();
            }
            int currentTick = TICK_FIELD.getInt(manager);
            List<Entry> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> mapEntry : map.entrySet()) {
                if (!(mapEntry.getKey() instanceof Identifier id)) {
                    continue;
                }
                Item item = Registries.ITEM.get(id);
                if (item == null || item == Items.AIR) {
                    continue;
                }
                Object value = mapEntry.getValue();
                if (value == null) {
                    continue;
                }
                int start = (int) START_METHOD.invoke(value);
                int end = (int) END_METHOD.invoke(value);
                float total = Math.max(1.0f, end - start);
                float remaining = Math.max(0.0f, end - (currentTick + tickDelta));
                float progress = Math.max(0.0f, Math.min(1.0f, remaining / total));
                if (progress <= 0.0f) {
                    continue;
                }
                ItemStack stack = findStack(player, item);
                entries.add(new Entry(stack, displayName(stack), progress, remaining));
            }
            return entries;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return List.of();
        }
    }

    private static List<Entry> collectFromInventory(PlayerEntity player, ItemCooldownManager manager,
                                                    float tickDelta) {
        List<Entry> entries = new ArrayList<>();
        Set<Item> seen = new LinkedHashSet<>();
        for (ItemStack stack : player.getInventory().getMainStacks()) {
            addIfCooling(entries, seen, stack, manager, tickDelta);
        }
        addIfCooling(entries, seen, player.getOffHandStack(), manager, tickDelta);
        return entries;
    }

    private static void addIfCooling(List<Entry> entries, Set<Item> seen, ItemStack stack,
                                     ItemCooldownManager manager, float tickDelta) {
        if (stack == null || stack.isEmpty() || !seen.add(stack.getItem())) {
            return;
        }
        float progress = manager.getCooldownProgress(stack, tickDelta);
        if (progress <= 0.0f) {
            return;
        }
        entries.add(new Entry(stack.copy(), displayName(stack), progress, progress * 20.0f));
    }

    private static Entry sample(Item item, int totalTicks, float progress) {
        ItemStack stack = new ItemStack(item);
        float remaining = totalTicks * progress;
        return new Entry(stack, displayName(stack), progress, remaining);
    }

    private static ItemStack findStack(PlayerEntity player, Item item) {
        if (player.getMainHandStack().isOf(item)) {
            return player.getMainHandStack().copy();
        }
        if (player.getOffHandStack().isOf(item)) {
            return player.getOffHandStack().copy();
        }
        for (ItemStack stack : player.getInventory().getMainStacks()) {
            if (stack.isOf(item)) {
                return stack.copy();
            }
        }
        return item.getDefaultStack();
    }

    private static String displayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String name = stack.getName().getString().replaceAll("\\s+", " ").trim();
        if (name.length() >= 2 && name.startsWith("[") && name.endsWith("]")) {
            name = name.substring(1, name.length() - 1).trim();
        }
        return name;
    }

    static String formatTime(float remainingTicks) {
        int totalSeconds = (int) Math.ceil(Math.max(0.0f, remainingTicks) / 20.0f);
        return (totalSeconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", totalSeconds % 60);
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String... names) {
        if (owner == null) {
            return null;
        }
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
