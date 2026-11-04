package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.inv.AuctionPrice;
import ru.method.linkenvisuals.utils.inv.InventoryUtils;

import java.util.Locale;

public class AutoRelist extends Module {
    private static final long PLAYER_STEP_DELAY_MS = 500L;
    private static final long PLAYER_CLOSE_DELAY_MS = 300L;
    private static final long PLAYER_TIMEOUT_MS = 5000L;
    private static final long START_AFTER_SCREEN_CLOSE_MS = 2000L;
    private static final String[] AUCTION_STORAGE_NAMES = {
            "\u0445\u0440\u0430\u043d\u0438\u043b\u0438", "\u043c\u043e\u0438 \u0442\u043e\u0432\u0430\u0440", "\u043c\u043e\u0438 \u043b\u043e\u0442",
            "\u043c\u043e\u0438 \u043f\u0440\u043e\u0434\u0430\u0436", "\u0442\u043e\u0432\u0430\u0440", "\u043f\u0440\u0435\u0434\u043c\u0435\u0442", "\u043b\u043e\u0442",
            "storage", "items", "sales", "lots"
    };
    private final EnumSetting<Mode> mode = new EnumSetting<>("modules.settings.auto_relist.mode", Mode.Prince);
    private final NumberSetting delay = new NumberSetting("modules.settings.auto_relist.delay", 65f, 20f, 180f, 5f);
    private long nextRelistMs;
    private long lastPlayerActionMs;
    private int playerStage;
    // Player-\u0440\u0435\u0436\u0438\u043c: \u0446\u0435\u043d\u0430 \u0438 \u0441\u043d\u0438\u043c\u043e\u043a \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u0430 \u0442\u0435\u043a\u0443\u0449\u0435\u0433\u043e \u043f\u0435\u0440\u0435\u0432\u044b\u0441\u0442\u0430\u0432\u043b\u044f\u0435\u043c\u043e\u0433\u043e \u043b\u043e\u0442\u0430.
    private long pendingPrice = -1;
    private ItemStack pendingStack;

    public AutoRelist() {
        super("AutoRelist", Category.Player);
    }

    @Override
    public void onEnable() {
        nextRelistMs = System.currentTimeMillis() + START_AFTER_SCREEN_CLOSE_MS;
        resetPlayerMode();
        super.onEnable();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck() || mc.player.networkHandler == null) return;

        long now = System.currentTimeMillis();
        if (playerStage == 0 && mc.currentScreen != null) {
            nextRelistMs = now + START_AFTER_SCREEN_CLOSE_MS;
            return;
        }

        if (mode.getValue() == Mode.Player) {
            tickPlayerMode();
            return;
        }

        if (now < nextRelistMs) return;

        mc.player.networkHandler.sendChatCommand("ah resell");
        nextRelistMs = now + (long) (delay.getValue() * 1000L);
    }

    private void tickPlayerMode() {
        long now = System.currentTimeMillis();

        if (playerStage == 0) {
            if (now < nextRelistMs) return;
            mc.player.networkHandler.sendChatCommand("ah");
            playerStage = 1;
            lastPlayerActionMs = now;
            return;
        }

        if (now - lastPlayerActionMs > PLAYER_TIMEOUT_MS) {
            resetPlayerMode();
            return;
        }

        switch (playerStage) {
            case 1 -> { // ждём аукцион, открываем «мои продажи»
                if (now - lastPlayerActionMs < PLAYER_STEP_DELAY_MS || !isAuctionScreen()) return;
                if (clickAuctionStorageButton()) {
                    playerStage = 2;
                    lastPlayerActionMs = now;
                }
            }
            case 2 -> { // в «моих продажах»: читаем цену первого лота и забираем его в инвентарь
                if (now - lastPlayerActionMs < PLAYER_STEP_DELAY_MS || !isStorageScreen()) return;
                ScreenHandler handler = getForeignHandler();
                if (handler == null) { resetPlayerMode(); return; }
                int lot = findPricedLotSlot(handler);
                if (lot < 0) { finishPlayerCycle(now); return; } // нет лотов с ценой — цикл окончен
                pendingPrice = AuctionPrice.parse(handler.getSlot(lot).getStack());
                pendingStack = handler.getSlot(lot).getStack().copy();
                clickSlot(handler, lot); // клик по своему лоту → предмет в инвентарь
                playerStage = 3;
                lastPlayerActionMs = now;
            }
            case 3 -> { // закрываем контейнер (нужно, чтобы взять предмет в руку и продать)
                if (now - lastPlayerActionMs < PLAYER_STEP_DELAY_MS) return;
                if (mc.player != null) mc.player.closeHandledScreen();
                playerStage = 4;
                lastPlayerActionMs = now;
            }
            case 4 -> { // GUI закрыт: берём предмет в руку и /ah sell <цена>
                if (now - lastPlayerActionMs < PLAYER_CLOSE_DELAY_MS) return;
                if (getForeignHandler() != null) { // ещё не закрылось — добиваем
                    if (mc.player != null) mc.player.closeHandledScreen();
                    return;
                }
                if (pendingStack != null && pendingPrice > 0 && takeInHand(pendingStack)) {
                    mc.player.networkHandler.sendChatCommand("ah sell " + pendingPrice);
                }
                finishPlayerCycle(now);
            }
            default -> resetPlayerMode();
        }
    }

    /** Первый слот-лот с ценой в лоре («мои продажи»). Контролы без цены пропускаются. */
    private int findPricedLotSlot(ScreenHandler handler) {
        int content = getContentSlotCount(handler);
        for (int i = 0; i < content; i++) {
            Slot slot = handler.getSlot(i);
            if (slot == null || !slot.hasStack()) continue;
            if (AuctionPrice.parse(slot.getStack()) >= 0) return i;
        }
        return -1;
    }

    /** Найти предмет в инвентаре по типу и взять в руку (хотбар — выбор; основной инв — свап в хотбар). */
    private boolean takeInHand(ItemStack want) {
        if (mc.player == null || mc.interactionManager == null || mc.player.networkHandler == null) return false;
        PlayerInventory inv = mc.player.getInventory();
        int found = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() == want.getItem()) { found = i; break; }
        }
        if (found < 0) return false;
        if (found <= 8) { // уже в хотбаре — просто выбираем и синкаем серверу
            inv.setSelectedSlot(found);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(found));
        } else { // основной инвентарь → SWAP в текущий слот хотбара (сервер держит hotbar[selectedSlot])
            InventoryUtils.clickHandlerSlot(mc.player.playerScreenHandler.syncId, found, inv.getSelectedSlot(), SlotActionType.SWAP);
        }
        return true;
    }

    private boolean clickAuctionStorageButton() {
        ScreenHandler handler = getForeignHandler();
        if (handler == null) return false;

        int base = getContentSlotCount(handler) - 9;
        if (base < 0) return false;

        if (clickMatchingSlot(handler, base, base + 2, true, AUCTION_STORAGE_NAMES)) return true;
        return clickFirstExistingSlot(handler, base + 2, base + 1, base);
    }

    private boolean clickMatchingSlot(ScreenHandler handler, int from, int to, boolean reverse, String... names) {
        if (names.length == 0) return false;

        int start = Math.max(0, Math.min(from, to));
        int end = Math.min(getContentSlotCount(handler) - 1, Math.max(from, to));
        if (end < start) return false;

        if (reverse) {
            for (int i = end; i >= start; i--) {
                if (slotNameContains(handler.getSlot(i), names)) {
                    clickSlot(handler, i);
                    return true;
                }
            }
            return false;
        }

        for (int i = start; i <= end; i++) {
            if (slotNameContains(handler.getSlot(i), names)) {
                clickSlot(handler, i);
                return true;
            }
        }
        return false;
    }

    private boolean slotNameContains(Slot slot, String... names) {
        if (slot == null || !slot.hasStack()) return false;

        ItemStack stack = slot.getStack();
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        for (String expected : names) {
            if (!expected.isEmpty() && name.contains(expected)) return true;
        }
        return false;
    }

    private boolean clickFirstExistingSlot(ScreenHandler handler, int... slots) {
        int lastContentSlot = getContentSlotCount(handler) - 1;
        for (int slot : slots) {
            if (slot < 0 || slot > lastContentSlot) continue;
            if (!handler.getSlot(slot).hasStack()) continue;
            clickSlot(handler, slot);
            return true;
        }
        return false;
    }

    private void clickSlot(ScreenHandler handler, int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        InventoryUtils.clickHandlerSlot(handler.syncId, slot, 0, SlotActionType.PICKUP);
    }

    private ScreenHandler getForeignHandler() {
        if (mc.player == null || mc.interactionManager == null) return null;
        ScreenHandler handler = mc.player.currentScreenHandler;
        return handler == null || handler == mc.player.playerScreenHandler ? null : handler;
    }

    private int getContentSlotCount(ScreenHandler handler) {
        return Math.max(0, handler.slots.size() - 36);
    }

    private boolean isAuctionScreen() {
        String title = getScreenTitle();
        return title.contains("\u0430\u0443\u043a\u0446\u0438\u043e\u043d") || title.contains("auction") || title.contains("ah");
    }

    private boolean isStorageScreen() {
        String title = getScreenTitle();
        return title.contains("\u0445\u0440\u0430\u043d\u0438\u043b\u0438") || title.contains("\u0441\u043a\u043b\u0430\u0434") || title.contains("storage");
    }

    private String getScreenTitle() {
        if (mc.currentScreen == null || mc.currentScreen.getTitle() == null) return "";
        return mc.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
    }

    private void finishPlayerCycle(long now) {
        resetPlayerMode();
        nextRelistMs = now + (long) (delay.getValue() * 1000L);
    }

    private void resetPlayerMode() {
        playerStage = 0;
        lastPlayerActionMs = 0L;
        pendingPrice = -1;
        pendingStack = null;
    }

    private enum Mode implements Nameable {
        Prince("modules.settings.auto_relist.mode.prince"),
        Player("modules.settings.auto_relist.mode.player");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
