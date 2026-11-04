package ru.method.linkenvisuals.screen.linkgui.components;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiLayout;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiSettings;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiTheme;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.render.GuiIcons;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

@Getter
public class ModuleCard {
    private static final float BADGE_SIZE = 34f;
    private static final float BADGE_RADIUS = 8f;
    private static final float BADGE_PAD_X = 12f;
    private static final float ICON_SIZE = 13f;

    private final Module module;
    @Setter
    private float x, y, width;
    @Setter
    private float contentAlpha = 1f;
    // Карточка, которая сейчас «слушает» назначение бинда (клавиша/кнопка мыши/колесо). Только одна за раз.
    private static ModuleCard bindingCard;
    // Прямоугольник пилюли бинда (для попадания кликом) — заполняется в render().
    private float pillX, pillY, pillW, pillH;
    // Кэш переноса описания: текст статичен, пересчитываем только при смене текста/ширины (не каждый кадр).
    private java.util.List<String> cachedDescLines;
    private String cachedDescText;
    private float cachedDescWidth = -1f;
    // Кэш перевода описания (I18n.translate на каждый кадр — лишний lookup в языковую мапу).
    private String cachedTranslatedDesc;
    // Предвычисленные lowercase-имена для поиска (toLowerCase на каждый модуль на кадр — мусор в GC).
    private String loweredName;
    private String loweredDisplayName;
    // Кэш текста бинда и ширины пилюли: пересобирались каждый кадр (toString+replace+getWidth).
    private String cachedBindText;
    private float cachedBindPillW;
    private int cachedBindKey = Integer.MIN_VALUE;
    private boolean cachedBindMouse;
    private boolean cachedBindActive;
    // Анимация смены текста бинда (как у чипа enum / смены гуи-стайла): старое уезжает вверх, новое — снизу.
    private final AnimatedString bindAnim = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "None");
    // Тайминги 1в1 из Lucky (LuckyAnimations): toggleSwitch / cardHover / moduleStripe.
    private final Animation toggleAnim = new Animation(320, 1f, false, Easing.EASE_OUT_EXPO);
    private final Animation hoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_EXPO);
    private final Animation enableAnim = new Animation(300, 1f, false, Easing.EASE_OUT_CUBIC);
    // «Филлер» при вкл/выкл функции — заполнение снизу вверх (порт Lucky moduleStripe).
    private final Animation stripeAnim = new Animation(520, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation searchVisibleAnim = new Animation(280, 1f, true, Easing.EASE_OUT_CUBIC);
    // Каскадное появление карточки (как cardEnter в PrimeVisuals): выезд снизу + затухание.
    private final Animation enterAnim = new Animation(840, 1f, true, Easing.EASE_OUT_EXPO);

    public ModuleCard(Module module) {
        this.module = module;
    }

    /** Имя модуля в нижнем регистре — для поиска (кэшировано, не аллоцируется на кадр). */
    public String loweredName() {
        if (loweredName == null) {
            loweredName = module.getName().toLowerCase(java.util.Locale.ROOT);
        }
        return loweredName;
    }

    /** Отображаемое имя в нижнем регистре — для поиска (кэшировано). */
    public String loweredDisplayName() {
        if (loweredDisplayName == null) {
            loweredDisplayName = module.getDisplayName().toLowerCase(java.util.Locale.ROOT);
        }
        return loweredDisplayName;
    }

    /** Перезапуск каскадной анимации появления с задержкой по позиции (как Prime replayEnter). */
    public void replayEnter(int index) {
        enterAnim.setDuration(780L + (long) index * 84L);
        enterAnim.reset();
        // Снап состояния тумблера/филлера под текущее значение — чтобы при смене категории
        // включённые модули не «проигрывали» включение заново.
        // snapOpen() = мгновенно «включено» (value=1); snapTo(false) = «выключено» (value=0).
        boolean on = module.isToggle();
        if (on) {
            toggleAnim.snapOpen();
            enableAnim.snapOpen();
            stripeAnim.snapOpen();
        } else {
            toggleAnim.snapTo(false);
            enableAnim.snapTo(false);
            stripeAnim.snapTo(false);
        }
    }

    /** Прогресс заполнения филлера: плавно вверх при вкл, линейно вниз при выкл (порт Lucky moduleStripeFill). */
    private static float moduleStripeFill(Animation anim) {
        if (anim.isForward() && anim.finished()) return 1f;
        float raw = anim.getLinear();
        if (anim.isForward()) {
            float filled = liquidFill(raw);
            return raw >= 0.985f ? 1f : filled;
        }
        return raw;
    }

    private static float liquidFill(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return c * c * (3f - 2f * c);
    }

    public float getHeight() {
        return LinkGuiLayout.CARD_HEIGHT;
    }

    public void updateSearchVisible(boolean visible) {
        searchVisibleAnim.update(visible);
    }

    /** Мгновенно выставить видимость (без анимации) — чтобы при открытии GUI не мелькали чужие категории. */
    public void snapVisible(boolean visible) {
        if (visible) searchVisibleAnim.snapOpen();
        else searchVisibleAnim.snapTo(false);
    }

    public float getVisibility() {
        return searchVisibleAnim.getValue();
    }

    /** Участвует ли карточка в раскладке (видима или ещё доигрывает сворачивание). */
    public boolean isRenderable() {
        return getVisibility() > 0.015f;
    }

    /** Плавный масштаб слота раскладки (smoothstep) — высота сворачивается при скрытии. */
    public float layoutScale() {
        float v = getVisibility();
        return v * v * (3f - 2f * v);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        render(context.getMatrices(), mouseX, mouseY, delta);
    }

    public void render(Matrix3x2fStack stack, int mouseX, int mouseY, float delta) {
        float vis = searchVisibleAnim.getValue() * contentAlpha;
        if (vis < 0.02f) return;

        boolean hovered = MathUtils.isHovered(x, y, width, getHeight(), mouseX, mouseY);
        boolean enabled = module.isToggle();
        toggleAnim.update(enabled);
        enableAnim.update(enabled);
        hoverAnim.update(hovered);
        stripeAnim.update(enabled);

        float toggleT = toggleAnim.getValue();
        float hoverT = hoverAnim.getValue();
        float enableT = enableAnim.getValue();
        // Прогресс «филлера» — общий blend для подсветки карточки/иконки (как enableBlend в Lucky).
        float stripeFill = moduleStripeFill(stripeAnim);
        boolean stripeFull = enabled && (stripeAnim.finished() || stripeFill >= 0.992f);
        float enableBlend = Math.max(enableT, stripeFill);
        float cardT = Math.max(enableBlend, hoverT * 0.42f);

        // Каскадное появление (enter) + видимость поиска (vis) дают общую прозрачность;
        // прогресс тумблера от них не зависит, иначе он ложно «включается» при смене категории.
        float enter = enterAnim.getValue();
        float prevFade = LinkGuiRenderContext.getFadeMultiplier();
        LinkGuiRenderContext.setFade(prevFade * Math.max(0f, Math.min(1f, vis * enter)));

        stack.pushMatrix();
        // Каскадное появление функции (при смене категории): лёгкий «подрост» масштаба от центра
        // + выезд снизу + затухание. Приподнимание при наведении — -3*hoverT.
        float centerX = x + width / 2f;
        float centerY = y + getHeight() / 2f;
        float enterScale = 0.94f + 0.06f * enter;
        stack.translate(centerX, centerY);
        stack.scale(enterScale, enterScale);
        stack.translate(-centerX, -centerY);
        stack.translate(0f, -3f * hoverT + (1f - enter) * 16f);

        // Тень растёт вместе с hover-подъёмом — отрыв карточки от панели читается физически.
        LinkGuiChrome.drawCardShadow(stack, x, y, width, getHeight(), 10f, hoverT);
        LinkGuiChrome.drawCard(stack, x, y, width, getHeight(), 10f, enabled, cardT);

        // «Аврора» включённой функции: свет цвета темы из левого края карточки — включённые модули
        // читаются мгновенно, карточка перестаёт быть серым прямоугольником.
        Color acc = LinkGuiThemeScope.accent();
        float washT = Math.max(moduleStripeFill(stripeAnim) * 0.9f, hoverAnim.getValue() * 0.22f);
        if (washT > 0.02f) {
            WindowGuiRender.drawRoundedHorLinearGradientRect(stack, x, y, width * 0.55f, getHeight(), 10f,
                    WindowGuiColors.withAlpha(acc, 0.11f * washT), WindowGuiColors.withAlpha(acc, 0f));
        }
        // Акцентная рамка: проявляется при наведении и у включённых.
        float ringT = Math.max(moduleStripeFill(stripeAnim) * 0.5f, hoverAnim.getValue());
        if (ringT > 0.02f) {
            WindowGuiRender.drawRoundedOutlineRect(stack, x, y, width, getHeight(), 10f, 1f,
                    WindowGuiColors.withAlpha(acc, 0.38f * ringT));
        }

        // «Филлер» слева — вертикальная полоска, заполняется снизу вверх при включении функции.
        float stripeW = 3f;
        float stripeH = getHeight() * 0.6f;
        float stripeY = y + (getHeight() - stripeH) / 2f;
        LinkGuiChrome.drawModuleStripe(stack, x + 5f, stripeY, stripeW, stripeH, stripeFill, stripeFull);

        Color accent = LinkGuiThemeScope.accent();
        float badgeX = x + BADGE_PAD_X;
        // Иконка/название/описание/тумблер подняты на 5px (компактнее верх карточки).
        float badgeY = y + (getHeight() - BADGE_SIZE) / 2f - 5f;
        float badgeT = Math.max(enableBlend, hoverT * 0.4f);
        LinkGuiChrome.drawIconBadge(stack, badgeX, badgeY, BADGE_SIZE, BADGE_RADIUS, enabled, badgeT);
        Color iconColor = WindowGuiColors.lerp(WindowGuiColors.withAlpha(WindowGuiTheme.TEXT_SECOND, 0.35f), accent, badgeT);
        // Иконка функции = иконка её категории (для Player — текстура-аватар, иначе глиф).
        net.minecraft.util.Identifier catTex = GuiIcons.forCategory(module.getCategory());
        if (catTex != null) {
            GuiIcons.draw(stack, catTex, badgeX + BADGE_SIZE / 2f, badgeY + BADGE_SIZE / 2f, ICON_SIZE + 3f, iconColor);
        } else {
            LinkGuiChrome.drawIconInBadge(stack, badgeX, badgeY, BADGE_SIZE,
                    module.getCategory().getIcon(), iconColor, ICON_SIZE);
        }

        // Кнопка бинда ПОД иконкой — только у обычных модулей. Action-модули (Elements) не биндятся.
        if (!module.isActionModule()) {
            // Иконка-клавиатура (цвет темы) + клавиша (или None). Клик по пилюле — назначение бинда;
            // во время назначения показывает «...».
            ru.method.linkenvisuals.modules.settings.api.Bind moduleBind = module.getBind();
            boolean thisBinding = bindingCard == this;
            float bindTextSize = 6.6f;
            float kbSize = 12f;
            // Текст бинда и ширина пилюли меняются только при перебинде — кэшируем по (key, mouse, binding).
            int bindKey = moduleBind == null ? -1 : moduleBind.getKey();
            boolean bindMouse = moduleBind != null && moduleBind.isMouse();
            if (cachedBindText == null || cachedBindKey != bindKey
                    || cachedBindMouse != bindMouse || cachedBindActive != thisBinding) {
                // Имя клавиши без подчёркиваний/дефисов: RIGHT_SHIFT → RIGHT SHIFT.
                cachedBindText = thisBinding ? "..."
                        : (moduleBind == null || bindKey == -1) ? "None"
                        : moduleBind.toString().replace('_', ' ').replace('-', ' ');
                cachedBindPillW = 8f + kbSize + 4f + Fonts.MEDIUM.getWidth(cachedBindText, bindTextSize) + 8f;
                cachedBindKey = bindKey;
                cachedBindMouse = bindMouse;
                cachedBindActive = thisBinding;
            }
            String keyText = cachedBindText;
            this.pillH = 14f;
            this.pillX = badgeX;
            this.pillY = badgeY + BADGE_SIZE + 5f;
            this.pillW = cachedBindPillW;
            // Без бинда (None) пилюля чуть тусклее, с биндом — чуть ярче; в режиме назначения — максимально яркая.
            boolean hasBind = moduleBind != null && moduleBind.getKey() != -1;
            float pillBase = hasBind ? 0.5f : 0.26f;
            LinkGuiChrome.drawPillButton(stack, pillX, pillY, pillW, pillH, 5f,
                    thisBinding ? 1f : Math.max(pillBase, Math.max(enableBlend, hoverT) * 0.85f));
            Color kbColor = hasBind || thisBinding ? accent
                    : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150);
            GuiIcons.draw(stack, GuiIcons.KEYBOARD, pillX + 8f + kbSize / 2f, pillY + pillH / 2f, kbSize, kbColor);
            bindAnim.update(keyText);
            drawBindText(stack, pillX + 8f + kbSize + 4f, pillY + pillH / 2f, bindTextSize,
                    LinkGuiThemeScope.text(hasBind || thisBinding ? 235 : 160));
        } else {
            this.pillW = 0f;
            this.pillH = 0f;
        }

        // Цвет текста следует теме (на светлых темах — тёмный, на тёмных — светлый).
        Color title = WindowGuiColors.lerp(LinkGuiThemeScope.text(140), LinkGuiThemeScope.text(255), Math.max(enableBlend, hoverT * 0.55f));
        Color desc = WindowGuiColors.lerp(LinkGuiThemeScope.text(75), LinkGuiThemeScope.text(170), Math.max(enableBlend, hoverT * 0.45f));
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, module.getDisplayName(),
                x + 54f, y + 23f, title, 9.4f);

        // Перевод описания кэшируем — I18n.translate на каждый кадр для каждой карточки не нужен
        // (ключ статичен; язык меняется через перезагрузку ресурсов, когда карточки пересоздаются).
        if (cachedTranslatedDesc == null) {
            String translated = I18n.translate(module.getDescription());
            cachedTranslatedDesc = translated.equals(module.getDescription()) ? module.getName() : translated;
        }
        String description = cachedTranslatedDesc;
        // Описание переносится на 2-ю строку, если не влезает (раньше обрезалось многоточием).
        float descSize = 7.9f;
        float descMaxW = width - 104f;
        if (cachedDescLines == null || !description.equals(cachedDescText) || cachedDescWidth != descMaxW) {
            cachedDescLines = wrapLines(description, descMaxW, descSize, 2);
            cachedDescText = description;
            cachedDescWidth = descMaxW;
        }
        java.util.List<String> descLines = cachedDescLines;
        float descLineH = Fonts.MEDIUM.getHeight(descSize) + 2.5f;
        float descStartY = descLines.size() <= 1 ? y + 41f : y + 36f;
        for (int i = 0; i < descLines.size(); i++) {
            WindowGuiRender.drawText(stack, Fonts.MEDIUM, descLines.get(i),
                    x + 54f, descStartY + i * descLineH, desc, descSize);
        }

        float toggleCx = x + width - 46f + 18f;
        float toggleCy = y + getHeight() / 2f - 5f;
        // Action-модули (например Elements) тумблер не показывают — у них только действие по клику.
        if (!module.hidesToggle()) {
            LinkGuiChrome.drawToggle(stack, x + width - 46f, toggleCy - 9f, 36f, 18f, toggleT);
        }
        // Точки под тумблером — индикатор, что у функции есть настройки (ПКМ открывает их).
        if (!module.getSettings().isEmpty()) {
            GuiIcons.draw(stack, GuiIcons.DOT, toggleCx, toggleCy + 20f, 15f,
                    LinkGuiThemeScope.text((int) (110 + 90 * Math.max(enableBlend, hoverT))));
        }
        stack.popMatrix();
        LinkGuiRenderContext.setFade(prevFade);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchVisibleAnim.getValue() < 0.35f || contentAlpha < 0.35f) return false;
        if (!MathUtils.isHovered(x, y, width, getHeight(), (float) mouseX, (float) mouseY)) return false;

        // Action-модуль (Elements): ЛКМ = действие (открыть редактор HUD), ПКМ = настройки. Без тогла и бинда.
        if (module.isActionModule()) {
            if (button == 0) {
                module.activate();
            } else if (button == 1 && !module.getSettings().isEmpty()) {
                LinkGuiSettings.open(module, (float) mouseX, (float) mouseY);
            }
            return true;
        }

        // Идёт назначение бинда у этой карточки → клик ЛЮБОЙ кнопкой мыши назначает её этой кнопкой.
        if (bindingCard == this) {
            module.getBind().setKey(button);
            module.getBind().setMouse(true);
            bindingCard = null;
            return true;
        }

        // Вход в режим назначения: ТОЛЬКО средняя кнопка мыши по карточке, либо ЛКМ по самой пилюле бинда.
        // Клик по иконке функции больше НЕ биндит (он идёт в обычный тогл ниже).
        boolean onPill = MathUtils.isHovered(pillX, pillY, pillW, pillH, (float) mouseX, (float) mouseY);
        if (button == 2 || (button == 0 && onPill)) {
            bindingCard = this;
            return true;
        }

        // Клик по другой карточке во время её назначения — отменяем назначение и обрабатываем клик обычно.
        if (bindingCard != null) {
            bindingCard = null;
        }

        if (button == 0) {
            module.toggle();
        } else if (button == 1 && !module.getSettings().isEmpty()) {
            LinkGuiSettings.open(module, (float) mouseX, (float) mouseY);
        }
        return true;
    }

    /** Идёт ли сейчас назначение бинда какой-либо карточке (чтобы не закрывать GUI клавишей-тоглом). */
    public static boolean isAnyBinding() {
        return bindingCard != null;
    }

    public static void cancelBinding() {
        bindingCard = null;
    }

    /** Назначить биндом нажатую клавишу (Esc/Delete — снять бинд). Возвращает true, если назначение шло. */
    public static boolean handleBindKey(int keyCode) {
        if (bindingCard == null) return false;
        Module m = bindingCard.module;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
            m.getBind().setKey(-1);
            m.getBind().setMouse(false);
        } else {
            m.getBind().setKey(keyCode);
            m.getBind().setMouse(false);
        }
        bindingCard = null;
        return true;
    }

    /** Назначить биндом колесо мыши (вверх/вниз). Возвращает true, если назначение шло. */
    public static boolean handleBindScroll(double verticalAmount) {
        if (bindingCard == null || verticalAmount == 0.0) return false;
        int key = verticalAmount > 0.0
                ? ru.method.linkenvisuals.modules.settings.api.Bind.MOUSE_SCROLL_UP
                : ru.method.linkenvisuals.modules.settings.api.Bind.MOUSE_SCROLL_DOWN;
        bindingCard.module.getBind().setKey(key);
        bindingCard.module.getBind().setMouse(true);
        bindingCard = null;
        return true;
    }

    /** Текст бинда с анимацией смены: старое уезжает вверх и гаснет, новое приезжает снизу (как чип enum). */
    private void drawBindText(Matrix3x2fStack stack, float x, float centerY, float size, Color color) {
        float th = Fonts.MEDIUM.getHeight(size);
        float baseY = centerY - th / 2f;
        if (!bindAnim.isAnimating()) {
            Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), bindAnim.getCurrent(), x, baseY, color);
            return;
        }
        float p = Math.max(0f, Math.min(1f, bindAnim.progress()));
        float shift = th * 1.1f;
        Color cPrev = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(color.getAlpha() * (1f - p)));
        Color cCurr = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(color.getAlpha() * p));
        Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), bindAnim.getPrevious(), x, baseY - shift * p, cPrev);
        Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), bindAnim.getCurrent(), x, baseY + shift * (1f - p), cCurr);
    }

    private static String clip(String text, float maxWidth, float size) {
        if (Fonts.MEDIUM.getWidth(text, size) <= maxWidth) return text;
        while (text.length() > 3 && Fonts.MEDIUM.getWidth(text + "...", size) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    /** Перенос текста по словам максимум на {@code maxLines} строк; последняя строка при необходимости — с «...». */
    private static java.util.List<String> wrapLines(String text, float maxWidth, float size, int maxLines) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int start = 0;
        while (start < text.length() && out.size() < maxLines) {
            int end = start;
            int lastSpace = -1;
            while (end < text.length()) {
                if (text.charAt(end) == ' ') lastSpace = end;
                if (Fonts.MEDIUM.getWidth(text.substring(start, end + 1), size) > maxWidth && end > start) {
                    if (lastSpace > start) end = lastSpace;
                    else end = Math.max(start, end - 1);
                    break;
                }
                end++;
            }
            if (end >= text.length()) {
                out.add(text.substring(start).trim());
                break;
            }
            if (out.size() == maxLines - 1) {
                // Ещё остаётся текст, а это последняя допустимая строка — обрезаем с многоточием.
                out.add(clip(text.substring(start), maxWidth, size));
                break;
            }
            out.add(text.substring(start, end).trim());
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') start++;
        }
        if (out.isEmpty()) out.add("");
        return out;
    }
}
