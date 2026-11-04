package ru.method.linkenvisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import org.joml.Matrix3x2fStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.misc.Sounds;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.GuiIcons;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD-оверлей серии убийств: компактная горизонтальная карточка сверху по центру экрана —
 * иконка-тайл, надзаголовок с точками-индикаторами, заголовок уровня, счётчик ×N, вотермарка-цифра
 * и полоса таймера комбо под карточкой. Уровни (fx 0..4) наращивают эффекты.
 *
 * <p>Считаются убийства, засчитанные игроку: игроки — всегда, мобы — по настройке
 * {@code countMobs}. Стойки брони не считаются.</p>
 *
 * <p>Таймингов два и они независимы: плашка показывается {@link #DISPLAY_MS} (3 c), а серия
 * продолжается, если следующее убийство уложилось в окно продления (настройка {@code window}).
 * Поэтому серия может «жить» с уже скрытой карточкой — новое убийство вернёт её с анимацией.</p>
 *
 * <p>Вся анимация идёт по системным часам, а не по тикам. Координаты логические (scaled), поэтому
 * любой GUI scale поддерживается: он по определению умножает всю плашку целиком. Размер плашки
 * задаётся константой {@link #S}.</p>
 *
 * <p>Цвет: акцент — из активной темы клиента, нейтральные цвета зеркалятся под светлые темы
 * ({@code LinkHudStyle.isLightTheme()}). Тема читается каждый кадр, так что переключение применяется
 * мгновенно.</p>
 */
public class KillStreak extends Module {

    // === Тайминги (мс) ===
    // Два РАЗНЫХ таймера: плашка висит DISPLAY_MS, а серия продолжается всё окно продления
    // (настройка streakWindow) — то есть серия живёт дольше, чем показана карточка.
    private static final long DISPLAY_MS = 3_000L;        // сколько плашка висит на экране
    private static final long FADE_OUT_MS = 350L;
    private static final long DAMAGE_RETENTION_MS = 4_500L;
    private static final long APPEAR_POP_MS = 480L;       // появление, fx 0-1
    private static final long APPEAR_SHAKE_MS = 620L;     // появление, fx >= 2
    private static final long COUNT_POP_MS = 420L;
    private static final long SHINE_MS = 1_100L;
    private static final long SHINE_DELAY_MS = 250L;
    private static final long SPEED_LINE_MS = 700L;
    private static final long RING_MS = 800L;
    private static final long FLASH_MS = 700L;
    private static final long CONIC_ROT_MS = 3_000L;
    private static final long GLITCH_PERIOD_MS = 2_200L;
    private static final long GLITCH_BURST_MS = 150L;
    private static final long SHIMMER_MS = 2_000L;
    private static final long CARD_PULSE_MS = 1_600L;
    private static final long DOT_MS = 250L;

    /**
     * Масштаб макета. 1.0 = размеры спецификации в экранных px при GUI scale 1; другие GUI scale
     * умножают всю плашку целиком сами, так как рендер идёт в логических координатах.
     *
     * <p>Масштаб запекается в геометрию ПРИ ОБЪЯВЛЕНИИ, а не матрицей в рендере: SDF-примитивы
     * (скруглённые прямоугольники и обводки) считают сглаживание и толщину в СВОИХ локальных
     * координатах, поэтому {@code ms.scale()} ужимал бы честную 1px-обводку до долей пикселя и края
     * бледнели бы. Текст MSDF от этого не страдает — его сглаживание идёт через экранные производные.</p>
     *
     * <p>Пропорции сетки от масштаба не зависят: все координаты домножаются на него разом, поэтому
     * раскладка остаётся 1:1 к макету — меняется только кегль в пикселях. Крутить размер плашки
     * следует ровно этой константой.</p>
     */
    private static final float S = 0.5f;

    // === Геометрия (макет × S) ===
    private static final float TOP_FRACTION = 0.08f;
    private static final float CARD_RADIUS = 16f * S;
    private static final float PAD_Y = 12f * S;
    private static final float PAD_L = 14f * S;
    private static final float PAD_R = 22f * S;
    private static final float GAP_ICON = 16f * S;        // иконка <-> текст
    private static final float GAP_COUNTER = 14f * S;     // текст <-> счётчик
    private static final float TILE = 52f * S;
    private static final float TILE_RADIUS = 14f * S;
    private static final float ICON = 26f * S;
    private static final float OVERLINE_SIZE = 10f * S;
    private static final float OVERLINE_SPACING = 3.5f * S;
    private static final float OVERLINE_GAP = 10f * S;    // надзаголовок <-> точки
    private static final float TITLE_SIZE = 23f * S;
    private static final float MONO_SIZE = 22f * S;
    private static final float COUNTER_PAD_X = 13f * S;
    private static final float COUNTER_RADIUS = 12f * S;
    private static final float COUNTER_H = 40f * S;
    private static final float MULT_GAP = 3f * S;         // × <-> цифра
    private static final float MULT_BOX = 0.46f;          // размер «×» в долях кегля
    private static final float WATERMARK_SIZE = 74f * S;
    private static final float WATERMARK_RIGHT = 86f * S; // правый край цифры от правого края карточки

    // === Абсолютная сетка по вертикали (origin — левый верхний угол карточки) ===
    private static final float OVERLINE_TOP = 18f * S;    // верх глифов «KILL STREAK»
    private static final float DOTS_CENTER_Y = 23f * S;   // центр точек-индикаторов
    private static final float TITLE_TOP = 34f * S;       // верх глифов заголовка
    private static final float DOT_SIZE = 5f * S;
    private static final float DOT_ACTIVE_W = 14f * S;
    private static final float DOT_GAP = 4f * S;
    private static final float TIMER_H = 3f * S;
    private static final float TIMER_GAP = 7f * S;

    // === Смещения/размеры эффектов (макет × S) ===
    private static final float POP_RISE = 14f * S;        // translateY появления
    private static final float SHAKE_PX = 3f * S;
    private static final float BLUR_MAX = 5f * S;
    private static final float SPEED_LINE_LEN = 150f * S;
    private static final float SPEED_LINE_H = 2f * S;
    private static final float SHINE_HALF_W = 40f * S;    // полоса 80px
    private static final float CONIC_THICK = 2f * S;
    private static final float GLITCH_CHROMA = 2f * S;    // хроматические тени ±2px
    private static final float GLITCH_SHIFT = 1f * S;

    // === Метрики MSDF-шрифтов (em) ===
    // BuiltText рисует базовую линию на y + baselineHeight*size, где baselineHeight = lineHeight+descender.
    // CAP — верх заглавных/цифр над базовой линией (planeBounds.top). Отсюда центрируем инк по вертикали.
    private static final float UNB_BASELINE = 0.995f;
    private static final float UNB_CAP = 0.84375f;
    private static final float MONO_BASELINE = 1.025f;
    private static final float MONO_CAP = 0.802f;
    // Середина инка (от верха заглавной до низа овершута) относительно базовой линии. Центровать надо
    // по ней, а не по половине cap: у цифр есть свес ниже базовой линии, и по cap вотермарка 74px
    // вылезала за нижнюю кромку карточки.
    private static final float UNB_INK_MID = 0.375f;      // (0.84375 + (-0.09375)) / 2
    private static final float MONO_INK_MID = 0.354f;     // (0.802 + (-0.09375)) / 2
    private static final float GETWIDTH_FUDGE = 1.05f;    // множитель внутри MsdfFont.getWidth

    // MSDF «толщина» = faux-weight (в атласах нет отдельных начертаний 900).
    private static final float W700 = 0.05f;
    private static final float W900 = 0.16f;

    // === Цвета ===
    // Акцент берётся из темы, а нейтральные цвета зеркалятся под светлые темы: на белом фоне
    // тёмная карточка с почти белым заголовком выглядела инородно и не читалась.
    private static final Color ACCENT_DEFAULT = new Color(0x2B, 0xE8, 0x86);       // #2BE886

    private static final Color CARD_BG_DARK = new Color(9, 14, 11, 240);           // rgba(9,14,11,0.94)
    private static final Color CARD_BG_LIGHT = new Color(247, 251, 248, 240);      // rgba(247,251,248,0.94)
    private static final Color TITLE_DARK = new Color(0xEE, 0xFF, 0xF5);           // #eefff5
    private static final Color TITLE_LIGHT = new Color(0x0B, 0x14, 0x0F);          // #0b140f
    private static final Color OVERLINE_DARK = new Color(200, 240, 218, 128);      // rgba(200,240,218,0.5)
    private static final Color OVERLINE_LIGHT = new Color(38, 74, 58, 140);
    private static final Color DOT_IDLE_DARK = new Color(255, 255, 255, 36);       // rgba(255,255,255,0.14)
    private static final Color DOT_IDLE_LIGHT = new Color(0, 0, 0, 36);
    private static final Color TRACK_DARK = new Color(255, 255, 255, 15);          // rgba(255,255,255,0.06)
    private static final Color TRACK_LIGHT = new Color(0, 0, 0, 20);

    private static final Color GLITCH_PINK = new Color(255, 60, 120, 179);
    private static final Color GLITCH_CYAN = new Color(60, 200, 255, 179);

    private static final String[] TITLES = {
            "ДВОЙНОЕ УБИЙСТВО",
            "ТРОЙНОЕ УБИЙСТВО",
            "МЕГАКИЛЛ",
            "МОНСТРКИЛЛ",
            "БЕЗУМИЕ"
    };

    /** Сколько секунд есть на продление серии. Плашка при этом висит DISPLAY_MS — таймеры разные. */
    private final NumberSetting streakWindow = new NumberSetting("settings.killstreak.window", 10f, 2f, 60f, 1f);
    private final BooleanSetting countMobs = new BooleanSetting("settings.killstreak.count_mobs", true);
    private final BooleanSetting comboTimer = new BooleanSetting("settings.killstreak.combo_timer", true);
    private final BooleanSetting themeAccent = new BooleanSetting("settings.killstreak.theme_accent", true);
    private final BooleanSetting voice = new BooleanSetting("settings.killstreak.voice", true);
    private final NumberSetting voiceVolume = new NumberSetting("settings.killstreak.voice_volume", 130f, 0f, 200f, 5f, voice::getValue);

    /**
     * id моба -> время последнего урона от игрока.
     *
     * <p>ConcurrentHashMap обязателен: {@code EventPacket.Receive} постится из netty-треда (HEAD
     * {@code ClientConnection.channelRead0}), а {@code onTick} итерирует мапу в главном треде — на
     * обычном HashMap вставка во время обхода роняла бы клиент через CME из MinecraftClient.tick.</p>
     */
    private final Map<Integer, Long> damaged = new ConcurrentHashMap<>();

    private int streak;
    private int fx = -1;
    private int prevFx = -1;
    private long lastKillMs;
    private long appearStartMs;
    private long countPopStartMs;
    private long flashStartMs;

    public KillStreak() {
        super("KillStreak", Category.Visuals);
    }

    @Override
    public void onDisable() {
        damaged.clear();
        resetStreak();
        super.onDisable();
    }

    private void resetStreak() {
        streak = 0;
        fx = -1;
        prevFx = -1;
    }

    // ------------------------------------------------------------------ детект убийств

    @EventHandler
    private void onAttack(EventAttackEntity event) {
        if (fullNullCheck() || event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof LivingEntity living) || !isCounted(living)) return;
        // Труп остаётся кликабельным ещё ~20 тиков (пока идёт анимация смерти), а событие атаки —
        // клиентское и сервером не подтверждается. Без этой проверки повторные клики по трупу
        // переотмечали бы уже засчитанного моба, и один зомби накручивал бы серию до «БЕЗУМИЯ».
        // В пакетном пути проверка НЕ нужна: сервер не шлёт урон по мёртвой сущности, зато там она
        // рискует пропустить добивающий удар из-за гонки с синхронизацией здоровья.
        if (!living.isAlive() || living.getHealth() <= 0.0f) return;
        damaged.put(living.getId(), System.currentTimeMillis());
    }

    @EventHandler
    private void onPacket(EventPacket.Receive event) {
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof EntityDamageS2CPacket dmg) || fullNullCheck()) return;

        int targetId = dmg.entityId();
        int playerId = mc.player.getId();
        if (targetId == playerId) return;

        boolean byPlayer = dmg.sourceCauseId() == playerId || dmg.sourceDirectId() == playerId;
        if (!byPlayer) {
            Entity direct = mc.world.getEntityById(dmg.sourceDirectId());
            if (!(direct instanceof ProjectileEntity proj) || !isOwnProjectile(proj)) return;
        }
        Entity target = mc.world.getEntityById(targetId);
        if (target instanceof LivingEntity living && isCounted(living)) {
            damaged.put(targetId, System.currentTimeMillis());
        }
    }

    /**
     * Кого считаем в серию: игроков — всегда, мобов — по настройке {@code countMobs}. Стойки брони
     * (единственный ванильный LivingEntity, который не моб и не игрок) не считаются никогда.
     * Отрицательный id — клиентские сущности-иллюзии (FakePlayer/KillEffect спавнят с id от
     * {@code Integer.MIN_VALUE}): их «смерть» — это discard на клиенте, не настоящее убийство.
     */
    private boolean isCounted(LivingEntity e) {
        if (e.getId() < 0) return false;
        if (e instanceof MobEntity) return countMobs.getValue();
        return e instanceof PlayerEntity && e != mc.player;
    }

    /** Считает ли плашка мобов — Sounds по этому решает, чьи тиры чьи. */
    public boolean countsMobs() {
        return countMobs.getValue();
    }

    private boolean isOwnProjectile(ProjectileEntity proj) {
        Entity owner = proj.getOwner();
        return owner instanceof PlayerEntity pe && pe.getUuid().equals(mc.player.getUuid());
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            damaged.clear();
            resetStreak();
            return;
        }
        long now = System.currentTimeMillis();

        // Серия живёт всё окно продления, даже когда плашка уже скрылась.
        if (streak > 0 && now - lastKillMs > streakWindowMs()) resetStreak();

        Iterator<Map.Entry<Integer, Long>> it = damaged.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> e = it.next();
            if (now - e.getValue() > DAMAGE_RETENTION_MS) {
                it.remove();
                continue;
            }
            Entity ent = mc.world.getEntityById(e.getKey());
            if (ent instanceof LivingEntity living) {
                if (living.isAlive() && living.getHealth() > 0.0f) continue;
                onKill(now);
                it.remove();
            } else if (ent == null) {
                // Сущность ИСЧЕЗЛА из мира (вышел из зоны видимости, ливнул с сервера, выгрузка
                // чанка/смена измерения). Клиент получает такое же удаление (DISCARDED), как и после
                // смерти, поэтому по факту исчезновения убийство от «просто пропал» не отличить —
                // надёжный сигнал только health<=0, наблюдаемый ПОКА цель ещё в мире (ветка выше).
                // Раньше здесь стоял onKill, и любой ливнувший/ушедший игрок накручивал серию.
                // ponytail: на серверах, мгновенно удаляющих труп без синка health, такой килл
                // не засчитается — компромисс в пользу отсутствия ложных срабатываний, как просил юзер.
                it.remove();
            }
        }
    }

    private void onKill(long now) {
        // Серия продолжается, только если убийство уложилось в окно продления.
        if (streak > 0 && now - lastKillMs > streakWindowMs()) resetStreak();

        // Плашка висит лишь DISPLAY_MS. Если она уже скрылась, а серия ещё жива — показываем её
        // заново с анимацией появления, иначе карточка возникла бы рывком, без pop/shake.
        boolean wasHidden = streak < 2 || now - lastKillMs > DISPLAY_MS + FADE_OUT_MS;

        streak++;
        lastKillMs = now;
        countPopStartMs = now;
        if (streak < 2) return;

        int newFx = fxFor(streak);
        boolean tierUp = newFx != fx;
        if (tierUp || wasHidden) {
            // Точки анимируются только при реальной смене уровня; на повторном показе они статичны.
            prevFx = tierUp ? fx : newFx;
            fx = newFx;
            appearStartMs = now;
            if (fx >= 2) flashStartMs = now;
        }
        // Озвучиваем ТОЛЬКО реальное повышение тира: на повторном показе плашки (wasHidden) уровень
        // тот же, и переигрывать «ДВОЙНОЕ УБИЙСТВО» было бы неверно.
        if (tierUp) playStreakSound(newFx);
    }

    /**
     * Озвучка тира (Dota) — самодостаточна: играет от собственных настроек модуля, включать
     * отдельный модуль Sounds не нужно (раньше голос жил за его тумблером и «терялся»).
     */
    private void playStreakSound(int fx) {
        if (!voice.getValue()) return;
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getSoundManager() == null || mc.getSoundManager() == null) return;
        SoundEvent event = instance.getSoundManager().getAssistSound(Sounds.streakCue(fx));
        if (event == null) return;
        float vol = Math.max(0f, voiceVolume.getValue() / 100f);
        if (vol <= 0f) return;
        mc.getSoundManager().play(PositionedSoundInstance.ui(event, 1.0f, vol));
    }

    /** Озвучивает ли этот модуль тиры своей плашки — Sounds по этому флагу не дублирует голос. */
    public boolean voicesTiers() {
        return voice.getValue();
    }

    /** Окно продления серии в мс (настройка задаётся в секундах). */
    private long streakWindowMs() {
        return (long) (streakWindow.getValue() * 1000f);
    }

    private static int fxFor(int s) {
        return MathHelper.clamp(s - 2, 0, 4);
    }

    // ------------------------------------------------------------------ рендер

    @EventHandler
    private void onRender2D(EventRender2D event) {
        if (mc.player == null || streak < 2 || fx < 0) return;

        long now = System.currentTimeMillis();
        long comboAge = now - lastKillMs;
        if (comboAge > DISPLAY_MS + FADE_OUT_MS) return;

        float alpha = comboAge <= DISPLAY_MS
                ? 1f
                : 1f - clamp01((comboAge - DISPLAY_MS) / (float) FADE_OUT_MS);
        if (alpha <= 0.004f) return;

        DrawContext ctx = event.getContext();
        var ms = ctx.getMatrices();
        Color accent = accent();
        // Тема читается каждый кадр — смена темы в рантайме перекрашивает плашку сразу.
        boolean light = LinkHudStyle.isLightTheme();
        Color cardBg = light ? CARD_BG_LIGHT : CARD_BG_DARK;
        Color titleColor = light ? TITLE_LIGHT : TITLE_DARK;
        Color overlineColor = light ? OVERLINE_LIGHT : OVERLINE_DARK;
        Color dotIdle = light ? DOT_IDLE_LIGHT : DOT_IDLE_DARK;
        Color trackColor = light ? TRACK_LIGHT : TRACK_DARK;

        // --- метрики контента ---
        String title = TITLES[fx];
        String digits = Integer.toString(streak);
        String mark = digits;
        float titleW = measure(Fonts.UNBOUNDED, title, TITLE_SIZE, titleWeight(), 0f);
        float overW = measure(Fonts.UNBOUNDED, "KILL STREAK", OVERLINE_SIZE, W700, OVERLINE_SPACING);
        float dotsW = dotsWidth(now);
        float textW = Math.max(overW + OVERLINE_GAP + dotsW, titleW);

        float multW = MONO_SIZE * MULT_BOX;
        float digitsW = measure(Fonts.PLEXMONO, digits, MONO_SIZE, W900, 0f);
        float counterTextW = multW + MULT_GAP + digitsW;
        float counterW = counterTextW + COUNTER_PAD_X * 2f;

        float cardW = PAD_L + TILE + GAP_ICON + textW + GAP_COUNTER + counterW + PAD_R;
        float cardH = TILE + PAD_Y * 2f;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float cardX = (sw - cardW) / 2f;
        float cardY = sh * TOP_FRACTION;
        float cx = cardX + cardW / 2f;
        float cy = cardY + cardH / 2f;

        // Масштаб уже запечён в геометрию; здесь только страховка от выхода за экран на крупном GUI scale.
        float fit = Math.min(1f, (sw - 8f) / cardW);

        // --- анимация появления ---
        long appearAge = now - appearStartMs;
        float scale, ty, blurPx, shakeX, shakeY, rot;
        if (fx >= 2) {
            float p = clamp01(appearAge / (float) APPEAR_SHAKE_MS);
            scale = kf3(p, 0.6f, 1.12f, 1.0f);
            float damp = 1f - p;
            shakeX = (float) Math.sin(appearAge * 0.062) * SHAKE_PX * damp;
            shakeY = (float) Math.cos(appearAge * 0.077) * SHAKE_PX * damp;
            rot = (float) Math.sin(appearAge * 0.055) * 0.6f * damp;
            ty = 0f;
            blurPx = 0f;
        } else {
            float p = clamp01(appearAge / (float) APPEAR_POP_MS);
            float eb = bezier(0.22f, 1.2f, 0.36f, 1f, p);
            scale = kf4(p, 0.7f, 1.06f, 0.98f, 1.0f);
            ty = -POP_RISE * (1f - eb);
            blurPx = BLUR_MAX * (1f - p);
            shakeX = shakeY = rot = 0f;
        }
        if (fx >= 4) {
            float t = (now % CARD_PULSE_MS) / (float) CARD_PULSE_MS;
            scale *= 1f + 0.025f * (0.5f - 0.5f * (float) Math.cos(t * Math.PI * 2.0));
        }

        // --- радиальная вспышка сверху экрана (экранные координаты, вне трансформа карточки) ---
        if (fx >= 2) {
            long flashAge = now - flashStartMs;
            if (flashAge >= 0 && flashAge < FLASH_MS) {
                float fp = clamp01(flashAge / (float) FLASH_MS);
                float peak = (fx >= 4 ? 0.16f : 0.10f) * (1f - fp) * alpha;
                drawRadialFlash(ms, sw / 2f, 0f, sw * 0.42f, accent, peak);
            }
        }

        ms.pushMatrix();
        ms.translate(cx + shakeX, cy + ty + shakeY);
        ms.scale(scale * fit, scale * fit);
        if (rot != 0f) ms.rotate((float) Math.toRadians(rot));
        ms.translate(-cx, -cy);

        float borderA = 0.3f + fx * 0.08f;
        Color borderColor = fade(accent, borderA * alpha);

        // --- скоростные линии по бокам: только всплеск на 700 мс при апгрейде (fx >= 2) ---
        // Раньше прогресс зажимался clamp01 и после 700 мс застывал на 1 — линии оставались на экране
        // навсегда и читались как сквозная горизонтальная черта за карточкой.
        if (fx >= 2 && appearAge >= 0 && appearAge < SPEED_LINE_MS) {
            float lp = appearAge / (float) SPEED_LINE_MS;
            float aOut = 1f - clamp01((appearAge - (SPEED_LINE_MS - 250L)) / 250f);
            float len = SPEED_LINE_LEN * lp;
            float ly = cy - SPEED_LINE_H / 2f;
            Color transparent = fade(accent, 0f);
            Color solid = fade(accent, 0.85f * alpha * aOut);
            Render2D.drawRoundedHorizontalGradient(ms, cardX - len, ly, len, SPEED_LINE_H, SPEED_LINE_H / 2f,
                    transparent, solid);
            Render2D.drawRoundedHorizontalGradient(ms, cardX + cardW, ly, len, SPEED_LINE_H, SPEED_LINE_H / 2f,
                    solid, transparent);
        }

        float tileX = cardX + PAD_L;
        float tileY = cardY + PAD_Y;

        // --- плоский фон карточки (без градиента; blur появления — ghost-проходы по заливке) ---
        if (blurPx > 0.3f) {
            for (int i = 0; i < 4; i++) {
                double a = i * (Math.PI / 2.0);
                Render2D.drawRoundedRect(ms, cardX + (float) (Math.cos(a) * blurPx), cardY + (float) (Math.sin(a) * blurPx),
                        cardW, cardH, CARD_RADIUS, fade(cardBg, 0.22f * alpha));
            }
        }
        Render2D.drawRoundedRect(ms, cardX, cardY, cardW, cardH, CARD_RADIUS, fade(cardBg, alpha));

        // --- вотермарка: крупная цифра серии за контентом, обрезана границами карточки ---
        // Правый край — на W-86, поэтому цифра уходит ЛЕВЕЕ пилла и лишь частично под него, а не
        // светится ровно за счётчиком. Центруем по инку, иначе свес цифры вылезал ниже карточки.
        float markW = measure(Fonts.UNBOUNDED, mark, WATERMARK_SIZE, W900, 0f);
        Render2D.startMatrixScissor(ctx, cardX, cardY, cardW, cardH, 0);
        drawText(ms, Fonts.UNBOUNDED, mark, WATERMARK_SIZE,
                cardX + cardW - WATERMARK_RIGHT - markW,
                yForInkCenter(cy, WATERMARK_SIZE, UNB_BASELINE, UNB_INK_MID),
                fade(accent, (0.05f + fx * 0.005f) * alpha), W900, 0f);
        Render2D.stopScissor(ctx);

        // --- иконка-тайл ---
        Render2D.drawRoundedRect(ms, tileX, tileY, TILE, TILE, TILE_RADIUS,
                fade(accent, (0.05f + fx * 0.02f) * alpha));
        Render2D.drawBorder(ms, tileX, tileY, TILE, TILE, TILE_RADIUS, 0.5f, 0.5f, borderColor);
        // Иконка меча — белая маска sword.png, крашенная акцентом; центр тайла.
        GuiIcons.draw(ms, GuiIcons.SWORD, tileX + TILE / 2f, tileY + TILE / 2f, ICON, fade(accent, alpha));

        // --- пульс-кольцо вокруг иконки (fx >= 2; fx >= 3 — бесконечно) ---
        // Только ПОСЛЕ заливки: под непрозрачной на 94% карточкой было бы видно лишь край (серп).
        if (fx >= 2) {
            float rp = fx >= 3
                    ? (appearAge % RING_MS) / (float) RING_MS
                    : clamp01(appearAge / (float) RING_MS);
            if (!(fx < 3 && appearAge >= RING_MS)) {
                float rs = 0.5f + 1.6f * rp;
                float rw = TILE * rs;
                float rx = tileX + TILE / 2f - rw / 2f;
                float ry = tileY + TILE / 2f - rw / 2f;
                Render2D.drawBorder(ms, rx, ry, rw, rw, TILE_RADIUS * rs, 0.5f, 0.5f,
                        fade(accent, (1f - rp) * 0.85f * alpha));
            }
        }

        // --- текстовый блок: абсолютная сетка, левый край x=82 ---
        float textX = tileX + TILE + GAP_ICON;
        drawText(ms, Fonts.UNBOUNDED, "KILL STREAK", OVERLINE_SIZE, textX,
                yForCapTop(cardY + OVERLINE_TOP, OVERLINE_SIZE, UNB_BASELINE, UNB_CAP),
                fade(overlineColor, alpha), W700, OVERLINE_SPACING);
        drawDots(ms, textX + overW + OVERLINE_GAP, cardY + DOTS_CENTER_Y, now, accent, dotIdle, alpha);
        drawTitle(ms, title, textX, yForCapTop(cardY + TITLE_TOP, TITLE_SIZE, UNB_BASELINE, UNB_CAP),
                now, accent, titleColor, light, alpha);

        // --- счётчик ×N: высота 40, центр y=38, правый край W-22 ---
        float counterX = cardX + cardW - PAD_R - counterW;
        float counterY = cy - COUNTER_H / 2f;
        Render2D.drawRoundedRect(ms, counterX, counterY, counterW, COUNTER_H, COUNTER_RADIUS,
                fade(accent, 0.06f * alpha));
        Render2D.drawBorder(ms, counterX, counterY, counterW, COUNTER_H, COUNTER_RADIUS, 0.5f, 0.5f, borderColor);
        float countPop = 1f + 0.7f * (1f - clamp01((now - countPopStartMs) / (float) COUNT_POP_MS)); // 1.7 → 1
        float ccx = counterX + counterW / 2f;
        ms.pushMatrix();
        ms.translate(ccx, cy);
        ms.scale(countPop, countPop);
        ms.translate(-ccx, -cy);
        float tx0 = counterX + COUNTER_PAD_X;
        // «×» — в одну строку с цифрой: тот же вертикальный центр, размер в пропорции кегля.
        drawMultiplySign(ms, tx0 + multW / 2f, cy, multW, MONO_SIZE * 0.10f, fade(accent, alpha));
        drawText(ms, Fonts.PLEXMONO, digits, MONO_SIZE, tx0 + multW + MULT_GAP,
                yForInkCenter(cy, MONO_SIZE, MONO_BASELINE, MONO_INK_MID), fade(accent, alpha), W900, 0f);
        ms.popMatrix();

        // --- одиночный shine-sweep (fx >= 1) ---
        if (fx >= 1) {
            long shineAge = appearAge - SHINE_DELAY_MS;
            if (shineAge >= 0 && shineAge < SHINE_MS) {
                float sp = shineAge / (float) SHINE_MS;
                float from = cardX - SHINE_HALF_W * 2f;
                float travel = cardW + SHINE_HALF_W * 4f;
                Render2D.startMatrixScissor(ctx, cardX, cardY, cardW, cardH, 0);
                drawShineBand(ms, from + sp * travel, cardY, cardY + cardH, SHINE_HALF_W,
                        fade(accent, (0.10f + fx * 0.03f) * alpha));
                Render2D.stopScissor(ctx);
            }
        }

        // --- обводка карточки 1px ---
        Render2D.drawBorder(ms, cardX, cardY, cardW, cardH, CARD_RADIUS, 0.5f, 0.5f, borderColor);

        // --- вращающаяся неоновая рамка (fx >= 3) ---
        if (fx >= 3) {
            float rotT = (now % CONIC_ROT_MS) / (float) CONIC_ROT_MS;
            drawConicBorder(ms, cardX, cardY, cardW, cardH, CARD_RADIUS, CONIC_THICK, accent,
                    (float) (rotT * Math.PI * 2.0), alpha);
        }

        // --- полоса таймера: ровно по ширине карточки, под ней ---
        // Тает за время ПОКАЗА плашки (3 c) и опустошается ровно к её исчезновению. По окну
        // продления серии (10 c) она бы успела уйти лишь на треть и выглядела бы застывшей.
        if (comboTimer.getValue()) {
            float remain = 1f - clamp01(comboAge / (float) DISPLAY_MS);
            float by = cardY + cardH + TIMER_GAP;
            Render2D.drawRoundedRect(ms, cardX, by, cardW, TIMER_H, TIMER_H / 2f, fade(trackColor, alpha));
            float fw = cardW * remain;
            if (fw > 0.5f) {
                Render2D.drawRoundedRect(ms, cardX, by, fw, TIMER_H, Math.min(TIMER_H / 2f, fw / 2f),
                        fade(accent, alpha));
            }
        }

        ms.popMatrix();
    }

    /** Заголовок уровня: глитч (fx>=3) и переливающийся градиент (fx4). Letter-spacing обычный (0). */
    private void drawTitle(Matrix3x2fStack ms, String title, float x, float y, long now, Color accent,
                           Color titleColor, boolean light, float alpha) {
        float weight = titleWeight();

        float gx = 0f;
        if (fx >= 3) {
            long phase = now % GLITCH_PERIOD_MS;
            if (phase < GLITCH_BURST_MS) {
                int step = (int) (phase / 50L);          // steps(3)
                gx = (step % 2 == 0) ? GLITCH_SHIFT : -GLITCH_SHIFT;
                drawText(ms, Fonts.UNBOUNDED, title, TITLE_SIZE, x + gx + GLITCH_CHROMA, y,
                        fade(GLITCH_PINK, alpha), weight, 0f);
                drawText(ms, Fonts.UNBOUNDED, title, TITLE_SIZE, x + gx - GLITCH_CHROMA, y,
                        fade(GLITCH_CYAN, alpha), weight, 0f);
            }
        }

        if (fx >= 4) {
            float t = (now % SHIMMER_MS) / (float) SHIMMER_MS;
            Fonts.UNBOUNDED.draw(ms, title, x + gx, y, fade(shimmer(t, accent, titleColor, light), alpha).getRGB(), TITLE_SIZE);
            return;
        }

        drawText(ms, Fonts.UNBOUNDED, title, TITLE_SIZE, x + gx, y, fade(titleColor, alpha), weight, 0f);
    }

    /** Блик рампы зеркалится под тему: на светлой белый пик был бы невидим на светлом фоне. */
    private static Color shimmer(float t, Color accent, Color titleColor, boolean light) {
        t = t - (float) Math.floor(t);
        Color peak = light ? Color.BLACK : Color.WHITE;
        Color[] stops = {titleColor, accent, peak, accent, titleColor};
        float p = t * 4f;
        int i = MathHelper.clamp((int) p, 0, 3);
        return mix(stops[i], stops[i + 1], p - i);
    }

    /** 5 точек-индикаторов уровней (×2…×6); переход 250 мс от предыдущего уровня к текущему. */
    private void drawDots(Matrix3x2fStack ms, float x, float cy, long now, Color accent, Color idle, float alpha) {
        float t = dotProgress(now);
        float cursor = x;
        for (int i = 0; i < 5; i++) {
            float w = MathHelper.lerp(t, dotW(i, prevFx), dotW(i, fx));
            Color c = mix(dotColor(i, prevFx, accent, idle), dotColor(i, fx, accent, idle), t);
            Render2D.drawRoundedRect(ms, cursor, cy - DOT_SIZE / 2f, w, DOT_SIZE, DOT_SIZE / 2f, fade(c, alpha));
            cursor += w + DOT_GAP;
        }
    }

    private float dotsWidth(long now) {
        float t = dotProgress(now);
        float total = DOT_GAP * 4f;
        for (int i = 0; i < 5; i++) total += MathHelper.lerp(t, dotW(i, prevFx), dotW(i, fx));
        return total;
    }

    private float dotProgress(long now) {
        return clamp01((now - appearStartMs) / (float) DOT_MS);
    }

    private static float dotW(int i, int level) {
        return i == level ? DOT_ACTIVE_W : DOT_SIZE;
    }

    private static Color dotColor(int i, int level, Color accent, Color idle) {
        return i <= level ? accent : idle;
    }

    private float titleWeight() {
        return fx >= 2 ? W900 : W700;
    }

    // ------------------------------------------------------------------ примитивы

    /**
     * Типографский «×» (U+00D7). В MSDF-атласах этого глифа нет (покрытие 32-126, 183, 1024-1119) —
     * он бы молча не отрисовался, поэтому рисуем его штрихами.
     */
    private static void drawMultiplySign(Matrix3x2fStack ms, float cx, float cy, float box, float stroke, Color color) {
        ColorRGBA c = new ColorRGBA(color);
        float h = box / 2f;
        float st = Math.max(0.85f, stroke);
        Render2D.drawLine(ms, cx - h, cy - h, cx + h, cy + h, st, c);
        Render2D.drawLine(ms, cx + h, cy - h, cx - h, cy + h, st, c);
    }

    private static void drawShineBand(Matrix3x2fStack ms, float centerX, float top, float bottom, float halfW, Color color) {
        if (color.getAlpha() <= 0 || halfW <= 0f) return;
        Render2D.drawRoundedHorizontalGradient(ms, centerX - halfW, top, halfW * 2f, bottom - top, 0f,
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 0), color);
    }

    private static void drawRadialFlash(Matrix3x2fStack ms, float cx, float cy, float radius, Color color, float peak) {
        if (peak <= 0.002f || radius <= 0f) return;
        Render2D.drawCircle(ms, cx, cy, radius, fade(color, peak));
    }

    /** Вращающийся conic-градиент: обходим периметр лентой и красим по углу от центра (2 сегмента). */
    private static void drawConicBorder(Matrix3x2fStack ms, float x, float y, float w, float h, float radius,
                                        float thickness, Color accent, float rotation, float alpha) {
        Render2D.drawOutline(ms, x, y, w, h, radius, thickness, fade(accent, alpha));
    }

    private static void appendArc(java.util.List<float[]> out, float cx, float cy, float r,
                                  float fromDeg, float toDeg, int steps) {
    }

    private static void appendEdge(java.util.List<float[]> out, float x1, float y1, float x2, float y2,
                                   float nx, float ny, int steps) {
    }

    // ------------------------------------------------------------------ текст/цвет/утилиты

    private static void drawText(Object ms, Font font, String text, float size, float x, float y,
                                 Color color, float thickness, float spacing) {
        if (text == null || text.isEmpty() || color.getAlpha() <= 0) return;
        font.draw(ms, text, x, y, color.getRGB(), size);
    }

    /**
     * y для {@link #drawText}, при котором ВЕРХ заглавных/цифр окажется ровно на {@code capTopY}.
     * BuiltText рисует базовую линию на {@code y + baselineHeight*size}, а верх заглавной — на
     * {@code baseline - cap*size}.
     */
    private static float yForCapTop(float capTopY, float size, float baselineHeight, float cap) {
        return capTopY + (cap - baselineHeight) * size;
    }

    /** y, при котором ЦЕНТР инка (верх заглавной ↔ низ свеса) окажется на {@code centerY}. */
    private static float yForInkCenter(float centerY, float size, float baselineHeight, float inkMid) {
        return centerY + (inkMid - baselineHeight) * size;
    }

    /**
     * Фактическая ширина строки — карточка должна обтягивать контент вплотную.
     *
     * <p>{@code MsdfFont.getWidth} = Σ advance*size*<b>1.05</b>: этих 5% в реальной отрисовке нет
     * (перо двигается ровно на {@code advance*size + thickness*0.5*size + spacing} за глиф, см.
     * BuiltText), поэтому запас снимаем, а spacing/толщину, наоборот, добавляем — их getWidth не знает.</p>
     */
    private static float measure(Font font, String text, float size, float thickness, float spacing) {
        if (text == null || text.isEmpty()) return 0f;
        return font.getWidth(text, size) / GETWIDTH_FUDGE
                + (thickness * 0.5f * size + spacing) * Math.max(0, text.length() - 1);
    }

    private Color accent() {
        return themeAccent.getValue() ? ColorUtils.getPrimaryColor() : ACCENT_DEFAULT;
    }

    private static Color fade(Color c, float mul) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                MathHelper.clamp(Math.round(c.getAlpha() * clamp01(mul)), 0, 255));
    }

    private static Color mix(Color a, Color b, float t) {
        t = clamp01(t);
        return new Color(
                Math.round(MathHelper.lerp(t, a.getRed(), b.getRed())),
                Math.round(MathHelper.lerp(t, a.getGreen(), b.getGreen())),
                Math.round(MathHelper.lerp(t, a.getBlue(), b.getBlue())),
                Math.round(MathHelper.lerp(t, a.getAlpha(), b.getAlpha()))
        );
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float kf3(float p, float a, float b, float c) {
        if (p < 0.5f) return MathHelper.lerp(smooth(p / 0.5f), a, b);
        return MathHelper.lerp(smooth((p - 0.5f) / 0.5f), b, c);
    }

    private static float kf4(float p, float a, float b, float c, float d) {
        if (p < 0.5f) return MathHelper.lerp(smooth(p / 0.5f), a, b);
        if (p < 0.75f) return MathHelper.lerp(smooth((p - 0.5f) / 0.25f), b, c);
        return MathHelper.lerp(smooth((p - 0.75f) / 0.25f), c, d);
    }

    private static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    /** cubic-bezier(x1,y1,x2,y2) — решаем x(t)=p методом Ньютона, возвращаем y (может быть >1). */
    private static float bezier(float x1, float y1, float x2, float y2, float p) {
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        float t = p;
        for (int i = 0; i < 8; i++) {
            float x = bezierAxis(t, x1, x2) - p;
            if (Math.abs(x) < 1e-4f) break;
            float d = bezierSlope(t, x1, x2);
            if (Math.abs(d) < 1e-6f) break;
            t = clamp01(t - x / d);
        }
        return bezierAxis(t, y1, y2);
    }

    private static float bezierAxis(float t, float a1, float a2) {
        float u = 1f - t;
        return 3f * u * u * t * a1 + 3f * u * t * t * a2 + t * t * t;
    }

    private static float bezierSlope(float t, float a1, float a2) {
        float u = 1f - t;
        return 3f * u * u * a1 + 6f * u * t * (a2 - a1) + 3f * t * t * (1f - a2);
    }
}
