package ru.method.linkenvisuals.screen.linkgui.windows;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import net.minecraft.client.resource.language.I18n;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiContext;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiLayout;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemePreset;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiStyle;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public final class ThemeWindow {
    private static final long PILL_SLIDE_MS = 480L;
    private static final float LIST_TOP = 34f;
    private static final float ROW_PAD_X = 10f;
    // Темы — сеткой свотчей 2×N: каждая ячейка показывает СВОЙ фон и акцент (мини-превью темы).
    private static final float SWATCH_H = 30f;
    private static final float SWATCH_GAP = 5f;

    private final LinkGuiContext context;
    private final InfinityAnimation pillSlideAnim = new InfinityAnimation(Easing.BOTH_SINE);
    private final Animation cursorToggleAnim = new Animation(220, 1f, false, Easing.BOTH_SINE);
    private final Animation scoreboardToggleAnim = new Animation(220, 1f, false, Easing.BOTH_SINE);
    private final AnimatedString boostStyleAnim = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");
    // Анимация «переписывания» значения стиля GUI (как ввод текста в поиск).
    private final AnimatedString styleAnim = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");
    private final AnimatedString hudStyleAnim = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");
    private final AnimatedString hudShapeAnim = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");
    // Hover всех кликабельных зон анимирован (раньше подсветка скакала за один кадр и выбивалась
    // из общей плавности GUI); галочка активной темы проявляется фейдом, а не вспыхивает.
    private final Animation[] rowHoverAnims = createRowAnims(180);
    private final Animation[] rowCheckAnims = createRowAnims(220);
    private final Animation kbHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation guiStyleHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation boostHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation hudStyleHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation hudShapeHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation applyHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation defaultHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
    private float x, y, width;
    private boolean pillSlideInitialized;
    private boolean draggingSlider;
    private boolean draggingTransparency;
    private boolean bindingUi;
    // Значение слайдера в ожидании применения (драг меняет только его, не реальный масштаб).
    private Float pendingScale;
    // Раскрывающиеся настройки кастомного скорборда (те же, что в HUD-редакторе по ПКМ).
    private static final float SCOREBOARD_EXTRA_H = 52f;
    private boolean draggingSbOpacity;
    private boolean draggingSbRadius;

    // Кэш Setting-объектов модуля UI: резолвим по имени один раз, дальше читаем getValue() без stream-поиска.
    // Ссылки стабильны в течение сессии (модуль UI один), поэтому кэшировать безопасно.
    private NumberSetting scaleSetting;
    private NumberSetting transparencySetting;
    private EnumSetting<?> boostFpsSetting;
    private EnumSetting<?> hudStyleSetting;
    private EnumSetting<?> hudShapeSetting;
    private EnumSetting<?> guiStyleSetting;
    private BooleanSetting scoreboardSetting;

    // Кэш перевода enum: если значение enum не менялось — не дёргаем I18n.translate каждый кадр.
    private Object boostFpsLastEnum;
    private String boostFpsCachedText;
    private Object hudStyleLastEnum;
    private String hudStyleCachedText;
    private Object hudShapeLastEnum;
    private String hudShapeCachedText;

    public ThemeWindow(LinkGuiContext context) {
        this.context = context;
    }

    private static Animation[] createRowAnims(long durationMs) {
        Animation[] anims = new Animation[LinkGuiThemePreset.values().length];
        for (int i = 0; i < anims.length; i++) {
            anims[i] = new Animation(durationMs, 1f, false, Easing.EASE_OUT_CUBIC);
        }
        return anims;
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public float getHeight() {
        // Панель плавно подрастает вниз, когда раскрыты настройки кастомного скорборда.
        return LinkGuiLayout.THEME_HEIGHT + SCOREBOARD_EXTRA_H * scoreboardToggleAnim.getValue();
    }

    /** Y координата начала секции «Интерфейс» (под сеткой тем). */
    private float interfaceTop() {
        int rows = (LinkGuiThemePreset.values().length + 1) / 2;
        return y + LIST_TOP + rows * (SWATCH_H + SWATCH_GAP) + 8f;
    }

    public void render(DrawContext drawContext, int mouseX, int mouseY) {
        render(drawContext.getMatrices(), mouseX, mouseY);
    }

    public void renderOverlay(Matrix3x2fStack stack, int mouseX, int mouseY) {
        render(stack, mouseX, mouseY);
    }

    private void render(Matrix3x2fStack stack, int mouseX, int mouseY) {
        float h = getHeight();
        LinkGuiChrome.drawGlassPanel(stack, x, y, width, h, LinkGuiLayout.RADIUS);
        LinkGuiChrome.drawSectionTitle(stack, "THEME", "U", x + 14f, y + 12f);

        LinkGuiThemePreset[] presets = LinkGuiThemePreset.values();
        if (!pillSlideInitialized) {
            // Рамка активной темы при первом кадре не анимируется — снапаем состояние.
            for (int i = 0; i < presets.length; i++) {
                if (context.getSelectedTheme() == presets[i]) rowCheckAnims[i].snapOpen();
                else rowCheckAnims[i].snapTo(false);
            }
            pillSlideInitialized = true;
        }

        // Сетка свотчей 2×N: каждая ячейка — мини-превью темы (её фон + «аврора» её акцента).
        float rowW = width - ROW_PAD_X * 2f;
        float cellW = (rowW - SWATCH_GAP) / 2f;
        for (int i = 0; i < presets.length; i++) {
            LinkGuiThemePreset preset = presets[i];
            boolean active = context.getSelectedTheme() == preset;
            float cx = x + ROW_PAD_X + (i % 2) * (cellW + SWATCH_GAP);
            float cy = y + LIST_TOP + (i / 2) * (SWATCH_H + SWATCH_GAP);
            boolean hover = MathUtils.isHovered(cx, cy, cellW, SWATCH_H, mouseX, mouseY);
            rowHoverAnims[i].update(hover && !active);
            rowCheckAnims[i].update(active);
            float hoverT = rowHoverAnims[i].getValue();
            float checkT = rowCheckAnims[i].getValue();

            // Фон свотча — фон самой темы; поверх — «аврора» её акцента (слева направо в ноль).
            // Внутренние слои (аврора/hover) рисуем с инсетом 1px и меньшим радиусом: их сглаженный
            // край ложится ВНУТРИ заливки и не спорит с её углами (иначе углы выглядели сломанными).
            WindowGuiRender.drawRoundedRect(stack, cx, cy, cellW, SWATCH_H, 8f, preset.background());
            WindowGuiRender.drawRoundedHorLinearGradientRect(stack, cx + 1f, cy + 1f, cellW - 2f, SWATCH_H - 2f, 7f,
                    WindowGuiColors.withAlpha(preset.primary(), 0.30f),
                    WindowGuiColors.withAlpha(preset.primary(), 0f));
            // Точка акцента.
            WindowGuiRender.drawRoundedRect(stack, cx + 8f, cy + SWATCH_H / 2f - 4f, 8f, 8f, 4f, preset.primary());
            // Hover — лёгкое осветление ячейки.
            if (hoverT > 0.01f) {
                WindowGuiRender.drawRoundedRect(stack, cx + 1f, cy + 1f, cellW - 2f, SWATCH_H - 2f, 7f,
                        new Color(255, 255, 255, Math.round(16f * hoverT)));
            }
            // Имя — контрастом к фону КОНКРЕТНОЙ темы (на светлых свотчах тёмный текст).
            float lum = (0.299f * preset.background().getRed() + 0.587f * preset.background().getGreen()
                    + 0.114f * preset.background().getBlue()) / 255f;
            Color nameC = lum > 0.6f ? new Color(26, 28, 34) : new Color(238, 240, 245);
            float nameSize = 7.2f;
            WindowGuiRender.drawText(stack, Fonts.MEDIUM, preset.getName(),
                    cx + 21f, cy + (SWATCH_H - Fonts.MEDIUM.getHeight(nameSize)) / 2f,
                    new Color(nameC.getRed(), nameC.getGreen(), nameC.getBlue(),
                            Math.round(195 + 60 * checkT)), nameSize);
            // Рамка: тусклая у всех, у активной — яркая акцентом её же темы (плавно проявляется).
            WindowGuiRender.drawRoundedOutlineRect(stack, cx, cy, cellW, SWATCH_H, 8f, 1.2f,
                    WindowGuiColors.withAlpha(preset.primary(), 0.16f + 0.74f * checkT));
            // Галочка активной темы.
            if (checkT > 0.03f) {
                Color check = preset.primary();
                WindowGuiRender.drawText(stack, Fonts.ICONSNEW, "H",
                        cx + cellW - 15f, cy + SWATCH_H / 2f - 4f,
                        new Color(check.getRed(), check.getGreen(), check.getBlue(),
                                Math.round(255 * checkT)), 7f);
            }
        }

        renderInterfaceSection(stack, mouseX, mouseY);
    }

    // ===== Секция «Интерфейс» (перенесена сюда из отдельного окна) =====

    private void renderInterfaceSection(Matrix3x2fStack stack, int mouseX, int mouseY) {
        float it = interfaceTop();
        LinkGuiChrome.drawDivider(stack, x + 12f, it - 6f, width - 24f);
        LinkGuiChrome.drawSectionTitle(stack, "INTERFACE", "T", x + 14f, it);

        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        float scaleVal = pendingScale != null ? pendingScale : readScale(ui);
        Color text = LinkGuiThemeScope.text(210);

        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "Interface Scale", x + 14f, it + 25f, text, 8.4f);
        String pct = Math.round(scaleVal * 100f) + "%";
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, pct,
                x + width - 14f - Fonts.ROUNDBOLD.getWidth(pct, 8.4f), it + 25f, LinkGuiThemeScope.accent(), 8.4f);

        float barX = x + 14f;
        float barY = it + 42f;
        float barW = width - 28f;
        WindowGuiRender.drawRoundedRect(stack, barX, barY, barW, 3f, 1.5f, LinkGuiThemeScope.text(20));
        // Кламп как у парного слайдера прозрачности: значение из конфига вне 0.75..1.5 не должно
        // выталкивать заливку и ручку за пределы трека.
        float t = MathUtils.clamp((scaleVal - 0.75f) / 0.75f, 0f, 1f);
        WindowGuiRender.drawRoundedRect(stack, barX, barY, barW * t, 3f, 1.5f, LinkGuiThemeScope.accent());
        LinkGuiChrome.drawSliderKnob(stack, barX + barW * t, barY + 1.5f, 8f);

        if (draggingSlider && ui != null) {
            float clicked = Math.max(0f, Math.min(1f, (mouseX - barX) / barW));
            // Только запоминаем значение — реальный масштаб не трогаем до «Apply».
            pendingScale = 0.75f + clicked * 0.75f;
        }

        float gap = 8f;
        float btnY = it + 56f;
        float btnH = 20f;
        float btnW = (width - 28f - gap) / 2f;
        drawActionButton(stack, x + 14f, btnY, btnW, btnH, "Apply", mouseX, mouseY, true, applyHoverAnim);
        drawActionButton(stack, x + 14f + btnW + gap, btnY, btnW, btnH, "Default", mouseX, mouseY, false, defaultHoverAnim);

        // GUI Style (Panel/Link) убран — остаётся только Link, выбор стиля не нужен.
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "Cursor Tracker", x + 14f, it + 86f, text, 8.4f);
        cursorToggleAnim.update(context.isCursorTracker());
        LinkGuiChrome.drawToggle(stack, x + width - 48f, it + 83f, 34f, 16f, cursorToggleAnim.getValue());

        // Прозрачность GUI (применяется сразу, без Apply).
        float trVal = readTransparency(ui); // 0..100
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "GUI Transparency", x + 14f, it + 112f, text, 8.4f);
        String trPct = Math.round(trVal) + "%";
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, trPct,
                x + width - 14f - Fonts.ROUNDBOLD.getWidth(trPct, 8.4f), it + 112f, LinkGuiThemeScope.accent(), 8.4f);
        float trBarX = x + 14f;
        float trBarY = it + 129f;
        float trBarW = width - 28f;
        WindowGuiRender.drawRoundedRect(stack, trBarX, trBarY, trBarW, 3f, 1.5f, LinkGuiThemeScope.text(20));
        float trT = MathUtils.clamp((trVal - 30f) / 70f, 0f, 1f);
        WindowGuiRender.drawRoundedRect(stack, trBarX, trBarY, trBarW * trT, 3f, 1.5f, LinkGuiThemeScope.accent());
        LinkGuiChrome.drawSliderKnob(stack, trBarX + trBarW * trT, trBarY + 1.5f, 8f);
        if (draggingTransparency && ui != null) {
            float clicked = MathUtils.clamp((mouseX - trBarX) / trBarW, 0f, 1f);
            setTransparency(ui, 30f + clicked * 70f);
        }

        // Кейбинд открытия GUI (можно перебиндить прямо здесь — захватит ту клавишу, что реально шлёт клавиатура).
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "Open Keybind", x + 14f, it + 152f, text, 8.4f);
        float kbX = x + 14f;
        float kbY = it + 167f;
        float kbW = width - 28f;
        float kbH = 22f;
        boolean kbHover = MathUtils.isHovered(kbX, kbY, kbW, kbH, mouseX, mouseY);
        kbHoverAnim.update(kbHover);
        LinkGuiChrome.drawInset(stack, kbX, kbY, kbW, kbH, 7f);
        // Hover 0.6 — тише режима назначения (1.0): иерархия «наведение < активное состояние».
        float kbStrength = Math.max(0.6f * kbHoverAnim.getValue(), bindingUi ? 1f : 0f);
        if (kbStrength > 0.01f) {
            LinkGuiChrome.drawPillButton(stack, kbX + 1f, kbY + 1f, kbW - 2f, kbH - 2f, 6f, kbStrength);
        }
        String bindText = bindingUi ? "..." : readUiBind(ui);
        float btw = Fonts.MEDIUM.getWidth(bindText, 8.4f);
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, bindText,
                kbX + (kbW - btw) / 2f, kbY + 6f, LinkGuiThemeScope.text(235), 8.4f);

        // HUD Style (оформление панелей HUD) и HUD Shape (форма/скругление) — одним рядом, прямо в Link GUI.
        float hudGap = 8f;
        float hudHalfW = (width - 28f - hudGap) / 2f;
        drawHudSelector(stack, "HUD Style", hudStyleAnim, hudStyleHoverAnim, readHudStyle(ui), x + 14f, it + 201f, hudHalfW, mouseX, mouseY);
        drawHudSelector(stack, "HUD Shape", hudShapeAnim, hudShapeHoverAnim, readHudShape(ui), x + 14f + hudHalfW + hudGap, it + 201f, hudHalfW, mouseX, mouseY);

        // Выбор стиля GUI: Link (новый интерфейс) / Panel (классические панели). Клик циклит значение.
        drawHudSelector(stack, "GUI Style", styleAnim, guiStyleHoverAnim, readGuiStyle(ui), x + 14f, it + 253f, width - 28f, mouseX, mouseY);

        // Кастомный скорборд (перенесено из настроек модуля UI — сам модуль в списке не показывается).
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "Custom Scoreboard", x + 14f, it + 287f, text, 8.4f);
        scoreboardToggleAnim.update(readCustomScoreboard(ui));
        float sbT = scoreboardToggleAnim.getValue();
        LinkGuiChrome.drawToggle(stack, x + width - 48f, it + 284f, 34f, 16f, sbT);

        // Раскрывающиеся настройки скорборда — единый источник с HUD-редактором (ПКМ по элементу).
        if (sbT > 0.03f) {
            renderScoreboardSettings(stack, it, sbT, mouseX, mouseY);
        }
    }

    /** Настройки кастомного скорборда под тумблером: Opacity и Radius (альфа/драг по {@code sbT}).
     *  Показ счёта настройкой не управляется — бессмысленные «порядковые» числа скрываются автоматически. */
    private void renderScoreboardSettings(Matrix3x2fStack stack, float it, float sbT, int mouseX, int mouseY) {
        ru.method.linkenvisuals.hud.impl.ScoreboardHud sb = scoreboardHud();
        if (sb == null) return;
        int a = Math.round(210 * sbT);
        Color label = LinkGuiThemeScope.text(a);

        // Прозрачность фон-панели скорборда (работает во всех HUD-стилях, включая Link).
        float opVal = sb.getOpacityValue();
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "Opacity", x + 14f, it + 313f, label, 8.4f);
        String opPct = Math.round(opVal / 255f * 100f) + "%";
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, opPct,
                x + width - 14f - Fonts.ROUNDBOLD.getWidth(opPct, 8.4f), it + 313f, LinkGuiThemeScope.accent(), 8.4f);
        float opBarX = x + 14f;
        float opBarY = it + 330f;
        float opBarW = width - 28f;
        WindowGuiRender.drawRoundedRect(stack, opBarX, opBarY, opBarW, 3f, 1.5f, LinkGuiThemeScope.text(Math.round(20 * sbT)));
        float opT = MathUtils.clamp(opVal / 255f, 0f, 1f);
        WindowGuiRender.drawRoundedRect(stack, opBarX, opBarY, opBarW * opT, 3f, 1.5f,
                ColorUtils.alpha(LinkGuiThemeScope.accent(), Math.round(255 * sbT)));
        LinkGuiChrome.drawSliderKnob(stack, opBarX + opBarW * opT, opBarY + 1.5f, 8f);
        if (draggingSbOpacity) {
            float clicked = MathUtils.clamp((mouseX - opBarX) / opBarW, 0f, 1f);
            sb.setOpacityValue(clicked * 255f);
        }

        // Сглаживание углов панели (тоже работает во всех стилях).
        float rVal = sb.getRadiusValue();
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "Corner Radius", x + 14f, it + 347f, label, 8.4f);
        String rTxt = String.valueOf(Math.round(rVal));
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, rTxt,
                x + width - 14f - Fonts.ROUNDBOLD.getWidth(rTxt, 8.4f), it + 347f, LinkGuiThemeScope.accent(), 8.4f);
        float rBarX = x + 14f;
        float rBarY = it + 364f;
        float rBarW = width - 28f;
        WindowGuiRender.drawRoundedRect(stack, rBarX, rBarY, rBarW, 3f, 1.5f, LinkGuiThemeScope.text(Math.round(20 * sbT)));
        float rT = MathUtils.clamp(rVal / 18f, 0f, 1f);
        WindowGuiRender.drawRoundedRect(stack, rBarX, rBarY, rBarW * rT, 3f, 1.5f,
                ColorUtils.alpha(LinkGuiThemeScope.accent(), Math.round(255 * sbT)));
        LinkGuiChrome.drawSliderKnob(stack, rBarX + rBarW * rT, rBarY + 1.5f, 8f);
        if (draggingSbRadius) {
            float clicked = MathUtils.clamp((mouseX - rBarX) / rBarW, 0f, 1f);
            sb.setRadiusValue(clicked * 18f);
        }
    }

    private static ru.method.linkenvisuals.hud.impl.ScoreboardHud scoreboardHud() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getHudManager() == null) return null;
        return instance.getHudManager().getHudElement(ru.method.linkenvisuals.hud.impl.ScoreboardHud.class);
    }

    /** Кликабельный селектор-«пилюля» с подписью и анимацией смены значения. {@code selX/selY/selW} — позиция/ширина. */
    private void drawHudSelector(Matrix3x2fStack stack, String label, AnimatedString anim, Animation hoverAnim, String value, float selX, float selY, float selW, int mouseX, int mouseY) {
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, label, selX, selY - 14f, LinkGuiThemeScope.text(210), 8.4f);
        float sh = 22f;
        boolean hover = MathUtils.isHovered(selX, selY, selW, sh, mouseX, mouseY);
        hoverAnim.update(hover);
        LinkGuiChrome.drawInset(stack, selX, selY, selW, sh, 7f);
        if (hoverAnim.getValue() > 0.01f) {
            LinkGuiChrome.drawPillButton(stack, selX + 1f, selY + 1f, selW - 2f, sh - 2f, 6f,
                    0.6f * hoverAnim.getValue());
        }
        anim.update(value);
        drawAnimatedValue(stack, anim, selX + 8f, selY + 6f, 8.4f, LinkGuiThemeScope.text(230));
    }

    public boolean hasActiveUiBinding() {
        return bindingUi;
    }

    /** Назначение клавиши открытия GUI нажатой клавишей (Esc/Delete — сброс на правый Shift). */
    public boolean keyPressed(int keyCode, int scanCode) {
        if (!bindingUi) return false;
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
                ui.setBind(new ru.method.linkenvisuals.modules.settings.api.Bind(
                        ru.method.linkenvisuals.modules.impl.client.UiKeyHandler.DEFAULT_KEY, false));
            } else {
                ui.setBind(new ru.method.linkenvisuals.modules.settings.api.Bind(keyCode, false));
            }
            LinkenVisuals instance = LinkenVisuals.getInstance();
            if (instance != null && !instance.isConfigLoading()) {
                instance.getConfigManager().forceSave();
            }
        }
        bindingUi = false;
        return true;
    }

    private static String readUiBind(UI ui) {
        if (ui == null || ui.getBind() == null || ui.getBind().isEmpty()) return "Right Shift";
        // Убираем дефисы и подчёркивания из названия клавиши (RIGHT_SHIFT → RIGHT SHIFT).
        return ui.getBind().toString().replace('_', ' ').replace('-', ' ').trim();
    }

    private void drawActionButton(Matrix3x2fStack stack, float bx, float by, float bw, float bh,
                                  String label, int mouseX, int mouseY, boolean primary, Animation hoverAnim) {
        boolean hover = MathUtils.isHovered(bx, by, bw, bh, mouseX, mouseY);
        hoverAnim.update(hover);
        float hoverT = hoverAnim.getValue();
        LinkGuiChrome.drawInset(stack, bx, by, bw, bh, 7f);
        LinkGuiChrome.drawPillButton(stack, bx + 1f, by + 1f, bw - 2f, bh - 2f, 6f, 0.18f + 0.82f * hoverT);
        Color labelColor = primary ? LinkGuiThemeScope.accent() : LinkGuiThemeScope.text(Math.round(210 + 35 * hoverT));
        float size = 7.5f;
        float labelH = WindowGuiRender.textHeight(Fonts.ROUNDBOLD, size);
        WindowGuiRender.drawCenteredText(stack, Fonts.ROUNDBOLD, label,
                bx + bw / 2f, by + (bh - labelH) / 2f, labelColor, size);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Сетка тем 2×N
        float rowW = width - ROW_PAD_X * 2f;
        float cellW = (rowW - SWATCH_GAP) / 2f;
        LinkGuiThemePreset[] presets = LinkGuiThemePreset.values();
        for (int i = 0; i < presets.length; i++) {
            float cx = x + ROW_PAD_X + (i % 2) * (cellW + SWATCH_GAP);
            float cy = y + LIST_TOP + (i / 2) * (SWATCH_H + SWATCH_GAP);
            if (MathUtils.isHovered(cx, cy, cellW, SWATCH_H, (float) mouseX, (float) mouseY)) {
                context.setSelectedTheme(presets[i]);
                return true;
            }
        }

        // Секция интерфейса
        float it = interfaceTop();
        float barX = x + 14f;
        float barY = it + 42f;
        float barW = width - 28f;
        if (MathUtils.isHovered(barX, barY - 5f, barW, 12f, (float) mouseX, (float) mouseY)) {
            draggingSlider = true;
            return true;
        }
        float gap = 8f;
        float btnY = it + 56f;
        float btnH = 20f;
        float btnW = (width - 28f - gap) / 2f;
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (MathUtils.isHovered(x + 14f, btnY, btnW, btnH, (float) mouseX, (float) mouseY)) {
            if (ui != null) {
                // Применяем отложенное значение слайдера и фиксируем масштаб.
                if (pendingScale != null) setScale(ui, pendingScale);
                ui.applyScaleSetting();
            }
            pendingScale = null;
            return true;
        }
        if (MathUtils.isHovered(x + 14f + btnW + gap, btnY, btnW, btnH, (float) mouseX, (float) mouseY)) {
            if (ui != null) {
                ui.resetScaleSetting();
                UI.previewInterfaceScale(readScale(ui));
            }
            pendingScale = null;
            return true;
        }
        // GUI Style (Panel/Link) убран — клика по нему больше нет.
        if (MathUtils.isHovered(x + width - 48f, it + 83f, 34f, 16f, (float) mouseX, (float) mouseY)) {
            context.setCursorTracker(!context.isCursorTracker());
            return true;
        }
        // Ползунок прозрачности GUI.
        float trBarX = x + 14f;
        float trBarY = it + 129f;
        float trBarW = width - 28f;
        if (MathUtils.isHovered(trBarX, trBarY - 5f, trBarW, 12f, (float) mouseX, (float) mouseY)) {
            draggingTransparency = true;
            if (ui != null) {
                float clicked = MathUtils.clamp(((float) mouseX - trBarX) / trBarW, 0f, 1f);
                setTransparency(ui, 30f + clicked * 70f);
            }
            return true;
        }
        // Строка кейбинда открытия GUI — клик включает режим назначения.
        if (MathUtils.isHovered(x + 14f, it + 167f, width - 28f, 22f, (float) mouseX, (float) mouseY)) {
            bindingUi = !bindingUi;
            return true;
        }
        // HUD Style / HUD Shape — две половинки в один ряд, клик циклит значение.
        float hudGap = 8f;
        float hudHalfW = (width - 28f - hudGap) / 2f;
        if (MathUtils.isHovered(x + 14f, it + 201f, hudHalfW, 22f, (float) mouseX, (float) mouseY)) {
            EnumSetting<?> s = hudStyleSetting(ui);
            if (s != null) s.increaseEnum();
            saveConfig();
            return true;
        }
        if (MathUtils.isHovered(x + 14f + hudHalfW + hudGap, it + 201f, hudHalfW, 22f, (float) mouseX, (float) mouseY)) {
            EnumSetting<?> s = hudShapeSetting(ui);
            if (s != null) s.increaseEnum();
            saveConfig();
            return true;
        }
        // Селектор стиля GUI (Link/Panel) — клик циклит значение.
        if (MathUtils.isHovered(x + 14f, it + 253f, width - 28f, 22f, (float) mouseX, (float) mouseY)) {
            cycleGuiStyle();
            return true;
        }
        // Тумблер кастомного скорборда.
        if (MathUtils.isHovered(x + width - 48f, it + 284f, 34f, 16f, (float) mouseX, (float) mouseY)) {
            if (ui != null) {
                setCustomScoreboard(ui, !readCustomScoreboard(ui));
            }
            return true;
        }
        // Раскрытые настройки скорборда (активны только при включённом тумблере).
        if (readCustomScoreboard(ui)) {
            ru.method.linkenvisuals.hud.impl.ScoreboardHud sb = scoreboardHud();
            if (sb != null) {
                if (MathUtils.isHovered(x + 14f, it + 330f - 5f, width - 28f, 12f, (float) mouseX, (float) mouseY)) {
                    draggingSbOpacity = true;
                    sb.setOpacityValue(MathUtils.clamp(((float) mouseX - (x + 14f)) / (width - 28f), 0f, 1f) * 255f);
                    return true;
                }
                if (MathUtils.isHovered(x + 14f, it + 364f - 5f, width - 28f, 12f, (float) mouseX, (float) mouseY)) {
                    draggingSbRadius = true;
                    sb.setRadiusValue(MathUtils.clamp(((float) mouseX - (x + 14f)) / (width - 28f), 0f, 1f) * 18f);
                    return true;
                }
            }
        }
        return false;
    }

    private static void saveConfig() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    // Лениво резолвит Setting по имени и типу; кэширует ссылку. Если ui == null или не найдено — вернёт null (попробуем в следующем кадре).
    @SuppressWarnings("unchecked")
    private static <T extends Setting<?>> T resolve(UI ui, T cached, String name, Class<T> type) {
        if (cached != null) return cached;
        if (ui == null) return null;
        for (Setting<?> s : ui.getSettings()) {
            if (type.isInstance(s) && name.equals(s.getName())) return (T) s;
        }
        return null;
    }

    private BooleanSetting scoreboardSetting(UI ui) {
        return scoreboardSetting = resolve(ui, scoreboardSetting, "settings.ui.scoreboard.custom", BooleanSetting.class);
    }

    private boolean readCustomScoreboard(UI ui) {
        BooleanSetting s = scoreboardSetting(ui);
        return s != null ? s.getValue() : false;
    }

    private void setCustomScoreboard(UI ui, boolean value) {
        BooleanSetting s = scoreboardSetting(ui);
        if (s != null) s.setValue(value); // setValue сам синхронизирует ScoreboardHud
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        // Отпускание слайдера НЕ применяет масштаб — только кнопка «Apply».
        if (button == 0) {
            draggingSlider = false;
            draggingTransparency = false;
            // Слайдеры скорборда сохраняем в конфиг по отпусканию (не на каждый кадр драга).
            if (draggingSbOpacity || draggingSbRadius) {
                draggingSbOpacity = false;
                draggingSbRadius = false;
                saveConfig();
            }
        }
    }

    private NumberSetting scaleSetting(UI ui) {
        return scaleSetting = resolve(ui, scaleSetting, "settings.ui.scale", NumberSetting.class);
    }

    private float readScale(UI ui) {
        NumberSetting s = scaleSetting(ui);
        return s != null ? s.getValue() : 1f;
    }

    private void setScale(UI ui, float value) {
        NumberSetting s = scaleSetting(ui);
        if (s != null) s.setValue(value);
    }

    private NumberSetting transparencySetting(UI ui) {
        return transparencySetting = resolve(ui, transparencySetting, "settings.ui.transparency", NumberSetting.class);
    }

    private float readTransparency(UI ui) {
        NumberSetting s = transparencySetting(ui);
        return s != null ? s.getValue() : 100f;
    }

    private void setTransparency(UI ui, float value) {
        NumberSetting s = transparencySetting(ui);
        if (s != null) s.setValue(value);
    }

    // Резолвер для EnumSetting<?> (raw-тип из-за generic-границы Value; ищем по имени и instanceof).
    private static EnumSetting<?> resolveEnum(UI ui, EnumSetting<?> cached, String name) {
        if (cached != null) return cached;
        if (ui == null) return null;
        for (Setting<?> s : ui.getSettings()) {
            if (s instanceof EnumSetting<?> es && name.equals(s.getName())) return es;
        }
        return null;
    }

    private EnumSetting<?> guiStyleSetting(UI ui) {
        return guiStyleSetting = resolveEnum(ui, guiStyleSetting, "settings.ui.gui");
    }

    private void cycleGuiStyle() {
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        EnumSetting<?> s = guiStyleSetting(ui);
        if (s != null) s.increaseEnum();
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    /** Значение с анимацией переписывания: старое уезжает вверх и гаснет, новое приезжает снизу. */
    private void drawAnimatedValue(Matrix3x2fStack stack, AnimatedString anim, float tx, float ty, float size, Color color) {
        if (!anim.isAnimating()) {
            WindowGuiRender.drawText(stack, Fonts.MEDIUM, anim.getCurrent(), tx, ty, color, size);
            return;
        }
        float p = Math.max(0f, Math.min(1f, anim.progress()));
        float shift = Fonts.MEDIUM.getHeight(size) * 1.1f;
        Color prevC = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, Math.round(color.getAlpha() * (1f - p)))));
        Color currC = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, Math.round(color.getAlpha() * p))));
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, anim.getPrevious(), tx, ty - shift * p, prevC, size);
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, anim.getCurrent(), tx, ty + shift * (1f - p), currC, size);
    }

    private String readGuiStyle(UI ui) {
        EnumSetting<?> s = guiStyleSetting(ui);
        if (s == null) return "Panel";
        Object v = s.getValue();
        return v instanceof WindowGuiStyle ws ? ws.getName() : String.valueOf(v);
    }

    private void cycleBoostFps() {
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        EnumSetting<?> s = boostFpsSetting(ui);
        if (s != null) s.increaseEnum();
        saveConfig();
    }

    private EnumSetting<?> boostFpsSetting(UI ui) {
        return boostFpsSetting = resolveEnum(ui, boostFpsSetting, "settings.ui.boost_fps");
    }

    private EnumSetting<?> hudStyleSetting(UI ui) {
        return hudStyleSetting = resolveEnum(ui, hudStyleSetting, "settings.ui.hud_style");
    }

    private EnumSetting<?> hudShapeSetting(UI ui) {
        return hudShapeSetting = resolveEnum(ui, hudShapeSetting, "settings.ui.hud_shape");
    }

    private String readHudStyle(UI ui) {
        Object[] cache = {hudStyleLastEnum, hudStyleCachedText};
        String out = enumDisplay(hudStyleSetting(ui), "Aurora", cache);
        hudStyleLastEnum = cache[0];
        hudStyleCachedText = (String) cache[1];
        return out;
    }

    private String readHudShape(UI ui) {
        Object[] cache = {hudShapeLastEnum, hudShapeCachedText};
        String out = enumDisplay(hudShapeSetting(ui), "Rounded", cache);
        hudShapeLastEnum = cache[0];
        hudShapeCachedText = (String) cache[1];
        return out;
    }

    /** Локализованное имя текущего значения enum-настройки. Перевод кэшируется по значению enum:
     *  I18n.translate вызывается только когда значение сменилось (cache[0]=enum, cache[1]=строка). */
    private static String enumDisplay(EnumSetting<?> setting, String fallback, Object[] cache) {
        if (setting == null) return fallback;
        Object v = setting.getValue();
        if (v == cache[0] && cache[1] != null) return (String) cache[1];
        String result;
        if (v instanceof Nameable n) {
            String nm = n.getName();
            String tr = I18n.translate(nm);
            result = nm.equals(tr) ? nm : tr;
        } else {
            result = String.valueOf(v);
        }
        cache[0] = v;
        cache[1] = result;
        return result;
    }

    private String readBoostFps(UI ui) {
        EnumSetting<?> setting = boostFpsSetting(ui);
        if (setting == null) return "Middle";
        // boost_fps не локализуется, но значение всё равно кэшируем — избегаем лишних String.valueOf/геттеров.
        Object v = setting.getValue();
        if (v == boostFpsLastEnum && boostFpsCachedText != null) return boostFpsCachedText;
        String result = v instanceof ru.method.linkenvisuals.modules.impl.client.BoostFpsMode m
                ? m.getName() : String.valueOf(v);
        boostFpsLastEnum = v;
        boostFpsCachedText = result;
        return result;
    }
}
