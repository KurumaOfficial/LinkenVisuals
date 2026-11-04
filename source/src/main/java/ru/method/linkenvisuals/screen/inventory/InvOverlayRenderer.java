package ru.method.linkenvisuals.screen.inventory;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderLayer;
import org.joml.Matrix3x2fStack;
import net.minecraft.item.ItemStack;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.List;

/**
 * Встройка менеджера инвентарей в ванильный экран инвентаря: кнопка-сундук слева от окна (ДОП-9)
 * и панель «Не хватает:» справа (ДОП-10). Вся логика здесь, {@code HandledScreenMixin} только зовёт.
 *
 * <p>Геометрия окна (x/y/backgroundWidth) приходит живой каждый кадр — открытие книги рецептов
 * двигает окно, пересчитывать/кэшировать нечего.</p>
 */
public final class InvOverlayRenderer implements Wrapper {
    private InvOverlayRenderer() {}

    private static final int GAP = 4;
    private static final int BTN = 22;

    private static final int PANEL_W = 132;
    private static final int PANEL_GAP = 6;
    private static final int ROW_H = 18;
    private static final int HEADER_H = 18;
    private static final int ACTION_H = 16;
    private static final int PAD = 6;
    private static final int MAX_ROWS = 8;

    private static final Font FONT = Fonts.SEMIBOLD;
    private static final float SIZE_TITLE = 8f;
    private static final float SIZE_ROW = 7f;

    private static final Color BG = new Color(18, 18, 20, 228);
    private static final Color BG_HOVER = new Color(38, 38, 44, 235);
    private static final Color TITLE = new Color(255, 85, 85);
    private static final Color NAME = new Color(226, 226, 232);
    private static final Color COUNT = new Color(255, 130, 130);
    private static final Color ROW_HOVER = new Color(255, 255, 255, 24);
    private static final Color ACTION_BG = new Color(255, 255, 255, 22);
    private static final Color ACTION_BG_HOVER = new Color(255, 255, 255, 46);
    private static final Color DONE_BG = new Color(255, 255, 255, 12);
    private static final Color ICON = new Color(235, 235, 240);
    private static final Color MUTED = new Color(150, 150, 160);

    // ---------- геометрия ----------

    /** Кнопка-сундук: квадрат слева от окна. Отрицательный x — окно прижато к краю, кнопку прячем. */
    private static int buttonX(int x) {
        return x - BTN - GAP;
    }

    /**
     * Прячем кнопку при открытой книге рецептов: она рисуется ПОСЛЕ нашего оверлея и полностью
     * накрывает кнопку, но RecipeBookWidget.mouseClicked съедает не всю панель — щели в правом
     * верхнем углу проваливаются в HandledScreen.mouseClicked, и невидимая кнопка ловит клик.
     * Геттеров у виджета нет, поэтому детектим по сдвигу окна: RecipeBookWidget.findLeftEdge
     * при закрытой/узкой книге даёт ровно (width - bgW) / 2, при открытой не-narrow — ровно на 77 больше.
     */
    private static boolean buttonVisible(InventoryScreen screen, int x, int bgW) {
        return buttonX(x) >= 0 && x == (screen.width - bgW) / 2;
    }

    private static boolean overRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    private record Panel(int x, int y, int h, int rows, int hidden) {
        int listY() {
            return y + HEADER_H;
        }

        int actionY() {
            return y + h - PAD - ACTION_H;
        }
    }

    /**
     * null — панель не показывается. Заодно отсекает узкий лэйаут (книга рецептов, width &lt; 379):
     * там панель не влезает справа, а её клики съедает RecipeBookScreen.mouseClicked.
     */
    private static Panel panel(InventoryScreen screen, int x, int y, int bgW) {
        if (!InventorySetupManager.overlayEnabled() || InventorySetupManager.loaded() == null) return null;

        // Панель показывается при ЛЮБОМ загруженном шаблоне, даже когда ничего не не хватает:
        // «Разложить всё» нужна и тогда, когда все предметы есть, но лежат не в своих слотах.
        // Раньше пустой список нехватки прятал панель целиком — вместе с кнопкой, и «Загрузить»
        // выглядела нерабочей: игрок жал её и не видел ровным счётом ничего.
        List<InventorySetupManager.Missing> missing = InventorySetupManager.missing();

        // Ванильный список эффектов рисуется ПОСЛЕ нас от x + bgW + 2 шириной 120 (широкий режим,
        // available >= 120) или 32 — ровно поверх панели. Сдвигаемся за него; если после сдвига
        // места не осталось, панель скроет проверка ниже.
        int fx = (mc.player == null || mc.player.getStatusEffects().isEmpty() || !screen.showsStatusEffects())
                ? 0
                : (screen.width - (x + bgW + 2) >= 120 ? 124 : 36);
        int px = x + bgW + PANEL_GAP + fx;
        if (px + PANEL_W > screen.width) return null;

        // -10 — резерв под строку «и ещё N», которую h прибавляет при hidden > 0:
        // без него нижний край панели (и кнопка «Разложить всё») уезжает за экран.
        int free = screen.height - y - HEADER_H - PAD * 2 - ACTION_H - 10;
        int fit = Math.max(1, Math.min(MAX_ROWS, free / ROW_H));
        int rows = Math.min(missing.size(), fit);
        int hidden = missing.size() - rows;
        int h = HEADER_H + rows * ROW_H + (hidden > 0 ? 10 : 0) + PAD * 2 + ACTION_H;
        return new Panel(px, y, h, rows, hidden);
    }

    // ---------- рендер ----------

    public static void render(DrawContext context, InventoryScreen screen, int x, int y, int bgW, int mouseX, int mouseY) {
        if (mc.player == null) return;
        Matrix3x2fStack ms = context.getMatrices();

        if (buttonVisible(screen, x, bgW)) {
            int bx = buttonX(x);
            boolean hover = overRect(mouseX, mouseY, bx, y, BTN, BTN);
            Render2D.drawRoundedRect(ms, bx, y, BTN, BTN, 5f, hover ? BG_HOVER : BG);
            ClickGuiIcons.cube(ms, bx + BTN / 2f, y + BTN / 2f, 12f, ICON);
        }

        Panel p = panel(screen, x, y, bgW);
        if (p == null) return;

        Render2D.drawRoundedRect(ms, p.x(), p.y(), PANEL_W, p.h(), 5f, BG);
        // Заголовок зависит от состояния: при полном комплекте «Не хватает:» вводило бы в заблуждение.
        boolean nothingMissing = p.rows() == 0 && p.hidden() == 0;
        Render2D.drawFont(ms, FONT.getFont(SIZE_TITLE),
                nothingMissing ? "Всё на месте" : "Не хватает:",
                p.x() + PAD, p.y() + (HEADER_H - FONT.getHeight(SIZE_TITLE)) / 2f,
                nothingMissing ? MenuChrome.accent() : TITLE);

        List<InventorySetupManager.Missing> missing = InventorySetupManager.missing();
        float textY = (ROW_H - FONT.getHeight(SIZE_ROW)) / 2f;
        for (int i = 0; i < p.rows(); i++) {
            InventorySetupManager.Missing m = missing.get(i);
            int ry = p.listY() + i * ROW_H;
            if (overRect(mouseX, mouseY, p.x() + 2, ry, PANEL_W - 4, ROW_H)) {
                Render2D.drawRoundedRect(ms, p.x() + 2, ry, PANEL_W - 4, ROW_H, 3f, ROW_HOVER);
            }

            drawGhost(context, m.sample(), p.x() + PAD, ry + 1);

            String count = "×" + m.count();
            float countW = FONT.getWidth(count, SIZE_ROW);
            float nameX = p.x() + PAD + 20;
            float nameMax = p.x() + PANEL_W - PAD - countW - 4 - nameX;
            String name = fit(itemName(m.sample()), SIZE_ROW, nameMax);
            Render2D.drawFont(ms, FONT.getFont(SIZE_ROW), name, nameX, ry + textY, NAME);
            Render2D.drawFont(ms, FONT.getFont(SIZE_ROW), count, p.x() + PANEL_W - PAD - countW, ry + textY, COUNT);
        }

        if (p.hidden() > 0) {
            Render2D.drawFont(ms, FONT.getFont(SIZE_ROW), "и ещё " + p.hidden(), p.x() + PAD, p.listY() + p.rows() * ROW_H + 1f, MUTED);
        }

        int ay = p.actionY();
        boolean busy = InventorySetupManager.arranging();
        boolean done = !busy && InventorySetupManager.arranged();
        boolean hoverAction = !busy && overRect(mouseX, mouseY, p.x() + PAD, ay, PANEL_W - PAD * 2, ACTION_H);
        Render2D.drawRoundedRect(ms, p.x() + PAD, ay, PANEL_W - PAD * 2, ACTION_H, 4f,
                done ? DONE_BG : hoverAction ? ACTION_BG_HOVER : ACTION_BG);
        String label = busy ? "Раскладываю…" : done ? "Инвентарь разложен" : "Разложить всё";
        Render2D.drawFont(ms, FONT.getFont(SIZE_ROW), label,
                p.x() + PANEL_W / 2f - FONT.getWidth(label, SIZE_ROW) / 2f,
                ay + (ACTION_H - FONT.getHeight(SIZE_ROW)) / 2f, done ? MenuChrome.accent() : NAME);
    }

    /**
     * Призрачная иконка по ванильному рецепту книги рецептов. Раньше здесь стоял
     * {@code setShaderColor(0.35f, 0.35f, 0.35f, 0.35f)}: этот множитель уходит в юниформ
     * ColorModulator, который item-шейдеры умножают И на RGB, И на альфу — предмет гас дважды
     * (0.35·0.35 ≈ 0.12), а затемняющий {@code fill} поверх добивал остаток. На скриншоте от иконки
     * оставался чёрный комок.
     *
     * <p>{@link RenderLayer#getGuiGhostRecipeOverlay()} — тот самый слой, которым ваниль рисует
     * призрачные предметы в книге рецептов: depth-test GREATER и запись только цвета, поэтому
     * засветка ложится ровно на пиксели предмета (он пишет глубину на z+150), а не квадратом 16×16
     * по фону панели. Ни shaderColor, ни push/translate/pop, ни ручной флаш больше не нужны.</p>
     *
     * <p>«×N» здесь не рисуем — количество уже стоит своей колонкой справа.</p>
     */
    private static void drawGhost(DrawContext context, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        context.drawItem(stack, x, y);
        // 0x30 — ванильная сила засветки. Нужно темнее вместо бледнее — тем же слоем, напр. 0x50000000.
        context.fill(net.minecraft.client.gl.RenderPipelines.GUI, x, y, x + 16, y + 16, 0x30FFFFFF);
    }

    // ---------- клики ----------

    public static boolean mouseClicked(InventoryScreen screen, int x, int y, int bgW, double mouseX, double mouseY, int button) {
        if (button != 0 || mc.player == null) return false;

        if (buttonVisible(screen, x, bgW) && overRect(mouseX, mouseY, buttonX(x), y, BTN, BTN)) {
            mc.setScreen(new InvManagerScreen(mc.currentScreen));
            return true;
        }

        Panel p = panel(screen, x, y, bgW);
        if (p == null || !overRect(mouseX, mouseY, p.x(), p.y(), PANEL_W, p.h())) return false;

        if (overRect(mouseX, mouseY, p.x() + PAD, p.actionY(), PANEL_W - PAD * 2, ACTION_H)) {
            InventorySetupManager.arrangeAll();
            return true;
        }

        int row = (int) ((mouseY - p.listY()) / ROW_H);
        List<InventorySetupManager.Missing> missing = InventorySetupManager.missing();
        if (mouseY >= p.listY() && row >= 0 && row < p.rows() && row < missing.size()) {
            // § и управляющие символы сервер режектит в StringHelper.isValidChar и кикает игрока
            // (multiplayer.disconnect.illegal_characters), а stripObfuscatedFormatting оставляет
            // одиночный § и § перед невалидным кодом — вычищаем перед отправкой.
            String q = itemName(missing.get(row).sample()).replaceAll("[\\u00a7\\u0000-\\u001f\\u007f]", "").trim();
            if (!q.isEmpty() && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendChatCommand("ah search " + q); // ведущий "/" не нужен
            }
        }
        // любой клик внутри панели гасим: иначе HandledScreen посчитает его кликом вне окна (slotId -999)
        // и игрок выбросит предмет с курсора
        return true;
    }

    // ---------- мелочь ----------

    /**
     * ВАНИЛЬНОЕ название предмета из Minecraft. {@code stack.getName()} отдаёт серверный
     * custom_name («111 Меч Крушителя 111») — в панели он бесполезен, а в {@code /ah search}
     * по нему ничего не находится. {@code getItem().getName()} игнорирует компоненты
     * CUSTOM_NAME/ITEM_NAME и даёт локализованное имя из реестра.
     */
    private static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return Render2D.stripObfuscatedFormatting(stack.getItem().getName().getString()).trim();
    }

    private static String fit(String text, float size, float max) {
        if (text.isEmpty() || FONT.getWidth(text, size) <= max) return text;
        while (text.length() > 1 && FONT.getWidth(text + "..", size) > max) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }
}
