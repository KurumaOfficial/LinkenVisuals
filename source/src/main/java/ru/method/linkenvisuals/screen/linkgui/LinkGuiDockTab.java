package ru.method.linkenvisuals.screen.linkgui;

import ru.method.linkenvisuals.modules.api.Category;

public enum LinkGuiDockTab {
    COMBAT(Category.Combat, "P", "Combat"),
    PLAYER(Category.Player, "V", "Player"),      // иконка-аватар (текстура), глиф не используется
    MOVEMENT(Category.Movement, "S", "Move"),
    VISUALS(Category.Visuals, "R", "Visuals"),   // была "U" → "R"
    MORE(Category.Other, "V", "Other");          // была "R" → "V"

    private final Category category;
    private final String icon;
    private final String label;

    LinkGuiDockTab(Category category, String icon, String label) {
        this.category = category;
        this.icon = icon;
        this.label = label;
    }

    public Category category() {
        return category;
    }

    public String icon() {
        return icon;
    }

    public String label() {
        return label;
    }
}
