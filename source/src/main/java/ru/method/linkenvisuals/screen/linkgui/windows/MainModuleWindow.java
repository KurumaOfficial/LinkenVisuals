package ru.method.linkenvisuals.screen.linkgui.windows;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.impl.client.UI;
import net.minecraft.client.resource.language.I18n;
import ru.method.linkenvisuals.screen.clickgui.components.SearchBar;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiContext;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiLayout;
import ru.method.linkenvisuals.screen.linkgui.components.ModuleCard;
import ru.method.linkenvisuals.screen.linkgui.components.MusicPlayerStrip;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.AnimatedTextHelper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.GuiIcons;
import ru.method.linkenvisuals.utils.render.GifCache;
import ru.method.linkenvisuals.utils.render.GifRenderer;
import ru.method.linkenvisuals.utils.render.GifTexture;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.util.math.MathHelper;

public final class MainModuleWindow implements Wrapper {
    private static final float PAD = 16f;
    private static final float CARD_GAP = 10f;
    // Доп. горизонтальный отступ сетки — карточки уже по ширине.
    private static final float GRID_H_PAD = 26f;
    private static final long SCROLL_ANIMATION_MS = 340L;
    private static final Identifier CONFUSED_GIF = LinkenVisuals.id("textures/gui/confused_spoty.gif");

    // Зона поиска: слева кнопка «All», правее — поле поиска.
    private static final float SEARCH_LEFT = 88f;
    private static final float SEARCH_RIGHT_MARGIN = 68f;
    private static final float ALL_BTN_W = 34f;
    private static final float ALL_GAP = 6f;

    private final LinkGuiContext context;
    private final SearchBar searchBar;
    private final MusicPlayerStrip music = new MusicPlayerStrip();
    private final List<ModuleCard> cards = new ArrayList<>();
    private final InfinityAnimation scrollAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final Animation searchFocusAnim = new Animation(320, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation searchActiveAnim = new Animation(360, 1f, false, Easing.BOTH_SINE);
    private final AnimatedString searchTextAnim = new AnimatedString(170, Easing.EASE_OUT_CUBIC, "");
    private final Animation emptyStateAnim = new Animation(220, 1f, false, Easing.EASE_OUT_CUBIC);
    // Кнопка «All»: поиск по всем категориям. Анимация плавной заливки цветом темы.
    private final Animation allFillAnim = new Animation(300, 1f, false, Easing.EASE_OUT_CUBIC);
    // Hover кнопок шапки анимирован, как у карточек/дока/плеера — бинарный скачок выбивался из общей плавности.
    private final Animation headHoverAnim = new Animation(200, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation closeHoverAnim = new Animation(200, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation themeHoverAnim = new Animation(200, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation allHoverAnim = new Animation(200, 1f, false, Easing.EASE_OUT_CUBIC);
    private boolean searchAllCategories;

    private float x, y, width, height;
    private float scrollTarget;
    private Category lastCategory = Category.Combat;
    private String lastQuery = "";
    private long searchCursorStart;
    private boolean wasSearchFocused;
    private Runnable onToggleProfile = () -> {};
    private Runnable onToggleTheme = () -> {};

    public void setHeaderActions(Runnable onToggleProfile, Runnable onToggleTheme) {
        this.onToggleProfile = onToggleProfile;
        this.onToggleTheme = onToggleTheme;
    }

    public MainModuleWindow(LinkGuiContext context) {
        this.context = context;
        this.searchBar = new SearchBar(0, 0, 280, 28);
        rebuildCards();
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        searchBar.setX(searchFieldX());
        searchBar.setY(y + 12f);
        searchBar.setWidth(searchFieldW());
        searchBar.setHeight(28f);
        music.setBounds(x + PAD, y + height - LinkGuiLayout.MUSIC + 2f, width - PAD * 2f, LinkGuiLayout.MUSIC - 4f);
    }

    private float allBtnX() { return x + SEARCH_LEFT; }
    private float allBtnY() { return y + 12f; }
    private float searchFieldX() { return x + SEARCH_LEFT + ALL_BTN_W + ALL_GAP; }
    private float searchFieldW() { return width - SEARCH_RIGHT_MARGIN - SEARCH_LEFT - ALL_BTN_W - ALL_GAP; }

    // Сохранённый между закрытиями GUI текст поиска (статика — переживает пересоздание экрана/окна).
    private static String persistedSearch = "";

    public void init() {
        music.init();
        // Восстанавливаем текст поиска, введённый до прошлого закрытия GUI.
        searchBar.setSearchText(persistedSearch);
        syncVisibilityInstant();
        replayCardsEnter(searchBar.getSearchText().toLowerCase(Locale.ROOT));
    }

    /** Мгновенно выставляет видимость карточек под текущую категорию/поиск — без мелькания при открытии. */
    private void syncVisibilityInstant() {
        String query = searchBar.getSearchText().toLowerCase(Locale.ROOT);
        for (ModuleCard card : cards) {
            card.snapVisible(matches(card, query));
        }
    }

    public void shutdown() {
        // Запоминаем текущий текст поиска, чтобы восстановить его при следующем открытии GUI.
        persistedSearch = searchBar.getSearchText();
        music.shutdown();
    }

    public void tick() {
        music.tick();
        searchFocusAnim.update(searchBar.isFocused());
        searchActiveAnim.update(searchBar.isFocused() || !searchBar.getSearchText().isEmpty());

        if (searchBar.isFocused() && !wasSearchFocused) {
            searchCursorStart = System.currentTimeMillis();
        }
        wasSearchFocused = searchBar.isFocused();
        searchTextAnim.update(searchBar.getSearchText());

        updateCardSearchVisibility(searchBar.getSearchText().toLowerCase(Locale.ROOT));
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta, Runnable onClose) {
        LinkGuiChrome.drawGlassPanel(context.getMatrices(), x, y, width, height, LinkGuiLayout.RADIUS);
        drawHeader(context.getMatrices(), context, mouseX, mouseY);

        float gridTop = y + LinkGuiLayout.HEADER + 2f;
        float gridBottom = y + height - LinkGuiLayout.MUSIC - 2f;
        float gridHeight = gridBottom - gridTop;
        String query = searchBar.getSearchText().toLowerCase(Locale.ROOT);

        syncScrollFilters(query);
        float maxScroll = Math.max(0f, computeContentHeight(query) - gridHeight);
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        float animatedScroll = MathHelper.clamp(scrollAnimation.animate(scrollTarget, SCROLL_ANIMATION_MS), 0f, maxScroll);

        if (context != null) {
            Render2D.startScissor(context, x + PAD, gridTop - 4f, width - PAD * 2f, gridHeight + 6f);
        }

        float cardW = (width - PAD * 2f - GRID_H_PAD * 2f - CARD_GAP) / 2f;
        unkw.visualtest.client.hud.RoundFontRenderer.beginFrame();
        layoutVisibleCards(query, animatedScroll, cardW, gridTop, (card, cardX, cardY) -> {
            card.setX(cardX);
            card.setY(cardY);
            card.setWidth(cardW);
            card.setContentAlpha(1f);
            // Отсечение по видимой области сетки: scissor и так обрезает карточки за её границами,
            // поэтому off-screen карточки (особенно в режиме «All») не рендерим — позиции им всё равно
            // выставлены выше, так что клики работают, а картинка идентична. Это убирает сотни draw-call'ов.
            if (cardY + card.getHeight() >= gridTop - 8f && cardY <= gridBottom + 8f) {
                card.render(context.getMatrices(), mouseX, mouseY, delta);
            }
        });

        emptyStateAnim.update(isSearchEmptyState(query));
        float emptyAlpha = emptyStateAnim.getValue() * LinkGuiRenderContext.getFadeMultiplier();
        if (emptyAlpha > 0.01f) {
            drawSearchEmptyState(context.getMatrices(), gridTop, gridHeight, emptyAlpha);
        }

        if (context != null) {
            unkw.visualtest.client.hud.RoundFontRenderer.flush(context);
            Render2D.stopScissor(context);
        }

        // Полосу-затухание у краёв сетки убрали — выглядела как чужеродная строка. Карточки просто
        // обрезаются по границе сетки; сверху шапка, снизу плеер — пустых полос нет.

        float searchFocus = Math.max(searchFocusAnim.getValue(), searchActiveAnim.getValue() * 0.55f);
        LinkGuiChrome.drawSearchField(context.getMatrices(), searchFieldX(), y + 12f, searchFieldW(), 28f, searchFocus);
        drawSearchText(context.getMatrices());
        drawAllButton(context.getMatrices(), mouseX, mouseY);

        music.render(context, mouseX, mouseY);
    }

    /** Кнопка «All» слева от поиска: вкл — плавная заливка цветом темы, выкл — более тёмный цвет темы. */
    private void drawAllButton(Matrix3x2fStack stack, int mouseX, int mouseY) {
        allFillAnim.update(searchAllCategories);
        float t = allFillAnim.getValue();

        float bx = allBtnX();
        float by = allBtnY();
        float bw = ALL_BTN_W;
        float bh = 28f;
        boolean hover = MathUtils.isHovered(bx, by, bw, bh, mouseX, mouseY);
        allHoverAnim.update(hover);
        float hoverT = allHoverAnim.getValue();

        Color accent = LinkGuiThemeScope.accent();
        // «Сквиркл» (скруглённый квадрат) с акцентной обводкой-рамкой — вместо обычной пилюли.
        float rad = 9f;
        LinkGuiChrome.drawPillButton(stack, bx, by, bw, bh, rad, 0.5f + 0.35f * hoverT);
        if (t > 0.01f) {
            Color on = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(235 * t));
            WindowGuiRender.drawRoundedRect(stack, bx, by, bw, bh, rad, on);
        }
        // Акцентная рамка (ярче при наведении/включении) — для «прикольного» вида.
        int ringA = Math.round((90 + 120 * Math.max(t, hoverT)));
        WindowGuiRender.drawRoundedOutlineRect(stack, bx, by, bw, bh, rad, 1.2f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.min(255, ringA)));

        // Текст «All»: на выкл — светлый, на вкл — контрастный (тёмный поверх яркой заливки).
        Color textColor = WindowGuiColors.lerp(ColorUtils.getTextColor(220), new Color(15, 15, 18, 255), t);
        float size = 7.6f;
        float tw = Fonts.ROUNDBOLD.getWidth("All", size);
        float th = Fonts.ROUNDBOLD.getHeight(size);
        Render2D.drawFont(stack, Fonts.ROUNDBOLD.getFont(size), "All",
                bx + (bw - tw) / 2f, by + (bh - th) / 2f, textColor);
    }

    private void drawSearchText(Matrix3x2fStack stack) {
        float sx = searchFieldX();
        float sy = y + 12f;
        float sw = searchFieldW();
        float sh = 28f;
        float size = 8.2f;
        float activeT = Math.max(searchFocusAnim.getValue(), searchActiveAnim.getValue());
        float textY = sy + (sh / 2f) - (Fonts.MEDIUM.getHeight(size) / 2f);
        float textX = sx + 14f;

        String text = searchBar.getSearchText();
        boolean empty = text.isEmpty();

        if (empty && !searchBar.isFocused() && !searchTextAnim.isAnimating()) {
            Color placeholder = ColorUtils.getTextColor((int) (120 + 35 * activeT));
            Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size),
                    I18n.translate("clickgui.search.placeholder"), textX, textY, placeholder);
        } else {
            Color textColor = ColorUtils.getTextColor((int) (235 + 20 * searchFocusAnim.getValue()));
            drawAnimatedSearchText(stack, textX, textY, size, textColor);
        }

        if (searchBar.isFocused()) {
            float cursorX = textX + (empty ? 0f : Fonts.MEDIUM.getWidth(text, size));
            long elapsed = System.currentTimeMillis() - searchCursorStart;
            double cycle = (elapsed % 1000L) / 1000.0;
            float cursorAlpha = (float) (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(cycle * Math.PI * 2)));
            Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), "|",
                    cursorX + 1f, textY, ColorUtils.getTextColor((int) (255 * cursorAlpha)));
        }

        Color iconColor = ColorUtils.getTextColor((int) (140 + 80 * activeT));
        WindowGuiRender.drawText(stack, Fonts.ICONSNEW, "F", sx + sw - 22f, sy + 9f, iconColor, 9f);
    }

    /** Набор/стирание текста поиска: изменённый сегмент проявляется/затухает со сдвигом. */
    private void drawAnimatedSearchText(Matrix3x2fStack stack, float x, float y, float size, Color color) {
        if (!searchTextAnim.isAnimating()) {
            Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), searchTextAnim.getCurrent(), x, y, color);
            return;
        }

        float progress = Math.max(0f, Math.min(1f, searchTextAnim.progress()));
        String previous = searchTextAnim.getPrevious();
        String current = searchTextAnim.getCurrent();
        int prefixLength = AnimatedTextHelper.getPrefixLength(previous, current);
        int suffixLength = AnimatedTextHelper.getSuffixLength(previous, current, prefixLength);

        String prefix = AnimatedTextHelper.substringSafe(current, 0, prefixLength);
        String prevMid = AnimatedTextHelper.substringSafe(previous, prefixLength, previous.length() - suffixLength);
        String currMid = AnimatedTextHelper.substringSafe(current, prefixLength, current.length() - suffixLength);
        String suffix = AnimatedTextHelper.substringSafe(current, current.length() - suffixLength, current.length());

        float prefixWidth = Fonts.MEDIUM.getWidth(prefix, size);
        float currMidWidth = Fonts.MEDIUM.getWidth(currMid, size);
        float shift = Fonts.MEDIUM.getHeight(size) * 0.6f;

        int baseAlpha = color.getAlpha();
        Color prevColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(baseAlpha * (1f - progress)));
        Color nextColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(baseAlpha * progress));

        Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), prefix, x, y, color);
        Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), suffix, x + prefixWidth + currMidWidth, y, color);
        Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), prevMid, x + prefixWidth, y - shift * progress, prevColor);
        Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), currMid, x + prefixWidth, y + shift * (1f - progress), nextColor);
    }

    private void drawHeader(Matrix3x2fStack stack, DrawContext context, int mouseX, int mouseY) {
        float chipX = x + PAD;
        float chipY = y + 6f;
        float chipSize = 40f;
        LinkGuiChrome.drawAccentChip(stack, chipX, chipY, chipSize, chipSize, 11f);
        // Логотип вместо «LV»: белая маска, крашенная цветом темы, с жирным glow-контуром.
        float logoCenterX = chipX + chipSize / 2f;
        float logoCenterY = chipY + chipSize / 2f;
        LinkGuiChrome.drawThemedLogo(stack, logoCenterX, logoCenterY, 37f);

        // Аватар (голова) — кнопка вызова профиля.
        float head = 28f;
        float headX = x + PAD + 42f;
        float headY = y + 12f;
        boolean headHover = MathUtils.isHovered(headX, headY, head, head, mouseX, mouseY);
        headHoverAnim.update(headHover);
        float headHoverT = headHoverAnim.getValue();
        LinkGuiChrome.drawInset(stack, headX, headY, head, head, 7f);
        if (context != null && mc.player instanceof AbstractClientPlayerEntity player) {
            // Голова гаснет вместе с GUI (раньше Color.WHITE игнорировал затухание и «выскакивала» без анимации).
            int headAlpha = Math.max(0, Math.min(255, Math.round(255f * LinkGuiRenderContext.getFadeMultiplier())));
            // Учитываем оверрайд AllSkins (иконка рисуется напрямую, мимо PlayerEntityRendererMixin).
            net.minecraft.util.Identifier headTex = ru.method.linkenvisuals.modules.impl.render.AllSkins.skinOverride();
            if (headTex == null) headTex = player.getSkin().body().texturePath();
            Render2D.drawTextureNearest(stack, headX + 1f, headY + 1f, head - 2f, head - 2f, 0f,
                    0.125f, 0.125f, 0.25f, 0.25f, headTex,
                    new Color(255, 255, 255, headAlpha));
        }
        // Hover-фидбек аватара — акцентная рамка ПОВЕРХ текстуры: подсветка под головой была
        // полностью накрыта непрозрачным скином и не читалась.
        if (headHoverT > 0.01f) {
            Color accent = LinkGuiThemeScope.accent();
            WindowGuiRender.drawRoundedOutlineRect(stack, headX, headY, head, head, 7f, 1.2f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(200 * headHoverT)));
        }

        float closeX = x + width - PAD - 24f;
        float closeY = y + 14f;
        boolean closeHover = MathUtils.isHovered(closeX, closeY, 24f, 24f, mouseX, mouseY);
        closeHoverAnim.update(closeHover);
        LinkGuiChrome.drawPillButton(stack, closeX, closeY, 24f, 24f, 6f, closeHoverAnim.getValue());
        WindowGuiRender.drawCenteredText(stack, Fonts.ICONSNEW, "L", closeX + 12f, closeY + 6f,
                ColorUtils.getTextColor(220), 9f);

        // Кнопка вызова окна «Тема» (рядом с крестиком) — иконка-палитра.
        float themeBtnX = closeX - 28f;
        boolean themeHover = MathUtils.isHovered(themeBtnX, closeY, 24f, 24f, mouseX, mouseY);
        themeHoverAnim.update(themeHover);
        LinkGuiChrome.drawPillButton(stack, themeBtnX, closeY, 24f, 24f, 6f, themeHoverAnim.getValue());
        GuiIcons.draw(stack, GuiIcons.PALETTE, themeBtnX + 12f, closeY + 12f, 16f, ColorUtils.getTextColor(235));

        // Акцентный разделитель под шапкой: тонкая линия, растворяющаяся к краям, — отделяет
        // шапку от сетки и добавляет структуре «дорогой» вид.
        float sepY = y + LinkGuiLayout.HEADER - 3f;
        java.awt.Color sep = LinkGuiThemeScope.accent();
        float sepHalf = (width - PAD * 2f) / 2f;
        WindowGuiRender.drawRoundedHorLinearGradientRect(stack, x + PAD, sepY, sepHalf, 1f, 0f,
                WindowGuiColors.withAlpha(sep, 0f), WindowGuiColors.withAlpha(sep, 0.45f));
        WindowGuiRender.drawRoundedHorLinearGradientRect(stack, x + PAD + sepHalf, sepY, sepHalf, 1f, 0f,
                WindowGuiColors.withAlpha(sep, 0.45f), WindowGuiColors.withAlpha(sep, 0f));
    }

    private void updateCardSearchVisibility(String query) {
        // searchVisibleAnim отвечает ТОЛЬКО за совпадение с поиском (плавный коллапс внутри категории).
        // Принадлежность к категории — отдельный жёсткий фильтр в раскладке, чтобы карточки разных
        // категорий не смешивались во время перехода.
        for (ModuleCard card : cards) {
            card.updateSearchVisible(matches(card, query));
        }
    }

    private boolean matches(Module module, String query) {
        if (query.isEmpty()) return true;
        return module.getName().toLowerCase(Locale.ROOT).contains(query)
                || module.getDisplayName().toLowerCase(Locale.ROOT).contains(query);
    }

    /** Поиск по кэшированным lowercase-именам карточки (без toLowerCase-аллокаций на кадр). */
    private boolean matches(ModuleCard card, String query) {
        if (query.isEmpty()) return true;
        return card.loweredName().contains(query) || card.loweredDisplayName().contains(query);
    }

    /** Поиск задан, но в текущем виде нет совпадений — показываем гифку «ничего не найдено». */
    private boolean isSearchEmptyState(String query) {
        if (query.isEmpty()) return false;
        for (ModuleCard card : cards) {
            if (inView(card) && matches(card, query)) return false;
        }
        return true;
    }

    private void drawSearchEmptyState(Matrix3x2fStack stack, float gridTop, float gridHeight, float alpha) {
        GifTexture gif = GifCache.get(CONFUSED_GIF);
        String message = I18n.translate("clickgui.search.empty");
        float size = 8.6f;
        float textW = Fonts.MEDIUM.getWidth(message, size);
        float textH = Fonts.MEDIUM.getHeight(size);
        float gifSize = 90f; // +40% от прежних 64px
        float gap = 14f;
        float blockW = gifSize + gap + textW;
        float blockX = x + PAD + (width - PAD * 2f - blockW) / 2f;
        float blockY = gridTop + gridHeight * 0.4f - gifSize / 2f;

        GifRenderer.drawGif(stack, gif, blockX, blockY, gifSize, gifSize, alpha);

        float textX = blockX + gifSize + gap;
        float textY = blockY + (gifSize - textH) / 2f;
        Render2D.drawFont(stack, Fonts.MEDIUM.getFont(size), message, textX, textY,
                ColorUtils.getTextColor((int) (210 * alpha)));
    }

    public void rebuildCards() {
        cards.clear();
        java.util.List<Module> mods = new java.util.ArrayList<>();
        for (Module module : LinkenVisuals.getInstance().getModuleManager().getModules()) {
            // Служебный модуль UI в списке не показываем — его настройки живут в окне «Тема».
            if (module instanceof UI) continue;
            mods.add(module);
        }
        // Порядок: сперва по категории (ordinal: Combat, Movement, Visuals, Player, Other, Hud),
        // внутри категории — по алфавиту имени. В режиме «All» это даёт сгруппированный по категориям
        // список (Combat-функции первыми, по алфавиту), а в обычном виде — категорию по алфавиту.
        mods.sort(java.util.Comparator
                .comparingInt((Module m) -> m.getCategory().ordinal())
                .thenComparing(Module::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        for (Module module : mods) {
            cards.add(new ModuleCard(module));
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, Runnable onClose) {
        float closeX = x + width - PAD - 24f;
        float closeY = y + 14f;
        if (MathUtils.isHovered(closeX, closeY, 24f, 24f, (float) mouseX, (float) mouseY) && button == 0) {
            onClose.run();
            return true;
        }
        if (button == 0) {
            float head = 28f;
            float headX = x + PAD + 42f;
            float headY = y + 12f;
            if (MathUtils.isHovered(headX, headY, head, head, (float) mouseX, (float) mouseY)) {
                onToggleProfile.run();
                return true;
            }
            float themeBtnX = closeX - 28f;
            if (MathUtils.isHovered(themeBtnX, closeY, 24f, 24f, (float) mouseX, (float) mouseY)) {
                onToggleTheme.run();
                return true;
            }
        }
        if (button == 0 && MathUtils.isHovered(allBtnX(), allBtnY(), ALL_BTN_W, 28f, (float) mouseX, (float) mouseY)) {
            searchAllCategories = !searchAllCategories;
            // Плавно «перезаезжают» карточки нового набора (по всем категориям / обратно к текущей).
            replayCardsEnter(searchBar.getSearchText().toLowerCase(Locale.ROOT));
            return true;
        }
        if (searchBar.mouseClicked(mouseX, mouseY, button)) return true;
        if (music.mouseClicked(mouseX, mouseY, button)) return true;

        String query = searchBar.getSearchText().toLowerCase(Locale.ROOT);
        float gridTop = y + LinkGuiLayout.HEADER + 2f;
        float gridBottom = y + height - LinkGuiLayout.MUSIC - 2f;
        float gridHeight = gridBottom - gridTop;
        if (!MathUtils.isHovered(x + PAD, gridTop, width - PAD * 2f, gridHeight, (float) mouseX, (float) mouseY)) {
            return false;
        }

        float maxScroll = Math.max(0f, computeContentHeight(query) - gridHeight);
        float animatedScroll = MathHelper.clamp(scrollAnimation.getValue(), 0f, maxScroll);
        float cardW = (width - PAD * 2f - GRID_H_PAD * 2f - CARD_GAP) / 2f;

        boolean[] handled = {false};
        layoutVisibleCards(query, animatedScroll, cardW, gridTop, (card, cardX, cardY) -> {
            if (handled[0]) return;
            float cardH = card.getHeight();
            if (cardY + cardH < gridTop || cardY > gridBottom) {
                return;
            }
            card.setX(cardX);
            card.setY(cardY);
            card.setWidth(cardW);
            if (card.mouseClicked(mouseX, mouseY, button)) {
                handled[0] = true;
            }
        });
        return handled[0];
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        music.mouseReleased(mouseX, mouseY, button);
    }

    public void mouseDragged(double mouseX, double mouseY) {
        music.mouseDragged(mouseX, mouseY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Назначение бинда функции на колесо имеет приоритет над прокруткой списка.
        if (ModuleCard.handleBindScroll(delta)) return true;
        float gridTop = y + LinkGuiLayout.HEADER + 2f;
        float gridBottom = y + height - LinkGuiLayout.MUSIC - 2f;
        float gridHeight = gridBottom - gridTop;
        if (MathUtils.isHovered(x + PAD, gridTop, width - PAD * 2f, gridHeight, (float) mouseX, (float) mouseY)) {
            scrollTarget -= (float) (delta * 36);
            String query = searchBar.getSearchText().toLowerCase(Locale.ROOT);
            float maxScroll = Math.max(0f, computeContentHeight(query) - gridHeight);
            scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
            return true;
        }
        return false;
    }

    private void syncScrollFilters(String query) {
        Category category = context.getSelectedCategory();
        boolean categoryChanged = category != lastCategory;
        if (categoryChanged || !query.equals(lastQuery)) {
            scrollTarget = 0f;
            scrollAnimation.animate(0f, 1L);
            if (categoryChanged) {
                replayCardsEnter(query);
            }
            lastCategory = category;
            lastQuery = query;
        }
    }

    /** Карточка относится к текущему виду: либо включён режим «All» (все категории), либо её категория выбрана. */
    private boolean inView(ModuleCard card) {
        return searchAllCategories || card.getModule().getCategory() == context.getSelectedCategory();
    }

    /** Перезапуск каскадного появления карточек текущего вида (как Prime). */
    private void replayCardsEnter(String query) {
        int index = 0;
        for (ModuleCard card : cards) {
            if (!inView(card)) continue;
            if (!matches(card, query)) continue;
            card.replayEnter(index++);
        }
    }

    private float computeContentHeight(String query) {
        // Высота раскладки по «сворачивающимся» слотам — карточки плавно убираются при поиске/смене категории.
        float[] colY = {0f, 0f};
        int col = 0;
        for (ModuleCard card : cards) {
            if (!inView(card)) continue;
            if (!card.isRenderable()) continue;
            colY[col] += (card.getHeight() + CARD_GAP) * card.layoutScale();
            col = 1 - col;
        }
        float total = Math.max(colY[0], colY[1]);
        return Math.max(0f, total - CARD_GAP);
    }

    private void layoutVisibleCards(String query, float scroll, float cardW, float gridTop, CardLayoutConsumer consumer) {
        // Раскладку определяет анимированная видимость карточки (searchVisibleAnim), а не жёсткий фильтр:
        // не подходящие под поиск/категорию карточки сворачиваются и затухают, соседи плавно подъезжают.
        float[] colY = {gridTop - scroll, gridTop - scroll};
        int col = 0;
        for (ModuleCard card : cards) {
            if (!inView(card)) continue;
            if (!card.isRenderable()) continue;

            float cardX = x + PAD + GRID_H_PAD + col * (cardW + CARD_GAP);
            float cardY = colY[col];
            consumer.accept(card, cardX, cardY);
            colY[col] += (card.getHeight() + CARD_GAP) * card.layoutScale();
            col = 1 - col;
        }
    }

    public boolean charTyped(char chr, int modifiers) {
        // Во время назначения бинда функции символ нажатой клавиши НЕ должен попадать в поиск
        // (keyPressed уже перехватывает клавишу через handleBindKey, но charTyped — отдельное событие).
        if (ModuleCard.isAnyBinding()) return false;
        return searchBar.charTyped(chr, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Назначение бинда функции нажатой клавишей имеет приоритет над вводом в поиск.
        if (ModuleCard.handleBindKey(keyCode)) return true;
        return searchBar.keyPressed(keyCode, scanCode, modifiers);
    }

    public SearchBar getSearchBar() {
        return searchBar;
    }

    @FunctionalInterface
    private interface CardLayoutConsumer {
        void accept(ModuleCard card, float x, float y);
    }
}
