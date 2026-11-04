package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.input.CharInput;

import net.minecraft.client.input.KeyInput;

import net.minecraft.client.gui.Click;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;

/**
 * Общий каркас экранов настроек редизайна (9b «Настройки», 9c «под-окна»). Полностью переиспользует
 * «хром» {@link MenuChrome} мейнменю/мультиплеера — фон+виньетка, чип «Назад», лого, часы, футер-подпись —
 * плюс общую виртуальную систему координат (эталон по высоте 1080, как «Сетевая игра»). Наследники рисуют
 * только своё центральное окно в {@link #drawContent} и обрабатывают ввод в content-хуках.
 */
public abstract class LinkenSettingsScreen extends Screen implements Wrapper {

    protected final Screen parent;

    // Виртуальная система координат: 1px = 1 дизайн-единица холста 1080p.
    protected float uiScale = 1f;
    protected int vw = 1, vh = 1;
    private float parX, parY;
    /** Момент открытия экрана. Наследники берут его для своих стаггер-анимаций — своих часов не заводят. */
    protected long openMs;

    /** Длительность ухода экрана перед сменой окна, мс. */
    private static final float CLOSE_MS = 150f;
    private long closingMs;
    private Screen pendingScreen;
    private boolean pendingToParent;

    private final Animation hBack = MenuChrome.anim();
    private final float[] rBack = new float[4];

    protected LinkenSettingsScreen(Screen parent, String title) {
        super(Text.literal(title));
        this.parent = parent;
    }

    private void recomputeScale() {
        if (mc == null || mc.getWindow() == null) return;
        // HiDPI-safe: от GUI-scaled размеров (getHeight() — логический размер окна, scaleFactor — от
        // фреймбуфера; на Retina давало uiScale вдвое меньше). См. CustomMainMenuScreen.
        uiScale = Math.max(0.05f, mc.getWindow().getScaledHeight() / MenuChrome.DESIGN_HEIGHT);
        vw = Math.max(1, Math.round(mc.getWindow().getScaledWidth() / uiScale));
        vh = Math.max(1, Math.round(mc.getWindow().getScaledHeight() / uiScale));
    }

    /**
     * Экран открыт поверх игры (сервер/одиночная), а не из главного меню. По спеке 10c это тот же самый
     * экран — меняются только фон и наличие хрома, отдельной копии экрана не существует.
     */
    protected boolean inGame() {
        return mc != null && mc.world != null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ru.method.linkenvisuals.utils.render.Render2D.prepareHudFrame(context);
        // Часы появления сеем ЗДЕСЬ, а не в init(): Screen.init() вызывается ровно один раз за
        // экземпляр (флаг screenInitialized не сбрасывается), а back() возвращает ТОТ ЖЕ объект
        // родителя — при возврате из категории часы оставались от первого открытия, и анимация
        // была «уже доиграна». Обнуляются в removed(), то есть при каждом уходе с экрана.
        if (openMs == 0L) openMs = System.currentTimeMillis();
        recomputeScale();
        boolean ingame = inGame();
        // Блюр — движковый, по настройке «Размытие фона»: своим проходом дублировать нельзя.
        // Вызывается до масштабирующего push, потому что работает по фреймбуферу, а не по матрице.
        if (ingame) applyBlur(context);
        if (!ingame) MenuChrome.tickMusic();

        Matrix3x2fStack ms = context.getMatrices();
        ms.pushMatrix();
        ms.scale(uiScale, uiScale);
        int mx = Math.round(mouseX / uiScale);
        int my = Math.round(mouseY / uiScale);
        ClickGuiIcons.pixelScale(uiScale);

        if (ingame) {
            // Живой замороженный кадр мира: только затемнение (~brightness 0.62) и виньетка.
            Render2D.drawRoundedRect(ms, 0f, 0f, vw, vh, 0f, new Color(4, 7, 6, 97));
            MenuChrome.drawRadialVignette(ms, vw, vh);
        } else {
            float tpx = MathHelper.clamp((mx - vw / 2f) / (vw / 2f), -1f, 1f);
            float tpy = MathHelper.clamp((my - vh / 2f) / (vh / 2f), -1f, 1f);
            parX += (tpx - parX) * 0.08f;
            parY += (tpy - parY) * 0.08f;
            MenuChrome.drawScreenBackground(ms, vw, vh, parX, parY);

            MenuChrome.drawBackChip(ms, mx, my, hBack, rBack);
            MenuChrome.drawLogo(ms, vw);
            MenuChrome.drawClock(ms, vw);
            MenuChrome.drawFooterSignature(ms, vw, vh, false);
        }

        long now = System.currentTimeMillis();
        float openT = MathHelper.clamp((now - openMs) / (ingame ? 180f : 240f), 0f, 1f);
        // Уход экрана при переходе в другое окно: та же анимация, прокрученная назад.
        float closeT = closingMs == 0L ? 0f : MathHelper.clamp((now - closingMs) / CLOSE_MS, 0f, 1f);
        float shown = closingMs == 0L ? openT : 1f - closeT;

        ms.pushMatrix();
        if (ingame) {
            // Появление в игре: scale 0.98→1 от центра холста (спека 10c).
            float scale = 0.98f + 0.02f * (1f - (1f - shown) * (1f - shown));
            ms.translate(vw / 2f, vh / 2f);
            ms.scale(scale, scale);
            ms.translate(-vw / 2f, -vh / 2f);
        } else {
            // В меню — слайд снизу 18px + easeOutCubic, как в «Сетевой игре».
            float openE = 1f - (1f - shown) * (1f - shown) * (1f - shown);
            ms.translate(0f, (1f - openE) * 18f);
        }
        drawContent(context, ms, mx, my);
        ms.popMatrix();

        if (closingMs != 0L && closeT >= 1f) commitTransition();

        if (ingame) drawEscHint(ms);

        ms.popMatrix();
        ClickGuiIcons.pixelScale(1f);
    }

    /** Подпись ESC-чипа; меню паузы переопределяет на «вернуться к игре». */
    protected String escHintLabel() {
        return "закрыть";
    }

    /** Чип «[ESC] …» внизу по центру — заменяет отсутствующий в игре хром меню. */
    private void drawEscHint(Matrix3x2fStack ms) {
        Instance kf = Fonts.PLEXMONO.getFont(8.5f);
        Instance lf = Fonts.GOLOS.getFont(11f);
        String label = escHintLabel();
        float capW = kf.getWidth("ESC") + 12f, capH = 15f;
        float h = 26f;
        float w = 10f + capW + 8f + lf.getWidth(label) + 12f;
        float x = vw / 2f - w / 2f, y = vh - 40f - h;

        Render2D.drawRoundedRect(ms, x, y, w, h, 8f, new Color(5, 8, 7, 140));
        Render2D.drawBorder(ms, x, y, w, h, 8f, 1.0f, 1.2f, MenuChrome.white(0.07f));

        float capX = x + 10f, capY = y + (h - capH) / 2f;
        Render2D.drawBorder(ms, capX, capY, capW, capH, 4f, 1.0f, 1.2f, MenuChrome.white(0.14f));
        Render2D.drawFont(ms, kf, "ESC", capX + 6f, capY + capH / 2f - kf.getHeight() / 2f, MenuChrome.accent());
        Render2D.drawFont(ms, lf, label, capX + capW + 8f, y + h / 2f - lf.getHeight() / 2f, MenuChrome.TEXT3);
    }

    /** Наследник рисует центральное окно (в дизайн-координатах). */
    protected abstract void drawContent(DrawContext context, Matrix3x2fStack ms, int mx, int my);

    /** Центрирование окна по ширине холста. */
    protected float winX(float winW) {
        return vw / 2f - winW / 2f;
    }

    // ---------- Ввод (в дизайн-координатах) ----------

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (closing()) return true; // экран уже уезжает — клики не принимаем
        float mx = (float) (mouseX / uiScale), my = (float) (mouseY / uiScale);
        // Наследник получает клик ПЕРВЫМ, включая площадь чипа «Назад»: экран записи бинда обязан
        // поглотить любую кнопку мыши (как ванильный KeybindsScreen), иначе промах по кейкапу
        // закрывает экран и запись теряется молча.
        if (contentClick(mx, my, button)) return true;
        if (button == 0 && MenuChrome.in(rBack, mx, my)) { back(); return true; }
        return super.mouseClicked(click, doubleClick);
    }

    protected boolean contentClick(float mx, float my, int button) { return false; }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        contentRelease((float) (mouseX / uiScale), (float) (mouseY / uiScale), button);
        return super.mouseReleased(click);
    }

    protected void contentRelease(float mx, float my, int button) { }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float mx = (float) (mouseX / uiScale), my = (float) (mouseY / uiScale);
        if (contentScroll(mx, my, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    protected boolean contentScroll(float mx, float my, double vertical) { return false; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (closing()) return true;
        // Наследник получает клавишу ПЕРВЫМ, включая ESC: экрану записи бинда нужно перехватить ESC
        // как «отмена записи», а не как «выйти из окна».
        if (contentKey(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { back(); return true; }
        return super.keyPressed(input);
    }

    protected boolean contentKey(int keyCode, int scanCode, int modifiers) { return false; }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (contentChar(chr, modifiers)) return true;
        return super.charTyped(input);
    }

    protected boolean contentChar(char chr, int modifiers) { return false; }

    protected void back() {
        // Настройки применяются сразу в GameOptions, поэтому «назад» обязан их записать — иначе
        // выход по ESC терял всё, что изменили в под-окне.
        mc.options.write();
        transitionTo(parent, true);
    }

    /**
     * Уход в другое окно с анимацией: экран доигрывает своё появление задом наперёд и только потом
     * подменяется. Без этого смена окна была мгновенной склейкой двух кадров.
     */
    protected void transitionTo(Screen next) {
        transitionTo(next, false);
    }

    private void transitionTo(Screen next, boolean toParent) {
        if (closingMs != 0L) return; // переход уже идёт — второй клик игнорируем
        pendingScreen = next;
        pendingToParent = toParent;
        closingMs = System.currentTimeMillis();
    }

    private void commitTransition() {
        Screen next = pendingScreen;
        boolean toParent = pendingToParent;
        closingMs = 0L;
        pendingScreen = null;
        // Без слияния типов Screen в тернарнике — см. VerifyError-заметку в CustomMultiplayerScreen.back().
        if (next != null) {
            MenuChrome.keepMusicAcrossSwap();
            mc.setScreen(next);
            return;
        }
        if (toParent && inGame()) {
            // Из хаба, открытого в игре, ESC/«Готово» возвращают в игру, а не в главное меню.
            mc.setScreen(null);
            return;
        }
        MenuChrome.keepMusicAcrossSwap();
        mc.setScreen(new ru.method.linkenvisuals.screen.mainmenu.CustomMainMenuScreen());
    }

    /** Идёт ли анимация ухода — на это время ввод замораживаем. */
    protected boolean closing() {
        return closingMs != 0L;
    }

    /** Переход на экран клавиш из строки-действия каталога — с той же анимацией ухода. */
    public void openKeybinds() {
        transitionTo(new KeybindsListScreen(this));
    }

    @Override
    public void removed() {
        openMs = 0L; // уход с экрана перезаряжает появление — init() при возврате не вызовется
        MenuChrome.onMenuScreenRemoved();
        super.removed();
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
}
