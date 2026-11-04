package ru.method.linkenvisuals.modules.impl.player.crasher;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Хелперы для клиентских сообщений. */
public final class CrasherMsg {

    private CrasherMsg() {
    }

    public static void send(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(text);
        }
    }

    public static MutableText lit(String s, Formatting... f) {
        return Text.literal(s).formatted(f);
    }

    /** Стиль WorldEdit/FAWE: светло-серый текст с подсветкой. */
    public static void we(String message) {
        send(lit(message, Formatting.LIGHT_PURPLE));
    }

    public static void weGray(String message) {
        send(lit(message, Formatting.GRAY));
    }

    public static void error(String message) {
        send(lit(message, Formatting.RED));
    }

    /** "1234 blocks have been changed. (0.021s, 58761 blocks/sec)" */
    public static void weAffected(long blocks, long nanos) {
        double secs = Math.max(nanos / 1_000_000_000.0D, 0.001D);
        long perSec = (long) (blocks / secs);
        MutableText t = lit(blocks + " blocks have been changed.", Formatting.LIGHT_PURPLE);
        t.append(lit(String.format(" (%.3fs, %d blocks/sec)", secs, perSec), Formatting.GRAY));
        send(t);
    }
}
