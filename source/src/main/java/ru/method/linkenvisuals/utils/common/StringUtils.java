package ru.method.linkenvisuals.utils.common;

public final class StringUtils {

    private StringUtils() {
    }

    public static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
