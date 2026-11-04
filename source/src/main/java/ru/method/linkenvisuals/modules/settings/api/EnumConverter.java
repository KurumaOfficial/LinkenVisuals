package ru.method.linkenvisuals.modules.settings.api;


import lombok.experimental.UtilityClass;

@UtilityClass

public class EnumConverter {

    public <T extends Enum<T>> int currentEnum(T clazz) {
        T[] constants = clazz.getDeclaringClass().getEnumConstants();
        for (int i = 0; i < constants.length; ++i) {
            T e = constants[i];
            if (!e.name().equalsIgnoreCase(clazz.name())) continue;
            return i;
        }
        return -1;
    }

    public <T extends Enum<T>> T increaseEnum(T clazz) {
        T[] constants = clazz.getDeclaringClass().getEnumConstants();
        int index = EnumConverter.currentEnum(clazz);
        for (int i = 0; i < constants.length; ++i) {
            T e = constants[i];
            if (i != index + 1) continue;
            return e;
        }

        return constants[0];
    }
}
