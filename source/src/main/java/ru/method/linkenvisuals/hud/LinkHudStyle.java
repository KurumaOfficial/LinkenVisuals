package ru.method.linkenvisuals.hud;

import net.minecraft.client.util.math.MatrixStack;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeManager;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemePreset;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

/**
 * Единый «Link GUI» вид для элементов HUD: тёмная/светлая панель в цветах выбранной темы с акцентной
 * обводкой (как окна Link GUI). Несколько вариантов оформления ({@link Variant}) переключаются одной
 * настройкой UI («HUD Style»), так что все элементы (TargetHUD, PotionHUD, ArmorHUD и т.д.) меняют
 * облик синхронно. Содержимое (текст/иконки) остаётся; меняется «оболочка» и палитра.
 *
 * <p>ВАЖНО: цвета берутся из выбранного пресета темы напрямую ({@code preset.background()/text()}),
 * а НЕ из {@code ColorUtils.getBackgroundColor()} — последний хранит светлую «стеклобазу» для тинта
 * Link GUI, из-за чего HUD получался светлым на тёмной теме.
 */
public final class LinkHudStyle {
    private LinkHudStyle() {
    }

    /** Варианты оформления панели HUD (выбираются настройкой UI). */
    public enum Variant implements Nameable {
        /** Card: точный вид карточек Link GUI — тёмное стекло темы (фон + примесь акцента),
         *  аврора акцента из-под верхней кромки, нижнее затенение, тонкая рамка, стеклянный блик.
         *  Родной для интерфейса, флагманский вид. */
        Card("settings.ui.hud_style.card"),
        /** Минимал-премиум: почти без панели — еле тёмная подложка + акцентная риска слева. */
        Minimal("settings.ui.hud_style.minimal"),
        /** Неон: плотное тёмное стекло + ярко светящаяся акцентная кромка и мягкий ореол. */
        Neon("settings.ui.hud_style.neon"),
        /** Премиум: градиент + верхний блик + акцентная боковая полоса + мягкое свечение обводки. */
        Aurora("settings.ui.hud_style.aurora"),
        /** Матовое стекло: вертикальный градиент фона темы + мягкая акцентная обводка. */
        Glass("settings.ui.hud_style.glass"),
        /** Плоская заливка фоном темы + тонкая акцентная рамка. */
        Solid("settings.ui.hud_style.solid"),
        /** Градиент + тонкая акцентная полоса сверху (primary→secondary). */
        Gradient("settings.ui.hud_style.gradient"),
        /** Минимал: почти прозрачный фон + выразительная акцентная обводка. */
        Outline("settings.ui.hud_style.outline");
        // Вариант Field (цельный visual-test HUD) удалён по требованию: у кого он стоял в конфиге,
        // setEnumValue молча не найдёт имя и оставит дефолт Card. Карточные куски VisualHud
        // (cardEffects/cardArmor/...) живы — их использует стиль Card.

        private final String name;

        Variant(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /** Форма (скругление) панелей HUD — выбирается настройкой UI «Форма HUD». */
    public enum Shape implements Nameable {
        /** Умеренное скругление (~7px) — мягкий прямоугольник. */
        Rounded("settings.ui.hud_shape.rounded"),
        /** Полное скругление по высоте — «пилюля»/капсула. */
        Pill("settings.ui.hud_shape.pill"),
        /** Почти острые углы (~2px) — строгий прямоугольник. */
        Sharp("settings.ui.hud_shape.sharp");

        private final String name;

        Shape(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /** Текущая выбранная форма панелей HUD. */
    public static Shape shape() {
        return UI.getHudShape();
    }

    /**
     * Радиус скругления панели для выбранной формы. Считается по МЕНЬШЕЙ стороне, иначе на узких
     * многострочных панелях (клавиши/эффекты/кулдауны) «Пилюля» давала радиус больше половины ширины
     * → drawRoundedRect вырождался и панель выглядела сломанной/маленькой.
     */
    public static float radius(float width, float height) {
        // ЕДИНЫЙ уровень сглаживания для всех элементов (НЕ зависит от их размера) — углы одинаковы
        // везде, как у TargetHUD (round=5). Ограничиваем половиной меньшей стороны, иначе у совсем
        // тонких баров скругление вырождается.
        float cap = Math.max(0f, Math.min(width, height)) * 0.5f;
        Shape s = shape();
        float r = switch (s == null ? Shape.Rounded : s) {
            case Sharp -> 3f;
            case Pill -> 10f;
            default -> 5f; // «Скруглённая» — ровно как TargetHUD
        };
        return Math.min(r, cap);
    }

    /** Активен ли стиль Link GUI (тогда HUD оформляется под него). */
    public static boolean active() {
        return UI.isLinkGuiStyle();
    }

    /** Текущий выбранный вариант оформления HUD. */
    public static Variant variant() {
        return UI.getHudVariant();
    }

    // --- Палитра темы (тёмная/светлая адаптация) ---

    /** Базовый цвет фона выбранной темы (тёмный для тёмных тем, светлый для светлых). */
    public static Color backgroundBase() {
        LinkGuiThemePreset preset = LinkGuiThemeManager.getSelected();
        return preset != null ? preset.background() : ColorUtils.getBackgroundColor();
    }

    /** Базовый цвет текста выбранной темы. */
    public static Color textBase() {
        LinkGuiThemePreset preset = LinkGuiThemeManager.getSelected();
        return preset != null ? preset.text() : ColorUtils.getTextColor();
    }

    /** Акцентный цвет темы. */
    public static Color accent() {
        return ColorUtils.getPrimaryColor();
    }

    /** Вторичный акцент темы. */
    public static Color accentSecondary() {
        return ColorUtils.getSecondaryColor();
    }

    /** Светлая ли тема (для выбора направления контраста подложек). */
    public static boolean isLightTheme() {
        Color bg = backgroundBase();
        return luminance(bg) > 0.6f;
    }

    public static Color background(int alpha) {
        return ColorUtils.alpha(backgroundBase(), clampAlpha(alpha));
    }

    public static Color text(int alpha) {
        return ColorUtils.alpha(textBase(), clampAlpha(alpha));
    }

    /** Приглушённый текст (подписи/время) — текст темы, смешанный с фоном. */
    public static Color textSecondary(int alpha) {
        Color faded = ColorUtils.mix(textBase(), backgroundBase(), 0.45f);
        return ColorUtils.alpha(faded, clampAlpha(alpha));
    }

    public static Color accent(int alpha) {
        return ColorUtils.alpha(accent(), clampAlpha(alpha));
    }

    /** Стекло панели в идиоме Link GUI: фон темы с примесью акцента ({@code colorAmount}), заданная альфа.
     *  Совпадает с {@code panelShade} из LinkGuiChrome — HUD-панели выглядят как родные карточки GUI. */
    private static Color hudShade(float colorAmount, int alpha) {
        Color acc = accent();
        Color b = backgroundBase();
        int r = Math.round(acc.getRed() * colorAmount + b.getRed() * (1f - colorAmount));
        int g = Math.round(acc.getGreen() * colorAmount + b.getGreen() * (1f - colorAmount));
        int bl = Math.round(acc.getBlue() * colorAmount + b.getBlue() * (1f - colorAmount));
        return new Color(clampChannel(r), clampChannel(g), clampChannel(bl), clampAlpha(alpha));
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Контрастная подложка ячейки (слот предмета/голова): темнее на светлой теме, светлее на тёмной. */
    public static Color inset(int alpha) {
        Color bg = backgroundBase();
        Color mixed = ColorUtils.mix(bg, isLightTheme() ? Color.BLACK : Color.WHITE, 0.16f);
        return ColorUtils.alpha(mixed, clampAlpha(alpha));
    }

    // --- Панели ---

    /** Панель текущего варианта. {@code alpha} 0..1 — общий множитель прозрачности (анимации). */
    public static void panel(Object stack, float x, float y, float width, float height, float radius, float alpha) {
        panel(variant(), stack, x, y, width, height, radius, alpha);
    }

    public static void panel(Variant variant, Object stack, float x, float y, float width, float height, float radius, float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha));
        Color base = backgroundBase();
        Color accent = accent();

        switch (variant == null ? Variant.Card : variant) {
            case Card -> {
                // Точный вид карточек Link GUI. Мягкая ТЁМНАЯ тень (не акцентный глоу — спокойно,
                // как у карточек интерфейса): панель мягко парит над миром.
                Render2D.drawBorder(stack, x - 6f, y - 6f + 1.5f, width + 12f, height + 12f, radius + 6f, 1.5f, 6f,
                        ColorUtils.alpha(Color.BLACK, Math.round(46f * a)));
                // Стеклянная заливка: фон темы с примесью акцента, вертикальный градиент (сверху чуть светлее) —
                // ровно как panelShade в LinkGuiChrome, поэтому HUD выглядит как родные карточки GUI.
                Render2D.drawRoundedVerticalGradient(stack, x, y, width, height, radius,
                        hudShade(0.05f, Math.round(238f * a)), hudShade(0.012f, Math.round(248f * a)));
                // Аврора акцента из-под верхней кромки (живой свет темы, не плоская коробка).
                Color aur = ColorUtils.mix(accent, Color.WHITE, 0.22f);
                float auroraH = Math.min(height * 0.5f, 15f);
                Render2D.drawRoundedVerticalGradient(stack, x, y, width, auroraH, radius,
                        ColorUtils.alpha(aur, Math.round(30f * a)), ColorUtils.alpha(aur, 0));
                // Нижнее затенение — контент «стоит на земле», появляется глубина.
                float shadeH = Math.min(height * 0.32f, 10f);
                Render2D.drawRoundedVerticalGradient(stack, x, y + height - shadeH, width, shadeH, radius,
                        new Color(0, 0, 0, 0), ColorUtils.alpha(Color.BLACK, Math.round(58f * a)));
                // Рамка: нейтральная с примесью акцента (как panelBorder GUI), спокойная.
                Color border = ColorUtils.mix(
                        ColorUtils.mix(base, isLightTheme() ? Color.BLACK : Color.WHITE, 0.14f), accent, 0.35f);
                Render2D.drawBorder(stack, x, y, width, height, radius, 1.0f, 1.6f,
                        ColorUtils.alpha(border, Math.round(120f * a)));
                // Тонкий стеклянный блик по верхней кромке — «стекло» ловит свет сверху.
                Render2D.drawRoundedRect(stack, x + radius, y + 1f, width - radius * 2f, 1f, 0.5f,
                        ColorUtils.alpha(Color.WHITE, Math.round(16f * a)));
            }
            case Minimal -> {
                // Премиум-минимал: почти без панели. Еле заметная тёмная подложка (только ради
                // читаемости текста поверх ярких сцен) + акцентная риска слева (primary→secondary).
                float r = Math.min(radius, 4f);
                Render2D.drawRoundedRect(stack, x, y, width, height, r, hudShade(0.02f, Math.round(120f * a)));
                float barW = 2.2f;
                float barInset = Math.min(height * 0.18f, radius + 1f);
                Render2D.drawRoundedVerticalGradient(stack, x + 2f, y + barInset, barW, height - barInset * 2f,
                        barW * 0.5f,
                        ColorUtils.alpha(accent, Math.round(235f * a)),
                        ColorUtils.alpha(accentSecondary(), Math.round(235f * a)));
            }
            case Neon -> {
                // Стекло + неон-кромка: мягкий акцентный ореол наружу (неон «светит»).
                Render2D.drawBorder(stack, x - 5f, y - 5f, width + 10f, height + 10f, radius + 5f, 1.5f, 5f,
                        ColorUtils.alpha(accent, Math.round(72f * a)));
                // Плотная тёмная стеклянная заливка.
                Render2D.drawRoundedVerticalGradient(stack, x, y, width, height, radius,
                        hudShade(0.02f, Math.round(236f * a)), hudShade(0.06f, Math.round(248f * a)));
                // Стеклянный блик сверху.
                Render2D.drawRoundedRect(stack, x + radius, y + 1.2f, width - radius * 2f, 1f, 0.5f,
                        ColorUtils.alpha(Color.WHITE, Math.round(22f * a)));
                // Яркая неон-кромка акцентом.
                Render2D.drawBorder(stack, x, y, width, height, radius, 1.0f, 2.0f,
                        ColorUtils.alpha(accent, Math.round(220f * a)));
                // Внутренняя тонкая акцентная линия — усиливает «неон».
                Render2D.drawBorder(stack, x + 1.2f, y + 1.2f, width - 2.4f, height - 2.4f,
                        Math.max(0f, radius - 1.2f), 1.0f, 1.2f, ColorUtils.alpha(accent, Math.round(60f * a)));
            }
            case Aurora -> {
                // Фон: мягкий вертикальный градиент темы.
                Color top = ColorUtils.alpha(ColorUtils.mix(base, Color.WHITE, 0.07f), Math.round(236f * a));
                Color bottom = ColorUtils.alpha(ColorUtils.mix(base, Color.BLACK, 0.34f), Math.round(248f * a));
                Render2D.drawRoundedVerticalGradient(stack, x, y, width, height, radius, top, bottom);
                // Верхний блик (стеклянный «sheen»).
                float sheenH = Math.min(height * 0.30f, 6f);
                Render2D.drawRoundedRect(stack, x + radius * 0.6f, y + 0.8f, width - radius * 1.2f, sheenH,
                        radius * 0.5f, ColorUtils.alpha(Color.WHITE, Math.round(26f * a)));
                // Фирменная акцентная боковая полоса слева (primary→secondary).
                float barW = 2.2f;
                Render2D.drawRoundedVerticalGradient(stack, x + 1.6f, y + radius * 0.55f, barW, height - radius * 1.1f,
                        barW * 0.5f, ColorUtils.alpha(accent, Math.round(240f * a)),
                        ColorUtils.alpha(accentSecondary(), Math.round(240f * a)));
                // Мягкое акцентное свечение по контуру.
                Render2D.drawBorder(stack, x, y, width, height, radius, 1.1f, 2.6f,
                        ColorUtils.alpha(accent, Math.round(125f * a)));
            }
            case Solid -> {
                // Угловатая форма: почти острые углы.
                float rad = Math.min(radius, 2.2f);
                Color fill = ColorUtils.alpha(ColorUtils.mix(base, Color.BLACK, 0.10f), Math.round(246f * a));
                Render2D.drawRoundedRect(stack, x, y, width, height, rad, fill);
                Render2D.drawBorder(stack, x, y, width, height, rad, 1.0f, 2.0f,
                        ColorUtils.alpha(accent, Math.round(135f * a)));
            }
            case Gradient -> {
                Color top = ColorUtils.alpha(ColorUtils.mix(base, Color.WHITE, 0.06f), Math.round(236f * a));
                Color bottom = ColorUtils.alpha(ColorUtils.mix(base, Color.BLACK, 0.30f), Math.round(248f * a));
                Render2D.drawRoundedVerticalGradient(stack, x, y, width, height, radius, top, bottom);
                // Тонкая акцентная полоса сверху (primary→secondary) — фирменный штрих.
                float stripeH = Math.min(2.5f, height * 0.12f);
                Render2D.drawRoundedHorizontalGradient(stack, x + radius * 0.5f, y, width - radius, stripeH,
                        stripeH * 0.5f,
                        ColorUtils.alpha(accent, Math.round(220f * a)),
                        ColorUtils.alpha(accentSecondary(), Math.round(220f * a)));
                Render2D.drawBorder(stack, x, y, width, height, radius, 1.0f, 2.0f,
                        ColorUtils.alpha(accent, Math.round(95f * a)));
            }
            case Outline -> {
                // Техно-минимал: полупрозрачный фон + уголки-скобки в углах + тонкая обводка.
                Color fill = ColorUtils.alpha(ColorUtils.mix(base, Color.BLACK, 0.12f), Math.round(140f * a));
                Render2D.drawRoundedRect(stack, x, y, width, height, radius, fill);
                Color br = ColorUtils.alpha(accent, Math.round(235f * a));
                float L = Math.min(width, height) * 0.30f;
                float t = 1.4f;
                Render2D.drawRoundedRect(stack, x, y, L, t, t * 0.5f, br);
                Render2D.drawRoundedRect(stack, x, y, t, L, t * 0.5f, br);
                Render2D.drawRoundedRect(stack, x + width - L, y, L, t, t * 0.5f, br);
                Render2D.drawRoundedRect(stack, x + width - t, y, t, L, t * 0.5f, br);
                Render2D.drawRoundedRect(stack, x, y + height - t, L, t, t * 0.5f, br);
                Render2D.drawRoundedRect(stack, x, y + height - L, t, L, t * 0.5f, br);
                Render2D.drawRoundedRect(stack, x + width - L, y + height - t, L, t, t * 0.5f, br);
                Render2D.drawRoundedRect(stack, x + width - t, y + height - L, t, L, t * 0.5f, br);
                Render2D.drawBorder(stack, x, y, width, height, radius, 1.0f, 2.0f,
                        ColorUtils.alpha(accent, Math.round(80f * a)));
            }
            default -> { // Glass
                Color top = ColorUtils.alpha(ColorUtils.mix(base, Color.WHITE, 0.05f), Math.round(232f * a));
                Color bottom = ColorUtils.alpha(ColorUtils.mix(base, Color.BLACK, 0.26f), Math.round(246f * a));
                Render2D.drawRoundedVerticalGradient(stack, x, y, width, height, radius, top, bottom);
                Render2D.drawBorder(stack, x, y, width, height, radius, 1.0f, 2.0f,
                        ColorUtils.alpha(accent, Math.round(115f * a)));
            }
        }
    }

    private static void softGlow(Object stack, float x, float y, float width, float height,
                                 float radius, Color color, int layers, float step) {
        if (color == null || color.getAlpha() <= 0) return;
        for (int i = layers; i >= 1; i--) {
            float expand = i * step;
            int alpha = Math.round(color.getAlpha() * (1f - (i - 1) / (float) layers)
                    / layers * 2.2f);
            if (alpha <= 0) continue;
            Render2D.drawRoundedRect(stack, x - expand, y - expand,
                    width + expand * 2f, height + expand * 2f, radius + expand,
                    ColorUtils.alpha(color, alpha));
        }
    }

    /** Подложка ячейки (слот предмета/голова) в стиле текущей темы. */
    public static void cell(Object stack, float x, float y, float width, float height, float radius, float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha));
        Render2D.drawRoundedRect(stack, x, y, width, height, radius, inset(Math.round(205f * a)));
        Variant v = variant();
        if (v == Variant.Outline || v == Variant.Gradient) {
            Render2D.drawBorder(stack, x, y, width, height, radius, 1.0f, 1.6f,
                    ColorUtils.alpha(accent(), Math.round(70f * a)));
        } else if (v == Variant.Card || v == Variant.Neon) {
            // Card/Neon: ячейки с тонкой светлой кромкой — читаются как «врезанные» слоты карточки.
            Render2D.drawBorder(stack, x, y, width, height, radius, 1.0f, 1.4f,
                    ColorUtils.alpha(isLightTheme() ? Color.BLACK : Color.WHITE, Math.round(34f * a)));
        }
    }

    /** Полоса-прогресс (например HP) primary→secondary поверх приглушённого трека. */
    public static void bar(Object stack, float x, float y, float width, float height, float radius, float fill, float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha));
        float f = Math.max(0f, Math.min(1f, fill));
        Render2D.drawRoundedRect(stack, x, y, width, height, radius, inset(Math.round(200f * a)));
        if (f <= 0f) return;
        float fillW = Math.max(height, width * f);
        Render2D.drawRoundedHorizontalGradient(stack, x, y, fillW, height, radius,
                ColorUtils.alpha(accent(), Math.round(245f * a)),
                ColorUtils.alpha(accentSecondary(), Math.round(245f * a)));
    }

    private static float luminance(Color c) {
        return (0.299f * c.getRed() + 0.587f * c.getGreen() + 0.114f * c.getBlue()) / 255f;
    }

    private static int clampAlpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }
}
