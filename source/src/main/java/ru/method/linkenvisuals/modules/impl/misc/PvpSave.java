package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.server.Server;

import java.util.List;
import java.util.Locale;

/**
 * Защита от случайного выхода в режиме боя (КТ): блокирует /hub|/lobby|/spawn пока идёт бой, требует
 * повторного /hub в течение 5 секунд.
 *
 * <p>Детект КТ — дедлайн-автомат: сканируем ВСЕ боссбары (а не первый), парсим секунды таймера и держим
 * состояние по дедлайну. Состояние не сбрасывается мгновенно при пропаже бара на тик и не сбрасывается
 * рано на «1 секунде» — только по честному нулю таймера, явному сообщению о конце боя или дедлайну.</p>
 */
public class PvpSave extends Module {
    private static final List<String> END_TOKENS = List.of(
            "режим боя окончен", "режим боя законч", "вы вышли из режима боя",
            "pvp ended", "combat ended"
    );
    private static final long HOLD_GRACE_MS = 2500L;  // держим состояние ещё чуть после пропажи бара
    private static final long CHAT_HOLD_MS = 8000L;   // окно, если КТ выставлен только из чата (без бара)

    private long pvpUntilMs = 0L;
    private long hubConfirmUntilMs = -1L;

    public PvpSave() {
        super("PvpSave", Category.Player);
    }

    @Override
    public void onDisable() {
        clear();
        super.onDisable();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            clear();
            return;
        }
        refreshBossBarState();
    }

    @EventHandler
    private void onPacketReceive(EventPacket.Receive event) {
        if (event.getPacket() instanceof GameMessageS2CPacket packet) {
            handleCombatText(packet.content().getString());
        }
    }

    public void handleChatMessage(String message) {
        handleCombatText(message);
    }

    public boolean shouldBlockHubCommand(String command) {
        refreshBossBarState();
        if (!isPvpNow() || command == null) return false;
        String normalized = normalizeCommand(command);
        if (!isHubCommand(normalized)) return false;

        long now = System.currentTimeMillis();
        if (hubConfirmUntilMs > now) {
            hubConfirmUntilMs = -1L;
            return false;
        }

        hubConfirmUntilMs = now + 5000L;
        ChatUtils.sendMessage("Ты сейчас в режиме боя. Напиши /hub ещё раз в течение 5 секунд, чтобы выйти.");
        return true;
    }

    private void refreshBossBarState() {
        if (fullNullCheck()) return;
        if (Server.isPvpAny()) {
            long now = System.currentTimeMillis();
            int secs = Server.getPvpSeconds();
            if (secs == 0) {
                // Честный ноль на таймере — бой закончился.
                pvpUntilMs = 0L;
            } else if (secs > 0) {
                pvpUntilMs = now + secs * 1000L + HOLD_GRACE_MS;
            } else {
                // Боевой бар есть, но таймер не распознан — держим по грейсу.
                pvpUntilMs = Math.max(pvpUntilMs, now + HOLD_GRACE_MS);
            }
        }
        // Бар пропал — состояние НЕ сбрасываем мгновенно, держим по дедлайну pvpUntilMs.
    }

    private void handleCombatText(String message) {
        if (message == null || message.isBlank()) return;

        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, END_TOKENS)) {
            clear();
            return;
        }
        // Ранний сброс на «0/1 сек» убран — на 1 секунде ты ещё в КТ.
        if (Server.isPvpText(message)) {
            long now = System.currentTimeMillis();
            pvpUntilMs = Math.max(pvpUntilMs, now + CHAT_HOLD_MS);
        }
    }

    private boolean isPvpNow() {
        return System.currentTimeMillis() < pvpUntilMs;
    }

    private boolean containsAny(String lower, List<String> tokens) {
        for (String token : tokens) {
            if (lower.contains(token)) return true;
        }
        return false;
    }

    private boolean isHubCommand(String command) {
        return command.equals("hub") || command.startsWith("hub ")
                || command.equals("lobby") || command.startsWith("lobby ")
                || command.equals("spawn") || command.startsWith("spawn ");
    }

    private String normalizeCommand(String command) {
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1).trim();
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private void clear() {
        pvpUntilMs = 0L;
        hubConfirmUntilMs = -1L;
    }
}
