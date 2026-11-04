package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.server.Server;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Автоответ на входящие личные сообщения. Детект формата ЛС зашит в код (regex),
 * потому что {@code StringSetting} вырезает часть regex-символов ({@code ^ $ | { }}).
 * Ответ отправляется реальной командой на сервер (по умолчанию {@code /r <text>}).
 */
public class AutoReply extends Module {

    // Типичные форматы ЛС на FunTime-подобных серверах: "Nick -> Вам: текст", "Nick → Вам » текст",
    // "[тег] Nick >> Вам: текст". Группа 1 — ник отправителя.
    private static final Pattern PM_PATTERN = Pattern.compile(
            "([A-Za-z\\u0410-\\u044f\\u0401\\u04510-9_]{2,16})\\s*(?:->|-->|\\u2192|\\u00bb|>>|\\u27a4)\\s*"
                    + "(?:\\u0432\\u0430\\u043c|\\u043c\\u043d\\u0435|\\u0442\\u0435\\u0431\\u0435|you|me)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final StringSetting replyCommand = new StringSetting(
            "settings.autoreply.command", "/r", "/r  |  /msg <player>");
    private final StringSetting replyText = new StringSetting(
            "settings.autoreply.text", "АФК, отвечу позже", "");
    private final NumberSetting cooldownSeconds = new NumberSetting(
            "settings.autoreply.cooldown", 30f, 3f, 300f, 1f);
    private final BooleanSetting onlyOnFuntime = new BooleanSetting(
            "settings.autoreply.only_funtime", true);
    private final BooleanSetting notifyLocally = new BooleanSetting(
            "settings.autoreply.notify", true);

    private final Map<String, Long> lastReplyBySender = new HashMap<>();
    private long lastGlobalReplyMs = -1L;

    public AutoReply() {
        super("AutoReply", Category.Player);
        getSettings().add(replyCommand);
        getSettings().add(replyText);
        getSettings().add(cooldownSeconds);
        getSettings().add(onlyOnFuntime);
        getSettings().add(notifyLocally);
    }

    @Override
    public void onDisable() {
        lastReplyBySender.clear();
        lastGlobalReplyMs = -1L;
        super.onDisable();
    }

    @EventHandler
    private void onPacketReceive(EventPacket.Receive event) {
        if (fullNullCheck()) return;
        if (!(event.getPacket() instanceof GameMessageS2CPacket packet)) return;
        if (onlyOnFuntime.getValue() && !Server.isFuntime()) return;

        String content = packet.content().getString();
        if (content == null || content.isBlank()) return;

        String sender = extractSender(content);
        if (sender == null) return;

        // Не отвечать на собственные сообщения/эхо.
        String selfName = mc.player.getName().getString();
        if (sender.equalsIgnoreCase(selfName)) return;

        long now = System.currentTimeMillis();
        long cooldownMs = (long) (cooldownSeconds.getValue() * 1000f);

        // Глобальный анти-спам: не чаще раза в секунду вообще.
        if (lastGlobalReplyMs > 0 && now - lastGlobalReplyMs < 1000L) return;

        Long last = lastReplyBySender.get(sender.toLowerCase(Locale.ROOT));
        if (last != null && now - last < cooldownMs) return;

        sendReply(sender);
        lastReplyBySender.put(sender.toLowerCase(Locale.ROOT), now);
        lastGlobalReplyMs = now;
    }

    private String extractSender(String content) {
        Matcher matcher = PM_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void sendReply(String sender) {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return;

        String command = replyCommand.getValue().replace("<player>", sender).trim();
        String text = replyText.getValue().trim();
        if (command.isEmpty() && text.isEmpty()) return;

        String full = command.isEmpty() ? text : (text.isEmpty() ? command : command + " " + text);

        if (full.startsWith("/")) {
            String body = full.substring(1).trim();
            if (!body.isEmpty()) {
                handler.sendChatCommand(body);
            }
        } else {
            handler.sendChatMessage(full);
        }

        if (notifyLocally.getValue()) {
            ChatUtils.sendMessage("AutoReply → " + sender);
        }
    }
}
