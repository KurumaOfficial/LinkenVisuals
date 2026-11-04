package ru.method.linkenvisuals.utils.text;

import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.managers.misc.UserSyncManager;
import ru.method.linkenvisuals.utils.debug.TabProfiler;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.ScoreTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.OrderedText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SyncedUserTextUtils {

    private static final TextColor SYNCED_NAME_COLOR = TextColor.fromRgb(0x5555FF);
    private static final String SYNCED_BADGE = "✦ ";
    private static final String REAL_SYNCED_BADGE = "✦";
    private static final Pattern USERNAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    // Префикс времени из ChatHelper.applyTimePrefix: "[HH:mm] " / "[HH:mm:ss] ". Бейдж визуалов должен
    // вставляться ПОСЛЕ него, а не перед — иначе при рендере значок уезжает левее времени.
    private static final Pattern TIME_PREFIX_PATTERN = Pattern.compile("^\\[\\d{1,2}:\\d{2}(?::\\d{2})?]\\s*");
    private static final java.util.Map<String, Pattern> NAME_PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();

    private SyncedUserTextUtils() {}

    public static String getSyncedBadge() {
        return SYNCED_BADGE.trim() + " ";
    }

    public static Text applyBadgeForPlayer(Text text, UUID uuid, String name) {
        return applyBadgeForPlayerInternal(StreamerModeUtils.sanitizeText(text), uuid, name, false);
    }

    public static Text applyBadgeForPlayerSanitized(Text text, UUID uuid, String name) {
        return applyBadgeForPlayerInternal(text, uuid, name, true);
    }

    private static Text applyBadgeForPlayerInternal(Text text, UUID uuid, String name, boolean badgeFirst) {
        long tabProfileStart = TabProfiler.startSection();
        try {
            if (text == null) return null;
            UserSyncManager syncManager = getSyncManager();
            boolean selfPlayer = isLocalPlayer(uuid, name);
            if ((syncManager == null || !syncManager.isSyncedUser(uuid, name)) && !selfPlayer) return text;
            if (containsSyncedBadge(text)) return badgeFirst ? moveSyncedBadgeBeforePlayerDonate(text) : text;

            String displayName = StreamerModeUtils.resolveTabDisplayName(name);
            if (StreamerModeUtils.shouldPreserveFriendNameColor(name)
                    || StreamerModeUtils.shouldPreserveFriendNameColor(displayName)) {
                return text;
            }
            Text highlighted = applyNameHighlightDeep(text, displayName);
            if (highlighted == null && displayName != null && name != null && !displayName.equalsIgnoreCase(name)) {
                highlighted = applyNameHighlightDeep(text, name);
            }
            if (highlighted != null) return badgeFirst ? moveSyncedBadgeBeforePlayerDonate(highlighted) : highlighted;

            Text inserted = insertBadgeForTabName(text, name, displayName);
            if (inserted != null) return badgeFirst ? moveSyncedBadgeBeforePlayerDonate(inserted) : inserted;

            if (selfPlayer || (syncManager != null && syncManager.isSyncedUser(uuid, name))) {
                Text badged = applySyncedColorShallow(text);
                return badgeFirst ? moveSyncedBadgeBeforePlayerDonate(badged) : badged;
            }
            return text;
        } catch (Throwable t) {
            // Бейдж НИКОГДА не должен ронять/вешать таб: при любой ошибке (в т.ч. StackOverflow на
            // глубоко вложенном Text от сервера) отдаём исходный текст — ник рисуется ванильным.
            return text;
        } finally {
            TabProfiler.endSection("tabBadge", tabProfileStart);
        }
    }

    public static Text applyBadgeForName(Text text) {
        return applyBadgeDeep(text);
    }

    public static Text applyBadgeDeep(Text text) {
        Text sanitized = StreamerModeUtils.sanitizeText(text);
        return applyBadgeDeepInternal(sanitized);
    }

    public static Text applyBadgeDeepRaw(Text text) {
        return applyBadgeDeepInternal(text);
    }

    public static Text applyBadgeToChatHeader(Text text) {
        if (text == null || !StreamerModeUtils.isLikelyPlayerChatMessage(text)) return text;
        UserSyncManager syncManager = getSyncManager();

        List<Segment> segments = collectSegments(text.asOrderedText());
        if (segments.isEmpty()) return text;
        String joined = joinSegments(segments);
        int separator = firstChatHeaderSeparator(joined);
        if (separator <= 0) return text;
        String header = joined.substring(0, separator);
        boolean headerHasBadge = containsSyncedBadgeString(header);
        if (headerHasBadge) {
            return moveSyncedBadgeBeforeChatDonate(text);
        }

        List<Style> styles = buildStyleMap(segments);
        Matcher matcherObj = USERNAME_TOKEN.matcher(header);
        int last = 0;
        boolean matched = false;
        MutableText out = Text.literal("");
        while (matcherObj.find()) {
            String token = header.substring(matcherObj.start(), matcherObj.end());
            // Свой ник здесь НЕ пропускаем. shouldPreserveSelfNameColor() истинно, когда стример-мод
            // включён, а имя-замена не задано — но этот цикл цвет ника вообще не меняет (токен
            // добавляется с исходными стилями), он только вставляет значок. Пропуск убирал значок
            // визуалов у собственного ника при включённом стример-моде.
            if (StreamerModeUtils.shouldPreserveFriendNameColor(token)) {
                continue;
            }
            boolean localPlayer = matchesLocalPlayerToken(token);
            boolean syncedPlayer = syncManager != null && syncManager.isSyncedName(token);
            if (!localPlayer && !syncedPlayer && !headerHasBadge) {
                continue;
            }
            // Значок вставляем перед ником, а ниже переносим его ПЕРЕД донат/клан-префиксом — как в табе.
            int badgeStart = Math.max(matcherObj.start(), timePrefixEnd(header));
            matched = true;
            appendStyledRangeWithoutSyncedBadge(out, joined, styles, last, badgeStart);
            out.append(Text.literal(getSyncedBadge()).setStyle(syncedBadgeStyle(Style.EMPTY)));
            appendStyledRangeWithoutSyncedBadge(out, joined, styles, badgeStart, matcherObj.end());
            last = matcherObj.end();
            break;
        }
        if (!matched) return text;
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, last, joined.length());
        // Единое место позиционирования: значок едет ПЕРЕД донат/клан-префиксом, как в табе
        // ([✦ Владелец] Ник вместо [Владелец] ✦ Ник). Тот же хелпер, что и для уже помеченных строк.
        return moveSyncedBadgeBeforeChatDonate(out);
    }

    public static Text moveSyncedBadgeToChatHeaderStart(Text text) {
        return moveSyncedBadgeBeforeChatDonate(text);
    }

    public static Text moveSyncedBadgeBeforeChatDonate(Text text) {
        if (text == null || !StreamerModeUtils.isLikelyPlayerChatMessage(text)) return text;
        List<Segment> segments = collectSegments(text.asOrderedText());
        if (segments.isEmpty()) return text;
        String joined = joinSegments(segments);
        int separator = firstChatHeaderSeparator(joined);
        if (separator <= 0) return text;

        String header = joined.substring(0, separator);
        if (!containsSyncedBadgeString(header)) return text;

        int nickStart = findChatNickStart(header);
        if (nickStart < 0) return text;

        List<Style> styles = buildStyleMap(segments);
        MutableText out = Text.literal("");
        // Значок визуалов — вплотную перед НИКОМ (после клан/донат-префикса), но не раньше префикса времени.
        int badgeStart = Math.max(nickStart, timePrefixEnd(header));
        if (isSyncedBadgeAlreadyBeforeDonate(header, badgeStart)) {
            return text;
        }
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, 0, badgeStart);
        out.append(Text.literal(getSyncedBadge()).setStyle(syncedBadgeStyle(Style.EMPTY)));
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, badgeStart, separator);
        appendStyledRange(out, joined, styles, separator, joined.length());
        return out;
    }

    public static OrderedText moveSyncedBadgeBeforeChatDonate(OrderedText text) {
        if (text == null) return null;
        if (!containsSyncedBadgeCodePoint(text)) return text;
        List<Segment> segments = collectSegments(text);
        if (segments.isEmpty()) return text;
        Text moved = moveSyncedBadgeBeforeChatDonate(buildText(segments));
        List<Segment> movedSegments = collectSegments(moved.asOrderedText());
        return movedSegments.isEmpty() ? text : buildOrderedText(movedSegments);
    }

    private static UserSyncManager getSyncManager() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) return null;
        return instance.getUserSyncManager();
    }

    private static Text applyBadgeDeepInternal(Text text) {
        UserSyncManager syncManager = getSyncManager();
        if (syncManager == null) return text;
        // Пересобранное дерево возвращаем ТОЛЬКО если что-то реально вставили: реконструкция
        // Score/Translatable-веток не байт-в-байт и ломает чужие стилизованные замены
        // (например донат стример-мода) — без совпадений текст должен остаться нетронутым.
        boolean[] changed = {false};
        Text out = applyBadgeDeepInternal(text, syncManager::isSyncedName, false, changed);
        return changed[0] ? out : text;
    }

    /**
     * Вставка бейджей для ТАБА (header/footer): как applyBadgeDeep, но матчит ещё и СВОЙ ник
     * (в т.ч. стримерский дисплей-ник) и не пропускает self-токены — в путях строк self
     * обрабатывается отдельной веткой, а в header-путь он иначе вообще не попадает.
     */
    public static Text applyBadgeDeepForTab(Text text) {
        Text sanitized = StreamerModeUtils.sanitizeText(text);
        UserSyncManager syncManager = getSyncManager();
        Predicate<String> match = token -> matchesLocalPlayerToken(token)
                || (syncManager != null && syncManager.isSyncedName(token));
        boolean[] changed = {false};
        Text out = applyBadgeDeepInternal(sanitized, match, true, changed);
        return changed[0] ? out : sanitized;
    }

    /** Диагностика вставки в таб: размер списка synced, найденные токены и итог. Для лога. */
    public static String tabBadgeDebug(Text before, Text after) {
        try {
            UserSyncManager sm = getSyncManager();
            String plain = before == null ? "" : before.getString();
            Matcher m = USERNAME_TOKEN.matcher(plain);
            List<String> tokens = new ArrayList<>();
            int matched = 0;
            while (m.find() && tokens.size() < 8) {
                String t = plain.substring(m.start(), m.end());
                boolean hit = matchesLocalPlayerToken(t) || (sm != null && sm.isSyncedName(t));
                if (hit) matched++;
                tokens.add(t + (hit ? "*" : ""));
            }
            boolean badgeOut = after != null && containsSyncedBadgeString(after.getString());
            return "listSize=" + (sm == null ? -1 : sm.getSyncedNameCount())
                    + " len=" + plain.length()
                    + " tokens=" + tokens
                    + " matched=" + matched
                    + " badgeInResult=" + badgeOut;
        } catch (Throwable t) {
            return "debug failed: " + t;
        }
    }

    private static Text applyBadgeDeepInternal(Text text, Predicate<String> matcher, boolean allowSelf, boolean[] changed) {
        if (text == null) return null;

        Text transformed = transformContent(text, matcher, allowSelf, changed);
        if (transformed == null) transformed = text;

        if (text.getSiblings().isEmpty()) return transformed;
        MutableText out = transformed.copy();
        out.getSiblings().clear();
        for (Text sibling : text.getSiblings()) {
            out.append(applyBadgeDeepInternal(sibling, matcher, allowSelf, changed));
        }
        return out;
    }

    private static Text transformContent(Text source, Predicate<String> matcher, boolean allowSelf, boolean[] changed) {
        if (source.getContent() instanceof PlainTextContent literal) {
            String value = literal.string();
            Text highlighted = highlightNamesInLiteral(value, source.getStyle(), matcher, allowSelf);
            if (highlighted != null) {
                changed[0] = true;
                return highlighted;
            }
            return Text.literal(value).setStyle(source.getStyle());
        }
        if (source.getContent() instanceof ScoreTextContent scoreContent) {
            String objective = scoreContent.objective();
            var nameEither = scoreContent.name();
            String nameValue = nameEither.right().orElse(null);
            if (nameValue == null) {
                Object holder = nameEither.left().orElse(null);
                nameValue = StreamerModeUtils.sanitizeString(holder == null ? null : holder.toString());
            }
            if (nameValue == null) return source;
            MutableText base = Text.score(nameValue, objective).setStyle(source.getStyle());
            if (matcher.test(nameValue)) {
                changed[0] = true;
                return applySyncedColorShallow(base);
            }
            return base;
        }
        if (source.getContent() instanceof TranslatableTextContent translatable) {
            Object[] args = getTranslatableArgs(translatable);
            Object[] updated = args == null ? new Object[0] : new Object[args.length];
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof Text textArg) {
                        updated[i] = applyBadgeDeepInternal(textArg, matcher, allowSelf, changed);
                    } else if (arg instanceof String strArg) {
                        Text highlighted = highlightNamesInLiteral(strArg, Style.EMPTY, matcher, allowSelf);
                        if (highlighted != null) changed[0] = true;
                        updated[i] = highlighted != null ? highlighted : strArg;
                    } else {
                        updated[i] = arg;
                    }
                }
            }
            String key = getTranslatableKey(translatable);
            if (key == null || key.isBlank()) {
                return source;
            }
            String fallback = getTranslatableFallback(translatable);
            MutableText out = fallback == null
                    ? Text.translatable(key, updated)
                    : Text.translatableWithFallback(key, fallback, updated);
            out.setStyle(source.getStyle());
            return out;
        }
        return source;
    }

    private static Text applySyncedColorShallow(Text text) {
        if (text == null) return null;
        return Text.literal("")
                .append(Text.literal(getSyncedBadge()).setStyle(syncedBadgeStyle(Style.EMPTY)))
                .append(text.copy());
    }

    private static boolean containsSyncedBadge(Text text) {
        if (text == null) return false;
        String value = text.getString();
        return containsSyncedBadgeString(value);
    }

    private static boolean containsSyncedBadgeString(String value) {
        if (value == null || value.isEmpty()) return false;
        return findSyncedBadgeIndex(value, 0, value.length()) >= 0;
    }

    /**
     * Быстрый пре-скан OrderedText без аллокаций: все варианты бейджа содержат '✦', так что
     * отсутствие этого кодпоинта означает «переставлять нечего» — 99% строк чата/таба
     * отваливаются одним проходом вместо collectSegments/buildText/joinSegments каждый кадр.
     */
    private static boolean containsSyncedBadgeCodePoint(OrderedText text) {
        boolean[] found = {false};
        text.accept((index, style, codePoint) -> {
            if (codePoint == '✦') {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }

    private static Text moveSyncedBadgeToFront(Text text) {
        if (text == null) return null;
        List<Segment> segments = collectSegments(text.asOrderedText());
        if (segments.isEmpty()) return text;
        String joined = joinSegments(segments);
        if (!containsSyncedBadgeString(joined)) return text;

        List<Style> styles = buildStyleMap(segments);
        MutableText out = Text.literal("");
        int start = firstNonWhitespace(joined);
        appendStyledRange(out, joined, styles, 0, start);
        out.append(Text.literal(getSyncedBadge()).setStyle(syncedBadgeStyle(Style.EMPTY)));
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, start, joined.length());
        return out;
    }

    public static Text moveSyncedBadgeBeforePlayerDonate(Text text) {
        if (text == null) return null;
        List<Segment> segments = collectSegments(text.asOrderedText());
        if (segments.isEmpty()) return text;
        String joined = joinSegments(segments);
        if (!containsSyncedBadgeString(joined)) return text;

        int nameStart = findNameStartAfterBadge(joined);
        if (nameStart < 0) {
            return text;
        }

        int donateStart = findBadgeStartBeforeDonate(joined, nameStart);
        if (donateStart < 0 || donateStart > nameStart) {
            return text;
        }
        if (isSyncedBadgeAlreadyBeforeDonate(joined, donateStart)) {
            return text;
        }
        List<Style> styles = buildStyleMap(segments);
        MutableText out = Text.literal("");
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, 0, donateStart);
        out.append(Text.literal(getSyncedBadge()).setStyle(syncedBadgeStyle(Style.EMPTY)));
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, donateStart, joined.length());
        return out;
    }

    private static Text removeSyncedBadge(Text text) {
        if (text == null) return null;
        List<Segment> segments = collectSegments(text.asOrderedText());
        if (segments.isEmpty()) return text;
        String joined = joinSegments(segments);
        if (!containsSyncedBadgeString(joined)) return text;

        List<Style> styles = buildStyleMap(segments);
        MutableText out = Text.literal("");
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, 0, joined.length());
        return out;
    }

    public static OrderedText moveSyncedBadgeBeforePlayerDonate(OrderedText text) {
        if (text == null) return null;
        if (!containsSyncedBadgeCodePoint(text)) return text;
        List<Segment> segments = collectSegments(text);
        if (segments.isEmpty()) return text;
        Text moved = moveSyncedBadgeBeforePlayerDonate(buildText(segments));
        List<Segment> movedSegments = collectSegments(moved.asOrderedText());
        return movedSegments.isEmpty() ? text : buildOrderedText(movedSegments);
    }

    private static boolean isLocalPlayer(UUID uuid, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getGameProfile() == null) {
            return false;
        }
        UUID selfUuid = client.player.getGameProfile().id();
        if (uuid != null && selfUuid != null && uuid.equals(selfUuid)) {
            return true;
        }
        String selfName = client.player.getGameProfile().name();
        return name != null && selfName != null && name.equalsIgnoreCase(selfName);
    }

    private static boolean matchesLocalPlayerToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (isLocalPlayer(null, token)) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getGameProfile() == null) {
            return false;
        }
        String profileName = client.player.getGameProfile().name();
        String displayName = StreamerModeUtils.resolveTabDisplayName(profileName);
        return !displayName.equalsIgnoreCase(profileName) && token.equalsIgnoreCase(displayName);
    }

    private static Text applySyncedColorDeep(Text text) {
        if (text == null) return null;
        MutableText out = text.copy();
        out.setStyle(applySyncedColor(out.getStyle()));
        if (!out.getSiblings().isEmpty()) {
            for (int i = 0; i < out.getSiblings().size(); i++) {
                out.getSiblings().set(i, applySyncedColorDeep(out.getSiblings().get(i)));
            }
        }
        return out;
    }

    private static Text applyNameHighlightDeep(Text text, String name) {
        if (text == null || name == null || name.isBlank()) return null;
        boolean[] matched = new boolean[] { false };
        Text out = applyNameHighlightDeepInternal(text, name, matched);
        return matched[0] ? out : null;
    }

    private static Text applyNameHighlightDeepInternal(Text text, String name, boolean[] matched) {
        if (text == null) return null;
        Text transformed = transformContentForName(text, name, matched);
        if (transformed == null) transformed = text;

        if (text.getSiblings().isEmpty()) return transformed;
        MutableText out = transformed.copy();
        out.getSiblings().clear();
        for (Text sibling : text.getSiblings()) {
            out.append(applyNameHighlightDeepInternal(sibling, name, matched));
        }
        return out;
    }

    private static Text transformContentForName(Text source, String name, boolean[] matched) {
        if (source.getContent() instanceof PlainTextContent literal) {
            String value = literal.string();
            Text highlighted = highlightNamesInLiteral(value, source.getStyle(), token -> token.equalsIgnoreCase(name));
            if (highlighted != null) {
                matched[0] = true;
                return highlighted;
            }
        }
        return source;
    }

    private static Text highlightNamesInLiteral(String value, Style baseStyle, Predicate<String> matcher) {
        return highlightNamesInLiteral(value, baseStyle, matcher, false);
    }

    private static Text highlightNamesInLiteral(String value, Style baseStyle, Predicate<String> matcher, boolean allowSelf) {
        if (value == null || value.isEmpty() || matcher == null) return null;
        Matcher matcherObj = USERNAME_TOKEN.matcher(value);
        int last = 0;
        boolean matched = false;
        Style base = baseStyle == null ? Style.EMPTY : baseStyle;
        MutableText out = Text.literal("").setStyle(base);
        while (matcherObj.find()) {
            String token = value.substring(matcherObj.start(), matcherObj.end());
            // Свой ник не пропускаем (см. пояснение в applyBadgeToChatHeader): цикл добавляет токен
            // с базовым стилем и цвет ника не трогает, поэтому пропуск только съедал значок.
            if (StreamerModeUtils.shouldPreserveFriendNameColor(token)) continue;
            if (!matcher.test(token)) continue;
            matched = true;
            int badgeStart = findHeaderBadgeStart(value, matcherObj.start());
            if (badgeStart < last || badgeStart > matcherObj.start()) {
                badgeStart = matcherObj.start();
            }
            if (badgeStart > last) {
                out.append(Text.literal(value.substring(last, badgeStart)).setStyle(base));
            }
            out.append(Text.literal(getSyncedBadge()).setStyle(syncedBadgeStyle(base)));
            if (matcherObj.start() > badgeStart) {
                out.append(Text.literal(value.substring(badgeStart, matcherObj.start())).setStyle(base));
            }
            out.append(Text.literal(token).setStyle(base));
            last = matcherObj.end();
        }
        if (!matched) return null;
        if (last < value.length()) {
            out.append(Text.literal(value.substring(last)).setStyle(base));
        }
        return out;
    }

    private static Style applySyncedColor(Style style) {
        return (style == null ? Style.EMPTY : style).withColor(SYNCED_NAME_COLOR);
    }

    // Значок визуалов (✦) рисуем ПРИНУДИТЕЛЬНО шрифтом minecraft:uniform. Серверные ресурспаки (напр.
    // ArtyGrief) полностью переопределяют default-шрифт (первым провайдером — negative_spaces.ttf, без
    // юникод-фолбэка), из-за чего глиф ✦ (U+2726) в default становится пустым. uniform ресурспак не трогает
    // (в паке нет font/uniform.json) — там ✦ есть всегда, поэтому значок отображается и под ресурспаком.
    private static final Identifier SYNCED_BADGE_FONT = Identifier.of("minecraft", "uniform");

    /** Стиль значка визуалов: цвет-акцент synced + жирный + гарантированный шрифт uniform (глиф ✦ не пропадёт). */
    private static Style syncedBadgeStyle(Style base) {
        return applySyncedColor(base).withBold(true)
                .withFont(new net.minecraft.text.StyleSpriteSource.Font(SYNCED_BADGE_FONT));
    }

    private static List<Segment> collectSegments(OrderedText text) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        Style[] current = new Style[] { null };
        text.accept((index, style, codePoint) -> {
            Style safeStyle = style == null ? Style.EMPTY : style;
            if (current[0] == null || !current[0].equals(safeStyle)) {
                if (!buffer.isEmpty()) {
                    segments.add(new Segment(current[0], buffer.toString()));
                    buffer.setLength(0);
                }
                current[0] = safeStyle;
            }
            buffer.appendCodePoint(codePoint);
            return true;
        });
        if (!buffer.isEmpty()) {
            segments.add(new Segment(current[0], buffer.toString()));
        }
        return segments;
    }

    private static String joinSegments(List<Segment> segments) {
        StringBuilder combined = new StringBuilder();
        for (Segment segment : segments) {
            combined.append(segment.text);
        }
        return combined.toString();
    }

    private static List<Style> buildStyleMap(List<Segment> segments) {
        List<Style> styles = new ArrayList<>();
        for (Segment segment : segments) {
            Style style = segment.style == null ? Style.EMPTY : segment.style;
            for (int i = 0; i < segment.text.length(); i++) {
                styles.add(style);
            }
        }
        return styles;
    }

    private static Style styleAt(List<Style> styles, int index) {
        if (styles.isEmpty() || index < 0) return Style.EMPTY;
        if (index >= styles.size()) return styles.getLast();
        return styles.get(index) == null ? Style.EMPTY : styles.get(index);
    }

    private static void appendStyledRange(MutableText out, String source, List<Style> styles, int start, int end) {
        if (out == null || source == null || start >= end) return;
        int index = Math.max(0, start);
        int safeEnd = Math.min(end, source.length());
        while (index < safeEnd) {
            Style style = styleAt(styles, index);
            int next = index + 1;
            while (next < safeEnd && styleAt(styles, next).equals(style)) {
                next++;
            }
            out.append(Text.literal(source.substring(index, next)).setStyle(style));
            index = next;
        }
    }

    private static void appendStyledRangeWithoutSyncedBadge(MutableText out, String source, List<Style> styles, int start, int end) {
        if (out == null || source == null || start >= end) return;

        int cursor = Math.max(0, start);
        int safeEnd = Math.min(end, source.length());
        while (cursor < safeEnd) {
            int badgeIndex = findSyncedBadgeIndex(source, cursor, safeEnd);
            if (badgeIndex < 0 || badgeIndex >= safeEnd) {
                appendStyledRange(out, source, styles, cursor, safeEnd);
                return;
            }
            appendStyledRange(out, source, styles, cursor, badgeIndex);
            cursor = badgeIndex + syncedBadgeLengthAt(source, badgeIndex);
            while (cursor < safeEnd && Character.isWhitespace(source.charAt(cursor))) {
                cursor++;
            }
        }
    }

    /** Длина ведущего префикса времени "[HH:mm(:ss)] " (или 0, если его нет) — бейдж ставим не раньше него. */
    private static int timePrefixEnd(String value) {
        if (value == null || value.isEmpty()) return 0;
        Matcher m = TIME_PREFIX_PATTERN.matcher(value);
        return (m.find() && m.start() == 0) ? m.end() : 0;
    }

    private static int firstChatHeaderSeparator(String value) {
        if (value == null || value.isEmpty()) return -1;
        int best = -1;
        char[] separators = new char[] {'>', '\u00BB', '\u21E8', '\u2192', '\u27A4', '\u279C'};
        for (char separator : separators) {
            int index = value.indexOf(separator);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static int findHeaderBadgeStart(String header, int nameStart) {
        if (header == null || nameStart <= 0 || nameStart > header.length()) {
            return Math.max(0, nameStart);
        }

        int cursor = nameStart;
        while (cursor > 0 && Character.isWhitespace(header.charAt(cursor - 1))) {
            cursor--;
        }
        if (cursor <= 0) {
            return Math.max(0, nameStart);
        }

        if (header.charAt(cursor - 1) == ']') {
            int bracketStart = header.lastIndexOf('[', cursor - 1);
            if (bracketStart >= 0) {
                return bracketStart;
            }
        }

        int start = cursor;
        while (start > 0 && !Character.isWhitespace(header.charAt(start - 1))) {
            start--;
        }

        while (start < cursor && Character.isWhitespace(header.charAt(start))) {
            start++;
        }

        String prefix = header.substring(start, cursor).trim();
        if (prefix.isBlank() || prefix.length() > 48) {
            return Math.max(0, nameStart);
        }
        return start;
    }

    private static int findBadgeStartBeforeDonate(String header, int nameStart) {
        if (header == null || nameStart <= 0 || nameStart > header.length()) {
            return Math.max(0, nameStart);
        }

        int direct = findDonateStartBeforeExistingBadge(header, nameStart);
        if (direct >= 0) {
            return direct;
        }

        int griefPrefix = StreamerModeUtils.resolveTabDonatePrefixStart(header, nameStart);
        if (griefPrefix >= 0 && griefPrefix < nameStart) {
            return griefPrefix;
        }

        int cursor = nameStart;
        while (cursor > 0 && Character.isWhitespace(header.charAt(cursor - 1))) {
            cursor--;
        }

        int badgeStart = findLastSyncedBadgeIndex(header, 0, cursor);
        if (badgeStart >= 0) {
            int afterBadge = badgeStart + syncedBadgeLengthAt(header, badgeStart);
            boolean onlyWhitespaceToName = true;
            for (int i = afterBadge; i < cursor; i++) {
                if (!Character.isWhitespace(header.charAt(i))) {
                    onlyWhitespaceToName = false;
                    break;
                }
            }
            if (onlyWhitespaceToName) {
                cursor = badgeStart;
                while (cursor > 0 && Character.isWhitespace(header.charAt(cursor - 1))) {
                    cursor--;
                }
            }
        }

        return findHeaderBadgeStart(header, cursor);
    }

    private static int findStrictDonateStartBeforeName(String header, int nameStart) {
        if (header == null || nameStart <= 0 || nameStart > header.length()) return -1;

        int direct = findDonateStartBeforeExistingBadge(header, nameStart);
        if (direct >= 0) {
            int badgeStart = findLastSyncedBadgeIndex(header, direct, Math.min(nameStart, header.length()));
            int donateEnd = badgeStart >= 0 ? badgeStart : nameStart;
            String donate = header.substring(direct, donateEnd).trim();
            return isStrictTabDonatePrefix(donate) ? direct : -1;
        }

        int cursor = nameStart;
        while (cursor > 0 && Character.isWhitespace(header.charAt(cursor - 1))) {
            cursor--;
        }

        int badgeStart = findLastSyncedBadgeIndex(header, 0, cursor);
        if (badgeStart >= 0) {
            int afterBadge = badgeStart + syncedBadgeLengthAt(header, badgeStart);
            boolean onlyWhitespaceToName = true;
            for (int i = afterBadge; i < cursor; i++) {
                if (!Character.isWhitespace(header.charAt(i))) {
                    onlyWhitespaceToName = false;
                    break;
                }
            }
            if (onlyWhitespaceToName) {
                cursor = badgeStart;
                while (cursor > 0 && Character.isWhitespace(header.charAt(cursor - 1))) {
                    cursor--;
                }
            }
        }

        int donateStart = findHeaderBadgeStart(header, cursor);
        if (donateStart < 0 || donateStart >= cursor) return -1;
        String donate = header.substring(donateStart, cursor).trim();
        if (!isStrictTabDonatePrefix(donate)) return -1;
        return donateStart;
    }

    private static boolean isStrictTabDonatePrefix(String donate) {
        if (donate == null) return false;
        String trimmed = donate.trim();
        if (trimmed.isBlank() || trimmed.length() > 48) return false;

        boolean hasLetterOrDigit = false;
        for (int i = 0; i < trimmed.length(); ) {
            int codePoint = trimmed.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint)) {
                hasLetterOrDigit = true;
                break;
            }
            i += Character.charCount(codePoint);
        }
        return hasLetterOrDigit || trimmed.codePointCount(0, trimmed.length()) >= 1;
    }

    private static int findDonateStartBeforeExistingBadge(String header, int nameStart) {
        if (header == null || header.isEmpty()) return -1;

        int badgeStart = findLastSyncedBadgeIndex(header, 0, Math.min(nameStart, header.length()));
        if (badgeStart < 0) return -1;

        int donateEnd = badgeStart;
        while (donateEnd > 0 && Character.isWhitespace(header.charAt(donateEnd - 1))) {
            donateEnd--;
        }
        if (donateEnd <= 0) return -1;

        if (header.charAt(donateEnd - 1) == ']') {
            int bracketStart = header.lastIndexOf('[', donateEnd - 1);
            if (bracketStart >= 0) {
                return bracketStart;
            }
        }

        int donateStart = donateEnd;
        while (donateStart > 0 && !Character.isWhitespace(header.charAt(donateStart - 1))) {
            donateStart--;
        }
        return donateStart < donateEnd ? donateStart : -1;
    }

    private static String[] syncedBadgeVariants() {
        return new String[] {
                getSyncedBadge(),
                getSyncedBadge().trim(),
                REAL_SYNCED_BADGE
        };
    }

    private static int findSyncedBadgeIndex(String value, int fromIndex, int toIndex) {
        if (value == null || value.isEmpty()) return -1;
        int safeFrom = Math.max(0, fromIndex);
        int safeTo = Math.min(Math.max(safeFrom, toIndex), value.length());
        int best = -1;
        for (String badge : syncedBadgeVariants()) {
            if (badge == null || badge.isEmpty()) continue;
            int index = value.indexOf(badge, safeFrom);
            if (index >= safeFrom && index < safeTo && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static int findLastSyncedBadgeIndex(String value, int fromIndex, int toIndex) {
        if (value == null || value.isEmpty()) return -1;
        int safeFrom = Math.max(0, fromIndex);
        int safeTo = Math.min(Math.max(safeFrom, toIndex), value.length());
        int best = -1;
        for (String badge : syncedBadgeVariants()) {
            if (badge == null || badge.isEmpty()) continue;
            int index = value.lastIndexOf(badge, Math.max(0, safeTo - 1));
            if (index >= safeFrom && index < safeTo && index > best) {
                best = index;
            }
        }
        return best;
    }

    private static int syncedBadgeLengthAt(String value, int index) {
        if (value == null || index < 0 || index >= value.length()) return 0;
        int best = 0;
        for (String badge : syncedBadgeVariants()) {
            if (badge == null || badge.isEmpty()) continue;
            if (value.startsWith(badge, index) && badge.length() > best) {
                best = badge.length();
            }
        }
        return best;
    }

    private static Text buildText(List<Segment> segments) {
        MutableText out = Text.literal("");
        for (Segment segment : segments) {
            if (segment == null || segment.text == null || segment.text.isEmpty()) continue;
            out.append(Text.literal(segment.text).setStyle(segment.style == null ? Style.EMPTY : segment.style));
        }
        return out;
    }

    private static OrderedText buildOrderedText(List<Segment> segments) {
        return buildText(segments).asOrderedText();
    }

    private static Text insertBadgeForTabName(Text text, String profileName, String displayName) {
        if (text == null) return null;
        List<Segment> segments = collectSegments(text.asOrderedText());
        if (segments.isEmpty()) return null;

        String joined = joinSegments(segments);
        int nameStart = findPlayerNameStart(joined, 0, profileName, displayName);
        if (nameStart < 0) return null;

        int donateStart = findBadgeStartBeforeDonate(joined, nameStart);
        if (donateStart < 0 || donateStart > nameStart) {
            donateStart = Math.max(0, nameStart);
        }
        if (isSyncedBadgeAlreadyBeforeDonate(joined, donateStart)) {
            return text;
        }

        List<Style> styles = buildStyleMap(segments);
        MutableText out = Text.literal("");
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, 0, donateStart);
        out.append(Text.literal(getSyncedBadge()).setStyle(syncedBadgeStyle(Style.EMPTY)));
        appendStyledRangeWithoutSyncedBadge(out, joined, styles, donateStart, joined.length());
        return out;
    }

    private static int findPlayerNameStart(String header, int fromIndex, String profileName, String displayName) {
        if (header == null || header.isEmpty()) return -1;

        int safeFrom = Math.max(0, Math.min(fromIndex, header.length()));
        for (String candidate : new String[] { displayName, profileName }) {
            if (candidate == null || candidate.isBlank()) continue;
            // Компиляция регулярки на каждый неймтег каждый кадр — дорого; кэш с грубым сбросом
            // по лимиту (как в StreamerModeUtils.compileWholeNamePattern).
            if (NAME_PATTERNS.size() > 512) NAME_PATTERNS.clear();
            Pattern pattern = NAME_PATTERNS.computeIfAbsent(candidate,
                    k -> Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(k) + "(?![\\p{L}\\p{N}_])"));
            Matcher matcher = pattern.matcher(header);
            if (matcher.find(safeFrom)) {
                return matcher.start();
            }
        }

        if (profileName != null && !profileName.isBlank()) {
            Matcher matcher = USERNAME_TOKEN.matcher(header);
            // ВАЖНО: Matcher.find(int) сбрасывает матчер и ищет с той же позиции заново. В while это
            // бесконечный цикл (тот же матч навсегда → фриз рендер-треда таба). Нужен region()+find().
            matcher.region(safeFrom, header.length());
            while (matcher.find()) {
                if (profileName.equalsIgnoreCase(matcher.group())) {
                    return matcher.start();
                }
            }
        }

        Matcher fallback = USERNAME_TOKEN.matcher(header);
        fallback.region(safeFrom, header.length()); // тот же фикс: find() без аргумента продвигается, find(safeFrom) — нет
        int preserveFallback = -1;
        while (fallback.find()) {
            String token = fallback.group();
            // Свой ник — валидная позиция для значка и при включённом стример-моде: здесь считается
            // только индекс вставки, цвет ника не меняется.
            if (StreamerModeUtils.shouldPreserveFriendNameColor(token)) {
                if (preserveFallback < 0) {
                    preserveFallback = fallback.start();
                }
                continue;
            }
            return fallback.start();
        }
        return preserveFallback;
    }

    private static int findNameStartAfterBadge(String header) {
        if (header == null || header.isEmpty()) return -1;
        int badgeIndex = findSyncedBadgeIndex(header, 0, header.length());
        int searchStart = badgeIndex < 0 ? 0 : badgeIndex + syncedBadgeLengthAt(header, badgeIndex);
        return findPlayerNameStart(header, searchStart, null, null);
    }

    /**
     * Начало РЕАЛЬНОГО ника игрока (synced-пользователь или локальный игрок) в шапке чата, либо -1.
     * В отличие от {@link #findNameStartAfterBadge} не хватает первый попавшийся токен (напр. слово
     * внутри клан-тега {@code [anarchy-1001]}) — значок визуалов должен встать именно перед ником.
     */
    private static int findChatNickStart(String header) {
        if (header == null || header.isEmpty()) return -1;
        UserSyncManager syncManager = getSyncManager();
        Matcher matcher = USERNAME_TOKEN.matcher(header);
        while (matcher.find()) {
            String token = header.substring(matcher.start(), matcher.end());
            if (matchesLocalPlayerToken(token)) return matcher.start();
            if (syncManager != null && syncManager.isSyncedName(token)) return matcher.start();
        }
        return -1;
    }

    private static boolean isSyncedBadgeAlreadyBeforeDonate(String header, int donateStart) {
        if (header == null || donateStart < 0) {
            return false;
        }
        int badgeIndex = findLastSyncedBadgeIndex(header, 0, donateStart);
        if (badgeIndex < 0) {
            return false;
        }
        int afterBadge = badgeIndex + syncedBadgeLengthAt(header, badgeIndex);
        while (afterBadge < donateStart && afterBadge < header.length()
                && Character.isWhitespace(header.charAt(afterBadge))) {
            afterBadge++;
        }
        return afterBadge == donateStart;
    }

    private static int firstNonWhitespace(String value) {
        if (value == null || value.isEmpty()) return 0;
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static final class Segment {
        private final Style style;
        private final String text;

        private Segment(Style style, String text) {
            this.style = style;
            this.text = text;
        }
    }

    // getMethod-лукап на каждый Translatable-узел таба дорог; методы фиксированы для класса —
    // ищем один раз (null = метода нет, тоже кэшируется).
    private static final java.lang.reflect.Method GET_ARGS = lookupTranslatableMethod("getArgs");
    private static final java.lang.reflect.Method GET_KEY = lookupTranslatableMethod("getKey");
    private static final java.lang.reflect.Method GET_FALLBACK = lookupTranslatableMethod("getFallback");

    private static java.lang.reflect.Method lookupTranslatableMethod(String name) {
        try {
            return TranslatableTextContent.class.getMethod(name);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object[] getTranslatableArgs(TranslatableTextContent content) {
        if (GET_ARGS == null) return null;
        try {
            return (Object[]) GET_ARGS.invoke(content);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String getTranslatableKey(TranslatableTextContent content) {
        if (GET_KEY == null) return null;
        try {
            return (String) GET_KEY.invoke(content);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String getTranslatableFallback(TranslatableTextContent content) {
        if (GET_FALLBACK == null) return null;
        try {
            return (String) GET_FALLBACK.invoke(content);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
