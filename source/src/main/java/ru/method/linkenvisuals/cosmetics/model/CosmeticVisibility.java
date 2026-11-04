package ru.method.linkenvisuals.cosmetics.model;

import ru.method.linkenvisuals.modules.settings.api.Nameable;

/** Чью косметику показывать: всех синхронизированных / только друзей / только участников пати / только свою. */
public enum CosmeticVisibility implements Nameable {
    EVERYONE("settings.cosmetics.visibility.everyone"),
    FRIENDS("settings.cosmetics.visibility.friends"),
    PARTY("settings.cosmetics.visibility.party"),
    SELF_ONLY("settings.cosmetics.visibility.self");

    private final String name;

    CosmeticVisibility(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
