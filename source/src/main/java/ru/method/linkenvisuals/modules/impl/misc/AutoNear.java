package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;

public class AutoNear extends Module {
    private final NumberSetting delay = new NumberSetting("modules.settings.auto_near.delay", 60f, 1f, 180f, 1f);
    private final StringSetting amount = new StringSetting("modules.settings.auto_near.amount", "", "100 / 500 / max");
    private long nextSendMs;

    public AutoNear() {
        super("AutoNear", Category.Player);
    }

    @Override
    public void onEnable() {
        nextSendMs = 0L;
        super.onEnable();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck() || mc.player.networkHandler == null) return;
        long now = System.currentTimeMillis();
        if (now < nextSendMs) return;

        String amount = resolveAmount();
        mc.player.networkHandler.sendChatCommand(amount.isBlank() ? "near" : "near " + amount);
        nextSendMs = now + (long) (delay.getValue() * 1000L);
    }

    private String resolveAmount() {
        String value = amount.getValue() == null ? "" : amount.getValue().trim();
        if (value.isBlank()) return "";
        if (value.equalsIgnoreCase("max")) return "max";
        if (value.matches("\\d{1,7}")) return value;
        return "";
    }
}
