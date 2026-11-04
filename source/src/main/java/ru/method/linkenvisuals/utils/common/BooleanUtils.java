package ru.method.linkenvisuals.utils.common;

import java.util.Locale;

public final class BooleanUtils {

    private BooleanUtils() {
    }

    public static boolean parseBoolean(String raw, boolean fallback) {
        String value = StringUtils.safeTrim(raw).toLowerCase(Locale.ROOT);
        if (value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on")) {
            return true;
        }
        if (value.equals("false") || value.equals("0") || value.equals("no") || value.equals("off")) {
            return false;
        }
        return fallback;
    }
}
