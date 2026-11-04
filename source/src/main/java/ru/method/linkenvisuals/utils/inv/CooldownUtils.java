package ru.method.linkenvisuals.utils.inv;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;

public final class CooldownUtils {

    private static final Field COOLDOWNS_FIELD;
    private static final Field CURRENT_TICK_FIELD;
    private static final Field ENTRY_START_FIELD;
    private static final Field ENTRY_END_FIELD;

    static {
        COOLDOWNS_FIELD = lookupField(ItemCooldownManager.class, "entries", "cooldowns", "field_8024");
        CURRENT_TICK_FIELD = lookupField(ItemCooldownManager.class, "tick", "field_8025");

        Class<?> entryClass = null;
        Field start = null;
        Field end = null;
        try {
            entryClass = Class.forName("net.minecraft.entity.player.ItemCooldownManager$Entry");
        } catch (ClassNotFoundException ignored) {
            try {
                entryClass = Class.forName("net.minecraft.class_1796$class_1797");
            } catch (ClassNotFoundException ignored2) {
                entryClass = null;
            }
        }
        if (entryClass != null) {
            start = lookupField(entryClass, "startTick", "startTime", "comp_3083");
            end = lookupField(entryClass, "endTick", "endTime", "comp_3084");
        }
        ENTRY_START_FIELD = start;
        ENTRY_END_FIELD = end;
    }

    private CooldownUtils() {
    }

    public static List<CooldownEntry> collectActive(MinecraftClient client, float tickDelta) {
        if (client == null || client.player == null) {
            return Collections.emptyList();
        }

        ItemCooldownManager manager = client.player.getItemCooldownManager();
        Map<?, ?> rawCooldowns = readCooldownMap(manager);
        if (rawCooldowns == null || rawCooldowns.isEmpty()) {
            return fallbackFromInventory(client.player, tickDelta);
        }

        int currentTick = readCurrentTick(manager);
        List<CooldownEntry> result = new ArrayList<>(rawCooldowns.size());

        for (Map.Entry<?, ?> entry : rawCooldowns.entrySet()) {
            Object rawKey = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            Item item;
            if (rawKey instanceof Item keyItem) {
                item = keyItem;
            } else if (rawKey instanceof Identifier identifier) {
                item = Registries.ITEM.get(identifier);
                if (item == Items.AIR && !Registries.ITEM.containsId(identifier)) {
                    continue;
                }
            } else {
                continue;
            }

            if (item == null || item == Items.AIR) {
                continue;
            }

            CooldownEntry parsed = parseEntry(client.player, item, value, currentTick, tickDelta);
            if (parsed != null) {
                result.add(parsed);
            }
        }

        if (result.isEmpty()) {
            return fallbackFromInventory(client.player, tickDelta);
        }

        result.sort(Comparator.comparingDouble(CooldownEntry::remainingTicks).reversed());
        return result;
    }

    private static List<CooldownEntry> fallbackFromInventory(PlayerEntity player, float tickDelta) {
        if (player == null) {
            return Collections.emptyList();
        }
        List<CooldownEntry> result = new ArrayList<>();
        Set<Item> seenItems = new LinkedHashSet<>();
        ItemCooldownManager manager = player.getItemCooldownManager();
        DefaultedList<ItemStack> main = player.getInventory().getMainStacks();

        for (ItemStack stack : main) {
            addIfCoolingDown(result, player, stack, manager, tickDelta, seenItems);
        }

        addIfCoolingDown(result, player, player.getOffHandStack(), manager, tickDelta, seenItems);

        result.sort(Comparator.comparingDouble(CooldownEntry::remainingTicks).reversed());
        return result;
    }

    private static void addIfCoolingDown(List<CooldownEntry> entries, PlayerEntity player, ItemStack stack, ItemCooldownManager manager, float tickDelta, Set<Item> seenItems) {
        if (stack.isEmpty()) {
            return;
        }
        float progress = manager.getCooldownProgress(stack, tickDelta);
        if (progress <= 0.0f) {
            return;
        }
        Item item = stack.getItem();
        if (!seenItems.add(item)) {
            return;
        }
        
        float ticksRemaining = progress * 20.0f;
        String displayName = resolveDisplayName(stack, item);
        entries.add(new CooldownEntry(stack.copy(), displayName, progress, ticksRemaining, 20.0f));
    }

    private static Map<?, ?> readCooldownMap(ItemCooldownManager manager) {
        if (COOLDOWNS_FIELD == null) {
            return null;
        }
        try {
            Object value = COOLDOWNS_FIELD.get(manager);
            if (value instanceof Map<?, ?> map) {
                return map;
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    private static int readCurrentTick(ItemCooldownManager manager) {
        if (CURRENT_TICK_FIELD != null) {
            try {
                return CURRENT_TICK_FIELD.getInt(manager);
            } catch (IllegalAccessException ignored) {
            }
        }
        return 0;
    }

    private static CooldownEntry parseEntry(PlayerEntity player, Item item, Object entry, int currentTick, float tickDelta) {
        if (ENTRY_START_FIELD == null || ENTRY_END_FIELD == null) {
            return null;
        }
        try {
            int startTick = ENTRY_START_FIELD.getInt(entry);
            int endTick = ENTRY_END_FIELD.getInt(entry);
            float totalTicks = Math.max(1.0f, endTick - startTick);
            float ticksRemaining = Math.max(0.0f, endTick - (currentTick + tickDelta));
            float progress = MathHelper.clamp(ticksRemaining / totalTicks, 0.0f, 1.0f);

            if (progress <= 0.0f) {
                return null;
            }

            ItemStack displayStack = findDisplayStack(player, item);
            String displayName = resolveDisplayName(displayStack, item);
            return new CooldownEntry(displayStack, displayName, progress, ticksRemaining, totalTicks);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static ItemStack findDisplayStack(PlayerEntity player, Item item) {
        if (player == null) {
            return item.getDefaultStack();
        }

        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.isOf(item)) {
            return mainHand.copy();
        }

        ItemStack offHand = player.getOffHandStack();
        if (offHand.isOf(item)) {
            return offHand.copy();
        }

        DefaultedList<ItemStack> inventory = player.getInventory().getMainStacks();
        for (ItemStack stack : inventory) {
            if (stack.isOf(item)) {
                return stack.copy();
            }
        }

        return item.getDefaultStack();
    }

    public static List<CooldownEntry> preview() {
        List<CooldownEntry> previewEntries = new ArrayList<>();
        previewEntries.add(sampleEntry(Items.ENDER_PEARL, 16 * 20, 0.85f));
        previewEntries.add(sampleEntry(Items.CHORUS_FRUIT, 20 * 20, 0.55f));
        previewEntries.add(sampleEntry(Items.GOLDEN_APPLE, 4 * 20, 0.25f));
        return previewEntries;
    }

    private static CooldownEntry sampleEntry(Item item, int totalTicks, float progress) {
        float clampedProgress = MathHelper.clamp(progress, 0.0f, 1.0f);
        float ticksRemaining = totalTicks * clampedProgress;
        ItemStack stack = new ItemStack(item);
        String displayName = resolveDisplayName(stack, item);
        return new CooldownEntry(stack, displayName, clampedProgress, ticksRemaining, totalTicks);
    }

    private static String resolveDisplayName(ItemStack stack, Item item) {
        String name;
        if (stack != null && !stack.isEmpty()) {
            name = visibleString(stack.getName());
        } else if (item != null) {
            name = visibleString(item.getName());
        } else {
            name = "";
        }
        return stripBrackets(stripServerDecor(name));
    }

    // Симметричные обёртки-иконки серверов: одинаковые группы символов в начале и конце имени
    // («111 Имя 111» — в шрифте ресурспака это картинки, у нас — мусор).
    private static final java.util.regex.Pattern SYMMETRIC_WRAPPER =
            java.util.regex.Pattern.compile("^(\\S{1,8})\\s+(.+?)\\s+\\1$");
    // Схлопывание пробелов — static final (не компилировать Pattern на каждый вызов, горячий путь).
    private static final java.util.regex.Pattern MULTI_SPACE =
            java.util.regex.Pattern.compile("\\s{2,}");

    private static String stripServerDecor(String name) {
        if (name == null || name.isEmpty()) return "";
        // Приватные юникод-диапазоны (иконки ресурспаков) вычищаем посимвольно.
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= '\uE000' && c <= '\uF8FF') continue;
            sb.append(c);
        }
        String out = MULTI_SPACE.matcher(sb.toString()).replaceAll(" ").trim();
        java.util.regex.Matcher wrapper = SYMMETRIC_WRAPPER.matcher(out);
        if (wrapper.matches()) {
            out = wrapper.group(2).trim();
        }
        return out;
    }

    /**
     * Текст без кусков с кастомным шрифтом сервера: иконки ресурспаков — это обычные символы
     * ('1' и т.п.) со style.font, при getString() они превращаются в мусор вида «111 Имя 111».
     */
    private static String visibleString(net.minecraft.text.Text text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        text.visit((style, part) -> {
            net.minecraft.text.StyleSpriteSource font = style.getFont();
            if (font == null || net.minecraft.text.StyleSpriteSource.DEFAULT.equals(font)) {
                sb.append(part);
            }
            return java.util.Optional.empty();
        }, net.minecraft.text.Style.EMPTY);
        return MULTI_SPACE.matcher(sb.toString()).replaceAll(" ").trim();
    }

    private static String stripBrackets(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed.replace("[", "").replace("]", "");
    }

    private static Field lookupField(Class<?> owner, String... candidates) {
        for (String name : candidates) {
            if (name == null || name.isEmpty()) continue;
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    public record CooldownEntry(ItemStack stack, String displayName, float progress, float remainingTicks, float totalTicks) {

        public String formattedTime() {
            int totalSeconds = (int) Math.ceil(Math.max(0.0f, remainingTicks) / 20.0f);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        public int progressPercent() {
            return Math.round(progress * 100.0f);
        }
    }
}
