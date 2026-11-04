package ru.method.linkenvisuals.managers.misc;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.macro.Macro;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;


public class MacroManager implements Wrapper {

    public MacroManager() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
    }

    @Getter private final List<Macro> macros = new ArrayList<>();
    @Getter private final List<String> names = new ArrayList<>();

    public void add(Macro macro) {
        macros.add(macro);
        names.add(macro.getName());
    }

    public void remove(Macro macro) {
        macros.remove(macro);
        names.remove(macro.getName());
    }

    public void clear() {
        if (!macros.isEmpty()) macros.clear();
        if (!names.isEmpty()) names.clear();
    }

    public boolean isEmpty() {
        return macros.isEmpty() || names.isEmpty();
    }

    public Macro getMacro(String name) {
        for (Macro macro : macros) {
            if (!macro.getName().equalsIgnoreCase(name)) continue;
            return macro;
        }
        
        return null;
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (Module.fullNullCheck() || mc.currentScreen != null || LinkenVisuals.getInstance().isPanic() || macros.isEmpty()) return;

        if (e.getAction() == 1)
            for (Macro macro : macros)
                if (macro.getBind().matchesKey(e.getKey())) {
                    if (macro.getCommand().startsWith("/")) mc.player.networkHandler.sendChatCommand(macro.getCommand().replace("/", ""));
                    else mc.player.networkHandler.sendChatMessage(macro.getCommand());
                }
    }
}
