package ru.method.linkenvisuals.hud.impl;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.impl.render.NoRender;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScoreboardHud extends HudElement {
    private static final float PADDING = 7f;
    private static final float SIDE_BAR = 2.5f;
    private static final int LINE_HEIGHT = 11;
    private static final int SCORE_GAP = 10;
    private static final float MIN_WIDTH = 132f;
    private static final float WIDTH_SAFETY = 10f;

    // Цвет фона / прозрачность / радиус скорборда — ЕДИНЫЙ источник в модуле UI (settings.ui.scoreboard.*),
    // который правится в Link GUI. Здесь остаётся только «Акцент» (полоса слева + счёт) — его в UI нет.
    // Показ счёта настройки не имеет: бессмысленные «порядковые» числа скрываются автоматически
    // (см. collectScoreboard), настоящие значения показываются.
    private final ColorSetting accentSetting = new ColorSetting("Акцент", new Color(0, 0, 0, 0));

    public ScoreboardHud() {
        super("ScoreboardHud");
        getSettings().add(accentSetting);
    }

    // Доступ к настройкам для секции «Custom Scoreboard» в окне Themes (Link GUI): те же значения,
    // что и в панели Link GUI и в реальном рендере — один источник (модуль UI).
    public float getOpacityValue() {
        return UI.getScoreboardBackgroundColor().getAlpha();
    }

    public void setOpacityValue(float value) {
        UI.setScoreboardBackgroundAlpha(Math.round(Math.max(0f, Math.min(255f, value))));
    }

    public float getRadiusValue() {
        return UI.getScoreboardRadius();
    }

    public void setRadiusValue(float value) {
        UI.setScoreboardRadius(value);
    }

    /** Акцент: если в настройке выставлен непрозрачный цвет — берём его, иначе акцент темы. */
    private Color accent() {
        Color a = accentSetting.getValue();
        if (a != null && a.getAlpha() > 8) {
            return a;
        }
        return ColorUtils.getPrimaryColor();
    }

    @Override
    public void onRender2D(EventRender2D event) {
        if (fullNullCheck() || closed()) return;
        if (!UI.isCustomScoreboardEnabled()) return;
        if (shouldHideScoreboard()) return;

        // Кэш скорборда: сервер обновляет его максимум раз в тик, а collectScoreboard (stream+sort+
        // decorateName на 15 строк) и промер ширины на каждый КАДР — чистая трата CPU. Пересобираем
        // раз в 50мс (клиентский тик) — визуально идентично.
        long nowMs = System.currentTimeMillis();
        if (cachedData == null || nowMs >= cacheExpiresAtMs) {
            // Cap ширины панели: не шире ~45% экрана. В базовых координатах (контент рисуется
            // в масштабе uiScale), обрезка строк попадает в кэшируемый снапшот.
            float maxBaseWidth = mc.getWindow().getScaledWidth() * 0.45f / getUiScale();
            cachedData = collectScoreboard(maxBaseWidth);
            cachedWidth = Math.min(measureWidth(cachedData, cachedData.showScores()), maxBaseWidth);
            cacheExpiresAtMs = nowMs + 50L;
        }
        ScoreboardData data = cachedData;
        if (data.entries().isEmpty()) return;
        boolean showScores = data.showScores();

        int x = Math.round(getX());
        int y = Math.round(getY());
        float uiScale = getUiScale();
        int baseWidth = Math.round(Math.max(MIN_WIDTH, cachedWidth));
        int baseHeight = Math.round(PADDING * 2f + LINE_HEIGHT * (data.entries().size() + 1) + 5f);
        int scaledWidth = Math.round(baseWidth * uiScale);
        int scaledHeight = Math.round(baseHeight * uiScale);

        event.getContext().getMatrices().pushMatrix();
        float[] dragOffset = getDragOffset();
        int renderX = Math.round(x + dragOffset[0]);
        // Clamp по правому краю: панель не должна уезжать за экран.
        renderX = Math.min(renderX, Math.max(0, mc.getWindow().getScaledWidth() - scaledWidth));
        int renderY = Math.round(y + dragOffset[1]);

        Color accent = accent();

        // --- Подложка (нескалированная матрица, масштабированные размеры) ---
        // Цвет/прозрачность/радиус фона задаются в Link GUI (settings.ui.scoreboard.*) и работают
        // одинаково и в игре, и при открытом GUI — это «Цвет фона скорборда», который просил юзер.
        // Лёгкий блюр под панелью — для глубины (как у кастом-таба), сверху — сплошной цвет из настройки.
        Color sbBg = UI.getScoreboardBackgroundColor();
        float radius = Math.min(UI.getScoreboardRadius() * uiScale,
                Math.max(0f, Math.min(scaledWidth, scaledHeight)) * 0.5f);
        // Блюр виден только сквозь полупрозрачный фон — при почти непрозрачном (>=240) он полностью
        // перекрыт заливкой, пасс пропускаем.
        if (sbBg.getAlpha() < 240) {
            Render2D.drawBlurredRect(event.getContext().getMatrices(), renderX, renderY, scaledWidth, scaledHeight,
                    radius, 10f, ColorUtils.alpha(sbBg, Math.round(sbBg.getAlpha() * 0.55f)));
        }
        Render2D.drawRoundedRect(event.getContext().getMatrices(), renderX, renderY, scaledWidth, scaledHeight, radius, sbBg);

        // --- Контент (масштаб uiScale вокруг renderX/renderY, рисуем в базовых координатах) ---
        event.getContext().getMatrices().pushMatrix();
        event.getContext().getMatrices().translate(renderX, renderY);
        event.getContext().getMatrices().scale(uiScale, uiScale);
        event.getContext().getMatrices().translate(-renderX, -renderY);

        // Акцентная полоса слева. Вертикальный отступ зависит от радиуса фона: у скруглённого угла
        // левая кромка панели уходит правее x=3 на высоте y < r - sqrt(6r - 9) (пересечение дуги
        // с вертикалью x=3), и с фиксированным отступом 4px полоса торчала за угол. Радиус фона
        // задан в экранных px, полоса рисуется в базовых — переводим через uiScale.
        float radiusBase = uiScale > 0f ? radius / uiScale : radius;
        float arcClear = radiusBase - (float) Math.sqrt(Math.max(0f, 6f * radiusBase - 9f));
        float stripInset = Math.max(4f, arcClear + 1.5f);
        Render2D.drawRoundedRect(event.getContext().getMatrices(), renderX + 3f, renderY + stripInset,
                SIDE_BAR, baseHeight - stripInset * 2f, SIDE_BAR / 2f, ColorUtils.alpha(accent, 235));

        float contentLeft = renderX + SIDE_BAR + PADDING;
        float contentRight = renderX + baseWidth - PADDING;

        // Заголовок по центру контентной области + лёгкая подсветка акцентом.
        int titleW = mc.textRenderer.getWidth(data.title());
        int titleX = Math.round(contentLeft + ((contentRight - contentLeft) - titleW) / 2f);
        int titleY = renderY + Math.round(PADDING);
        event.getContext().drawText(mc.textRenderer, data.title(), titleX, titleY, 0xFFFFFFFF, false);

        // Разделитель под заголовком (акцент, мягко гаснущий к краям имитируем равномерным тонким).
        int dividerY = titleY + LINE_HEIGHT;
        Render2D.drawRoundedRect(event.getContext().getMatrices(), contentLeft, dividerY, contentRight - contentLeft, 0.8f,
                0.4f, ColorUtils.alpha(accent, 130));

        int accentRgb = 0xFF000000 | (accent.getRGB() & 0x00FFFFFF);
        int currentY = dividerY + 4;
        for (Entry entry : data.entries()) {
            event.getContext().drawText(mc.textRenderer, entry.name(), Math.round(contentLeft), currentY, 0xFFE8E8E8, false);
            if (showScores && entry.score() != null) {
                int sw = mc.textRenderer.getWidth(entry.score());
                event.getContext().drawText(mc.textRenderer, entry.score(),
                        Math.round(contentRight - sw), currentY, accentRgb, false);
            }
            currentY += LINE_HEIGHT;
        }
        event.getContext().getMatrices().popMatrix();

        event.getContext().getMatrices().popMatrix();
        setBounds(x, y, scaledWidth, scaledHeight);
        super.onRender2D(event);
    }

    // Кэш собранного скорборда (пересбор раз в ~тик, см. onRender2D).
    private ScoreboardData cachedData;
    private float cachedWidth;
    private long cacheExpiresAtMs;

    private ScoreboardData collectScoreboard(float maxBaseWidth) {
        if (mc.world == null) return new ScoreboardData(OrderedText.EMPTY, List.of(), false);

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = getSidebarObjective(scoreboard);
        if (objective == null) return new ScoreboardData(OrderedText.EMPTY, List.of(), false);

        List<ScoreboardEntry> sorted = scoreboard.getScoreboardEntries(objective).stream()
                .filter(entry -> !entry.hidden())
                .sorted(Comparator.comparingInt(ScoreboardEntry::value).reversed()
                        .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15)
                .toList();

        boolean showScores = shouldShowScores(sorted);
        // Максимум под текст внутри панели (зеркально measureWidth: полоса + отступы + запас).
        float maxText = Math.max(40f, maxBaseWidth - (SIDE_BAR + PADDING * 2f + WIDTH_SAFETY));

        List<Entry> entries = new ArrayList<>(sorted.size());
        for (ScoreboardEntry entry : sorted) {
            Text score = Text.literal(String.valueOf(entry.value()));
            // Имени остаётся место за вычетом счёта справа.
            float nameMax = maxText - (showScores ? SCORE_GAP + mc.textRenderer.getWidth(score) : 0f);
            // Прибавка «наторгованного» модулем SecretDupe к строке монет (локально, поверх серверного значения).
            Text name = ru.method.linkenvisuals.modules.impl.misc.FakeCosmetic.boostCoins(
                    Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name()));
            entries.add(new Entry(
                    trimWithEllipsis(name, Math.round(nameMax)),
                    score
            ));
        }

        return new ScoreboardData(trimWithEllipsis(objective.getDisplayName(), Math.round(maxText)), entries, showScores);
    }

    /** Обрезка с многоточием, если текст шире maxWidth; Text-форматирование сохраняется. */
    private OrderedText trimWithEllipsis(Text text, int maxWidth) {
        if (mc.textRenderer.getWidth(text) <= maxWidth) return text.asOrderedText();
        String suffix = "...";
        StringVisitable trimmed = mc.textRenderer.trimToWidth(text,
                Math.max(0, maxWidth - mc.textRenderer.getWidth(suffix)));
        return Language.getInstance().reorder(StringVisitable.concat(trimmed, StringVisitable.plain(suffix)));
    }

    /**
     * Автоопределение осмысленности счёта: серверы часто используют значения лишь для сортировки строк
     * (последовательные 15,14,13… или одинаковые числа) — такие «красные цифры» скрываем без настройки.
     */
    private static boolean shouldShowScores(List<ScoreboardEntry> sorted) {
        if (sorted.isEmpty()) return false;
        if (sorted.size() == 1) return sorted.get(0).value() != 0;
        boolean sequential = true;
        boolean allEqual = true;
        for (int i = 1; i < sorted.size(); i++) {
            int prev = sorted.get(i - 1).value();
            int cur = sorted.get(i).value();
            if (cur != prev - 1) sequential = false;
            if (cur != prev) allEqual = false;
        }
        return !(sequential || allEqual);
    }

    private ScoreboardObjective getSidebarObjective(Scoreboard scoreboard) {
        if (mc.player != null) {
            Team team = scoreboard.getScoreHolderTeam(mc.player.getNameForScoreboard());
            if (team != null) {
                ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
                if (slot != null) {
                    ScoreboardObjective teamObjective = scoreboard.getObjectiveForSlot(slot);
                    if (teamObjective != null) {
                        return teamObjective;
                    }
                }
            }
        }
        return scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
    }

    private float measureWidth(ScoreboardData data, boolean showScores) {
        int max = mc.textRenderer.getWidth(data.title());
        for (Entry entry : data.entries()) {
            int line = mc.textRenderer.getWidth(entry.name());
            if (showScores && entry.score() != null) {
                line += SCORE_GAP + mc.textRenderer.getWidth(entry.score());
            }
            max = Math.max(max, line);
        }
        return SIDE_BAR + PADDING + max + PADDING + WIDTH_SAFETY;
    }

    private boolean shouldHideScoreboard() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return false;
        }
        NoRender noRender = instance.getModuleManager().getModule(NoRender.class);
        return noRender != null && noRender.isToggled() && noRender.scoreboard.getValue();
    }

    // name/title — уже обрезанные OrderedText (см. trimWithEllipsis), score — короткое число.
    private record ScoreboardData(OrderedText title, List<Entry> entries, boolean showScores) {}
    private record Entry(OrderedText name, Text score) {}
}
