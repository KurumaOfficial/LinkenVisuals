package ru.method.linkenvisuals.utils.text;

import java.util.regex.Pattern;

/**
 * Удаление legacy-кодов форматирования Minecraft из строки: hex ({@code &x&r&r...}, {@code §x§r...}),
 * амперсанд-коды ({@code &a}, {@code &l}…) и §-коды ({@code §a}, {@code §l}…). Единая копия — раньше
 * этот же парсер (с идентичными 4 паттернами) был скопирован в PotionHud и PotionTracker.
 */
public final class ColorCodes {

    private static final Pattern HEX_AMP = Pattern.compile("(?i)&x(?:&[0-9a-f]){6}");
    private static final Pattern HEX_SECTION = Pattern.compile("(?i)§x(?:§[0-9a-f]){6}");
    private static final Pattern AMP_COLOR = Pattern.compile("(?i)&[0-9a-fklmnor]");
    private static final Pattern SECTION_COLOR = Pattern.compile("(?i)§[0-9a-fklmnor]");

    private ColorCodes() {
    }

    /** Снимает все коды форматирования и обрезает пробелы. {@code null}/пусто → {@code ""}. */
    public static String strip(String text) {
        if (text == null || text.isEmpty()) return "";
        String cleaned = HEX_AMP.matcher(text).replaceAll("");
        cleaned = HEX_SECTION.matcher(cleaned).replaceAll("");
        cleaned = AMP_COLOR.matcher(cleaned).replaceAll("");
        cleaned = SECTION_COLOR.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }
}
