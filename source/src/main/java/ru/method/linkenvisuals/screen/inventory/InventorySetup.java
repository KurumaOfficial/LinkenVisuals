package ru.method.linkenvisuals.screen.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;

import java.util.Arrays;

/**
 * Снимок инвентаря игрока: 41 слот в индексации {@code PlayerInventory.getStack(i)}
 * (0..8 хотбар, 9..35 основной, 36..39 броня FEET/LEGS/CHEST/HEAD, 40 оффхенд).
 */
public final class InventorySetup {

    public static final int SLOTS = 41;

    public String name;
    public final ItemStack[] slots = new ItemStack[SLOTS];
    public int itemCount;

    public InventorySetup() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    public InventorySetup(String name) {
        this();
        this.name = name;
    }

    public JsonObject toJson(RegistryOps<JsonElement> ops) {
        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        object.addProperty("count", itemCount);

        JsonArray array = new JsonArray();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.isEmpty()) continue;
            JsonElement encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
            if (encoded == null) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", i);
            entry.add("item", encoded);
            array.add(entry);
        }
        object.add("slots", array);
        return object;
    }

    public static InventorySetup fromJson(RegistryOps<JsonElement> ops, JsonObject object) {
        InventorySetup setup = new InventorySetup(object.has("name") ? object.get("name").getAsString() : "inv");
        setup.itemCount = object.has("count") ? object.get("count").getAsInt() : 0;

        if (object.has("slots")) {
            for (JsonElement element : object.getAsJsonArray("slots")) {
                // битая запись (ручная правка файла, обрыв записи) не должна ронять весь сетап:
                // get() без has() вернул бы null и улетел NPE наружу
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("slot") || !entry.has("item")) continue;
                int slot = entry.get("slot").getAsInt();
                if (slot < 0 || slot >= SLOTS) continue;
                // предмет из удалённого мода / другого сервера не должен ронять загрузку всего файла
                setup.slots[slot] = ItemStack.CODEC.parse(ops, entry.get("item")).result().orElse(ItemStack.EMPTY);
            }
        }
        return setup;
    }
}
