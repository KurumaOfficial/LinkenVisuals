package ru.method.linkenvisuals.screen.inventory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.slot.SlotActionType;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.client.ClientTasks;
import ru.method.linkenvisuals.utils.inv.InventoryUtils;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InventorySetupManager implements Wrapper {

    private static final int SLOTS = InventorySetup.SLOTS;
    private static final int ARRANGE_STEP_BUDGET = 60;
    /**
     * Тиков между кликами обмена. Раньше все три клика уходили в ОДИН тик, и раскладка выдавала до
     * 180 ClickSlotC2SPacket за ~3 секунды — типовая сигнатура автокликера, на которую серверный
     * плагин отвечает закрытием контейнера (CloseScreenS2CPacket). 2 тика = 10 кликов/с, темп,
     * который человек реально выдаёт мышкой.
     */
    private static final int ARRANGE_CLICK_INTERVAL = 2;
    /** Сколько тиков после последнего клика ещё сторожим экран: серверу нужен RTT, чтобы прислать close. */
    private static final int REOPEN_GUARD_TICKS = 40;

    private static final List<InventorySetup> SETUPS = new ArrayList<>();
    private static InventorySetup loaded;
    private static boolean greeted;
    private static boolean overlay = true;
    private static boolean fileLoaded;

    private static List<Missing> missingCache = List.of();
    private static InventorySetup missingFor;
    private static int missingAge = Integer.MIN_VALUE;

    private static boolean arranging;
    private static int arrangeStepsLeft;
    /** 0 — раскладку не доводили до конца; иначе момент, когда двигать стало нечего. */
    private static long arrangedAtMs;

    /** Очередь кликов текущего обмена (src → dst → src); pendingIndex == 3 — обмен закрыт. */
    private static final int[] PENDING_CLICKS = new int[3];
    private static int pendingIndex = 3;

    private static int reopenGuardTicks;
    private static boolean reopenGuardRunning;
    /** Игрок закрыл инвентарь сам (E/ESC) — сторож обязан замолчать и не открывать окно обратно. */
    private static boolean closedByPlayer;

    private InventorySetupManager() {
    }

    public record Missing(ItemStack sample, int count) {
    }

    public static List<InventorySetup> setups() {
        ensureLoaded();
        return SETUPS;
    }

    public static InventorySetup loaded() {
        return loaded;
    }

    public static void load(InventorySetup s) {
        loaded = s;
        missingFor = null;
        arrangedAtMs = 0L;
    }

    /** Раскладка идёт прямо сейчас (обмены уходят по одному за тик). */
    public static boolean arranging() {
        return arranging;
    }

    /** Раскладка доведена до конца: двигать больше нечего. Сбрасывается при новом клике и смене шаблона. */
    public static boolean arranged() {
        return arrangedAtMs != 0L;
    }

    public static InventorySetup saveCurrent(String name) {
        if (mc.player == null) return null;
        ensureLoaded();

        InventorySetup setup = null;
        for (InventorySetup existing : SETUPS) {
            if (existing.name != null && existing.name.equals(name)) {
                setup = existing;
                break;
            }
        }
        if (setup == null) {
            setup = new InventorySetup(name);
            SETUPS.add(setup);
        }

        PlayerInventory inv = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = i < inv.size() ? inv.getStack(i).copy() : ItemStack.EMPTY;
            setup.slots[i] = stack;
            if (!stack.isEmpty()) count++;
        }
        setup.itemCount = count;

        if (loaded == setup) missingFor = null;
        save();
        return setup;
    }

    public static void remove(InventorySetup s) {
        ensureLoaded();
        SETUPS.remove(s);
        if (loaded == s) {
            loaded = null;
            missingFor = null;
        }
        save();
    }

    public static boolean overlayEnabled() {
        return overlay;
    }

    public static void setOverlayEnabled(boolean v) {
        if (overlay == v) return;
        overlay = v;
        save();
    }

    public static boolean greeted() {
        return greeted;
    }

    public static void setGreeted(boolean v) {
        if (greeted == v) return;
        greeted = v;
        save();
    }

    /** Недостающие относительно loaded(): предмет → сколько не хватает. Пусто если loaded()==null. */
    public static List<Missing> missing() {
        if (loaded == null || mc.player == null) return List.of();

        int age = mc.player.age;
        if (loaded == missingFor && Math.abs(age - missingAge) < 5) return missingCache;
        missingFor = loaded;
        missingAge = age;

        // ponytail: сравнение по Item, компоненты (зачарования/имена) игнорируются —
        // на apply перейти на ItemStack.areItemsAndComponentsEqual
        Map<Item, Integer> need = new LinkedHashMap<>();
        Map<Item, ItemStack> sample = new HashMap<>();
        for (ItemStack stack : loaded.slots) {
            if (stack == null || stack.isEmpty()) continue;
            need.merge(stack.getItem(), stack.getCount(), Integer::sum);
            sample.putIfAbsent(stack.getItem(), stack);
        }

        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < Math.min(inv.size(), SLOTS); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            need.computeIfPresent(stack.getItem(), (item, left) -> left - stack.getCount());
        }

        List<Missing> result = new ArrayList<>();
        need.forEach((item, left) -> {
            if (left > 0) result.add(new Missing(sample.get(item), left));
        });
        result.sort(Comparator.comparingInt(Missing::count).reversed());
        missingCache = result;
        return missingCache;
    }

    /** Поставить в очередь раскладку имеющихся предметов по слотам шаблона (по 1 обмену за тик). */
    public static void arrangeAll() {
        if (loaded == null || arranging || mc.player == null) return;
        arranging = true;
        arrangedAtMs = 0L;
        pendingIndex = 3;
        closedByPlayer = false;
        arrangeStepsLeft = ARRANGE_STEP_BUDGET;
        startReopenGuard();
        ClientTasks.schedule(1, InventorySetupManager::arrangeStep);
    }

    /** Игрок закрыл инвентарь сам — прекращаем и раскладку, и сторожа (см. {@code HandledScreenMixin}). */
    public static void notifyPlayerClosedScreen() {
        closedByPlayer = true;
        arranging = false;
        pendingIndex = 3;
        reopenGuardTicks = 0;
    }

    private static void startReopenGuard() {
        reopenGuardTicks = REOPEN_GUARD_TICKS;
        if (reopenGuardRunning) return;
        reopenGuardRunning = true;
        ClientTasks.schedule(1, InventorySetupManager::reopenGuard);
    }

    /**
     * Сервер отвечает на серию ClickSlot закрытием контейнера: CloseScreenS2CPacket →
     * ClientPlayNetworkHandler.onCloseScreen → ClientPlayerEntity.closeScreen(), минуя
     * HandledScreen.close(). Возвращаем экран сами — открытие СВОЕГО инвентаря чисто клиентское
     * (как нажатие E), пакета на открытие не требуется. Закрытие руками игрока идёт через
     * HandledScreen.close() и гасит сторожа, поэтому окно не «залипает».
     */
    private static void reopenGuard() {
        if (reopenGuardTicks-- <= 0 || closedByPlayer) {
            reopenGuardRunning = false;
            return;
        }
        // Открываем только когда экрана нет вообще — чужой экран не подменяем.
        if (mc.player != null && mc.currentScreen == null) mc.setScreen(new InventoryScreen(mc.player));
        ClientTasks.schedule(1, InventorySetupManager::reopenGuard);
    }

    private static void arrangeStep() {
        if (!arranging) return;
        // клики по инвентарю вне открытого InventoryScreen — типовой флаг античита
        if (loaded == null || mc.player == null || !(mc.currentScreen instanceof InventoryScreen)) {
            arranging = false; // прервано — «разложено» не заявляем
            pendingIndex = 3;
            return;
        }
        reopenGuardTicks = REOPEN_GUARD_TICKS; // сторож живёт всю раскладку, а не только её старт

        // Клики обмена сливаем по одному за ARRANGE_CLICK_INTERVAL тиков.
        if (pendingIndex < 3) {
            InventoryUtils.clickPlayerHandlerSlot(PENDING_CLICKS[pendingIndex++], 0, SlotActionType.PICKUP);
            ClientTasks.schedule(ARRANGE_CLICK_INTERVAL, InventorySetupManager::arrangeStep);
            return;
        }

        // Курсор проверяем ТОЛЬКО между обменами: внутри обмена он законно держит стак.
        // Грязный курсор здесь = предыдущий обмен не закрылся (напр. броня с Curse of Binding);
        // следующий клик «поднять из src» на деле положил бы застрявший стак в чужой слот.
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
            arranging = false;
            return;
        }
        // Бюджет кончился = работа осталась, это тоже НЕ успех.
        if (arrangeStepsLeft-- <= 0) {
            arranging = false;
            return;
        }
        if (!exchangeOnce()) {
            // двигать нечего — раскладка закончена
            arranging = false;
            arrangedAtMs = System.currentTimeMillis();
            return;
        }
        ClientTasks.schedule(ARRANGE_CLICK_INTERVAL, InventorySetupManager::arrangeStep);
    }

    /** Один обмен из живого состояния инвентаря: очередь, посчитанная заранее, устаревает после первого же клика. */
    private static boolean exchangeOnce() {
        PlayerInventory inv = mc.player.getInventory();
        int size = Math.min(inv.size(), SLOTS);

        boolean[] satisfied = new boolean[SLOTS];
        for (int i = 0; i < size; i++) {
            ItemStack want = loaded.slots[i];
            if (want != null && !want.isEmpty() && inv.getStack(i).getItem() == want.getItem()) satisfied[i] = true;
        }

        for (int i = 0; i < size; i++) {
            ItemStack want = loaded.slots[i];
            if (want == null || want.isEmpty() || satisfied[i]) continue;
            // занятый чужой бронёй слот пропускаем: предмет туда не встанет и обмен зациклится «снял-надел»
            if (i >= 36 && i <= 39 && !inv.getStack(i).isEmpty()) continue;
            // ПУСТОЙ слот брони с неподходящим предметом в шаблоне — тот же мёртвый цикл:
            // Slot.canInsert=false рубит второй клик, третий возвращает всё назад, satisfied[i]
            // не поднимается, и один и тот же обмен повторяется до конца бюджета впустую.
            if (i >= 36 && i <= 39
                    && !mc.player.playerScreenHandler.getSlot(handlerSlot(i)).canInsert(want)) continue;

            int from = -1;
            for (int j = 0; j < size; j++) {
                if (j == i || satisfied[j]) continue;
                // третий клик вернёт бывшее содержимое i в слот j; в слот брони не-броня не встанет
                // (ArmorSlot.canInsert=false → insertStack отдаёт стак назад) и он застрянет на курсоре
                if (j >= 36 && j <= 39 && !inv.getStack(i).isEmpty()) continue;
                if (inv.getStack(j).getItem() == want.getItem()) {
                    from = j;
                    break;
                }
            }
            if (from < 0) continue;

            // Не кликаем прямо здесь: три пакета в один тик — сигнатура автокликера.
            // Ставим обмен в очередь, arrangeStep сливает по клику за ARRANGE_CLICK_INTERVAL тиков.
            PENDING_CLICKS[0] = handlerSlot(from);
            PENDING_CLICKS[1] = handlerSlot(i);
            PENDING_CLICKS[2] = PENDING_CLICKS[0];
            pendingIndex = 0;
            return true;
        }
        return false;
    }

    /** Индекс PlayerInventory → slotId PlayerScreenHandler (InventoryUtils.indexToSlot покрывает только хотбар). */
    static int handlerSlot(int i) {
        if (i <= 8) return 36 + i;
        if (i <= 35) return i;
        if (i <= 39) return 8 - (i - 36);
        return 45;
    }

    private static Path path() {
        return LinkenVisuals.getInstance().getGlobalsDir().toPath().resolve("setups.linken");
    }

    private static RegistryOps<JsonElement> ops() {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        return handler == null ? null : handler.getRegistryManager().getOps(JsonOps.INSTANCE);
    }

    /**
     * WrapperLookup существует только в мире, поэтому грузим лениво: без сети выходим НЕ помечая
     * файл загруженным — первое реальное обращение всегда идёт из открытого инвентаря.
     */
    private static void ensureLoaded() {
        if (fileLoaded) return;
        RegistryOps<JsonElement> ops = ops();
        if (ops == null) return;

        Path path = path();
        if (!Files.exists(path)) {
            // файла нет — грузить нечего, но состояние достоверно: save() разрешён
            fileLoaded = true;
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            boolean greetedRead = root.has("greeted") && root.get("greeted").getAsBoolean();
            boolean overlayRead = !root.has("overlay") || root.get("overlay").getAsBoolean();

            // парсим в локальный список: срыв на середине не должен оставить SETUPS полупустым
            List<InventorySetup> parsed = new ArrayList<>();
            if (root.has("setups")) {
                for (JsonElement element : root.getAsJsonArray("setups")) {
                    try {
                        parsed.add(InventorySetup.fromJson(ops, element.getAsJsonObject()));
                    } catch (Exception e) {
                        LinkenVisuals.LOGGER.warn("[InventorySetupManager] Skipping broken setup entry", e);
                    }
                }
            }

            greeted = greetedRead;
            overlay = overlayRead;
            SETUPS.clear();
            SETUPS.addAll(parsed);
            // флаг ТОЛЬКО после успешного чтения: иначе битый файл считался бы загруженным
            // и первый же save() записал бы поверх него пустой список
            fileLoaded = true;
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[InventorySetupManager] Error loading setups:", e);
        }
    }

    private static void save() {
        RegistryOps<JsonElement> ops = ops();
        if (ops == null) return;
        ensureLoaded();
        // пока файл не прочитан успешно, SETUPS не отражает диск — запись стёрла бы шаблоны игрока
        if (!fileLoaded) return;

        try {
            JsonObject root = new JsonObject();
            root.addProperty("greeted", greeted);
            root.addProperty("overlay", overlay);

            JsonArray array = new JsonArray();
            for (InventorySetup setup : SETUPS) array.add(setup.toJson(ops));
            root.add("setups", array);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);

            // сериализация целиком в память, затем атомарная подмена: сбой кодека, полный диск
            // или kill процесса оставляют старый файл нетронутым, а не нулевой длины
            Path path = path();
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = path.resolveSibling("setups.linken.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[InventorySetupManager] Error saving setups:", e);
        }
    }
}
