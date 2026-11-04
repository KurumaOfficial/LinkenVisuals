package ru.method.linkenvisuals.utils.macro;

import ru.method.linkenvisuals.modules.settings.api.Bind;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter
public class Macro {
    private String name, command;
    private Bind bind;
}
