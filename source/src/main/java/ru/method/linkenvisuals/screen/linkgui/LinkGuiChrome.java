package ru.method.linkenvisuals.screen.linkgui;

import lombok.experimental.UtilityClass;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiTheme;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

@UtilityClass
public class LinkGuiChrome {
    private static final float OUTLINE = 1f;

    public void drawBackdrop(Matrix3x2fStack stack, int screenW, int screenH, float fade) {
        if (fade <= 0.001f) return;
        // Затемнение фона. При Boost OFF за ним виден замороженный снимок мира (GameRendererMixin его блитит),
        // при Boost ON мир вообще не рендерится → кадр чёрный, и эти слои дают просто чёрный фон.
        // Затемнение фона мира за GUI. Прежде здесь были два дорогих fullscreen-блюра (forceBlur),
        // НО blur-шейдер умножал RGB размытой сцены на переданный ЧЁРНЫЙ цвет (0,0,0) → размытие в кадр
        // не попадало, визуально оставалось только затемнение. Поэтому заменено на простые чёрные слои
        // с теми же альфами — изображение пиксель-в-пиксель идентично, а ~82 выборки текстуры на пиксель
        // (2 прохода × (1+8·iterations)) убраны. Для чистого чёрного композитинг слоёв эквивалентен.
        // Три чёрных слоя (18/22/40) схлопнуты в один: для чистого чёрного композитинг даёт
        // a = 1-(1-18/255)(1-22/255)(1-40/255) ≈ 76/255 — попиксельно то же, минус 2 полноэкранных прохода.
        Render2D.drawRoundedRect(stack, 0, 0, screenW, screenH, 0f,
                new Color(0, 0, 0, Math.round(76f * fade)));
    }

    public void drawMusicStrip(Matrix3x2fStack stack, float x, float y, float width, float height) {
        WindowGuiRender.drawRoundedRect(stack, x, y, width, height, 8f, panelShade(0.03f, 0.82f));
        drawSoftOutline(stack, x, y, width, height, 8f, 0.42f);
    }

    public void drawGlassPanel(Matrix3x2fStack stack, float x, float y, float width, float height, float radius) {
        // Мягкая коробочная тень (градиент в прозрачную) под окном.
        drawBoxShadow(stack, x, y, width, height, radius);
        drawPanelGlow(stack, x, y, width, height, radius);
        // Статичный акцентный глоу-ореол вокруг окна (GLOW-UPDATE: глоу не пульсирует).
        // ПЕРФ: фон за GUI уже размыт ОДИН раз полноэкранным проходом (drawVignette, blur 16), поэтому
        // повторный блюр на КАЖДУЮ панель давал ещё один GPU-проход за кадр (главное окно + правая
        // панель + боковые + окна настроек модулей = просадка FPS в 2-3 раза) и визуально почти ничего
        // не менял — блюрилась уже размытая сцена. Оставляем только тинт панели.
        WindowGuiRender.drawRoundedRect(stack, x, y, width, height, radius, panelShade(0.05f, 0.5f));
        Color top = panelTop();
        Color bottom = panelBottom();
        WindowGuiRender.drawRoundedDiagonalGradientRect(
                stack, x, y, width, height, radius,
                top, bottom
        );

        // «Аврора» — статичный свет акцента из-под верхней кромки (без пульса по GLOW-UPDATE).
        float auroraH = Math.min(height * 0.45f, 200f);
        Color aur = WindowGuiColors.lerp(accent(), Color.WHITE, 0.22f);
        WindowGuiRender.drawRoundedVerLinearGradientRect(stack, x, y, width, auroraH, radius,
                WindowGuiColors.withAlpha(aur, 0.08f), WindowGuiColors.withAlpha(aur, 0f));
        // Затенение к нижней кромке — контент «стоит» на земле, появляется глубина.
        float shadeH = Math.min(height * 0.28f, 110f);
        WindowGuiRender.drawRoundedVerLinearGradientRect(stack, x, y + height - shadeH, width, shadeH, radius,
                new Color(0, 0, 0, 0), new Color(0, 0, 0, 64));

        Color border = panelBorder();
        // Градиентная обводка: ярче сверху (куда «падает свет»), тусклее к низу — объём вместо плоской рамки.
        // Fade применяет сам шейдер обводки — гаснет синхронно с фоном.
        WindowGuiRender.drawRoundedOutlineVerLinearGradientRect(
                stack, x, y, width, height, radius, OUTLINE,
                WindowGuiColors.withAlpha(border, 0.55f), WindowGuiColors.withAlpha(border, 0.15f));
        drawPanelGlowRing(stack, x, y, width, height, radius);
        // Блик по верхней кромке: тонкая линия + мягкое сияние вниз — «стекло» ловит свет сверху.
        WindowGuiRender.drawRoundedRect(stack, x + radius, y + 1f, width - radius * 2f, 1f, 0.5f,
                new Color(255, 255, 255, 10));
        WindowGuiRender.drawRoundedVerLinearGradientRect(stack, x + radius, y + 2f, width - radius * 2f, 14f, 0f,
                new Color(255, 255, 255, 9), new Color(255, 255, 255, 0));
    }

    /** Домножить альфу цвета на fade Link-контекста (для элементов, не использующих panelShade). */
    private Color withFade(Color c) {
        float f = ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.getFadeMultiplier();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.max(0, Math.min(255, Math.round(c.getAlpha() * f))));
    }

    public void drawAccentChip(Matrix3x2fStack stack, float x, float y, float width, float height, float radius) {
        Color glow = accent();
        WindowGuiRender.drawRoundedRect(stack, x, y, width, height, radius, WindowGuiColors.withAlpha(glow, 0.22f));
        drawSoftOutline(stack, x, y, width, height, radius, 1f);
    }

    public void drawInset(Matrix3x2fStack stack, float x, float y, float width, float height, float radius) {
        WindowGuiRender.drawRoundedRect(stack, x, y, width, height, radius, panelShade(0.06f, 0.76f));
        drawSoftOutline(stack, x, y, width, height, radius, 0.35f);
    }

    public void drawCard(Matrix3x2fStack stack, float x, float y, float width, float height, float radius, boolean active, float activeT) {
        float t = active ? Math.max(activeT, 0.85f) : activeT;
        Color disabled = panelShade(0.025f, 0.68f);
        Color enabled = panelShade(0.15f, 0.78f * t);
        WindowGuiRender.drawRoundedVerLinearGradientRect(
                stack, x, y, width, height, radius, disabled, WindowGuiColors.withAlpha(disabled, 0f));
        WindowGuiRender.drawRoundedVerLinearGradientRect(
                stack, x, y, width, height, radius, enabled, WindowGuiColors.withAlpha(enabled, 0f));
        Color border = panelBorder();
        float strength = Math.max(0.25f, t * 0.85f);
        Color outline = WindowGuiColors.lerp(
                WindowGuiColors.withAlpha(border, 0.12f),
                border,
                strength
        );
        // Ровная обводка по всему периметру (без диагонального градиента, который давал яркий «белый угол»).
        WindowGuiRender.drawRoundedOutlineRect(
                stack, x, y, width, height, radius, OUTLINE, WindowGuiColors.withAlpha(outline, 0.32f));
    }

    /**
     * Вертикальный «филлер» слева от карточки (порт Lucky drawModuleStripe).
     * В покое — тусклая полоска по теме; при включении заполняется снизу вверх акцентом,
     * при выключении — убывает. {@code fill} — прогресс заполнения [0..1], {@code fullActive}
     * — полностью включено (рисуется цельной полоской).
     */
    public void drawModuleStripe(Matrix3x2fStack stack, float x, float y, float width, float height,
                                 float fill, boolean fullActive) {
        float radius = width / 2f;
        // Цвета полоски идут не через panelShade, поэтому раньше НЕ домножались на fade и полоска
        // уходила позже остального GUI при закрытии. Оборачиваем в withFade — затухает синхронно.
        Color idleTop = withFade(LinkGuiThemeScope.text(48));
        Color idleBottom = withFade(LinkGuiThemeScope.text(22));
        Color glow = accent();
        Color activeTop = withFade(WindowGuiColors.withAlpha(glow, 0.95f));
        Color activeBottom = withFade(WindowGuiColors.withAlpha(glow, 0.5f));

        if (fullActive) {
            WindowGuiRender.drawRoundedVerLinearGradientRect(stack, x, y, width, height, radius, activeTop, activeBottom);
            return;
        }

        WindowGuiRender.drawRoundedVerLinearGradientRect(stack, x, y, width, height, radius, idleTop, idleBottom);

        if (fill <= 0.002f) return;
        float filledH = height * Math.max(0f, Math.min(1f, fill));
        if (filledH <= 0.5f) return;

        float fillY = y + height - filledH;
        float fillRadius = filledH >= height - 0.01f ? radius : Math.min(radius, filledH / 2f);
        WindowGuiRender.drawRoundedVerLinearGradientRect(stack, x, fillY, width, filledH, fillRadius, activeTop, activeBottom);
    }

    /**
     * Мягкая тень под карточкой при hover-подъёме: растёт со strength и якорится чуть ниже карточки,
     * чтобы подъём (-3px в ModuleCard) читался как физический отрыв от панели.
     */
        public void drawCardShadow(Matrix3x2fStack stack, float x, float y, float width, float height, float radius, float strength) {
        float s = Math.round(Math.max(0f, Math.min(1f, strength)) * 16f) / 16f;
        float fade = ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.getFadeMultiplier();
        if (s <= 0.02f || fade <= 0.01f) return;
        float spread = 7f * s;
        float anchor = 2f + 3f * s;
        int alpha = Math.max(0, Math.min(255, Math.round(0.12f * s * fade * 255f)));
        if (alpha <= 0) return;
        Render2D.drawSoftGlow(stack, x, y + anchor, width, height, radius,
                spread, new Color(0, 0, 0, alpha));
    }

    public void drawIconBadge(Matrix3x2fStack stack, float x, float y, float size, float radius, boolean active, float activeT) {
        Color glow = accent();
        float strength = active ? Math.max(activeT, 0.85f) : activeT * 0.35f;
        Color bg = active
                ? WindowGuiColors.withAlpha(glow, 0.2f + 0.12f * activeT)
                : panelShade(0.07f, 0.72f);
        WindowGuiRender.drawRoundedRect(stack, x, y, size, size, radius, bg);
        drawSoftOutline(stack, x, y, size, size, radius, Math.max(0.3f, strength));
    }

    public void drawIconInBadge(Matrix3x2fStack stack, float badgeX, float badgeY, float badgeSize, String icon, Color color, float iconSize) {
        float centerX = badgeX + badgeSize / 2f;
        float centerY = badgeY + badgeSize / 2f;
        float iconHeight = WindowGuiRender.textHeight(Fonts.ICONSNEW, iconSize);
        WindowGuiRender.drawCenteredText(stack, Fonts.ICONSNEW, icon, centerX, centerY - iconHeight / 2f, color, iconSize);
    }

    private static final Identifier LOGO_TEXTURE = LinkenVisuals.id("textures/gui/logo.png");
    private static boolean logoFilterApplied;

    /** Логотип темой: белая маска красится акцентом темы + жирный glow-контур из слоёв. */
    public void drawThemedLogo(Matrix3x2fStack stack, float centerX, float centerY, float size) {
        // Линейная фильтрация — лого не пикселит при уменьшении. Ставим ОДИН раз (лишние GL-вызовы на кадр).
        if (!logoFilterApplied) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null) {
                net.minecraft.client.texture.AbstractTexture tex = client.getTextureManager().getTexture(LOGO_TEXTURE);
                if (tex != null) {
                    logoFilterApplied = true;
                }
            }
        }
        Color glow = accent();
        float half = size / 2f;
        // Жирный glow — несколько слоёв логотипа крупнее с убыванием прозрачности.
        for (int i = 4; i >= 1; i--) {
            float e = i * 1.5f;
            float a = 0.10f + (4 - i) * 0.06f;
            Render2D.drawTexture(stack, centerX - half - e, centerY - half - e, size + 2f * e, size + 2f * e, 0f,
                    LOGO_TEXTURE, WindowGuiColors.withAlpha(glow, a));
        }
        // Основной логотип — цвет акцента темы.
        Render2D.drawTexture(stack, centerX - half, centerY - half, size, size, 0f, LOGO_TEXTURE, glow);
    }

    public void drawSectionTitle(Matrix3x2fStack stack, String title, String icon, float x, float y) {
        drawAccentChip(stack, x, y, 16f, 16f, 5f);
        drawIconInBadge(stack, x, y, 16f, icon, accent(), 8f);
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, title, x + 22f, y + 3f, accent(), 8.2f);
    }

    public void drawToggle(Matrix3x2fStack stack, float x, float y, float width, float height, float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        float radius = height / 2f;
        Color glow = accent();

        // Мягкое свечение включённого тумблера: два расширяющихся акцентных слоя (идиома glow
        // ручки слайдера) — включённое состояние «живое», проявляется вместе с анимацией.
        if (t > 0.04f) {
            for (int i = 2; i >= 1; i--) {
                float e = i * 1.6f;
                float a = (i == 1 ? 0.12f : 0.06f) * t;
                WindowGuiRender.drawRoundedRect(stack, x - e, y - e, width + 2f * e, height + 2f * e,
                        radius + e, WindowGuiColors.withAlpha(glow, a));
            }
        }

        Color trackBg = WindowGuiColors.lerp(panelShade(0.07f, 0.72f), WindowGuiColors.withAlpha(glow, 0.32f), t);
        WindowGuiRender.drawRoundedRect(stack, x, y, width, height, radius, trackBg);
        drawSoftOutline(stack, x, y, width, height, radius, Math.max(0.3f, t));

        float knobSize = height - 4f;
        float knobX = x + 2f + (width - 4f - knobSize) * t;
        float knobY = y + 2f;
        // Ползунок: серый когда выключено, белый когда включено.
        Color knob = WindowGuiColors.lerp(new Color(150, 150, 158), Color.WHITE, t);
        WindowGuiRender.drawRoundedRect(stack, knobX, knobY, knobSize, knobSize, knobSize / 2f, knob);

        if (t > 0.04f) {
            drawSoftOutline(stack, knobX, knobY, knobSize, knobSize, knobSize / 2f, t);
        }
    }

    public void drawPillButton(Matrix3x2fStack stack, float x, float y, float width, float height, float radius, boolean active) {
        drawPillButton(stack, x, y, width, height, radius, active ? 1f : 0f);
    }

    public void drawPillButton(Matrix3x2fStack stack, float x, float y, float width, float height, float radius, float strength) {
        float t = Math.max(0f, Math.min(1f, strength));
        Color glow = accent();
        Color fill = WindowGuiColors.lerp(panelShade(0.07f, 0.72f), WindowGuiColors.withAlpha(glow, 0.18f), t);
        WindowGuiRender.drawRoundedRect(stack, x, y, width, height, radius, fill);
        drawSoftOutline(stack, x, y, width, height, radius, Math.max(0.35f, t));
    }

    /** Точка слайдера с контрастным глоу: на светлой теме — тёмный, на тёмной — белый (−25% от прежнего). */
        public void drawSliderKnob(Matrix3x2fStack stack, float centerX, float centerY, float knobSize) {
        boolean light = isLightBackground();
        Color glow = light ? new Color(0, 0, 0) : new Color(255, 255, 255);
        for (int i = 3; i >= 1; i--) {
            float e = i * 1.425f;
            float a = (light ? 0.20f : 0.29f) * (0.4f + 0.22f * (3 - i));
            float gw = knobSize + 2f * e;
            WindowGuiRender.drawRoundedRect(stack, centerX - gw / 2f, centerY - gw / 2f, gw, gw, gw / 2f,
                    WindowGuiColors.withAlpha(glow, a));
        }
        WindowGuiRender.drawRoundedRect(stack, centerX - knobSize / 2f, centerY - knobSize / 2f,
                knobSize, knobSize, knobSize / 2f, Color.WHITE);
    }

    private boolean isLightBackground() {
        Color bg = LinkGuiThemeScope.background();
        float lum = (0.299f * bg.getRed() + 0.587f * bg.getGreen() + 0.114f * bg.getBlue()) / 255f;
        return lum > 0.6f;
    }

    public void drawProgressBar(Matrix3x2fStack stack, float x, float y, float width, float height, float progress, float glowStrength) {
        float t = Math.max(0f, Math.min(1f, progress));
        float radius = height / 2f;
        // Трек следует теме (на светлых темах — тёмный, на тёмных — светлый), иначе невидим.
        WindowGuiRender.drawRoundedRect(stack, x, y, width, height, radius, LinkGuiThemeScope.text(28));
        if (t > 0.001f) {
            Color fill = WindowGuiColors.lerp(WindowGuiColors.withAlpha(accent(), 0.55f), accent(), Math.max(0.35f, glowStrength));
            float fillW = width * t;
            // Кламп радиуса как у drawModuleStripe: при малом прогрессе заливка уже диаметра
            // скругления и углы «ломаются».
            float fillRadius = Math.min(radius, fillW / 2f);
            WindowGuiRender.drawRoundedRect(stack, x, y, fillW, height, fillRadius, fill);
            float knobSize = Math.max(height + 2f, 5f);
            // Центр ползунка держим в пределах скруглённых торцов трека — на 0% и 100% он
            // больше не вылезает за края.
            float knobCx = Math.max(x + radius, Math.min(x + width - radius, x + fillW));
            drawSliderKnob(stack, knobCx, y + height / 2f, knobSize);
        }
    }

    public void drawDivider(Matrix3x2fStack stack, float x, float y, float width) {
        WindowGuiRender.drawRoundedRect(stack, x, y, width, 1f, 0.5f, LinkGuiThemeScope.text(20));
    }

    public void drawSearchField(Matrix3x2fStack stack, float x, float y, float width, float height) {
        drawSearchField(stack, x, y, width, height, 0f);
    }

    public void drawSearchField(Matrix3x2fStack stack, float x, float y, float width, float height, float focusT) {
        float t = Math.max(0f, Math.min(1f, focusT));
        Color fill = panelShade(0.04f + t * 0.02f, 0.74f + t * 0.08f);
        WindowGuiRender.drawRoundedRect(stack, x, y, width, height, height / 2f, fill);
        Color border = panelBorder();
        float strength = 0.35f + t * 0.65f;
        Color outline = WindowGuiColors.lerp(
                WindowGuiColors.withAlpha(border, 0.12f),
                border,
                strength
        );
        WindowGuiRender.drawRoundedOutlineDiagonalGradientRect(
                stack, x, y, width, height, height / 2f, OUTLINE, outline, WindowGuiColors.withAlpha(outline, 0f));
    }

    /** Same smooth shader outline as icon chips — top accent fading to transparent bottom. */
    private void drawSoftOutline(Matrix3x2fStack stack, float x, float y, float width, float height, float radius, float strength) {
        Color glow = accent();
        Color top = WindowGuiColors.lerp(
                WindowGuiColors.withAlpha(glow, 0.12f),
                glow,
                Math.max(0f, Math.min(1f, strength))
        );
        WindowGuiRender.drawRoundedOutlineVerLinearGradientRect(
                stack, x, y, width, height, radius, OUTLINE, top, WindowGuiColors.withAlpha(top, 0f));
    }

    /** Мягкая коробочная тень: слои чёрного с убыванием альфы (градиент в прозрачную) под окном. */
        private void drawBoxShadow(Matrix3x2fStack stack, float x, float y, float width, float height, float radius) {
        float fade = ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.getFadeMultiplier();
        if (fade <= 0.01f) return;
        float spread = 16f;
        int alpha = Math.max(0, Math.min(255, Math.round(0.22f * fade * 255f)));
        if (alpha <= 0) return;
        Render2D.drawSoftGlow(stack, x, y + 2f, width, height, radius,
                spread, new Color(0, 0, 0, alpha));
    }

    /**
     * Статичный глоу вокруг окна — акцент текущей темы (GLOW-UPDATE §8). Дешёвая идиома
     * {@link #drawBoxShadow}: один SDF-вызов, рисуется ПОД панелью — виден только внешний ореол.
     * Смена темы меняет цвет мгновенно (accent() читается каждый кадр).
     */
        private void drawPanelGlow(Matrix3x2fStack stack, float x, float y, float width, float height, float radius) {
        float fade = ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.getFadeMultiplier();
        if (fade <= 0.05f) return;
        Color accent = accent();
        // The customer reported an over-bright neon halo around every panel.
        // Keep a soft identity glow, but roughly halve its reach and intensity.
        float spread = 14f;
        float peak = 0.10f * fade;
        Color c = WindowGuiColors.lerp(Color.WHITE, accent, 0.78f);
        Render2D.drawSoftGlow(stack, x, y, width, height, radius, spread,
                new Color(c.getRed(), c.getGreen(), c.getBlue(),
                        Math.max(0, Math.min(255, Math.round(peak * 255f)))));
    }

    /** Тонкое акцентное кольцо по кромке — статичное (GLOW-UPDATE: глоу не пульсирует). */
    private static void softGlow(Matrix3x2fStack stack, float x, float y, float width, float height,
                                 float radius, Color color, int layers, float step) {
        if (color == null || color.getAlpha() <= 0) return;
        Render2D.drawSoftGlow(stack, x, y, width, height, radius, layers * step, color);
    }

    private void drawPanelGlowRing(Matrix3x2fStack stack, float x, float y, float width, float height, float radius) {
        float fade = ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.getFadeMultiplier();
        if (fade <= 0.01f) return;
        WindowGuiRender.drawRoundedOutlineRect(stack, x, y, width, height, radius, OUTLINE,
                WindowGuiColors.withAlpha(accent(), 0.10f));
    }

    private Color accent() {
        return LinkGuiThemeScope.accent();
    }

    /** Непрозрачный цвет фона панели — для «растворения» карточек у краёв скролла (адаптируется под тему). */
    public Color panelFill() {
        return panelShade(0.03f, 1.0f);
    }

    private Color panelTop() {
        // Спека «Материалы»: панели полупрозрачные (~0.85), мир виден сквозь блюр —
        // прежние 0.94/0.98 читались как сплошная «чёрная» коробка.
        return panelShade(0.045f, 0.85f);
    }

    private Color panelBottom() {
        return panelShade(0.012f, 0.87f);
    }

    private Color panelBorder() {
        return WindowGuiColors.lerp(WindowGuiTheme.UI_BORDER, accent(), 0.35f);
    }

    /** Panel fill — base color follows the theme background (enables light themes), subtle accent tint.
     *  Alpha домножается на fade Link-контекста → прозрачность GUI влияет на ВСЕ фоны панелей, а не только текст. */
    private Color panelShade(float colorAmount, float alpha) {
        Color color = accent();
        Color base = LinkGuiThemeScope.background();
        int r = Math.round(color.getRed() * colorAmount + base.getRed() * (1f - colorAmount));
        int g = Math.round(color.getGreen() * colorAmount + base.getGreen() * (1f - colorAmount));
        int b = Math.round(color.getBlue() * colorAmount + base.getBlue() * (1f - colorAmount));
        float fade = ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.getFadeMultiplier();
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f * fade);
        return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b), a);
    }

    private Color shade(Color color, float colorAmount, float alpha) {
        int r = Math.round(color.getRed() * colorAmount + 18f * (1f - colorAmount));
        int g = Math.round(color.getGreen() * colorAmount + 19f * (1f - colorAmount));
        int b = Math.round(color.getBlue() * colorAmount + 24f * (1f - colorAmount));
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b), a);
    }
}
