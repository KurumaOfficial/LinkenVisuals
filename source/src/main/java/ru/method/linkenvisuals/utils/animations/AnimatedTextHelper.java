package ru.method.linkenvisuals.utils.animations;

public final class AnimatedTextHelper {

    private AnimatedTextHelper() {}

    public static int getPrefixLength(String previous, String current) {
        int max = Math.min(previous.length(), current.length());
        int index = 0;
        while (index < max && previous.charAt(index) == current.charAt(index)) {
            index++;
        }
        return index;
    }

    public static int getSuffixLength(String previous, String current, int prefixLength) {
        int prevLen = previous.length();
        int currLen = current.length();
        int maxSuffix = Math.min(prevLen - prefixLength, currLen - prefixLength);
        int count = 0;
        while (count < maxSuffix &&
                previous.charAt(prevLen - 1 - count) == current.charAt(currLen - 1 - count)) {
            count++;
        }
        return count;
    }

    public static String substringSafe(String source, int start, int end) {
        if (source == null) return "";
        int safeStart = Math.max(0, Math.min(start, source.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, source.length()));
        return source.substring(safeStart, safeEnd);
    }
}
