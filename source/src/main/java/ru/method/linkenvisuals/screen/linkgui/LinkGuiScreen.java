package ru.method.linkenvisuals.screen.linkgui;

import net.minecraft.client.input.CharInput;

import net.minecraft.client.input.KeyInput;

import net.minecraft.client.gui.Click;

import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.resource.language.I18n;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import dev.linkenvisuals.render.ClickGuiFirePass;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.impl.client.UiKeyHandler;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.ActionSetting;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.screen.clickgui.components.SearchBar;
import ru.method.linkenvisuals.utils.waypoint.Waypoint;
import net.minecraft.util.math.BlockPos;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.clickgui.redesign.ModuleIcons;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiPins;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiPlayer;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeManager;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemePreset;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.input.KeyNameUtils;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.GifCache;
import ru.method.linkenvisuals.utils.render.GifRenderer;
import ru.method.linkenvisuals.utils.render.GifTexture;
import ru.method.linkenvisuals.utils.render.GuiIcons;
import ru.method.linkenvisuals.utils.render.GuiSkinTextures;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI клиента — редизайн «Linken ClickGUI 3a» (пиксель-в-пиксель, холст 1920×1080).
 * Слева профиль + закреплённые модули, по центру окно (топбар с поиском, грид модулей, плеер),
 * внизу док категорий, справа панель «Тема» + «Интерфейс». Логика модулей/настроек сохранена —
 * рисуется новый вид, а тумблеры/слайдеры/чипы/бинды дёргают реальные Setting/Module API.
 */
public class LinkGuiScreen extends Screen implements Wrapper {

    // ── холст 1920×1080 ──
    private static final float DW = 1920f, DH = 1080f;

    // ── дизайн-токены (пересчитываются под тему: тёмная/светлая — см. updateThemeColors) ──
    private Color TEXT = new Color(0xee, 0xf2, 0xef);
    private Color TEXT2 = new Color(0xdf, 0xe5, 0xe1);
    private Color TEXT3 = new Color(0xc3, 0xcc, 0xc7);
    private Color TEXT4 = new Color(0xa8, 0xb1, 0xac);
    private Color MUTED = new Color(0x8b, 0x96, 0x8f);
    private Color MUTED2 = new Color(0x78, 0x83, 0x7d);
    private Color MUTED3 = new Color(0x5d, 0x68, 0x62);
    private Color MUTED4 = new Color(0x4c, 0x56, 0x51);
    private Color INK = new Color(0x0b, 0x12, 0x0e);
    private final Color DANGER = new Color(0xe0, 0x8c, 0x8c);
    // Светлая ли выбранная тема (светлый фон + тёмный текст). Обновляется каждый кадр.
    private boolean lightTheme = false;
    // Тема, под которую последний раз пересобраны токены TEXT*/MUTED*/INK — чтобы не аллоцировать их каждый кадр.
    private Boolean themeTokensLight = null;
    private static final Identifier CONFUSED_GIF = LinkenVisuals.id("textures/gui/confused_spoty.gif");

    // ── анимации открытия/закрытия (сохранены из старого ClickGui) ──
    private static final long OPEN_ANIMATION_DURATION = 560L;
    private static final long CLOSE_FADE_DURATION = 320L;
    private static final float MIN_VISIBLE_FADE = 0.02f;
    private final Animation openAnimation = new Animation(OPEN_ANIMATION_DURATION, 1f, true, Easing.EASE_OUT_CUBIC);
    private final Animation closeFadeAnimation = new Animation(CLOSE_FADE_DURATION, 1f, true, Easing.BOTH_SINE);
    private boolean close = false;

    // ── совместимость со старыми компонентами (Link GUI шарит их) ──
    private final SearchBar searchBar;
    @Setter private String description;

    // ── состояние ──
    private Category selectedCategory = Category.Combat;
    // Режим «Все»: показывать модули всех категорий сразу (кнопка «Все» в топбаре).
    private boolean showAll = false;
    private float gridScroll;
    private float gridScrollTarget;
    // Появление функций по идентичности: всплывает только карточка, реально вошедшая в выдачу.
    private final java.util.Set<String> shownLastFrame = new java.util.HashSet<>();
    private boolean gridSeeded = false;
    // Анимированные позиции карточек грида в КОНТЕНТ-пространстве {x, yBezScroll} — карточки ПЛАВНО едут
    // на новые места (грид не «прыгает»/не «трясётся» при печати и смене категории).
    private final java.util.HashMap<String, float[]> gridPos = new java.util.HashMap<>();
    // Ушедшие из выдачи карточки — гаснут+сжимаются на месте («отбрасывание»): {x, yBezScroll, alpha}.
    private final java.util.HashMap<String, float[]> gridExit = new java.util.HashMap<>();
    private boolean gridCardInteractive = true; // false у гаснущей карточки — её клик-регионы не регистрируем
    private final float[] gridRect = new float[4];
    private final ClickGuiPlayer player = new ClickGuiPlayer();
    private final LinkGuiContext guiContext = new LinkGuiContext();
    // Вызываемые панели: профиль — кнопка-аватар, правая панель — кнопка sliders.
    // По умолчанию обе ЗАКРЫТЫ; состояние живёт в полях и сохраняется между открытиями GUI.
    // Анимация: fade + сдвиг ~10px, 150ms ease-out (по спеке 120–150ms).
    // Спека: профиль и правая панель ОТКРЫТЫ по умолчанию при первом открытии GUI (forward=true).
    private final Animation profileAnim = new Animation(150, 1f, true, Easing.EASE_OUT_CUBIC);
    // Профиль-плеер прячется (плавно), если окно настроек модуля налезает на него (напр. «Сферы» с длинным
    // списком настроек вырастает до профиля). 1 = виден, 0 = скрыт.
    private float profileVis = 1f;
    private final Animation rightPanelAnim = new Animation(150, 1f, true, Easing.EASE_OUT_CUBIC);
    // Отдельные вызываемые окна: менеджер друзей и менеджер меток. Закрыты по умолчанию (forward=false),
    // взаимоисключимы с правой панелью (открытие одного закрывает остальные — все на слоте x=1540).
    private final Animation friendsPanelAnim = new Animation(150, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation waypointsPanelAnim = new Animation(150, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation configsPanelAnim = new Animation(150, 1f, false, Easing.EASE_OUT_CUBIC);
    private final SearchBar friendInput = new SearchBar(0, 0, 0, 0);   // поле ввода ника друга
    private final SearchBar waypointInput = new SearchBar(0, 0, 0, 0); // поле ввода имени метки
    private final SearchBar configInput = new SearchBar(0, 0, 0, 0);   // поле имени конфига
    private final SearchBar wpXInput = new SearchBar(0, 0, 0, 0);      // координаты метки X/Y/Z
    private final SearchBar wpYInput = new SearchBar(0, 0, 0, 0);
    private final SearchBar wpZInput = new SearchBar(0, 0, 0, 0);
    private float friendsScroll, friendsScrollTarget, waypointsScroll, waypointsScrollTarget;
    private float configsScroll, configsScrollTarget;

    // биндинг клавиши
    private enum BindKind { NONE, MODULE, SETTING, OPENKEY }
    private BindKind bindKind = BindKind.NONE;
    private Module bindModule;
    private BindSetting bindSetting;

    // контекст-меню «⋯»
    private Module ctxModule;
    private float ctxX, ctxY;

    // слайдер-драг
    private Slider01 activeSlider;
    private String activeSliderId; // id перетаскиваемого слайдера — его кноб следует за курсором МГНОВЕННО
    private float sliderTrackX, sliderTrackW;
    private StringSetting activeStringSetting; // редактируемая строковая настройка (ник и т.п.)
    private float activeSliderRaw; // СЫРАЯ (неквантованная) позиция курсора [0..1] активного слайдера —
                                   // кноб едет по ней плавно, а значение снапится к шагу (иначе кноб дёргается ступеньками)

    // цветовой пикер пиненной карточки (открывается кликом по свотчу ColorSetting)
    private static final Identifier CP_HUE_TEXTURE = LinkenVisuals.id("client/hue/hue.png");
    private ColorSetting activeColorSetting; // какая настройка сейчас открыта в пикере (null = закрыт)
    private ColorSetting cpSyncedFor; // для какой настройки уже синхронизировано рабочее HSV-состояние
    private float cpHue, cpSat, cpVal, cpOpacity; // рабочее HSV+alpha пикера
    private int cpDrag; // 0 none, 1 sv, 2 hue, 3 opacity — какая зона тянется мышью
    private float cpX, cpY; // якорь-позиция панели пикера (design-координаты)
    // прямоугольники зон пикера (design-координаты) — для хит-теста в mouseClicked/drag-поле
    private float cpSvX, cpSvY, cpSvW, cpSvH;
    private float cpHueX, cpHueY, cpHueW, cpHueH;
    private float cpOpX, cpOpY, cpOpW, cpOpH;
    private float cpPanX, cpPanY, cpPanW, cpPanH;
    private float cpCloseX, cpCloseY, cpCloseS;

    // Раскрытый enum-дропдаун (HUD Style / HUD Shape / GUI Style и пр.). Рисуется оверлеем поверх панели,
    // приоритетный хит-тест в mouseClicked (как у color-picker). null = закрыт.
    private EnumSetting<?> activeEnumDropdown;
    private EnumSetting<?> lastEnumDropdown; // удерживаем на время анимации закрытия (activeEnumDropdown уже null)
    private float edX, edY, edW, edH;   // прямоугольник поля-якоря (design-координаты, обновляется в drawLabeledSelect)
    private float edListY, edRowH;      // Y списка (может открыться вверх) и высота строки — для хит-теста

    // рендер-регионы для кликов (пересобираются каждый кадр)
    private final List<Region> regions = new ArrayList<>();

    // hover-сглаживание по id
    private final java.util.HashMap<String, Float> hover = new java.util.HashMap<>();
    // пин-карточки в процессе анимации закрытия (unpin откладывается до конца анимации)
    private final java.util.Set<String> unpinning = new java.util.HashSet<>();
    // анимированные позиции пиненных карточек {x,y} — для плавного перемещения справа↔влево при перебалансировке
    private final java.util.HashMap<String, float[]> pinPos = new java.util.HashMap<>();
    private boolean lastRightFree = true; // была ли правая колонка свободна в прошлом кадре (для перезакрытия при открытии панели)
    private long lastHoverMs;
    private float dt;
    // Время последнего ввода/фокуса поиска — курсор после нажатия горит полностью, потом плавно мигает.
    private long searchCaretMark;
    // Трейл курсора (настройка «Трекер курсора»): точки в экранных координатах, гаснут по времени.
    private final java.util.ArrayList<LinkGuiCursorPoint> cursorTrail = new java.util.ArrayList<>();
    private final java.util.ArrayList<ru.method.linkenvisuals.utils.render.CircleBatchPipeline.Dot> cursorDots =
            new java.util.ArrayList<>();
    // Анимация ввода текста в поиске: морф previous→current (новый символ плавно проявляется).
    private final AnimatedString searchTextAnim = new AnimatedString(150, Easing.EASE_OUT_CUBIC, "");
    // Анимация смены значений энумов/селектов (кроссфейд старого→нового текста).
    private final java.util.HashMap<String, AnimatedString> valueAnims = new java.util.HashMap<>();

    // Автодиагностика: при каждом открытии GUI сохраняем скрин + лог (screenshots/linken_gui_debug.png).
    private boolean debugCaptured;
    private final java.util.HashMap<String, float[]> gridTargets = new java.util.HashMap<>();
    private final List<Module> filteredModuleScratch = new ArrayList<>();
    private List<Module> gridTargetModules = List.of();
    private float gridTargetX = Float.NaN;
    private float gridTargetColumnStep = Float.NaN;
    private float gridTargetRowStep = Float.NaN;
    private static final float[][] TILE_GLOW_LAYERS = {
            {10f, 0.072f}, {6.5f, 0.116f}, {4f, 0.159f}, {2f, 0.188f}
    };
    /** 1.0.147 lit every panel like a neon sign; keep the identity halo but halve it. */
    private static final float GLOW_SCALE = 0.55f;
    // текущий кадр
    private float fade = 1f;
    private Color accent = new Color(0x7e, 0xdf, 0xb4);
    /** Current frame context for panel halos drawn from MatrixStack-only helpers. */
    private DrawContext currentContext;
    // Сглаженные accent/яркость-фона темы — для плавной анимации смены темы/акцента.
    private Color displayedAccent = null;
    private float displayedBgLum = -1f;
    // Сглаженный фон выбранной темы (preset.background()) — база всех заливок панелей/окон, плавно
    // доезжает при смене темы так же, как accent.
    private Color displayedThemeBg = null;
    private float lastScale = 1f, lastOx, lastOy;
    private boolean previewPending;
    private float previewScale;
    private int previewX1, previewY1, previewX2, previewY2;

    /** Перевод экранных координат мыши в пространство холста 1920×1080 (трансформация последнего кадра). */
    private float[] design(double mx, double my) {
        return new float[]{ (float) ((mx - lastOx) / lastScale), (float) ((my - lastOy) / lastScale) };
    }

    public LinkGuiScreen() {
        super(Text.literal("LinkGui"));
        searchBar = new SearchBar(0, 0, 150, 18);
    }

    // ═══════════════════════ RENDER ═══════════════════════
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc == null || mc.currentScreen != this) return;

        boolean finishAfterRender = false;
        if (close) {
            closeFadeAnimation.update(false);
            if (closeFadeAnimation.getValue() <= MIN_VISIBLE_FADE) finishAfterRender = true;
        } else {
            openAnimation.update(true);
        }

        fade = getUiFade() * UI.getGuiTransparency();
        long now = System.currentTimeMillis();
        dt = lastHoverMs == 0 ? 0f : Math.min(0.1f, (now - lastHoverMs) / 1000f);
        lastHoverMs = now;
        // Плавная смена акцента/темы: displayedAccent time-based доезжает к целевому цвету (~300ms).
        // Берём акцент НАПРЯМУЮ из выбранного пресета темы, а не из ColorUtils.getPrimaryColor(): последний
        // мог «залипнуть» на дефолтном зелёном (95,218,139), если синк ColorUtils с темой разошёлся —
        // тогда карточки/тумблеры оставались зелёными при серой/светлой теме. Пресет — источник истины.
        Color targetAccent = LinkGuiThemeManager.getSelected().primary();
        if (displayedAccent == null) displayedAccent = targetAccent;
        float tk = 1f - (float) Math.exp(-12f * dt);
        displayedAccent = new Color(
                Math.round(displayedAccent.getRed() + (targetAccent.getRed() - displayedAccent.getRed()) * tk),
                Math.round(displayedAccent.getGreen() + (targetAccent.getGreen() - displayedAccent.getGreen()) * tk),
                Math.round(displayedAccent.getBlue() + (targetAccent.getBlue() - displayedAccent.getBlue()) * tk));
        if (Math.abs(targetAccent.getRed() - displayedAccent.getRed()) <= 1
                && Math.abs(targetAccent.getGreen() - displayedAccent.getGreen()) <= 1
                && Math.abs(targetAccent.getBlue() - displayedAccent.getBlue()) <= 1) {
            displayedAccent = targetAccent; // снап при сходимости — стабилизирует ключи кэша SDF
        }
        accent = displayedAccent;
        // Фон панелей адаптируется к фону выбранной темы — доезжает тем же tk, что и акцент.
        Color targetBg = LinkGuiThemeManager.getSelected().background();
        if (displayedThemeBg == null) displayedThemeBg = targetBg;
        displayedThemeBg = new Color(
                Math.round(displayedThemeBg.getRed() + (targetBg.getRed() - displayedThemeBg.getRed()) * tk),
                Math.round(displayedThemeBg.getGreen() + (targetBg.getGreen() - displayedThemeBg.getGreen()) * tk),
                Math.round(displayedThemeBg.getBlue() + (targetBg.getBlue() - displayedThemeBg.getBlue()) * tk));
        updateThemeColors();
        regions.clear();
        ClickGuiFirePass.begin();

        LinkGuiThemeScope.begin(guiContext.getSelectedTheme());
        // Link-контекст обязателен: окна настроек и их компоненты рисуются в Link-стиле по isActive(),
        // а затухают по общему fade (иначе они бы рисовались классическим Panel-видом поверх 3a).
        // Fade собственного канваса применяется ОДИН раз — в a(); контекстный множитель здесь 1,
        // иначе примитивы Render2D домножали бы альфу второй раз (alpha·fade²), и плоские заливки/
        // текст/обводки гасли быстрее градиентных заливок, которые контекст не трогает.
        LinkGuiRenderContext.begin();
        LinkGuiRenderContext.setFade(1f);
        try {
            Render2D.prepareHudFrame(context);

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            // Анимация Link-стиля: выезд снизу + затухание (как у прежнего Link GUI), без масштабного «попа».
            float prog = close
                    ? MathHelper.clamp(closeFadeAnimation.getValue(), 0f, 1f)
                    : MathHelper.clamp(openAnimation.getValue(), 0f, 1f);
            // Масштаб интерфейса (settings.ui.scale, 0.75..1.5) применяется и к самому редизайну — раньше
            // он влиял только на легаси-ClickGUI/HUD, поэтому «Масштаб интерфейса» в панели визуально
            // ничего не менял. Теперь окно зумируется из центра экрана вместе с общим масштабом UI.
            // Холст 1920x1080 не должен выходить за экран (иначе правая панель — друзья — «режется»
            // справа). Базовый fit с паддингом ×масштаб UI, но не больше «сырого» fit по экрану.
            float pad = 12f;
            float fitRaw = Math.min(sw / DW, sh / DH);
            float fitPad = Math.min((sw - pad * 2f) / DW, (sh - pad * 2f) / DH);
            float s = Math.min(fitPad * UI.getInterfaceScale(), fitRaw);
            // Штриховые иконки модулей компенсируют суб-пиксельный рендер под этим масштабом.
            ModuleIcons.pixelScale(s);
            float ox = (sw - DW * s) / 2f;
            float oy = (sh - DH * s) / 2f + (1f - prog) * 90f;
            lastScale = s; lastOx = ox; lastOy = oy;

            float dmx = (mouseX - ox) / s;
            float dmy = (mouseY - oy) / s;

            // Перетаскивание слайдера НЕ зависит от доставки Screen.mouseDragged (в обф-сборке / через
            // мышь-миксин drag-события могут не долетать до экрана — это и была причина «кноб нельзя
            // зажать и тащить»). Пока кноб схвачен и ЛКМ физически зажата — значение каждый кадр тянется
            // за курсором; отпустили кнопку — отпускаем слайдер. Надёжно для ВСЕХ слайдеров.
            if (activeSlider != null && sliderTrackW > 0f) {
                if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                    activeSliderRaw = MathHelper.clamp((dmx - sliderTrackX) / sliderTrackW, 0f, 1f);
                    activeSlider.set(activeSliderRaw);
                } else {
                    activeSlider = null; activeSliderId = null;
                }
            }

            // Драг зон цветового пикера — аналогично слайдеру: пока зона схвачена и ЛКМ физически
            // зажата, канал тянется за курсором каждый кадр (mouseDragged в обф-сборке не долетает).
            if (cpDrag != 0 && activeColorSetting != null) {
                if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                    updateColorDrag(dmx, dmy);
                } else {
                    cpDrag = 0;
                }
            }

            Matrix3x2fStack ms = context.getMatrices();
            drawVignette(context, ms, sw, sh);
            currentContext = context;

            ms.pushMatrix();
            ms.translate(ox, oy);
            ms.scale(s, s);

            drawLeftColumn(context, dmx, dmy);
            drawCenterWindow(context, dmx, dmy);
            drawDock(ms, dmx, dmy);
            // Правая панель — вызываемая (кнопка sliders): fade + сдвиг ~10px.
            float rt = MathHelper.clamp(rightPanelAnim.getValue(), 0f, 1f);
            if (rt > 0.01f) {
                float prevPanelFade = fade;
                fade = prevPanelFade * rt;
                // Появление правой панели — только fade (без ms.translate): чтобы зона клика её слайдеров
                // («Масштаб»/«Прозрачность») совпадала с нарисованным кнобом — иначе их нельзя схватить.
                drawRightPanel(context, dmx, dmy);
                fade = prevPanelFade;
            }
            // Окна «Друзья» и «Метки» — на том же правом слоте, взаимоисключимы с панелью тем.
            // Плавная смена = чистый КРОССФЕЙД по fade (без слайда — иначе при переключении друзья↔метки
            // два окна ехали в противоположные стороны и это выглядело дёргано).
            float frv = MathHelper.clamp(friendsPanelAnim.getValue(), 0f, 1f);
            if (frv > 0.01f) {
                float pf = fade; fade = pf * frv; drawFriendsPanel(context, dmx, dmy); fade = pf;
            }
            float wpv = MathHelper.clamp(waypointsPanelAnim.getValue(), 0f, 1f);
            if (wpv > 0.01f) {
                float pf = fade; fade = pf * wpv; drawWaypointsPanel(context, dmx, dmy); fade = pf;
            }
            float cfv = MathHelper.clamp(configsPanelAnim.getValue(), 0f, 1f);
            if (cfv > 0.01f) {
                float pf = fade; fade = pf * cfv; drawConfigsPanel(context, dmx, dmy); fade = pf;
            }

            // Окна настроек модулей (существующая Link-система) — поверх основного контента.
            // Они полагаются на контекстный fade (LinkGuiChrome/panelShade), поэтому выставляем его
            // только на время их отрисовки и возвращаем 1 для собственного канваса (см. begin выше).
            LinkGuiSettings.setPanels(null, null);
            LinkGuiSettings.setVisibleBounds(0f, DH);
            LinkGuiSettings.setVisibleBoundsX(0f, DW);
            LinkGuiSettings.trimToFit();
            List<LinkGuiSettingsWindow> settingsWindows = LinkGuiSettings.getWindows();
            settingsWindows.removeIf(LinkGuiSettingsWindow::isFinishedClosing);
            LinkGuiRenderContext.setFade(fade);
            for (LinkGuiSettingsWindow window : settingsWindows) {
                window.render(context, Math.round(dmx), Math.round(dmy), delta);
            }
            LinkGuiRenderContext.setFade(1f);

            drawContextMenu(ms, dmx, dmy);

            // Колорпикер пиненной карточки — рисуем ПОСЛЕДНИМ, поверх центрального окна/панелей/меню.
            // Раньше он вызывался внутри drawLeftColumn (до drawCenterWindow) → появлялся ПОД GUI.
            if (activeColorSetting != null) drawColorPicker(ms, dmx, dmy);
            // Плавное раскрытие/закрытие enum-дропдауна: держим последний setting на время анимации закрытия.
            float ddOpen = anim("enumdd", activeEnumDropdown != null ? 1f : 0f);
            if (activeEnumDropdown != null) lastEnumDropdown = activeEnumDropdown;
            if (lastEnumDropdown != null && ddOpen > 0.01f) drawEnumDropdown(ms, dmx, dmy, ddOpen);

            ms.popMatrix();

            // Queue the shader fire; GuiRendererMixin draws it after the GUI
            // batch, and the shader discards every panel interior.
            ClickGuiFirePass.prepare(fade, accent.getRGB());

            if (previewPending && mc.player != null) {
                previewPending = false;
                context.createNewRootLayer();
                InventoryScreen.drawEntity(context, previewX1, previewY1, previewX2, previewY2,
                        Math.max(2, Math.round(previewScale)), 0.0625f,
                        previewX1 + (previewX2 - previewX1) * 0.5f,
                        previewY1 + (previewY2 - previewY1) * 0.80f, mc.player);
            }

            // «Трекер курсора» — светящийся трейл за курсором, в ЭКРАННЫХ координатах (совпадает с
            // реальной позицией мыши, ничего не «съезжает»). Заменяет прежний параллакс панелей.
            if (guiContext.isCursorTracker()) {
                drawCursorTrail(context, mouseX, mouseY);
            } else if (!cursorTrail.isEmpty()) {
                cursorTrail.clear();
            }
        } finally {
            LinkGuiRenderContext.resetFade();
            LinkGuiRenderContext.end();
            LinkGuiThemeScope.end();
        }

        // Flush deferred GUI special elements (player-skin preview) for this custom screen.
        if (finishAfterRender) finishClosing();

        // Диагностический снимок: один раз за открытие, после полного появления GUI.
        // Только при -Dlinken.guiDebug=true: синхронный glReadPixels+PNG давал хитч на каждое открытие.
        if (Boolean.getBoolean("linken.guiDebug") && !debugCaptured && !close && openAnimation.getValue() > 0.95f) {
            debugCaptured = true;
            try {
                List<Module> dbgMods = filteredModules();
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < Math.min(6, dbgMods.size()); i++) names.append(dbgMods.get(i).getName()).append(',');
                LinkenVisuals.LOGGER.info("[LinkGui3a] dbg sw={} sh={} scale={} ox={} oy={} cat={} mods={} [{}] grid=({},{},{},{})",
                        mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                        lastScale, lastOx, lastOy, selectedCategory, dbgMods.size(), names,
                        gridRect[0], gridRect[1], gridRect[2], gridRect[3]);
                net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                        mc.runDirectory, "linken_gui_debug.png", mc.getFramebuffer(), 1, text -> {});
            } catch (Throwable t) {
                LinkenVisuals.LOGGER.warn("[LinkGui3a] debug capture failed", t);
            }
        }
    }

    // ── фон ──
        private void drawVignette(DrawContext context, Matrix3x2fStack ms, int sw, int sh) {
        if (ColorUtils.isBlurEnabled()) {
            try {
                // Use the same 1/8 downsample + 9-tap glass capture as the
                // donor main menu instead of vanilla applyBlur(). Vanilla's
                // blur is visibly blocky at 1440p/4K and was the "bad
                // background" in the report; the capture keeps the scene
                // smooth while the radial vignette supplies the depth.
                ru.method.linkenvisuals.screen.mainmenu.MenuChrome.drawScreenBackground(ms, sw, sh, 0f, 0f);
                return;
            } catch (RuntimeException ignored) {
                // fall through to the theme tint below
            }
        }
        Color tint = lightTheme ? bgDark(0.88f, 153) : bgDark(0.55f, 153);
        WindowGuiRender.drawRoundedRect(ms, 0, 0, sw, sh, 0f, tint);
    }

    /** Анимация ввода поиска: общий префикс держится, новый хвост проявляется и подъезжает снизу,
     *  удаляемый хвост гаснет (морф previous→current). */
    private void drawSearchText(Matrix3x2fStack ms, Instance f, float x, float y, Color color) {
        String cur = searchTextAnim.getCurrent();
        if (!searchTextAnim.isAnimating()) {
            Render2D.drawFont(ms, f, cur, x, y, color);
            return;
        }
        String prev = searchTextAnim.getPrevious();
        float p = searchTextAnim.progress();
        int pl = 0, n = Math.min(cur.length(), prev.length());
        while (pl < n && cur.charAt(pl) == prev.charAt(pl)) pl++;
        String prefix = cur.substring(0, pl);
        String curTail = cur.substring(pl);
        String prevTail = prev.substring(pl);
        Render2D.drawFont(ms, f, prefix, x, y, color);
        float px = x + f.getWidth(prefix);
        if (!prevTail.isEmpty()) {
            Render2D.drawFont(ms, f, prevTail, px, y, alpha(color, 1f - p));
        }
        if (!curTail.isEmpty()) {
            Render2D.drawFont(ms, f, curTail, px, y + (1f - p) * 3f, alpha(color, p));
        }
    }

    private Color alpha(Color c, float t) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.max(0, Math.min(255, Math.round(c.getAlpha() * MathHelper.clamp(t, 0f, 1f)))));
    }

    /** Значение энума/селекта с кроссфейд-анимацией смены (старое гаснет, новое проявляется). */
    private void drawMorphValue(Matrix3x2fStack ms, Instance f, String id, String value, float x, float y, Color color) {
        AnimatedString anim = valueAnims.computeIfAbsent(id, k -> new AnimatedString(160, Easing.EASE_OUT_CUBIC, value));
        anim.update(value);
        if (!anim.isAnimating()) {
            Render2D.drawFont(ms, f, anim.getCurrent(), x, y, color);
            return;
        }
        float p = anim.progress();
        Render2D.drawFont(ms, f, anim.getPrevious(), x, y - p * 2f, alpha(color, 1f - p));
        Render2D.drawFont(ms, f, anim.getCurrent(), x, y + (1f - p) * 2f, alpha(color, p));
    }

        private void drawCursorTrail(DrawContext ctx, int mouseX, int mouseY) {
        Matrix3x2fStack ms = ctx.getMatrices();
        long now = System.currentTimeMillis();
        boolean far = cursorTrail.isEmpty()
                || Math.hypot(mouseX - cursorTrail.get(cursorTrail.size() - 1).x(),
                              mouseY - cursorTrail.get(cursorTrail.size() - 1).y()) > 3.0;
        if (far) cursorTrail.add(new LinkGuiCursorPoint(mouseX, mouseY, now));
        cursorTrail.removeIf(p -> now - p.t() > 450L);
        while (cursorTrail.size() > 32) cursorTrail.remove(0);

        int n = cursorTrail.size();
        for (int i = 0; i < n; i++) {
            LinkGuiCursorPoint p = cursorTrail.get(i);
            float prog = n <= 1 ? 1f : i / (float) (n - 1);
            float age = MathHelper.clamp((now - p.t()) / 450f, 0f, 1f);
            float al = (1f - age) * (1f - age) * (0.15f + 0.55f * prog);
            if (al <= 0.02f) continue;
            float r = 1.4f + 2.8f * prog;
            Render2D.drawCircle(ms, p.x(), p.y(), r * 2.2f, acc(Math.round(60 * al)));
            Render2D.drawCircle(ms, p.x(), p.y(), r, acc(Math.round(200 * al)));
        }
        Render2D.drawCircle(ms, mouseX, mouseY, 6.5f, acc(60));
        Render2D.drawCircle(ms, mouseX, mouseY, 3f, acc(230));
        Render2D.drawCircle(ms, mouseX, mouseY, 1.4f, hl(235));
    }

    // ═══════════════════════ LEFT COLUMN ═══════════════════════
    private void drawLeftColumn(DrawContext ctx, float mx, float my) {
        Matrix3x2fStack ms = ctx.getMatrices();
        float x = 40f, w = 330f, y = 90f;
        float profilePt = MathHelper.clamp(profileAnim.getValue(), 0f, 1f);
        drawProfileCard(ms, x, y, w, mx, my, profilePt);
        // ── закреплённые карточки настроек. Левая колонка (под профилем) заполняется первой. Когда СПРАВА
        // СВОБОДНО (правая панель тем/интерфейса не открыта) и слева уже нет места — излишек уходит в ПРАВУЮ
        // колонку (x=1540). Когда правая панель открыта — правый столбец занят ею: работает только левая
        // колонка, при нехватке места закрывается самая СТАРАЯ карточка (enforcePinFit по текущей вместимости).
        // Ничего не уходит за экран и без скролла. Позиции анимируются.
        float rtp = MathHelper.clamp(sidePanelValue(), 0f, 1f);
        // rightFree по ЦЕЛЕВОМУ состоянию (isForward), а не по значению анимации: при переключении
        // друзья↔метки значение max проседает до ~0.5 и пины дёргались вправо-влево. isForward стабилен.
        boolean rightFree = !anySidePanelOpen();
        lastRightFree = rightFree;
        // Держим карточки в пределах экрана каждый кадр (высота карточки могла измениться) — закрываем самые
        // старые ровно когда реальная раскладка не влезает. Дешёво: пинов единицы.
        enforcePinFit();
        // Балансировки нет — раскладка ЛЕВАЯ-СНАЧАЛА (как хочет пользователь: карточки заполняют левую колонку,
        // при переполнении уходят вправо). Общий placePins ⇒ рендер и enforcePinFit не расходятся.
        java.util.Map<String, Integer> pinCol = placePins(activePins(), pinLeftStart(), DH - 40f, rightFree).col;
        float leftPy = y + (16f + 24f + 40f + 12f + 238f + 28f + 14f)
                * MathHelper.clamp(profileAnim.getValue(), 0f, 1f), rightPy = 90f;
        float ek = 1f - (float) Math.exp(-14f * dt);
        java.util.HashSet<String> alive = new java.util.HashSet<>();
        for (String name : new ArrayList<>(ClickGuiPins.names())) {
            Module m = moduleByName(name);
            if (m == null) continue;
            // Старые пины модулей без настроек (из прошлых версий) — чистим, карточку не показываем.
            if (!hasCardRows(m)) { ClickGuiPins.unpin(name); continue; }
            boolean closing = unpinning.contains(name);
            float ct = anim("pin:" + name, closing ? 0f : 1f);
            if (closing && ct <= 0.01f) {
                ClickGuiPins.unpin(name); unpinning.remove(name); hover.remove("pin:" + name); pinPos.remove(name);
                continue;
            }
            alive.add(name);
            float chStable = pinnedCardHeight(m) + 14f;
            float[] p = pinPos.get(name);
            // Колонка активной карточки — из общей раскладки; у закрывающейся сохраняем текущую (по факт.
            // позиции), чтобы она гасла на месте, а соседи плавно поднимались.
            boolean left = closing
                    ? (p == null || p[0] < x + 300f)
                    : (pinCol.getOrDefault(name, 0) == 0);
            // Закрывающуюся карточку не таскаем и НЕ двигаем стек колонки — гаснет НА МЕСТЕ. Иначе она
            // «съедала» место, и нижние карточки уезжали за экран на время анимации закрытия (~200мс).
            float targetX, targetY;
            if (closing && p != null) {
                targetX = p[0]; targetY = p[1];
            } else {
                targetX = left ? x : 1540f;
                targetY = left ? leftPy : rightPy;
                if (left) leftPy += chStable * ct; else rightPy += chStable * ct;
            }
            if (p == null) { p = new float[]{ targetX, targetY }; pinPos.put(name, p); }
            p[0] += (targetX - p[0]) * ek;
            p[1] += (targetY - p[1]) * ek;
            // Бэкстоп ТОЛЬКО вниз. Раньше здесь стоял голый Math.min, и карточку, которая не влезает в
            // колонку, он задирал ВЫШЕ её слота: у «Косметики» со «Сферами» высота 818px при вместимости
            // 578 давала min(462, 222) = 222 — карточка уезжала на профиль (левая колонка тот же x=40),
            // а 3D-модель профиля пробивала её насквозь. Ниже слота опускать можно, выше — нельзя.
            if (!closing) p[1] = Math.max(targetY, Math.min(p[1], (DH - 40f) - pinnedCardHeight(m)));
            // Карточки в правой колонке (по факт. позиции) гаснут при открытии правой панели.
            boolean onRight = p[0] > x + 300f;
            if (!onRight || rtp < 0.99f) {
                float prevF = fade;
                fade = prevF * ct * (onRight ? (1f - rtp) : 1f);
                drawPinnedCard(ms, p[0], p[1], w, m, mx, my);
                fade = prevF;
            }
        }
        pinPos.keySet().retainAll(alive);
    }

    /** Высота пиненной карточки настроек (для контроля вместимости левой колонки). */
    private float pinnedCardHeight(Module m) {
        int rows = 0;
        for (Setting<?> s : m.getSettings()) {
            if (!s.isVisible()) continue;
            if (s instanceof BooleanSetting || s instanceof EnumSetting<?> || s instanceof NumberSetting || s instanceof BindSetting || s instanceof ActionSetting || s instanceof StringSetting || s instanceof ColorSetting) rows++;
        }
        float headH = 30f, rowH = 34f;
        // Должно 1:1 совпадать с drawPinnedCard (иначе раскладка пинов и «что влезает» разъедутся).
        float bodyH = rows == 0 ? (12f + 58f) : 12f + rows * rowH;
        return 14f + headH + bodyH + 14f;
    }

    /** Результат раскладки пинов: колонка каждого (0=левая x=40, 1=правая x=1540) + влезло ли всё на экран. */
    private static final class PinPlacement {
        final java.util.HashMap<String, Integer> col = new java.util.HashMap<>();
        boolean fits = true;
    }

    /**
     * ЕДИНАЯ раскладка закреплённых карточек по двум колонкам — используется И рендером, И enforcePinFit,
     * чтобы «сколько влезает» и «что закрыть» никогда не расходились.
     * <p>1) Сначала «левая-сначала» с добивкой хвоста (визуально приятно, стабильно): карточка идёт в ЛЕВУЮ,
     * пока та не переполнится, излишек — в правую; мелкая последующая ещё может добить хвост левой.
     * <p>2) Если так правая переполняется, ищем ЛЮБУЮ допустимую 2-колоночную раскладку (subset-sum DP по
     * высотам) — чтобы НЕ закрывать настройку, пока валидная раскладка существует (иначе «левая-сначала»
     * фрагментировала место и старейшая карточка закрывалась зря).
     * <p>Правая занята боковой панелью (rightFree=false) — только левая. fits=false лишь когда даже лучшая
     * раскладка не влезает (тогда enforcePinFit закроет старейшую).
     */
    private PinPlacement placePins(List<String> ordered, float leftStart, float bottom, boolean rightFree) {
        PinPlacement r = new PinPlacement();
        int n = ordered.size();
        float[] h = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            Module m = moduleByName(ordered.get(i));
            h[i] = (m == null) ? 0f : pinnedCardHeight(m) + 14f;
            total += h[i];
        }
        float Lcap = bottom - leftStart, Rcap = bottom - 90f;
        if (!rightFree) {
            for (String nm : ordered) r.col.put(nm, 0);
            r.fits = total <= Lcap + 0.5f;
            return r;
        }
        // 1) «Левая-сначала» с добивкой.
        int[] col = new int[n];
        float lb = leftStart, rb = 90f;
        for (int i = 0; i < n; i++) {
            boolean useLeft = lb <= leftStart + 0.001f || lb + h[i] <= bottom + 0.5f;
            col[i] = useLeft ? 0 : 1;
            if (useLeft) lb += h[i]; else rb += h[i];
        }
        if (lb <= bottom + 0.5f && rb <= bottom + 0.5f) {
            for (int i = 0; i < n; i++) r.col.put(ordered.get(i), col[i]);
            r.fits = true;
            return r;
        }
        // 2) Переполнилось — ищем допустимую раскладку (subset-sum).
        int[] feasible = feasibleColumns(h, Lcap, Rcap, total);
        if (feasible != null) {
            for (int i = 0; i < n; i++) r.col.put(ordered.get(i), feasible[i]);
            r.fits = true;
            return r;
        }
        // 3) Не влезает никак — оставляем «левую-сначала», fits=false.
        for (int i = 0; i < n; i++) r.col.put(ordered.get(i), col[i]);
        r.fits = false;
        return r;
    }

    /**
     * Ищет разбиение карточек по 2 колонкам, при котором ОБЕ влезают: подмножество слева с суммой в
     * [total-Rcap, Lcap]. Возвращает колонку каждой (0=левая, 1=правая) или null, если такого нет.
     * Плотнее набивает левую (макс. допустимая левая сумма); новые карточки уходят вправо. DP по суммам —
     * полиномиальный (O(n·Lcap)), пинов единицы.
     */
    private int[] feasibleColumns(float[] h, float Lcap, float Rcap, float total) {
        int n = h.length;
        int L = Math.max(0, Math.round(Lcap)), R = Math.max(0, Math.round(Rcap));
        int[] hi = new int[n];
        int tot = 0;
        for (int i = 0; i < n; i++) { hi[i] = Math.max(0, Math.round(h[i])); tot += hi[i]; }
        int lo = Math.max(0, tot - R);       // левая сумма ≥ lo, иначе правая не влезет
        if (lo > L) return null;             // даже полностью набитая левая не спасает
        boolean[][] dp = new boolean[n + 1][L + 1];
        dp[0][0] = true;
        for (int i = 0; i < n; i++)
            for (int s = 0; s <= L; s++) if (dp[i][s]) {
                dp[i + 1][s] = true;                                  // карта i → вправо
                if (s + hi[i] <= L) dp[i + 1][s + hi[i]] = true;      // карта i → влево
            }
        int best = -1;
        for (int s = L; s >= lo; s--) if (dp[n][s]) { best = s; break; }
        if (best < 0) return null;
        int[] col = new int[n];
        int s = best;
        for (int i = n - 1; i >= 0; i--) {
            if (dp[i][s]) col[i] = 1;                                 // достижимо без i слева → i вправо
            else { col[i] = 0; s -= hi[i]; }                          // i обязана слева
        }
        return col;
    }

    /** Активные (не закрывающиеся, с настройками) пины в порядке старые→новые — как их рисуем/раскладываем. */
    private List<String> activePins() {
        List<String> active = new ArrayList<>();
        for (String n : ClickGuiPins.names()) {
            if (unpinning.contains(n)) continue;
            Module m = moduleByName(n);
            if (m == null || !hasCardRows(m)) continue;
            active.add(n);
        }
        return active;
    }

    /** Верх левой колонки пинов по ЦЕЛЕВОМУ состоянию профиля (стабильно при его анимации). */
    private float pinLeftStart() {
        return 90f + (16f + 24f + 40f + 12f + 238f + 28f + 14f)
                * (profileAnim.isForward() ? 1f : 0f);
    }

    /** Держим карточки настроек в пределах экрана: закрываем САМЫЕ СТАРЫЕ ровно тогда, когда РЕАЛЬНАЯ
     *  раскладка (та же, что в рендере) не влезает — ничего не уходит за экран и настройка не закрывается,
     *  пока есть куда её поставить (новое всегда открывается, без скролла). */
    /**
     * Клик по аватару — открыть/закрыть профиль. При ЯВНОМ открытии освобождаем место под профилем:
     * закрываем самые старые пины, которые под ним не влезают. Иначе enforcePinFit тем же кадром
     * свернул бы профиль обратно (guard на :785), и при открытых настройках косметики профиль
     * «не открывался вообще». Теперь профиль открывается, а конфликтующая карточка настроек уходит.
     */
    private void toggleProfile() {
        boolean opening = !profileAnim.isForward();
        profileAnim.update(opening);
        if (!opening) return;
        boolean rightFree = !anySidePanelOpen();
        float bottom = DH - 40f;
        List<String> active = activePins(); // isForward()==true → pinLeftStart() уже «под профилем»
        int guard = 0;
        while (!active.isEmpty() && guard++ < 128
                && !placePins(active, pinLeftStart(), bottom, rightFree).fits) {
            unpinning.add(active.remove(0)); // анимированно закрываем старейшую конфликтующую карточку
        }
    }

    private void enforcePinFit() {
        boolean rightFree = !anySidePanelOpen();
        float bottom = DH - 40f;
        List<String> active = activePins();
        // Карточка не влезает ПОД профилем, но влезает без него — сворачиваем профиль. Именно этого
        // ждут от «Косметики» со «Сферами»: настройки должны убирать профиль, а не обрезаться низом
        // экрана. Осцилляции нет: после сворачивания isForward() == false и условие больше не ловит,
        // обратно профиль поднимается только кнопкой-аватаром.
        if (profileAnim.isForward() && !active.isEmpty()
                && !placePins(active, pinLeftStart(), bottom, rightFree).fits
                && placePins(active, 90f, bottom, rightFree).fits) {
            profileAnim.update(false);
        }
        float leftStart = pinLeftStart();
        // Пока раскладка не влезает — закрываем самый СТАРЫЙ (начало списка) и пробуем снова.
        int guard = 0;
        while (active.size() > 1 && guard++ < 128 && !placePins(active, leftStart, bottom, rightFree).fits) {
            active.remove(0);
        }
        // Всё, что не попало в active и ещё не закрывается — закрываем АНИМИРОВАННО (через unpinning).
        java.util.HashSet<String> keep = new java.util.HashSet<>(active);
        for (String n : ClickGuiPins.names()) {
            if (keep.contains(n) || unpinning.contains(n)) continue;
            Module m = moduleByName(n);
            if (m != null && hasCardRows(m)) unpinning.add(n);
        }
    }

    /** Есть ли у модуля хоть одна отображаемая в карточке настройка (Boolean/Enum/Number/Bind). */
    private boolean hasCardRows(Module m) {
        for (Setting<?> s : m.getSettings()) {
            if (!s.isVisible()) continue;
            if (s instanceof BooleanSetting || s instanceof EnumSetting<?> || s instanceof NumberSetting || s instanceof BindSetting || s instanceof ActionSetting || s instanceof StringSetting || s instanceof ColorSetting)
                return true;
        }
        return false;
    }

    /** ПКМ по карточке модуля: открыть/закрыть его пин-карточку в левой колонке (с анимацией). */
    private void togglePin(String name) {
        if (ClickGuiPins.names().contains(name)) {
            // Повторный ПКМ во время закрытия отменяет его (карточка плавно возвращается).
            if (!unpinning.add(name)) unpinning.remove(name);
            return;
        }
        Module m = moduleByName(name);
        // Нет отображаемых настроек — карточка была бы пустой: вообще не открываем.
        if (m == null || !hasCardRows(m)) return;
        hover.put("pin:" + name, 0f);
        ClickGuiPins.toggle(name);
        enforcePinFit(); // закрыть старые, если не влезают — ничего не уходит за экран
    }

    /** Возвращает высоту нарисованной карточки. */
    private float drawPinnedCard(Matrix3x2fStack ms, float x, float y, float w, Module m, float mx, float my) {
        List<Setting<?>> rows = new ArrayList<>();
        for (Setting<?> s : m.getSettings()) {
            if (!s.isVisible()) continue;
            if (s instanceof BooleanSetting || s instanceof EnumSetting<?> || s instanceof NumberSetting || s instanceof BindSetting || s instanceof ActionSetting || s instanceof StringSetting || s instanceof ColorSetting)
                rows.add(s);
        }
        float pad = 16f;
        float headH = 30f;
        float rowH = 34f; // воздух между настройками (было 26 → +3px по просьбе)
        // Нет row-настроек → тело с GIF-заглушкой «нет настроек» (а не пустая карточка).
        // Тело = зазор 12px (в нём сидит разделитель на +6) + строки. Разделитель и его зазор НЕ
        // добавляют высоту сверх этих 12px — иначе снизу карточки копится лишняя пустота (~13px),
        // особенно заметная при малом числе видимых настроек (напр. ViewModel в режиме «Анимированное»).
        float bodyH = rows.isEmpty() ? (12f + 58f) : 12f + rows.size() * rowH;
        float h = 14f + headH + bodyH + 14f;

        // Неоновый ореол карточки настроек (спека §1 левой колонки: 0 0 50px -20px accent·0.3) — ПОД фоном,
        // входит в fade открытия/закрытия карточки (fade домножен на ct у вызывающего). Панель всегда, t=1.
        ClickGuiFirePass.addPanel(currentContext, x, y, w, h, 8f, fade);
        glow(ms, x, y, w, h, 8f, 16f, 40, 1f);
        card(ms, x, y, w, h, 8f);
        float cx = x + pad, cy = y + 14f, cw = w - pad * 2f;

        // тайл иконки (активный модуль — акцент); КЛИК по нему включает/выключает модуль (как в старой системе)
        boolean on = m.isToggled();
        float tile = 30f;
        float tileGt = anim("pintile:" + m.getName(), on ? 1f : 0f);
        glow(ms, cx, cy, tile, tile, 6f, 6f, 90, tileGt); // мягкий глоу тайла у включённого модуля
        Render2D.drawRoundedRect(ms, cx, cy, tile, tile, 6f, on ? acc(28) : hl( 8));
        Render2D.drawBorder(ms, cx, cy, tile, tile, 6f, 0.8f, new ColorRGBA(on ? acc(90) : hl( 30)));
        drawCatIcon(ms, m.getCategory(), cx + tile / 2f, cy + tile / 2f, 14f, on ? accent : MUTED);
        region(cx, cy, tile, tile, 0, () -> toggleModule(m));
        // имя + категория
        Instance nf = Fonts.UNBOUNDED.getFont(12f);
        Render2D.drawFont(ms, nf, m.getDisplayName(), cx + tile + 11f, cy + 2f, a(TEXT));
        Instance cf = Fonts.GOLOS.getFont(11f);
        Render2D.drawFont(ms, cf, m.getCategory().name(), cx + tile + 11f, cy + 15f, a(MUTED3));
        // крестик (закрыть настройки) — кнопка ВСЕГДА есть (лёгкая подложка), обводка появляется на
        // ховере (красная); глиф крестика строго по центру кнопки.
        float bs = 22f, bx = cx + cw - bs, byy = cy;
        boolean xh = hit(mx, my, bx, byy, bs, bs);
        float xt = anim("pinx:" + m.getName(), xh ? 1f : 0f);
        Render2D.drawRoundedRect(ms, bx, byy, bs, bs, 6f, mix(hl(8), a(DANGER, 34), xt));
        if (xt > 0.01f) {
            Render2D.drawBorder(ms, bx, byy, bs, bs, 6f, 0.9f, new ColorRGBA(a(DANGER, Math.round(150 * xt))));
        }
        ClickGuiIcons.close(ms, bx + bs / 2f, byy + bs / 2f, 12f, a(mix(MUTED, DANGER, xt)));
        region(bx, byy, bs, bs, 0, () -> unpinning.add(m.getName()));

        // разделитель
        Render2D.drawRoundedRect(ms, cx, cy + headH + 6f, cw, 1f, 0.5f, hl( 16));
        if (rows.isEmpty()) {
            // Empty-state с гифкой — модуль без отображаемых настроек (вместо пустого тела).
            GifTexture gif = GifCache.get(CONFUSED_GIF);
            float gs = 40f;
            float gY = cy + headH + 14f;
            GifRenderer.drawGif(ms, gif, cx + (cw - gs) / 2f, gY, gs, gs, fade);
            Instance ef = Fonts.GOLOS.getFont(11f);
            String em = I18n.translate("clickgui.settings.empty");
            Render2D.drawFont(ms, ef, em, cx + (cw - ef.getWidth(em)) / 2f, gY + gs + 2f, a(MUTED));
            return h;
        }
        float ry = cy + headH + 12f;
        for (Setting<?> s : rows) {
            drawSettingRow(ms, cx, ry, cw, s, mx, my);
            ry += rowH;
        }
        return h;
    }

    // ── строка настройки (в закреплённой карточке) ──
    private void drawSettingRow(Matrix3x2fStack ms, float x, float y, float w, Setting<?> s, float mx, float my) {
        // ActionSetting — полноширинная кнопка (напр. «Открыть гардероб» у Косметики).
        if (s instanceof ActionSetting as) {
            float bh = 22f, by = y + (16f - bh) / 2f + 1f;
            boolean bhov = hit(mx, my, x, by, w, bh);
            float bt = anim("act:" + s.getName(), bhov ? 1f : 0f);
            Render2D.drawRoundedRect(ms, x, by, w, bh, 6f, mix(acc(20), acc(45), bt));
            Render2D.drawBorder(ms, x, by, w, bh, 6f, 0.8f, new ColorRGBA(mix(acc(90), acc(150), bt)));
            Instance bf = Fonts.UNBOUNDED.getFont(10f);
            String label = I18n.translate(as.getLabelKey());
            Render2D.drawFont(ms, bf, label, x + (w - bf.getWidth(label)) / 2f, by + bh / 2f - bf.getHeight() / 2f, acc(255));
            region(x, by, w, bh, 0, as::run);
            return;
        }
        Instance lf = Fonts.UNBOUNDED_L.getFont(10.5f);
        // Лейбл клипаем, чтобы не наезжал на контрол справа (бинд/энум-чипы бывают широкими).
        float labelMax = w - 62f;
        if (s instanceof EnumSetting<?> es2) labelMax = w - chipWidth(I18n.translate(es2.currentEnumName())) - 10f;
        else if (s instanceof BindSetting bnd2) labelMax = w - chipWidth(bindText(bnd2.getValue())) - 10f;
        else if (s instanceof StringSetting) labelMax = w * 0.4f;
        Render2D.drawFont(ms, lf, clip(lf, I18n.translate(s.getName()), Math.max(28f, labelMax)), x, y + 8f - lf.getHeight() / 2f, a(TEXT3));

        if (s instanceof BooleanSetting bs) {
            float tw = 36f, th = 20f, tx = x + w - tw, ty = y + (16f - th) / 2f + 1f;
            drawToggle(ms, tx, ty, "tgl:" + s.getName(), bs.getValue());
            region(tx, ty, tw, th, 0, () -> bs.setValue(!bs.getValue()));
        } else if (s instanceof EnumSetting<?> es) {
            String val = I18n.translate(es.currentEnumName());
            drawValueChip(ms, x + w, y, s.getName(), val, mx, my);
            float cw2 = chipWidth(val);
            region(x + w - cw2, y - 2, cw2 + 2, 24, 0, es::increaseEnum);
        } else if (s instanceof BindSetting bnd) {
            boolean listening = bindKind == BindKind.SETTING && bindSetting == bnd;
            String val = listening ? "..." : bindText(bnd.getValue());
            drawValueChip(ms, x + w, y, s.getName(), val, mx, my);
            float cw2 = chipWidth(val);
            region(x + w - cw2, y - 2, cw2 + 2, 24, 0, () -> startBind(BindKind.SETTING, null, bnd));
        } else if (s instanceof NumberSetting ns) {
            drawInlineSlider(ms, x, y, w, ns, mx, my);
        } else if (s instanceof StringSetting ss) {
            // Редактируемое текстовое поле (ник/донат и т.п.). Клик — фокус; ввод правит значение настройки.
            float fw = w * 0.56f, fx = x + w - fw, fh = 22f, fyc = y + (16f - fh) / 2f + 1f;
            boolean active = activeStringSetting == ss;
            float t = anim("strf:" + s.getName(), active ? 1f : 0f);
            Render2D.drawRoundedRect(ms, fx, fyc, fw, fh, 6f, hl(10));
            WindowGuiRender.drawRoundedOutlineRect(ms, fx, fyc, fw, fh, 6f, 0.8f, mix(hl(20), acc(150), t));
            Instance ff = Fonts.GOLOS.getFont(11f);
            String val = ss.getValue();
            boolean empty = val.isEmpty();
            String shown = (empty && !active) ? I18n.translate(ss.getPlaceholder()) : val;
            String disp = clipTail(ff, shown, fw - 14f);
            // Значение рисуем RAW: обычный drawFont вырезает &-коды (&bВЛАДЕЛЕЦ показывался как
            // «ВЛАДЕЛЕЦ»), а в поле ввода юзер должен видеть и править сырой текст с кодами.
            if (empty && !active) {
                Render2D.drawFont(ms, ff, disp, fx + 8f, fyc + fh / 2f - ff.getHeight() / 2f, a(MUTED3));
            } else {
                Render2D.drawFont(ms, ff, disp, fx + 8f, fyc + fh / 2f - ff.getHeight() / 2f, a(TEXT2));
            }
            if (active) {
                float cwd = Math.min(ff.getWidth(val), fw - 14f);
                float blink = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() % 1000) / 1000.0 * Math.PI * 2.0));
                Render2D.drawRoundedRect(ms, fx + 8f + cwd + 1f, fyc + fh / 2f - 6f, 1f, 12f, 0.5f, a(TEXT2, Math.round(255 * blink)));
            }
            region(fx, fyc, fw, fh, 0, () -> focusStringSetting(ss));
        } else if (s instanceof ColorSetting cs) {
            // Свотч цвета справа (как тумблер) — клик открывает/закрывает пикер над всеми карточками.
            float sw = 26f, sh = 14f, sx = x + w - sw, sy = y + (16f - sh) / 2f + 1f;
            Color cv = cs.getValue();
            // Клетчатая подложка не нужна — заливаем текущим цветом с его альфой поверх тёмной базы.
            Render2D.drawRoundedRect(ms, sx, sy, sw, sh, 4f, hl(10));
            Render2D.drawRoundedRect(ms, sx, sy, sw, sh, 4f, a(cv, cv.getAlpha()));
            boolean open = activeColorSetting == cs;
            WindowGuiRender.drawRoundedOutlineRect(ms, sx, sy, sw, sh, 4f, 0.9f, open ? acc(180) : hl(28));
            region(sx, sy, sw, sh, 0, () -> {
                if (activeColorSetting == cs) { activeColorSetting = null; cpDrag = 0; }
                else {
                    activeColorSetting = cs;
                    cpSyncedFor = null; // пересинхронизировать HSV из значения настройки при открытии
                    cpDrag = 0;
                    // Якорь панели — правее левой колонки, вертикально у свотча (клампится при рисовании).
                    cpX = 40f + 330f + 16f;
                    cpY = MathHelper.clamp(sy - 24f, 90f, DH - 260f);
                }
            });
        }
    }

    /** Ряд «подпись + свотч цвета» в панели интерфейса. Клик по свотчу открывает общий HSV-пикер для cs. */
    private void drawColorRow(Matrix3x2fStack ms, String label, float x, float rowY, float w, ColorSetting cs) {
        Render2D.drawFont(ms, Fonts.UNBOUNDED_L.getFont(10f), label, x, rowY + 4f, a(TEXT3));
        if (cs == null) return;
        float sw = 26f, sh = 14f, sx = x + w - sw, sy = rowY + (20f - sh) / 2f;
        Color cv = cs.getValue();
        Render2D.drawRoundedRect(ms, sx, sy, sw, sh, 4f, hl(10));
        Render2D.drawRoundedRect(ms, sx, sy, sw, sh, 4f, a(cv, cv.getAlpha()));
        boolean open = activeColorSetting == cs;
        WindowGuiRender.drawRoundedOutlineRect(ms, sx, sy, sw, sh, 4f, 0.9f, open ? acc(180) : hl(28));
        region(sx, sy, sw, sh, 0, () -> {
            if (activeColorSetting == cs) { activeColorSetting = null; cpDrag = 0; }
            else {
                activeColorSetting = cs;
                cpSyncedFor = null; // пересинхронизировать HSV из значения настройки при открытии
                cpDrag = 0;
                // Якорь пикера — слева от свотча (в пределах правой панели), клампится при отрисовке.
                cpX = MathHelper.clamp(sx - 190f, 12f, DW - 200f);
                cpY = MathHelper.clamp(sy - 24f, 90f, DH - 260f);
            }
        });
    }

    // ── цветовой пикер (оверлей поверх пиненных карточек) ──
    /** Рисует HSV-пикер для activeColorSetting; собирает зоны в поля cp* для хит-теста/драга. */
    private void drawColorPicker(Matrix3x2fStack ms, float mx, float my) {
        ColorSetting cs = activeColorSetting;
        if (cs == null) return;
        // Синхронизация рабочего HSV из значения настройки при первом открытии.
        if (cpSyncedFor != cs) {
            Color c = cs.getValue();
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            cpHue = hsb[0]; cpSat = hsb[1]; cpVal = hsb[2];
            cpOpacity = c.getAlpha() / 255f;
            cpSyncedFor = cs;
        }
        float pad = 12f, sv = 120f, gap = 8f, hueW = 16f, opH = 12f, titleH = 18f;
        float panW = pad + sv + gap + hueW + pad;
        float panH = pad + titleH + sv + 10f + opH + pad;
        float px = MathHelper.clamp(cpX, 0f, DW - panW);
        float py = MathHelper.clamp(cpY, 0f, DH - panH);
        cpPanX = px; cpPanY = py; cpPanW = panW; cpPanH = panH;
        card(ms, px, py, panW, panH, 8f);
        // заголовок
        Instance tf = Fonts.UNBOUNDED.getFont(10.5f);
        Render2D.drawFont(ms, tf, clip(tf, I18n.translate(cs.getName()), panW - pad * 2f - 20f), px + pad, py + pad + titleH / 2f - tf.getHeight() / 2f - 2f, a(TEXT2));
        // крестик закрытия
        float clS = 16f, clX = px + panW - pad - clS, clY = py + pad - 1f;
        cpCloseX = clX; cpCloseY = clY; cpCloseS = clS;
        boolean clh = hit(mx, my, clX, clY, clS, clS);
        float clt = anim("cpx:" + cs.getName(), clh ? 1f : 0f);
        ClickGuiIcons.close(ms, clX + clS / 2f, clY + clS / 2f, 11f, a(mix(MUTED, DANGER, clt)));

        // SV-квадрат
        float svx = px + pad, svy = py + pad + titleH;
        cpSvX = svx; cpSvY = svy; cpSvW = sv; cpSvH = sv;
        Render2D.drawRoundedHorizontalGradient(ms, svx, svy, sv, sv, 6f, Color.WHITE, a(Color.getHSBColor(cpHue, 1f, 1f), 255));
        Render2D.drawRoundedVerticalGradient(ms, svx, svy, sv, sv, 6f, new Color(0, 0, 0, 0), a(new Color(0, 0, 0, 255), 255));
        float hxp = svx + cpSat * sv, hyp = svy + (1f - cpVal) * sv;
        hxp = MathHelper.clamp(hxp, svx + 2f, svx + sv - 2f);
        hyp = MathHelper.clamp(hyp, svy + 2f, svy + sv - 2f);
        Render2D.drawRoundedRect(ms, hxp - 4f, hyp - 4f, 8f, 8f, 4f, a(new Color(0, 0, 0, 160), 160));
        Render2D.drawBorder(ms, hxp - 4f, hyp - 4f, 8f, 8f, 4f, 1.2f, new ColorRGBA(a(Color.WHITE, 235)));

        // Hue-полоса (вертикальная)
        float hueX = svx + sv + gap, hueY = svy;
        cpHueX = hueX; cpHueY = hueY; cpHueW = hueW; cpHueH = sv;
        Render2D.drawTexture(ms, hueX, hueY, hueW, sv, 6f, CP_HUE_TEXTURE, a(Color.WHITE, 255));
        float hmY = MathHelper.clamp(hueY + cpHue * sv, hueY + 2f, hueY + sv - 2f);
        Render2D.drawRoundedRect(ms, hueX - 1f, hmY - 1.6f, hueW + 2f, 3.2f, 1.6f, a(new Color(0, 0, 0, 140), 140));
        Render2D.drawRoundedRect(ms, hueX, hmY - 1f, hueW, 2f, 1f, a(Color.WHITE, 235));

        // Прозрачность (горизонтальная полоса)
        float opX = svx, opY = svy + sv + 10f, opW = sv + gap + hueW;
        cpOpX = opX; cpOpY = opY; cpOpW = opW; cpOpH = opH;
        int rgbI = Color.HSBtoRGB(cpHue, cpSat, cpVal);
        Color solid = new Color((rgbI >> 16) & 0xFF, (rgbI >> 8) & 0xFF, rgbI & 0xFF);
        Render2D.drawRoundedRect(ms, opX, opY, opW, opH, 6f, hl(10));
        Render2D.drawRoundedHorizontalGradient(ms, opX, opY, opW, opH, 6f, a(new Color(solid.getRed(), solid.getGreen(), solid.getBlue(), 0), 0), a(solid, 255));
        float omX = MathHelper.clamp(opX + cpOpacity * opW, opX + 2f, opX + opW - 2f);
        Render2D.drawRoundedRect(ms, omX - 1.6f, opY - 1f, 3.2f, opH + 2f, 1.6f, a(new Color(0, 0, 0, 140), 140));
        Render2D.drawRoundedRect(ms, omX - 1f, opY, 2f, opH, 1f, a(Color.WHITE, 235));
    }

    /** Применить текущее рабочее HSV+alpha пикера к активной ColorSetting. */
    private void applyColorPicker() {
        if (activeColorSetting == null) return;
        int rgb = Color.HSBtoRGB(cpHue, cpSat, cpVal);
        activeColorSetting.setValue(new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.round(MathHelper.clamp(cpOpacity, 0f, 1f) * 255f)));
    }

    /** Обновить тянущийся канал пикера из design-координат курсора. */
    private void updateColorDrag(float dmx, float dmy) {
        if (cpDrag == 1) {
            cpSat = MathHelper.clamp((dmx - cpSvX) / cpSvW, 0f, 1f);
            cpVal = 1f - MathHelper.clamp((dmy - cpSvY) / cpSvH, 0f, 1f);
        } else if (cpDrag == 2) {
            cpHue = MathHelper.clamp((dmy - cpHueY) / cpHueH, 0f, 1f);
        } else if (cpDrag == 3) {
            cpOpacity = MathHelper.clamp((dmx - cpOpX) / cpOpW, 0f, 1f);
        }
        applyColorPicker();
    }

    // ═══════════════════════ CENTER WINDOW ═══════════════════════
    private void drawCenterWindow(DrawContext ctx, float mx, float my) {
        Matrix3x2fStack ms = ctx.getMatrices();
        float x = 410f, y = 90f, w = 1090f, h = 830f;

        // фон окна
        // Мягкий статичный ореол контура (GLOW-UPDATE §8, accent·0.35 — но приглушённо по альфе,
        // иначе SDF-бордер без CSS-spread даёт слишком яркий широкий налив).
        ClickGuiFirePass.addPanel(ctx, x, y, w, h, 12f, fade);
        glow(ms, x, y, w, h, 12f, 22f, 46, 1f);
        // База окна: почти непрозрачная тёмная заливка. Спека даёт 0.9, но при 0.9 сквозь окно
        // просвечивал игровой HUD (DynamicIsland/скорборд) из блюр-фона — читалось как «призраки/
        // артефакт» в середине. Поднято до ~0.97: HUD за окном больше не виден, «стекло» остаётся
        // за счёт размытого фона мира ПО КРАЯМ экрана (вне панелей).
        WindowGuiRender.drawRoundedVerLinearGradientRect(ms, x, y, w, h, 12f, winTop(), winBot());
        // Диагональная штриховка (спека: -45°, шаг 24px, rgba(255,255,255,0.014)); каждая линия
        // клампится к прямоугольнику окна (без scissor). Одним батчем: ~80 drawLine = draw call на линию.
        Render2D.drawHatch(ms, x, y, w, h, 24f, 1f, hl(4));
        WindowGuiRender.drawRoundedOutlineRect(ms, x, y, w, h, 12f, 1.0f, hl( 23));
        // Уголки-скобки рисуются в КОНЦЕ метода (после топбара/плеера), чтобы их ничто не перекрывало.

        // ── топбар ──
        float tbH = 62f;
        float bx = x + 20f, bcy = y + tbH / 2f;
        Instance logo = Fonts.GOLOS_SEMI.getFont(16f);
        drawSpaced(ms, logo, "linken", bx, bcy - logo.getHeight() / 2f, a(TEXT), -0.035f * 16f);
        float lw = widthSpaced(logo, "linken", -0.035f * 16f);
        Render2D.drawFont(ms, logo, ".", bx + lw, bcy - logo.getHeight() / 2f, acc(255));
        float cursorX = bx + lw + logo.getWidth(".") + 12f;
        Render2D.drawRoundedRect(ms, cursorX, bcy - 11f, 1f, 22f, 0.5f, hl( 26));
        cursorX += 13f;

        // 1.21.4 profile toggle: 34x34 skin head; accent border while the profile card is open.
        drawSkinHead(ms, cursorX, bcy - 17f, 34f, 4.5f);
        float tbavT = anim("tbav", hit(mx, my, cursorX, bcy - 17f, 34f, 34f) ? 1f : 0f);
        if (profileAnim.isForward()) {
            Render2D.drawBorder(ms, cursorX, bcy - 17f, 34f, 34f, 4.5f, 1.0f,
                    new ColorRGBA(acc(Math.round(90 + 60 * tbavT))));
        } else if (tbavT > 0.01f) {
            Render2D.drawBorder(ms, cursorX, bcy - 17f, 34f, 34f, 4.5f, 1.0f,
                    new ColorRGBA(acc(Math.round(130 * tbavT))));
        }
        region(cursorX, bcy - 17f, 34f, 34f, 0, this::toggleProfile);
        cursorX += 34f + 12f;

        // чип «Все» — режим показа модулей ВСЕХ категорий (активен = showAll)
        Instance allF = Fonts.UNBOUNDED.getFont(10.5f);
        float allW = allF.getWidth("Все") + 28f;
        float tballT = anim("tball", hit(mx, my, cursorX, bcy - 17f, allW, 34f) ? 1f : 0f);
        if (showAll) {
            glow(ms, cursorX, bcy - 17f, allW, 34f, 7f, 8f, 128, 1f); // GLOW-UPDATE §5
            Render2D.drawRoundedRect(ms, cursorX, bcy - 17f, allW, 34f, 7f, acc(18));
            Render2D.drawBorder(ms, cursorX, bcy - 17f, allW, 34f, 7f, 6f, 0.5f, acc(102));
            Render2D.drawBorder(ms, cursorX, bcy - 17f, allW, 34f, 7f, 0.8f, new ColorRGBA(acc(140)));
            Render2D.drawFont(ms, allF, "Все", cursorX + 14f, bcy - allF.getHeight() / 2f, acc(255));
        } else {
            Render2D.drawRoundedRect(ms, cursorX, bcy - 17f, allW, 34f, 7f, hl(10));
            Render2D.drawBorder(ms, cursorX, bcy - 17f, allW, 34f, 7f, 0.8f,
                    new ColorRGBA(hl(Math.round(18 + 40 * tballT))));
            Render2D.drawFont(ms, allF, "Все", cursorX + 14f, bcy - allF.getHeight() / 2f, a(mix(TEXT3, TEXT, tballT)));
        }
        // Повторный клик по «Все» ВЫКЛЮЧАЕТ режим — возврат к выбранной категории (toggle).
        region(cursorX, bcy - 17f, allW, 34f, 0, () -> {
            showAll = !showAll; searchBar.reset(); gridScrollTarget = 0f; // скролл к верху анимируем (как поиск)
        });
        cursorX += allW + 12f;
        // поиск (flex)
        float closeBtn = 34f, sliderBtn = 34f;
        float searchW = x + w - 20f - closeBtn - 8f - sliderBtn - 8f - cursorX;
        float searchX = cursorX, searchY = bcy - 17f;
        boolean sf = searchBar.isFocused();
        float sfT = anim("tbsearch", sf ? 1f : 0f); // плавный фокус поиска
        // При фокусе — мягкий акцентный ореол под полем (появляется/гаснет вместе с sfT).
        glow(ms, searchX, searchY, searchW, 34f, 7f, 7f, 70, sfT);
        WindowGuiRender.drawRoundedRect(ms, searchX, searchY, searchW, 34f, 7f, hl( 10));
        // Рамка плавно перекрашивается из белой в акцентную и ярчает при фокусе.
        WindowGuiRender.drawRoundedOutlineRect(ms, searchX, searchY, searchW, 34f, 7f, 0.9f,
                mix(hl( 18), acc(150), sfT));
        // Лупа акцентится при фокусе.
        ClickGuiIcons.search(ms, searchX + 16f, bcy, 14f, mix(a(MUTED), acc(255), sfT));
        String q = searchBar.getSearchText();
        searchTextAnim.update(q);
        Instance qf = Fonts.GOLOS.getFont(12.5f);
        if (q.isEmpty() && !sf && !searchTextAnim.isAnimating()) {
            Render2D.drawFont(ms, qf, "Поиск по модулям...", searchX + 30f, bcy - qf.getHeight() / 2f, a(MUTED3));
        } else {
            drawSearchText(ms, qf, searchX + 30f, bcy - qf.getHeight() / 2f, a(TEXT2));
            if (sf) {
                float cw2 = qf.getWidth(q);
                // Плавное мигание курсора (синус, период 1с); сразу после ввода — полная видимость 350ms.
                long since = System.currentTimeMillis() - searchCaretMark;
                float blink = since < 350 ? 1f
                        : 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() % 1000) / 1000.0 * Math.PI * 2.0));
                Render2D.drawRoundedRect(ms, searchX + 30f + cw2 + 1f, bcy - 7f, 1f, 14f, 0.5f, a(TEXT2, Math.round(255 * blink)));
            }
        }
        region(searchX, searchY, searchW, 34f, 0, () -> { searchBar.setFocused(true); friendInput.setFocused(false); waypointInput.setFocused(false); searchCaretMark = System.currentTimeMillis(); });
        cursorX = searchX + searchW + 8f;
        // кнопка-слайдеры — вызов правой панели «Тема»+«Интерфейс»; активная — акцентная рамка (спека).
        boolean rpOpen = rightPanelAnim.isForward();
        float tbsT = anim("tbs", hit(mx, my, cursorX, bcy - 17f, 34, 34) ? 1f : 0f);
        Render2D.drawRoundedRect(ms, cursorX, bcy - 17f, sliderBtn, 34f, 7f, rpOpen ? acc(18) : hl( 14));
        Render2D.drawBorder(ms, cursorX, bcy - 17f, sliderBtn, 34f, 7f, 1.0f,
                new ColorRGBA(rpOpen ? acc(90) : hl( Math.round(34 + 36 * tbsT))));
        ClickGuiIcons.sliders(ms, cursorX + 17f, bcy, 15f,
                rpOpen ? acc(255) : a(mix(TEXT3, TEXT, tbsT)));
        region(cursorX, bcy - 17f, sliderBtn, 34f, 0, () -> toggleSidePanel(rightPanelAnim));
        cursorX += sliderBtn + 8f;
        // кнопка закрытия
        boolean xh = hit(mx, my, cursorX, bcy - 17f, 34, 34);
        float tbxT = anim("tbx", xh ? 1f : 0f);
        Render2D.drawRoundedRect(ms, cursorX, bcy - 17f, closeBtn, 34f, 7f, hl( 14));
        Render2D.drawBorder(ms, cursorX, bcy - 17f, closeBtn, 34f, 7f, 1.0f,
                new ColorRGBA(mix(hl( 34), a(DANGER, 160), tbxT)));
        ClickGuiIcons.close(ms, cursorX + 17f, bcy, 15f, a(mix(TEXT3, DANGER, tbxT)));
        float fcx = cursorX;
        region(fcx, bcy - 17f, closeBtn, 34f, 0, this::close);
        // hairline топбара
        WindowGuiRender.drawRoundedRect(ms, x, y + tbH, w, 1f, 0.5f, hl( 18));

        // ── грид модулей ──
        float playerAreaTop = y + h - 92f;
        float gx = x + 20f, gy = y + tbH + 20f, gw = w - 40f;
        float gh = playerAreaTop - gy - 8f;
        gridRect[0] = gx; gridRect[1] = gy; gridRect[2] = gw; gridRect[3] = gh;

        List<Module> mods = filteredModules();
        float colGap = 12f, cardH = 102f, rowGap = 12f;
        float colW = (gw - 2f * colGap) / 3f;
        float contentH = (float) Math.ceil(mods.size() / 3.0) * (cardH + rowGap);
        float maxScroll = Math.max(0f, contentH - gh);
        // Плавный скролл грида: цель задаётся колесом, gridScroll затухающе доезжает (без рывков).
        gridScrollTarget = MathHelper.clamp(gridScrollTarget, 0f, maxScroll);
        gridScroll += (gridScrollTarget - gridScroll) * (1f - (float) Math.exp(-16f * dt));
        if (Math.abs(gridScrollTarget - gridScroll) < 0.3f) gridScroll = gridScrollTarget;
        gridScroll = MathHelper.clamp(gridScroll, 0f, maxScroll);
        // ── ИДЕНТИЧНАЯ анимация грида: карточки ПЛАВНО едут на новые места (грид не «прыгает» при печати и
        //    не «трясётся» при смене категории), ушедшие из выдачи — гаснут+сжимаются НА МЕСТЕ («отбрасывание»),
        //    пришедшие — всплывают. Позиции храним в КОНТЕНТ-пространстве (без скролла), рисуем со сдвигом.
        // Positions are independent of scroll and animation; rebuild only when layout inputs change.
        float columnStep = colW + colGap, rowStep = cardH + rowGap;
        if (!mods.equals(gridTargetModules) || gx != gridTargetX
                || columnStep != gridTargetColumnStep || rowStep != gridTargetRowStep) {
            gridTargets.clear();
            for (int i = 0; i < mods.size(); i++) {
                int col = i % 3, row = i / 3;
                gridTargets.put(mods.get(i).getName(), new float[]{ gx + col * columnStep, row * rowStep });
            }
            gridTargetModules = List.copyOf(mods);
            gridTargetX = gx;
            gridTargetColumnStep = columnStep;
            gridTargetRowStep = rowStep;
        }
        java.util.Map<String, float[]> targets = gridTargets;
        java.util.Set<String> shownNow = targets.keySet();
        if (!gridSeeded) {
            // Первый кадр после открытия GUI: карточки сразу на месте (само окно анимируется) — без «попа».
            for (String nm : shownNow) { hover.put("cardin:" + nm, 1f); gridPos.put(nm, targets.get(nm).clone()); }
            gridSeeded = true;
        } else {
            for (String nm : shownNow) {
                if (!gridPos.containsKey(nm)) {              // новая в выдаче — стартует на целевом месте, всплывает
                    gridPos.put(nm, targets.get(nm).clone());
                    hover.put("cardin:" + nm, 0f);
                    gridExit.remove(nm);
                }
            }
            for (String nm : shownLastFrame) {               // ушедшие из выдачи → в exit (гаснут на месте)
                if (!shownNow.contains(nm) && !gridExit.containsKey(nm)) {
                    float[] p = gridPos.get(nm);
                    // Замораживаем в ЭКРАННОМ пространстве (минус текущий скролл): смена категории/«Все»
                    // сбрасывает gridScroll 300→0 в один кадр — если хранить контент-Y, карточка телепортнётся.
                    if (p != null) gridExit.put(nm, new float[]{ p[0], p[1] - gridScroll, 1f });
                }
            }
        }
        gridPos.keySet().retainAll(shownNow);
        shownLastFrame.clear();
        shownLastFrame.addAll(shownNow);
        // Доезд живых карточек к целям (экспоненциальное сглаживание).
        float posEk = 1f - (float) Math.exp(-18f * dt);
        for (String nm : shownNow) {
            float[] p = gridPos.get(nm), tg = targets.get(nm);
            p[0] += (tg[0] - p[0]) * posEk;
            p[1] += (tg[1] - p[1]) * posEk;
        }

        float prevGridFade = fade;
        // Скиссор в DESIGN-координатах. Регион расширен по бокам/низу И СВЕРХУ (16px), чтобы неоновый
        // глоу верхнего ряда включённых карточек не срезался кромкой грида (остаётся ниже хайрлайна топбара).
        Render2D.startScissor(ctx, gx - 14f, gy - 16f, gw + 28f, gh + 12f + 16f);
        // Спека 7c §1: точечный паттерн области — точки rgba(255,255,255,0.035) r~1, шаг 24×24 (статичный).
        // Одним батчем: ~1100 отдельных drawCircle давали draw call на точку (скролл грида проседал).
        Render2D.drawDotGrid(ctx, gx, gy, gw, gh, 24f, 12f, hl(9));
        // живые карточки (в порядке выдачи), позиция — анимированная
        for (Module mm : mods) {
            String nm = mm.getName();
            float[] p = gridPos.get(nm);
            float cxp = p[0], cyp = gy + p[1] - gridScroll;
            if (cyp + cardH < gy - 6 || cyp > gy + gh + 6) continue;
            float ct = anim("cardin:" + nm, 1f);
            ct = 1f - (1f - ct) * (1f - ct); // ease-out
            fade = prevGridFade * (0.15f + 0.85f * ct);
            drawModuleCard(ms, cxp, cyp + (1f - ct) * 12f, colW, cardH, mm, mx, my, true);
        }
        // «отбрасывание» ушедших: гаснут + слегка сжимаются на месте, кликов не ловят
        java.util.Iterator<java.util.Map.Entry<String, float[]>> exIt = gridExit.entrySet().iterator();
        while (exIt.hasNext()) {
            java.util.Map.Entry<String, float[]> e = exIt.next();
            float[] p = e.getValue();
            p[2] -= dt * 6.5f;
            if (p[2] <= 0.02f) { exIt.remove(); hover.remove("cardin:" + e.getKey()); continue; }
            Module mm = moduleByName(e.getKey());
            if (mm == null) { exIt.remove(); continue; }
            float cxp = p[0], cyp = gy + p[1]; // p[1] уже в ЭКРАННОМ пространстве (заморожено со сдвигом скролла)
            if (cyp + cardH < gy - 40 || cyp > gy + gh + 40) continue;
            float a = p[2], ease = a * a * (3f - 2f * a); // smoothstep
            float sc = 0.88f + 0.12f * ease;
            fade = prevGridFade * ease;
            ms.pushMatrix();
            float ccx = cxp + colW / 2f, ccy = cyp + cardH / 2f;
            ms.translate(ccx, ccy);
            ms.scale(sc, sc);
            ms.translate(-ccx, -ccy);
            drawModuleCard(ms, cxp, cyp, colW, cardH, mm, mx, my, false);
            ms.popMatrix();
        }
        gridCardInteractive = true; // сброс после гаснущих карточек
        fade = prevGridFade;
        if (mods.isEmpty() && gridExit.isEmpty()) {
            // Empty-state с гифкой — пустой результат поиска / пустая категория (после завершения «отбрасывания»).
            GifTexture gif = GifCache.get(CONFUSED_GIF);
            String msg = I18n.translate(searchBar.getSearchText().isEmpty()
                    ? "clickgui.category.empty" : "clickgui.search.empty");
            float gs = 90f, gap = 14f;
            Instance mf = Fonts.GOLOS.getFont(12.5f);
            float tw = mf.getWidth(msg), th = mf.getHeight();
            float blockW = gs + gap + tw;
            float ebx = gx + (gw - blockW) / 2f, eby = gy + gh * 0.38f - gs / 2f;
            GifRenderer.drawGif(ms, gif, ebx, eby, gs, gs, fade);
            Render2D.drawFont(ms, mf, msg, ebx + gs + gap, eby + (gs - th) / 2f, a(TEXT3));
        }
        Render2D.stopScissor(ctx);
        fade = prevGridFade;

        // ── плеер ──
        // Прячем профиль (плавно), если окно настроек модуля налезает на его прямоугольник.
        float profTop = playerAreaTop, profBottom = playerAreaTop + 16f + player.height();
        float profLeft = x + 20f, profRight = x + w - 20f;
        boolean overlapped = false;
        for (LinkGuiSettingsWindow win : LinkGuiSettings.getWindows()) {
            float wx = win.getX(), wy = win.getY();
            if (wx < profRight && wx + LinkGuiSettingsWindow.WINDOW_WIDTH > profLeft
                    && wy < profBottom && wy + win.getCurrentHeight() > profTop) {
                overlapped = true;
                break;
            }
        }
        float profTarget = overlapped ? 0f : 1f;
        profileVis += (profTarget - profileVis) * 0.25f;
        if (Math.abs(profTarget - profileVis) < 0.01f) profileVis = profTarget;
        if (profileVis > 0.02f) {
            // Чёткая черта-разделитель между гридом функций и плеером (заметная линия).
            WindowGuiRender.drawRoundedRect(ms, x + 20f, playerAreaTop, w - 40f, 1f, 0.5f, hl(Math.round(55 * profileVis)));
            player.setBounds(x + 20f, playerAreaTop + 16f, w - 40f);
            player.render(ctx, mx, my, accent, fade * profileVis, lightTheme);
        }

        // акцентные уголки-скобки — поверх всего содержимого; отступ 14 (≥ радиус рамки 12),
        // чтобы локоть скобки не врастал в скруглённую дугу рамки окна.
        drawCorner(ms, x + 14f, y + 14f, true);
        drawCorner(ms, x + w - 14f + 2f, y + h - 14f + 2f, false); // правый уголок на 2px ниже и правее
    }

    // Кэш переносов описаний карточек: I18n.translate + regex-split + промер слов на ~24 карточки
    // каждый кадр — дорого; colW постоянна, так что ключа по имени модуля достаточно.
    // Инвалидация — при смене языка клиента.
    private final java.util.HashMap<String, List<String>> descLinesCache = new java.util.HashMap<>();
    private String descCacheLang = "";

    /** Плитка модуля по спеке 7c: 3-колоночная сетка, состояния ON/OFF/hover, штриховая иконка,
     *  тоггл 32×18, кейкап бинда с нижней гранью, «···». Функционал прежний. */
    private void drawModuleCard(Matrix3x2fStack ms, float x, float y, float w, float h, Module m, float mx, float my, boolean interactive) {
        gridCardInteractive = interactive; // гаснущая карточка (interactive=false) не регистрирует клики
        boolean on = m.isToggled();
        boolean hov = hit(mx, my, x, y, w, h);
        float gt = anim("mcg:" + m.getName(), on ? 1f : 0f);
        float hovT = anim("mch:" + m.getName(), hov ? 1f : 0f); // плавный ховер 120–150ms (спека)
        float r = 10f;

        // Ореол включённой плитки 1:1 со спекой 7c (§2 ON: 0 0 30px -8px accent·0.4) — гаснет с выключением.
        tileGlow(ms, x, y, w, h, r, gt);
        // База: OFF-градиент всегда, поверх — ON-градиент по gt (плавная смена состояния синхронно с тогглом).
        WindowGuiRender.drawRoundedVerLinearGradientRect(ms, x, y, w, h, r, cardOffTop(), cardOffBot());
        if (gt > 0.001f) {
            WindowGuiRender.drawRoundedVerLinearGradientRect(ms, x, y, w, h, r,
                    a(dark(cardOnTop()), Math.round(cardOnTop().getAlpha() / fadeSafe() * gt)),
                    a(dark(cardOnBot()), Math.round(cardOnBot().getAlpha() / fadeSafe() * gt)));
            // §2: зелёный отсвет сверху (radial 240×70 acc·0.1 → прозрачный) — как верхняя полоса-градиент.
            float glowH = Math.min(46f, h * 0.5f);
            WindowGuiRender.drawRoundedVerLinearGradientRect(ms, x + 1f, y + 1f, w - 2f, glowH, r,
                    acc(Math.round(26 * gt)), acc(0));
        }
        // Рамка: OFF white 0.08 → hover white 0.22 (любое состояние); ON accent·0.26 → hover white 0.22.
        Color baseBd = on ? acc(66) : hl(20);
        WindowGuiRender.drawRoundedOutlineRect(ms, x, y, w, h, r, 1.0f, mix(baseBd, hl(56), hovT));
        // Inset-блик сверху: ON 0.06 / OFF 0.04.
        Render2D.drawRoundedRect(ms, x + r, y + 1f, w - r * 2f, 1f, 0.5f, hl(on ? 15 : 10));

        float padX = 12f, padTop = 11f;
        // ── Шапка (иконка 15 + имя + тоггл 32×18), высота ряда 18 ──
        float rowH = 18f, hy = y + padTop;
        // тоггл справа
        float tgw = 32f, tgh = 18f, tgx = x + w - padX - tgw, tgy = hy;
        boolean showToggle = !m.hidesToggle();
        // иконка 15×15 без подложки: ON → accent, OFF → MUTED (#8b968f)
        float iconS = 17f, icx = x + padX + iconS / 2f, icy = hy + rowH / 2f;
        Color iconCol = mix(MUTED, accent, gt);
        drawModuleIcon(ms, m, icx, icy, iconS, iconCol);
        // имя — одна строка, ellipsis, остаток ширины до тоггла
        float nameX = x + padX + iconS + 9f;
        float nameRight = (showToggle ? tgx : x + w - padX) - 8f;
        Instance nf = Fonts.UNBOUNDED.getFont(11.5f);
        Render2D.drawFont(ms, nf, clip(nf, m.getDisplayName(), nameRight - nameX), nameX,
                hy + rowH / 2f - nf.getHeight() / 2f, a(TEXT));

        // Регион тела плитки — ПЕРВЫМ (суб-контролы регистрируются позже, в обратном обходе имеют приоритет).
        regionG(x, y, w, h, 0, () -> toggleModule(m));
        regionG(x, y, w, h, 1, () -> togglePin(m.getName())); // ПКМ — карточка настроек

        if (showToggle) {
            drawModuleToggle(ms, tgx, tgy, "mtg:" + m.getName(), on);
            regionG(tgx, tgy, tgw, tgh, 0, () -> toggleModule(m));
        }

        // ── Описание: Golos 11, максимум 2 строки, фикс-высота (плитки в ряду не пляшут) ──
        float descX = x + padX, descY = hy + rowH + 7f;
        String lang = mc.getLanguageManager().getLanguage();
        if (!lang.equals(descCacheLang)) { descLinesCache.clear(); descCacheLang = lang; }
        Instance df = Fonts.GOLOS.getFont(11f);
        List<String> lines = descLinesCache.computeIfAbsent(m.getName(), k -> {
            String desc = desc(m);
            return desc.isEmpty() ? List.of() : wrap(df, desc, w - padX * 2f, 2);
        });
        for (int i = 0; i < lines.size(); i++)
            Render2D.drawFont(ms, df, lines.get(i), descX, descY + i * (df.getHeight() + 4f), a(MUTED2));

        // ── Футер: кейкап бинда + «···» ──
        float footH = 20f, fy = y + h - 9f - footH;
        boolean hasDots = hasAnySettings(m);
        boolean listening = bindKind == BindKind.MODULE && bindModule == m;
        String bindTxt = listening ? "..." : bindText(m.getBind());
        Instance bf = Fonts.UNBOUNDED.getFont(8f);
        float rightLimit = (hasDots ? x + w - padX - 30f : x + w - padX);
        float maxChipTextW = Math.max(16f, rightLimit - (x + padX) - 34f);
        bindTxt = clip(bf, bindTxt, maxChipTextW);
        AnimatedString mbAnim = valueAnims.get("mbindv:" + m.getName());
        float bindTxtW = bf.getWidth(bindTxt);
        if (mbAnim != null && mbAnim.isAnimating()) bindTxtW = Math.max(bindTxtW, bf.getWidth(mbAnim.getPrevious()));
        // Кейкап: [pad7][глиф клавы 9][gap4][текст][pad7], высота 18 с нижней гранью 2px (эффект клавиши).
        float kcH = 18f, kcY = fy + (footH - kcH) / 2f, kcX = x + padX;
        float kcW = 7f + 9f + 4f + bindTxtW + 7f;
        float lt = anim("bindlisten:" + m.getName(), listening ? 1f : 0f);
        Render2D.drawRoundedRect(ms, kcX, kcY, kcW, kcH, 5f, mix(hl(10), acc(24), lt));
        // нижняя грань 2px (rgba(255,255,255,0.2)) — «клавиша»
        Render2D.drawRoundedRect(ms, kcX + 1.5f, kcY + kcH - 2f, kcW - 3f, 2f, 1f, mix(hl(51), acc(120), lt));
        Render2D.drawBorder(ms, kcX, kcY, kcW, kcH, 5f, 0.8f, new ColorRGBA(mix(hl(30), acc(200), lt)));
        // иконку бинда (клавиатура) не меняем — оставляем прежний глиф
        GuiIcons.draw(ms, GuiIcons.KEYBOARD, kcX + 7f + 4.5f, kcY + kcH / 2f, 9f, mix(a(MUTED), acc(255), lt));
        drawMorphValue(ms, bf, "mbindv:" + m.getName(), bindTxt, kcX + 7f + 9f + 4f, kcY + kcH / 2f - bf.getHeight() / 2f, mix(a(MUTED), acc(255), lt));
        regionG(kcX, kcY, kcW, kcH, 0, () -> startBind(BindKind.MODULE, m, null));

        // Бейдж справа от кейкапа: «NEW» (свежий модуль, зелёный) ИЛИ «UPD» (обновились/добавились
        // настройки, янтарный — чтобы отличать от NEW). Показывается, если влезает до «···».
        String tag = m.isNew() ? "NEW" : (UPD_MODULES.contains(m.getName()) ? "UPD" : null);
        if (tag != null) {
            boolean upd = tag.equals("UPD");
            Instance bF = Fonts.UNBOUNDED.getFont(7.5f);
            float bW = bF.getWidth(tag) + 12f, bH = 15f;
            float nx = kcX + kcW + 6f, ny = fy + (footH - bH) / 2f;
            float limit = hasDots ? x + w - padX - 28f : x + w - padX;
            if (nx + bW <= limit) {
                Color fill = upd ? a(UPD_AMBER, 40) : a(NEW_GREEN, 40);
                Color bd   = upd ? a(UPD_AMBER, 165) : a(NEW_GREEN, 165);
                Color txc  = upd ? a(UPD_AMBER, 255) : a(NEW_GREEN, 255);
                Render2D.drawRoundedRect(ms, nx, ny, bW, bH, 4f, fill);
                Render2D.drawBorder(ms, nx, ny, bW, bH, 4f, 0.8f, new ColorRGBA(bd));
                Render2D.drawFont(ms, bF, tag, nx + 6f, ny + bH / 2f - bF.getHeight() / 2f, txc);
            }
        }
        // «···»: 24×20, radius 6, MUTED3 → hover white 0.06 фон + TEXT2.
        if (hasDots) {
            float dw = 24f, dh2 = 20f, dxx = x + w - padX - dw, dyy = fy;
            boolean dh = hit(mx, my, dxx, dyy, dw, dh2);
            float dht = anim("mcd:" + m.getName(), dh ? 1f : 0f);
            if (dht > 0.001f) Render2D.drawRoundedRect(ms, dxx, dyy, dw, dh2, 6f, hl(Math.round(15 * dht)));
            ClickGuiIcons.moreHorizontal(ms, dxx + dw / 2f, dyy + dh2 / 2f, 16f, a(mix(MUTED3, TEXT2, dht)));
            regionG(dxx, dyy, dw, dh2, 0, () -> openContext(m, dxx + dw / 2f, dyy + dh2 / 2f + 8f));
        }
    }

    /** Тоггл модуля 32×18 (спека 7c §3): ON — светло-акцентный градиент + glow + блик, кноб справа;
     *  OFF — трек white 0.1 + внутр. тень, кноб слева. Кноб едет 120–150ms. */
    private void drawModuleToggle(Matrix3x2fStack ms, float x, float y, String id, boolean on) {
        float w = 32f, h = 18f, rr = h / 2f;
        float t = anim(id, on ? 1f : 0f);
        // glow ON: 0 0 14px -2px accent·0.7 — синхронно с состоянием
        glow(ms, x, y, w, h, rr, 6f, 178, t);
        // OFF-трек (white 0.1) всегда как база
        Render2D.drawRoundedRect(ms, x, y, w, h, rr, hl(26));
        if (t > 0.001f) {
            // светлые производные accent: top = mix(accent,white,.13), bot = mix(accent,black,.055)
            Color top = mix(accent, java.awt.Color.WHITE, 0.13f);
            Color bot = mix(accent, java.awt.Color.BLACK, 0.055f);
            WindowGuiRender.drawRoundedVerLinearGradientRect(ms, x, y, w, h, rr,
                    a(top, Math.round(255 * t)), a(bot, Math.round(255 * t)));
            // No hard white inset line here: at 1x/2x it reads as a stray 1px
            // wire that stops before the knob. The ON gradient already has a
            // lighter top, so the track keeps its glassy highlight.
        }
        float knob = 12f;
        float kx = x + 3f + (w - 6f - knob) * t; // OFF x=3 → ON x=17
        // тень кноба 0 1px 2px rgba(0,0,0,0.4)
        Render2D.drawRoundedRect(ms, kx, y + 3f + 1f, knob, knob, knob / 2f, a(0, 0, 0, 90));
        Color kc = mix(a(0xc3, 0xcc, 0xc7, 255), a(0x0b, 0x12, 0x0e, 255), t); // #c3ccc7 → #0b120e
        Render2D.drawRoundedRect(ms, kx, y + 3f, knob, knob, knob / 2f, kc);
    }

    /**
     * Неоновый ореол ON-плитки 1:1 со спекой 7c: CSS {@code 0 0 30px -8px accent·0.4}.
     * Гауссову box-shadow приближаем стопкой feather-рамок: спред -8px держит яркую кромку у края,
     * blur 30px растягивает мягкий спад наружу (~24px). Наложение слоёв даёт пиковую α≈0.4 у кромки.
     * Отдельно от общего {@link #glow} (тот — тонкое одиночное кольцо для дока/тумблера/уголков).
     */
    private void tileGlow(Matrix3x2fStack ms, float x, float y, float w, float h, float r, float t) {
        float tt = MathHelper.clamp(t, 0f, 1f);
        if (tt <= 0.02f) return;
        for (float[] l : TILE_GLOW_LAYERS) {
            float out = l[0];
            int al = Math.round(l[1] * tt * 255f * GLOW_SCALE);
            if (al <= 1) continue;
            // Soft feather layers, same SDF halo as the HUD/settings glow.
            Render2D.drawSoftGlow(ms, x, y, w, h, r, out, acc(al));
        }
    }

    /** fade текущего кадра без нуля — для восстановления «сырой» альфы из a()-цвета. */
    private float fadeSafe() { return Math.max(0.001f, fade); }

    /** Тёмная тема-зависимая база ON-градиента без множителя fade (a() применит fade заново). */
    private Color dark(Color c) { return new Color(c.getRed(), c.getGreen(), c.getBlue()); }

    // Модули, у которых В ЭТОЙ ВЕРСИИ добавились/обновились настройки (бейдж «UPD»). Не пересекается
    // с NEW (у новых модулей — NEW). Пополнять по мере добавления настроек.
    private static final java.util.Set<String> UPD_MODULES = java.util.Set.of("ViewModel", "HitSound");
    private static final Color UPD_AMBER = new Color(0xE6, 0xB4, 0x55);
    // Бейдж NEW — фиксированный зелёный темы Emerald (62,207,122), НЕ из accent (не перекрашивается сменой темы).
    private static final Color NEW_GREEN = new Color(62, 207, 122);

    /** Иконка модуля = иконка его категории (единый набор, крупнее и центрирована). */
    private void drawModuleIcon(Matrix3x2fStack ms, Module m, float icx, float icy, float size, Color col) {
        drawCatIcon(ms, m.getCategory(), icx, icy, size, col);
    }

    /** Есть ли у модуля хоть одна видимая настройка (любого типа) — для показа «⋯». */
    private boolean hasAnySettings(Module m) {
        for (Setting<?> s : m.getSettings()) if (s.isVisible()) return true;
        return false;
    }

    // ═══════════════════════ DOCK ═══════════════════════
    @FunctionalInterface
    private interface DockIcon { void draw(Matrix3x2fStack ms, float cx, float cy, float size, Color color); }

    /** Кнопка дока (категория ИЛИ окно): пилюля-тайл 52px, при активности раскрывается подписью, глоу,
     *  акцентная заливка. aC — плавная «активность» (0..1). Возвращает ширину bw. */
    private float drawDockButton(Matrix3x2fStack ms, float mx, float my, float x, float y, float aC,
                                 String label, boolean active, DockIcon icon, String id, Runnable action, float iconNudgeY) {
        float tile = 52f;
        Instance af = Fonts.UNBOUNDED.getFont(10.5f);
        float bw = tile + (12f + af.getWidth(label) + 18f) * aC;
        boolean hov = hit(mx, my, x, y, bw, tile);
        float ht = anim("dkh:" + id, (hov && !active) ? 1f : 0f);
        float gt = anim("dkg:" + id, active ? 1f : 0f);
        float yy = y - 2f * ht;
        // Нижние категории/кнопки дока тоже получают fire-глоу, как основные панели.
        ClickGuiFirePass.addPanel(currentContext, x, yy, bw, tile, 8f, fade);
        glow(ms, x, yy, bw, tile, 8f, 12f, 90, gt);
        if (aC < 0.999f) card(ms, x, yy, bw, tile, 8f);
        if (aC > 0.001f) Render2D.drawRoundedRect(ms, x, yy, bw, tile, 8f, acc(Math.round(255 * aC)));
        float iconCx = x + tile / 2f + (18f + 8f - tile / 2f) * aC;
        icon.draw(ms, iconCx, yy + tile / 2f + iconNudgeY, 17f - aC, mix(mix(MUTED, TEXT2, ht), INK, aC));
        if (aC > 0.02f) {
            Render2D.drawFont(ms, af, label, x + 18f + 24f, yy + tile / 2f - af.getHeight() / 2f, a(INK, Math.round(255 * aC)));
        }
        region(x, y, bw, tile, 0, action);
        return bw;
    }

    private void drawDock(Matrix3x2fStack ms, float mx, float my) {
        List<Category> cats = new ArrayList<>();
        for (Category c : LinkenVisuals.getInstance().getModuleManager().getCategories())
            if (c != Category.Hud) cats.add(c);

        float gap = 10f, tile = 52f, sep = 16f;
        Instance af = Fonts.UNBOUNDED.getFont(10.5f);
        // «Активность» (aC) ведёт ширину пилюли/подписи/цвет/центрирование — плавно при смене.
        java.util.LinkedHashMap<Category, Float> aMap = new java.util.LinkedHashMap<>();
        float total = 0f;
        for (Category c : cats) {
            float aC = anim("dka:" + c.name(), (!showAll && c == selectedCategory) ? 1f : 0f);
            aMap.put(c, aC);
            total += tile + (12f + af.getWidth(c.name()) + 18f) * aC + gap;
        }
        // Кнопки-окна «Друзья»/«Конфиги»/«Метки» — тоже кнопки дока (как категории), активны когда окно открыто.
        String frLbl = "Друзья", cfLbl = "Конфиги", wpLbl = "Метки";
        float frAC = anim("dka:friends", friendsPanelAnim.isForward() ? 1f : 0f);
        float cfAC = anim("dka:configs", configsPanelAnim.isForward() ? 1f : 0f);
        float wpAC = anim("dka:waypoints", waypointsPanelAnim.isForward() ? 1f : 0f);
        total += sep;
        total += tile + (12f + af.getWidth(frLbl) + 18f) * frAC + gap;
        total += tile + (12f + af.getWidth(cfLbl) + 18f) * cfAC + gap;
        total += tile + (12f + af.getWidth(wpLbl) + 18f) * wpAC + gap;
        total += tile + gap; // «Элементы» — иконка-кнопка (открывает редактор HUD), без активного лейбла
        total -= gap;
        float x = 410f + (1090f - total) / 2f;
        float y = 1080f - 45f - tile; // строка категорий поднята на 5px

        for (Category c : cats) {
            final Category cc = c;
            boolean active = !showAll && c == selectedCategory;
            float bw = drawDockButton(ms, mx, my, x, y, aMap.get(c), c.name(), active,
                    (m2, cx, cy, s, col) -> drawCatIcon(m2, cc, cx, cy, s, col), c.name(), () -> selectCategory(cc), 0f);
            x += bw + gap;
        }
        // разделитель между категориями и кнопками-окнами
        Render2D.drawRoundedRect(ms, x - gap + (sep - 1f) / 2f, y + tile / 2f - 12f, 1f, 24f, 0.5f, hl(22));
        x += sep - gap;
        // iconNudgeY=0 у ВСЕХ кнопок дока: иконки теперь центрируются единообразно через
        // ClickGuiIcons.glyph/drawCatIcon (по cy = yy + tile/2f), а прежние per-button сдвиги
        // (-2/-3px) — устаревший костыль от старых векторных иконок, из-за которого утилитарные
        // значки (Друзья/Конфиги/Метки/Элементы) стояли выше категорийных. 0f = один уровень.
        float frBw = drawDockButton(ms, mx, my, x, y, frAC, frLbl, friendsPanelAnim.isForward(),
                ClickGuiIcons::users, "friends", () -> toggleSidePanel(friendsPanelAnim), 0f);
        x += frBw + gap;
        float cfBw = drawDockButton(ms, mx, my, x, y, cfAC, cfLbl, configsPanelAnim.isForward(),
                ClickGuiIcons::gear, "configs", () -> toggleSidePanel(configsPanelAnim), 0f);
        x += cfBw + gap;
        float wpBw = drawDockButton(ms, mx, my, x, y, wpAC, wpLbl, waypointsPanelAnim.isForward(),
                ClickGuiIcons::mapPin, "waypoints", () -> toggleSidePanel(waypointsPanelAnim), 0f);
        x += wpBw + gap;
        // «Элементы» — редактор HUD (одноразовое действие, открывает HudEditScreen, а не панель)
        drawDockButton(ms, mx, my, x, y, 0f, "Элементы", false,
                ClickGuiIcons::sliders, "elements",
                () -> mc.setScreen(new ru.method.linkenvisuals.screen.hud.HudEditScreen()), 0f);
    }

    // ═══════════════════════ RIGHT PANEL ═══════════════════════
    private void drawRightPanel(DrawContext ctx, float mx, float my) {
        Matrix3x2fStack ms = ctx.getMatrices();
        // Выровнена по верху центрального окна (y=90, была выше — «слишком высоко»); высота под контент.
        // Высота увеличена под доп. ряды группы 3 (цвет фона скорборда + свой таб/цвет фона таба).
        float x = 1540f, y = 90f, w = 340f, h = 920f;

        ClickGuiFirePass.addPanel(ctx, x, y, w, h, 12f, fade);
        glow(ms, x, y, w, h, 12f, 22f, 46, 1f); // GLOW-UPDATE §8: статичный ореол контура (приглушённо)
        // Заливка панели почти непрозрачна: сквозь неё не должен просвечивать игровой HUD (см. окно).
        WindowGuiRender.drawRoundedRect(ms, x, y, w, h, 12f, panelFill());
        // Свечение сверху (спека: radial 440×200 at 50% 0, accent·0.055 — шире панели, поэтому на всю ширину).
        WindowGuiRender.drawRoundedVerLinearGradientRect(ms, x + 1f, y + 1f, w - 2f, 200f, 11f, acc(14), acc(0));
        WindowGuiRender.drawRoundedOutlineRect(ms, x, y, w, h, 12f, 1.0f, hl( 23));

        float px = x + 20f, cw = w - 40f;
        float cy = y + 20f;

        // ── заголовок «Тема» ──
        ClickGuiIcons.droplet(ms, px + 6f, cy + 6f, 12f, acc(255));
        Instance sh = Fonts.GOLOS_SEMI.getFont(13f);
        Render2D.drawFont(ms, sh, "Тема", px + 18f, cy + 6f - sh.getHeight() / 2f, a(TEXT2));
        float thx = px + 18f + sh.getWidth("Тема") + 10f;
        Render2D.drawRoundedRect(ms, thx, cy + 6f, px + cw - thx, 1f, 0.5f, hl( 18));
        cy += 24f;

        // чипы тем 2×4
        LinkGuiThemePreset[] themes = LinkGuiThemePreset.values();
        float chipGap = 8f, chipW = (cw - chipGap) / 2f, chipH = 28f;
        LinkGuiThemePreset sel = LinkGuiThemeManager.getSelected();
        for (int i = 0; i < themes.length; i++) {
            int col = i % 2, row = i / 2;
            float chx = px + col * (chipW + chipGap);
            float chy = cy + row * (chipH + chipGap);
            LinkGuiThemePreset t = themes[i];
            boolean active = t == sel;
            float thg = anim("thg:" + t.getName(), active ? 1f : 0f); // GLOW-UPDATE §6: 0 0 20px -5px accent·0.55
            float thh = anim("thh:" + t.getName(), hit(mx, my, chx, chy, chipW, chipH) ? 1f : 0f); // ховер (спека 120–150ms)
            glow(ms, chx, chy, chipW, chipH, 10f, 8f, 140, thg);
            Color[] g = chipGradient(t.getName());
            Render2D.drawRoundedVerticalGradient(ms, chx, chy, chipW, chipH, 10f, a(g[0]), a(g[1]));
            Render2D.drawBorder(ms, chx, chy, chipW, chipH, 10f, 0.9f, new ColorRGBA(active
                    ? acc(140)
                    : hl( Math.round(20 + 69 * thh))));
            Render2D.drawCircle(ms, chx + 13f + 4f, chy + chipH / 2f, 4f, a(t.primary()));
            // Текст названия — по цвету САМОГО чипа (у него свой фон), а не текущей темы: иначе на светлой
            // теме тёмный текст на тёмных чипах (Midnight и т.п.) исчезал и чипы читались как «чёрные».
            Color txt = isLightChip(t.getName()) ? a(new Color(0x1a, 0x1f, 0x1c)) : a(new Color(0xdf, 0xe5, 0xe1));
            Render2D.drawFont(ms, Fonts.GOLOS.getFont(12f), t.getName(), chx + 26f, chy + chipH / 2f - Fonts.GOLOS.getFont(12f).getHeight() / 2f, txt);
            if (active) ClickGuiIcons.check(ms, chx + chipW - 14f, chy + chipH / 2f, 12f, acc(255));
            region(chx, chy, chipW, chipH, 0, () -> LinkGuiThemeManager.setTheme(t));
        }
        cy += 4f * (chipH + chipGap) + 4f;

        // ── заголовок «Интерфейс» ──
        ClickGuiIcons.sliders(ms, px + 6f, cy + 6f, 12f, acc(255));
        Render2D.drawFont(ms, sh, "Интерфейс", px + 18f, cy + 6f - sh.getHeight() / 2f, a(TEXT2));
        float ihx = px + 18f + sh.getWidth("Интерфейс") + 10f;
        Render2D.drawRoundedRect(ms, ihx, cy + 6f, px + cw - ihx, 1f, 0.5f, hl( 18));
        cy += 24f;

        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui == null) return;

        // группа 1: масштаб + Применить/Сброс
        float g1H = 13f + 30f + 13f + 34f + 13f;
        group(ms, px, cy, cw, g1H);
        NumberSetting scale = num(ui, "settings.ui.scale");
        float gy = cy + 13f;
        if (scale != null) {
            // Драг меняет ТОЛЬКО значение настройки (без live-превью) — масштаб применяется по «Применить».
            drawPanelSlider(ms, px + 13f, gy, cw - 26f, "Масштаб интерфейса", pct(scale.getValue()), sliderT(scale), mx, my,
                    v -> setNum(scale, v), px + 13f, cw - 26f);
            gy += 30f + 13f;
        }
        float btnW = (cw - 26f - 8f) / 2f;
        float apT = anim("btnApply", hit(mx, my, px + 13f, gy, btnW, 34f) ? 1f : 0f); // ховер: осветление (спека)
        glow(ms, px + 13f, gy, btnW, 34f, 7f, 7f, 128, 1f); // GLOW-UPDATE §4: 0 0 16px -4px accent·0.5
        Render2D.drawRoundedRect(ms, px + 13f, gy, btnW, 34f, 7f, mix(acc(255), hl( 255), 0.07f * apT));
        Render2D.drawFont(ms, Fonts.UNBOUNDED.getFont(10f), "Применить", px + 13f + (btnW - Fonts.UNBOUNDED.getFont(10f).getWidth("Применить")) / 2f, gy + 17f - Fonts.UNBOUNDED.getFont(10f).getHeight() / 2f, a(INK));
        region(px + 13f, gy, btnW, 34f, 0, ui::applyScaleSetting);
        float rbx = px + 13f + btnW + 8f;
        float rsT = anim("btnReset", hit(mx, my, rbx, gy, btnW, 34f) ? 1f : 0f);
        card(ms, rbx, gy, btnW, 34f, 7f);
        if (rsT > 0.01f) {
            Render2D.drawBorder(ms, rbx, gy, btnW, 34f, 7f, 0.9f,
                    new ColorRGBA(hl( Math.round(61 * rsT))));
        }
        Render2D.drawFont(ms, Fonts.UNBOUNDED.getFont(10f), "Сброс", rbx + (btnW - Fonts.UNBOUNDED.getFont(10f).getWidth("Сброс")) / 2f, gy + 17f - Fonts.UNBOUNDED.getFont(10f).getHeight() / 2f, a(mix(TEXT4, TEXT2, rsT)));
        region(rbx, gy, btnW, 34f, 0, ui::resetScaleSetting);
        cy += g1H + 12f;

        // группа 2: трекер курсора · прозрачность · клавиша открытия
        float g2H = 13f + 20f + 13f + 30f + 13f + 10f + 8f + 36f + 13f;
        group(ms, px, cy, cw, g2H);
        gy = cy + 13f;
        // трекер курсора
        Render2D.drawFont(ms, Fonts.UNBOUNDED_L.getFont(10f), "Трекер курсора", px + 13f, gy + 4f, a(TEXT3));
        drawToggle(ms, px + cw - 13f - 36f, gy, "cursorTracker", guiContext.isCursorTracker());
        region(px + cw - 13f - 36f, gy, 36f, 20f, 0, () -> guiContext.setCursorTracker(!guiContext.isCursorTracker()));
        gy += 20f + 13f;
        // прозрачность
        NumberSetting transp = num(ui, "settings.ui.transparency");
        if (transp != null) {
            drawPanelSlider(ms, px + 13f, gy, cw - 26f, "Прозрачность GUI", pctRaw(transp.getValue()), sliderT(transp), mx, my,
                    v -> setNum(transp, v), px + 13f, cw - 26f);
            gy += 30f + 13f;
        }
        // клавиша открытия
        Render2D.drawFont(ms, Fonts.UNBOUNDED_L.getFont(10f), "Клавиша открытия", px + 13f, gy, a(TEXT3));
        gy += 8f + Fonts.UNBOUNDED_L.getFont(10f).getHeight() * 0f + 6f;
        boolean okListen = bindKind == BindKind.OPENKEY;
        String okTxt = okListen ? "..." : bindText(ui.getBind()).toUpperCase(java.util.Locale.ROOT).replace('_', ' ');
        float okT = anim("openkey", hit(mx, my, px + 13f, gy, cw - 26f, 36f) || okListen ? 1f : 0f);
        drawDashedField(ms, px + 13f, gy, cw - 26f, 36f, okTxt, okT);
        region(px + 13f, gy, cw - 26f, 36f, 0, () -> startBind(BindKind.OPENKEY, null, null));
        cy += g2H + 12f;

        // группа 3: HUD стиль/форма · GUI стиль · скорборд (вкл/цвет/непрозрачность/радиус) · таб (вкл/цвет)
        float g3H = 13f + 52f + 13f + 52f + 13f + 20f + 13f + 20f + 13f + 30f + 13f + 30f + 13f + 20f + 13f + 20f + 13f;
        group(ms, px, cy, cw, g3H);
        gy = cy + 13f;
        // HUD стиль / форма (в ряд)
        EnumSetting<?> hs = enumS(ui, "settings.ui.hud_style");
        EnumSetting<?> hsh = enumS(ui, "settings.ui.hud_shape");
        float halfW = (cw - 26f - 8f) / 2f;
        drawLabeledSelect(ms, px + 13f, gy, halfW, "Стиль HUD", hs != null ? I18n.translate(hs.currentEnumName()) : "—", mx, my, hs);
        drawLabeledSelect(ms, px + 13f + halfW + 8f, gy, halfW, "Форма HUD", hsh != null ? I18n.translate(hsh.currentEnumName()) : "—", mx, my, hsh);
        gy += 52f + 13f;
        // GUI стиль
        EnumSetting<?> gs = enumS(ui, "settings.ui.gui");
        drawLabeledSelect(ms, px + 13f, gy, cw - 26f, "Стиль GUI", gs != null ? I18n.translate(gs.currentEnumName()) : "—", mx, my, gs);
        gy += 52f + 13f;
        // свой скорборд
        BooleanSetting sb = bool(ui, "settings.ui.scoreboard.custom");
        Render2D.drawFont(ms, Fonts.UNBOUNDED_L.getFont(10f), "Свой скорборд", px + 13f, gy + 4f, a(TEXT3));
        if (sb != null) {
            drawToggle(ms, px + cw - 13f - 36f, gy, "sbtgl", sb.getValue());
            region(px + cw - 13f - 36f, gy, 36f, 20f, 0, () -> sb.setValue(!sb.getValue()));
        }
        gy += 20f + 13f;
        // цвет фона скорборда — свотч открывает общий HSV-пикер (RGB), альфа правится слайдером ниже
        drawColorRow(ms, "Цвет фона скорборда", px + 13f, gy, cw - 26f, color(ui, "settings.ui.scoreboard.background"));
        gy += 20f + 13f;
        // непрозрачность (альфа фона скорборда)
        ColorSetting sbg = color(ui, "settings.ui.scoreboard.background");
        if (sbg != null) {
            float alphaT = sbg.getValue().getAlpha() / 255f;
            drawPanelSlider(ms, px + 13f, gy, cw - 26f, "Непрозрачность", Math.round(alphaT * 100) + "%", alphaT, mx, my,
                    v -> { Color c = sbg.getValue(); sbg.setValue(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(MathHelper.clamp(v, 0f, 1f) * 255))); },
                    px + 13f, cw - 26f);
        }
        gy += 30f + 13f;
        // радиус углов
        NumberSetting sr = num(ui, "settings.ui.scoreboard.radius");
        if (sr != null) {
            drawPanelSlider(ms, px + 13f, gy, cw - 26f, "Радиус углов", String.valueOf(Math.round(sr.getValue())), sliderT(sr), mx, my,
                    v -> setNum(sr, v), px + 13f, cw - 26f);
        }
        gy += 30f + 13f;
        // свой таб (кастомный фон таба — скругление + блюр + свой цвет)
        BooleanSetting tb = bool(ui, "settings.ui.tab.custom");
        Render2D.drawFont(ms, Fonts.UNBOUNDED_L.getFont(10f), "Свой таб", px + 13f, gy + 4f, a(TEXT3));
        if (tb != null) {
            drawToggle(ms, px + cw - 13f - 36f, gy, "tabtgl", tb.getValue());
            region(px + cw - 13f - 36f, gy, 36f, 20f, 0, () -> tb.setValue(!tb.getValue()));
        }
        gy += 20f + 13f;
        // цвет фона таба — свотч открывает общий HSV-пикер (RGB+альфа)
        drawColorRow(ms, "Цвет фона таба", px + 13f, gy, cw - 26f, color(ui, "settings.ui.tab.color"));

        // футер
        Instance ff = Fonts.GOLOS_SEMI.getFont(11.5f);
        float fy = y + h - 20f - ff.getHeight();
        drawSpaced(ms, ff, "linken", px, fy, a(MUTED), -0.03f * 11.5f);
        float flw = widthSpaced(ff, "linken", -0.03f * 11.5f);
        Render2D.drawFont(ms, ff, ".", px + flw, fy, acc(255));
        Instance vf = Fonts.PLEXMONO.getFont(9.5f);
        String ver = "1.21.11 · 1.0.147";
        float verW = vf.getWidth(ver);
        Render2D.drawFont(ms, vf, ver, px + cw - verW, fy, a(MUTED4));
        float fhx = px + flw + ff.getWidth(".") + 10f;
        Render2D.drawRoundedRect(ms, fhx, fy + ff.getHeight() / 2f, px + cw - verW - 10f - fhx, 1f, 0.5f, hl( 16));
    }

    private void drawLabeledSelect(Matrix3x2fStack ms, float x, float y, float w, String label, String value, float mx, float my, EnumSetting<?> setting) {
        Render2D.drawFont(ms, Fonts.UNBOUNDED_L.getFont(10f), label, x, y, a(TEXT3));
        float fy = y + 16f, fh = 36f;
        boolean hov = hit(mx, my, x, fy, w, fh);
        float t = anim("sel:" + label, hov ? 1f : 0f); // плавный ховер (спека 120–150ms)
        Render2D.drawRoundedVerticalGradient(ms, x, fy, w, fh, 7f, selTop(), selBot());
        Render2D.drawBorder(ms, x, fy, w, fh, 7f, 0.8f, new ColorRGBA(hl( Math.round(24 + 37 * t))));
        Instance vf = Fonts.UNBOUNDED.getFont(10f);
        drawMorphValue(ms, vf, "selv:" + label, clip(vf, value, w - 30f), x + 12f, fy + fh / 2f - vf.getHeight() / 2f, a(TEXT2));
        ClickGuiIcons.chevronDown(ms, x + w - 14f, fy + fh / 2f, 12f, a(MUTED3));
        if (setting != null) {
            // Пока этот селектор раскрыт — обновляем прямоугольник-якорь (панель может анимироваться/двигаться),
            // чтобы список-оверлей всегда рисовался ровно под полем.
            if (setting == activeEnumDropdown) { edX = x; edY = fy; edW = w; edH = fh; }
            // Клик по полю РАСКРЫВАЕТ список (сверху вниз), а не циклит значение.
            region(x, fy, w, fh, 0, () -> toggleEnumDropdown(setting, x, fy, w, fh));
        }
    }

    /** Открыть/закрыть enum-дропдаун селектора. */
    private void toggleEnumDropdown(EnumSetting<?> setting, float fx, float fy, float fw, float fh) {
        if (setting == null) return;
        if (activeEnumDropdown == setting) { activeEnumDropdown = null; return; }
        activeEnumDropdown = setting;
        edX = fx; edY = fy; edW = fw; edH = fh;
    }

    /** Рисует раскрытый список вариантов enum-селектора под полем-якорем (сверху вниз), поверх панели.
     *  {@code open} 0..1 — фактор анимации: панель растёт по высоте, строки каскадом проявляются. */
    private void drawEnumDropdown(Matrix3x2fStack ms, float mx, float my, float open) {
        EnumSetting<?> setting = lastEnumDropdown;
        if (setting == null) return;
        Object[] values = ((Enum<?>) setting.getValue()).getDeclaringClass().getEnumConstants();
        if (values == null || values.length == 0) return;
        Object cur = setting.getValue();

        float rowH = 24f;
        float listX = edX;
        float listW = edW;
        float listH = values.length * rowH + 8f;
        float listY = edY + edH + 4f;
        // Не влезает вниз — раскрываем ВВЕРХ от поля.
        if (listY + listH > DH) listY = Math.max(0f, edY - listH - 4f);
        edListY = listY;
        edRowH = rowH;

        // Ease-out: панель быстро выезжает и мягко доводится.
        float e = 1f - (1f - open) * (1f - open) * (1f - open);
        float revealH = Math.max(1f, listH * e);

        card(ms, listX, listY, listW, revealH, 8f);
        Instance vf = Fonts.UNBOUNDED.getFont(10f);
        float ry = listY + 4f;
        for (Object o : values) {
            // Строка проявляется по мере раскрытия панели (каскад сверху вниз).
            float rowA = MathHelper.clamp((listY + revealH - ry) / rowH, 0f, 1f);
            if (rowA > 0.02f) {
                boolean sel = o == cur;
                boolean hov = revealH >= listH - 1f && hit(mx, my, listX + 3f, ry, listW - 6f, rowH);
                float t = anim("edrow:" + setting.getName() + ":" + o, (sel || hov) ? 1f : 0f);
                if (t > 0.01f) {
                    Render2D.drawRoundedRect(ms, listX + 4f, ry + 1f, listW - 8f, rowH - 2f, 6f,
                            acc(Math.round((sel ? 42 : 26) * t * rowA)));
                }
                String name = o instanceof Nameable n ? I18n.translate(n.getName()) : String.valueOf(o);
                Color c = sel ? acc(Math.round(255 * rowA)) : a(TEXT2, Math.round(220 * rowA));
                Render2D.drawFont(ms, vf, clip(vf, name, listW - 22f), listX + 12f,
                        ry + rowH / 2f - vf.getHeight() / 2f, c);
            }
            ry += rowH;
        }
        Render2D.drawBorder(ms, listX, listY, listW, revealH, 8f, 0.8f, new ColorRGBA(hl(Math.round(34 * e))));
    }

    // ═══════════════════════ CONTEXT MENU ═══════════════════════
    private void openContext(Module m, float x, float y) {
        ctxModule = m; ctxX = x; ctxY = y;
    }

    private void drawContextMenu(Matrix3x2fStack ms, float mx, float my) {
        if (ctxModule == null) return;
        drawingContext = true;
        final Module m = ctxModule;
        // Спека v2: «⋯» — прочее (биндинг, сброс). «Все настройки» добавляем ТОЛЬКО модулям,
        // чьи настройки не помещаются в карточку левой колонки (Color/String/List/Action).
        boolean complex = false;
        for (Setting<?> s : m.getSettings()) {
            if (!(s instanceof BooleanSetting || s instanceof EnumSetting<?>
                    || s instanceof NumberSetting || s instanceof BindSetting
                    || s instanceof ColorSetting || s instanceof StringSetting
                    || s instanceof ActionSetting)) { complex = true; break; }
        }
        float w = 160f, itemH = 30f;
        float h = itemH * (complex ? 3f : 2f) + 12f;
        float x = Math.min(ctxX, DW - w - 10f), y = Math.min(ctxY, DH - h - 10f);
        card(ms, x, y, w, h, 8f);
        final float mcx = x + w * 0.5f, mcy = y;
        String[] labels = complex
                ? new String[]{ "Биндинг", "Все настройки", "Сброс настроек" }
                : new String[]{ "Биндинг", "Сброс настроек" };
        Runnable[] acts = complex
                ? new Runnable[]{
                        () -> startBind(BindKind.MODULE, m, null),
                        () -> LinkGuiSettings.open(m, mcx, mcy),
                        () -> { for (Setting<?> s : m.getSettings()) s.reset(); } }
                : new Runnable[]{
                        () -> startBind(BindKind.MODULE, m, null),
                        () -> { for (Setting<?> s : m.getSettings()) s.reset(); } };
        for (int i = 0; i < labels.length; i++) {
            float iy = y + 6f + i * itemH;
            boolean hov = hit(mx, my, x + 4f, iy, w - 8f, itemH);
            float ht = anim("ctx:" + i, hov ? 1f : 0f); // плавная подсветка пункта
            if (ht > 0.01f) Render2D.drawRoundedRect(ms, x + 4f, iy, w - 8f, itemH, 6f, hl( Math.round(12 * ht)));
            Render2D.drawFont(ms, Fonts.GOLOS.getFont(11.5f), labels[i], x + 12f, iy + itemH / 2f - Fonts.GOLOS.getFont(11.5f).getHeight() / 2f, a(i == labels.length - 1 ? DANGER : TEXT2));
            final Runnable act = acts[i];
            region(x + 4f, iy, w - 8f, itemH, 0, () -> { act.run(); ctxModule = null; });
        }
        drawingContext = false;
    }

    // ═══════════════════════ FRIENDS / WAYPOINTS PANELS ═══════════════════════
    /** Макс. значение анимации любого из трёх боковых окон (пин-раскладка реагирует на любое). */
    private float sidePanelValue() {
        return Math.max(Math.max(rightPanelAnim.getValue(), configsPanelAnim.getValue()),
                Math.max(friendsPanelAnim.getValue(), waypointsPanelAnim.getValue()));
    }

    /** Открыто (по цели) ли хоть одно боковое окно — стабильно при кроссфейде переключения. */
    private boolean anySidePanelOpen() {
        return rightPanelAnim.isForward() || friendsPanelAnim.isForward()
                || waypointsPanelAnim.isForward() || configsPanelAnim.isForward();
    }

    /** Переключение бокового окна: остальные закрываются (все на одном правом слоте x=1540). */
    private void toggleSidePanel(Animation target) {
        boolean open = !target.isForward();
        rightPanelAnim.update(target == rightPanelAnim && open);
        friendsPanelAnim.update(target == friendsPanelAnim && open);
        waypointsPanelAnim.update(target == waypointsPanelAnim && open);
        configsPanelAnim.update(target == configsPanelAnim && open);
        panelInputs().forEach(sb -> sb.setFocused(false));
    }

    /** Фокус на одном текстовом поле снимает фокус с остальных. */
    /** Все текстовые поля боковых окон (для роутинга ввода/фокуса). */
    private java.util.List<SearchBar> panelInputs() {
        return java.util.List.of(friendInput, waypointInput, wpXInput, wpYInput, wpZInput, configInput);
    }

    private void focusInput(SearchBar which) {
        searchBar.setFocused(false);
        activeStringSetting = null;
        for (SearchBar sb : panelInputs()) sb.setFocused(sb == which);
    }

    private Integer parseIntOrNull(String s) {
        try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; }
    }

    /** Добавить метку по введённым координатам X/Y/Z (и имени). */
    private void addWaypointFromCoords() {
        String name = waypointInput.getSearchText().trim();
        Integer x = parseIntOrNull(wpXInput.getSearchText());
        Integer y = parseIntOrNull(wpYInput.getSearchText());
        Integer z = parseIntOrNull(wpZInput.getSearchText());
        if (name.isEmpty() || x == null || y == null || z == null) return;
        LinkenVisuals.getInstance().getWaypointManager().add(new Waypoint(name, x, y, z));
        waypointInput.setSearchText(""); wpXInput.setSearchText(""); wpYInput.setSearchText(""); wpZInput.setSearchText("");
    }

    /** Enter в полях метки: если координаты заполнены — добавить по ним, иначе — на текущей позиции. */
    private void addWaypointSmart() {
        boolean coords = parseIntOrNull(wpXInput.getSearchText()) != null
                && parseIntOrNull(wpYInput.getSearchText()) != null
                && parseIntOrNull(wpZInput.getSearchText()) != null;
        if (coords) addWaypointFromCoords(); else addWaypointFromInput();
    }

    /** Фокус на строковой настройке (ник и т.п.) — снимает фокус с прочих полей. */
    private void focusStringSetting(StringSetting ss) {
        activeStringSetting = ss;
        searchBar.setFocused(false);
        friendInput.setFocused(false);
        waypointInput.setFocused(false);
    }

    /** Клип текста с ХВОСТА (показываем конец строки — видно, что печатаешь в переполненном поле). */
    private String clipTail(Instance f, String s, float maxW) {
        if (f.getWidth(s) <= maxW) return s;
        int i = 0;
        while (i < s.length() && f.getWidth(s.substring(i)) > maxW) i++;
        return s.substring(Math.min(i, s.length()));
    }

    private void addFriendFromInput() {
        String name = friendInput.getSearchText().trim();
        if (name.isEmpty()) return;
        var fm = LinkenVisuals.getInstance().getFriendManager();
        if (!fm.isFriendIgnoreCase(name)) fm.add(name);
        friendInput.setSearchText("");
        LinkenVisuals inst = LinkenVisuals.getInstance();
        if (inst.getConfigManager() != null) inst.getConfigManager().forceSave();
    }

    private void addWaypointFromInput() {
        String name = waypointInput.getSearchText().trim();
        if (name.isEmpty() || mc.player == null) return;
        BlockPos p = mc.player.getBlockPos();
        LinkenVisuals.getInstance().getWaypointManager().add(new Waypoint(name, p.getX(), p.getY(), p.getZ()));
        waypointInput.setSearchText("");
    }

    /** Текстовое поле ввода: бокс + текст/плейсхолдер + мигающий курсор + регион фокуса. */
    private void drawTextInput(Matrix3x2fStack ms, float x, float y, float w, float h, SearchBar input, String placeholder, String animId) {
        boolean f = input.isFocused();
        float t = anim(animId, f ? 1f : 0f);
        Render2D.drawRoundedRect(ms, x, y, w, h, 7f, hl(10));
        WindowGuiRender.drawRoundedOutlineRect(ms, x, y, w, h, 7f, 0.9f, mix(hl(18), acc(150), t));
        Instance inf = Fonts.GOLOS.getFont(11.5f);
        String txt = input.getSearchText();
        float tx = x + 12f, tyc = y + h / 2f - inf.getHeight() / 2f;
        if (txt.isEmpty() && !f) {
            Render2D.drawFont(ms, inf, placeholder, tx, tyc, a(MUTED3));
        } else {
            String shown = clip(inf, txt, w - 22f);
            Render2D.drawFont(ms, inf, shown, tx, tyc, a(TEXT2));
            if (f) {
                float cwid = inf.getWidth(shown);
                float blink = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() % 1000) / 1000.0 * Math.PI * 2.0));
                Render2D.drawRoundedRect(ms, tx + cwid + 1f, y + h / 2f - 7f, 1f, 14f, 0.5f, a(TEXT2, Math.round(255 * blink)));
            }
        }
        region(x, y, w, h, 0, () -> focusInput(input));
    }

    /** Фон+шапка бокового окна В СТИЛЕ КАРТОЧКИ НАСТРОЙКИ: card() + тайл-иконка + название + подпись +
     *  крестик закрытия (красный на ховере). Тема-адаптивно (card/a/hl/acc). Возвращает Y контента. */
    private float sideWindowFrame(Matrix3x2fStack ms, float x, float y, float w, float h, DockIcon icon,
                                  String title, String subtitle, Animation closeAnim, float mx, float my) {
        ClickGuiFirePass.addPanel(currentContext, x, y, w, h, 8f, fade);
        card(ms, x, y, w, h, 8f);
        float pad = 16f, cx = x + pad, cy = y + 14f, cw = w - pad * 2f;
        float tile = 30f;
        glow(ms, cx, cy, tile, tile, 6f, 6f, 90, 1f);
        Render2D.drawRoundedRect(ms, cx, cy, tile, tile, 6f, acc(28));
        Render2D.drawBorder(ms, cx, cy, tile, tile, 6f, 0.8f, new ColorRGBA(acc(90)));
        icon.draw(ms, cx + tile / 2f, cy + tile / 2f, 15f, acc(255));
        Render2D.drawFont(ms, Fonts.UNBOUNDED.getFont(12f), title, cx + tile + 11f, cy + 2f, a(TEXT));
        if (subtitle != null) Render2D.drawFont(ms, Fonts.GOLOS.getFont(11f), subtitle, cx + tile + 11f, cy + 15f, a(MUTED3));
        // крестик закрытия окна (как у карточки настройки модуля)
        float bs = 22f, bx = cx + cw - bs, byy = cy;
        float xt = anim("swx:" + title, hit(mx, my, bx, byy, bs, bs) ? 1f : 0f);
        Render2D.drawRoundedRect(ms, bx, byy, bs, bs, 6f, mix(hl(8), a(DANGER, 34), xt));
        if (xt > 0.01f) Render2D.drawBorder(ms, bx, byy, bs, bs, 6f, 0.9f, new ColorRGBA(a(DANGER, Math.round(150 * xt))));
        ClickGuiIcons.close(ms, bx + bs / 2f, byy + bs / 2f, 12f, a(mix(MUTED, DANGER, xt)));
        region(bx, byy, bs, bs, 0, () -> { closeAnim.update(false); panelInputs().forEach(sb -> sb.setFocused(false)); });
        Render2D.drawRoundedRect(ms, cx, cy + 30f + 6f, cw, 1f, 0.5f, hl(16));
        return cy + 30f + 12f;
    }

    /** Окно менеджера друзей (стиль карточки настройки): ввод ника + «Добавить» + список с удалением. */
    private void drawFriendsPanel(DrawContext ctx, float mx, float my) {
        Matrix3x2fStack ms = ctx.getMatrices();
        float x = 1540f, y = 90f, w = 340f, h = 600f;
        List<String> friends = LinkenVisuals.getInstance().getFriendManager().getFriends();
        float cy = sideWindowFrame(ms, x, y, w, h, ClickGuiIcons::users, "Друзья", friends.size() + " в списке", friendsPanelAnim, mx, my);
        float px = x + 16f, cw = w - 32f;
        // строка ввода + кнопка «Добавить»
        float addW = 82f, inH = 32f, inW = cw - addW - 8f;
        drawTextInput(ms, px, cy, inW, inH, friendInput, "Ник игрока...", "frinF");
        boolean canAdd = !friendInput.getSearchText().trim().isEmpty();
        float adT = anim("fradd", hit(mx, my, px + inW + 8f, cy, addW, inH) && canAdd ? 1f : 0f);
        Render2D.drawRoundedRect(ms, px + inW + 8f, cy, addW, inH, 7f, canAdd ? mix(acc(210), acc(255), adT) : hl(12));
        Instance bf = Fonts.UNBOUNDED.getFont(9.5f);
        Render2D.drawFont(ms, bf, "Добавить", px + inW + 8f + (addW - bf.getWidth("Добавить")) / 2f, cy + inH / 2f - bf.getHeight() / 2f, canAdd ? a(INK) : a(MUTED3));
        if (canAdd) region(px + inW + 8f, cy, addW, inH, 0, this::addFriendFromInput);
        cy += inH + 12f;
        // список друзей (скроллируемый)
        float listTop = cy, listBot = y + h - 16f, listH = listBot - listTop;
        float rowH = 34f, rowGap = 6f;
        int n = friends.size();
        float maxSc = Math.max(0f, n * (rowH + rowGap) - listH);
        friendsScrollTarget = MathHelper.clamp(friendsScrollTarget, 0f, maxSc);
        friendsScroll += (friendsScrollTarget - friendsScroll) * (1f - (float) Math.exp(-16f * dt));
        // Немного шире области списка, чтобы рамку строки/кнопку удаления не подрезало по правому краю
        // (в пределах панели: cw+6 → правый край 1870 < панель 1880).
        Render2D.startScissor(ctx, px, listTop, cw + 6f, listH);
        if (n == 0) {
            Instance ef = Fonts.GOLOS.getFont(11.5f);
            String em = "Список друзей пуст";
            Render2D.drawFont(ms, ef, em, px + (cw - ef.getWidth(em)) / 2f, listTop + 20f, a(MUTED3));
        } else {
            Instance nf = Fonts.GOLOS.getFont(12f);
            Instance pf = Fonts.ICONS2.getFont(11f);
            for (int i = 0; i < n; i++) {
                String name = friends.get(i);
                float ry = listTop + i * (rowH + rowGap) - friendsScroll;
                if (ry + rowH < listTop - 4 || ry > listBot + 4) continue;
                Render2D.drawRoundedRect(ms, px, ry, cw, rowH, 7f, hl(8));
                Render2D.drawBorder(ms, px, ry, cw, rowH, 7f, 0.8f, new ColorRGBA(hl(16)));
                Render2D.drawFont(ms, pf, "A", px + 12f, ry + rowH / 2f - pf.getHeight() / 2f, a(MUTED));
                Render2D.drawFont(ms, nf, clip(nf, name, cw - 62f), px + 28f, ry + rowH / 2f - nf.getHeight() / 2f, a(TEXT2));
                float bs = 22f, bxx = px + cw - bs - 6f, byy = ry + (rowH - bs) / 2f;
                float xt = anim("frx:" + name, hit(mx, my, bxx, byy, bs, bs) ? 1f : 0f);
                Render2D.drawRoundedRect(ms, bxx, byy, bs, bs, 6f, mix(hl(8), a(DANGER, 34), xt));
                ClickGuiIcons.close(ms, bxx + bs / 2f, byy + bs / 2f, 11f, a(mix(MUTED, DANGER, xt)));
                if (ry >= listTop - 1 && ry + rowH <= listBot + 1) {
                    final String fn = name;
                    region(bxx, byy, bs, bs, 0, () -> {
                        LinkenVisuals.getInstance().getFriendManager().remove(fn);
                        LinkenVisuals inst = LinkenVisuals.getInstance();
                        if (inst.getConfigManager() != null) inst.getConfigManager().forceSave();
                    });
                }
            }
        }
        Render2D.stopScissor(ctx);
    }

    /** Окно менеджера меток (стиль карточки настройки): имя + «Добавить здесь» + список координат с удалением. */
    private void drawWaypointsPanel(DrawContext ctx, float mx, float my) {
        Matrix3x2fStack ms = ctx.getMatrices();
        float x = 1540f, y = 90f, w = 340f, h = 600f;
        List<Waypoint> wps = LinkenVisuals.getInstance().getWaypointManager().getWaypoints();
        float cy = sideWindowFrame(ms, x, y, w, h, ClickGuiIcons::mapPin, "Метки", wps.size() + " меток", waypointsPanelAnim, mx, my);
        float px = x + 16f, cw = w - 32f;
        // поле имени
        drawTextInput(ms, px, cy, cw, 32f, waypointInput, "Имя метки...", "wpinF");
        cy += 32f + 8f;
        // строка ввода координат X / Y / Z
        float cw3 = (cw - 12f) / 3f, ch = 30f;
        drawTextInput(ms, px, cy, cw3, ch, wpXInput, "X", "wpX");
        drawTextInput(ms, px + cw3 + 6f, cy, cw3, ch, wpYInput, "Y", "wpY");
        drawTextInput(ms, px + 2f * (cw3 + 6f), cy, cw3, ch, wpZInput, "Z", "wpZ");
        cy += ch + 8f;
        // кнопки: «Здесь» (текущая позиция) + «По коорд.» (введённые X/Y/Z)
        boolean hasName = !waypointInput.getSearchText().trim().isEmpty();
        boolean canHere = hasName && mc.player != null;
        boolean canCoords = hasName && parseIntOrNull(wpXInput.getSearchText()) != null
                && parseIntOrNull(wpYInput.getSearchText()) != null && parseIntOrNull(wpZInput.getSearchText()) != null;
        float bh = 32f, bw2 = (cw - 8f) / 2f;
        Instance bf = Fonts.UNBOUNDED.getFont(9.5f);
        float hT = anim("wphere", hit(mx, my, px, cy, bw2, bh) && canHere ? 1f : 0f);
        Render2D.drawRoundedRect(ms, px, cy, bw2, bh, 7f, canHere ? mix(acc(210), acc(255), hT) : hl(12));
        String hlbl = mc.player == null ? "Нет мира" : "Здесь";
        Render2D.drawFont(ms, bf, hlbl, px + (bw2 - bf.getWidth(hlbl)) / 2f, cy + bh / 2f - bf.getHeight() / 2f, canHere ? a(INK) : a(MUTED3));
        if (canHere) region(px, cy, bw2, bh, 0, this::addWaypointFromInput);
        float cT = anim("wpcoord", hit(mx, my, px + bw2 + 8f, cy, bw2, bh) && canCoords ? 1f : 0f);
        Render2D.drawRoundedRect(ms, px + bw2 + 8f, cy, bw2, bh, 7f, canCoords ? mix(acc(210), acc(255), cT) : hl(12));
        String clbl = "По коорд.";
        Render2D.drawFont(ms, bf, clbl, px + bw2 + 8f + (bw2 - bf.getWidth(clbl)) / 2f, cy + bh / 2f - bf.getHeight() / 2f, canCoords ? a(INK) : a(MUTED3));
        if (canCoords) region(px + bw2 + 8f, cy, bw2, bh, 0, this::addWaypointFromCoords);
        cy += bh + 12f;
        // список меток (скроллируемый)
        float listTop = cy, listBot = y + h - 16f, listH = listBot - listTop;
        float rowH = 40f, rowGap = 6f;
        int n = wps.size();
        float maxSc = Math.max(0f, n * (rowH + rowGap) - listH);
        waypointsScrollTarget = MathHelper.clamp(waypointsScrollTarget, 0f, maxSc);
        waypointsScroll += (waypointsScrollTarget - waypointsScroll) * (1f - (float) Math.exp(-16f * dt));
        Render2D.startScissor(ctx, px, listTop, cw, listH);
        if (n == 0) {
            Instance ef = Fonts.GOLOS.getFont(11.5f);
            String em = "Меток пока нет";
            Render2D.drawFont(ms, ef, em, px + (cw - ef.getWidth(em)) / 2f, listTop + 20f, a(MUTED3));
        } else {
            Instance nf = Fonts.GOLOS.getFont(12f);
            Instance cof = Fonts.PLEXMONO.getFont(9.5f);
            for (int i = 0; i < n; i++) {
                Waypoint wp = wps.get(i);
                float ry = listTop + i * (rowH + rowGap) - waypointsScroll;
                if (ry + rowH < listTop - 4 || ry > listBot + 4) continue;
                Render2D.drawRoundedRect(ms, px, ry, cw, rowH, 7f, hl(8));
                Render2D.drawBorder(ms, px, ry, cw, rowH, 7f, 0.8f, new ColorRGBA(hl(16)));
                ClickGuiIcons.mapPin(ms, px + 14f, ry + rowH / 2f, 15f, acc(220));
                Render2D.drawFont(ms, nf, clip(nf, wp.getName(), cw - 70f), px + 30f, ry + 9f, a(TEXT2));
                String coords = (int) wp.getX() + ", " + (int) wp.getY() + ", " + (int) wp.getZ();
                Render2D.drawFont(ms, cof, coords, px + 30f, ry + 23f, a(MUTED2));
                float bs = 22f, bxx = px + cw - bs - 6f, byy = ry + (rowH - bs) / 2f;
                float xt = anim("wpx:" + i + wp.getName(), hit(mx, my, bxx, byy, bs, bs) ? 1f : 0f);
                Render2D.drawRoundedRect(ms, bxx, byy, bs, bs, 6f, mix(hl(8), a(DANGER, 34), xt));
                ClickGuiIcons.close(ms, bxx + bs / 2f, byy + bs / 2f, 11f, a(mix(MUTED, DANGER, xt)));
                if (ry >= listTop - 1 && ry + rowH <= listBot + 1) {
                    final Waypoint fw = wp;
                    region(bxx, byy, bs, bs, 0, () -> LinkenVisuals.getInstance().getWaypointManager().remove(fw));
                }
            }
        }
        Render2D.stopScissor(ctx);
    }

    /** Окно менеджера конфигов (стиль «Друзья»): имя + «Сохранить» + список (клик — загрузить, крестик — удалить). */
    private void drawConfigsPanel(DrawContext ctx, float mx, float my) {
        Matrix3x2fStack ms = ctx.getMatrices();
        float x = 1540f, y = 90f, w = 340f, h = 600f;
        var cm = LinkenVisuals.getInstance().getConfigManager();
        List<String> configs = cm == null ? java.util.List.of() : cm.listConfigNames();
        String current = cm == null ? "" : cm.getCurrentConfigName();
        float cy = sideWindowFrame(ms, x, y, w, h, ClickGuiIcons::gear, "Конфиги", configs.size() + " шт.", configsPanelAnim, mx, my);
        float px = x + 16f, cw = w - 32f;
        // строка ввода + кнопка «Сохранить» (текущие настройки под этим именем, и он становится активным)
        float addW = 96f, inH = 32f, inW = cw - addW - 8f;
        drawTextInput(ms, px, cy, inW, inH, configInput, "Имя конфига...", "cfinF");
        boolean canSave = cm != null && !configInput.getSearchText().trim().isEmpty();
        float adT = anim("cfsave", hit(mx, my, px + inW + 8f, cy, addW, inH) && canSave ? 1f : 0f);
        Render2D.drawRoundedRect(ms, px + inW + 8f, cy, addW, inH, 7f, canSave ? mix(acc(210), acc(255), adT) : hl(12));
        Instance bf = Fonts.UNBOUNDED.getFont(9.5f);
        String sl = "Сохранить";
        Render2D.drawFont(ms, bf, sl, px + inW + 8f + (addW - bf.getWidth(sl)) / 2f, cy + inH / 2f - bf.getHeight() / 2f, canSave ? a(INK) : a(MUTED3));
        if (canSave) region(px + inW + 8f, cy, addW, inH, 0, this::saveConfigFromInput);
        cy += inH + 8f;
        // Кнопка «Обновить текущий» — явно пересохраняет активный конфиг текущими настройками.
        float ubh = 28f;
        boolean canUpd = cm != null && !current.isBlank();
        String updLbl = "Обновить текущий: " + current;
        Instance uf = Fonts.UNBOUNDED.getFont(9f);
        float uT = anim("cfupd", hit(mx, my, px, cy, cw, ubh) && canUpd ? 1f : 0f);
        Render2D.drawRoundedRect(ms, px, cy, cw, ubh, 7f, mix(hl(10), acc(40), uT));
        Render2D.drawBorder(ms, px, cy, cw, ubh, 7f, 0.8f, new ColorRGBA(mix(hl(20), acc(160), uT)));
        String updClip = clip(uf, updLbl, cw - 16f);
        Render2D.drawFont(ms, uf, updClip, px + (cw - uf.getWidth(updClip)) / 2f, cy + ubh / 2f - uf.getHeight() / 2f, a(mix(MUTED, TEXT2, uT)));
        if (canUpd) region(px, cy, cw, ubh, 0, this::updateCurrentConfig);
        cy += ubh + 12f;
        // список конфигов (скроллируемый)
        float listTop = cy, listBot = y + h - 16f, listH = listBot - listTop;
        float rowH = 34f, rowGap = 6f;
        int n = configs.size();
        float maxSc = Math.max(0f, n * (rowH + rowGap) - listH);
        configsScrollTarget = MathHelper.clamp(configsScrollTarget, 0f, maxSc);
        configsScroll += (configsScrollTarget - configsScroll) * (1f - (float) Math.exp(-16f * dt));
        Render2D.startScissor(ctx, px, listTop, cw + 6f, listH);
        if (n == 0) {
            Instance ef = Fonts.GOLOS.getFont(11.5f);
            String em = "Конфигов пока нет";
            Render2D.drawFont(ms, ef, em, px + (cw - ef.getWidth(em)) / 2f, listTop + 20f, a(MUTED3));
        } else {
            Instance nf = Fonts.GOLOS.getFont(12f);
            Instance byf = Fonts.GOLOS.getFont(12f);   // «by ник» — было 9, +~30%
            Instance actf = Fonts.GOLOS.getFont(11f);  // «активен» — было 9, +~20%
            for (int i = 0; i < n; i++) {
                String name = configs.get(i);
                boolean isActive = name.equalsIgnoreCase(current);
                float ry = listTop + i * (rowH + rowGap) - configsScroll;
                if (ry + rowH < listTop - 4 || ry > listBot + 4) continue;
                Render2D.drawRoundedRect(ms, px, ry, cw, rowH, 7f, isActive ? acc(26) : hl(8));
                Render2D.drawBorder(ms, px, ry, cw, rowH, 7f, 0.8f, new ColorRGBA(isActive ? acc(120) : hl(16)));
                ClickGuiIcons.gear(ms, px + 14f, ry + rowH / 2f, 13f, isActive ? acc(230) : a(MUTED));
                float cvy = ry + rowH / 2f;
                // Имя + «by ник» в одну строку: имя слева, «by ник» правее в свободной зоне.
                float rightLimit = px + cw - (isActive ? 62f : 34f); // до «активен»/крестика
                String nameClip = clip(nf, name, rightLimit - (px + 28f));
                Render2D.drawFont(ms, nf, nameClip, px + 28f, cvy - nf.getHeight() / 2f, a(TEXT2));
                String author = cm == null ? "" : cm.getConfigAuthor(name);
                if (author != null && !author.isBlank()) {
                    float byX = px + 28f + nf.getWidth(nameClip) + 12f;
                    float byW = rightLimit - byX;
                    if (byW > 14f) Render2D.drawFont(ms, byf, clip(byf, "by " + author, byW), byX, cvy - byf.getHeight() / 2f, a(MUTED3));
                }
                if (isActive) {
                    // активный конфиг: метка вместо крестика, клики не нужны
                    String al = "активен";
                    Render2D.drawFont(ms, actf, al, px + cw - actf.getWidth(al) - 12f, ry + rowH / 2f - actf.getHeight() / 2f, acc(220));
                } else {
                    float bs = 22f, bxx = px + cw - bs - 6f, byy = ry + (rowH - bs) / 2f;
                    float xt = anim("cfx:" + name, hit(mx, my, bxx, byy, bs, bs) ? 1f : 0f);
                    Render2D.drawRoundedRect(ms, bxx, byy, bs, bs, 6f, mix(hl(8), a(DANGER, 34), xt));
                    ClickGuiIcons.close(ms, bxx + bs / 2f, byy + bs / 2f, 11f, a(mix(MUTED, DANGER, xt)));
                    if (ry >= listTop - 1 && ry + rowH <= listBot + 1) {
                        final String fn = name;
                        // клик по строке (кроме крестика) — загрузить конфиг
                        region(px, ry, cw - bs - 14f, rowH, 0, () -> loadConfigByName(fn));
                        region(bxx, byy, bs, bs, 0, () -> removeConfigByName(fn));
                    }
                }
            }
        }
        Render2D.stopScissor(ctx);
    }

    private void saveConfigFromInput() {
        var cm = LinkenVisuals.getInstance().getConfigManager();
        if (cm == null) return;
        String name = configInput.getSearchText().trim();
        if (name.isEmpty()) return;
        try {
            cm.saveConfig(name);
            cm.loadConfig(name); // сохранённый становится активным
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[LinkGui] Save config failed: " + name, e);
        }
        configInput.setSearchText("");
        configInput.setFocused(false);
    }

    private void loadConfigByName(String name) {
        var cm = LinkenVisuals.getInstance().getConfigManager();
        if (cm == null) return;
        try {
            cm.loadConfig(name);
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[LinkGui] Load config failed: " + name, e);
        }
    }

    /** Пересохранить активный конфиг текущими настройками (кнопка «Обновить текущий»). */
    private void updateCurrentConfig() {
        var cm = LinkenVisuals.getInstance().getConfigManager();
        if (cm == null) return;
        try {
            cm.saveConfig(cm.getCurrentConfigName());
            ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotificationManager.getInstance()
                    .addInfoNotification("Конфиг сохранён!");
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[LinkGui] Update current config failed", e);
        }
    }

    private void removeConfigByName(String name) {
        var cm = LinkenVisuals.getInstance().getConfigManager();
        if (cm != null) cm.removeConfig(name);
    }

    // ═══════════════════════ COMMON WIDGETS ═══════════════════════
    /**
     * 1.21.4 profile card: 2D head/skin row plus the 3D player viewer. The 3D entity is drawn
     * after the deferred GUI pass through the existing previewPending root-layer hook.
     */
    /**
     * 1.21.4 profile card verbatim: open/close is driven by profileAnim (fade + 10px slide),
     * the pinned cards move down by (profH + 14), and the viewer contains the 3D player model.
     */
    private void drawProfileCard(Matrix3x2fStack ms, float x, float y, float w, float mx, float my, float pt) {
        float profH = 16f + 24f + 40f + 12f + 238f + 28f;
        if (pt <= 0.01f) {
            return;
        }
        float prevFade = fade;
        fade = prevFade * pt;
        ms.pushMatrix();
        ms.translate(-10f * (1f - pt), 0f);
        ClickGuiFirePass.addPanel(currentContext, x, y, w, profH, 8f, fade);
        glow(ms, x, y, w, profH, 8f, 16f, 40, 1f);
        card(ms, x, y, w, profH, 8f);
        float cx = x + 16f, cy = y + 16f, cw = w - 32f;

        Instance hf = Fonts.GOLOS_SEMI.getFont(13f);
        Instance pif = Fonts.ICONS2.getFont(12f);
        Render2D.drawFont(ms, pif, "A", cx + 6f - pif.getWidth("A") / 2f, cy + 6f - pif.getHeight() / 2f, acc(255));
        Render2D.drawFont(ms, hf, "Профиль", cx + 18f, cy + 6f - hf.getHeight() / 2f, a(TEXT2));
        float hlX = cx + 18f + hf.getWidth("Профиль") + 10f;
        Render2D.drawRoundedRect(ms, hlX, cy + 6f, cx + cw - hlX, 1f, 0.5f, hl(18));

        float rowY = cy + 24f;
        drawSkinHead(ms, cx, rowY, 40f, 8f);
        String nick = nick();
        Instance nf = Fonts.UNBOUNDED.getFont(13.5f);
        Render2D.drawFont(ms, nf, nick, cx + 52f, rowY + 3f, a(TEXT));
        Instance lf = Fonts.GOLOS.getFont(11.5f);
        float planY = rowY + 25f;
        Render2D.drawFont(ms, lf, "План: ", cx + 52f, planY, a(MUTED2));
        Render2D.drawFont(ms, lf, "Free", cx + 52f + lf.getWidth("План: "), planY, acc(255));

        float vY = cy + 24f + 40f + 12f, vH = 238f;
        // Separate fire shape around the 3D skin preview itself.
        ClickGuiFirePass.addPanel(currentContext, cx, vY, cw, vH, 8f, fade);
        Render2D.drawRoundedRect(ms, cx, vY, cw, vH, 8f, viewerBg());
        Render2D.drawBorder(ms, cx, vY, cw, vH, 8f, 0.8f, new ColorRGBA(hl(16)));
        Render2D.drawRoundedVerticalGradient(ms, cx, vY + vH - 72f, cw, 72f, 8f,
                acc(0), acc(lightTheme ? 30 : 46));
        if (mc.player != null) {
            int ex = Math.round(cx + 16f), ey = Math.round(vY + 12f);
            int ew = Math.round(cw - 32f), eh = Math.round(vH - 22f);
            float cf = MathHelper.clamp(getUiFade(), 0f, 1f);
            float ease = pt * pt * (3f - 2f * pt) * cf;
            int entSize = Math.round((Math.min(ew, eh) / 3.05f) * ease);
            if (entSize >= 2) {
                // The deferred GUI pass needs screen coordinates; convert through the current card matrix.
                org.joml.Matrix3x2f pose = new org.joml.Matrix3x2f(ms);
                org.joml.Vector2f p0 = pose.transformPosition(ex, ey, new org.joml.Vector2f());
                org.joml.Vector2f p1 = pose.transformPosition(ex + ew, ey + eh, new org.joml.Vector2f());
                previewPending = true;
                previewX1 = Math.round(p0.x);
                previewY1 = Math.round(p0.y);
                previewX2 = Math.round(p1.x);
                previewY2 = Math.round(p1.y);
                float scale = Math.max(0.01f, (p1.x - p0.x) / Math.max(1f, ew));
                previewScale = Math.max(2f, entSize * scale);
            }
        }
        Render2D.drawRoundedRect(ms, cx + 24f, vY + vH - 12f, cw - 48f, 1f, 0.5f, acc(90));

        ms.popMatrix();
        fade = prevFade;
    }

    private void card(Matrix3x2fStack ms, float x, float y, float w, float h, float r) {
        // Карточки левой колонки стоят прямо на блюр-фоне — заливка почти непрозрачна, иначе сквозь
        // них просвечивает игровой HUD (артефакт). Спека даёт 0.85; поднято до ~0.96.
        Render2D.drawRoundedVerticalGradient(ms, x, y, w, h, r, cardTop(), cardBot());
        Render2D.drawBorder(ms, x, y, w, h, r, 0.9f, new ColorRGBA(hl( 23)));
        Render2D.drawRoundedRect(ms, x + r, y + 1f, w - r * 2f, 1f, 0.5f, hl( 13));
    }

    private void group(Matrix3x2fStack ms, float x, float y, float w, float h) {
        Render2D.drawRoundedRect(ms, x, y, w, h, 10f, hl( 8));
        Render2D.drawBorder(ms, x, y, w, h, 10f, 0.8f, new ColorRGBA(hl( 16)));
    }

        private void drawCorner(Matrix3x2fStack ms, float cx, float cy, boolean topLeft) {
        Color c = acc(238);
        float s = 20f, th = 2f;
        if (topLeft) {
            glow(ms, cx, cy, s, th, 1f, 6f, 80, 1f);
            glow(ms, cx, cy, th, s, 1f, 6f, 80, 1f);
            Render2D.drawRoundedRect(ms, cx, cy, s, th, th / 2f, c);
            Render2D.drawRoundedRect(ms, cx, cy, th, s, th / 2f, c);
        } else {
            glow(ms, cx - s, cy - th, s, th, 1f, 6f, 80, 1f);
            glow(ms, cx - th, cy - s, th, s, 1f, 6f, 80, 1f);
            Render2D.drawRoundedRect(ms, cx - s, cy - th, s, th, th / 2f, c);
            Render2D.drawRoundedRect(ms, cx - th, cy - s, th, s, th / 2f, c);
        }
    }

    private void drawToggle(Matrix3x2fStack ms, float x, float y, String id, boolean on) {
        float w = 36f, h = 20f;
        float t = anim(id, on ? 1f : 0f);
        glow(ms, x, y, w, h, h / 2f, 7f, 140, t); // GLOW-UPDATE §1: тумблер ON 0 0 14px -2px accent·0.55, гаснет с выключением
        Color track = mix(hl( 36), acc(255), t);
        Render2D.drawRoundedRect(ms, x, y, w, h, h / 2f, track);
        float knob = 14f;
        float kx = x + 3f + (w - 6f - knob) * t;
        Color kc = mix(a(0x8b, 0x96, 0x8f, 255), a(INK, 255), t);
        Render2D.drawRoundedRect(ms, kx, y + 3f, knob, knob, knob / 2f, kc);
    }

    private void drawValueChip(Matrix3x2fStack ms, float rightX, float y, String id, String value, float mx, float my) {
        Instance f = Fonts.UNBOUNDED.getFont(9.5f);
        float w = f.getWidth(value) + 20f, h = 26f;
        float x = rightX - w, cy = y + 8f;
        float t = anim("chip:" + id, hit(mx, my, x, cy - h / 2f, w, h) ? 1f : 0f); // плавный ховер (спека)
        Render2D.drawRoundedRect(ms, x, cy - h / 2f, w, h, 6f, hl( 10));
        Render2D.drawBorder(ms, x, cy - h / 2f, w, h, 6f, 0.8f, new ColorRGBA(hl( Math.round(26 + 34 * t))));
        drawMorphValue(ms, f, "chipv:" + id, value, x + 10f, cy - h / 2f + h / 2f - f.getHeight() / 2f, a(TEXT2));
    }

    private float chipWidth(String value) {
        return Fonts.UNBOUNDED.getFont(9.5f).getWidth(value) + 20f;
    }

    private void drawInlineSlider(Matrix3x2fStack ms, float x, float y, float w, NumberSetting ns, float mx, float my) {
        String sid = "sld:" + ns.getName();
        // значение-чип справа (акцент) — число меняется ПЛАВНО (роллинг к реальному значению), не прыжком
        String val = smoothNumStr(sid, ns);
        Instance vf = Fonts.UNBOUNDED.getFont(9.5f);
        float vw = vf.getWidth(val) + 12f;
        Render2D.drawRoundedRect(ms, x + w - vw, y, vw, 18f, 4f, acc(20));
        Render2D.drawBorder(ms, x + w - vw, y, vw, 18f, 4f, 0.7f, new ColorRGBA(acc(70)));
        Render2D.drawFont(ms, vf, val, x + w - vw + 6f, y + 9f - vf.getHeight() / 2f, acc(255));
        // трек (чуть толще, скруглённый); ход кноба с ОТСТУПОМ от краёв — иначе кноб вылезал за трек и обрезался.
        // Трек опущен так, чтобы кноб у максимума не наезжал на чип значения сверху.
        float trackH = 4f, ty = y + 24.5f - trackH / 2f, tw = w, inset = 8f;
        Render2D.drawRoundedRect(ms, x, ty, tw, trackH, trackH / 2f, hl( 41));
        float t = sliderT(ns);
        // Во время использования кноб едет по СЫРОЙ позиции курсора (плавно, непрерывно), а не по
        // квантованному значению — иначе он дёргается ступеньками по шагу. Вне драга — плавно к значению.
        boolean active = sid.equals(activeSliderId);
        float dt2;
        if (active) { dt2 = activeSliderRaw; hover.put(sid, dt2); } else { dt2 = anim(sid, t); }
        float knobCx = x + inset + (tw - 2f * inset) * dt2;
        Render2D.drawRoundedRect(ms, x, ty, knobCx - x, trackH, trackH / 2f, acc(255));
        drawSliderKnob(ms, knobCx, ty + trackH / 2f, sid, active);
        sliderRegion(x, ty - 9f, tw, 20f, x + inset, tw - 2f * inset, sid, v -> setNum(ns, v));
    }

    private void drawPanelSlider(Matrix3x2fStack ms, float x, float y, float w, String label, String value, float t,
                                 float mx, float my, java.util.function.Consumer<Float> set, float trackX, float trackW) {
        Render2D.drawFont(ms, Fonts.UNBOUNDED_L.getFont(10f), label, x, y, a(TEXT3));
        Instance vf = Fonts.UNBOUNDED.getFont(9.5f);
        float vw = vf.getWidth(value) + 12f;
        Render2D.drawRoundedRect(ms, x + w - vw, y - 2f, vw, 18f, 4f, acc(20));
        Render2D.drawBorder(ms, x + w - vw, y - 2f, vw, 18f, 4f, 0.7f, new ColorRGBA(acc(70)));
        Render2D.drawFont(ms, vf, value, x + w - vw + 6f, y - 2f + 9f - vf.getHeight() / 2f, acc(255));
        float trackH = 4f, ty = y + 23.5f - trackH / 2f, inset = 8f;
        Render2D.drawRoundedRect(ms, x, ty, w, trackH, trackH / 2f, hl( 41));
        // Во время использования кноб едет по сырой позиции курсора (плавно), вне драга — плавно к значению.
        String sid = "sldp:" + label;
        float tc = MathHelper.clamp(t, 0f, 1f);
        boolean active = sid.equals(activeSliderId);
        float dt2;
        if (active) { dt2 = activeSliderRaw; hover.put(sid, dt2); } else { dt2 = anim(sid, tc); }
        float knobCx = x + inset + (w - 2f * inset) * dt2;
        Render2D.drawRoundedRect(ms, x, ty, knobCx - x, trackH, trackH / 2f, acc(255));
        drawSliderKnob(ms, knobCx, ty + trackH / 2f, sid, active);
        sliderRegion(x, ty - 9f, w, 20f, x + inset, w - 2f * inset, sid, set);
    }

    /** Кноб слайдера: при использовании плавно становится «пожирнее» и обзаводится акцентной обводкой. */
    private void drawSliderKnob(Matrix3x2fStack ms, float cx, float cy, String sid, boolean active) {
        float grab = anim("sldgrab:" + sid, active ? 1f : 0f);
        float r = 5f + 1.7f * grab; // толще при использовании
        // Кноб — SDF-скругление (квадрат с радиусом = r ⇒ идеально СГЛАЖЕННЫЙ круг, без «пикселей» триангл-фана).
        if (grab > 0.01f) {
            float rr = r + 2.4f; // акцентное кольцо-ореол при использовании
            Render2D.drawRoundedRect(ms, cx - rr, cy - rr, rr * 2f, rr * 2f, rr, acc(Math.round(210 * grab)));
        }
        Render2D.drawRoundedRect(ms, cx - r, cy - r, r * 2f, r * 2f, r, a(TEXT));
        // тонкая акцентная обводка — чёткость и «не плоскость» кноба (сильнее при использовании)
        Render2D.drawBorder(ms, cx - r, cy - r, r * 2f, r * 2f, r, 0.9f,
                new ColorRGBA(acc(Math.round(110 + 120 * grab))));
    }

    private void drawDashedField(Matrix3x2fStack ms, float x, float y, float w, float h, String text, float activeT) {
        // пунктирная рамка; ховер/прослушивание — плавный переход к акцентному пунктиру (спека)
        float t = MathHelper.clamp(activeT, 0f, 1f);
        Color bc = mix(hl( 46), acc(128), t);
        float dash = 4f, gap = 3f;
        for (float dx = x; dx < x + w; dx += dash + gap) {
            Render2D.drawRoundedRect(ms, dx, y, Math.min(dash, x + w - dx), 1f, 0.5f, bc);
            Render2D.drawRoundedRect(ms, dx, y + h - 1f, Math.min(dash, x + w - dx), 1f, 0.5f, bc);
        }
        for (float dy = y; dy < y + h; dy += dash + gap) {
            Render2D.drawRoundedRect(ms, x, dy, 1f, Math.min(dash, y + h - dy), 0.5f, bc);
            Render2D.drawRoundedRect(ms, x + w - 1f, dy, 1f, Math.min(dash, y + h - dy), 0.5f, bc);
        }
        Color fg = mix(a(TEXT2), acc(255), t);
        GuiIcons.draw(ms, GuiIcons.KEYBOARD, x + w / 2f - Fonts.UNBOUNDED.getFont(9.5f).getWidth(text) / 2f - 9f, y + h / 2f, 13f, fg);
        Instance f = Fonts.UNBOUNDED.getFont(9.5f);
        drawSpaced(ms, f, text, x + w / 2f - f.getWidth(text) / 2f + 4f, y + h / 2f - f.getHeight() / 2f, fg, 0.18f * 9.5f);
    }

    // ═══════════════════════ INPUT ═══════════════════════
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (close || mc.currentScreen != this) return false;
        float[] d = design(mouseX, mouseY);
        float dmx = d[0], dmy = d[1];

        if (bindKind != BindKind.NONE) {
            if (button != 0) { applyBindMouse(button); } else { clearBind(); }
            return true;
        }
        // окна настроек — поверх всего (реверс-обход, клик поднимает окно наверх стопки)
        List<LinkGuiSettingsWindow> settingsWindows = LinkGuiSettings.getWindows();
        for (int i = settingsWindows.size() - 1; i >= 0; i--) {
            LinkGuiSettingsWindow window = settingsWindows.get(i);
            if (window.mouseClicked(dmx, dmy, button)) {
                searchBar.setFocused(false);
                if (settingsWindows.contains(window)) {
                    settingsWindows.remove(window);
                    settingsWindows.add(window);
                }
                return true;
            }
        }
        // контекст-меню — только его регионы, иначе закрыть
        if (ctxModule != null) {
            for (int i = regions.size() - 1; i >= 0; i--) {
                Region r = regions.get(i);
                if (r.ctx && r.button == button && hit(dmx, dmy, r.x, r.y, r.w, r.h)) { r.action.run(); return true; }
            }
            ctxModule = null;
            return true;
        }
        // цветовой пикер — приоритетный хит-тест, пока открыт (перекрывает регионы под ним)
        if (activeColorSetting != null && button == 0) {
            if (hit(dmx, dmy, cpCloseX, cpCloseY, cpCloseS, cpCloseS)) { activeColorSetting = null; cpDrag = 0; return true; }
            if (hit(dmx, dmy, cpSvX, cpSvY, cpSvW, cpSvH)) { cpDrag = 1; updateColorDrag(dmx, dmy); return true; }
            if (hit(dmx, dmy, cpHueX, cpHueY, cpHueW, cpHueH)) { cpDrag = 2; updateColorDrag(dmx, dmy); return true; }
            if (hit(dmx, dmy, cpOpX, cpOpY, cpOpW, cpOpH)) { cpDrag = 3; updateColorDrag(dmx, dmy); return true; }
            if (hit(dmx, dmy, cpPanX, cpPanY, cpPanW, cpPanH)) return true; // клик внутри панели (не по зоне) — поглотить
            // Клик вне панели — закрыть и поглотить, чтобы этот же клик не «переоткрыл» пикер по свотчу.
            activeColorSetting = null; cpDrag = 0; return true;
        }
        // enum-дропдаун — приоритетный хит-тест, пока открыт (перекрывает регионы под ним)
        if (activeEnumDropdown != null && button == 0) {
            EnumSetting<?> setting = activeEnumDropdown;
            if (hit(dmx, dmy, edX, edY, edW, edH)) { activeEnumDropdown = null; return true; } // повторный клик по полю — свернуть
            Object[] values = ((Enum<?>) setting.getValue()).getDeclaringClass().getEnumConstants();
            if (values != null) {
                float ry = edListY + 4f;
                for (Object o : values) {
                    if (hit(dmx, dmy, edX + 3f, ry, edW - 6f, edRowH)) {
                        if (o instanceof Nameable n) setting.setEnumValue(n.getName());
                        activeEnumDropdown = null;
                        return true;
                    }
                    ry += edRowH;
                }
            }
            activeEnumDropdown = null; return true; // клик вне списка/поля — закрыть и поглотить
        }
        // плеер (клики только когда профиль виден — при налезании окна он скрыт)
        if (profileVis > 0.5f && player.mouseClicked(dmx, dmy, button)) return true;
        // регионы (сверху вниз)
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.button != button || !hit(dmx, dmy, r.x, r.y, r.w, r.h)) continue;
            if (r.slider != null) {
                activeSlider = r.slider; activeSliderId = r.sliderId; sliderTrackX = r.trackX; sliderTrackW = r.trackW;
                activeSliderRaw = MathHelper.clamp((dmx - sliderTrackX) / sliderTrackW, 0f, 1f);
                activeSlider.set(activeSliderRaw);
            } else if (r.action != null) {
                r.action.run();
            }
            return true;
        }
        searchBar.setFocused(false);
        for (SearchBar sb : panelInputs()) sb.setFocused(false);
        activeStringSetting = null;
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        float[] d = design(mouseX, mouseY);
        for (LinkGuiSettingsWindow window : LinkGuiSettings.getWindows()) {
            window.mouseReleased(d[0], d[1], button);
        }
        if (button == 0) { activeSlider = null; activeSliderId = null; cpDrag = 0; player.mouseReleased(d[0], d[1], button); }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        float[] d = design(mouseX, mouseY);
        for (LinkGuiSettingsWindow window : LinkGuiSettings.getWindows()) {
            window.mouseDragged(d[0], d[1], button);
        }
        if (activeSlider != null && button == 0) {
            activeSliderRaw = MathHelper.clamp((d[0] - sliderTrackX) / sliderTrackW, 0f, 1f);
            activeSlider.set(activeSliderRaw);
            return true;
        }
        player.mouseDragged(d[0], d[1]);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (close || mc.currentScreen != this) return false;
        float[] d = design(mouseX, mouseY);
        for (LinkGuiSettingsWindow window : LinkGuiSettings.getWindows()) {
            window.mouseScrolled(d[0], d[1], h, v);
        }
        if (hit(d[0], d[1], gridRect[0], gridRect[1], gridRect[2], gridRect[3])) {
            gridScrollTarget -= (float) v * 45f;
        }
        // скролл списков боковых окон (правый слот x=1540)
        if (d[0] > 1540f) {
            if (friendsPanelAnim.isForward()) friendsScrollTarget -= (float) v * 40f;
            else if (waypointsPanelAnim.isForward()) waypointsScrollTarget -= (float) v * 40f;
            else if (configsPanelAnim.isForward()) configsScrollTarget -= (float) v * 40f;
        }
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (close || mc.currentScreen != this) return false;
        if (bindKind != BindKind.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) applyBindClear();
            else applyBindKey(keyCode);
            return true;
        }
        // Захват бинда в окне настроек: клавиша идёт в окно, поиск/тоггл GUI её не съедают.
        if (hasWindowBinding()) {
            for (LinkGuiSettingsWindow window : LinkGuiSettings.getWindows()) {
                window.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        if (activeStringSetting != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { activeStringSetting = null; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String v = activeStringSetting.getValue();
                if (!v.isEmpty()) activeStringSetting.setValue(v.substring(0, v.length() - 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && ctrlDown()) {
                String clip = GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
                if (clip != null) activeStringSetting.setValue(activeStringSetting.getValue() + clip);
                return true;
            }
            return true; // прочие клавиши поглощаем, пока редактируем строку
        }
        if (keyCode == GLFW.GLFW_KEY_F && ctrlDown()) { searchBar.setFocused(true); return true; }
        for (SearchBar sb : panelInputs()) {
            if (!sb.isFocused()) continue;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { sb.setFocused(false); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (sb == friendInput) addFriendFromInput(); else addWaypointSmart();
                return true;
            }
            sb.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (searchBar.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { searchBar.setFocused(false); return true; }
            searchCaretMark = System.currentTimeMillis();
            if (searchBar.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true;
        }
        for (LinkGuiSettingsWindow window : LinkGuiSettings.getWindows()) {
            window.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (close || mc.currentScreen != this) return false;
        if (hasWindowBinding()) return true;
        if (activeStringSetting != null) {
            if (!Character.isISOControl(chr)) activeStringSetting.setValue(activeStringSetting.getValue() + chr);
            return true;
        }
        for (SearchBar sb : panelInputs()) {
            if (!sb.isFocused()) continue;
            boolean coord = (sb == wpXInput || sb == wpYInput || sb == wpZInput);
            if (coord && !(Character.isDigit(chr) || chr == '-')) return true; // координаты — только числа
            sb.charTyped(chr, modifiers);
            return true;
        }
        if (searchBar.isFocused()) {
            searchCaretMark = System.currentTimeMillis();
            searchBar.charTyped(chr, modifiers);
            return true;
        }
        for (LinkGuiSettingsWindow window : LinkGuiSettings.getWindows()) {
            window.charTyped(chr, modifiers);
        }
        return super.charTyped(input);
    }

    private boolean hasWindowBinding() {
        for (LinkGuiSettingsWindow window : LinkGuiSettings.getWindows()) {
            if (window.hasActiveBinding()) return true;
        }
        return false;
    }

    // ═══════════════════════ BINDING ═══════════════════════
    private void startBind(BindKind kind, Module m, BindSetting s) {
        bindKind = kind; bindModule = m; bindSetting = s; ctxModule = null;
    }

    private void clearBind() { bindKind = BindKind.NONE; bindModule = null; bindSetting = null; }

    private void applyBindKey(int key) {
        switch (bindKind) {
            case MODULE -> { if (bindModule != null) bindModule.setBind(new Bind(key, false)); }
            case SETTING -> { if (bindSetting != null) { bindSetting.getValue().setKey(key); bindSetting.getValue().setMouse(false); } }
            case OPENKEY -> setOpenKey(new Bind(key, false));
            default -> {}
        }
        clearBind();
    }

    private void applyBindMouse(int button) {
        switch (bindKind) {
            case MODULE -> { if (bindModule != null) bindModule.setBind(new Bind(button, true)); }
            case SETTING -> { if (bindSetting != null) { bindSetting.getValue().setKey(button); bindSetting.getValue().setMouse(true); } }
            case OPENKEY -> setOpenKey(new Bind(button, true));
            default -> {}
        }
        clearBind();
    }

    private void applyBindClear() {
        switch (bindKind) {
            case MODULE -> { if (bindModule != null) bindModule.setBind(new Bind(-1, false)); }
            case SETTING -> { if (bindSetting != null) { bindSetting.getValue().setKey(-1); bindSetting.getValue().setMouse(false); } }
            case OPENKEY -> setOpenKey(new Bind(UiKeyHandler.DEFAULT_KEY, false));
            default -> {}
        }
        clearBind();
    }

    private void setOpenKey(Bind bind) {
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui == null) return;
        ui.setBind(bind);
        LinkenVisuals inst = LinkenVisuals.getInstance();
        if (inst.getConfigManager() != null) inst.getConfigManager().forceSave();
    }

    // ═══════════════════════ HELPERS ═══════════════════════
    private void selectCategory(Category c) {
        selectedCategory = c;
        showAll = false; // выбор категории выключает режим «Все»
        // Скролл к верху АНИМИРУЕМ (не мгновенный gridScroll=0): иначе улетающие карточки замораживались
        // по уже нулевому скроллу и телепортировались. С анимированным скроллом они уходят в gridExit на
        // верных экранных позициях и «отбрасываются» ровно как при поиске — обновление списка идентично.
        gridScrollTarget = 0f;
        // Появление новых функций анимируется по идентичности в цикле грида (см. render).
    }

    private void toggleModule(Module m) {
        if (m.isActionModule()) m.activate(); else m.toggle();
    }

    private String filterCacheQuery = "\u0000";
    private boolean filterCacheAll;
    private Category filterCacheCategory;

    private List<Module> filteredModules() {
        String q = searchBar.getSearchText().trim().toLowerCase(java.util.Locale.ROOT);
        if (q.equals(filterCacheQuery) && showAll == filterCacheAll && selectedCategory == filterCacheCategory) {
            return filteredModuleScratch;
        }
        filterCacheQuery = q;
        filterCacheAll = showAll;
        filterCacheCategory = selectedCategory;
        // Re-evaluate live module state as before, reusing only the output storage.
        List<Module> out = filteredModuleScratch;
        out.clear();
        List<Module> src = (q.isEmpty() && !showAll)
                ? LinkenVisuals.getInstance().getModuleManager().getModules(selectedCategory)
                : LinkenVisuals.getInstance().getModuleManager().getModules();
        for (Module m : src) {
            if (m.getCategory() == Category.Hud) continue;
            if (m instanceof UI) continue; // модуль UI не показываем в гриде — его настройки живут в правой панели
            if (m instanceof ru.method.linkenvisuals.modules.impl.client.Elements) continue; // «Элементы» — теперь dock-кнопка, не карточка
            if (!q.isEmpty()) {
                if (!m.getName().toLowerCase(java.util.Locale.ROOT).contains(q)
                        && !m.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            }
            out.add(m);
        }
        return out;
    }

    private Module moduleByName(String name) {
        for (Module m : LinkenVisuals.getInstance().getModuleManager().getModules())
            if (m.getName().equals(name)) return m;
        return null;
    }

    private void drawCatIcon(Matrix3x2fStack ms, Category c, float cx, float cy, float size, Color color) {
        Identifier tex = GuiIcons.forCategory(c);
        if (tex != null) { GuiIcons.draw(ms, tex, cx, cy, size, a(color)); return; }
        Instance f = Fonts.ICONSNEW.getFont(size);
        Render2D.drawFont(ms, f, c.getIcon(), cx - f.getWidth(c.getIcon()) / 2f, cy - f.getHeight() / 2f, a(color));
    }

    private void drawSkinHead(Matrix3x2fStack ms, float x, float y, float size, float radius) {
        if (mc.player == null) return;
        Identifier tex = GuiSkinTextures.resolveTexture(mc.player);
        if (tex == null) return;
        Color w = a(Color.WHITE);
        Render2D.drawTextureNearest(ms, x, y, size, size, radius, 0.125f, 0.125f, 0.25f, 0.25f, tex, w);
        Render2D.drawTextureNearest(ms, x, y, size, size, radius, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, tex, w);
        Render2D.drawBorder(ms, x, y, size, size, radius, 0.8f, new ColorRGBA(hl( 30)));
    }

    private String desc(Module m) {
        String key = m.getDescription();
        String t = I18n.translate(key);
        return t.equals(key) ? "" : t;
    }

    private String bindText(Bind b) {
        // Подчёркивание в имени клавиши → пробел (RIGHT_SHIFT → RIGHT SHIFT).
        return b == null || b.isEmpty() ? "None" : KeyNameUtils.formatBind(b).replace('_', ' ');
    }

    private String nick() {
        if (mc.player != null) return mc.player.getGameProfile().name();
        return mc.getSession() != null ? mc.getSession().getUsername() : "player";
    }

    private String uid() {
        if (mc.player == null) return "00000";
        return String.valueOf(Math.abs(mc.player.getUuid().hashCode() % 100000));
    }

    private String license() {
        return java.time.LocalDate.now().plusYears(1).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    // числовые настройки
    private NumberSetting num(UI ui, String name) { Setting<?> s = find(ui, name); return s instanceof NumberSetting n ? n : null; }
    private BooleanSetting bool(UI ui, String name) { Setting<?> s = find(ui, name); return s instanceof BooleanSetting b ? b : null; }
    private EnumSetting<?> enumS(UI ui, String name) { Setting<?> s = find(ui, name); return s instanceof EnumSetting<?> e ? e : null; }
    private ColorSetting color(UI ui, String name) { Setting<?> s = find(ui, name); return s instanceof ColorSetting c ? c : null; }
    private Setting<?> find(UI ui, String name) {
        for (Setting<?> s : ui.getSettings()) if (s.getName().equals(name)) return s;
        return null;
    }

    private float sliderT(NumberSetting ns) {
        float r = ns.getMax() - ns.getMin();
        return r <= 0 ? 0f : MathHelper.clamp((ns.getValue() - ns.getMin()) / r, 0f, 1f);
    }
    private void setNum(NumberSetting ns, float t01) {
        float v = ns.getMin() + t01 * (ns.getMax() - ns.getMin());
        float inc = ns.getIncrement();
        if (inc > 0) v = Math.round(v / inc) * inc;
        ns.setValue(MathHelper.clamp(v, ns.getMin(), ns.getMax()));
    }
    private String fmtNum(NumberSetting ns) { return fmtNumVal(ns.getValue(), ns); }
    private String fmtNumVal(float v, NumberSetting ns) {
        return ns.getIncrement() >= 1f ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.2f", v);
    }
    // Сглаженное (роллинг) число для бейджа слайдера: displayed-значение плавно едет к реальному.
    private final java.util.HashMap<String, Float> numDisplay = new java.util.HashMap<>();
    private String smoothNumStr(String sid, NumberSetting ns) {
        float target = ns.getValue();
        float cur = numDisplay.getOrDefault(sid, target);
        cur += (target - cur) * (1f - (float) Math.exp(-14f * dt));
        if (Math.abs(target - cur) < Math.max(0.001f, ns.getIncrement() * 0.25f)) cur = target;
        numDisplay.put(sid, cur);
        return fmtNumVal(cur, ns);
    }
    private String pct(float scale) { return Math.round(scale * 100) + "%"; }
    private String pctRaw(float v) { return Math.round(v) + "%"; }

    // hover/anim сглаживание (time-based; k=20/с → 90% пути за ~115ms — transition 120–150ms из спеки)
    private float anim(String id, float target) {
        // Старт от покоя (0), а не от target: иначе первая же анимация ключа мгновенна (v сразу=target).
        // Это чинит рывок рамки фокуса поиска, первый кадр фейда грида и «пилюли» дока.
        float v = hover.getOrDefault(id, 0f);
        v += (target - v) * (1f - (float) Math.exp(-20f * dt));
        if (Math.abs(target - v) < 0.001f) v = target;
        hover.put(id, v);
        return MathHelper.clamp(v, 0f, 1f);
    }

    // чипы тем — градиенты/светлость из споки
    private Color[] chipGradient(String name) {
        return switch (name) {
            case "Midnight" -> new Color[]{ new Color(0x13, 0x25, 0x1d), new Color(0x0b, 0x17, 0x12) };
            case "Graphite" -> new Color[]{ new Color(0x1e, 0x22, 0x25), new Color(0x14, 0x17, 0x19) };
            case "Amethyst" -> new Color[]{ new Color(0x20, 0x18, 0x30), new Color(0x14, 0x0f, 0x21) };
            case "Ocean" -> new Color[]{ new Color(0x10, 0x20, 0x2e), new Color(0x0a, 0x15, 0x20) };
            case "Rose" -> new Color[]{ new Color(0x2a, 0x15, 0x1d), new Color(0x1b, 0x0d, 0x13) };
            case "Emerald" -> new Color[]{ new Color(0x0e, 0x24, 0x17), new Color(0x08, 0x1a, 0x10) };
            case "Light" -> new Color[]{ new Color(0xee, 0xf1, 0xf5), new Color(0xd8, 0xdd, 0xe6) };
            case "Purple" -> new Color[]{ new Color(0xe7, 0xde, 0xfa), new Color(0xcf, 0xc2, 0xf0) };
            default -> new Color[]{ new Color(0x1e, 0x22, 0x25), new Color(0x14, 0x17, 0x19) };
        };
    }
    private boolean isLightChip(String name) { return name.equals("Light") || name.equals("Purple"); }

    // текстовые утилиты
    private void drawSpaced(Matrix3x2fStack ms, Instance f, String text, float x, float y, Color color, float spacing) {
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            Render2D.drawFont(ms, f, ch, cx, y, color);
            cx += f.getWidth(ch) + spacing;
        }
    }
    private float widthSpaced(Instance f, String text, float spacing) {
        float w = 0f;
        for (int i = 0; i < text.length(); i++) w += f.getWidth(String.valueOf(text.charAt(i))) + spacing;
        return Math.max(0f, w - spacing);
    }
    private String clip(Instance f, String text, float max) {
        if (text == null) return "";
        if (f.getWidth(text) <= max) return text;
        // "..." вместо U+2026: глифа «…» нет в атласах (charset U+0020–U+045F), он рисовался пустотой.
        while (text.length() > 1 && f.getWidth(text + "...") > max) text = text.substring(0, text.length() - 1);
        return text + "...";
    }
    private List<String> wrap(Instance f, String text, float max, int maxLines) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (f.getWidth(test) > max && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
                if (lines.size() == maxLines - 1) break;
            } else cur = new StringBuilder(test);
        }
        String rest = cur.toString();
        if (lines.size() < maxLines) lines.add(clip(f, rest, max));
        return lines;
    }

    // цвет + fade
    private Color a(int r, int g, int b, int al) { return new Color(r, g, b, Math.max(0, Math.min(255, Math.round(al * fade)))); }
    private Color a(Color c) { return a(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()); }
    private Color a(Color c, int al) { return a(c.getRed(), c.getGreen(), c.getBlue(), al); }
    private Color acc(int al) { return a(accent.getRed(), accent.getGreen(), accent.getBlue(), al); }

    // ── адаптация под тему: тёмная (по умолчанию) / светлая (Light, Purple) ──
    /** Пересчитывает текстовые токены под яркость фона выбранной темы. Зовётся каждый кадр. */
    private void updateThemeColors() {
        LinkGuiThemePreset t = LinkGuiThemeManager.getSelected();
        Color bg = t != null ? t.background() : new Color(15, 18, 23);
        float targetLum = (0.299f * bg.getRed() + 0.587f * bg.getGreen() + 0.114f * bg.getBlue()) / 255f;
        if (displayedBgLum < 0f) displayedBgLum = targetLum;
        displayedBgLum += (targetLum - displayedBgLum) * (1f - (float) Math.exp(-12f * dt));
        lightTheme = displayedBgLum > 0.6f;
        // Токены зависят только от lightTheme — пересобираем 9 Color лишь при смене темы, а не каждый кадр.
        if (themeTokensLight != null && themeTokensLight == lightTheme) return;
        themeTokensLight = lightTheme;
        if (lightTheme) {
            TEXT = new Color(0x1a, 0x1f, 0x26); TEXT2 = new Color(0x2c, 0x32, 0x3b);
            TEXT3 = new Color(0x40, 0x47, 0x52); TEXT4 = new Color(0x5c, 0x64, 0x70);
            MUTED = new Color(0x78, 0x80, 0x8c); MUTED2 = new Color(0x8e, 0x96, 0xa1);
            MUTED3 = new Color(0xa6, 0xad, 0xb7); MUTED4 = new Color(0xbc, 0xc2, 0xcb);
            INK = new Color(0x12, 0x16, 0x1c);
        } else {
            TEXT = new Color(0xee, 0xf2, 0xef); TEXT2 = new Color(0xdf, 0xe5, 0xe1);
            TEXT3 = new Color(0xc3, 0xcc, 0xc7); TEXT4 = new Color(0xa8, 0xb1, 0xac);
            MUTED = new Color(0x8b, 0x96, 0x8f); MUTED2 = new Color(0x78, 0x83, 0x7d);
            MUTED3 = new Color(0x5d, 0x68, 0x62); MUTED4 = new Color(0x4c, 0x56, 0x51);
            INK = new Color(0x0b, 0x12, 0x0e);
        }
    }

    /** Hairline/рамка: белая на тёмной теме, тёмная на светлой (иначе не видна). */
    private Color hl(int alpha) { return lightTheme ? a(18, 22, 28, alpha) : a(255, 255, 255, alpha); }

    // Адаптивные заливки панелей/карточек — выводятся из фона ВЫБРАННОЙ ТЕМЫ (displayedThemeBg), а НЕ из
    // фиксированного зелёно-чёрного. Тёмные темы: панель чуть светлее фона, инсеты — темнее; ON-карточка
    // тонируется акцентом темы. Светлые темы: почти белые поверхности, инсет чуть темнее фона.
    // Так Graphite → серо-чёрный фон, Ocean → сине-чёрный, Emerald → зелёно-чёрный и т.д.
    private Color themeBg()   { return displayedThemeBg != null ? displayedThemeBg : new Color(15, 18, 23); }
    private Color bgMix(Color target, float amt, int alpha) { Color m = mix(themeBg(), target, amt); return a(m.getRed(), m.getGreen(), m.getBlue(), alpha); }
    private Color bgLite(float amt, int alpha) { return bgMix(java.awt.Color.WHITE, amt, alpha); }
    private Color bgDark(float amt, int alpha) { return bgMix(java.awt.Color.BLACK, amt, alpha); }

    private Color winTop()    { return lightTheme ? bgLite(0.55f, 248) : bgLite(0.03f, 248); }
    private Color winBot()    { return lightTheme ? bgLite(0.03f, 248) : bgDark(0.60f, 248); }
    private Color panelFill() { return lightTheme ? bgLite(0.50f, 248) : bgDark(0.50f, 248); }
    private Color cardTop()   { return lightTheme ? bgLite(0.85f, 245) : bgLite(0.05f, 245); }
    private Color cardBot()   { return lightTheme ? bgLite(0.30f, 245) : bgDark(0.06f, 245); }
    private Color cardOffTop(){ return lightTheme ? bgLite(0.85f, 205) : bgLite(0.05f, 178); }
    private Color cardOffBot(){ return lightTheme ? bgLite(0.25f, 205) : bgDark(0.06f, 178); }
    private Color cardOnTop() { return lightTheme ? mix(bgLite(0.85f, 225), acc(225), 0.14f) : bgMix(accent, 0.16f, 204); }
    private Color cardOnBot() { return lightTheme ? mix(bgLite(0.55f, 225), acc(225), 0.10f) : bgMix(accent, 0.09f, 204); }
    private Color selTop()    { return lightTheme ? bgLite(0.85f, 205) : bgLite(0.05f, 179); }
    private Color selBot()    { return lightTheme ? bgLite(0.25f, 205) : bgDark(0.06f, 179); }
    private Color viewerBg()  { return lightTheme ? bgDark(0.04f, 210) : bgDark(0.68f, 140); }

    private Color mix(Color x, Color y2, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return new Color(
                Math.round(x.getRed() + (y2.getRed() - x.getRed()) * t),
                Math.round(x.getGreen() + (y2.getGreen() - x.getGreen()) * t),
                Math.round(x.getBlue() + (y2.getBlue() - x.getBlue()) * t),
                Math.round(x.getAlpha() + (y2.getAlpha() - x.getAlpha()) * t));
    }

    // ── неоновая подсветка: цвет из accent+альфа. GLOW-UPDATE §Поведение: глоу СТАТИЧЕН (не пульсирует),
    // появляется/гаснет только вместе с состоянием элемента (t) тем же transition, что и ховеры. ──
    /** Мягкий ореол по контуру прямоугольника. Рисуется ПОД элементом. t = прогресс активного состояния [0..1]. */
    private void glow(Matrix3x2fStack ms, float x, float y, float w, float h, float radius, float spread, int baseAlpha, float t) {
        int al = Math.round(baseAlpha * GLOW_SCALE * MathHelper.clamp(t, 0f, 1f));
        if (al <= 1) return;
        // Soft SDF halo, same as the settings menu/HUD. The border shader draws
        // visible thin lines at panel edges in 1.21.11 instead of a glow.
        Render2D.drawSoftGlow(ms, x, y, w, h, radius, spread, acc(al));
    }

    /** Ореол вокруг кноба слайдера (белая точка) — accent+альфа. */
    private void glowCircle(Matrix3x2fStack ms, float cx, float cy, float r, float spread, int baseAlpha) {
        int al = Math.max(0, Math.min(255, Math.round(baseAlpha * GLOW_SCALE)));
        if (al <= 1) return;
        Render2D.drawSoftGlow(ms, cx - r, cy - r, r * 2f, r * 2f, r, spread, acc(al));
    }

    private boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void region(float x, float y, float w, float h, int button, Runnable action) {
        Region r = new Region();
        r.x = x; r.y = y; r.w = w; r.h = h; r.button = button; r.action = action; r.ctx = ctxModule != null && drawingContext;
        regions.add(r);
    }
    /** Регион карточки грида, ОБРЕЗАННЫЙ по видимой области грида — клики вне грида (в топбаре/над плеером)
     *  не срабатывают на скролл-скрытых карточках (клик по верху не включает функцию под кнопкой). */
    private void regionG(float x, float y, float w, float h, int button, Runnable action) {
        if (!gridCardInteractive) return; // гаснущая («отбрасываемая») карточка кликов не ловит
        float top = gridRect[1], bot = gridRect[1] + gridRect[3];
        float y0 = Math.max(y, top), y1 = Math.min(y + h, bot);
        if (y1 <= y0) return;
        region(x, y0, w, y1 - y0, button, action);
    }
    private void sliderRegion(float x, float y, float w, float h, float trackX, float trackW, String id, java.util.function.Consumer<Float> set) {
        Region r = new Region();
        r.x = x; r.y = y; r.w = w; r.h = h; r.button = 0; r.slider = set::accept; r.trackX = trackX; r.trackW = trackW; r.sliderId = id;
        regions.add(r);
    }
    private boolean drawingContext;

    private interface Slider01 { void set(float v01); }
    private static final class Region {
        float x, y, w, h; int button; Runnable action; Slider01 slider; float trackX, trackW; boolean ctx; String sliderId;
    }

    // ═══════════════════════ LIFECYCLE (Link GUI) ═══════════════════════
    @Override public void init() {
        debugCaptured = false;
        resetOpenAnimation(); searchBar.reset(); player.init();
        // Залипшие окна настроек прошлых сессий не должны всплывать «чёрными» панелями.
        LinkGuiSettings.clear();
        LinkGuiThemeManager.applyForced(LinkGuiThemeManager.getSelected());
    }
    @Override public void tick() { player.tick(); }
    @Override public boolean shouldPause() { return false; }

    /** Старт анимации закрытия — вызывается LinkGuiController.beginClose() и по ESC. */
    public void beginClose() {
        if (close) return;
        close = true;
        closeFadeAnimation.snapOpen();
        closeFadeAnimation.update(false);
        activeSlider = null; activeSliderId = null; ctxModule = null; clearBind();
        activeColorSetting = null; cpDrag = 0; cpSyncedFor = null;
        // Ввод окон настроек сбрасываем, но окна НЕ закрываем — сохраняются к следующему открытию GUI.
        LinkGuiSettings.resetAllInput();
        searchBar.reset();
    }

    @Override public void close() { beginClose(); }

    /** Сброс анимации открытия — вызывается LinkGuiController.open() перед setScreen. */
    public void resetOpenAnimation() {
        close = false;
        openAnimation.setDuration(OPEN_ANIMATION_DURATION);
        openAnimation.snapTo(true);
        closeFadeAnimation.setDuration(CLOSE_FADE_DURATION);
        closeFadeAnimation.snapOpen();
        gridSeeded = false; shownLastFrame.clear(); gridPos.clear(); gridExit.clear(); // свежее открытие — без «попа»
    }

    @Override public void removed() {
        player.shutdown();
        syncClosedState();
        super.removed();
    }

    public boolean isClosing() { return close; }

    private void finishClosing() {
        if (!close) return;
        close = false;
        closeFadeAnimation.snapOpen();
        resetOpenAnimation();
        syncClosedState();
        if (mc != null && mc.currentScreen == this) super.close();
    }

    private void syncClosedState() {
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui != null) ui.syncClosedState();
        LinkGuiController.getInstance().markClosed();
    }

    private float getUiFade() {
        if (close) return closeFadeAnimation.getValue();
        return Math.max(MIN_VISIBLE_FADE, openAnimation.getValue());
    }

    // ── публичное API для внешних систем (UiKeyHandler / ClickGuiWalk / LinkGuiWorldClose) ──
    public LinkGuiContext getGuiContext() { return guiContext; }
    public boolean isBindingUiKey() { return bindKind != BindKind.NONE; }
    public boolean isSearchFocused() { return searchBar.isFocused(); }
    public void renderWorldCaptureCanvas(DrawContext context) { }

    private static boolean ctrlDown() {
        var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        return net.minecraft.client.util.InputUtil.isKeyPressed(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                || net.minecraft.client.util.InputUtil.isKeyPressed(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
