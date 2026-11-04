package ru.method.linkenvisuals.modules.api;


import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter

public enum Category {
    Combat("P"),
    Movement("S"),
    Visuals("R"),   // была "U" → получила иконку Other ("R")
    Player("V"),    // рендерится текстурой-аватаром (глиф не используется)
    Other("V"),     // была "R" → получила иконку Player ("V")
    Hud("H");

    private final String icon;
}
