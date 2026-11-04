package ru.method.linkenvisuals.utils.text.streamer;

import java.util.regex.Pattern;

public final class StreamerModePatterns {

    public static final String DONATE_KEYWORDS =
            "owner|admin|moder|moderator|helper|media|streamer|legend|premium|prem|vip|elite|hero|king|lord|titan|yt|youtube|ютубер|стример|медиа|вип|прем|элита|легенда|админ|хелпер|модер";

    public static final Pattern NAME_BOUNDARY = Pattern.compile("[\\p{L}\\p{N}_]");
    public static final Pattern DONATE_LABEL_PATTERN = Pattern.compile(
            "(?iu)((?:донат|привилегия|ранг|rank|donate|prefix)\\s*[:=\\-»>]?\\s*)([\\[\\]★✦✧⚝⚜\\p{L}\\p{N}_+\\- ]{2,32})");
    public static final Pattern COIN_LABEL_PATTERN = Pattern.compile(
            "(?iu)((?:монет(?:ы)?|коин(?:ы|ов)?|coins?|balance|баланс)\\s*[:=\\-»>]?\\s*)(\\d[\\d\\s.,_]*)");
    public static final Pattern DONATE_LABEL_REPLACEMENT_PATTERN = Pattern.compile(
            "(?iu)((?:донат|привилегия|ранг|rank|donate|prefix)\\s*[:=\\-»>]?)\\s*([\\[\\]★✦✧⚝⚜\\p{L}\\p{N}_+\\- ]{1,32})");
    public static final Pattern RANK_LABEL_PATTERN = Pattern.compile(
            "(?iu)((?:ранг|rank)\\s*[:=\\-»>]?)\\s*([\\[\\]★✦✧⚝⚜\\p{L}\\p{N}_+\\- ]{1,32})");
    public static final Pattern SERVICE_TAG_PATTERN = Pattern.compile(
            "(?iu)^\\[?(?:защита|security|auth|login|регистрация|register|captcha|капча|пароль|password)\\]?$");
    public static final Pattern ANARCHY_PATTERN = Pattern.compile(
            "(?iu)(\\u0430\\u043d\\u0430\\u0440\\u0445\\u0438\\u044f|anarchy)([\\s#\\-]*)(\\d+)");
    public static final Pattern GRIEF_PATTERN = Pattern.compile(
            "(?iu)(\\u0433\\u0440\\u0438\\u0444|grief|grif)([\\s#\\-]*)(\\d+)");
    public static final Pattern KNOWN_DONATE_TOKEN_PATTERN = Pattern.compile(
            "(?iu)(?:" + DONATE_KEYWORDS + ")[\\p{L}\\p{N}_+\\-]*");
    public static final Pattern PLAYER_PLACEHOLDER_PATTERN = Pattern.compile(Pattern.quote("%player%"));
    // Команды авторизации, чей ввод (пароль) маскируется в стример-режиме. Расширенный список:
    // помимо AuthMe /l /login /register /changepassword добавлены /setpass, /2fa, /premium и алиасы.
    // ВАЖНО: сюда попадают ТОЛЬКО парольные команды. /code НЕ включаем — на многих серверах это
    // публичная команда (промо/клан-код), и маскировать её текст не нужно (иначе видно как «цензуру»).
    public static final Pattern SENSITIVE_COMMAND_PATTERN = Pattern.compile(
            "(?i)^\\s*/(?:l|login|log|pas|pass|passwd|password|reg|register|authme|auth"
                    + "|changepassword|changepass|changepasswd|chpass|setpass|setpassword|newpass"
                    + "|resetpass|password2|repeat|confirmpass|2fa|totp|session|premium)(?=\\s|$|[:=])");
    public static final Pattern NICK_LABEL_PATTERN = Pattern.compile(
            "(?iu)(?:\\u043d\\u0438\\u043a|nick|name|player|\\u0438\\u0433\\u0440\\u043e\\u043a)\\s*[:=\\-В»>]");
    public static final Pattern SCOREBOARD_GRIEF_PATTERN = Pattern.compile("(?:ГРИФ-|ГРИФ)");
    public static final Pattern SCOREBOARD_ANARCHY_PATTERN = Pattern.compile("(?:Анархия-|Анархия)");

    private StreamerModePatterns() {
    }
}
