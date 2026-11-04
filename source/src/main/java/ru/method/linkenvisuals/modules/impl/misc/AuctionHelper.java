package ru.method.linkenvisuals.modules.impl.misc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;
import ru.method.linkenvisuals.utils.inv.AuctionPrice;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;
import java.util.List;

/**
 * Аукцион-хелпер: подсвечивает в окне аукциона самый ДЕШЁВЫЙ (зелёный) и самый ДОРОГОЙ (красный) лот.
 * Цена парсится из названия/лора предмета (с поддержкой k/kk/m/кк и дробей). Только подсветка фона слота.
 */
public class AuctionHelper extends Module {

    private final ColorSetting cheapestColor = new ColorSetting("settings.auctionhelper.cheapest", new Color(75, 255, 75, 255));
    private final ColorSetting expensiveColor = new ColorSetting("settings.auctionhelper.effective", new Color(255, 75, 75, 255));
    /** Часть названия GUI аукциона. Несколько вариантов — через запятую (сервера зовут окно по-разному). */
    private final StringSetting titleKeyword =
            new StringSetting("settings.auctionhelper.title", "аукцион, auction", "аукцион, auction");

    public AuctionHelper() {
        super("AuctionHelper", Category.Other);
        getSettings().add(cheapestColor);
        getSettings().add(expensiveColor);
        getSettings().add(titleKeyword);
    }


    // Считается раз за кадр (render HEAD), фон рисуется per-slot ДО предмета (drawSlot HEAD).
    private Slot cheapestSlot;
    private Slot expensiveSlot;
    private float framePulse;

    // Детект аукциона по СОДЕРЖИМОМУ, а не по заголовку: сервер рисует заголовок и счётчик страниц
    // кастомным шрифтом, поэтому getString() их не читает. Зато цена в лоре — обычные цифры.
    private static final int MIN_PRICED_LOTS = 3;

    /** Команды, после которых сервер открывает аукцион. Дополняются словами из настройки «title». */
    private static final String[] AUCTION_COMMANDS = {"ah", "auction", "аукцион", "ах", "market"};
    /** Сколько ждём GUI после команды: сервер открывает его в течение секунды-двух, берём с запасом. */
    private static final long COMMAND_WINDOW_MS = 8000L;

    private static volatile long auctionCommandMs = -COMMAND_WINDOW_MS;
    /** Экран, уже признанный аукционом (по команде) — пока он открыт, повторно проверять не нужно. */
    private Object commandAuctionScreen;

    /**
     * Игрок отправил команду — если это вызов аукциона, взводим окно ожидания GUI.
     * Зовётся из перехвата отправки команд (ClientPlayNetworkHandlerMixin).
     */
    public static void noteCommand(String command) {
        if (command == null) return;
        String c = command.trim().toLowerCase(java.util.Locale.ROOT);
        while (c.startsWith("/")) c = c.substring(1);
        // Сравниваем ПЕРВОЕ слово: «/ah», «/ah sell», «/auction» — да; «/help ah» — нет.
        int sp = c.indexOf(' ');
        String head = sp < 0 ? c : c.substring(0, sp);
        for (String kw : AUCTION_COMMANDS) {
            if (head.equals(kw)) {
                auctionCommandMs = System.currentTimeMillis();
                return;
            }
        }
    }

    public void prepare(HandledScreen<?> screen) {
        cheapestSlot = null;
        expensiveSlot = null;
        if (mc.player == null || screen == null || screen.getScreenHandler() == null) return;
        // Хелпер работает ТОЛЬКО в меню аукциона: одного «есть предметы с ценой» мало — ценники есть и
        // в шопах, и в меню продажи. Аукцион опознаём двумя путями: по названию GUI ИЛИ по тому, что
        // это окно открылось сразу после команды игрока (/ah, /auction — заголовок читать не нужно).
        if (!isAuctionScreen(screen)) return;

        List<Slot> slots = screen.getScreenHandler().slots;

        long minPrice = Long.MAX_VALUE;
        long maxPrice = -1;
        int priced = 0;
        for (Slot s : slots) {
            if (!isLotSlot(s)) continue;
            long price = AuctionPrice.parse(s.getStack());
            if (price < 0) continue;
            priced++;
            if (price < minPrice) {
                minPrice = price;
                cheapestSlot = s;
            }
            if (price > maxPrice) {
                maxPrice = price;
                expensiveSlot = s;
            }
        }

        // Не аукцион (мало лотов с ценой) — ничего не красим, чтобы не подсвечивать чужие контейнеры.
        if (priced < MIN_PRICED_LOTS) {
            cheapestSlot = null;
            expensiveSlot = null;
            return;
        }

        // Один лот (или все с одной ценой) — красим только как «дешёвый», без дубля.
        if (expensiveSlot == cheapestSlot || maxPrice == minPrice) expensiveSlot = null;

        framePulse = pulseAlpha();
    }

    /**
     * Подсветка ПОВЕРХ предметов на render TAIL (координаты абсолютные: guiX+slot.x). Фон-подсветка ЗА
     * предметом (старый drawSlot HEAD) на кастомном GUI сервера невидима — предмет заполняет слот.
     */
    public void renderOverlay(DrawContext ctx, int guiX, int guiY) {
        drawOver(ctx, cheapestSlot, guiX, guiY, cheapestColor.getValue());
        drawOver(ctx, expensiveSlot, guiX, guiY, expensiveColor.getValue());
    }

    private void drawOver(DrawContext ctx, Slot slot, int guiX, int guiY, Color color) {
        if (slot == null) return;
        // Полупрозрачная заливка тинтит предмет (видно поверх), + пульс. 0.55 — чтобы предмет читался.
        int a = Math.max(0, Math.min(255, Math.round(color.getAlpha() * framePulse * 0.55f)));
        Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
        Render2D.drawRoundedRect(ctx.getMatrices(), guiX + slot.x, guiY + slot.y, 16f, 16f, 0f, c);
    }

    /**
     * Это меню аукциона? Да, если название GUI содержит ключевое слово ИЛИ окно открылось в течение
     * {@link #COMMAND_WINDOW_MS} после команды вызова аукциона. Признанный по команде экран
     * запоминается — пока он открыт (смена страниц идёт в том же экземпляре), проверка не повторяется.
     */
    private boolean isAuctionScreen(HandledScreen<?> screen) {
        if (screen == commandAuctionScreen) return true;
        if (isAuctionTitle(screen)) return true;
        if (System.currentTimeMillis() - auctionCommandMs <= COMMAND_WINDOW_MS) {
            commandAuctionScreen = screen;   // это окно открылось по нашей команде — оно и есть аукцион
            return true;
        }
        return false;
    }

    /**
     * Название GUI содержит ключевое слово аукциона. Сервера часто рисуют заголовок кастомным шрифтом
     * (приватные глифы U+E000..U+F8FF) — их выкидываем, чтобы они не мешали сопоставлению, а сравниваем
     * то, что осталось, без регистра. Пустая настройка = дефолтные «аукцион/auction».
     */
    private boolean isAuctionTitle(HandledScreen<?> screen) {
        if (screen.getTitle() == null) return false;
        StringBuilder clean = new StringBuilder();
        for (char c : screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (c < '' || c > '') clean.append(c);
        }
        String title = clean.toString();
        String raw = titleKeyword.getValue() == null ? "" : titleKeyword.getValue().trim();
        if (raw.isEmpty()) raw = "аукцион, auction";
        for (String part : raw.toLowerCase(java.util.Locale.ROOT).split(",")) {
            String kw = part.trim();
            if (!kw.isEmpty() && title.contains(kw)) return true;
        }
        return false;
    }

    private boolean isLotSlot(Slot s) {
        if (s == null) return false;
        // Фильтр по y убран: у сервера кастомный GUI с толстой рамкой, лоты стоят низко (y>=104) и
        // старый порог их резал (lots=0). Контролы (стрелки/страница) и так не попадают — у них нет
        // «Цена …» в лоре, значит parsePrice=-1 и в подсветку они не входят.
        if (mc.player != null && s.inventory == mc.player.getInventory()) return false;
        return !s.getStack().isEmpty();
    }

    /** Пульсация альфы подсветки [0.4..1.0]. */
    private float pulseAlpha() {
        float t = (System.currentTimeMillis() % 900L) / 900f;
        float wave = 0.5f - 0.5f * (float) Math.cos(t * 2.0 * Math.PI);
        return 0.4f + 0.6f * wave;
    }
}
