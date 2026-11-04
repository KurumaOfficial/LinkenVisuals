package ru.method.linkenvisuals.screen.multiplayer;

import net.minecraft.client.input.CharInput;

import net.minecraft.client.input.KeyInput;

import net.minecraft.client.gui.Click;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.managers.misc.UserSyncManager;
import ru.method.linkenvisuals.screen.clickgui.redesign.ClickGuiIcons;
import ru.method.linkenvisuals.screen.mainmenu.CustomMainMenuScreen;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Instance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Экран «Сетевая игра» — редизайн «Linken Multiplayer 4b (Окно)». Заменяет ванильный MultiplayerScreen.
 * Реализован по дизайн-хендоффу пиксель-в-пиксель, масштаб от эталона 1920×1080.
 *
 * <p>Хром (фон, лого, часы, плеер, материал кнопок, палитра) — общий с мейнменю через {@link MenuChrome}.
 * Материал центрального окна — по спеке handoff'а (штриховка, глоу, уголки-скобки).</p>
 */
public class CustomMultiplayerScreen extends Screen implements Wrapper {

    // ---------- Геометрия окна (дизайн-единицы) ----------
    private static final float WIN_W = 1000f;
    private static final float WIN_TOP = 168f;
    private static final float WIN_R = 12f;
    private static final float PAD_X = 22f;
    private static final float TOPBAR_H = 68f;  // padding 16 + контент 36 + padding 16
    private static final float ROW_H = 64f;     // padding 13 + тайл 38 + padding 13
    private static final float FOOTER_H = 76f;  // padding 16 + кнопка 44 + padding 16

    // ---------- Палитра (из общих токенов) ----------
    private static final Color TEXT   = MenuChrome.TEXT;
    private static final Color TEXT2  = MenuChrome.TEXT2;
    private static final Color MUTED  = MenuChrome.MUTED;
    private static final Color MUTED2 = MenuChrome.MUTED2;
    private static final Color MUTED3 = MenuChrome.MUTED3;
    private static final Color MUTED4 = MenuChrome.MUTED4;
    private static final Color INK    = MenuChrome.INK;
    private static final Color DANGER = MenuChrome.DANGER;
    private static final Color DANGER_BD = new Color(224, 110, 110, 140); // rgba(224,110,110,0.55)

    /** Пул пингов: ванильный SERVER_PINGER_THREAD_POOL package-private, а pinger.add() блокирующий. */
    private static final ExecutorService PING_POOL = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("Linken Server Pinger #", 0).factory());

    private final Screen parent;
    private ServerList serverList;
    private static final class PingAttempt {
        final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
        final ServerInfo probe;
        PingAttempt(ServerInfo source) {
            probe = new ServerInfo(source.name, source.address, source.getServerType());
            probe.setFavicon(source.getFavicon());
        }
        volatile boolean cancelled;
        java.util.concurrent.Future<?> task;
        void close() {
            cancelled = true;
            if (task != null) task.cancel(true);
            pinger.cancel();
        }
    }
    private final Map<ServerInfo, PingAttempt> attempts = new java.util.IdentityHashMap<>();
    private boolean closed;
    private final Map<String, NativeImageBackedTexture> favicons = new HashMap<>();
    private final Set<String> faviconTried = new HashSet<>();
    // По ИДЕНТИЧНОСТИ объекта, а не по name|address: иначе два одинаковых сервера делят ключ и
    // второй дубликат никогда не пингуется (ключ уже занят первым).
    private final Set<ServerInfo> pinging = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    // Время старта пинга — для надёжного таймаута (колбэки пингера могут не сработать при reset/зависании).
    private final Map<ServerInfo, Long> pingStartMs = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Кэш последнего успешного статуса на СЕССИЮ игры (статик — переживает пересоздание экрана).
     * servers.dat не хранит MOTD/онлайн, а повторный пинг сразу после выхода с сервера часто
     * режется его защитой — без кэша после «зашёл-вышел» строки оставались без MOTD.
     */
    private record CachedStatus(Text label, Text playerCountLabel,
                                net.minecraft.server.ServerMetadata.Players players, long ping) {}
    private static final Map<String, CachedStatus> STATUS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Резолвнутые IP по ключу сервера (на сессию) — чтобы поиск находил и по цифровому адресу. */
    private static final Map<String, String> RESOLVED_IPS = new java.util.concurrent.ConcurrentHashMap<>();

    // --- Виртуальная система координат ---
    private float uiScale = 1f;
    private int vw = 1, vh = 1;

    // Закреплённые «от клиента» — {имя, адрес}. Добавляются под теми, что пришли с бэкенда, и выглядят
    // ровно так же (тот же список pinned: глоу, всегда сверху, без edit/delete).
    private static final String[][] BUILTIN_PINNED = {
    };

    // --- Состояние ---
    private final List<ServerInfo> visible = new ArrayList<>();
    // Закреплённые сервера (с бэкенда, общие для всех): всегда сверху, глоу, без edit/delete.
    private final List<ServerInfo> pinned = new ArrayList<>();
    // Полный порядок строк: закреплённые + пользовательские (без дублей закреплённых по адресу).
    private final List<ServerInfo> allRows = new ArrayList<>();
    private int selected = -1;
    private String query = "";
    private boolean searchFocused;
    private float scroll, scrollTarget;
    private long lastClickMs;
    private int lastClickIndex = -1;
    private ServerInfo pendingDelete;

    // --- Ховеры ---
    private final Animation hBack = MenuChrome.anim(), hChange = MenuChrome.anim(), hCover = MenuChrome.anim();
    private final Animation hSearch = MenuChrome.anim(), hRefresh = MenuChrome.anim();
    private final Animation hEdit = MenuChrome.anim(), hDelete = MenuChrome.anim();
    private final Animation hDirect = MenuChrome.anim(), hAdd = MenuChrome.anim(), hJoin = MenuChrome.anim();
    // Пустое состояние рисует свою «Добавить» — у неё отдельные ховер/хит, иначе футер их перетирает.
    private final Animation hAddEmpty = MenuChrome.anim();
    private final float[] rAddEmpty = new float[4];
    private final float[] rBack = new float[4], rChange = new float[4], rCover = new float[4];
    private final float[] rSearch = new float[4], rRefresh = new float[4];
    private final float[] rEdit = new float[4], rDelete = new float[4];
    private final float[] rDirect = new float[4], rAdd = new float[4], rJoin = new float[4];
    private final List<float[]> rRows = new ArrayList<>();
    // По идентичности объекта (см. pinging): одинаковые сервера — независимые строки со своей анимацией.
    private final Map<ServerInfo, RowAnim> rowAnims = new java.util.IdentityHashMap<>();
    private final Map<Integer, Animation> hRows = new HashMap<>();

    /**
     * Кэш производных строк строки списка: regex-цепочки cleanName/MOTD/versionChip не гоняем каждый
     * кадр. Источники сверяем по ссылке (пингер/markUnreachable/edit всегда присваивают новые объекты),
     * поэтому инвалидация не требует хуков в колбэках — всё живёт только на render-потоке.
     */
    private static final class RowText {
        String name; Text label; Text version;
        String clean, motd, chip;
    }
    private final Map<ServerInfo, RowText> rowTexts = new java.util.IdentityHashMap<>();
    // Индекс visible по идентичности — замена O(n²) visible.indexOf в цикле отрисовки.
    private final Map<ServerInfo, Integer> visibleIndex = new java.util.IdentityHashMap<>();
    // Отложенное сохранение servers.dat: колбэк каждого пинга лишь ставит флаг, пишем раз в ~2с.
    private boolean serversDirty;
    private long lastSaveMs;

    /** Пер-серверная анимация строки: гаснет/подъезжает при фильтрации поиском. */
    private static final class RowAnim {
        float alpha;   // 0..1 — совпадает ли с поиском
        float y;       // текущая Y относительно верха списка
        boolean placed;
    }

    private static Color fade(Color c, float k) {
        if (k >= 0.999f) return c;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.round(c.getAlpha() * MathHelper.clamp(k, 0f, 1f)));
    }

    private float parX, parY;

    // --- Анимации: открытие экрана, прожатие кнопок, фокус поиска, dt для плавного скролла ---
    private long openMs;
    private long lastFrameMs;
    private final Map<String, Long> pressMs = new HashMap<>();
    private final Animation searchFocusAnim = MenuChrome.anim();

    public CustomMultiplayerScreen(Screen parent) {
        super(Text.literal("Сетевая игра"));
        this.parent = parent;
    }

    // ---- singleplayer-world mode hooks (same UI, different data) ----
    protected boolean worldsMode() { return false; }
    protected String titleText() { return "СЕТЕВАЯ ИГРА"; }
    protected String searchPlaceholder() { return "Поиск по серверам..."; }
    protected String footerAddLabel() { return "ДОБАВИТЬ"; }
    protected String footerJoinLabel() { return "ПОДКЛЮЧИТЬСЯ"; }
    protected String footerDirectLabel() { return "ПРЯМОЕ ПОДКЛЮЧЕНИЕ"; }
    protected String emptyTitle() { return "Список серверов пуст"; }
    protected String emptySearchTitle() { return "Ничего не найдено"; }
    protected boolean showAccountPill() { return true; }
    protected boolean showStatus() { return true; }
    protected boolean usePinnedServers() { return true; }
    protected boolean pingServers() { return true; }
    protected ServerInfo selectedServerInfo() { return sel(); }
    protected NativeImageBackedTexture rowIcon(ServerInfo s) { return favicon(s); }
    protected void setServerEntries(java.util.List<ServerInfo> entries) {
        serverList = new ServerList(mc);
        for (ServerInfo s : entries) serverList.add(s, false);
        rebuildVisible();
    }
    protected void rebuildRows() { rebuildVisible(); }

    @Override
    protected void init() {
        closed = false;
        cancelPings();
        if (openMs == 0L) openMs = System.currentTimeMillis(); // resize не перезапускает анимацию
        serverList = new ServerList(mc);
        serverList.loadFile();
        rebuildVisible();
        refreshAll();
        fetchPinned();
    }

    /** Подтягиваем закреплённые сервера с бэкенда (подписанный GET). Колбэк — в HTTP-потоке. */
    private void fetchPinned() {
        if (!usePinnedServers()) { applyPinned(List.of()); return; }
        UserSyncManager sync = LinkenVisuals.getInstance().getUserSyncManager();
        if (sync == null) {
            applyPinned(List.of());   // синка нет — показываем хотя бы вшитые
            return;
        }
        sync.fetchPinnedServers(list -> mc.execute(() -> applyPinned(list)));
    }

    private void applyPinned(List<UserSyncManager.PinnedServer> list) {
        if (closed) return;
        pinned.clear();
        Set<String> seen = new HashSet<>();
        for (UserSyncManager.PinnedServer ps : list) {
            pinned.add(new ServerInfo(ps.name(), ps.ip(), ServerInfo.ServerType.OTHER));
            seen.add(ps.ip() == null ? "" : ps.ip().toLowerCase(Locale.ROOT));
        }
        // Вшитые закреплённые идут ПОСЛЕ серверных — так порядок с бэкенда (сверху АРТИГРИФ) не ломается,
        // а оформление у строки то же самое: они лежат в том же списке pinned.
        for (String[] b : BUILTIN_PINNED) {
            if (seen.add(b[1].toLowerCase(Locale.ROOT))) {
                pinned.add(new ServerInfo(b[0], b[1], ServerInfo.ServerType.OTHER));
            }
        }
        rebuildVisible();
        for (ServerInfo s : pinned) ping(s);
    }

    private boolean isPinned(ServerInfo s) {
        return s != null && pinned.contains(s);
    }

    /** Отметить прожатие кнопки (короткий «клик»-эффект в отрисовке). */
    private void press(String id) {
        pressMs.put(id, System.currentTimeMillis());
    }

    /** 1 → 0 за 150 мс с момента прожатия. */
    private float pressT(String id) {
        Long t = pressMs.get(id);
        if (t == null) return 0f;
        float e = (System.currentTimeMillis() - t) / 150f;
        if (e >= 1f) {
            pressMs.remove(id);
            return 0f;
        }
        return 1f - e;
    }

    // ---------- Данные ----------

    private void rebuildVisible() {
        // Полный порядок: закреплённые сверху, затем пользовательские (дубли закреплённых по адресу
        // выкидываем — закреплённый показывается один раз, даже если он уже был в servers.dat).
        allRows.clear();
        Set<String> pinnedAddrs = new HashSet<>();
        for (ServerInfo p : pinned) {
            allRows.add(p);
            pinnedAddrs.add(p.address == null ? "" : p.address.toLowerCase(Locale.ROOT));
        }
        for (int i = 0; i < serverList.size(); i++) {
            ServerInfo s = serverList.get(i);
            if (s == null) continue;
            if (pinnedAddrs.contains(s.address == null ? "" : s.address.toLowerCase(Locale.ROOT))) continue;
            allRows.add(s);
        }

        visible.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (ServerInfo s : allRows) {
            // Закреплённые «постоянно в самом верху» — не прячем их поиском.
            if (!isPinned(s) && !q.isEmpty()) {
                String name = s.name == null ? "" : s.name.toLowerCase(Locale.ROOT);
                String addr = s.address == null ? "" : s.address.toLowerCase(Locale.ROOT);
                String ip = RESOLVED_IPS.getOrDefault(key(s), "");
                if (!name.contains(q) && !addr.contains(q) && !ip.contains(q)) continue;
            }
            visible.add(s);
        }
        if (selected >= visible.size()) selected = visible.size() - 1;

        visibleIndex.clear();
        for (int i = 0; i < visible.size(); i++) visibleIndex.put(visible.get(i), i);
    }

    /** Перепинговать все серверы списка (кнопка «Обновить» и вход на экран). */
    private void refreshAll() {
        if (!pingServers()) return;
        for (ServerInfo s : allRows) {
            if (s != null) ping(s);
        }
    }

    private void ping(ServerInfo s) {
        String key = key(s);
        if (closed || !pinging.add(s)) return;
        PingAttempt previous = attempts.remove(s);
        if (previous != null) previous.close();
        PingAttempt attempt = new PingAttempt(s);
        attempts.put(s, attempt);
        s.setStatus(ServerInfo.Status.PINGING);
        pingStartMs.put(s, System.currentTimeMillis());
        // НЕ зачищаем label/playerCountLabel: там сохранённый из servers.dat MOTD/онлайн — показываем
        // его сразу, а успешный пинг перезапишет. Иначе у не-пингующихся серверов MOTD пропадает.
        // Плюс префилл из сессионного кэша: свежесозданный ServerInfo после «зашёл-вышел» пуст.
        CachedStatus cached = STATUS_CACHE.get(key);
        if (cached != null && (s.label == null || s.label.getString().isBlank())) {
            s.label = cached.label();
            s.playerCountLabel = cached.playerCountLabel();
            s.players = cached.players();
            s.ping = cached.ping();
        }
        // Семантика колбэков пингера (проверено по байткоду 1.21.4): ПЕРВЫЙ Runnable зовётся в
        // onResponse (пришли метаданные: MOTD/версия/онлайн), ВТОРОЙ — в onPingResult, т.е. при
        // УСПЕШНОМ замере пинга. Раньше на второй был повешен markUnreachable — каждый удачный
        // пинг помечал сервер «недоступен», и число пинга («NN мс») не показывалось никогда.
        attempt.task = PING_POOL.submit(() -> {
            if (attempt.cancelled) return;
            try {
                attempt.pinger.add(attempt.probe, () -> mc.execute(() -> {
                    if (attempt.cancelled || attempts.get(s) != attempt) return;
                    copyPingResult(s, attempt.probe);
                    serversDirty = true; // не пишем servers.dat на каждый пинг — см. flush в render()
                    faviconTried.remove(key); // фавикон мог приехать в этом пинге
                    if (s.label != null && !s.label.getString().isBlank()) {
                        STATUS_CACHE.put(key, new CachedStatus(s.label, s.playerCountLabel, s.players, s.ping));
                    }
                }), () -> mc.execute(() -> {
                    if (attempt.cancelled || attempts.get(s) != attempt) return;
                    copyPingResult(s, attempt.probe);
                    pinging.remove(s);
                    s.setStatus(ServerInfo.Status.SUCCESSFUL);
                    pingStartMs.remove(s);
                    STATUS_CACHE.put(key, new CachedStatus(s.label, s.playerCountLabel, s.players, s.ping));
                }), net.minecraft.network.NetworkingBackend.remote(mc.options.shouldUseNativeTransport()));
                // Optional search index, after the actual status request has started.
                if (!attempt.cancelled && !RESOLVED_IPS.containsKey(key)) {
                    try {
                        String host = ServerAddress.parse(attempt.probe.address).getAddress();
                        RESOLVED_IPS.put(key, java.net.InetAddress.getByName(host).getHostAddress());
                    } catch (java.net.UnknownHostException ignored) { }
                }
            } catch (Exception t) {
                mc.execute(() -> {
                    if (!attempt.cancelled && attempts.get(s) == attempt) markUnreachable(s);
                });
            } finally {
                // add() can finish after removed()/timeout, including after blocking DNS resolution.
                if (attempt.cancelled) attempt.pinger.cancel();
            }
        });
    }

    /** Сервер не ответил — помечаем недоступным (кэшированный MOTD, если он есть, не затираем). */
    private static void copyPingResult(ServerInfo target, ServerInfo source) {
        target.label = source.label;
        target.playerCountLabel = source.playerCountLabel;
        target.players = source.players;
        target.ping = source.ping;
        target.protocolVersion = source.protocolVersion;
        target.version = source.version;
        target.playerListSummary = source.playerListSummary;
        target.setFavicon(source.getFavicon());
    }

    private void cancelPings() {
        for (PingAttempt attempt : attempts.values()) attempt.close();
        attempts.clear();
        pinging.clear();
        pingStartMs.clear();
    }

    private void markUnreachable(ServerInfo s) {
        PingAttempt attempt = attempts.remove(s);
        if (attempt != null) attempt.close();
        pinging.remove(s);
        s.setStatus(ServerInfo.Status.UNREACHABLE);
        pingStartMs.remove(s);
        if (s.label == null || s.label.getString().isBlank()) {
            s.label = Text.literal("Не удалось подключиться");
        }
    }

    /** Надёжный таймаут: если сервер висит в PINGING >7с (колбэк не пришёл) — недоступен. */
    private void checkPingTimeouts() {
        if (pingStartMs.isEmpty()) return;
        long now = System.currentTimeMillis();
        var it = pingStartMs.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerInfo s = e.getKey();
            if (s.getStatus() == ServerInfo.Status.SUCCESSFUL) { it.remove(); continue; }
            if (s.getStatus() == ServerInfo.Status.PINGING && now - e.getValue() > 7000L) {
                markUnreachable(s);
                it.remove();
            }
        }
    }

    private static String key(ServerInfo s) {
        return (s.name == null ? "" : s.name) + '|' + (s.address == null ? "" : s.address);
    }

    /** Фавикон сервера как текстура (лениво, один раз на набор байт). Null → рисуем букву-фолбэк. */
    private NativeImageBackedTexture favicon(ServerInfo s) {
        String k = key(s);
        NativeImageBackedTexture cached = favicons.get(k);
        if (cached != null) return cached;
        if (!faviconTried.add(k)) return null;
        byte[] bytes = s.getFavicon();
        if (bytes == null) return null;
        try {
            NativeImage img = NativeImage.read(bytes);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "server_favicon", img);
            favicons.put(k, tex);
            return tex;
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.warn("[Multiplayer] bad favicon for {}", s.address);
            return null;
        }
    }

    // ---------- Рендер ----------

    private void recomputeScale() {
        if (mc == null || mc.getWindow() == null) return;
        // HiDPI-safe: от GUI-scaled размеров (getHeight() — логический размер окна, scaleFactor — от
        // фреймбуфера; на Retina давало uiScale вдвое меньше). См. CustomMainMenuScreen.
        uiScale = Math.max(0.05f, mc.getWindow().getScaledHeight() / MenuChrome.DESIGN_HEIGHT);
        vw = Math.max(1, Math.round(mc.getWindow().getScaledWidth() / uiScale));
        vh = Math.max(1, Math.round(mc.getWindow().getScaledHeight() / uiScale));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ru.method.linkenvisuals.utils.render.Render2D.prepareHudFrame(context);
        recomputeScale();
        MenuChrome.tickMusic();
        for (PingAttempt attempt : java.util.List.copyOf(attempts.values())) {
            if (!attempt.cancelled) attempt.pinger.tick();
        }
        checkPingTimeouts();
        if (serversDirty && System.currentTimeMillis() - lastSaveMs > 2000L) {
            lastSaveMs = System.currentTimeMillis();
            serversDirty = false;
            serverList.saveFile();
        }

        Matrix3x2fStack ms = context.getMatrices();
        ms.pushMatrix();
        ms.scale(uiScale, uiScale);
        int mx = Math.round(mouseX / uiScale);
        int my = Math.round(mouseY / uiScale);
        // Компенсация толщины вектор-иконок под наш ms.scale (иначе тонкие штрихи < 1px device).
        ClickGuiIcons.pixelScale(uiScale);

        // Фон: арт мейнменю + виньетка по спеке.
        float tpx = MathHelper.clamp((mx - vw / 2f) / (vw / 2f), -1f, 1f);
        float tpy = MathHelper.clamp((my - vh / 2f) / (vh / 2f), -1f, 1f);
        parX += (tpx - parX) * 0.08f;
        parY += (tpy - parY) * 0.08f;
        MenuChrome.drawScreenBackground(ms, vw, vh, parX, parY);

        // Хром, общий с мейнменю.
        drawBackChip(ms, mx, my);
        MenuChrome.drawLogo(ms, vw);
        if (showAccountPill()) drawAccountPill(ms, mx, my);
        MenuChrome.drawClock(ms, vw);
        drawVersionLabel(ms);
        if (MenuChrome.hasMusic()) MenuChrome.drawMusic(ms, vw, vh, mx, my, hCover, rCover);

        // Плавное появление окна при входе на экран: слайд снизу 18px + easeOutCubic (240 мс).
        float openT = MathHelper.clamp((System.currentTimeMillis() - openMs) / 240f, 0f, 1f);
        float openE = 1f - (1f - openT) * (1f - openT) * (1f - openT);
        ms.pushMatrix();
        ms.translate(0f, (1f - openE) * 18f);
        drawWindow(context, ms, mx, my);
        ms.popMatrix();

        ms.popMatrix();
        ClickGuiIcons.pixelScale(1f); // сброс — чтобы ClickGUI/другие экраны не унаследовали наш scale
    }

    // ---------- Чип «Назад» (top 44, left 56) ----------
    private void drawBackChip(Matrix3x2fStack ms, int mx, int my) {
        Instance f = Fonts.UNBOUNDED.getFont(10f);
        String label = "Назад";
        float h = 38f, icon = 17f;
        float w = 12f + icon + 9f + f.getWidth(label) + 15f; // padding 0 15 0 12, gap 9
        float x = 56f, y = 44f;
        boolean hov = MenuChrome.hit(mx, my, x, y, w, h);
        float t = MenuChrome.upd(hBack, hov);
        float ty = y - t; // подъём 1px на ховере
        MenuChrome.drawSecondaryMaterial(ms, x, ty, w, h, 8f, t, false);
        ClickGuiIcons.arrowLeft(ms, x + 12f + icon / 2f, ty + h / 2f, icon, MUTED);
        Render2D.drawFont(ms, f, label, x + 12f + icon + 9f, ty + h / 2f - f.getHeight() / 2f, TEXT2);
        MenuChrome.set(rBack, x, y, w, h);
    }

    // ---------- Пилюля аккаунта (под лого, gap 12) ----------
    protected void drawAccountPill(Matrix3x2fStack ms, int mx, int my) {
        String nick = MenuChrome.nick();
        Instance nf = Fonts.UNBOUNDED.getFont(11f);
        Instance cf = Fonts.GOLOS.getFont(11f);
        String change = "сменить";
        float h = 34f, av = 26f;
        // padding 0 5; gap 10; разделитель 1×14; «сменить» + отступ справа 7
        float w = 5f + av + 10f + nf.getWidth(nick) + 10f + 1f + 10f + cf.getWidth(change) + 7f;
        float logoBottom = 44f + Fonts.GOLOS_SEMI.getFont(23f).getHeight();
        float x = vw / 2f - w / 2f, y = logoBottom + 12f;

        MenuChrome.drawSecondaryMaterial(ms, x, y, w, h, 17f, 0f, false);

        // Аватар: тёмный круг с акцентной рамкой + буква ника.
        float ax = x + 5f, ay = y + (h - av) / 2f;
        Render2D.drawRoundedVerticalGradient(ms, ax, ay, av, av, av / 2f, new Color(0x2a, 0x38, 0x30), new Color(0x10, 0x16, 0x13));
        Render2D.drawBorder(ms, ax, ay, av, av, av / 2f, 1.0f, 1.4f, MenuChrome.accentA(0.32f));
        Instance af = Fonts.UNBOUNDED.getFont(9f);
        String initial = nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(Locale.ROOT);
        Render2D.drawFont(ms, af, initial, ax + (av - af.getWidth(initial)) / 2f, ay + (av - af.getHeight()) / 2f, MenuChrome.accent());

        float tx = ax + av + 10f;
        Render2D.drawFont(ms, nf, nick, tx, y + h / 2f - nf.getHeight() / 2f, TEXT);
        tx += nf.getWidth(nick) + 10f;
        Render2D.drawRoundedRect(ms, tx, y + (h - 14f) / 2f, 1f, 14f, 0f, MenuChrome.white(0.12f));
        tx += 1f + 10f;

        float chW = cf.getWidth(change);
        float chY = y + h / 2f - cf.getHeight() / 2f - 1f; // оптически поднято на 1px
        boolean chHov = MenuChrome.hit(mx, my, tx, y, chW, h);
        float chT = MenuChrome.upd(hChange, chHov);
        Render2D.drawFont(ms, cf, change, tx, chY, MenuChrome.lerp(MUTED2, MenuChrome.accent(), chT));
        MenuChrome.set(rChange, tx, y, chW, h);
    }

    // ---------- Версия (низ-лево) ----------
    private void drawVersionLabel(Matrix3x2fStack ms) {
        Instance f = Fonts.PLEXMONO.getFont(9.5f);
        String s = "by @emper0rhero";
        Render2D.drawFont(ms, f, s, 56f, vh - 44f - f.getHeight(), MUTED4);
    }

    // ---------- Центральное окно ----------

    private float winX() { return vw / 2f - WIN_W / 2f; }

    private int maxRows() {
        // Высота окна — по контенту, но список не должен налезать на плеер снизу.
        float bottomLimit = vh - 44f - 110f;
        float avail = bottomLimit - WIN_TOP - TOPBAR_H - FOOTER_H;
        return Math.max(1, (int) Math.floor(avail / ROW_H));
    }

    private float listH() {
        int rows = Math.min(Math.max(visible.size(), 1), maxRows());
        return rows * ROW_H;
    }

    private float winH() { return TOPBAR_H + listH() + FOOTER_H; }

    private void drawWindow(DrawContext context, Matrix3x2fStack ms, int mx, int my) {
        float x = winX(), y = WIN_TOP, w = WIN_W, h = winH();

        WindowMaterial.window(ms, x, y, w, h);
        drawTopbar(ms, x, y, w, mx, my);

        float listTop = y + TOPBAR_H;
        drawList(context, ms, x, listTop, w, listH(), mx, my);

        drawFooter(ms, x, y + h - FOOTER_H, w, mx, my);

        // Уголки-скобки рисуем последними — поверх контента (спека: 14×14, 1px, accent 0.65, отступ 10).
        WindowMaterial.corners(ms, x, y, w, h);
    }

    // ---------- Топбар ----------
    private void drawTopbar(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + TOPBAR_H / 2f;

        Instance tf = Fonts.GOLOS_SEMI.getFont(17f);
        Render2D.drawFont(ms, tf, titleText(), x + PAD_X, cy - tf.getHeight() / 2f, TEXT);

        // «Обновить» 36×36 — у правого края. Прожатие: иконка на миг сжимается и «крутится» вниз.
        float rs = 36f, rx = x + w - PAD_X - rs, ry = cy - rs / 2f;
        boolean rHov = MenuChrome.hit(mx, my, rx, ry, rs, rs);
        float rT = MenuChrome.upd(hRefresh, rHov);
        float rP = pressT("refresh");
        WindowMaterial.fieldBox(ms, rx, ry + rP, rs, rs, rT, false);
        ClickGuiIcons.refresh(ms, rx + rs / 2f, ry + rP + rs / 2f, 18f - 3f * rP,
                MenuChrome.lerp(MUTED, MenuChrome.accent(), Math.max(rT, rP)));
        MenuChrome.set(rRefresh, rx, ry, rs, rs);

        // Поиск слева от «Обновить» (gap 12): при фокусе плавно расширяется влево и подсвечивается.
        float fT = MenuChrome.upd(searchFocusAnim, searchFocused);
        float sw = 320f + 24f * fT, sh = 36f, sx = rx - 12f - sw, sy = cy - sh / 2f;
        boolean sHov = MenuChrome.hit(mx, my, sx, sy, sw, sh);
        float sT = MenuChrome.upd(hSearch, sHov || searchFocused);
        WindowMaterial.fieldBox(ms, sx, sy, sw, sh, Math.max(sT, fT), false);
        ClickGuiIcons.search(ms, sx + 13f + 7f, sy + sh / 2f, 17f, MenuChrome.lerp(MUTED3, MenuChrome.accent(), fT));
        Instance pf = Fonts.GOLOS.getFont(12.5f);
        float qx = sx + 13f + 14f + 10f;
        if (query.isEmpty() && !searchFocused) {
            Render2D.drawFont(ms, pf, searchPlaceholder(), qx, sy + sh / 2f - pf.getHeight() / 2f, MUTED3);
        } else {
            Render2D.drawFont(ms, pf, query, qx, sy + sh / 2f - pf.getHeight() / 2f, TEXT2);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                Render2D.drawRoundedRect(ms, qx + pf.getWidth(query) + 1f, sy + sh / 2f - 6f, 1f, 12f, 0.5f, TEXT2);
            }
        }
        MenuChrome.set(rSearch, sx, sy, sw, sh);

        // Волосяная линия снизу.
        Render2D.drawRoundedRect(ms, x, y + TOPBAR_H - 1f, w, 1f, 0f, MenuChrome.white(0.07f));
    }

    // ---------- Список серверов ----------
    private void drawList(DrawContext context, Matrix3x2fStack ms, float x, float y, float w, float h, int mx, int my) {
        rRows.clear();

        // Пустое состояние показываем только когда отфильтрованные строки ДОГАСЛИ — иначе текст
        // наложился бы на анимацию «отбрасывания».
        boolean fading = false;
        for (RowAnim a : rowAnims.values()) if (a.alpha > 0.01f) { fading = true; break; }
        if (visible.isEmpty() && !fading) {
            Instance ef = Fonts.GOLOS.getFont(13f);
            String s = query.isEmpty() ? emptyTitle() : emptySearchTitle();
            Render2D.drawFont(ms, ef, s, x + (w - ef.getWidth(s)) / 2f, y + h / 2f - ef.getHeight() / 2f - 14f, MUTED3);
            if (query.isEmpty()) {
                Instance bf = Fonts.UNBOUNDED.getFont(11f);
                String b = footerAddLabel();
                float bw = 18f + 15f + 10f + bf.getWidth(b) + 18f, bh = 44f;
                float bx = x + (w - bw) / 2f, by = y + h / 2f + 6f;
                boolean hov = MenuChrome.hit(mx, my, bx, by, bw, bh);
                float t = MenuChrome.upd(hAddEmpty, hov);
                WindowMaterial.fieldBox(ms, bx, by, bw, bh, t, false);
                ClickGuiIcons.plus(ms, bx + 18f + 7.5f, by + bh / 2f, 18f, MUTED);
                Render2D.drawFont(ms, bf, b, bx + 18f + 15f + 10f, by + bh / 2f - bf.getHeight() / 2f, TEXT2);
                MenuChrome.set(rAddEmpty, bx, by, bw, bh);
            } else {
                MenuChrome.set(rAddEmpty, 0f, 0f, 0f, 0f);
            }
            return;
        }
        MenuChrome.set(rAddEmpty, 0f, 0f, 0f, 0f);

        // Плавный скролл: экспоненциальное сглаживание по реальному dt (не зависит от FPS) —
        // список мягко «доезжает» до цели вместо ступенчатых прыжков на колесо.
        long nowMs = System.currentTimeMillis();
        float dtSec = lastFrameMs == 0L ? 0.016f : Math.min(0.05f, (nowMs - lastFrameMs) / 1000f);
        lastFrameMs = nowMs;
        float maxScroll = Math.max(0f, visible.size() * ROW_H - h);
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-13f * dtSec));
        if (Math.abs(scrollTarget - scroll) < 0.4f) scroll = scrollTarget;

        for (int i = 0; i < visible.size(); i++) rRows.add(new float[4]);

        // startScissor принимает DESIGN-координаты: enableScissor сам применяет текущую матрицу.
        Render2D.startScissor(context, x, y, w, h);

        // Рисуем ВСЕ серверы: совпавшие едут на свои места (alpha→1), отфильтрованные гаснут на месте
        // (alpha→0) — отсюда эффект «отбрасывания» при вводе в поиск.
        for (ServerInfo s : allRows) {
            if (s == null) continue;
            RowAnim ra = rowAnims.computeIfAbsent(s, k -> new RowAnim());
            int vi = visibleIndex.getOrDefault(s, -1);
            boolean match = vi >= 0;

            float targetY = match ? vi * ROW_H : ra.y;
            if (!ra.placed) { ra.y = targetY; ra.alpha = match ? 1f : 0f; ra.placed = true; }
            ra.y += (targetY - ra.y) * 0.22f;
            if (Math.abs(targetY - ra.y) < 0.3f) ra.y = targetY;
            ra.alpha += ((match ? 1f : 0f) - ra.alpha) * 0.22f;
            if (Math.abs((match ? 1f : 0f) - ra.alpha) < 0.01f) ra.alpha = match ? 1f : 0f;

            if (ra.alpha <= 0.01f) continue;
            float ry = y + ra.y - scroll;
            if (ry + ROW_H < y - ROW_H || ry > y + h + ROW_H) continue;
            // Гаснущая строка ещё и слегка уезжает влево — «отбрасывание».
            float dx = (1f - ra.alpha) * -14f;
            drawRow(ms, s, vi, x + dx, ry, w, mx, my, y, h, ra.alpha);
        }
        Render2D.stopScissor(context);
    }

    /** Производные строки из кэша; пересчёт — только когда пингер/edit подменили source-объекты. */
    private RowText rowText(ServerInfo s) {
        RowText t = rowTexts.computeIfAbsent(s, k -> new RowText());
        if (t.clean == null || t.name != s.name || t.label != s.label || t.version != s.version) {
            t.name = s.name;
            t.label = s.label;
            t.version = s.version;
            t.clean = cleanName(s);
            t.motd = motdText(s);
            t.chip = versionChip(s);
        }
        return t;
    }

    private void drawRow(Matrix3x2fStack ms, ServerInfo s, int i, float x, float y, float w, int mx, int my, float clipY, float clipH, float alpha) {
        RowText rt = rowText(s);
        boolean sel = i >= 0 && i == selected;
        boolean hov = alpha > 0.9f && my >= clipY && my <= clipY + clipH && MenuChrome.hit(mx, my, x, y, w, ROW_H);
        float t = i < 0 ? 0f : MenuChrome.upd(hRows.computeIfAbsent(i, k -> MenuChrome.anim()), hov && !sel);

        // Закреплённый сервер — глоу: акцентная подложка + мягкое свечение по контуру + чёткая рамка.
        // Всегда сверху, редактировать/удалять нельзя (см. footer/openEdit/askDelete).
        boolean pin = isPinned(s);
        if (pin) {
            float gx = x + 2f, gy = y + 3f, gw = w - 4f, gh = ROW_H - 6f;
            Render2D.drawRoundedRect(ms, gx, gy, gw, gh, 9f, fade(MenuChrome.accentA(0.08f), alpha));
            Render2D.drawBorder(ms, gx, gy, gw, gh, 9f, 1.0f, 3.4f, fade(MenuChrome.accentA(0.18f), alpha));
            Render2D.drawBorder(ms, gx, gy, gw, gh, 9f, 1.0f, 1.4f, fade(MenuChrome.accentA(0.6f), alpha));
        }

        // Выбранная — фон accent 0.05 + полоса слева 2px accent; hover — white 0.035.
        if (sel) {
            Render2D.drawRoundedRect(ms, x, y, w, ROW_H, 0f, fade(MenuChrome.accentA(0.05f), alpha));
            Render2D.drawRoundedRect(ms, x, y, 2f, ROW_H, 0f, fade(MenuChrome.accent(), alpha));
        } else if (t > 0.001f) {
            Render2D.drawRoundedRect(ms, x, y, w, ROW_H, 0f, fade(MenuChrome.white(0.035f * t), alpha));
        }
        Render2D.drawRoundedRect(ms, x, y + ROW_H - 1f, w, 1f, 0f, fade(MenuChrome.white(0.05f), alpha));

        float cx = x + PAD_X, cy = y + ROW_H / 2f;

        // 1. Тайл 38×38 radius 8: фавикон 32×32, иначе тонированный градиент + буква (рамка white 0.1).
        float tile = 38f, tx = cx, ty = cy - tile / 2f;
        NativeImageBackedTexture fav = rowIcon(s);
        if (fav != null) {
            float fs = 32f, fo = (tile - fs) / 2f;
            Render2D.drawTexture(ms, tx + fo, ty + fo, fs, fs, 6f, fav, fade(Color.WHITE, alpha));
            Render2D.drawBorder(ms, tx, ty, tile, tile, 8f, 1.0f, 1.4f, fade(MenuChrome.white(0.1f), alpha));
        } else {
            Color tint = tileTint(s);
            Render2D.drawRoundedVerticalGradient(ms, tx, ty, tile, tile, 8f, fade(tint, alpha), fade(darker(tint), alpha));
            Render2D.drawBorder(ms, tx, ty, tile, tile, 8f, 1.0f, 1.4f,
                    fade(sel ? MenuChrome.accentA(0.32f) : MenuChrome.white(0.1f), alpha));
            Instance lf = Fonts.UNBOUNDED.getFont(12f);
            String cn = rt.clean;
            String letter = (cn.isEmpty() ? "?" : cn.substring(0, 1)).toUpperCase(Locale.ROOT);
            Render2D.drawFont(ms, lf, letter, tx + (tile - lf.getWidth(letter)) / 2f, ty + (tile - lf.getHeight()) / 2f,
                    fade(sel ? MenuChrome.accent() : letterColor(s), alpha));
        }

        // 3. Правая колонка (align-end) — считаем первой, чтобы знать ширину под имя/MOTD.
        float rightX = x + w - PAD_X - 16f;
        if (showStatus()) {
            Instance plf = Fonts.PLEXMONO.getFont(11f);
            Instance pgf = Fonts.PLEXMONO.getFont(10.5f);
            String players = playersText(s);
            String pingS = pingText(s);
            float barsW = 4f * 3f + 3f * 2f;
            float pingRowW = barsW + (pingS.isEmpty() ? 0f : 7f + pgf.getWidth(pingS));
            float rightW = Math.max(plf.getWidth(players), pingRowW);
            rightX = x + w - PAD_X - rightW;

            Render2D.drawFont(ms, plf, players, x + w - PAD_X - plf.getWidth(players), cy - 5f / 2f - plf.getHeight(), fade(MUTED, alpha));
            float barsX = x + w - PAD_X - pingRowW, barsBottom = cy + 5f / 2f + 14f;
            drawPingBars(ms, barsX, barsBottom, s, alpha);
            if (!pingS.isEmpty()) {
                Render2D.drawFont(ms, pgf, pingS, barsX + barsW + 7f, barsBottom - 14f / 2f - pgf.getHeight() / 2f,
                        fade(sel ? MenuChrome.accent() : MUTED, alpha));
            }
        }

        // 2. Колонка: имя (Unbounded) + один чип версии, ниже MOTD (Golos, ellipsis).
        float colX = tx + tile + 14f;
        float colW = rightX - 14f - colX;
        Instance nf = Fonts.UNBOUNDED.getFont(11.5f);
        String name = rt.clean;
        String nameClip = clip(nf, name, colW);
        float nameY = cy - 4f / 2f - nf.getHeight();
        Render2D.drawFont(ms, nf, nameClip, colX, nameY, fade(TEXT, alpha));

        String ver = rt.chip;
        if (!ver.isEmpty()) {
            Instance vf = Fonts.PLEXMONO.getFont(9f);
            float chipX = colX + nf.getWidth(nameClip) + 8f;
            float chipW = vf.getWidth(ver) + 12f, chipH = 16f;
            if (chipX + chipW <= colX + colW) {
                float chipY = nameY + nf.getHeight() / 2f - chipH / 2f;
                Render2D.drawBorder(ms, chipX, chipY, chipW, chipH, 4f, 1.0f, 1.4f, fade(MenuChrome.white(0.12f), alpha));
                Render2D.drawFont(ms, vf, ver, chipX + 6f, chipY + chipH / 2f - vf.getHeight() / 2f, fade(MUTED, alpha));
            }
        }

        Instance mf = Fonts.GOLOS.getFont(12f);
        Render2D.drawFont(ms, mf, clip(mf, rt.motd, colW), colX, cy + 4f / 2f, fade(MUTED2, alpha));

        if (i >= 0 && i < rRows.size()) MenuChrome.set(rRows.get(i), x, y, w, ROW_H);
    }

    /** 4 полоски: высоты 5/8/11/14 снизу, ширина 3, зазор 2, radius 1; залитые — accent, пустые white 0.16. */
    private void drawPingBars(Matrix3x2fStack ms, float x, float bottom, ServerInfo s, float alpha) {
        int filled = pingBars(s);
        float[] hs = { 5f, 8f, 11f, 14f };
        for (int i = 0; i < 4; i++) {
            float bx = x + i * 5f;
            Color c = i < filled ? MenuChrome.accent() : MenuChrome.white(0.16f);
            Render2D.drawRoundedRect(ms, bx, bottom - hs[i], 3f, hs[i], 1f, fade(c, alpha));
        }
    }

    private static int pingBars(ServerInfo s) {
        if (s.getStatus() == ServerInfo.Status.UNREACHABLE || s.getStatus() == ServerInfo.Status.INCOMPATIBLE) return 0;
        if (s.getStatus() != ServerInfo.Status.SUCCESSFUL) return 0;
        long p = s.ping;
        if (p < 80) return 4;    // <80 мс — все 4 палки
        if (p <= 120) return 3;  // 80–120 мс — 3 палки
        if (p <= 200) return 2;  // 120–200 мс — 2 палки
        return 1;                // >200 мс — 1 палка
    }

    /** Только число «NN мс» при успешном пинге; иначе пусто (без ···/—). */
    private static String pingText(ServerInfo s) {
        return s.getStatus() == ServerInfo.Status.SUCCESSFUL ? s.ping + " мс" : "";
    }

    private static final Pattern FMT = Pattern.compile("[§&][0-9A-Za-z]");
    private static final Pattern AD_PAREN = Pattern.compile("(?i)\\s*[\\(\\[](?=[^\\)\\]]*(?:erid|\\d+\\+))[^\\)\\]]*[\\)\\]]");
    private static final Pattern ERID = Pattern.compile("(?i)\\berid[:=]?\\S*");
    private static final Pattern VER = Pattern.compile("1\\.(\\d{1,2})(?:\\.\\d{1,2})?");

    private static String stripFormatting(String s) {
        if (s == null) return "";
        return FMT.matcher(s).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    /** Пользовательское имя без ad-мусора: «dob.boxpvp.day (12+,erid:…)» → «dob.boxpvp.day». */
    private static String cleanName(ServerInfo s) {
        String n = stripFormatting(s.name == null ? "" : s.name);
        n = AD_PAREN.matcher(n).replaceAll("");
        n = ERID.matcher(n).replaceAll("").trim();
        n = n.replaceAll("[\\s,;·|]+$", "").trim();
        if (n.isEmpty()) n = s.address == null ? "" : s.address;
        return n;
    }

    private static String motdText(ServerInfo s) {
        return stripFormatting(s.label == null ? "" : s.label.getString().replace('\n', ' '));
    }

    /** Версия — ОДИН компактный чип: список «1.8.x … 1.21.x, 26.x» → «1.8–1.21». */
    private static String versionChip(ServerInfo s) {
        String raw = stripFormatting(s.version == null ? "" : s.version.getString());
        if (raw.isEmpty()) return "";
        Matcher m = VER.matcher(raw);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        while (m.find()) {
            int minor = Integer.parseInt(m.group(1));
            if (minor < lo) lo = minor;
            if (minor > hi) hi = minor;
        }
        if (hi < 0) return raw.length() > 12 ? raw.substring(0, 12) : raw; // нет 1.x — короткий сырой
        return lo == hi ? "1." + lo : "1." + lo + "–" + "1." + hi; // en-dash
    }

    private static String playersText(ServerInfo s) {
        if (s.players == null) return "—";
        return group(s.players.online()) + " / " + group(s.players.max());
    }

    /** «1482» → «1 482» (как в спеке). */
    private static String group(int v) {
        String s = Integer.toString(Math.max(0, v));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) out.append(' ');
            out.append(s.charAt(i));
        }
        return out.toString();
    }

    /** Стабильный тон тайла из адреса — фолбэк, когда у сервера нет фавикона. */
    private static Color tileTint(ServerInfo s) {
        String src = s.address == null ? (s.name == null ? "?" : s.name) : s.address;
        int hash = Math.abs(src.hashCode());
        float hue = (hash % 360) / 360f;
        Color c = Color.getHSBColor(hue, 0.42f, 0.20f);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
    }

    private static Color letterColor(ServerInfo s) {
        String src = s.address == null ? (s.name == null ? "?" : s.name) : s.address;
        int hash = Math.abs(src.hashCode());
        Color c = Color.getHSBColor((hash % 360) / 360f, 0.45f, 0.80f);
        return c;
    }

    private static Color darker(Color c) {
        return new Color(Math.round(c.getRed() * 0.62f), Math.round(c.getGreen() * 0.62f), Math.round(c.getBlue() * 0.62f), c.getAlpha());
    }

    private static String clip(Instance f, String text, float max) {
        if (text == null) return "";
        if (max <= 0f) return "";
        if (f.getWidth(text) <= max) return text;
        String ell = "...";
        float ew = f.getWidth(ell);
        StringBuilder sb = new StringBuilder();
        float acc = 0f;
        for (int i = 0; i < text.length(); i++) {
            float cw = f.getWidth(String.valueOf(text.charAt(i)));
            if (acc + cw + ew > max) break;
            sb.append(text.charAt(i));
            acc += cw;
        }
        return sb + ell;
    }

    // ---------- Футер ----------
    private void drawFooter(Matrix3x2fStack ms, float x, float y, float w, int mx, int my) {
        float cy = y + FOOTER_H / 2f;
        boolean has = selected >= 0 && selected < visible.size();
        // Закреплённые редактировать/удалять нельзя — кнопки для них выключены.
        boolean canModify = has && !isPinned(visible.get(selected));

        // Слева: «Настроить» и «Удалить» (38×38, gap 9).
        float is = 38f, iy = cy - is / 2f;
        float ex = x + PAD_X;
        boolean eHov = canModify && MenuChrome.hit(mx, my, ex, iy, is, is);
        float eT = MenuChrome.upd(hEdit, eHov);
        float eP = pressT("edit");
        WindowMaterial.fieldBox(ms, ex, iy + eP, is, is, eT, false);
        ClickGuiIcons.gear(ms, ex + is / 2f, iy + eP + is / 2f, 17f - 2f * eP, canModify ? MenuChrome.lerp(MUTED, TEXT2, eT) : MUTED4);
        MenuChrome.set(rEdit, ex, iy, is, is);

        float dx = ex + is + 9f;
        boolean dHov = canModify && MenuChrome.hit(mx, my, dx, iy, is, is);
        float dT = MenuChrome.upd(hDelete, dHov);
        float dP = pressT("delete");
        WindowMaterial.fieldBox(ms, dx, iy + dP, is, is, dT, true);
        ClickGuiIcons.trash(ms, dx + is / 2f, iy + dP + is / 2f, 17f - 2f * dP, canModify ? MenuChrome.lerp(MUTED, DANGER, dT) : MUTED4);
        MenuChrome.set(rDelete, dx, iy, is, is);

        // Справа: «Подключиться» (акцент), «Добавить», «По адресу» — справа налево, gap 9.
        Instance bf = Fonts.UNBOUNDED.getFont(11f);
        float bh = 44f, by = cy - bh / 2f;

        String join = footerJoinLabel();
        float joinW = 24f + 15f + 11f + bf.getWidth(join) + 24f;
        float joinX = x + w - PAD_X - joinW;
        boolean jHov = MenuChrome.hit(mx, my, joinX, by, joinW, bh);
        float jT = MenuChrome.upd(hJoin, jHov);
        drawJoinButton(ms, joinX, by + 2f * pressT("join"), joinW, bh, jT, bf, join);
        MenuChrome.set(rJoin, joinX, by, joinW, bh);

        String add = footerAddLabel();
        float addW = 18f + 15f + 10f + bf.getWidth(add) + 18f;
        float addX = joinX - 9f - addW;
        boolean aHov = MenuChrome.hit(mx, my, addX, by, addW, bh);
        float aT = MenuChrome.upd(hAdd, aHov);
        drawFooterButton(ms, addX, by + 2f * pressT("add"), addW, bh, aT, bf, add, true);
        MenuChrome.set(rAdd, addX, by, addW, bh);

        // «По адресу» — без иконки (по просьбе): текст по центру.
        String direct = footerDirectLabel();
        float dirW = 20f + bf.getWidth(direct) + 20f;
        float dirX = addX - 9f - dirW;
        boolean diHov = MenuChrome.hit(mx, my, dirX, by, dirW, bh);
        float diT = MenuChrome.upd(hDirect, diHov);
        drawTextButton(ms, dirX, by + 2f * pressT("direct"), dirW, bh, diT, bf, direct);
        MenuChrome.set(rDirect, dirX, by, dirW, bh);
    }

    private void drawFooterButton(Matrix3x2fStack ms, float x, float y, float w, float h, float t, Instance f, String label, boolean plus) {
        Render2D.drawRoundedRect(ms, x, y, w, h, 8f, MenuChrome.white(0.04f));
        Render2D.drawBorder(ms, x, y, w, h, 8f, 1.0f, 1.4f,
                MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.24f), t));
        ClickGuiIcons.plus(ms, x + 18f + 7.5f, y + h / 2f, 18f, MUTED);
        Render2D.drawFont(ms, f, label, x + 18f + 15f + 10f, y + h / 2f - f.getHeight() / 2f, TEXT2);
    }

    /** Кнопка без иконки — текст по центру (напр. «По адресу»). */
    private void drawTextButton(Matrix3x2fStack ms, float x, float y, float w, float h, float t, Instance f, String label) {
        Render2D.drawRoundedRect(ms, x, y, w, h, 8f, MenuChrome.white(0.04f));
        Render2D.drawBorder(ms, x, y, w, h, 8f, 1.0f, 1.4f,
                MenuChrome.lerp(MenuChrome.white(0.08f), MenuChrome.white(0.24f), t));
        Render2D.drawFont(ms, f, label, x + (w - f.getWidth(label)) / 2f, y + h / 2f - f.getHeight() / 2f, TEXT2);
    }

    /** «Подключиться»: акцентный фон + тени по спеке, ховер brightness 1.07 и подъём 1px. */
    private void drawJoinButton(Matrix3x2fStack ms, float x, float y, float w, float h, float t, Instance f, String label) {
        float ty = y - t;
        Color acc = MenuChrome.accent();
        // Глоу убран по просьбе — только акцентная заливка + внутренний блик.
        int br = Math.min(255, Math.round(acc.getRed() * (1f + 0.07f * t)));
        int bg = Math.min(255, Math.round(acc.getGreen() * (1f + 0.07f * t)));
        int bb = Math.min(255, Math.round(acc.getBlue() * (1f + 0.07f * t)));
        Render2D.drawRoundedRect(ms, x, ty, w, h, 8f, new Color(br, bg, bb));
        Render2D.drawRoundedRect(ms, x + 8f, ty + 1f, w - 16f, 1f, 0.5f, MenuChrome.white(0.35f)); // inset 0 1px 0
        ClickGuiIcons.arrowRight(ms, x + 24f + 7.5f, ty + h / 2f, 18f, INK);
        Render2D.drawFont(ms, f, label, x + 24f + 15f + 11f, ty + h / 2f - f.getHeight() / 2f, INK);
    }

    // ---------- Ввод ----------

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button != 0) return super.mouseClicked(click, doubleClick);
        float mx = (float) (mouseX / uiScale), my = (float) (mouseY / uiScale);

        if (MenuChrome.in(rBack, mx, my)) { back(); return true; }
        if (showAccountPill() && MenuChrome.in(rChange, mx, my)) { openAccountSwitcher(); return true; }
        if (MenuChrome.hasMusic() && MenuChrome.in(rCover, mx, my)) { MenuChrome.toggleMusic(); return true; }

        searchFocused = MenuChrome.in(rSearch, mx, my);
        if (searchFocused) return true;

        if (rAddEmpty[2] > 0f && MenuChrome.in(rAddEmpty, mx, my)) { openAdd(); return true; }
        if (MenuChrome.in(rRefresh, mx, my)) { press("refresh"); refreshAll(); return true; }
        if (MenuChrome.in(rJoin, mx, my)) { press("join"); connectSelected(); return true; }
        if (MenuChrome.in(rAdd, mx, my)) { press("add"); openAdd(); return true; }
        if (MenuChrome.in(rDirect, mx, my)) { press("direct"); openDirect(); return true; }
        if (MenuChrome.in(rEdit, mx, my)) { press("edit"); openEdit(); return true; }
        if (MenuChrome.in(rDelete, mx, my)) { press("delete"); askDelete(); return true; }

        for (int i = 0; i < rRows.size(); i++) {
            float[] r = rRows.get(i);
            if (r[2] <= 0f) continue;
            if (MenuChrome.in(r, mx, my)) {
                long now = System.currentTimeMillis();
                boolean dbl = i == lastClickIndex && now - lastClickMs < 300L;
                selected = i;
                lastClickIndex = i;
                lastClickMs = now;
                if (dbl) connectSelected();
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = MathHelper.clamp(scrollTarget - (float) vertical * ROW_H * 0.8f,
                0f, Math.max(0f, visible.size() * ROW_H - listH()));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { back(); return true; }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); rebuildVisible(); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { searchFocused = false; return true; }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { connectSelected(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (searchFocused && chr >= ' ') {
            query += chr;
            rebuildVisible();
            return true;
        }
        return super.charTyped(input);
    }

    // ---------- Действия ----------

    private void back() {
        MenuChrome.keepMusicAcrossSwap();
        // НИКАКОГО слияния Screen/CustomMainMenuScreen — ни тернарником, ни общей локальной:
        // ProGuard не выводит общий супертип и пишет в StackMapTable java/lang/Object,
        // из-за чего класс не проходит верификацию (VerifyError при линковке).
        if (parent != null) {
            mc.setScreen(parent);
        } else {
            mc.setScreen(new CustomMainMenuScreen());
        }
    }

    private ServerInfo sel() {
        return selected >= 0 && selected < visible.size() ? visible.get(selected) : null;
    }

    protected void connectSelected() {
        ServerInfo s = sel();
        if (s == null) return;
        ConnectScreen.connect(this, mc, ServerAddress.parse(s.address), s, false, null);
    }

    protected void openEdit() {
        ServerInfo s = sel();
        if (s == null || isPinned(s)) return; // закреплённые не редактируются
        MenuChrome.keepMusicAcrossSwap();
        mc.setScreen(new ServerFormScreen(this, s, false));
    }

    protected void openAdd() {
        MenuChrome.keepMusicAcrossSwap();
        mc.setScreen(new ServerFormScreen(this, new ServerInfo("Сервер", "", ServerInfo.ServerType.OTHER), true));
    }

    protected void openDirect() {
        MenuChrome.keepMusicAcrossSwap();
        mc.setScreen(new DirectConnectFormScreen(this));
    }

    protected void askDelete() {
        ServerInfo s = sel();
        if (s == null || isPinned(s)) return; // закреплённые не удаляются
        pendingDelete = s;
        MenuChrome.keepMusicAcrossSwap();
        mc.setScreen(new ConfirmDeleteScreen(this, s));
    }

    /** Вызывается формами: сохранить новый/изменённый сервер. */
    void applyServer(ServerInfo edited, boolean isNew) {
        if (isNew) {
            serverList.add(edited, false);
        }
        serverList.saveFile();
        rebuildVisible();
        ping(edited);
    }

    /** Вызывается подтверждением удаления. */
    void deleteConfirmed() {
        if (pendingDelete != null) {
            serverList.remove(pendingDelete);
            serverList.saveFile();
            pendingDelete = null;
            selected = -1;
            rebuildVisible();
        }
    }

    /** Подключение по прямому адресу. */
    void connectDirect(String address) {
        ServerInfo info = new ServerInfo("Сервер", address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(this, mc, ServerAddress.parse(address), info, false, null);
    }

    protected void openAccountSwitcher() {
        if (mc == null || !ru.method.linkenvisuals.screen.account.IasBridge.available()) return;
        MenuChrome.keepMusicAcrossSwap();
        mc.setScreen(new ru.method.linkenvisuals.screen.account.AccountSwitcherScreen(this));
    }

    @Override
    public void removed() {
        if (serversDirty) {
            serversDirty = false;
            serverList.saveFile(); // не теряем фавиконы/MOTD, приехавшие после последнего flush
        }
        closed = true;
        cancelPings();
        // Текстуры фавиконов создаём мы — освобождаем, иначе течёт GPU-память при каждом открытии.
        for (NativeImageBackedTexture t : favicons.values()) {
            try { t.close(); } catch (Throwable ignored) { }
        }
        favicons.clear();
        faviconTried.clear();
        MenuChrome.onMenuScreenRemoved();
        super.removed();
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
}
