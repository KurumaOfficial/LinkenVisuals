package ru.method.linkenvisuals.screen.hud;

import net.minecraft.client.gui.Click;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.managers.system.HudManager;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.client.Elements;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Прозрачный экран-редактор расположения HUD (модуль {@link Elements}). Курсор свободен, мир и HUD
 * видны за экраном, элементы перетаскиваются мышью. Снизу — строка чипов всех HUD-элементов:
 * клик по чипу показывает/скрывает элемент (добавить/убрать).
 */
public class HudEditScreen extends Screen {

    // Прямоугольник строки чипов (для защиты перетаскивания HUD за ней).
    private static float panelX, panelY, panelW, panelH;

    private final Animation openAnim = new Animation(220, 1f, true, Easing.EASE_OUT_CUBIC);
    private final List<float[]> chipBounds = new ArrayList<>();
    private final List<HudElement> chipElements = new ArrayList<>();
    // Плавная анимация загорания/гашения зелёного огонька (и яркости текста) на каждый элемент.
    private final Map<String, Animation> dotAnim = new HashMap<>();
    // Фиксированный порядок чипов в строке. hudElements мутируется focusElement() (z-order при перетаскивании),
    // поэтому НЕ берём порядок оттуда — иначе чипы прыгают местами. Здесь — стабильный порядок отображения.
    private static final List<String> CHIP_ORDER = List.of(
            "TargetHud", "PotionHud", "CoordinatesHud", "CooldownsHud", "KeyBindsHud",
            "HelperItemsHud", "ArmorHud", "InventoryHud", "ScoreboardHud", "LowArmorNotificationsHud",
            "Watermark", "DynamicIsland");
    private boolean closing;
    private boolean finished;

    public HudEditScreen() {
        super(Text.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        openAnim.reset();
    }

    /** Активен ли режим редактирования HUD (чат или этот экран) — общая проверка для drag/сетки/меню. */
    public static boolean editingActive() {
        Screen s = MinecraftClient.getInstance().currentScreen;
        return s instanceof ChatScreen || s instanceof HudEditScreen;
    }

    /** Курсор над строкой чипов — тогда HUD-элемент за ней не должен перетаскиваться. */
    public static boolean overPanel(double mouseX, double mouseY) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof HudEditScreen)) return false;
        return mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private float openProgress() {
        return Math.max(0f, Math.min(1f, openAnim.getValue()));
    }

    /** Отображаемое имя элемента в списке (с нужными переименованиями). */
    private String displayName(HudManager hm, HudElement el) {
        switch (el.getName()) {
            case "ScoreboardHud": return "Скорборд";
            case "Watermark": return "Вотермарк";
            default: break;
        }
        BooleanSetting setting = hm.getElementSetting(el);
        if (setting != null) {
            String key = setting.getName();
            String tr = I18n.translate(key);
            if (!key.equals(tr)) return tr;
        }
        return el.getName();
    }

    /** Компактное имя элемента для строки-селектора (чтобы строка была короче). */
    private String shortName(HudManager hm, HudElement el) {
        switch (el.getName()) {
            case "TargetHud": return "Таргет";
            case "PotionHud": return "Эффекты";
            case "CoordinatesHud": return "Коорды";
            case "CooldownsHud": return "Задержка";
            case "KeyBindsHud": return "Клавиши";
            case "HelperItemsHud": return "Хелпер";
            case "ArmorHud": return "Броня";
            case "InventoryHud": return "Инвентарь";
            case "LowArmorNotificationsHud": return "Увед. брони";
            case "Watermark": return "Вотермарк";
            case "ScoreboardHud": return "Скорборд";
            default: return displayName(hm, el);
        }
    }

    /** Линейная интерполяция двух цветов (для плавного загорания индикатора). */
    private static Color mix(Color a, Color b, float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * c),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * c),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * c));
    }

    /** Показывать ли элемент в строке выбора. */
    private boolean listed(HudElement el) {
        String n = el.getName();
        // «Динамик. остров» в Link-стиле скрыт — убираем и из списка.
        if (LinkHudStyle.active() && n.equals("DynamicIsland")) return false;
        // Скорборд — только если включён «Кастом скорборд».
        if (n.equals("ScoreboardHud") && !UI.isCustomScoreboardEnabled()) return false;
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        int a = Math.round(51f * openProgress());
        context.fill(0, 0, this.width, this.height, a << 24);
    }

    /** Запустить плавное закрытие (анимация наружу), реальное закрытие — в finishClose(). */
    public void startClosing() {
        closing = true;
    }

    private void finishClose() {
        if (finished) return;
        finished = true;
        Module module = LinkenVisuals.getInstance().getModuleManager().getModule(Elements.class);
        if (module != null && module.isToggle()) {
            LinkenVisuals.getInstance().setSuppressToggleFeedback(true);
            try {
                module.setToggled(false);
            } finally {
                LinkenVisuals.getInstance().setSuppressToggleFeedback(false);
            }
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == this) {
            mc.setScreen(null);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ru.method.linkenvisuals.utils.render.Render2D.prepareHudFrame(context);
        openAnim.update(!closing);
        if (closing && openProgress() <= 0.02f) {
            finishClose();
            return;
        }
        super.render(context, mouseX, mouseY, delta);

        float p = openProgress();
        boolean link = LinkHudStyle.active();

        // --- Подсказка сверху ---
        String hint = "Перетаскивайте HUD-элементы   •   Esc — выход";
        float hintSize = 8f;
        float hintW = Fonts.ROUNDBOLD.getWidth(hint, hintSize) + 22f;
        float hbh = 18f;
        float hbx = (this.width - hintW) / 2f;
        float hby = 8f - (1f - p) * 8f;
        if (link) {
            LinkHudStyle.panel(context.getMatrices(), hbx, hby, hintW, hbh, LinkHudStyle.radius(hintW, hbh), p);
        }
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(hintSize), hint,
                hbx + 11f, hby + (hbh - Fonts.ROUNDBOLD.getHeight(hintSize)) / 2f,
                link ? LinkHudStyle.text(Math.round(255 * p)) : new Color(255, 255, 255, Math.round(255 * p)));

        // --- Единая строка выбора элементов (внизу). Порядок стабилен: при выкл элементы
        //     НЕ меняются местами, а гаснут анимацией на месте. ---
        chipBounds.clear();
        chipElements.clear();
        HudManager hm = LinkenVisuals.getInstance().getHudManager();
        if (hm == null) return;

        List<HudElement> shown = new ArrayList<>();
        for (HudElement el : hm.getHudElements()) {
            if (listed(el)) shown.add(el);
        }
        if (shown.isEmpty()) return;
        // Стабильный порядок (не зависит от z-order hudElements) — чипы не прыгают местами.
        shown.sort((a, b) -> Integer.compare(orderIndex(a), orderIndex(b)));

        int n = shown.size();
        float chipH = 17f;
        float dotD = 4.5f;
        float dotGap = 4.5f;
        float segPad = 8f;
        float fsize = 6.8f;

        // Ширины текста и общая ширина строки; при переполнении ужимаем всё пропорционально в одну строку.
        float[] textW = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            textW[i] = Fonts.MEDIUM.getWidth(shortName(hm, shown.get(i)), fsize);
            total += dotD + dotGap + textW[i] + segPad * 2f;
        }
        float avail = this.width - 60f;
        if (total > avail) {
            float k = avail / total;
            fsize *= k; segPad *= k; dotGap *= k; dotD *= k;
            total = 0f;
            for (int i = 0; i < n; i++) {
                textW[i] = Fonts.MEDIUM.getWidth(shortName(hm, shown.get(i)), fsize);
                total += dotD + dotGap + textW[i] + segPad * 2f;
            }
        }

        float bottomMargin = 50f;
        float stripX = (this.width - total) / 2f;
        float stripY = this.height - bottomMargin - chipH + (1f - p) * 8f;
        float cr = LinkHudStyle.radius(total, chipH);

        // Подпись над строкой.
        String caption = "Элементы";
        float capSize = 7f;
        float capW = Fonts.ROUNDBOLD.getWidth(caption, capSize);
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(capSize), caption,
                (this.width - capW) / 2f, stripY - capSize - 6f,
                link ? LinkHudStyle.text(Math.round(190 * p)) : new Color(210, 214, 222, Math.round(190 * p)));

        // Мягкая тень-подложка под строкой — даёт глубину.
        for (int s = 3; s >= 1; s--) {
            float gg = s * 2f;
            Render2D.drawRoundedRect(context.getMatrices(), stripX - gg, stripY - gg + 1.5f, total + gg * 2f, chipH + gg * 2f,
                    cr + gg, new Color(0, 0, 0, Math.round(18f * p / s)));
        }

        // Единый фон-строка (одна целая «строка», а не куча кнопок) + рамка.
        Color accent = ColorUtils.getPrimaryColor();
        if (link) {
            LinkHudStyle.panel(context.getMatrices(), stripX, stripY, total, chipH, cr, p);
        } else {
            Render2D.drawRoundedRect(context.getMatrices(), stripX, stripY, total, chipH, cr,
                    ColorUtils.getGlassColor(Math.round(175 * p)));
        }
        Render2D.drawBorder(context.getMatrices(), stripX, stripY, total, chipH, cr, 1f,
                new ColorRGBA(new Color(255, 255, 255, Math.round(45 * p))));

        Color offC = new Color(140, 146, 158);
        Color onC = new Color(76, 217, 124); // мягкий зелёный
        float sx = stripX;
        for (int i = 0; i < n; i++) {
            HudElement el = shown.get(i);
            boolean vis = hm.isElementVisible(el);
            float segWidth = dotD + dotGap + textW[i] + segPad * 2f;
            boolean hov = MathUtils.isHovered(sx, stripY, segWidth, chipH, mouseX, mouseY);

            // Плавное загорание/гашение (анимация на месте, с easing).
            Animation anim = dotAnim.computeIfAbsent(el.getName(),
                    key -> new Animation(320, 1f, vis, Easing.EASE_OUT_CUBIC));
            anim.update(vis);
            float fill = Math.max(0f, Math.min(1f, anim.getValue()));

            // Разделитель между сегментами.
            if (i > 0) {
                Render2D.drawRoundedRect(context.getMatrices(), sx, stripY + chipH * 0.26f, 1f, chipH * 0.48f, 0.5f,
                        new Color(255, 255, 255, Math.round(24 * p)));
            }
            // Подсветка активного/наведённого сегмента — мягкая акцентная пилюля, плавно появляется.
            float hl = Math.max(fill, hov ? 0.55f : 0f);
            if (hl > 0.01f) {
                float pH = chipH - 5f;
                Render2D.drawRoundedRect(context.getMatrices(), sx + 2.5f, stripY + 2.5f, segWidth - 5f, pH, pH / 2f,
                        ColorUtils.alpha(accent, Math.round(42 * hl * p)));
            }

            // Контент сегмента (огонёк + текст) отцентрирован по сегменту.
            float contentW = dotD + dotGap + textW[i];
            float cx0 = sx + (segWidth - contentW) / 2f;
            float dotCx = cx0 + dotD / 2f;
            float dotCy = stripY + chipH / 2f;

            // Зелёный огонёк с мягким свечением (край не «пиксельный»).
            if (fill > 0.01f) {
                for (int g = 3; g >= 1; g--) {
                    float gd = dotD + g * 2.6f;
                    Render2D.drawRoundedRect(context.getMatrices(), dotCx - gd / 2f, dotCy - gd / 2f, gd, gd, gd / 2f,
                            new Color(onC.getRed(), onC.getGreen(), onC.getBlue(), Math.round(34f * fill / g * p)));
                }
            }
            Color dotC = mix(offC, onC, fill);
            Render2D.drawRoundedRect(context.getMatrices(), dotCx - dotD / 2f, dotCy - dotD / 2f, dotD, dotD, dotD / 2f,
                    new Color(dotC.getRed(), dotC.getGreen(), dotC.getBlue(), Math.round(238 * p)));

            // Текст: яркость плавно следует за анимацией (гаснет при выключении).
            int tA = Math.round((160 + 88 * fill) * p);
            Color tc = link ? LinkHudStyle.text(tA) : new Color(255, 255, 255, tA);
            Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(fsize), shortName(hm, el),
                    cx0 + dotD + dotGap, stripY + (chipH - Fonts.MEDIUM.getHeight(fsize)) / 2f, tc);

            chipBounds.add(new float[]{sx, stripY, segWidth, chipH});
            chipElements.add(el);
            sx += segWidth;
        }

        panelX = stripX - 4f;
        panelY = stripY - 4f;
        panelW = total + 8f;
        panelH = chipH + 8f;
    }

    /** Индекс элемента в фиксированном порядке строки (неизвестные — в конец). */
    private static int orderIndex(HudElement el) {
        int idx = CHIP_ORDER.indexOf(el.getName());
        return idx < 0 ? CHIP_ORDER.size() : idx;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button == 0) {
            for (int i = 0; i < chipBounds.size(); i++) {
                float[] b = chipBounds.get(i);
                if (MathUtils.isHovered(b[0], b[1], b[2], b[3], (float) mouseX, (float) mouseY)) {
                    HudManager hm = LinkenVisuals.getInstance().getHudManager();
                    HudElement el = chipElements.get(i);
                    if (hm != null) {
                        BooleanSetting setting = hm.getElementSetting(el);
                        boolean next = !hm.isElementVisible(el);
                        if (setting != null) {
                            setting.setValue(next);
                            hm.handleElementToggle(setting, next, (float) mouseX, (float) mouseY);
                        } else {
                            el.setToggled(next);
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public void close() {
        // Esc — не закрываем сразу, а запускаем анимацию наружу (реальное закрытие в finishClose()).
        startClosing();
    }
}
