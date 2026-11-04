package ru.method.linkenvisuals.utils.text;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.ScoreTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.managers.misc.FriendManager;
import ru.method.linkenvisuals.modules.impl.misc.StreamerMode;
import ru.method.linkenvisuals.utils.debug.TabProfiler;
import ru.method.linkenvisuals.utils.text.streamer.StreamerModePatterns;
import ru.method.linkenvisuals.utils.text.streamer.StreamerModeState;
import ru.method.linkenvisuals.utils.text.streamer.StreamerModeState.ScoreboardServerType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StreamerModeUtils {
    private StreamerModeUtils() {}

    private static final Map<String, Pattern> WHOLE_NAME_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> DONATE_PREFIX_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> GLYPH_DONATE_PREFIX_PATTERN_CACHE = new ConcurrentHashMap<>();
    // Кэши паттернов для горячего пути санитизации (чат/скорборд/таб): раньше эти
    // регэкспы компилировались Pattern.compile на каждый вызов. Ключ включает все
    // динамические части (donate/name), поэтому при их смене паттерн пересобирается.
    private static final Map<String, Pattern> CUSTOM_DONATE_PREFIX_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> DUPLICATE_DONATE_NAME_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> DONATE_BEFORE_MENTION_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Text, TabNameCacheEntry> TAB_NAME_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile FriendNicknameCache FRIEND_NICKNAME_CACHE = new FriendNicknameCache("", -1, Collections.emptyMap());
    private static int CHAT_DONATE_DEBUG_LINES;

    public static boolean isEnabled() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return false;
        StreamerMode module = instance.getModuleManager().getModule(StreamerMode.class);
        return module != null && module.isToggle();
    }

    public static String getReplacementName() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return "";
        StreamerMode module = instance.getModuleManager().getModule(StreamerMode.class);
        if (module == null || !module.isToggle()) return "";
        String name = module.getCustomName();
        return name == null ? "" : name.trim();
    }

    public static String resolveTabDisplayName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return profileName;
        }
        String replacement = stripLegacyFormatting(resolveNameReplacement(profileName));
        return replacement.isBlank() ? profileName : replacement;
    }

    public static boolean isGriefServerType() {
        return StreamerModeState.scoreboardServerType() == ScoreboardServerType.GRIEF;
    }

    public static int resolveTabDonatePrefixStart(String header, int nameStart) {
        if (header == null || nameStart <= 0 || nameStart > header.length()) {
            return -1;
        }
        int loose = findLoosePrefixStart(header, nameStart);
        if (loose < 0 || loose >= nameStart) {
            return -1;
        }
        String slice = header.substring(loose, nameStart).trim();
        if (slice.isBlank() || isLikelyChatServicePrefix(slice)) {
            return -1;
        }
        if (isGriefServerType()
                || containsDonateGlyph(slice)
                || (slice.startsWith("[") && slice.endsWith("]"))) {
            return loose;
        }
        return -1;
    }

    public static boolean shouldHideAnarchy() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return false;
        StreamerMode module = instance.getModuleManager().getModule(StreamerMode.class);
        return module != null && module.isToggle() && module.shouldHideAnarchy();
    }

    private static String hiddenModeNumber(boolean grief) {
        return grief ? "666" : "999";
    }

    public static void beginScoreboardPass() {
        // Keep the last detected scoreboard type so replacements work even if
        // the marker line is rendered after the lines that contain donate/coins.
    }

    public static void observeScoreboardText(Text text) {
        if (text == null) {
            return;
        }
        observeScoreboardPlain(buildMapping(text.getString()).plain);
    }

    public static void observeScoreboardString(String input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        observeScoreboardPlain(buildMapping(input).plain);
    }

    public static void observeTabText(Text text) {
        if (text == null) {
            return;
        }
        observeTabPlain(buildMapping(text.getString()).plain);
    }

    public static void beginChatHudRender() {
        StreamerModeState.incrementChatHudRenderDepth();
    }

    public static void endChatHudRender() {
        StreamerModeState.decrementChatHudRenderDepth();
    }

    public static boolean shouldBypassDrawSanitization() {
        return StreamerModeState.shouldBypassDrawSanitization();
    }

    public static void beginSelfNametagRender() {
        StreamerModeState.incrementSelfNametagRenderDepth();
    }

    public static void endSelfNametagRender() {
        StreamerModeState.decrementSelfNametagRenderDepth();
    }

    public static boolean isSelfNametagRenderActive() {
        return StreamerModeState.isSelfNametagRenderActive();
    }

    public static void beginTextFieldRender() {
        StreamerModeState.incrementTextFieldRenderDepth();
    }

    public static void endTextFieldRender() {
        StreamerModeState.decrementTextFieldRenderDepth();
    }

    private static final Pattern DONATE_GUARD_SPACES = Pattern.compile("\\s+");

    /**
     * Пост-условие стример-режима: донат разрешено ТОЛЬКО ЗАМЕНЯТЬ, но никогда не ПРИСВАИВАТЬ.
     *
     * <p>Проверка не зависит от того, какая ветвь эвристики сработала, — а их десятки, и каждая
     * решает «донат ли это» по своим признакам (глиф, ресурс-пак-шрифт, слово капсом, метка «Ранг:»).
     * Любая из них ошибочно принимает обычное оформление сервера за донат и вписывает настроенный.
     * Инвариант ловит это на выходе: если убрать из результата вписанный донат и получить ровно
     * исходную строку — заменять было нечего, донат вставлен на пустое место (случай сайдбара
     * «◆ ВЛАДЕЛЕС bin», где сервер печатал «◆ bin»). При НАСТОЯЩЕЙ замене исходный токен из
     * результата исчез, строки не совпадут, и замена проходит как раньше.</p>
     *
     * <p>Откат срабатывает только когда вставка доната — ЕДИНСТВЕННОЕ отличие: если тем же проходом
     * скрыта анархия или замаскированы койны, сравнение не сойдётся и результат уцелеет.</p>
     *
     * ponytail: сравнение по плоскому тексту — одновременная подмена ника И вставка доната в одном
     * проходе не ловится; на такой связке переходить на пометку сегментов.
     */
    private static boolean donateInventedFromNothing(String before, String after) {
        if (before == null || after == null || before.equals(after)) return false;
        String donate = stripLegacyFormatting(getConfiguredCustomDonate()).trim();
        if (donate.isEmpty()) return false;
        return donateOnlyInserted(stripLegacyFormatting(before), stripLegacyFormatting(after), donate);
    }

    /**
     * Чистая часть инварианта — без обращения к модулю, поэтому проверяется тестом.
     * true, если единственное отличие {@code plainAfter} от {@code plainBefore} — вписанный
     * {@code donate}, то есть заменять было нечего.
     */
    static boolean donateOnlyInserted(String plainBefore, String plainAfter, String donate) {
        if (plainBefore == null || plainAfter == null || donate == null || donate.isEmpty()) return false;
        if (!plainAfter.contains(donate)) return false;
        if (plainBefore.contains(donate)) return false; // донат был и в оригинале — вставка не наша
        return squashSpaces(plainAfter.replace(donate, " ")).equals(squashSpaces(plainBefore));
    }

    private static String squashSpaces(String input) {
        return DONATE_GUARD_SPACES.matcher(input).replaceAll(" ").trim();
    }

    /** {@link #donateInventedFromNothing} для Text: при вставке из ниоткуда отдаём исходный текст. */
    private static Text guardInventedDonate(Text original, Text result) {
        if (original == null || result == null || original == result) return result;
        return donateInventedFromNothing(original.getString(), result.getString()) ? original : result;
    }

    public static Text sanitizeText(Text text) {
        long tabProfileStart = TabProfiler.startSection();
        try {
            if (!isEnabled() || text == null) return text;
            List<Segment> segments = collectSegments(text.asOrderedText());
            if (segments.isEmpty()) return text;
            SanitizedSegments sanitized = sanitizeSegments(segments, false);
            return sanitized.changed ? guardInventedDonate(text, buildText(sanitized.segments)) : text;
        } finally {
            TabProfiler.endSection("sanitizeText", tabProfileStart);
        }
    }

    public static Text sanitizePlayerLabel(Text text) {
        long tabProfileStart = TabProfiler.startSection();
        try {
            if (!isEnabled() || text == null) return text;
            List<Segment> segments = collectSegments(text.asOrderedText());
            if (segments.isEmpty()) return text;
            SanitizedSegments sanitized = sanitizeSegments(segments, false);
            List<Segment> current = sanitized.segments;
            boolean changed = sanitized.changed;

            if (isOwnPlayerLabel(joinSegments(current))) {
                NormalizeResult normalized = normalizeOwnPlayerLabel(current);
                current = normalized.segments;
                changed |= normalized.changed;
            }

            if (containsSyncedBadge(joinSegments(current))) {
                Text movedBadge = SyncedUserTextUtils.moveSyncedBadgeBeforePlayerDonate(buildText(current));
                if (movedBadge != null) {
                    List<Segment> movedSegments = collectSegments(movedBadge.asOrderedText());
                    if (!movedSegments.isEmpty()) {
                        current = movedSegments;
                        changed = true;
                    }
                }
            }

            return changed ? guardInventedDonate(text, buildText(current)) : text;
        } finally {
            TabProfiler.endSection("sanitizePlayerLabel", tabProfileStart);
        }
    }

    public static Text sanitizeSelfNametag(Text text) {
        if (text == null) {
            return text;
        }
        if (!isEnabled()) {
            return text;
        }
        List<Segment> segments = collectSegments(text.asOrderedText());
        if (segments.isEmpty()) {
            return sanitizePlayerLabel(text);
        }
        boolean hadSyncedBadge = containsSyncedBadge(joinSegments(segments));
        String donate = getConfiguredCustomDonate();
        if (donate.isBlank()) {
            Text sanitized = sanitizePlayerLabel(text);
            if (hadSyncedBadge && sanitized != null && !containsSyncedBadge(sanitized.getString())) {
                List<Segment> sanitizedSegments = collectSegments(sanitized.asOrderedText());
                ReplaceResult restoredBadge = restoreSyncedBadgeBeforeSelfName(sanitizedSegments);
                return restoredBadge.changed ? buildText(restoredBadge.segments) : sanitized;
            }
            return sanitized;
        }
        String donateToken = donate + " ";

        ReplaceResult donateReplacement = new ReplaceResult(segments, false);
        String donateStage = "none";
        for (String target : getSelfNametagTargets()) {
            if (target == null || target.isBlank()) continue;
            donateReplacement = replaceDonatePrefixSegments(segments, compileDonatePrefixPattern(target), donate, false, true, true, StreamerModeState.DONATE_COLOR);
            if (donateReplacement.changed) {
                donateStage = "prefix:" + target;
            }
            if (!donateReplacement.changed) {
                donateReplacement = replaceDonatePrefixSegments(segments, compileGlyphDonatePrefixPattern(target), donate, false, true, true, StreamerModeState.DONATE_COLOR);
                if (donateReplacement.changed) {
                    donateStage = "glyph:" + target;
                }
            }
            if (!donateReplacement.changed) {
                donateReplacement = replaceResourcePackDonateBeforeNameSegments(segments, target, donateToken, StreamerModeState.DONATE_COLOR, true);
                if (donateReplacement.changed) {
                    donateStage = "resource:" + target;
                }
            }
            if (donateReplacement.changed) {
                break;
            }
        }

        List<Segment> current = donateReplacement.changed ? donateReplacement.segments : segments;
        boolean changed = donateReplacement.changed;
        String replacementName = isEnabled() ? getReplacementName() : "";
        if (!replacementName.isBlank()) {
            for (String target : getSelfNames()) {
                if (target == null || target.isBlank()) continue;
                if (target.equalsIgnoreCase(replacementName)) continue;
                ReplaceResult renamed = replaceFormattedSegmentsPreservingLeadingWhitespace(current, compileWholeNamePattern(target), 0, replacementName, null);
                current = renamed.segments;
                changed |= renamed.changed;
            }
        }
        ReplaceResult collapsedDuplicate = collapseDuplicateCustomDonateAndName(current);
        current = collapsedDuplicate.segments;
        changed |= collapsedDuplicate.changed;
        if (hadSyncedBadge && !containsSyncedBadge(joinSegments(current))) {
            ReplaceResult restoredBadge = restoreSyncedBadgeBeforeSelfName(current);
            current = restoredBadge.segments;
            changed |= restoredBadge.changed;
        }
        return changed ? guardInventedDonate(text, buildText(current)) : text;
    }

    public static OrderedText sanitizeSelfNametagOrdered(OrderedText text) {
        if (text == null) {
            return text;
        }
        List<Segment> segments = collectSegments(text);
        if (segments.isEmpty()) {
            return text;
        }
        Text sanitized = sanitizeSelfNametag(buildText(segments));
        if (sanitized == null) {
            return text;
        }
        List<Segment> sanitizedSegments = collectSegments(sanitized.asOrderedText());
        return sanitizedSegments.isEmpty() ? text : buildOrderedText(sanitizedSegments);
    }

    public static Text sanitizeChatText(Text text) {
        if (!isEnabled() || text == null) return text;
        List<Segment> segments = collectSegments(text.asOrderedText());
        if (segments.isEmpty()) return text;
        String joined = joinSegments(segments);
        if (!isLikelyPlayerChatHeader(joined)) {
            return sanitizeText(text);
        }
        // Стример-режим правит ТОЛЬКО шапку чата (ник / донат-префикс / клан-тег до разделителя »).
        // Тело сообщения игрока НЕ трогаем — иначе "анархия999" и донат-скобки в самом тексте
        // сообщения подменялись (баг). Режем сегменты на шапку+тело по позиции разделителя.
        int sep = firstChatHeaderSeparator(joined);
        List<Segment> headerSegs = segments;
        List<Segment> bodySegs = null;
        if (sep > 0 && sep < joined.length()) {
            headerSegs = headSegments(segments, sep + 1);
            bodySegs = tailSegments(segments, sep + 1);
        }
        debugChatDonate("input plain='" + text.getString()
                + "' customDonate='" + getCustomDonate()
                + "' replacementName='" + getReplacementName()
                + "' selfNames=" + getSelfNames());
        boolean originalHadSelfDonatePrefix = hasSelfDonatePrefix(headerSegs);
        ReplaceResult chatDonate = replaceSelfDonateInChatHeader(headerSegs);
        SanitizedSegments sanitized = sanitizeSegments(chatDonate.segments, true);
        List<Segment> current = sanitized.segments;
        boolean changed = chatDonate.changed || sanitized.changed;
        if (!originalHadSelfDonatePrefix && !chatDonate.changed) {
            ReplaceResult strippedMentionDonate = stripCustomDonateBeforeChatMention(current);
            current = strippedMentionDonate.segments;
            changed |= strippedMentionDonate.changed;
        }
        ReplaceResult collapsedDuplicate = collapseDuplicateCustomDonateAndName(current);
        current = collapsedDuplicate.segments;
        changed |= collapsedDuplicate.changed;
        if (changed) {
            debugChatDonate("output changed=true plain='" + joinSegments(current) + "'");
        } else {
            debugChatDonate("output changed=false");
        }
        if (!changed) {
            return text;
        }
        if (bodySegs != null && !bodySegs.isEmpty()) {
            List<Segment> combined = new ArrayList<>(current.size() + bodySegs.size());
            combined.addAll(current);
            combined.addAll(bodySegs);
            return guardInventedDonate(text, buildText(combined));
        }
        return guardInventedDonate(text, buildText(current));
    }

    public static void debugChatHudMutation(Text before, Text after) {
        if (!isEnabled() || before == null || after == null) {
            return;
        }
        String customDonate = getCustomDonate();
        if (customDonate.isBlank()) {
            return;
        }
        debugChatDonate("ChatHud before='" + before.getString() + "' after='" + after.getString()
                + "' changed=" + !before.getString().equals(after.getString()));
    }

    public static boolean isLikelyPlayerChatMessage(Text text) {
        return text != null && isLikelyPlayerChatHeader(text.getString());
    }

    public static boolean isLikelyPlayerChatHeader(String plain) {
        int separator = firstChatHeaderSeparator(plain);
        if (separator <= 0 || separator >= plain.length() - 1) {
            return false;
        }
        char c = plain.charAt(separator);
        return c == '>'
                || c == '\u00BB'
                || c == '\u21E8'
                || c == '\u2192'
                || c == '\u27A4'
                || c == '\u279C';
    }

    public static Text sanitizeTabName(Text text, String entryName) {
        if (!isEnabled() || text == null || entryName == null || entryName.isBlank()) {
            return text;
        }
        observeTabText(text);
        boolean selfEntry = isSelfName(entryName);
        String replacementName = resolveNameReplacement(entryName);
        boolean shouldReplaceName = !replacementName.isBlank() && !entryName.equalsIgnoreCase(replacementName);
        if (!selfEntry && !shouldReplaceName) {
            return text;
        }

        String customDonate = selfEntry ? getActiveCustomDonate() : "";
        TabNameCacheEntry cached = TAB_NAME_CACHE.get(text);
        if (cached != null && cached.matches(entryName, customDonate, replacementName)) {
            return cached.result;
        }

        long tabProfileStart = TabProfiler.startSection();
        try {
            List<Segment> segments = collectSegments(text.asOrderedText());
            if (segments.isEmpty()) return text;

            boolean changed = false;
            String joined = joinSegments(segments);
            if (selfEntry && !customDonate.isBlank() && !hasCustomDonatePrefix(joined, entryName, customDonate)) {
                ReplaceResult tabDonate = new ReplaceResult(segments, false);
                for (String target : getDonatePrefixTargets(true)) {
                    if (target == null || target.isBlank()) continue;
                    tabDonate = replaceDonatePrefixSegments(segments, compileDonatePrefixPattern(target), customDonate, false, true, false, StreamerModeState.DONATE_COLOR);
                    if (!tabDonate.changed) {
                        tabDonate = replaceDonatePrefixSegments(segments, compileGlyphDonatePrefixPattern(target), customDonate, false, true, false, StreamerModeState.DONATE_COLOR);
                    }
                    if (!tabDonate.changed) {
                        tabDonate = replaceContextualPrefixBeforeNameSegments(segments, target, getPlainDonatePrefixToken(), StreamerModeState.DONATE_COLOR, true, false);
                    }
                    if (!tabDonate.changed) {
                        tabDonate = replaceLoosePrefixBeforeNameSegments(segments, target, getPlainDonatePrefixToken(), StreamerModeState.DONATE_COLOR, true);
                    }
                    if (tabDonate.changed) {
                        break;
                    }
                }
                segments = tabDonate.segments;
                changed = tabDonate.changed;
                if (changed) {
                    joined = joinSegments(segments);
                }
            }

            if (shouldReplaceName) {
                ReplaceResult renamed = replaceFormattedSegmentsPreservingLeadingWhitespace(
                        segments,
                        compileWholeNamePattern(entryName),
                        0,
                        replacementName,
                        null
                );
                segments = renamed.segments;
                changed |= renamed.changed;
            }
            ReplaceResult collapsedDuplicate = collapseDuplicateCustomDonateAndName(segments);
            segments = collapsedDuplicate.segments;
            changed |= collapsedDuplicate.changed;

            // Единственный эмиттер доната, который оставался без инварианта «только заменять».
            // Гейт обязан стоять ДО put: кэш иначе сохранит вписанный донат навсегда.
            Text result = changed ? guardInventedDonate(text, buildText(segments)) : text;
            TAB_NAME_CACHE.put(text, new TabNameCacheEntry(result, entryName, customDonate, replacementName));
            return result;
        } finally {
            TabProfiler.endSection("sanitizeTabName", tabProfileStart);
        }
    }

    public static Text sanitizeTextCollapsed(Text text) {
        return sanitizeText(text);
    }

    public static OrderedText sanitizeOrderedText(OrderedText text) {
        long tabProfileStart = TabProfiler.startSection();
        try {
            if (!isEnabled() || text == null) return text;
            List<Segment> segments = collectSegments(text);
            if (segments.isEmpty()) return text;
            SanitizedSegments sanitized = sanitizeSegments(segments, false);
            if (!sanitized.changed) return text;
            // Донат только заменяем, не присваиваем — см. donateInventedFromNothing.
            if (donateInventedFromNothing(joinSegments(segments), joinSegments(sanitized.segments))) return text;
            return buildOrderedText(sanitized.segments);
        } finally {
            TabProfiler.endSection("sanitizeOrderedText", tabProfileStart);
        }
    }

    public static String sanitizeString(String input) {
        long tabProfileStart = TabProfiler.startSection();
        try {
            if (!isEnabled() || input == null || input.isEmpty()) return input;
            String replacement = stripLegacyFormatting(getReplacementName());
            String result = input;
            if (!replacement.isBlank()) {
                result = replaceIgnoringFormatting(result, StreamerModePatterns.PLAYER_PLACEHOLDER_PATTERN, replacement);
            }
            result = replaceSelfDonatePrefixes(result);
            result = replaceDonateLabels(result);
            if (!replacement.isBlank()) {
                List<String> selfNames = getSelfNames();
                for (String name : selfNames) {
                    if (name == null || name.isEmpty()) continue;
                    if (name.equals(replacement)) continue;
                    Pattern namePattern = compileWholeNamePattern(name);
                    result = replaceIgnoringFormattingPreservingLeadingWhitespace(result, namePattern, replacement);
                }
            }
            Map<String, String> friendNicknames = getFriendNicknames();
            if (!friendNicknames.isEmpty()) {
                for (Map.Entry<String, String> entry : friendNicknames.entrySet()) {
                    String friendName = entry.getKey();
                    String friendNickname = entry.getValue();
                    if (friendNickname == null || friendNickname.isBlank() || friendName.equalsIgnoreCase(friendNickname)) continue;
                    result = replaceIgnoringFormattingPreservingLeadingWhitespace(
                            result,
                            compileWholeNamePattern(friendName),
                            friendNickname
                    );
                }
            }
            result = replaceCoinAmounts(result);
            if (shouldHideAnarchy()) {
                result = replaceCapturedGroupIgnoringFormatting(result, StreamerModePatterns.ANARCHY_PATTERN, 3, hiddenModeNumber(false));
                result = replaceCapturedGroupIgnoringFormatting(result, StreamerModePatterns.GRIEF_PATTERN, 3, hiddenModeNumber(true));
            }
            result = maskSensitiveCommandLine(result);
            // Донат только заменяем, не присваиваем — см. donateInventedFromNothing.
            return donateInventedFromNothing(input, result) ? input : result;
        } finally {
            TabProfiler.endSection("sanitizeString", tabProfileStart);
        }
    }

    public static String resolvePlainDisplayName(String actualName) {
        if (actualName == null || actualName.isBlank()) {
            return "";
        }
        if (!isEnabled()) {
            return stripLegacyFormatting(actualName);
        }

        String replacement = resolveNameReplacement(actualName);
        if (replacement != null && !replacement.isBlank()) {
            return stripLegacyFormatting(replacement);
        }

        return stripLegacyFormatting(sanitizeString(actualName));
    }

    public static boolean shouldPreserveSelfNameColor(String name) {
        return isEnabled()
                && isSelfName(name)
                && stripLegacyFormatting(getReplacementName()).isBlank();
    }

    public static boolean shouldPreserveFriendNameColor(String name) {
        if (!isEnabled() || name == null || name.isBlank()) {
            return false;
        }
        String stripped = stripLegacyFormatting(name).trim();
        if (stripped.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : getFriendNicknames().entrySet()) {
            String friendName = entry.getKey();
            String nickname = entry.getValue();
            if (nickname == null || nickname.isBlank() || friendName.equalsIgnoreCase(nickname)) {
                continue;
            }
            if (stripped.equalsIgnoreCase(stripLegacyFormatting(friendName))
                    || stripped.equalsIgnoreCase(stripLegacyFormatting(nickname))) {
                return true;
            }
        }
        return false;
    }

    public static String stripLegacyFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < input.length()
                    && Formatting.byCode(Character.toLowerCase(input.charAt(i + 1))) != null) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    public static String maskChatInput(String input) {
        if (!isEnabled() || input == null || input.isEmpty()) return input;
        int commandEnd = getSensitiveCommandEnd(input);
        return maskFromIndex(input, commandEnd);
    }

    public static String maskChatInputSegment(String fullInput, int startIndex, int length) {
        if (!isEnabled() || fullInput == null || fullInput.isEmpty()) {
            return null;
        }

        int commandEnd = getSensitiveCommandEnd(fullInput);
        if (commandEnd < 0) {
            return null;
        }

        String masked = maskFromIndex(fullInput, commandEnd);
        int safeStart = Math.max(0, Math.min(startIndex, masked.length()));
        int safeEnd = Math.min(masked.length(), safeStart + Math.max(0, length));
        return masked.substring(safeStart, safeEnd);
    }

    public static boolean isSensitiveCommandStart(String input) {
        if (!isEnabled() || input == null || input.isEmpty()) return false;
        return getSensitiveCommandEnd(input) > -1;
    }

    private static List<String> getSelfNames() {
        List<String> names = new ArrayList<>(2);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return names;
        if (client.player != null && client.player.getGameProfile() != null) {
            String playerName = client.player.getGameProfile().name();
            if (playerName != null && !playerName.isEmpty()) {
                names.add(playerName);
            }
        }
        if (client.getSession() != null) {
            String sessionName = client.getSession().getUsername();
            if (sessionName != null && !sessionName.isEmpty() && !names.contains(sessionName)) {
                names.add(sessionName);
            }
        }
        return names;
    }

    private static List<String> getSelfNametagTargets() {
        List<String> targets = new ArrayList<>(getSelfNames());
        String replacementName = stripLegacyFormatting(getReplacementName());
        if (replacementName != null && !replacementName.isBlank()) {
            boolean exists = false;
            for (String target : targets) {
                if (target != null && target.equalsIgnoreCase(replacementName)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                targets.add(replacementName);
            }
        }
        return targets;
    }

    private static String getCustomDonate() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return "";
        StreamerMode module = instance.getModuleManager().getModule(StreamerMode.class);
        if (module == null || !module.isToggle()) return "";
        String donate = module.getCustomDonate();
        return donate == null ? "" : donate.trim();
    }

    private static String getConfiguredCustomDonate() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return "";
        StreamerMode module = instance.getModuleManager().getModule(StreamerMode.class);
        if (module == null) return "";
        String donate = module.getCustomDonate();
        donate = donate == null ? "" : donate.trim();
        return isCustomDonateSameAsReplacementName(donate) ? "" : donate;
    }

    private static String getActiveCustomDonate() {
        String donate = getCustomDonate();
        return isCustomDonateSameAsReplacementName(donate) ? "" : donate;
    }

    private static boolean isCustomDonateSameAsReplacementName(String donate) {
        String plainDonate = stripLegacyFormatting(donate);
        String plainName = stripLegacyFormatting(getReplacementName());
        return plainDonate != null
                && plainName != null
                && !plainDonate.trim().isBlank()
                && !plainName.trim().isBlank()
                && plainDonate.trim().equalsIgnoreCase(plainName.trim());
    }

    private static String getCustomCoins() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return "";
        StreamerMode module = instance.getModuleManager().getModule(StreamerMode.class);
        if (module == null || !module.isToggle()) return "";
        String rawCoins = module.getCustomCoins();
        if (rawCoins == null || rawCoins.isBlank()) {
            return "";
        }
        if (StreamerModeState.scoreboardServerType() == ScoreboardServerType.GRIEF) {
            return formatCoinsString(rawCoins);
        }
        return formatCoinsString(rawCoins);
    }

    private static String getDisplayedDonateValue() {
        String donate = getActiveCustomDonate();
        if (donate.isBlank()) {
            return donate;
        }
        if (StreamerModeState.scoreboardServerType() == ScoreboardServerType.ANARCHY) {
            return "[" + donate + "]";
        }
        return donate;
    }

    private static String getPlainDonatePrefixToken() {
        String donate = getActiveCustomDonate();
        if (donate.isBlank()) {
            return donate;
        }
        return donate + " ";
    }

    private static String getChatDonateValue() {
        String donate = getActiveCustomDonate();
        if (donate.isBlank()) {
            return donate;
        }
        return shouldUseChatBracketedDonate() ? "[" + donate + "]" : donate;
    }

    private static String getChatDonatePrefixToken() {
        String donate = getChatDonateValue();
        if (donate.isBlank()) {
            return donate;
        }
        return donate + " ";
    }

    private static String getFriendNicknamesRaw() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return "";
        StreamerMode module = instance.getModuleManager().getModule(StreamerMode.class);
        if (module == null || !module.isToggle()) return "";
        String raw = module.getFriendNicknames();
        return raw == null ? "" : raw.trim();
    }

    private static String resolveNameReplacement(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        if (isSelfName(name)) {
            return getReplacementName();
        }
        return getFriendNickname(name);
    }

    private static String getFriendNickname(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return getFriendNicknames().getOrDefault(name.toLowerCase(Locale.ROOT), "");
    }

    private static Map<String, String> getFriendNicknames() {
        String raw = getFriendNicknamesRaw();
        LinkenVisuals instance = LinkenVisuals.getInstance();
        FriendManager friendManager = instance == null ? null : instance.getFriendManager();
        int revision = friendManager == null ? -1 : friendManager.getRevision();

        FriendNicknameCache cached = FRIEND_NICKNAME_CACHE;
        if (cached.matches(raw, revision)) {
            return cached.mappings;
        }

        Map<String, String> mappings = parseFriendNicknames(raw, friendManager);
        FriendNicknameCache updated = new FriendNicknameCache(raw, revision, mappings);
        FRIEND_NICKNAME_CACHE = updated;
        return updated.mappings;
    }

    private static String replaceSelfDonatePrefixes(String input) {
        String result = input;
        String donate = getPlainDonatePrefixToken();
        if (donate.isBlank()) {
            return result;
        }
        for (String name : getSelfNames()) {
            if (name == null || name.isBlank()) continue;
            if (isNickLabelLine(result, name)) continue;
            if (hasAnyCustomDonatePrefix(result, name)) continue;
            Pattern pattern = compileDonatePrefixPattern(name);
            String replaced = replaceDonatePrefixGroupIgnoringFormatting(result, pattern, 1, donate);
            if (replaced.equals(result) && StreamerModeState.scoreboardServerType() == ScoreboardServerType.GRIEF) {
                replaced = replaceDonatePrefixGroupIgnoringFormatting(result, compileGlyphDonatePrefixPattern(name), 1, donate);
            }
            if (replaced.equals(result) && StreamerModeState.scoreboardServerType() == ScoreboardServerType.GRIEF) {
                replaced = replaceContextualPrefixBeforeName(result, name, donate);
            }
            result = replaced;
        }
        return result;
    }

    private static String replaceDonateLabels(String input) {
        String donate = getActiveCustomDonate();
        if (donate.isBlank()) {
            return input;
        }
        return replaceRankLabels(input, donate);
    }

    private static boolean isNickLabelLine(String input, String name) {
        if (input == null || input.isBlank() || name == null || name.isBlank()) {
            return false;
        }
        Mapping mapping = buildMapping(input);
        if (mapping.plain.isBlank()) {
            return false;
        }
        Matcher nameMatcher = compileWholeNamePattern(name).matcher(mapping.plain);
        if (!nameMatcher.find()) {
            return false;
        }
        String beforeName = mapping.plain.substring(0, nameMatcher.start());
        return StreamerModePatterns.NICK_LABEL_PATTERN.matcher(beforeName).find();
    }

    private static String getCustomDonatePrefixToken() {
        String donate = getDisplayedDonateValue();
        if (donate.isBlank()) {
            return donate;
        }
        return donate + " ";
    }

    private static String replaceRankLabels(String input, String replacement) {
        if (input == null || input.isEmpty() || replacement == null || replacement.isBlank()) {
            return input;
        }
        Mapping mapping = buildMapping(input);
        if (mapping.plain.isEmpty()) {
            return input;
        }
        Matcher matcher = StreamerModePatterns.RANK_LABEL_PATTERN.matcher(mapping.plain);
        if (!matcher.find()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        int lastOrig = 0;
        do {
            int labelEnd = matcher.end(1);
            int valueEnd = matcher.end(2);
            if (labelEnd <= 0 || valueEnd <= labelEnd) {
                continue;
            }
            int origLabelEnd = mapping.origIndex.get(labelEnd - 1) + 1;
            int origValueEnd = mapping.origIndex.get(valueEnd - 1) + 1;
            out.append(input, lastOrig, origLabelEnd);
            out.append(' ').append(replacement);
            lastOrig = origValueEnd;
        } while (matcher.find());
        out.append(input.substring(lastOrig));
        return out.toString();
    }

    private static String replaceCoinAmounts(String input) {
        String coins = getCustomCoins();
        if (coins == null || coins.isBlank()) {
            return input;
        }
        Mapping mapping = buildMapping(input);
        if (mapping.plain.isEmpty()) {
            return input;
        }

        Matcher matcher = StreamerModePatterns.COIN_LABEL_PATTERN.matcher(mapping.plain);
        if (!matcher.find()) {
            return input;
        }

        String replacement = formatCoinsForString(coins);
        StringBuilder out = new StringBuilder(input.length() + 4);
        int lastOrig = 0;
        do {
            int valueStart = matcher.start(2);
            int valueEnd = matcher.end(2);
            if (valueStart < 0 || valueEnd <= valueStart) {
                continue;
            }

            int origValueStart = mapping.origIndex.get(valueStart);
            int adjustedEnd = shouldNormalizeGriefCoinsSpace() ? preserveTrailingWhitespaceEnd(mapping.plain, valueEnd) : valueEnd;
            int origValueEnd = mapping.origIndex.get(adjustedEnd - 1) + 1;
            out.append(input, lastOrig, origValueStart);
            out.append(replacement);
            if (shouldNormalizeGriefCoinsSpace()) {
                out.append(' ');
            }
            lastOrig = origValueEnd;
        } while (matcher.find());

        out.append(input.substring(lastOrig));
        return out.toString();
    }

    private static Map<String, String> parseFriendNicknames(String raw, FriendManager friendManager) {
        if (raw == null || raw.isBlank() || friendManager == null || friendManager.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> mappings = new LinkedHashMap<>();
        String nickname = raw.trim();
        if (nickname.isEmpty()) {
            return Collections.emptyMap();
        }

        for (String friendName : friendManager.getFriends()) {
            if (friendName == null || friendName.isBlank()) {
                continue;
            }
            if (friendName.equalsIgnoreCase(nickname)) {
                continue;
            }
            // Свой ник НЕ подменяем ником друга: если игрок сам попал в список друзей (добавил себя/
            // пати), иначе его собственный нейм-тег заменялся ником друга, а не другого игрока.
            if (isSelfName(friendName)) {
                continue;
            }
            mappings.putIfAbsent(friendName.toLowerCase(Locale.ROOT), nickname);
        }

        return mappings.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(mappings);
    }

    private static int getCoinsColor() {
        return StreamerModeState.scoreboardServerType() == ScoreboardServerType.GRIEF ? StreamerModeState.GRIEF_COINS_COLOR : StreamerModeState.DEFAULT_COINS_COLOR;
    }

    private static String formatCoinsForString(String coins) {
        if (coins == null || coins.isBlank()) {
            return coins;
        }
        return switch (StreamerModeState.scoreboardServerType()) {
            case GRIEF -> toMinecraftHexColor(StreamerModeState.GRIEF_COINS_COLOR) + coins;
            case ANARCHY -> "\u00A7e" + coins;
            case UNKNOWN -> coins;
        };
    }

    private static String toMinecraftHexColor(int rgb) {
        char[] hex = String.format(Locale.ROOT, "%06X", rgb).toCharArray();
        return "\u00A7x\u00A7" + hex[0] + "\u00A7" + hex[1] + "\u00A7" + hex[2] + "\u00A7" + hex[3] + "\u00A7" + hex[4] + "\u00A7" + hex[5];
    }

    private static void observeScoreboardPlain(String plain) {
        if (plain == null || plain.isBlank()) {
            return;
        }
        if (StreamerModePatterns.SCOREBOARD_ANARCHY_PATTERN.matcher(plain).find()) {
            StreamerModeState.setScoreboardServerType(ScoreboardServerType.ANARCHY);
            return;
        }
        if (StreamerModePatterns.SCOREBOARD_GRIEF_PATTERN.matcher(plain).find()) {
            StreamerModeState.setScoreboardServerType(ScoreboardServerType.GRIEF);
        }
    }

    private static void observeTabPlain(String plain) {
        if (plain == null || plain.isBlank()) {
            return;
        }
        if (StreamerModePatterns.SCOREBOARD_ANARCHY_PATTERN.matcher(plain).find() || StreamerModePatterns.ANARCHY_PATTERN.matcher(plain).find()) {
            StreamerModeState.setTabHasAnarchy(true);
            TAB_NAME_CACHE.clear();
        }
    }

    private static boolean shouldUseChatBracketedDonate() {
        return StreamerModeState.scoreboardServerType() == ScoreboardServerType.ANARCHY;
    }

    private static Pattern compileWholeNamePattern(String name) {
        return WHOLE_NAME_PATTERN_CACHE.computeIfAbsent(
                name,
                key -> Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(key) + "(?![\\p{L}\\p{N}_])")
        );
    }

    private static Pattern compileDonatePrefixPattern(String name) {
        String token = strictDonateTokenPattern();
        return DONATE_PREFIX_PATTERN_CACHE.computeIfAbsent(
                name,
                key -> Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])((?:(?:" + token + ")\\s*){1,6})" + Pattern.quote(key) + "(?![\\p{L}\\p{N}_])")
        );
    }

    private static Pattern compileGlyphDonatePrefixPattern(String name) {
        return GLYPH_DONATE_PREFIX_PATTERN_CACHE.computeIfAbsent(
                name,
                key -> Pattern.compile("(?u)(?<![\\p{L}\\p{N}_])((?:(?:[\\p{Co}\\p{So}★✦✧⚝⚜♛]{1,8})\\s*){1,6})" + Pattern.quote(key) + "(?![\\p{L}\\p{N}_])")
        );
    }

    // Паттерн «донат + имя» для hasCustomDonatePrefix. Ключ = plainDonate + ' ' + name,
    // чтобы при смене доната или имени паттерн пересобрался (разделитель   исключает коллизии).
    private static Pattern compileCustomDonatePrefixPattern(String plainDonate, String name) {
        return CUSTOM_DONATE_PREFIX_PATTERN_CACHE.computeIfAbsent(
                plainDonate + ' ' + name,
                key -> Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(plainDonate) + "\\s+" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])")
        );
    }

    // Паттерн дубликата «донат + имя» для collapseDuplicateCustomDonateAndName.
    // Ключ = customDonate + ' ' + replacementName (обе части динамические).
    private static Pattern compileDuplicateDonateNamePattern(String customDonate, String replacementName) {
        return DUPLICATE_DONATE_NAME_PATTERN_CACHE.computeIfAbsent(
                customDonate + ' ' + replacementName,
                key -> Pattern.compile(
                        "(?iu)(?<![\\p{L}\\p{N}_])(?:\\[" + Pattern.quote(customDonate) + "]|" + Pattern.quote(customDonate) + ")\\s+("
                                + Pattern.quote(replacementName) + ")(?![\\p{L}\\p{N}_])"
                )
        );
    }

    // Паттерн «донат перед упоминанием» для stripCustomDonateBeforeChatMention.
    // Ключ = donate + ' ' + replacementName (обе части динамические).
    private static Pattern compileDonateBeforeMentionPattern(String donate, String replacementName) {
        return DONATE_BEFORE_MENTION_PATTERN_CACHE.computeIfAbsent(
                donate + ' ' + replacementName,
                key -> Pattern.compile(
                        "(?iu)(?<![\\p{L}\\p{N}_])(?:\\[" + Pattern.quote(donate) + "]|" + Pattern.quote(donate) + ")\\s+"
                                + Pattern.quote(replacementName)
                                + "(?![\\p{L}\\p{N}_])"
                )
        );
    }

    private static boolean hasCustomDonatePrefix(String input, String name, String donate) {
        if (input == null || input.isEmpty() || name == null || name.isBlank() || donate == null || donate.isBlank()) {
            return false;
        }
        String plainDonate = stripLegacyFormatting(donate);
        if (plainDonate == null || plainDonate.isBlank()) {
            return false;
        }
        Pattern pattern = compileCustomDonatePrefixPattern(plainDonate, name);
        return pattern.matcher(input).find();
    }

    private static boolean hasAnyCustomDonatePrefix(String input, String name) {
        String plainDonate = getActiveCustomDonate();
        if (hasCustomDonatePrefix(input, name, plainDonate)) {
            return true;
        }
        String displayedDonate = getDisplayedDonateValue();
        return !displayedDonate.equals(plainDonate) && hasCustomDonatePrefix(input, name, displayedDonate);
    }

    private static boolean hasDisplayedCustomDonatePrefix(String input, String name) {
        return hasCustomDonatePrefix(input, name, getDisplayedDonateValue());
    }

    private static ReplaceResult collapseDuplicateCustomDonateAndName(List<Segment> input) {
        if (input == null || input.isEmpty()) {
            return new ReplaceResult(input, false);
        }

        String customDonate = stripLegacyFormatting(getActiveCustomDonate());
        String replacementName = stripLegacyFormatting(getReplacementName());
        if (customDonate == null || replacementName == null) {
            return new ReplaceResult(input, false);
        }

        customDonate = customDonate.trim();
        replacementName = replacementName.trim();
        if (customDonate.isBlank() || replacementName.isBlank() || !customDonate.equalsIgnoreCase(replacementName)) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Pattern duplicatePattern = compileDuplicateDonateNamePattern(customDonate, replacementName);
        Matcher matcher = duplicatePattern.matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        int cursor = 0;
        boolean changed = false;
        do {
            appendStyledRange(output, joined, styles, cursor, matcher.start());
            Style nameStyle = styleAt(styles, matcher.start(1));
            appendFormattedSegment(output, nameStyle, getReplacementName());
            cursor = matcher.end();
            changed = true;
        } while (matcher.find());
        appendStyledRange(output, joined, styles, cursor, joined.length());
        return new ReplaceResult(output, changed);
    }

    private static boolean isNameInChatHeader(String input, String name) {
        if (input == null || input.isBlank() || name == null || name.isBlank()) {
            return false;
        }
        Matcher matcher = compileWholeNamePattern(name).matcher(input);
        if (!matcher.find()) {
            return false;
        }
        int separator = firstChatHeaderSeparator(input);
        return separator >= 0 && matcher.start() < separator;
    }

    private static int firstChatHeaderSeparator(String input) {
        if (input == null || input.isEmpty()) {
            return -1;
        }
        int best = -1;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isAdditionalChatHeaderSeparator(c)) {
                if (best < 0 || i < best) {
                    best = i;
                }
                continue;
            }
            if (c != '>' && c != ':' && c != '»') {
                continue;
            }
            if (c == ':' && i + 1 < input.length() && Character.isDigit(input.charAt(i + 1))) {
                continue;
            }
            if (best < 0 || i < best) {
                best = i;
            }
        }
        return best;
    }

    private static boolean isAdditionalChatHeaderSeparator(char c) {
        return c == '\u00BB'
                || c == '\u21E8'
                || c == '\u2192'
                || c == '\u27A4'
                || c == '\u279C';
    }

    private static boolean hasSelfDonatePrefix(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        String joined = joinSegments(segments);
        if (joined == null || joined.isBlank()) {
            return false;
        }
        for (String name : getSelfNames()) {
            if (name == null || name.isBlank()) continue;
            if (compileDonatePrefixPattern(name).matcher(joined).find()
                    || compileGlyphDonatePrefixPattern(name).matcher(joined).find()) {
                return true;
            }
        }
        return false;
    }

    private static ReplaceResult replaceSelfDonateInChatHeader(List<Segment> input) {
        if (input == null || input.isEmpty()) {
            return new ReplaceResult(input, false);
        }
        String customDonate = getActiveCustomDonate();
        if (customDonate.isBlank()) {
            return new ReplaceResult(input, false);
        }

        List<Segment> current = input;
        boolean changed = false;
        String initial = joinSegments(current);
        debugChatDonate("header initial='" + initial + "' separator=" + firstChatHeaderSeparator(initial));
        for (String target : getDonatePrefixTargets(true)) {
            if (target == null || target.isBlank()) continue;
            String joined = joinSegments(current);
            debugChatDonate("header target='" + target + "' inHeader=" + isNameInChatHeader(joined, target));
            if (!isNameInChatHeader(joined, target)) {
                continue;
            }
            ReplaceResult result = replaceChatHeaderDonateBeforeName(current, target, customDonate);
            current = result.segments;
            changed |= result.changed;
            if (result.changed) {
                break;
            }
        }
        return new ReplaceResult(current, changed);
    }

    private static ReplaceResult replaceChatHeaderDonateBeforeName(List<Segment> input, String name, String donate) {
        if (input == null || input.isEmpty() || name == null || name.isBlank() || donate == null || donate.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        int separator = firstChatHeaderSeparator(joined);
        if (separator <= 0) {
            debugChatDonate("replace skip: no separator joined='" + joined + "'");
            return new ReplaceResult(input, false);
        }
        if (!isLikelyPlayerChatHeader(joined)) {
            debugChatDonate("replace skip: system header joined='" + joined + "'");
            return new ReplaceResult(input, false);
        }

        Matcher nameMatcher = compileWholeNamePattern(name).matcher(joined);
        if (!nameMatcher.find() || nameMatcher.start() >= separator) {
            debugChatDonate("replace skip: name not before separator name='" + name + "' separator=" + separator);
            return new ReplaceResult(input, false);
        }

        int prefixStart = findContextualPrefixStart(joined, nameMatcher.start());
        if (prefixStart < 0 || prefixStart >= nameMatcher.start() || prefixStart > separator) {
            debugChatDonate("replace skip: no prefixStart name='" + name + "' nameStart=" + nameMatcher.start() + " prefixStart=" + prefixStart);
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        String prefixSlice = joined.substring(prefixStart, nameMatcher.start()).trim();
        boolean resourcePackDonate = isResourcePackChatDonatePrefix(styles, prefixStart, nameMatcher.start(), prefixSlice);
        if (prefixSlice.isBlank()
                || isLikelyChatServicePrefix(prefixSlice)
                || containsChatHeaderSeparator(prefixSlice)
                || prefixSlice.length() > 48) {
            debugChatDonate("replace skip: prefix rejected slice='" + prefixSlice
                    + "' service=" + isLikelyChatServicePrefix(prefixSlice)
                    + " likely=" + isLikelyDonatePrefixSlice(prefixSlice)
                    + " resourcePack=" + resourcePackDonate);
            return new ReplaceResult(input, false);
        }
        // Гейт «это вообще донат?» — как в остальных четырёх заменах (см. replaceContextualPrefixBeforeName
        // и соседей). Раньше результаты isLikelyDonatePrefixSlice/resourcePackDonate вычислялись, но в
        // условие не входили — попадали только в debug-строку. Из-за этого ЛЮБОЙ текст слева от ника
        // (клан-тег, слово, тег мира) считался донат-префиксом и подменялся на customDonate: стример-мод
        // ПРИСВАИВАЛ префикс там, где заменять было нечего.
        if (!resourcePackDonate && !isLikelyDonatePrefixSlice(prefixSlice)) {
            debugChatDonate("replace skip: not a donate slice='" + prefixSlice + "'");
            return new ReplaceResult(input, false);
        }
        debugChatDonate("replace apply: name='" + name + "' slice='" + prefixSlice + "' donate='" + donate + "'");

        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, prefixStart);
        Style baseStyle = styleAt(styles, prefixStart);
        appendChatDonate(output, baseStyle, donate);
        appendStyledRange(output, joined, styles, nameMatcher.start(), joined.length());
        return new ReplaceResult(output, true);
    }

    private static boolean containsChatHeaderSeparator(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '>' || c == ':' || c == '|' || c == '\n' || c == '\r' || isAdditionalChatHeaderSeparator(c)) {
                return true;
            }
        }
        return false;
    }

    private static void appendChatDonate(List<Segment> output, Style baseStyle, String donate) {
        if (donate.isBlank()) {
            return;
        }
        Style bracketStyle = donateReplacementStyle(baseStyle, StreamerModeState.DONATE_BRACKET_COLOR, true);
        Style donateStyle = donateReplacementStyle(baseStyle, StreamerModeState.DONATE_COLOR, true);
        appendChatSyncedBadge(output);
        if (shouldUseChatBracketedDonate()) {
            appendSegment(output, bracketStyle, "[");
            appendFormattedSegment(output, donateStyle, donate);
            appendSegment(output, bracketStyle, "]");
            appendSegment(output, donateStyle, " ");
        } else {
            appendFormattedSegment(output, donateStyle, donate);
            appendSegment(output, donateStyle, " ");
        }
    }

    private static void appendChatSyncedBadge(List<Segment> output) {
        Style badgeStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x5555FF)).withBold(true)
                .withFont(new net.minecraft.text.StyleSpriteSource.Font(net.minecraft.util.Identifier.of("minecraft", "uniform")));
        appendSegment(output, badgeStyle, SyncedUserTextUtils.getSyncedBadge());
    }

    private static void debugChatDonate(String message) {
        // Отладочный лог [StreamerChatDonateDebug] убран — спамил в latest.log на каждое сообщение чата.
    }

    private static boolean isRelevantChatDonateDebug(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (message.contains("replace apply") || message.contains("changed=true")) {
            return true;
        }
        String lower = stripLegacyFormatting(message).toLowerCase(Locale.ROOT);
        for (String name : getDonatePrefixTargets(true)) {
            String plainName = stripLegacyFormatting(name);
            if (plainName != null && !plainName.isBlank() && lower.contains(plainName.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        String donate = stripLegacyFormatting(getCustomDonate());
        return donate != null && !donate.isBlank() && lower.contains(donate.toLowerCase(Locale.ROOT));
    }

    private static ReplaceResult stripCustomDonateBeforeChatMention(List<Segment> input) {
        if (input == null || input.isEmpty()) {
            return new ReplaceResult(input, false);
        }
        String customDonate = stripLegacyFormatting(getCustomDonate());
        String replacementName = stripLegacyFormatting(getReplacementName());
        if (customDonate == null || customDonate.isBlank() || replacementName == null || replacementName.isBlank()) {
            return new ReplaceResult(input, false);
        }

        List<Segment> current = input;
        boolean changed = false;
        String displayedDonate = stripLegacyFormatting(getDisplayedDonateValue());
        for (String donate : new String[] { customDonate.trim(), displayedDonate == null ? "" : displayedDonate.trim() }) {
            if (donate.isBlank()) continue;
            Pattern pattern = compileDonateBeforeMentionPattern(donate, replacementName.trim());
            ReplaceResult result = replaceFormattedSegments(current, pattern, 0, getReplacementName(), null);
            current = result.segments;
            changed |= result.changed;
        }
        return new ReplaceResult(current, changed);
    }

    private static String donateTokenPattern() {
        return "(?:\\[[^\\]\\r\\n]{1,24}]|[★✦✧⚝⚜♛]?[\\p{L}\\p{N}_+\\-]{2,24}|(?:" + StreamerModePatterns.DONATE_KEYWORDS + ")[\\p{L}\\p{N}_+\\-]*)";
    }

    private static String replaceIgnoringFormatting(String input, Pattern pattern, String replacement) {
        return replaceIgnoringFormatting(input, pattern, replacement, false);
    }

    private static String replaceIgnoringFormattingPreservingLeadingWhitespace(String input, Pattern pattern, String replacement) {
        return replaceIgnoringFormatting(input, pattern, replacement, true);
    }

    private static String replaceIgnoringFormatting(String input, Pattern pattern, String replacement, boolean preserveLeadingWhitespace) {
        if (input == null || input.isEmpty()) return input;
        Mapping mapping = buildMapping(input);
        if (mapping.plain.isEmpty()) return input;
        Matcher matcher = pattern.matcher(mapping.plain);
        if (!matcher.find()) return input;
        StringBuilder out = new StringBuilder(input.length());
        int lastOrig = 0;
        do {
            int start = matcher.start();
            int end = matcher.end();
            if (end <= start) continue;
            int adjustedStart = preserveLeadingWhitespace ? preserveLeadingWhitespaceStart(mapping.plain, start) : start;
            int origStart = mapping.origIndex.get(adjustedStart);
            int origMatchStart = mapping.origIndex.get(start);
            int origEnd = mapping.origIndex.get(end - 1) + 1;
            out.append(input, lastOrig, origStart);
            if (adjustedStart < start) {
                out.append(input, origStart, origMatchStart);
            }
            String resolved = resolveReplacement(matcher, replacement);
            out.append(resolved);
            lastOrig = origEnd;
        } while (matcher.find());
        out.append(input.substring(lastOrig));
        return out.toString();
    }

    private static String replaceCapturedGroupIgnoringFormatting(String input, Pattern pattern, int group, String replacement) {
        return replaceCapturedGroupIgnoringFormatting(input, pattern, group, replacement, false, false);
    }

    private static String replaceCapturedGroupIgnoringFormattingPreservingTrailingWhitespace(String input, Pattern pattern, int group, String replacement) {
        return replaceCapturedGroupIgnoringFormatting(input, pattern, group, replacement, false, true);
    }

    private static String replaceDonatePrefixGroupIgnoringFormatting(String input, Pattern pattern, int group, String replacement) {
        if (input == null || input.isEmpty()) return input;
        Mapping mapping = buildMapping(input);
        if (mapping.plain.isEmpty()) return input;

        Matcher matcher = pattern.matcher(mapping.plain);
        StringBuilder out = new StringBuilder(input.length());
        int lastOrig = 0;
        boolean changed = false;

        while (matcher.find()) {
            if (group > matcher.groupCount()) continue;

            int start = matcher.start(group);
            int end = matcher.end(group);
            if (start < 0 || end <= start) continue;

            if (!isLikelyDonatePrefixSlice(mapping.plain.substring(start, end))) {
                continue;
            }

            int origStart = mapping.origIndex.get(start);
            int preserveEnd = start + leadingDonateDecorationLength(mapping.plain, start, end);
            int origPreserveEnd = preserveEnd > start ? mapping.origIndex.get(preserveEnd - 1) + 1 : origStart;
            int origEnd = mapping.origIndex.get(end - 1) + 1;
            out.append(input, lastOrig, origStart);
            if (origPreserveEnd > origStart) {
                out.append(input, origStart, origPreserveEnd);
            }
            out.append(resolveReplacement(matcher, replacement));
            lastOrig = origEnd;
            changed = true;
        }

        if (!changed) return input;
        out.append(input.substring(lastOrig));
        return out.toString();
    }

    private static String replaceCapturedGroupIgnoringFormatting(String input, Pattern pattern, int group, String replacement, boolean preserveLeadingWhitespace, boolean preserveTrailingWhitespace) {
        if (input == null || input.isEmpty()) return input;
        Mapping mapping = buildMapping(input);
        if (mapping.plain.isEmpty()) return input;
        Matcher matcher = pattern.matcher(mapping.plain);
        if (!matcher.find()) return input;
        StringBuilder out = new StringBuilder(input.length());
        int lastOrig = 0;
        boolean changed = false;
        do {
            if (group > matcher.groupCount()) continue;
            int start = matcher.start(group);
            int end = matcher.end(group);
            if (start < 0 || end <= start) continue;
            int adjustedStart = preserveLeadingWhitespace ? preserveLeadingWhitespaceStart(mapping.plain, start) : start;
            int adjustedEnd = preserveTrailingWhitespace ? preserveTrailingWhitespaceEnd(mapping.plain, end) : end;
            int origStart = mapping.origIndex.get(adjustedStart);
            int origMatchStart = mapping.origIndex.get(start);
            int origMatchEnd = mapping.origIndex.get(end - 1) + 1;
            int origEnd = mapping.origIndex.get(adjustedEnd - 1) + 1;
            out.append(input, lastOrig, origStart);
            if (adjustedStart < start) {
                out.append(input, origStart, origMatchStart);
            }
            String resolved = resolveReplacement(matcher, replacement);
            if (adjustedEnd > end) {
                resolved = resolved + input.substring(origMatchEnd, origEnd);
            }
            out.append(resolved);
            lastOrig = origEnd;
            changed = true;
        } while (matcher.find());
        if (!changed) return input;
        out.append(input.substring(lastOrig));
        return out.toString();
    }

    private static String resolveReplacement(Matcher matcher, String replacement) {
        if (replacement.indexOf('$') == -1 || matcher.groupCount() < 1) {
            return replacement;
        }
        String resolved = replacement;
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            resolved = resolved.replace("$" + i, group == null ? "" : group);
        }
        return resolved;
    }

    private static Mapping buildMapping(String input) {
        StringBuilder plain = new StringBuilder(input.length());
        List<Integer> origIndex = new ArrayList<>(input.length());
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            int hexSkip = getInlineHexLength(input, i);
            if (hexSkip > 0) {
                i += hexSkip;
                continue;
            }
            if ((c == '\u00A7' || c == '&') && i + 1 < input.length()) {
                if (isFormatCode(input.charAt(i + 1))) {
                    i += 2;
                    continue;
                }
            }
            if (c == '{') {
                int tagEnd = input.indexOf('}', i);
                if (tagEnd != -1 && isBraceColorTag(input, i, tagEnd)) {
                    i = tagEnd + 1;
                    continue;
                }
            }
            if (c == '<') {
                int tagEnd = input.indexOf('>', i);
                if (tagEnd != -1 && isAngleColorTag(input, i, tagEnd)) {
                    i = tagEnd + 1;
                    continue;
                }
            }
            plain.append(c);
            origIndex.add(i);
            i++;
        }
        return new Mapping(plain.toString(), origIndex);
    }

    private static String maskSensitiveCommandLine(String input) {
        int commandEnd = getSensitiveCommandEnd(input);
        return maskFromIndex(input, commandEnd);
    }

    private static int getSensitiveCommandEnd(String input) {
        if (input == null || input.isEmpty()) return -1;
        Matcher matcher = StreamerModePatterns.SENSITIVE_COMMAND_PATTERN.matcher(input);
        if (!matcher.find()) return -1;
        int end = matcher.end();
        if (end < input.length()) {
            char next = input.charAt(end);
            if (next == ':' || next == '=') {
                end++;
            }
        }
        return end;
    }

    private static String maskFromIndex(String input, int maskStart) {
        if (input == null || input.isEmpty()) return input;
        if (maskStart < 0 || maskStart >= input.length()) return input;
        StringBuilder masked = new StringBuilder(input.length());
        masked.append(input, 0, maskStart);
        for (int i = maskStart; i < input.length(); i++) {
            char c = input.charAt(i);
            masked.append(Character.isWhitespace(c) ? c : '*');
        }
        return masked.toString();
    }

    private static int getInlineHexLength(String input, int start) {
        if (start + 7 >= input.length()) return 0;
        char marker = input.charAt(start);
        if (marker != '\u00A7' && marker != '&') return 0;
        char type = input.charAt(start + 1);
        if (type == '#') {
            return isHexRange(input, start + 2, start + 8) ? 8 : 0;
        }
        if (type == 'x' || type == 'X') {
            return isHexRange(input, start + 2, start + 8) ? 8 : 0;
        }
        return 0;
    }

    private static boolean isHexRange(String input, int start, int end) {
        if (start < 0 || end > input.length()) return false;
        for (int i = start; i < end; i++) {
            char c = input.charAt(i);
            boolean digit = (c >= '0' && c <= '9');
            boolean upper = (c >= 'A' && c <= 'F');
            boolean lower = (c >= 'a' && c <= 'f');
            if (!(digit || upper || lower)) return false;
        }
        return true;
    }

    private static boolean isFormatCode(char code) {
        return (code >= '0' && code <= '9')
                || (code >= 'a' && code <= 'f')
                || (code >= 'A' && code <= 'F')
                || code == 'x' || code == 'X'
                || code == 'k' || code == 'K'
                || code == 'l' || code == 'L'
                || code == 'm' || code == 'M'
                || code == 'n' || code == 'N'
                || code == 'o' || code == 'O'
                || code == 'r' || code == 'R';
    }

    private static boolean isAngleColorTag(String input, int start, int end) {
        if (end <= start + 2) return false;
        String tag = input.substring(start + 1, end);
        if (tag.length() == 7 && tag.charAt(0) == '#') {
            return isHex(tag, 1);
        }
        if (tag.length() == 8 && tag.charAt(0) == '/' && tag.charAt(1) == '#') {
            return isHex(tag, 2);
        }
        return false;
    }

    private static boolean isBraceColorTag(String input, int start, int end) {
        if (end <= start + 2) return false;
        String tag = input.substring(start + 1, end);
        if (tag.length() == 7 && tag.charAt(0) == '#') {
            return isHex(tag, 1);
        }
        if (tag.length() == 8 && tag.charAt(0) == '/' && tag.charAt(1) == '#') {
            return isHex(tag, 2);
        }
        return false;
    }

    private static boolean isHex(String tag, int offset) {
        for (int i = offset; i < tag.length(); i++) {
            char c = tag.charAt(i);
            boolean digit = (c >= '0' && c <= '9');
            boolean upper = (c >= 'A' && c <= 'F');
            boolean lower = (c >= 'a' && c <= 'f');
            if (!(digit || upper || lower)) return false;
        }
        return true;
    }

    private static String normalizeCoins(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String cleaned = raw.trim().replaceAll("[^0-9.,_\\s]", "");
        return cleaned.trim();
    }

    private static String formatCoinsString(String raw) {
        String normalized = normalizeCoins(raw).replaceAll("[^0-9]", "");
        if (normalized.isEmpty()) return "";
        StringBuilder out = new StringBuilder(normalized.length() + normalized.length() / 3);
        int firstGroup = normalized.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        out.append(normalized, 0, firstGroup);
        for (int i = firstGroup; i < normalized.length(); i += 3) {
            out.append(',').append(normalized, i, i + 3);
        }
        return out.toString();
    }

    private static boolean isSingleCodePoint(String value) {
        return value != null && value.codePointCount(0, value.length()) == 1;
    }

    private static boolean isSelfName(String name) {
        if (name == null || name.isBlank()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;

        if (client.player != null && client.player.getGameProfile() != null) {
            String playerName = client.player.getGameProfile().name();
            if (playerName != null && name.equalsIgnoreCase(playerName)) {
                return true;
            }
        }

        if (client.getSession() != null) {
            String sessionName = client.getSession().getUsername();
            if (sessionName != null && name.equalsIgnoreCase(sessionName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOwnPlayerLabel(String plain) {
        if (plain == null || plain.isBlank()) {
            return false;
        }

        for (String name : getSelfNames()) {
            if (name != null && !name.isBlank() && compileWholeNamePattern(name).matcher(plain).find()) {
                return true;
            }
        }

        String replacement = stripLegacyFormatting(getReplacementName());
        return replacement != null && !replacement.isBlank() && compileWholeNamePattern(replacement).matcher(plain).find();
    }

    private static NormalizeResult normalizeOwnPlayerLabel(List<Segment> input) {
        if (input.isEmpty()) {
            return new NormalizeResult(input, false);
        }

        List<Segment> output = new ArrayList<>(input.size());
        boolean changed = false;
        for (Segment segment : input) {
            Style style = segment.style == null ? Style.EMPTY : segment.style;
            if (shouldForceOwnLabelWhite(style)) {
                output.add(new Segment(style.withColor(TextColor.fromRgb(0xFFFFFF)), segment.text));
                changed = true;
            } else {
                output.add(new Segment(style, segment.text));
            }
        }
        return new NormalizeResult(changed ? output : input, changed);
    }

    private static boolean shouldForceOwnLabelWhite(Style style) {
        TextColor color = style == null ? null : style.getColor();
        if (color == null) {
            return true;
        }

        int rgb = color.getRgb() & 0xFFFFFF;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int spread = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
        return spread <= 10 && red >= 80 && red <= 210;
    }

    private static String getScoreHolderName(Object holder) {
        if (holder == null) return null;
        try {
            Object name = holder.getClass().getMethod("getNameForScoreboard").invoke(holder);
            if (name instanceof String stringName) {
                return stringName;
            }
            if (name instanceof Text textName) {
                return textName.getString();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Object name = holder.getClass().getMethod("getName").invoke(holder);
            if (name instanceof String stringName) {
                return stringName;
            }
            if (name instanceof Text textName) {
                return textName.getString();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return holder.toString();
    }

    private static MutableText sanitizeTranslatable(Text source, TranslatableTextContent content) {
        Object[] args = getTranslatableArgs(content);
        Object[] sanitized = args == null ? new Object[0] : new Object[args.length];
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Text textArg) {
                    sanitized[i] = sanitizeText(textArg);
                } else if (arg instanceof String strArg) {
                    sanitized[i] = sanitizeString(strArg);
                } else {
                    sanitized[i] = arg;
                }
            }
        }
        String key = getTranslatableKey(content);
        if (key == null || key.isBlank()) {
            return source.copy();
        }
        String fallback = getTranslatableFallback(content);
        MutableText out = fallback == null
                ? Text.translatable(key, sanitized)
                : Text.translatableWithFallback(key, fallback, sanitized);
        out.setStyle(source.getStyle());
        return out;
    }

    private static Object[] getTranslatableArgs(TranslatableTextContent content) {
        try {
            return (Object[]) content.getClass().getMethod("getArgs").invoke(content);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String getTranslatableKey(TranslatableTextContent content) {
        try {
            return (String) content.getClass().getMethod("getKey").invoke(content);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String getTranslatableFallback(TranslatableTextContent content) {
        try {
            return (String) content.getClass().getMethod("getFallback").invoke(content);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static List<Segment> collectSegments(OrderedText text) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        Style[] current = new Style[] { null };
        text.accept((index, style, codePoint) -> {
            Style safeStyle = style == null ? Style.EMPTY : style;
            if (current[0] == null || !current[0].equals(safeStyle)) {
                if (buffer.length() > 0) {
                    segments.add(new Segment(current[0], buffer.toString()));
                    buffer.setLength(0);
                }
                current[0] = safeStyle;
            }
            buffer.appendCodePoint(codePoint);
            return true;
        });
        if (buffer.length() > 0) {
            segments.add(new Segment(current[0], buffer.toString()));
        }
        return segments;
    }

    private static boolean containsSyncedBadge(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String badge = SyncedUserTextUtils.getSyncedBadge().trim();
        return !badge.isEmpty() && text.contains(badge);
    }

    private static ReplaceResult restoreSyncedBadgeBeforeSelfName(List<Segment> input) {
        if (input == null || input.isEmpty()) {
            return new ReplaceResult(input, false);
        }
        String joined = joinSegments(input);
        if (joined == null || joined.isEmpty() || containsSyncedBadge(joined)) {
            return new ReplaceResult(input, false);
        }

        List<String> targets = new ArrayList<>();
        String replacementName = stripLegacyFormatting(getReplacementName());
        if (replacementName != null && !replacementName.isBlank()) {
            targets.add(replacementName.trim());
        }
        for (String name : getSelfNames()) {
            String stripped = stripLegacyFormatting(name);
            if (stripped == null || stripped.isBlank()) {
                continue;
            }
            boolean exists = false;
            for (String target : targets) {
                if (target.equalsIgnoreCase(stripped.trim())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                targets.add(stripped.trim());
            }
        }

        List<Style> styles = buildStyleMap(input);
        for (String target : targets) {
            Matcher matcher = compileWholeNamePattern(target).matcher(joined);
            if (!matcher.find()) {
                continue;
            }
            List<Segment> output = new ArrayList<>();
            appendStyledRange(output, joined, styles, 0, matcher.start());
            Style badgeStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x5555FF)).withBold(true);
            appendSegment(output, badgeStyle, SyncedUserTextUtils.getSyncedBadge());
            appendStyledRange(output, joined, styles, matcher.start(), joined.length());
            return new ReplaceResult(output, true);
        }

        return new ReplaceResult(input, false);
    }

    private static String joinSegments(List<Segment> segments) {
        StringBuilder combined = new StringBuilder();
        for (Segment segment : segments) {
            combined.append(segment.text);
        }
        return combined.toString();
    }

    /** Сегменты до символьного индекса splitIndex (сегмент на границе делится). */
    private static List<Segment> headSegments(List<Segment> segments, int splitIndex) {
        List<Segment> head = new ArrayList<>();
        int pos = 0;
        for (Segment segment : segments) {
            int end = pos + segment.text.length();
            if (end <= splitIndex) {
                head.add(segment);
            } else if (pos < splitIndex) {
                head.add(new Segment(segment.style, segment.text.substring(0, splitIndex - pos)));
            }
            pos = end;
        }
        return head;
    }

    /** Сегменты от символьного индекса splitIndex до конца (сегмент на границе делится). */
    private static List<Segment> tailSegments(List<Segment> segments, int splitIndex) {
        List<Segment> tail = new ArrayList<>();
        int pos = 0;
        for (Segment segment : segments) {
            int end = pos + segment.text.length();
            if (pos >= splitIndex) {
                tail.add(segment);
            } else if (end > splitIndex) {
                tail.add(new Segment(segment.style, segment.text.substring(splitIndex - pos)));
            }
            pos = end;
        }
        return tail;
    }

    private static OrderedText buildOrderedText(List<Segment> segments) {
        return visitor -> {
            int index = 0;
            for (Segment segment : segments) {
                Style style = segment.style == null ? Style.EMPTY : segment.style;
                String content = segment.text;
                for (int i = 0; i < content.length(); ) {
                    int codePoint = content.codePointAt(i);
                    if (!visitor.accept(index, style, codePoint)) return false;
                    int charCount = Character.charCount(codePoint);
                    index += charCount;
                    i += charCount;
                }
            }
            return true;
        };
    }

    private static OrderedText buildOrderedText(String text, Style style) {
        Style safeStyle = style == null ? Style.EMPTY : style;
        return visitor -> {
            int index = 0;
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                if (!visitor.accept(index, safeStyle, codePoint)) return false;
                int charCount = Character.charCount(codePoint);
                index += charCount;
                i += charCount;
            }
            return true;
        };
    }

    private static SanitizedSegments sanitizeSegments(List<Segment> input, boolean chatDecoratedDonatePrefix) {
        List<Segment> current = copySegments(input);
        boolean changed = false;
        String replacementName = getReplacementName();
        String customDonate = getActiveCustomDonate();
        boolean suppressDonateReplacement = chatDecoratedDonatePrefix && isHealingSystemMessage(joinSegments(current));

        ReplaceResult result = new ReplaceResult(current, false);
        if (!replacementName.isBlank()) {
            result = replaceFormattedSegments(current, StreamerModePatterns.PLAYER_PLACEHOLDER_PATTERN, 0, replacementName, null);
            current = result.segments;
            changed |= result.changed;
        }

        if (!customDonate.isBlank() && !suppressDonateReplacement && chatDecoratedDonatePrefix) {
            List<String> donateTargets = chatDecoratedDonatePrefix ? getSelfNames() : getDonatePrefixTargets(false);
            for (String name : donateTargets) {
                if (name == null || name.isBlank()) continue;
                String joined = joinSegments(current);
                if (chatDecoratedDonatePrefix && !isNameInChatHeader(joined, name)) {
                    continue;
                }
                if (chatDecoratedDonatePrefix && StreamerModeState.scoreboardServerType() == ScoreboardServerType.ANARCHY) {
                    if (hasDisplayedCustomDonatePrefix(joined, name)) {
                        continue;
                    }
                    result = replaceDonatePrefixSegments(current, compileDonatePrefixPattern(name), customDonate);
                    if (!result.changed) {
                        result = replaceDonatePrefixSegments(current, compileGlyphDonatePrefixPattern(name), customDonate, true, true, false, StreamerModeState.DONATE_COLOR);
                    }
                } else {
                    if (hasAnyCustomDonatePrefix(joined, name)) {
                        continue;
                    }
                    if (chatDecoratedDonatePrefix) {
                        result = replaceDonatePrefixSegments(current, compileDonatePrefixPattern(name), customDonate, true, true);
                        if (!result.changed) {
                            result = replaceDonatePrefixSegments(current, compileGlyphDonatePrefixPattern(name), customDonate, true, true);
                        }
                    } else if (StreamerModeState.scoreboardServerType() == ScoreboardServerType.GRIEF) {
                        result = replaceDonatePrefixSegments(current, compileDonatePrefixPattern(name), customDonate, false, true);
                        if (!result.changed) {
                            result = replaceDonatePrefixSegments(current, compileGlyphDonatePrefixPattern(name), customDonate, false, true);
                        }
                        if (!result.changed) {
                            result = replaceContextualPrefixBeforeNameSegments(current, name, getPlainDonatePrefixToken(), StreamerModeState.DONATE_COLOR, true);
                        }
                        if (!result.changed) {
                            result = replaceContextualPrefixBeforeNameSegments(current, name, getPlainDonatePrefixToken(), StreamerModeState.DONATE_COLOR, true, false);
                        }
                        if (!result.changed) {
                            result = replaceLoosePrefixBeforeNameSegments(current, name, getPlainDonatePrefixToken(), StreamerModeState.DONATE_COLOR, true);
                        }
                    } else {
                        result = replaceDonatePrefixSegments(current, compileDonatePrefixPattern(name), customDonate, false, false);
                        if (!result.changed) {
                            result = replaceContextualPrefixBeforeNameSegments(current, name, getPlainDonatePrefixToken(), StreamerModeState.DONATE_COLOR, false, false);
                        }
                        if (!result.changed) {
                            result = replaceLoosePrefixBeforeNameSegments(current, name, getPlainDonatePrefixToken(), StreamerModeState.DONATE_COLOR, false);
                        }
                    }
                }
                current = result.segments;
                changed |= result.changed;
            }

            result = replaceRankLabelSegments(current, customDonate, StreamerModeState.DONATE_COLOR);
            current = result.segments;
            changed |= result.changed;
        } else if (!customDonate.isBlank() && !suppressDonateReplacement) {
            result = replaceRankLabelSegments(current, customDonate, StreamerModeState.DONATE_COLOR);
            current = result.segments;
            changed |= result.changed;
        }

        if (!replacementName.isBlank()) {
            for (String name : getSelfNames()) {
                if (name == null || name.isBlank()) continue;
                if (name.equalsIgnoreCase(replacementName)) continue;
                result = replaceFormattedSegmentsPreservingLeadingWhitespace(current, compileWholeNamePattern(name), 0, replacementName, null);
                current = result.segments;
                changed |= result.changed;
            }
        }
        result = collapseDuplicateCustomDonateAndName(current);
        current = result.segments;
        changed |= result.changed;

        Map<String, String> friendNicknames = getFriendNicknames();
        if (!friendNicknames.isEmpty()) {
            for (Map.Entry<String, String> entry : friendNicknames.entrySet()) {
                String friendName = entry.getKey();
                String friendNickname = entry.getValue();
                if (friendNickname == null || friendNickname.isBlank() || friendName.equalsIgnoreCase(friendNickname)) continue;
                result = replaceFormattedSegmentsPreservingLeadingWhitespace(
                        current,
                        compileWholeNamePattern(friendName),
                        0,
                        friendNickname,
                        null
                );
                current = result.segments;
                changed |= result.changed;
            }
        }

        String customCoins = getCustomCoins();
        if (!customCoins.isBlank()) {
            result = replaceCoinSegments(current, customCoins, getCoinsColor());
            current = result.segments;
            changed |= result.changed;
        }

        if (shouldHideAnarchy()) {
            result = replaceSegments(current, StreamerModePatterns.ANARCHY_PATTERN, 3, hiddenModeNumber(false), null);
            current = result.segments;
            changed |= result.changed;
            result = replaceSegments(current, StreamerModePatterns.GRIEF_PATTERN, 3, hiddenModeNumber(true), null);
            current = result.segments;
            changed |= result.changed;
        }

        return new SanitizedSegments(current, changed);
    }

    private static boolean isHealingSystemMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("исцелен")
                || lower.contains("исцелён")
                || lower.contains("исцелили")
                || lower.contains("вылечил")
                || lower.contains("вылечили")
                || lower.contains("healed");
    }

    private static List<String> getDonatePrefixTargets(boolean chatDecoratedDonatePrefix) {
        List<String> targets = new ArrayList<>(getSelfNames());
        if (!chatDecoratedDonatePrefix) {
            return targets;
        }

        String replacementName = getReplacementName();
        if (replacementName.isBlank()) {
            return targets;
        }

        for (String target : targets) {
            if (replacementName.equalsIgnoreCase(target)) {
                return targets;
            }
        }
        targets.add(replacementName);
        String plainReplacementName = stripLegacyFormatting(replacementName);
        if (!plainReplacementName.isBlank()) {
            boolean exists = false;
            for (String target : targets) {
                if (plainReplacementName.equalsIgnoreCase(target)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                targets.add(plainReplacementName);
            }
        }
        return targets;
    }

    private static ReplaceResult replaceRankLabelSegments(List<Segment> input, String replacement, Integer color) {
        if (input.isEmpty() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = StreamerModePatterns.RANK_LABEL_PATTERN.matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        int cursor = 0;
        boolean changed = false;

        do {
            int labelEnd = matcher.end(1);
            int valueStart = matcher.start(2);
            int valueEnd = matcher.end(2);
            if (labelEnd <= 0 || valueStart < 0 || valueEnd <= valueStart) {
                continue;
            }

            appendStyledRange(output, joined, styles, cursor, labelEnd);

            Style separatorStyle = styleAt(styles, Math.max(labelEnd - 1, 0));
            appendSegment(output, separatorStyle, " ");

            Style baseStyle = styleAt(styles, valueStart);
            Style replacementStyle = color == null ? baseStyle : baseStyle.withColor(TextColor.fromRgb(color));
            appendFormattedSegment(output, replacementStyle, replacement);

            cursor = valueEnd;
            changed = true;
        } while (matcher.find());

        appendStyledRange(output, joined, styles, cursor, joined.length());
        return new ReplaceResult(output, changed);
    }

    private static ReplaceResult replaceDonatePrefixSegments(List<Segment> input, Pattern pattern, String donate) {
        return replaceDonatePrefixSegments(input, pattern, donate, true, false);
    }

    private static ReplaceResult replaceDonatePrefixSegments(List<Segment> input, Pattern pattern, String donate, boolean bracketed, boolean resetFont) {
        return replaceDonatePrefixSegments(input, pattern, donate, bracketed, resetFont, true);
    }

    private static ReplaceResult replaceDonatePrefixSegments(List<Segment> input, Pattern pattern, String donate, boolean bracketed, boolean resetFont, boolean validatePrefix) {
        return replaceDonatePrefixSegments(input, pattern, donate, bracketed, resetFont, validatePrefix, StreamerModeState.DONATE_COLOR);
    }

    private static ReplaceResult replaceDonatePrefixSegments(List<Segment> input, Pattern pattern, String donate, boolean bracketed, boolean resetFont, boolean validatePrefix, Integer donateColor) {
        if (input.isEmpty() || donate == null || donate.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = pattern.matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        int cursor = 0;
        boolean changed = false;

        do {
            int start = matcher.start(1);
            int end = matcher.end(1);
            if (start < 0 || end <= start) {
                continue;
            }

            String matchedPrefix = joined.substring(start, end);
            boolean customFontPrefix = hasCustomFont(styles, start, end);
            String matchedPrefixWithoutBadge = removeSyncedBadgeFromPrefix(matchedPrefix);
            boolean glyphPrefix = containsDonateGlyph(matchedPrefixWithoutBadge);
            if (!hasDonateContentBesidesSyncedBadge(matchedPrefix) && !customFontPrefix && !glyphPrefix) {
                continue;
            }

            if (validatePrefix && !customFontPrefix && !glyphPrefix && !isLikelyDonatePrefixSlice(matchedPrefixWithoutBadge)) {
                continue;
            }

            appendStyledRange(output, joined, styles, cursor, start);

            int preserveEnd = syncedBadgePreserveEnd(joined, start, end);
            if (preserveEnd <= start) {
                preserveEnd = start + leadingDonateDecorationLength(joined, start, end);
            }
            if (preserveEnd > start) {
                appendStyledRange(output, joined, styles, start, preserveEnd);
            }

            Style baseStyle = styleAt(styles, Math.min(preserveEnd, end - 1));
            Style bracketStyle = donateReplacementStyle(baseStyle, StreamerModeState.DONATE_BRACKET_COLOR, resetFont);
            Style donateStyle = donateReplacementStyle(baseStyle, donateColor, resetFont);
            if (bracketed) {
                appendSegment(output, bracketStyle, "[");
            }
            appendFormattedSegment(output, donateStyle, donate);
            if (bracketed) {
                appendSegment(output, bracketStyle, "] ");
            } else {
                appendSegment(output, donateStyle, " ");
            }

            cursor = end;
            changed = true;
        } while (matcher.find());

        appendStyledRange(output, joined, styles, cursor, joined.length());
        return new ReplaceResult(output, changed);
    }

    private static boolean hasDonateContentBesidesSyncedBadge(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        String withoutBadge = removeSyncedBadgeFromPrefix(prefix);
        if (withoutBadge.isBlank()) {
            return false;
        }
        String normalized = stripDonateDecorators(withoutBadge).trim();
        return !normalized.isBlank() && !isServiceTag(normalized);
    }

    private static String removeSyncedBadgeFromPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        String badge = SyncedUserTextUtils.getSyncedBadge();
        String result = prefix;
        if (badge != null && !badge.isBlank()) {
            result = result.replace(badge, "");
            result = result.replace(badge.trim(), "");
        }
        return result;
    }

    private static int syncedBadgePreserveEnd(String input, int start, int end) {
        if (input == null || start < 0 || end <= start || start >= input.length()) {
            return -1;
        }
        String badge = SyncedUserTextUtils.getSyncedBadge();
        if (badge == null || badge.isBlank()) {
            return -1;
        }
        int cursor = start;
        while (cursor < end && Character.isWhitespace(input.charAt(cursor))) {
            cursor++;
        }
        if (!input.regionMatches(cursor, badge, 0, badge.length())) {
            String trimmedBadge = badge.trim();
            if (trimmedBadge.isEmpty() || !input.regionMatches(cursor, trimmedBadge, 0, trimmedBadge.length())) {
                return -1;
            }
            cursor += trimmedBadge.length();
        } else {
            cursor += badge.length();
        }
        while (cursor < end && Character.isWhitespace(input.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static String replaceContextualPrefixBeforeName(String input, String name, String replacement) {
        if (input == null || input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return input;
        }

        Mapping mapping = buildMapping(input);
        if (mapping.plain.isEmpty()) {
            return input;
        }

        Matcher matcher = compileWholeNamePattern(name).matcher(mapping.plain);
        if (!matcher.find()) {
            return input;
        }

        int nameStart = matcher.start();
        int prefixStart = findContextualPrefixStart(mapping.plain, nameStart);
        if (prefixStart < 0 || prefixStart >= nameStart) {
            return input;
        }

        if (!isLikelyDonatePrefixSlice(mapping.plain.substring(prefixStart, nameStart))) {
            return input;
        }

        int origPrefixStart = mapping.origIndex.get(prefixStart);
        int preserveEnd = prefixStart + leadingDonateDecorationLength(mapping.plain, prefixStart, nameStart);
        int origPreserveEnd = preserveEnd > prefixStart ? mapping.origIndex.get(preserveEnd - 1) + 1 : origPrefixStart;
        int origNameStart = mapping.origIndex.get(nameStart);
        return input.substring(0, origPreserveEnd) + replacement + input.substring(origNameStart);
    }

    private static ReplaceResult replaceContextualPrefixBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color) {
        return replaceContextualPrefixBeforeNameSegments(input, name, replacement, color, false);
    }

    private static ReplaceResult replaceContextualPrefixBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont) {
        return replaceContextualPrefixBeforeNameSegments(input, name, replacement, color, resetFont, true);
    }

    private static ReplaceResult replaceContextualPrefixBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont, boolean validatePrefix) {
        if (input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        List<Style> styles = buildStyleMap(input);
        Matcher matcher = compileWholeNamePattern(name).matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        int nameStart = matcher.start();
        int prefixStart = findContextualPrefixStart(joined, nameStart);
        if (prefixStart < 0 || prefixStart >= nameStart) {
            return new ReplaceResult(input, false);
        }

        String prefixSlice = joined.substring(prefixStart, nameStart);
        if (validatePrefix && !isLikelyDonatePrefixSlice(prefixSlice)) {
            return new ReplaceResult(input, false);
        }
        if (!validatePrefix && !isSafeBracketedDonatePrefix(prefixSlice) && !hasCustomFont(styles, prefixStart, nameStart)) {
            return new ReplaceResult(input, false);
        }

        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, prefixStart);

        int preserveEnd = prefixStart + leadingDonateDecorationLength(joined, prefixStart, nameStart);
        if (preserveEnd > prefixStart) {
            appendStyledRange(output, joined, styles, prefixStart, preserveEnd);
        }

        Style baseStyle = styleAt(styles, Math.min(preserveEnd, nameStart - 1));
        Style replacementStyle = donateReplacementStyle(baseStyle, color, resetFont);
        appendFormattedSegment(output, replacementStyle, replacement);
        appendStyledRange(output, joined, styles, nameStart, joined.length());
        return new ReplaceResult(output, true);
    }

    private static boolean hasCustomFont(List<Style> styles, int start, int end) {
        if (styles == null || styles.isEmpty() || start < 0 || end <= start) {
            return false;
        }
        int safeEnd = Math.min(end, styles.size());
        for (int i = Math.min(start, styles.size() - 1); i < safeEnd; i++) {
            Style style = styles.get(i);
            if (style != null && style.getFont() != null && !style.getFont().equals(Style.EMPTY.getFont())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isResourcePackChatDonatePrefix(List<Style> styles, int start, int end, String slice) {
        if (slice == null) {
            return false;
        }
        String trimmed = slice.trim();
        if (trimmed.isBlank() || trimmed.length() > 32 || isLikelyChatServicePrefix(trimmed)) {
            return false;
        }
        if (trimmed.indexOf('>') >= 0
                || trimmed.indexOf(':') >= 0
                || trimmed.indexOf('|') >= 0
                || trimmed.indexOf('\n') >= 0
                || trimmed.indexOf('\r') >= 0) {
            return false;
        }
        return hasCustomFont(styles, start, end)
                || containsDonateGlyph(trimmed)
                || isSafeBracketedDonatePrefix(trimmed);
    }

    private static ReplaceResult replaceLoosePrefixBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont) {
        if (input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = compileWholeNamePattern(name).matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        int nameStart = matcher.start();
        int prefixStart = findLoosePrefixStart(joined, nameStart);
        if (prefixStart < 0 || prefixStart >= nameStart) {
            return new ReplaceResult(input, false);
        }

        String prefixSlice = joined.substring(prefixStart, nameStart).trim();
        if (prefixSlice.isBlank() || isLikelyChatServicePrefix(prefixSlice)) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        boolean resourcePackPrefix = hasCustomFont(styles, prefixStart, nameStart);
        boolean decoratedPrefix = containsDonateGlyph(prefixSlice)
                || isSafeBracketedDonatePrefix(prefixSlice)
                || isLikelyDonatePrefixSlice(prefixSlice);
        if (!resourcePackPrefix && !decoratedPrefix) {
            return new ReplaceResult(input, false);
        }

        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, prefixStart);

        int preserveEnd = prefixStart + leadingDonateDecorationLength(joined, prefixStart, nameStart);
        if (preserveEnd > prefixStart) {
            appendStyledRange(output, joined, styles, prefixStart, preserveEnd);
        }

        Style baseStyle = styleAt(styles, Math.min(preserveEnd, nameStart - 1));
        Style replacementStyle = donateReplacementStyle(baseStyle, color, resetFont);
        appendFormattedSegment(output, replacementStyle, replacement);
        appendStyledRange(output, joined, styles, nameStart, joined.length());
        return new ReplaceResult(output, true);
    }

    private static ReplaceResult replaceIconDonatePrefixBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont) {
        if (input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = compileWholeNamePattern(name).matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        int nameStart = matcher.start();
        int prefixStart = findLoosePrefixStart(joined, nameStart);
        if (prefixStart < 0 || prefixStart >= nameStart) {
            return new ReplaceResult(input, false);
        }

        int iconEnd = readLeadingIconWithSpaceEnd(joined, prefixStart, nameStart);
        if (iconEnd <= prefixStart || iconEnd >= nameStart) {
            return new ReplaceResult(input, false);
        }

        String donateSlice = joined.substring(iconEnd, nameStart).trim();
        if (donateSlice.isBlank() || isLikelyChatServicePrefix(donateSlice)) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, iconEnd);
        Style baseStyle = styleAt(styles, iconEnd);
        Style replacementStyle = donateReplacementStyle(baseStyle, color, resetFont);
        appendFormattedSegment(output, replacementStyle, replacement);
        appendStyledRange(output, joined, styles, nameStart, joined.length());
        return new ReplaceResult(output, true);
    }

    private static int readLeadingIconWithSpaceEnd(String input, int start, int end) {
        if (input == null || start < 0 || end <= start) {
            return -1;
        }
        int cursor = start;
        boolean sawIcon = false;
        while (cursor < end && Character.isWhitespace(input.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= end) {
            return -1;
        }
        int iconStart = cursor;
        int codePoint = input.codePointAt(cursor);
        if (!Character.isLetterOrDigit(codePoint)) {
            sawIcon = true;
            cursor += Character.charCount(codePoint);
        }
        if (!sawIcon || cursor >= end || !Character.isWhitespace(input.charAt(cursor))) {
            return -1;
        }
        while (cursor < end && Character.isWhitespace(input.charAt(cursor))) {
            cursor++;
        }
        return cursor > iconStart ? cursor : -1;
    }

    private static ReplaceResult replaceWhitespaceDonateBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont) {
        if (input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = compileWholeNamePattern(name).matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        int nameStart = matcher.start();
        int donateEnd = nameStart;
        while (donateEnd > 0 && Character.isWhitespace(joined.charAt(donateEnd - 1))) {
            donateEnd--;
        }
        if (donateEnd <= 0 || donateEnd >= nameStart) {
            return new ReplaceResult(input, false);
        }

        int donateStart = donateEnd;
        while (donateStart > 0 && !Character.isWhitespace(joined.charAt(donateStart - 1))) {
            donateStart--;
        }
        if (donateStart >= donateEnd) {
            return new ReplaceResult(input, false);
        }

        int iconEnd = donateStart;
        while (iconEnd > 0 && Character.isWhitespace(joined.charAt(iconEnd - 1))) {
            iconEnd--;
        }
        if (iconEnd <= 0 || !isDonateLeadingDecoration(joined.codePointBefore(iconEnd))) {
            return new ReplaceResult(input, false);
        }

        String donateSlice = joined.substring(donateStart, donateEnd).trim();
        if (donateSlice.isBlank() || isLikelyChatServicePrefix(donateSlice)) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, donateStart);
        Style baseStyle = styleAt(styles, donateStart);
        Style replacementStyle = donateReplacementStyle(baseStyle, color, resetFont);
        appendFormattedSegment(output, replacementStyle, replacement);
        appendStyledRange(output, joined, styles, nameStart, joined.length());
        return new ReplaceResult(output, true);
    }

    private static ReplaceResult replaceImmediateTokenBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont) {
        if (input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = compileWholeNamePattern(name).matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        int nameStart = matcher.start();
        int donateEnd = nameStart;
        while (donateEnd > 0 && Character.isWhitespace(joined.charAt(donateEnd - 1))) {
            donateEnd--;
        }
        if (donateEnd <= 0 || donateEnd >= nameStart) {
            return new ReplaceResult(input, false);
        }

        int donateStart = donateEnd;
        while (donateStart > 0 && !Character.isWhitespace(joined.charAt(donateStart - 1))) {
            donateStart--;
        }
        if (donateStart >= donateEnd) {
            return new ReplaceResult(input, false);
        }

        String donateSlice = joined.substring(donateStart, donateEnd).trim();
        if (donateSlice.isBlank() || isLikelyChatServicePrefix(donateSlice)) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, donateStart);
        Style baseStyle = styleAt(styles, donateStart);
        Style replacementStyle = donateReplacementStyle(baseStyle, color, resetFont);
        appendFormattedSegment(output, replacementStyle, replacement);
        appendStyledRange(output, joined, styles, nameStart, joined.length());
        return new ReplaceResult(output, true);
    }

    private static ReplaceResult replaceResourcePackDonateBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont) {
        if (input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = compileWholeNamePattern(name).matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        int nameStart = matcher.start();
        int prefixStart = findLoosePrefixStart(joined, nameStart);
        if (prefixStart < 0 || prefixStart >= nameStart) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        int donateStart = syncedBadgePreserveEnd(joined, prefixStart, nameStart);
        if (donateStart <= prefixStart) {
            donateStart = prefixStart;
        }
        while (donateStart < nameStart && Character.isWhitespace(joined.charAt(donateStart))) {
            donateStart++;
        }
        if (donateStart >= nameStart) {
            return new ReplaceResult(input, false);
        }

        String donateSlice = joined.substring(donateStart, nameStart).trim();
        if (donateSlice.isBlank() || isLikelyChatServicePrefix(donateSlice)) {
            return new ReplaceResult(input, false);
        }
        if (!hasCustomFont(styles, donateStart, nameStart) && !containsDonateGlyph(donateSlice)) {
            return new ReplaceResult(input, false);
        }

        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, prefixStart);
        int preserveEnd = syncedBadgePreserveEnd(joined, prefixStart, nameStart);
        if (preserveEnd > prefixStart) {
            appendStyledRange(output, joined, styles, prefixStart, preserveEnd);
        }

        Style baseStyle = styleAt(styles, Math.max(donateStart, Math.min(nameStart - 1, styles.size() - 1)));
        Style replacementStyle = donateReplacementStyle(baseStyle, color, resetFont);
        appendFormattedSegment(output, replacementStyle, replacement);
        appendStyledRange(output, joined, styles, nameStart, joined.length());
        return new ReplaceResult(output, true);
    }

    private static ReplaceResult replaceImmediateTokenAfterNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont) {
        if (input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = compileWholeNamePattern(name).matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        int nameEnd = matcher.end();
        int donateStart = nameEnd;
        while (donateStart < joined.length() && Character.isWhitespace(joined.charAt(donateStart))) {
            donateStart++;
        }
        if (donateStart >= joined.length()) {
            return new ReplaceResult(input, false);
        }

        int donateEnd = donateStart;
        while (donateEnd < joined.length() && !Character.isWhitespace(joined.charAt(donateEnd))) {
            donateEnd++;
        }
        if (donateStart >= donateEnd) {
            return new ReplaceResult(input, false);
        }

        String donateSlice = joined.substring(donateStart, donateEnd).trim();
        if (donateSlice.isBlank() || isLikelyChatServicePrefix(donateSlice)) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, donateStart);
        Style baseStyle = styleAt(styles, donateStart);
        Style replacementStyle = donateReplacementStyle(baseStyle, color, resetFont);
        appendFormattedSegment(output, replacementStyle, replacement.trim());
        appendStyledRange(output, joined, styles, donateEnd, joined.length());
        return new ReplaceResult(output, true);
    }

    private static ReplaceResult replaceAutoDetectedDonateBeforeNameSegments(List<Segment> input, String name, String replacement, Integer color, boolean resetFont) {
        if (input.isEmpty() || name == null || name.isBlank() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = compileWholeNamePattern(name).matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        int nameStart = matcher.start();
        int donateStart = findAutoDonateStart(joined, nameStart);
        if (donateStart < 0 || donateStart >= nameStart) {
            return new ReplaceResult(input, false);
        }

        String slice = joined.substring(donateStart, nameStart).trim();
        if (slice.isBlank() || isLikelyChatServicePrefix(slice)) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        appendStyledRange(output, joined, styles, 0, donateStart);
        Style baseStyle = styleAt(styles, donateStart);
        Style replacementStyle = donateReplacementStyle(baseStyle, color, resetFont);
        appendFormattedSegment(output, replacementStyle, replacement);
        appendStyledRange(output, joined, styles, nameStart, joined.length());
        return new ReplaceResult(output, true);
    }

    private static int findAutoDonateStart(String input, int nameStart) {
        if (input == null || nameStart <= 0 || nameStart > input.length()) {
            return -1;
        }
        int cursor = nameStart - 1;
        while (cursor >= 0 && Character.isWhitespace(input.charAt(cursor))) {
            cursor--;
        }
        if (cursor < 0) {
            return -1;
        }
        int iconBoundary = -1;
        int floor = Math.max(0, nameStart - 80);
        for (int i = cursor; i >= floor; ) {
            int codePoint = input.codePointBefore(i + 1);
            int charStart = i + 1 - Character.charCount(codePoint);
            if (isDonateLeadingDecoration(codePoint)) {
                iconBoundary = charStart + Character.charCount(codePoint);
                break;
            }
            i = charStart - 1;
        }
        if (iconBoundary < 0 || iconBoundary >= nameStart) {
            return -1;
        }
        int donateStart = iconBoundary;
        while (donateStart < nameStart && Character.isWhitespace(input.charAt(donateStart))) {
            donateStart++;
        }
        return donateStart >= nameStart ? -1 : donateStart;
    }

    private static int leadingDonateDecorationLength(String input, int start, int end) {
        if (input == null || start < 0 || end <= start || start >= input.length()) {
            return 0;
        }
        int cursor = start;
        int safeEnd = Math.min(end, input.length());
        boolean sawDecoration = false;
        while (cursor < safeEnd) {
            int codePoint = input.codePointAt(cursor);
            if (Character.isWhitespace(codePoint)) {
                cursor += Character.charCount(codePoint);
                continue;
            }
            if (isDonateLeadingDecoration(codePoint)) {
                sawDecoration = true;
                cursor += Character.charCount(codePoint);
                continue;
            }
            break;
        }
        if (!sawDecoration || cursor >= safeEnd) {
            return 0;
        }
        return cursor - start;
    }

    private static boolean isDonateLeadingDecoration(int codePoint) {
        if (codePoint == '[' || codePoint == '&' || codePoint == '\u00A7') {
            return false;
        }
        if (Character.isLetterOrDigit(codePoint)) {
            return false;
        }
        int type = Character.getType(codePoint);
        return type == Character.OTHER_SYMBOL
                || type == Character.PRIVATE_USE
                || type == Character.MATH_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.DASH_PUNCTUATION
                || codePoint == '•'
                || codePoint == '●'
                || codePoint == '◆'
                || codePoint == '❖'
                || codePoint == '✚';
    }

    private static int findLoosePrefixStart(String input, int nameStart) {
        if (input == null || input.isEmpty() || nameStart <= 0 || nameStart > input.length()) {
            return -1;
        }
        int cursor = nameStart - 1;
        while (cursor >= 0 && Character.isWhitespace(input.charAt(cursor))) {
            cursor--;
        }
        if (cursor < 0) {
            return -1;
        }
        int floor = Math.max(0, nameStart - 64);
        int start = cursor;
        while (start > floor) {
            char previous = input.charAt(start - 1);
            if (previous == '<' || previous == '>' || previous == ':' || previous == '|' || previous == ',' || previous == '\n' || previous == '\r') {
                break;
            }
            start--;
        }
        while (start < nameStart && Character.isWhitespace(input.charAt(start))) {
            start++;
        }
        return start >= nameStart ? -1 : start;
    }

    private static boolean isLikelyChatServicePrefix(String slice) {
        if (slice == null || slice.isBlank()) {
            return true;
        }
        String normalized = stripDonateDecorators(slice.trim());
        return normalized.isBlank() || isServiceTag(normalized);
    }

    private static boolean isSafeBracketedDonatePrefix(String slice) {
        if (slice == null) {
            return false;
        }
        String trimmed = slice.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return false;
        }
        String normalized = stripDonateDecorators(trimmed);
        return !normalized.isBlank() && !isServiceTag(normalized);
    }

    private static ReplaceResult replaceCoinSegments(List<Segment> input, String replacement, Integer color) {
        if (input.isEmpty() || replacement == null || replacement.isBlank()) {
            return new ReplaceResult(input, false);
        }

        String joined = joinSegments(input);
        Matcher matcher = StreamerModePatterns.COIN_LABEL_PATTERN.matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        int cursor = 0;
        boolean changed = false;

        do {
            int start = matcher.start(2);
            int end = matcher.end(2);
            if (start < 0 || end <= start) {
                continue;
            }

            appendStyledRange(output, joined, styles, cursor, start);

            Style baseStyle = styleAt(styles, start);
            Style replacementStyle = color == null ? baseStyle : baseStyle.withColor(TextColor.fromRgb(color));
            appendSegment(output, replacementStyle, replacement);
            if (shouldNormalizeGriefCoinsSpace()) {
                appendSegment(output, replacementStyle, " ");
            }

            cursor = shouldNormalizeGriefCoinsSpace() ? preserveTrailingWhitespaceEnd(joined, end) : end;
            changed = true;
        } while (matcher.find());

        appendStyledRange(output, joined, styles, cursor, joined.length());
        return new ReplaceResult(output, changed);
    }

    private static ReplaceResult replaceSegments(List<Segment> input, Pattern pattern, int group, String replacement, Integer color) {
        return replaceSegments(input, pattern, group, replacement, color, false, false);
    }

    private static ReplaceResult replaceSegmentsPreservingLeadingWhitespace(List<Segment> input, Pattern pattern, int group, String replacement, Integer color) {
        return replaceSegments(input, pattern, group, replacement, color, true, false);
    }

    private static ReplaceResult replaceFormattedSegments(List<Segment> input, Pattern pattern, int group, String replacement, Integer color) {
        return replaceSegments(input, pattern, group, replacement, color, false, false, true);
    }

    private static ReplaceResult replaceFormattedSegmentsPreservingLeadingWhitespace(List<Segment> input, Pattern pattern, int group, String replacement, Integer color) {
        return replaceSegments(input, pattern, group, replacement, color, true, false, true);
    }

    private static ReplaceResult replaceSegmentsPreservingTrailingWhitespace(List<Segment> input, Pattern pattern, int group, String replacement, Integer color) {
        return replaceSegments(input, pattern, group, replacement, color, false, true);
    }

    private static ReplaceResult replaceSegments(List<Segment> input, Pattern pattern, int group, String replacement, Integer color, boolean preserveLeadingWhitespace, boolean preserveTrailingWhitespace) {
        return replaceSegments(input, pattern, group, replacement, color, preserveLeadingWhitespace, preserveTrailingWhitespace, false);
    }

    private static ReplaceResult replaceSegments(List<Segment> input, Pattern pattern, int group, String replacement, Integer color, boolean preserveLeadingWhitespace, boolean preserveTrailingWhitespace, boolean parseFormatting) {
        if (input.isEmpty()) return new ReplaceResult(input, false);

        String joined = joinSegments(input);
        Matcher matcher = pattern.matcher(joined);
        if (!matcher.find()) {
            return new ReplaceResult(input, false);
        }

        List<Style> styles = buildStyleMap(input);
        List<Segment> output = new ArrayList<>();
        int cursor = 0;
        boolean changed = false;

        do {
            int start = group == 0 ? matcher.start() : matcher.start(group);
            int end = group == 0 ? matcher.end() : matcher.end(group);
            if (start < 0 || end <= start) continue;

            int adjustedStart = preserveLeadingWhitespace ? preserveLeadingWhitespaceStart(joined, start) : start;
            int adjustedEnd = preserveTrailingWhitespace ? preserveTrailingWhitespaceEnd(joined, end) : end;
            appendStyledRange(output, joined, styles, cursor, adjustedStart);
            Style baseStyle = styleAt(styles, start);
            Style replacementStyle = color == null ? baseStyle : baseStyle.withColor(TextColor.fromRgb(color));
            String resolved = resolveReplacement(matcher, replacement);
            if (adjustedStart < start) {
                resolved = joined.substring(adjustedStart, start) + resolved;
            }
            if (adjustedEnd > end) {
                resolved = resolved + joined.substring(end, adjustedEnd);
            }
            if (parseFormatting) {
                appendFormattedSegment(output, replacementStyle, resolved);
            } else {
                appendSegment(output, replacementStyle, resolved);
            }
            cursor = adjustedEnd;
            changed = true;
        } while (matcher.find());

        appendStyledRange(output, joined, styles, cursor, joined.length());
        return new ReplaceResult(output, changed);
    }

    private static int preserveLeadingWhitespaceStart(String input, int start) {
        if (input == null || start <= 0 || start > input.length()) {
            return start;
        }
        char previous = input.charAt(start - 1);
        return Character.isWhitespace(previous) ? start - 1 : start;
    }

    private static int preserveTrailingWhitespaceEnd(String input, int end) {
        if (input == null || end < 0 || end >= input.length()) {
            return end;
        }
        int adjustedEnd = end;
        while (adjustedEnd < input.length() && Character.isWhitespace(input.charAt(adjustedEnd))) {
            adjustedEnd++;
        }
        return adjustedEnd;
    }

    private static boolean shouldNormalizeGriefCoinsSpace() {
        return StreamerModeState.scoreboardServerType() == ScoreboardServerType.GRIEF;
    }

    private static Style donateReplacementStyle(Style baseStyle, Integer color, boolean resetFont) {
        Style style = resetFont ? Style.EMPTY : (baseStyle == null ? Style.EMPTY : baseStyle);
        return color == null ? style : style.withColor(TextColor.fromRgb(color));
    }

    private static int findContextualPrefixStart(String input, int nameStart) {
        if (input == null || input.isEmpty() || nameStart <= 0 || nameStart > input.length()) {
            return -1;
        }

        int cursor = nameStart - 1;
        while (cursor >= 0 && Character.isWhitespace(input.charAt(cursor))) {
            cursor--;
        }
        if (cursor < 0) {
            return -1;
        }

        int floor = Math.max(0, nameStart - 48);
        if (input.charAt(cursor) == ']') {
            int bracketStart = input.lastIndexOf('[', cursor);
            if (bracketStart >= floor) {
                return bracketStart;
            }
        }
        while (cursor >= floor && !isContextualPrefixBoundary(input.charAt(cursor))) {
            cursor--;
        }

        int start = cursor >= floor && input.charAt(cursor) == '[' ? cursor : cursor + 1;
        while (start < nameStart && Character.isWhitespace(input.charAt(start))) {
            start++;
        }

        if (start >= nameStart) {
            return -1;
        }

        String slice = input.substring(start, nameStart).trim();
        return slice.isEmpty() ? -1 : start;
    }

    private static boolean isContextualPrefixBoundary(char c) {
        return Character.isWhitespace(c)
                || c == '<'
                || c == '>'
                || c == '['
                || c == ']'
                || c == '('
                || c == ')'
                || c == '{'
                || c == '}'
                || c == ':'
                || c == ';'
                || c == '|'
                || c == ','
                || c == '«'
                || c == '»';
    }

    private static boolean isLikelyDonatePrefixSlice(String slice) {
        if (slice == null) {
            return false;
        }

        String trimmed = slice.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0 || tokens.length > 6) {
            return false;
        }

        boolean strongSignal = false;
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                return false;
            }

            if (!isLikelyDonateToken(token)) {
                return false;
            }

            String normalized = stripDonateDecorators(token);
            if (isServiceTag(normalized)) {
                return false;
            }

            if (containsDonateGlyph(token)
                    || StreamerModePatterns.KNOWN_DONATE_TOKEN_PATTERN.matcher(normalized).matches()
                    || isBracketedDonateToken(token, normalized)
                    || isUppercaseDonateToken(normalized)) {
                strongSignal = true;
            }
        }

        return strongSignal;
    }

    private static boolean isLikelyDonateToken(String token) {
        String normalized = stripDonateDecorators(token);
        if (normalized.isEmpty()) {
            return false;
        }

        if (StreamerModePatterns.KNOWN_DONATE_TOKEN_PATTERN.matcher(normalized).matches()) {
            return true;
        }

        if (isServiceTag(normalized)) {
            return false;
        }

        if (containsDonateGlyph(token)) {
            return true;
        }

        if (isBracketedDonateToken(token, normalized)) {
            return true;
        }

        return isUppercaseDonateToken(normalized);
    }

    private static boolean isBracketedDonateToken(String token, String normalized) {
        if (token == null || normalized == null || normalized.isBlank()) {
            return false;
        }
        return token.indexOf('[') >= 0 && token.indexOf(']') >= 0 && !isServiceTag(normalized);
    }

    private static boolean isServiceTag(String normalized) {
        return normalized != null && StreamerModePatterns.SERVICE_TAG_PATTERN.matcher(normalized).matches();
    }

    private static boolean isUppercaseDonateToken(String token) {
        boolean hasLetter = false;
        boolean hasUpper = false;

        for (int i = 0; i < token.length(); ) {
            int codePoint = token.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                hasLetter = true;
                if (!Character.isUpperCase(codePoint)) {
                    return false;
                }
                hasUpper = true;
            }
            i += Character.charCount(codePoint);
        }

        return hasLetter && hasUpper;
    }

    private static boolean containsDonateGlyph(String token) {
        for (int i = 0; i < token.length(); ) {
            int codePoint = token.codePointAt(i);
            int type = Character.getType(codePoint);
            if (type == Character.OTHER_SYMBOL || type == Character.PRIVATE_USE) {
                return true;
            }
            i += Character.charCount(codePoint);
        }

        return false;
    }

    private static String stripDonateDecorators(String token) {
        StringBuilder builder = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); ) {
            int codePoint = token.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == '+' || codePoint == '-') {
                builder.appendCodePoint(codePoint);
            }
            i += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private static String strictDonateTokenPattern() {
        return "(?:\\[[^\\]\\r\\n]{1,24}]|(?:[\\p{Co}\\p{So}\\u2605\\u2726\\u2727\\u269D\\u269C\\u265B]{1,8})[\\p{L}\\p{N}_+\\-]{1,24}|(?:" + StreamerModePatterns.DONATE_KEYWORDS + ")[\\p{L}\\p{N}_+\\-]*)";
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

    private static void appendStyledRange(List<Segment> output, String source, List<Style> styles, int start, int end) {
        if (start >= end) return;
        int index = start;
        while (index < end) {
            Style style = styleAt(styles, index);
            int next = index + 1;
            while (next < end && styleAt(styles, next).equals(style)) {
                next++;
            }
            appendSegment(output, style, source.substring(index, next));
            index = next;
        }
    }

    private static Style styleAt(List<Style> styles, int index) {
        if (styles.isEmpty()) return Style.EMPTY;
        if (index < 0) return Style.EMPTY;
        if (index >= styles.size()) return styles.getLast();
        return styles.get(index) == null ? Style.EMPTY : styles.get(index);
    }

    private static void appendSegment(List<Segment> output, Style style, String text) {
        if (text == null || text.isEmpty()) return;
        Style safeStyle = style == null ? Style.EMPTY : style;
        if (!output.isEmpty()) {
            Segment last = output.getLast();
            if (last.style.equals(safeStyle)) {
                last.text = last.text + text;
                return;
            }
        }
        output.add(new Segment(safeStyle, text));
    }

    private static void appendFormattedSegment(List<Segment> output, Style baseStyle, String text) {
        if (text == null || text.isEmpty()) return;
        if (stripLegacyFormatting(text).isBlank()) {
            appendSegment(output, baseStyle, text);
            return;
        }

        Style base = baseStyle == null ? Style.EMPTY : baseStyle;
        Style current = base;
        StringBuilder currentText = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < text.length()) {
                Formatting formatting = Formatting.byCode(Character.toLowerCase(text.charAt(i + 1)));
                if (formatting != null) {
                    if (!currentText.isEmpty()) {
                        appendSegment(output, current, currentText.toString());
                        currentText.setLength(0);
                    }
                    current = applyLegacyFormatting(current, base, formatting);
                    i++;
                    continue;
                }
            }
            currentText.append(c);
        }

        if (!currentText.isEmpty()) {
            appendSegment(output, current, currentText.toString());
        }
    }

    private static Style applyLegacyFormatting(Style currentStyle, Style baseStyle, Formatting formatting) {
        Style base = baseStyle == null ? Style.EMPTY : baseStyle;
        Style current = currentStyle == null ? base : currentStyle;
        if (formatting == Formatting.RESET) {
            return base;
        }
        if (formatting.isColor()) {
            TextColor color = TextColor.fromFormatting(formatting);
            return color == null ? current : current.withColor(color);
        }
        return switch (formatting) {
            case OBFUSCATED -> current.withObfuscated(true);
            case BOLD -> current.withBold(true);
            case STRIKETHROUGH -> current.withStrikethrough(true);
            case UNDERLINE -> current.withUnderline(true);
            case ITALIC -> current.withItalic(true);
            default -> current;
        };
    }

    private static List<Segment> copySegments(List<Segment> segments) {
        List<Segment> copy = new ArrayList<>(segments.size());
        for (Segment segment : segments) {
            copy.add(new Segment(segment.style, segment.text));
        }
        return copy;
    }

    private static Text buildText(List<Segment> segments) {
        MutableText out = Text.literal("");
        for (Segment segment : segments) {
            appendTextSegment(out, segment.style, segment.text);
        }
        return out;
    }

    private static void appendTextSegment(MutableText out, Style style, String text) {
        if (text == null || text.isEmpty()) return;
        out.append(Text.literal(text).setStyle(style == null ? Style.EMPTY : style));
    }

    private static void appendFormattedText(MutableText out, Style baseStyle, String text) {
        if (out == null || text == null || text.isEmpty()) return;
        List<Segment> segments = new ArrayList<>();
        appendFormattedSegment(segments, baseStyle, text);
        for (Segment segment : segments) {
            appendTextSegment(out, segment.style, segment.text);
        }
    }

    private static final class Segment {
        private final Style style;
        private String text;

        private Segment(Style style, String text) {
            this.style = style;
            this.text = text;
        }
    }

    private static final class ReplaceResult {
        private final List<Segment> segments;
        private final boolean changed;

        private ReplaceResult(List<Segment> segments, boolean changed) {
            this.segments = segments;
            this.changed = changed;
        }
    }

    private static final class NormalizeResult {
        private final List<Segment> segments;
        private final boolean changed;

        private NormalizeResult(List<Segment> segments, boolean changed) {
            this.segments = segments;
            this.changed = changed;
        }
    }

    private static final class SanitizedSegments {
        private final List<Segment> segments;
        private final boolean changed;

        private SanitizedSegments(List<Segment> segments, boolean changed) {
            this.segments = segments;
            this.changed = changed;
        }
    }

    private static final class Mapping {
        private final String plain;
        private final List<Integer> origIndex;

        private Mapping(String plain, List<Integer> origIndex) {
            this.plain = plain;
            this.origIndex = origIndex;
        }
    }

    private static final class FriendNicknameCache {
        private final String raw;
        private final int friendRevision;
        private final Map<String, String> mappings;

        private FriendNicknameCache(String raw, int friendRevision, Map<String, String> mappings) {
            this.raw = raw;
            this.friendRevision = friendRevision;
            this.mappings = mappings;
        }

        private boolean matches(String raw, int friendRevision) {
            return this.friendRevision == friendRevision && TabNameCacheEntry.equalsNullable(this.raw, raw);
        }
    }

    private static final class TabNameCacheEntry {
        private final Text result;
        private final String entryName;
        private final String customDonate;
        private final String replacementName;

        private TabNameCacheEntry(Text result, String entryName, String customDonate, String replacementName) {
            this.result = result;
            this.entryName = entryName;
            this.customDonate = customDonate;
            this.replacementName = replacementName;
        }

        private boolean matches(String entryName, String customDonate, String replacementName) {
            return equalsNullable(this.entryName, entryName)
                    && equalsNullable(this.customDonate, customDonate)
                    && equalsNullable(this.replacementName, replacementName);
        }

        private static boolean equalsNullable(String left, String right) {
            if (left == null) return right == null;
            return left.equals(right);
        }
    }
}
