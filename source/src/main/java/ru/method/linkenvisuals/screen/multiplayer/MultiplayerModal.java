package ru.method.linkenvisuals.screen.multiplayer;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Базовая модалка экрана «Сетевая игра»: окно в материале спеки поверх затемнённого экрана,
 * заголовок, текстовые поля (материал поиска) и пара кнопок «Отмена» / primary-акцент.
 */
abstract class MultiplayerModal extends Screen implements Wrapper {

    protected static final float FIELD_H = 36f;
    protected static final float SEG_H = 34f;
    private static final float PAD = 22f;

    protected final CustomMultiplayerScreen owner;
    private final String title;
    private final String primaryLabel;
    private final boolean primaryDanger;

    protected final List<Field> fields = new ArrayList<>();
    protected final List<Choice> choices = new ArrayList<>();
    private int focused = -1;

    private final Animation hPrimary = MenuChrome.anim(), hCancel = MenuChrome.anim();
    private final float[] rPrimary = new float[4], rCancel = new float[4];

    private float uiScale = 1f;
    private int vw = 1, vh = 1;
    private long openMs; // плавное появление модалки (слайд+изинг), как у основного окна

    /** Ужать текст в одну строку по ширине с многоточием (длинные описания/имена не вылазят за окно). */
    private static String fitEllipsis(Instance font, String text, float maxW) {
        if (text == null || text.isEmpty() || font.getWidth(text) <= maxW) return text;
        float ellW = font.getWidth("…");
        int end = text.length();
        while (end > 0 && font.getWidth(text.substring(0, end)) + ellW > maxW) end--;
        return text.substring(0, end) + "…";
    }

    /** Сегментный переключатель «как в майне»: подпись + ряд из нескольких взаимоисключающих кнопок. */
    protected static final class Choice {
        final String label;
        final String[] options;
        int selected;
        final float[][] segRects;
        final Animation[] hover;

        Choice(String label, int selected, String... options) {
            this.label = label;
            this.options = options;
            this.selected = MathHelper.clamp(selected, 0, options.length - 1);
            this.segRects = new float[options.length][4];
            this.hover = new Animation[options.length];
            for (int i = 0; i < hover.length; i++) hover[i] = MenuChrome.anim();
        }
    }

    protected static final class Field {
        final String label;
        final String placeholder;
        String value;
        final float[] rect = new float[4];
        final Animation hover = MenuChrome.anim();
        // Анимация ввода: время последней правки и была ли она вставкой символа (для «въезда» последнего глифа).
        long lastEditMs;
        boolean lastEditInsert;

        // Позиция каретки (0..value.length) — стрелки/Home/End/Delete и вставка в середину текста.
        int caret;
        // Ctrl+A: выделено всё (упрощённая модель выделения — только select-all).
        boolean allSelected;

        Field(String label, String placeholder, String value) {
            this.label = label;
            this.placeholder = placeholder;
            this.value = value == null ? "" : value;
            this.caret = this.value.length();
        }

        void markEdit(boolean insert) {
            this.lastEditMs = System.currentTimeMillis();
            this.lastEditInsert = insert;
        }

        void insert(String text) {
            if (text == null || text.isEmpty()) return;
            if (allSelected) {
                value = "";
                caret = 0;
                allSelected = false;
            }
            caret = MathHelper.clamp(caret, 0, value.length());
            value = value.substring(0, caret) + text + value.substring(caret);
            caret += text.length();
            markEdit(true);
        }

        void deleteBack() {
            if (allSelected) {
                clearAll();
                return;
            }
            caret = MathHelper.clamp(caret, 0, value.length());
            if (caret > 0) {
                value = value.substring(0, caret - 1) + value.substring(caret);
                caret--;
            }
            markEdit(false);
        }

        void deleteForward() {
            if (allSelected) {
                clearAll();
                return;
            }
            caret = MathHelper.clamp(caret, 0, value.length());
            if (caret < value.length()) {
                value = value.substring(0, caret) + value.substring(caret + 1);
            }
            markEdit(false);
        }

        void clearAll() {
            value = "";
            caret = 0;
            allSelected = false;
            markEdit(false);
        }
    }

    protected MultiplayerModal(CustomMultiplayerScreen owner, String title, String primaryLabel, boolean primaryDanger) {
        super(Text.literal(title));
        this.owner = owner;
        this.title = title;
        this.primaryLabel = primaryLabel;
        this.primaryDanger = primaryDanger;
    }

    /** Текст под заголовком (или null). */
    protected String description() { return null; }

    /** Нажата акцентная кнопка. Вернуть true, чтобы закрыть модалку. */
    protected abstract boolean onPrimary();

    protected float modalWidth() { return 460f; }

    private float modalHeight() {
        float h = PAD + Fonts.GOLOS_SEMI.getFont(17f).getHeight() + 18f;
        if (description() != null) h += Fonts.GOLOS.getFont(12.5f).getHeight() + 14f;
        for (Field ignored : fields) h += Fonts.GOLOS.getFont(11f).getHeight() + 6f + FIELD_H + 14f;
        for (Choice ignored : choices) h += Fonts.GOLOS.getFont(11f).getHeight() + 6f + SEG_H + 14f;
        return h + 44f + PAD;
    }

    private void recomputeScale() {
        if (mc == null || mc.getWindow() == null) return;
        // HiDPI-safe: от GUI-scaled размеров (getHeight() — логический размер окна, scaleFactor — от
        // фреймбуфера; на Retina давало uiScale вдвое меньше). См. CustomMainMenuScreen.
        uiScale = Math.max(0.05f, mc.getWindow().getScaledHeight() / MenuChrome.DESIGN_HEIGHT);
        vw = Math.max(1, Math.round(mc.getWindow().getScaledWidth() / uiScale));
        vh = Math.max(1, Math.round(mc.getWindow().getScaledHeight() / uiScale));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        recomputeScale();
        MenuChrome.tickMusic();

        Matrix3x2fStack ms = context.getMatrices();
        ms.pushMatrix();
        ms.scale(uiScale, uiScale);
        int mx = Math.round(mouseX / uiScale);
        int my = Math.round(mouseY / uiScale);

        MenuChrome.drawScreenBackground(ms, vw, vh, 0f, 0f);
        Render2D.drawRoundedRect(ms, 0, 0, vw, vh, 0f, new Color(4, 7, 6, 140)); // затемнение под модалкой

        // Плавное появление модалки: слайд снизу 14px + easeOutCubic (200 мс).
        if (openMs == 0L) openMs = System.currentTimeMillis();
        float openT = MathHelper.clamp((System.currentTimeMillis() - openMs) / 200f, 0f, 1f);
        float openE = 1f - (1f - openT) * (1f - openT) * (1f - openT);
        ms.translate(0f, (1f - openE) * 14f);

        float w = modalWidth(), h = modalHeight();
        float x = vw / 2f - w / 2f, y = vh / 2f - h / 2f;
        WindowMaterial.window(ms, x, y, w, h);

        float cy = y + PAD;
        Instance tf = Fonts.GOLOS_SEMI.getFont(17f);
        Render2D.drawFont(ms, tf, title, x + PAD, cy, MenuChrome.TEXT);
        cy += tf.getHeight() + 18f;

        String desc = description();
        if (desc != null) {
            Instance df = Fonts.GOLOS.getFont(12.5f);
            // Длинное описание (напр. огромное имя сервера в «Удалить сервер?») не должно вылезать за окно —
            // ужимаем в одну строку по ширине с многоточием.
            Render2D.drawFont(ms, df, fitEllipsis(df, desc, w - PAD * 2f), x + PAD, cy, MenuChrome.MUTED2);
            cy += df.getHeight() + 14f;
        }

        Instance lf = Fonts.GOLOS.getFont(11f);
        Instance vf = Fonts.GOLOS.getFont(12.5f);
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            Render2D.drawFont(ms, lf, f.label, x + PAD, cy, MenuChrome.MUTED3);
            cy += lf.getHeight() + 6f;
            float fw = w - PAD * 2f;
            boolean hov = MenuChrome.hit(mx, my, x + PAD, cy, fw, FIELD_H);
            float t = MenuChrome.upd(f.hover, hov || focused == i);
            WindowMaterial.fieldBox(ms, x + PAD, cy, fw, FIELD_H, t, false);
            // Прогресс «свежести» правки: 0 сразу после нажатия → 1 через 150 мс (для въезда глифа и пульса).
            float editT = MathHelper.clamp((System.currentTimeMillis() - f.lastEditMs) / 150f, 0f, 1f);
            float editPulse = focused == i ? (1f - editT) : 0f;
            // Активное поле — только тонкая акцентная рамка фокуса. Свечение (ореол) при печати убрано
            // по запросу; пульс рамки тоже убран, чтобы поле не «светилось» при вводе.
            if (focused == i) {
                Render2D.drawBorder(ms, x + PAD, cy, fw, FIELD_H, 7f, 1.0f, 1.4f, MenuChrome.accentA(0.42f * t));
            }
            float tx = x + PAD + 13f, tcy = cy + FIELD_H / 2f - vf.getHeight() / 2f;
            if (f.value.isEmpty() && focused != i) {
                Render2D.drawFont(ms, vf, f.placeholder, tx, tcy, MenuChrome.MUTED3);
            } else {
                // Окно видимого текста: длинное значение не должно вылезать за поле (и за окно). Показываем
                // часть, которая содержит каретку и влезает в ширину поля — при печати в конец это «хвост».
                float innerW = fw - 26f;
                int caretPos = MathHelper.clamp(f.caret, 0, f.value.length());
                int vstart = 0;
                while (vstart < caretPos && vf.getWidth(f.value.substring(vstart, caretPos)) > innerW) vstart++;
                String shown = f.value.substring(vstart);
                while (!shown.isEmpty() && vf.getWidth(shown) > innerW) shown = shown.substring(0, shown.length() - 1);

                // Подсветка Ctrl+A — акцентная плашка под видимой частью текста.
                if (focused == i && f.allSelected && !shown.isEmpty()) {
                    Render2D.drawRoundedRect(ms, tx - 2f, cy + FIELD_H / 2f - 7.5f,
                            vf.getWidth(shown) + 4f, 15f, 3f, MenuChrome.accentA(0.28f));
                }
                boolean animIn = f.lastEditInsert && editT < 1f && !shown.isEmpty()
                        && f.caret == f.value.length();
                if (animIn) {
                    // Последний введённый символ «въезжает» снизу с нарастанием прозрачности; остальное статично.
                    String head = shown.substring(0, shown.length() - 1);
                    String tail = shown.substring(shown.length() - 1);
                    Render2D.drawFont(ms, vf, head, tx, tcy, MenuChrome.TEXT2);
                    float ease = 1f - (1f - editT) * (1f - editT); // easeOutQuad
                    int ca = Math.round(ease * 255f);
                    Color tailCol = new Color(MenuChrome.TEXT2.getRed(), MenuChrome.TEXT2.getGreen(), MenuChrome.TEXT2.getBlue(), ca);
                    Render2D.drawFont(ms, vf, tail, tx + vf.getWidth(head), tcy + (1f - ease) * 5f, tailCol);
                } else {
                    Render2D.drawFont(ms, vf, shown, tx, tcy, MenuChrome.TEXT2);
                }
                if (focused == i) {
                    // Плавный пульс курсора (акцент); сразу после ввода — короткая яркая вспышка (editPulse).
                    float pulse = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 210.0));
                    pulse = Math.min(1f, pulse + 0.5f * editPulse);
                    Render2D.drawRoundedRect(ms, tx + vf.getWidth(f.value.substring(vstart, caretPos)) + 1.5f,
                            cy + FIELD_H / 2f - 6f, 1.5f, 12f, 0.75f, MenuChrome.accentA(pulse));
                }
            }
            MenuChrome.set(f.rect, x + PAD, cy, fw, FIELD_H);
            cy += FIELD_H + 14f;
        }

        Instance sf = Fonts.GOLOS.getFont(12f);
        for (Choice c : choices) {
            Render2D.drawFont(ms, lf, c.label, x + PAD, cy, MenuChrome.MUTED3);
            cy += lf.getHeight() + 6f;
            float fw = w - PAD * 2f;
            int n = c.options.length;
            float gap = 6f;
            float segW = (fw - gap * (n - 1)) / n;
            for (int j = 0; j < n; j++) {
                float sx = x + PAD + j * (segW + gap);
                boolean on = c.selected == j;
                boolean segHov = MenuChrome.hit(mx, my, sx, cy, segW, SEG_H);
                float st = MenuChrome.upd(c.hover[j], segHov && !on);
                if (on) {
                    Color acc = MenuChrome.accent();
                    Render2D.drawRoundedRect(ms, sx, cy, segW, SEG_H, 7f, acc);
                    Render2D.drawRoundedRect(ms, sx + 7f, cy + 1f, segW - 14f, 1f, 0.5f, MenuChrome.white(0.35f));
                } else {
                    Render2D.drawRoundedRect(ms, sx, cy, segW, SEG_H, 7f, MenuChrome.white(0.03f + 0.03f * st));
                    Render2D.drawBorder(ms, sx, cy, segW, SEG_H, 7f, 1.0f, 1.4f,
                            MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.22f), st));
                }
                String opt = c.options[j];
                Color tc = on ? MenuChrome.INK : MenuChrome.lerp(MenuChrome.MUTED2, MenuChrome.TEXT2, st);
                Render2D.drawFont(ms, sf, opt, sx + (segW - sf.getWidth(opt)) / 2f, cy + SEG_H / 2f - sf.getHeight() / 2f, tc);
                MenuChrome.set(c.segRects[j], sx, cy, segW, SEG_H);
            }
            cy += SEG_H + 14f;
        }

        // Кнопки: «Отмена» + primary, справа снизу.
        Instance bf = Fonts.UNBOUNDED.getFont(11f);
        float bh = 44f, by = y + h - PAD - bh;
        float pw = 24f + bf.getWidth(primaryLabel) + 24f;
        float px = x + w - PAD - pw;
        boolean pHov = MenuChrome.hit(mx, my, px, by, pw, bh);
        float pT = MenuChrome.upd(hPrimary, pHov);
        drawPrimary(ms, px, by, pw, bh, pT, bf);
        MenuChrome.set(rPrimary, px, by, pw, bh);

        String cancel = "Отмена";
        float cw = 18f + bf.getWidth(cancel) + 18f;
        float cx2 = px - 9f - cw;
        boolean cHov = MenuChrome.hit(mx, my, cx2, by, cw, bh);
        float cT = MenuChrome.upd(hCancel, cHov);
        Render2D.drawRoundedRect(ms, cx2, by, cw, bh, 8f, MenuChrome.white(0.04f));
        Render2D.drawBorder(ms, cx2, by, cw, bh, 8f, 1.0f, 1.4f,
                MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.24f), cT));
        Render2D.drawFont(ms, bf, cancel, cx2 + 18f, by + bh / 2f - bf.getHeight() / 2f, MenuChrome.TEXT2);
        MenuChrome.set(rCancel, cx2, by, cw, bh);

        WindowMaterial.corners(ms, x, y, w, h);
        ms.popMatrix();
    }

    private void drawPrimary(Matrix3x2fStack ms, float x, float y, float w, float h, float t, Instance f) {
        float ty = y - t;
        Color base = primaryDanger ? MenuChrome.DANGER : MenuChrome.accent();
        Color glow = new Color(base.getRed(), base.getGreen(), base.getBlue(), 90);
        MenuChrome.glow(ms, x, ty, w, h, 8f, glow, 4f, 5, 2.4f);
        int br = Math.min(255, Math.round(base.getRed() * (1f + 0.07f * t)));
        int bg = Math.min(255, Math.round(base.getGreen() * (1f + 0.07f * t)));
        int bb = Math.min(255, Math.round(base.getBlue() * (1f + 0.07f * t)));
        Render2D.drawRoundedRect(ms, x, ty, w, h, 8f, new Color(br, bg, bb));
        Render2D.drawRoundedRect(ms, x + 8f, ty + 1f, w - 16f, 1f, 0.5f, MenuChrome.white(0.35f));
        Render2D.drawFont(ms, f, primaryLabel, x + (w - f.getWidth(primaryLabel)) / 2f, ty + h / 2f - f.getHeight() / 2f, MenuChrome.INK);
    }

    /** Вставка из буфера в поле (многострочное схлопывается — поле однострочное). */
    private void pasteInto(Field f) {
        String clip = mc.keyboard.getClipboard();
        if (clip != null) {
            f.insert(clip.replace("\r", "").replace("\n", " ").trim());
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button == 1) {
            // ПКМ по полю — вставка из буфера (привычный путь в лаунчерах/клиентах).
            float rmx = (float) (mouseX / uiScale), rmy = (float) (mouseY / uiScale);
            for (int i = 0; i < fields.size(); i++) {
                if (MenuChrome.in(fields.get(i).rect, rmx, rmy)) {
                    focused = i;
                    pasteInto(fields.get(i));
                    return true;
                }
            }
        }
        if (button != 0) return super.mouseClicked(click, doubleClick);
        float mx = (float) (mouseX / uiScale), my = (float) (mouseY / uiScale);
        if (MenuChrome.in(rCancel, mx, my)) { dismiss(); return true; }
        if (MenuChrome.in(rPrimary, mx, my)) { if (onPrimary()) dismiss(); return true; }
        focused = -1;
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).allSelected = false;
            if (MenuChrome.in(fields.get(i).rect, mx, my)) { focused = i; }
        }
        for (Choice c : choices) {
            for (int j = 0; j < c.options.length; j++) {
                if (MenuChrome.in(c.segRects[j], mx, my)) { c.selected = j; break; }
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { dismiss(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (onPrimary()) dismiss();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && !fields.isEmpty()) {
            focused = (focused + 1) % fields.size();
            return true;
        }
        if (focused >= 0) {
            Field f = fields.get(focused);
            // Ванильные предикаты (isPaste/isCopy/isCut) — как в TextFieldWidget: живой опрос Ctrl/Cmd,
            // а не бит modifiers. Плюс классический Shift+Insert.
            boolean shiftInsert = keyCode == GLFW.GLFW_KEY_INSERT && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            net.minecraft.client.input.MouseInput clip = new net.minecraft.client.input.MouseInput(keyCode, modifiers);
            if (clip.isPaste() || shiftInsert) {
                pasteInto(f);
                return true;
            }
            if (clip.isCopy()) {
                if (!f.value.isEmpty()) mc.keyboard.setClipboard(f.value);
                return true;
            }
            if (clip.isCut()) {
                if (!f.value.isEmpty()) mc.keyboard.setClipboard(f.value);
                f.clearAll();
                return true;
            }
            if (clip.isSelectAll()) {
                f.allSelected = !f.value.isEmpty();
                f.caret = f.value.length();
                return true;
            }
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> { f.deleteBack(); return true; }
                case GLFW.GLFW_KEY_DELETE -> { f.deleteForward(); return true; }
                case GLFW.GLFW_KEY_LEFT -> { f.allSelected = false; f.caret = Math.max(0, f.caret - 1); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { f.allSelected = false; f.caret = Math.min(f.value.length(), f.caret + 1); return true; }
                case GLFW.GLFW_KEY_HOME -> { f.allSelected = false; f.caret = 0; return true; }
                case GLFW.GLFW_KEY_END -> { f.allSelected = false; f.caret = f.value.length(); return true; }
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (focused >= 0 && chr >= ' ') {
            fields.get(focused).insert(String.valueOf(chr));
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    protected void init() {
        // Авто-фокус первого поля — сразу можно печатать (иначе charTyped игнорится: focused == -1,
        // отсюда «текст не сохраняется»). На resize фокус не сбрасываем.
        if (focused < 0 && !fields.isEmpty()) focused = 0;
    }

    protected void dismiss() {
        MenuChrome.keepMusicAcrossSwap();
        mc.setScreen(owner);
    }

    @Override public void removed() { MenuChrome.onMenuScreenRemoved(); super.removed(); }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
}
