package ru.method.linkenvisuals.modules.impl.client;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.hud.impl.ScoreboardHud;
import ru.method.linkenvisuals.managers.system.HudManager;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiController;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiStyle;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeManager;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemePreset;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.RenderFrameTracker;
import dev.linkenvisuals.render.UiGlow;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;

public class UI extends Module {
    private static final float MIN_SCALE = 0.75f;
    private static final float MAX_SCALE = 1.5f;
    private static final float SCALE_SMOOTH_SPEED = 10.0f;

    private static float appliedInterfaceScale = 1.0f;
    private static float currentInterfaceScale = 1.0f;
    private static float guiTransparency = 1.0f;
    private static long lastScaleUpdateNs = System.nanoTime();
    private static long lastScaleAnimatedFrameId = -1L;

    // Выбор стиля GUI: Link (новый интерфейс) / Panel (классические панели по категориям).
    // Переключается в Themes → GUI Style и в настройках модуля UI из самого Panel GUI.
    private final EnumSetting<WindowGuiStyle> guiStyle = new EnumSetting<>("settings.ui.gui", WindowGuiStyle.LINK) {
        @Override
        public void setValue(WindowGuiStyle value) {
            WindowGuiStyle old = getValue();
            super.setValue(value);
            if (old != value) closeOpenGuiOnStyleSwitch();
        }
    };
    // Вариант оформления HUD в стиле Link (TargetHUD/PotionHUD/ArmorHUD и т.д. меняют облик синхронно).
    // Стиль/форма HUD действуют только при GUI=Link, поэтому в Panel-режиме настройки скрыты.
    private final EnumSetting<LinkHudStyle.Variant> hudStyle = new EnumSetting<>("settings.ui.hud_style", LinkHudStyle.Variant.Card, this::usesLinkGui);
    private final EnumSetting<LinkHudStyle.Shape> hudShape = new EnumSetting<>("settings.ui.hud_shape", LinkHudStyle.Shape.Rounded, this::usesLinkGui);

    private final ColorSetting primaryColor = new ColorSetting("settings.ui.primary_color", ColorUtils.getPrimaryColor()) {
        @Override
        public void setValue(Color value) {
            super.setValue(value);
            ColorUtils.setPrimaryColor(getValue());
        }
    };

    private final ColorSetting secondaryColor = new ColorSetting("settings.ui.secondary_color", ColorUtils.getSecondaryColor()) {
        @Override
        public void setValue(Color value) {
            super.setValue(value);
            ColorUtils.setSecondaryColor(getValue());
        }
    };

    private final ColorSetting backgroundColor = new ColorSetting("settings.ui.background_color", ColorUtils.getBackgroundColor()) {
        @Override
        public void setValue(Color value) {
            super.setValue(value);
            ColorUtils.setBackgroundColor(getValue());
        }
    };

    private final ColorSetting textColor = new ColorSetting("settings.ui.text_color", ColorUtils.getTextColor()) {
        @Override
        public void setValue(Color value) {
            super.setValue(value);
            ColorUtils.setTextColor(getValue());
        }
    };

    private final BooleanSetting fade = new BooleanSetting("settings.ui.fade", true) {
        @Override
        public void setValue(Boolean value) {
            super.setValue(value);
            ColorUtils.setThemeFadeEnabled(getValue());
        }
    };

    private final BooleanSetting blur = new BooleanSetting("settings.ui.blur", true) {
        @Override
        public void setValue(Boolean value) {
            super.setValue(value);
            ColorUtils.setBlurEnabled(getValue());
        }
    };

    /** Master switch for the LinkGUI accent halos/rims (theme borders stay). */
    private final BooleanSetting glow = new BooleanSetting("settings.ui.glow", true) {
        @Override
        public void setValue(Boolean value) {
            super.setValue(value);
            UiGlow.setEnabled(getValue());
        }
    };

    // «Boost FPS UI»: режим фона при открытом Link GUI (Default/Middle/High). В общем списке скрыт
    // (visible=false) — управляется выбором в секции «Интерфейс» окна Темы.
    private static BoostFpsMode boostFpsMode = BoostFpsMode.DEFAULT;
    private final EnumSetting<BoostFpsMode> boostFpsUi = new EnumSetting<>("settings.ui.boost_fps", BoostFpsMode.DEFAULT, () -> false) {
        @Override
        public void setValue(BoostFpsMode value) {
            super.setValue(value);
            boostFpsMode = getValue();
        }
    };

    private final BooleanSetting customScoreboard = new BooleanSetting("settings.ui.scoreboard.custom", false) {
        @Override
        public void setValue(Boolean value) {
            super.setValue(value);
            syncCustomScoreboardHud(getValue());
        }
    };
    private final NumberSetting scoreboardRadius = new NumberSetting("settings.ui.scoreboard.radius", 9.0f, 0.0f, 18.0f, 0.5f, customScoreboard::getValue);
    private final ColorSetting scoreboardBackground = new ColorSetting("settings.ui.scoreboard.background", new Color(18, 20, 26, 185), customScoreboard::getValue);

    // Кастом-таб: начинку (головы/ники/пинг) не трогаем, меняем только фон — скругление + блюр + свой цвет + анимация.
    private final BooleanSetting customTab = new BooleanSetting("settings.ui.tab.custom", false);
    private final ColorSetting customTabColor = new ColorSetting("settings.ui.tab.color", new Color(15, 17, 22, 205), customTab::getValue);

    // Прозрачность GUI в процентах (30–100%). Применяется как множитель альфы контента Link GUI.
    // Прозрачность в общем списке настроек НЕ показываем (visible=false): управляется ползунком в секции
    // «Интерфейс» окна Темы (только Link GUI). В панельной GUI её нет вовсе.
    private final NumberSetting transparency = new NumberSetting("settings.ui.transparency", 100f, 30f, 100f, 1f, () -> false) {
        @Override
        public void setValue(Float value) {
            super.setValue(value);
            guiTransparency = MathHelper.clamp(getValue() / 100f, 0.3f, 1f);
        }
    };

    private final NumberSetting scale = new NumberSetting("settings.ui.scale", 1.0f, MIN_SCALE, MAX_SCALE, 0.01f) {
        @Override
        public void setValue(Float value) {
            super.setValue(MathHelper.clamp(value, MIN_SCALE, MAX_SCALE));
            LinkenVisuals instance = LinkenVisuals.getInstance();
            if (instance != null && instance.isConfigLoading()) {
                snapInterfaceScale(getValue());
            }
        }

        @Override
        public void reset() {
            setValue(getDefaultValue());
        }
    };

    public UI() {
        super("UI", Category.Other);
        setBind(new Bind(UiKeyHandler.DEFAULT_KEY, false));
        getSettings().add(guiStyle);
        getSettings().add(hudStyle);
        getSettings().add(hudShape);
        getSettings().add(primaryColor);
        getSettings().add(secondaryColor);
        getSettings().add(backgroundColor);
        getSettings().add(textColor);
        getSettings().add(fade);
        getSettings().add(blur);
        getSettings().add(glow);
        getSettings().add(boostFpsUi);
        getSettings().add(customScoreboard);
        getSettings().add(scoreboardRadius);
        getSettings().add(scoreboardBackground);
        getSettings().add(customTab);
        getSettings().add(customTabColor);
        getSettings().add(transparency);
        getSettings().add(scale);

        ColorUtils.setPrimaryColor(primaryColor.getValue());
        ColorUtils.setSecondaryColor(secondaryColor.getValue());
        ColorUtils.setBackgroundColor(backgroundColor.getValue());
        ColorUtils.setTextColor(textColor.getValue());
        ColorUtils.setThemeFadeEnabled(fade.getValue());
        ColorUtils.setBlurEnabled(blur.getValue());
        UiGlow.setEnabled(glow.getValue());
        boostFpsMode = boostFpsUi.getValue();
        guiTransparency = MathHelper.clamp(transparency.getValue() / 100f, 0.3f, 1f);
        snapInterfaceScale(scale.getValue());
    }

    public void ensureDefaultBind() {
        setBind(new Bind(UiKeyHandler.DEFAULT_KEY, false));
    }

    public boolean usesLinkGui() {
        return guiStyle.getValue() != WindowGuiStyle.PANEL;
    }

    /** Смена стиля GUI (Link ↔ Panel) закрывает открытый интерфейс — иначе на экране остаётся
     *  старый (уже неактуальный) GUI. При загрузке конфига не трогаем. */
    private void closeOpenGuiOnStyleSwitch() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.isConfigLoading()) return;
        LinkGuiController link = LinkGuiController.getInstance();
        if (link != null && link.isDisplayed()) link.beginClose();
        ClickGui panel = instance.getClickGui();
        if (panel != null && panel.isDisplayed()) panel.beginClose();
        this.toggled = false;
    }

    /** Статически: активен ли стиль Link GUI (для оформления HUD под Link, вне зависимости от открытого экрана). */
    public static boolean isLinkGuiStyle() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return false;
        }
        UI ui = instance.getModuleManager().getModule(UI.class);
        return ui != null && ui.usesLinkGui();
    }

    /** Статически: выбранный вариант оформления HUD (Glass/Solid/Gradient/Outline). */
    public static LinkHudStyle.Variant getHudVariant() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return LinkHudStyle.Variant.Glass;
        }
        UI ui = instance.getModuleManager().getModule(UI.class);
        // Стиль HUD применяется ТОЛЬКО при Link GUI. При Panel GUI — нейтральный дефолт, чтобы выбранный
        // в Link-настройках вариант оформления не «протекал» в Panel-режим.
        if (ui == null || !ui.usesLinkGui()) {
            return LinkHudStyle.Variant.Glass;
        }
        return ui.hudStyle.getValue();
    }

    /** Статически: выбранная форма панелей HUD (Rounded/Pill/Sharp). */
    public static LinkHudStyle.Shape getHudShape() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return LinkHudStyle.Shape.Rounded;
        }
        UI ui = instance.getModuleManager().getModule(UI.class);
        if (ui == null || !ui.usesLinkGui()) {
            return LinkHudStyle.Shape.Rounded;
        }
        return ui.hudShape.getValue();
    }

    /** Keeps UI module color settings aligned with the active Link GUI theme preset. */
    public void syncFromLinkTheme(LinkGuiThemePreset preset) {
        if (preset == null) {
            return;
        }
        primaryColor.setValue(preset.primary());
        secondaryColor.setValue(preset.secondaryAccent());
        backgroundColor.setValue(preset.colorUtilsBackground());
        textColor.setValue(preset.text());
    }

    private void openActiveGui(boolean toggled) {
        if (usesLinkGui()) {
            LinkGuiController linkGui = LinkGuiController.getInstance();
            if (toggled) {
                if (!isInPlayWorld()) return;
                linkGui.open();
                if (linkGui.isDisplayed()) this.toggled = true;
            } else {
                linkGui.beginClose();
                this.toggled = false;
            }
            return;
        }
        ClickGui clickGui = LinkenVisuals.getInstance().getClickGui();
        if (toggled) {
            if (!isInPlayWorld()) return;
            clickGui.open();
            if (clickGui.isDisplayed()) this.toggled = true;
        } else {
            clickGui.beginClose();
            this.toggled = false;
        }
    }

    @Override
    public void toggle() {
        if (!isInPlayWorld()) {
            return;
        }
        if (usesLinkGui()) {
            LinkGuiController linkGui = LinkGuiController.getInstance();
            if (linkGui.isOpen()) setToggled(false);
            else if (linkGui.isDisplayed()) { linkGui.open(); this.toggled = true; }
            else setToggled(true);
            return;
        }
        ClickGui clickGui = LinkenVisuals.getInstance().getClickGui();
        if (clickGui.isOpen()) {
            setToggled(false);
        } else if (clickGui.isDisplayed()) {
            clickGui.open();
            this.toggled = true;
        } else {
            setToggled(true);
        }
    }

    @Override
    public void setToggled(boolean toggled) {
        openActiveGui(toggled);
    }

    public void syncClosedState() {
        this.toggled = false;
    }

    public static boolean isInPlayWorld() {
        return mc.player != null && mc.world != null;
    }

    public static void previewInterfaceScale(float scaleValue) {
        setAppliedScale(scaleValue, false);
    }

    public static float getInterfaceScale() {
        updateScaleAnimation();
        return currentInterfaceScale;
    }

    public static float peekInterfaceScale() {
        return currentInterfaceScale;
    }

    /** Прозрачность GUI [0.3..1.0] — множитель альфы контента Link GUI. */
    public static float getGuiTransparency() {
        return guiTransparency;
    }

    /** Текущий режим «Boost FPS UI» (Default/Middle/High). */
    public static BoostFpsMode getBoostFpsMode() {
        // «Boost FPS Middle» убран из UI — у всех принудительно DEFAULT (мир рендерится нормально, без
        // заморозки в размытый снимок), независимо от сохранённого конфига.
        return BoostFpsMode.DEFAULT;
    }

    public static boolean isCustomScoreboardEnabled() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return false;
        UI module = instance.getModuleManager().getModule(UI.class);
        return module != null && module.customScoreboard.getValue();
    }

    public static boolean isCustomTabEnabled() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return false;
        UI module = instance.getModuleManager().getModule(UI.class);
        return module != null && module.customTab.getValue();
    }

    public static Color getCustomTabColor() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return new Color(15, 17, 22, 205);
        UI module = instance.getModuleManager().getModule(UI.class);
        return module == null ? new Color(15, 17, 22, 205) : module.customTabColor.getValue();
    }

    public static float getScoreboardRadius() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return 9.0f;
        UI module = instance.getModuleManager().getModule(UI.class);
        return module == null ? 9.0f : module.scoreboardRadius.getValue();
    }

    public static void setScoreboardRadius(float value) {
        UI module = module();
        if (module != null) module.scoreboardRadius.setValue(MathHelper.clamp(value, 0f, 18f));
    }

    public static Color getScoreboardBackgroundColor() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return new Color(18, 20, 26, 185);
        UI module = instance.getModuleManager().getModule(UI.class);
        return module == null ? new Color(18, 20, 26, 185) : module.scoreboardBackground.getValue();
    }

    /** Сменить прозрачность (альфу) фона скорборда, сохранив RGB — для парного слайдера «Непрозрачность». */
    public static void setScoreboardBackgroundAlpha(int alpha) {
        UI module = module();
        if (module == null) return;
        Color c = module.scoreboardBackground.getValue();
        module.scoreboardBackground.setValue(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.max(0, Math.min(255, alpha))));
    }

    private static UI module() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return null;
        return instance.getModuleManager().getModule(UI.class);
    }

    public void applyScaleSetting() {
        setAppliedScale(scale.getValue(), false);
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    public void resetScaleSetting() {
        scale.reset();
        setAppliedScale(scale.getValue(), false);
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    private static void snapInterfaceScale(float scaleValue) {
        setAppliedScale(scaleValue, true);
    }

    private static void setAppliedScale(float scaleValue, boolean immediate) {
        float clamped = MathHelper.clamp(scaleValue, MIN_SCALE, MAX_SCALE);
        appliedInterfaceScale = clamped;
        if (immediate) {
            currentInterfaceScale = clamped;
        }
        lastScaleUpdateNs = System.nanoTime();
    }

    private static void updateScaleAnimation() {
        long frameId = RenderFrameTracker.getFrameId();
        if (frameId == lastScaleAnimatedFrameId) {
            return;
        }
        lastScaleAnimatedFrameId = frameId;

        long now = System.nanoTime();
        double deltaSeconds = Math.max(0.0, (now - lastScaleUpdateNs) / 1_000_000_000.0);
        lastScaleUpdateNs = now;

        if (Math.abs(currentInterfaceScale - appliedInterfaceScale) <= 0.0005f) {
            currentInterfaceScale = appliedInterfaceScale;
            return;
        }

        float factor = (float) (1.0 - Math.exp(-SCALE_SMOOTH_SPEED * deltaSeconds));
        currentInterfaceScale = MathHelper.lerp(MathHelper.clamp(factor, 0.0f, 1.0f), currentInterfaceScale, appliedInterfaceScale);
    }

    private static void syncCustomScoreboardHud(boolean enabled) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getHudManager() == null) return;
        if (mc == null || mc.getWindow() == null) return;
        HudManager hudManager = instance.getHudManager();
        ScoreboardHud scoreboardHud = hudManager.getHudElement(ScoreboardHud.class);
        if (scoreboardHud == null) return;

        if (scoreboardHud.isToggled() != enabled) {
            hudManager.handleElementToggle(
                    hudManager.getElementSetting(scoreboardHud),
                    enabled,
                    Math.max(0f, mc.getWindow().getScaledWidth() - 150f),
                    40f
            );
        }
    }

}
