package ru.method.linkenvisuals.modules.settings.impl;


import com.google.gson.JsonObject;
import ru.method.linkenvisuals.modules.settings.Setting;
import lombok.Getter;

import java.util.Objects;
import java.util.function.Supplier;


@Getter
public class StringSetting extends Setting<String> {
    private final String description;
    private final int maxLength;
    private String value;
    private String placeholder;
    private final ChangeListener listener;

    public StringSetting(String name, String defaultValue, String placeholder) {
        this(name, defaultValue, placeholder, "", 64, null, null);
    }

    public StringSetting(String name, String defaultValue, String placeholder, Supplier<Boolean> visible) {
        this(name, defaultValue, placeholder, "", 64, visible, null);
    }

    public StringSetting(String name, String defaultValue, String placeholder, String description) {
        this(name, defaultValue, placeholder, description, 64, null, null);
    }

    public StringSetting(String name, String defaultValue, String placeholder, String description, Supplier<Boolean> visible) {
        this(name, defaultValue, placeholder, description, 64, visible, null);
    }

    public StringSetting(String name, String defaultValue, String placeholder, String description, int maxLength, ChangeListener listener) {
        this(name, defaultValue, placeholder, description, maxLength, null, listener);
    }

    public StringSetting(String name, String defaultValue, String placeholder, String description, int maxLength, Supplier<Boolean> visible, ChangeListener listener) {
        super(name);
        this.placeholder = placeholder;
        this.description = Objects.requireNonNullElse(description, "");
        this.maxLength = Math.max(1, maxLength);
        this.listener = listener;
        this.value = sanitize(defaultValue);
        this.defaultValue = this.value;
        super.value = this.value;
        if (visible != null) {
            setVisible(visible);
        }
    }

    @Override
    public void setValue(String newValue) {
        String sanitized = sanitize(newValue);
        if (sanitized.equals(this.value)) {
            return;
        }
        String old = this.value;
        this.value = sanitized;
        super.value = sanitized;
        if (listener != null) {
            listener.onChange(old, sanitized);
        }
    }

    private String sanitize(String raw) {
        String source = Objects.requireNonNullElse(raw, "");
        StringBuilder builder = new StringBuilder(Math.min(source.length(), maxLength));
        for (int i = 0; i < source.length() && builder.length() < maxLength; i++) {
            char c = source.charAt(i);
            if (c == '§') {
                // § храним как & (парсеры понимают оба, а глифа § в MSDF-шрифте нет — в поле он невидим)
                builder.append('&');
                continue;
            }
            if (isAllowedInputChar(c)) {
                builder.append(c);
            }
        }
        String safe = builder.toString();
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        return safe;
    }

    private boolean isAllowedInputChar(char c) {
        if (c >= 'A' && c <= 'Z') return true;
        if (c >= 'a' && c <= 'z') return true;
        if (c >= 'А' && c <= 'я') return true;
        if (c == 'Ё' || c == 'ё') return true;
        if (c >= '0' && c <= '9') return true;
        return c == ' ' || c == '/' || c == '\\' || c == '_' || c == '-' || c == '.'
                || c == ',' || c == ':' || c == ';' || c == '!' || c == '?' || c == '#'
                || c == '&' || c == '[' || c == ']' || c == '(' || c == ')' || c == '<'
                || c == '>' || c == '+' || c == '=' || c == '*' || c == '\'' || c == '"';
    }

    @Override
    public void reset() {
        setValue(defaultValue);
    }

    @Override
    public void safe(JsonObject propertiesObject) {
        propertiesObject.addProperty(String.valueOf(name), value);
    }

    @Override
    public void load(JsonObject propertiesObject) {
        if (!propertiesObject.has(String.valueOf(name))) {
            return;
        }
        setValue(propertiesObject.get(String.valueOf(name)).getAsString());
    }

    public interface ChangeListener {
        void onChange(String oldValue, String newValue);
    }
}
