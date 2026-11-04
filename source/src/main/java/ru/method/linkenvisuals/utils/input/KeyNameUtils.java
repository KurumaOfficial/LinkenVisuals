package ru.method.linkenvisuals.utils.input;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.modules.settings.api.Bind;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KeyNameUtils {
    private static final Map<String, String> SYMBOL_TO_GLFW = new LinkedHashMap<>();
    private static final Map<String, String> GLFW_TO_SYMBOL = new LinkedHashMap<>();

    static {
        alias("'", "APOSTROPHE");
        alias(";", "SEMICOLON");
        alias("[", "LEFT_BRACKET");
        alias("]", "RIGHT_BRACKET");
        alias("/", "SLASH");
        alias(".", "PERIOD");
        alias(",", "COMMA");
        alias("\\", "BACKSLASH");
        alias("-", "MINUS");
        alias("=", "EQUAL");
        alias("`", "GRAVE_ACCENT");
        alias("!", "1");
        alias("@", "2");
        alias("#", "3");
        alias("$", "4");
        alias("%", "5");
        alias("^", "6");
        alias("&", "7");
        alias("*", "8");
        alias("(", "9");
        alias(")", "0");
        alias("_", "MINUS");
        alias("+", "EQUAL");
    }

    private KeyNameUtils() {}

    public static Bind parseBind(String raw) {
        if (raw == null) return null;
        String token = normalizeToken(raw);
        if (token.isEmpty()) return null;
        if ("NONE".equals(token)) return new Bind(-1, false);
        if ("WHEELUP".equals(token) || "SCROLLUP".equals(token) || "WHEEL_UP".equals(token)) return new Bind(Bind.MOUSE_SCROLL_UP, true);
        if ("WHEELDOWN".equals(token) || "SCROLLDOWN".equals(token) || "WHEEL_DOWN".equals(token)) return new Bind(Bind.MOUSE_SCROLL_DOWN, true);

        String mouseNumber = null;
        if (token.startsWith("MOUSE")) mouseNumber = token.substring(5);
        else if (token.startsWith("MB")) mouseNumber = token.substring(2);
        else if (token.startsWith("M")) mouseNumber = token.substring(1);

        if (mouseNumber != null) {
            try {
                int button = Integer.parseInt(mouseNumber);
                if (button >= 1) return new Bind(button - 1, true);
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            int key = GLFW.class.getField("GLFW_KEY_" + token).getInt(null);
            return new Bind(key, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String formatBind(Bind bind) {
        if (bind == null || bind.isEmpty()) return "None";
        if (bind.isMouse()) {
            if (bind.getKey() == Bind.MOUSE_SCROLL_UP) return "Wheel Up";
            if (bind.getKey() == Bind.MOUSE_SCROLL_DOWN) return "Wheel Down";
            return "M" + (bind.getKey() + 1);
        }
        String glfwName = glfwKeyName(bind.getKey());
        if (glfwName != null) return displayName(glfwName);
        try {
            InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(bind.getKey());
            return key.getLocalizedText().getString();
        } catch (Exception ignored) {
            return String.valueOf(bind.getKey());
        }
    }

    // Кэш канонических токенов бинда. Держим ЕДИНЫЙ источник истины для подсказок: каждый токен здесь
    // гарантированно (а) печатается брайгадировским word() — только [A-Z0-9_], без символов вроде ';'/'!',
    // которые word() отвергал → команда падала в «неизвестный бинд» красным; (б) принимается parseBind.
    private static volatile List<String> bindableTokens;

    private static List<String> bindableTokens() {
        List<String> cached = bindableTokens;
        if (cached != null) return cached;
        List<String> list = new ArrayList<>();
        for (Field field : GLFW.class.getDeclaredFields()) {
            String name = field.getName();
            if (!name.startsWith("GLFW_KEY_")) continue;
            String glfwName = name.substring("GLFW_KEY_".length());
            if (isHidden(glfwName)) continue;
            // Каноническое GLFW-имя ([A-Z0-9_]) — word-safe и принимается parseBind через getField.
            list.add(glfwName.toUpperCase(Locale.ROOT));
        }
        // Мышь/колесо/none — все формы, что понимает parseBind.
        for (int i = 1; i <= 8; i++) {
            list.add("MOUSE" + i);
            list.add("M" + i);
        }
        list.add("WHEELUP");
        list.add("WHEELDOWN");
        list.add("SCROLLUP");
        list.add("SCROLLDOWN");
        list.add("NONE");
        cached = list.stream().distinct().sorted().toList();
        bindableTokens = cached;
        return cached;
    }

    public static List<String> suggestions(String remaining) {
        String upper = (remaining == null ? "" : remaining.trim()).toUpperCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String token : bindableTokens()) {
            if (token.startsWith(upper)) result.add(token);
        }
        return result;
    }

    public static String normalizeKeyName(String raw) {
        return normalizeToken(raw);
    }

    private static void alias(String symbol, String glfw) {
        SYMBOL_TO_GLFW.put(symbol, glfw);
        GLFW_TO_SYMBOL.putIfAbsent(glfw, symbol);
    }

    private static String normalizeToken(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        String alias = SYMBOL_TO_GLFW.get(trimmed);
        if (alias != null) return alias;
        return trimmed.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String displayName(String glfwName) {
        if (glfwName == null || glfwName.isBlank()) return "NONE";
        String symbol = GLFW_TO_SYMBOL.get(glfwName.toUpperCase(Locale.ROOT));
        if (symbol != null) return symbol;
        return glfwName.toUpperCase(Locale.ROOT);
    }

    private static boolean isHidden(String glfwName) {
        return glfwName.equalsIgnoreCase("UNKNOWN")
                || glfwName.equalsIgnoreCase("WORLD_1")
                || glfwName.equalsIgnoreCase("WORLD_2");
    }

    private static String glfwKeyName(int keyCode) {
        for (Field field : GLFW.class.getDeclaredFields()) {
            String name = field.getName();
            if (!name.startsWith("GLFW_KEY_")) continue;
            try {
                if (field.getInt(null) == keyCode) return name.substring("GLFW_KEY_".length());
            } catch (IllegalAccessException ignored) {
            }
        }
        return null;
    }
}
