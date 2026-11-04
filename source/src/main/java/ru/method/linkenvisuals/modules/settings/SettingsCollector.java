package ru.method.linkenvisuals.modules.settings;

import ru.method.linkenvisuals.LinkenVisuals;

import java.lang.reflect.Field;
import java.util.List;

public final class SettingsCollector {

    private SettingsCollector() {
    }

    public static void collectDeclaredSettings(Object owner, List<Setting<?>> target, String ownerLabel) {
        if (owner == null || target == null) {
            return;
        }

        for (Field field : owner.getClass().getDeclaredFields()) {
            if (!Setting.class.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Setting<?> setting = (Setting<?>) field.get(owner);
                if (setting != null && !target.contains(setting)) {
                    target.add(setting);
                }
            } catch (ReflectiveOperationException exception) {
                LinkenVisuals.LOGGER.warn(
                        "[Settings] Failed to read setting {}#{}",
                        ownerLabel,
                        field.getName(),
                        exception
                );
            }
        }
    }
}
