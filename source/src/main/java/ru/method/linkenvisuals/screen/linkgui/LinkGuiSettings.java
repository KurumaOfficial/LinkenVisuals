package ru.method.linkenvisuals.screen.linkgui;

import ru.method.linkenvisuals.modules.api.Module;

import java.util.ArrayList;
import java.util.List;

public final class LinkGuiSettings {
    private static final float EDGE_PAD = 10f;
    private static final float GAP = 8f;
    private static final List<LinkGuiSettingsWindow> WINDOWS = new ArrayList<>();

    // Занятые открытыми панелями области (профиль/тема) — {x,y,w,h}, null если панель закрыта.
    private static float[] profileRect;
    private static float[] themeRect;

    // Видимая по вертикали область канваса (зависит от масштаба интерфейса): меньше масштаб — больше места.
    private static float visibleTopY = 0f;
    private static float visibleBottomY = LinkGuiLayout.CANVAS_HEIGHT;
    // Видимая по горизонтали область канваса (зависит от ширины окна и масштаба) — чтобы окна не уходили за края.
    private static float visibleLeftX = 0f;
    private static float visibleRightX = LinkGuiLayout.CANVAS_WIDTH;

    private LinkGuiSettings() {}

    public static List<LinkGuiSettingsWindow> getWindows() {
        return WINDOWS;
    }

    public static void clear() {
        for (LinkGuiSettingsWindow window : WINDOWS) {
            window.resetInputState();
        }
        WINDOWS.clear();
    }

    public static void resetAllInput() {
        for (LinkGuiSettingsWindow window : WINDOWS) {
            window.resetInputState();
        }
    }

    public static void setPanels(float[] profile, float[] theme) {
        profileRect = profile;
        themeRect = theme;
    }

    public static void setVisibleBounds(float topY, float bottomY) {
        visibleTopY = topY;
        visibleBottomY = bottomY;
    }

    public static void setVisibleBoundsX(float leftX, float rightX) {
        visibleLeftX = leftX;
        visibleRightX = rightX;
    }

    // Сколько пикселей окна минимум остаётся в видимой области (чтобы окно не улетело совсем и его
    // всегда можно было схватить за край). НЕ требуем, чтобы окно влезало целиком — иначе высокие окна
    // прибивались к краю и вообще не двигались.
    private static final float KEEP_VISIBLE = 48f;

    /** Зажать X: окно можно двигать свободно, но хотя бы KEEP_VISIBLE px остаётся на экране. */
    public static float clampX(float x, float w) {
        float min = visibleLeftX - (w - KEEP_VISIBLE);
        float max = visibleRightX - KEEP_VISIBLE;
        if (max < min) return Math.min(visibleLeftX + EDGE_PAD, max);
        return Math.max(min, Math.min(x, max));
    }

    /** Зажать Y: верх (шапку) не прячем выше видимой области, снизу — оставляем видимой шапку. */
    public static float clampY(float y, float h) {
        float min = visibleTopY + EDGE_PAD;
        float max = visibleBottomY - KEEP_VISIBLE;
        if (max < min) max = min;
        return Math.max(min, Math.min(y, max));
    }

    /**
     * Максимальная высота КОНТЕНТА окна, при которой оно (шапка + контент) целиком влезает в видимую
     * область по высоте. Если настроек больше — окно каппится этим значением, а лишнее уходит в скролл
     * (иначе высокие окна, напр. с эффектом «Сферы», вылезали за экран и низ было не пролистать).
     */
    public static float maxContentHeight(float header) {
        return Math.max(24f, (visibleBottomY - visibleTopY) - header - EDGE_PAD * 2f);
    }

    /** Available content height for a window that starts at {@code y} — keeps it above the screen bottom. */
    public static float maxContentHeightAt(float y, float header) {
        return Math.max(24f, visibleBottomY - y - header - EDGE_PAD * 2f);
    }

    /** Профиль/тема при открытии закрывают (с анимацией) самое старое окно настроек — «заменяют» его. */
    public static void closeOldest() {
        for (LinkGuiSettingsWindow w : WINDOWS) {
            if (!w.isClosing()) {
                w.requestClose();
                return;
            }
        }
    }

    /**
     * Переупаковывает и, если какие-то окна не влезают на экран (например, открыли панель/уменьшили масштаб),
     * закрывает ПОСЛЕДНИЕ открытые из них, пока ничего не уходит за экран. Вызывается каждый кадр.
     */
    public static void trimToFit() {
        // Ранний выход: без открытых окон настроек relayout (аллокации списков + сортировка)
        // гонялся каждый кадр впустую.
        if (WINDOWS.isEmpty()) return;
        int guard = 0;
        while (guard++ < 8) {
            relayout();
            // Закрываем САМОЕ СТАРОЕ выехавшее окно (наименьший openOrder) — новые остаются.
            LinkGuiSettingsWindow offscreen = null;
            for (LinkGuiSettingsWindow w : WINDOWS) {
                if (w.isClosing() || w.isUserMoved()) continue;
                if (w.getTargetY() + w.getCurrentHeight() > bottomY() + 60f
                        && (offscreen == null || w.getOpenOrder() < offscreen.getOpenOrder())) {
                    offscreen = w;
                }
            }
            if (offscreen == null) break;
            offscreen.requestClose();
        }
    }

    private static float topY() {
        // Дизайн-пространство редизайна 1920×1080 (было по старому 1280×720 → окно уезжало в угол).
        return Math.max(visibleTopY + EDGE_PAD, 130f);
    }

    private static float bottomY() {
        return visibleBottomY - EDGE_PAD;
    }

    private static float[] columns() {
        float width = LinkGuiSettingsWindow.WINDOW_WIDTH;
        // По центру дизайна (960): колонка 0 — по центру, колонка 1 — правее (для 2-го окна).
        return new float[]{
                960f - width / 2f,
                960f + width / 2f + 16f
        };
    }

    public static void open(Module module, float mouseX, float mouseY) {
        for (LinkGuiSettingsWindow existing : WINDOWS) {
            if (existing.getModule() == module && !existing.isClosing()) {
                return;
            }
        }
        LinkGuiSettingsWindow created = new LinkGuiSettingsWindow(module, columns()[0], topY());
        WINDOWS.add(created);
        relayout();

        // Новое окно стоит первым в порядке (сверху). Если на экране не хватает места — закрываем
        // САМЫЕ СТАРЫЕ окна (наименьший openOrder), пока новое и остальные не влезут. Так новое всегда
        // открывается, заменяя самое старое.
        int guard = 0;
        while (guard++ < 16 && !allFitOnScreen()) {
            LinkGuiSettingsWindow oldest = null;
            for (LinkGuiSettingsWindow w : WINDOWS) {
                if (w == created || w.isClosing() || w.isUserMoved()) continue;
                if (oldest == null || w.getOpenOrder() < oldest.getOpenOrder()) oldest = w;
            }
            if (oldest == null) break;
            oldest.requestClose();
            relayout();
        }
        created.snapPosition();
    }

    /** Все ли (не закрывающиеся) окна помещаются на экране по вертикали. */
    private static boolean allFitOnScreen() {
        for (LinkGuiSettingsWindow w : WINDOWS) {
            if (w.isClosing() || w.isUserMoved()) continue;
            if (w.getTargetY() + w.getCurrentHeight() > bottomY()) return false;
        }
        return true;
    }

    /**
     * Глобальная упаковка окон: каждое окно занимает первую свободную «дырку» в колонке сверху вниз,
     * обходя открытые панели (профиль/тема). Колонка 0 (слева) заполняется первой — поэтому окна встают
     * НАД профилем; когда место кончается, идут ниже него и затем в правую колонку. Ничего не уходит за экран.
     * Закрывающиеся окна исключаются — при закрытии окна остальные плавно поднимаются.
     */
    public static void relayout() {
        float[] columnX = columns();
        float width = LinkGuiSettingsWindow.WINDOW_WIDTH;

        // Занятые интервалы по колонкам — изначально только панели, попадающие в эту колонку по X.
        @SuppressWarnings("unchecked")
        List<float[]>[] occupied = new List[columnX.length];
        for (int c = 0; c < columnX.length; c++) {
            occupied[c] = new ArrayList<>();
            // Окна настроек обходят и профиль (слева), и тему (справа) — не налезают на открытые панели.
            for (float[] r : new float[][]{profileRect, themeRect}) {
                if (r != null && columnX[c] < r[0] + r[2] && columnX[c] + width > r[0]) {
                    occupied[c].add(new float[]{r[1], r[1] + r[3]});
                }
            }
        }

        // Перетащенные вручную окна — тоже «занятые» области: когда тащишь окно на другое,
        // авто-окна обходят его и уезжают на свободное место (это поведение вернули по просьбе).
        for (LinkGuiSettingsWindow w : WINDOWS) {
            if (w.isClosing() || !w.isUserMoved()) continue;
            for (int c = 0; c < columnX.length; c++) {
                if (columnX[c] < w.getX() + width && columnX[c] + width > w.getX()) {
                    occupied[c].add(new float[]{w.getY(), w.getY() + w.getCurrentHeight()});
                }
            }
        }

        // Раскладываем по СТАБИЛЬНОМУ порядку открытия (новые — выше): список WINDOWS переупорядочивается
        // при клике (z-order) и прыгал бы; openOrder стабилен. Новое окно всегда получает верхний слот,
        // лишние старые уходят вниз и закрываются trimToFit — поэтому новая настройка всегда открывается.
        List<LinkGuiSettingsWindow> ordered = new ArrayList<>(WINDOWS);
        ordered.sort((a, b) -> Long.compare(b.getOpenOrder(), a.getOpenOrder()));
        for (LinkGuiSettingsWindow w : ordered) {
            if (w.isClosing() || w.isUserMoved()) continue;
            float h = w.getCurrentHeight();
            int placedCol = -1;
            float placedY = topY();
            int bestCol = 0;
            float bestY = Float.MAX_VALUE;
            // Колонки в порядке предпочтения: сначала текущая колонка окна (чтобы оно не прыгало влево↔вправо
            // при изменении высоты контента), затем остальные.
            for (int c : columnOrder(w.getSlot(), columnX.length)) {
                float y = findSlot(occupied[c], h);
                if (y + h <= bottomY()) {
                    placedCol = c;
                    placedY = y;
                    occupied[c].add(new float[]{y, y + h});
                    break;
                }
                if (y < bestY) {
                    bestY = y;
                    bestCol = c;
                }
            }
            if (placedCol < 0) {
                // Не влезает целиком — ставим ПОД панелями/окнами с наименьшим переполнением (не внахлёст на профиль/тему).
                placedCol = bestCol;
                placedY = bestY;
                occupied[bestCol].add(new float[]{bestY, bestY + h});
            }
            w.setSlot(placedCol);
            w.setTarget(columnX[placedCol], placedY);
        }
    }

    /** Порядок перебора колонок: предпочитаемая первой, затем остальные слева направо. */
    private static int[] columnOrder(int preferred, int count) {
        int pref = Math.max(0, Math.min(count - 1, preferred));
        int[] order = new int[count];
        order[0] = pref;
        int idx = 1;
        for (int c = 0; c < count; c++) {
            if (c != pref) order[idx++] = c;
        }
        return order;
    }

    /** Первая свободная позиция сверху вниз для окна высотой h, не пересекающая занятые интервалы. */
    private static float findSlot(List<float[]> occupied, float h) {
        occupied.sort((a, b) -> Float.compare(a[0], b[0]));
        float y = topY();
        for (float[] iv : occupied) {
            if (y + h <= iv[0]) {
                return y;
            }
            if (iv[1] + GAP > y) {
                y = iv[1] + GAP;
            }
        }
        return y;
    }
}
