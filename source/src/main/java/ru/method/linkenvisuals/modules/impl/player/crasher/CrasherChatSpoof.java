package ru.method.linkenvisuals.modules.impl.player.crasher;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import ru.method.linkenvisuals.api.mixins.accessors.IClientPlayNetworkHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Фейковая «реакция сервера» на локальные правки мира: через несколько секунд после {@code //set}
 * или {@code //sphere} в чат падают 3–4 сообщения от РЕАЛЬНЫХ игроков с сервера (ники берутся из
 * таб-листа) — будто они видят те же лаги/лаву, что и ты.
 *
 * <p>Сообщения рисуются локально в чат-хаде в формате чата FunTime:
 * {@code Ⓖ <писе> [Барон] Ник ⇨ текст}. На сервер ничего не уходит, никто этих строк, кроме тебя,
 * не видит.</p>
 */
public final class CrasherChatSpoof {

    /** Цвета из серверного формата. */
    private static final int COLOR_GLOBE = 0xFF471A;
    private static final int COLOR_BRACKET = 0x8C8C8C;
    private static final int COLOR_CHANNEL = 0xFF6600;
    private static final int COLOR_PREFIX = 0x5CFFE5;
    private static final int COLOR_ARROW = 0xFFFFE6;
    private static final int COLOR_TEXT = 0xD8D8D8;
    private static final int COLOR_WHITE = 0xFFFFFF;

    private static final String CHANNEL = "писе";
    private static final String PREFIX = "Барон";

    /** Реакции «на лаги/лаву», из которых собирается пачка. */
    private static final String[] PHRASES = {
            "чо за лаги",
            "-серв",
            "-серв срочно",
            "серв лег кто ещё видит",
            "на спавне лава чо такое",
            "кто лаву на спавн залил",
            "у меня блоки пропали посреди спавна",
            "админы гляньте спавн",
            "ну и говно",
            "лагает пиздец",
            "я в блоках застрял",
            "чё за камень везде",
            "рестарт делайте",
            "у всех так или у меня одного",
            "спавн снесли что ли"
    };

    /** Минимальная и максимальная задержка до первого сообщения (тики). */
    private static final int FIRST_DELAY_MIN = 100; // 5 сек
    private static final int FIRST_DELAY_MAX = 140; // 7 сек
    /** Пауза между сообщениями пачки (тики). */
    private static final int GAP_MIN = 12;
    private static final int GAP_MAX = 36;

    private final Random random = new Random();
    private final Deque<Pending> queue = new ArrayDeque<>();
    private int timer;

    private record Pending(String nick, String phrase, int delay) {
    }

    public void clear() {
        queue.clear();
        timer = 0;
    }

    /** Планирует пачку из 3–4 сообщений от случайных реальных игроков. */
    public void schedule() {
        List<String> nicks = onlineNicks();
        if (nicks.isEmpty()) {
            return;
        }

        clear();

        int count = 3 + random.nextInt(2); // 3 или 4
        List<String> phrases = new ArrayList<>(List.of(PHRASES));
        Collections.shuffle(phrases, random);
        Collections.shuffle(nicks, random);

        int delay = FIRST_DELAY_MIN + random.nextInt(FIRST_DELAY_MAX - FIRST_DELAY_MIN + 1);
        for (int i = 0; i < count; i++) {
            String nick = nicks.get(i % nicks.size());
            String phrase = phrases.get(i % phrases.size());
            queue.add(new Pending(nick, phrase, delay));
            delay = GAP_MIN + random.nextInt(GAP_MAX - GAP_MIN + 1);
        }

        timer = queue.peek() == null ? 0 : queue.peek().delay();
    }

    /** Тик клиента: отсчитывает задержки и печатает готовые сообщения. */
    public void tick() {
        if (queue.isEmpty()) {
            return;
        }
        if (--timer > 0) {
            return;
        }

        Pending pending = queue.poll();
        if (pending != null) {
            CrasherMsg.send(format(pending.nick(), pending.phrase()));
        }

        Pending next = queue.peek();
        timer = next == null ? 0 : next.delay();
    }

    /** Ники реальных игроков сервера (таб-лист), кроме своего. */
    private static List<String> onlineNicks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<String> nicks = new ArrayList<>();
        if (mc.getNetworkHandler() == null || mc.player == null) {
            return nicks;
        }

        for (PlayerListEntry entry : ((IClientPlayNetworkHandler) mc.getNetworkHandler())
                .linkenvisuals$getPlayerListEntries().values()) {
            if (entry == null || entry.getProfile() == null) continue;
            String name = entry.getProfile().name();
            if (name == null || name.isBlank()) continue;
            if (name.equals(mc.player.getGameProfile().name())) continue;
            nicks.add(name);
        }
        return nicks;
    }

    /** {@code Ⓖ <писе> [Барон] Ник ⇨ текст} — один в один формат серверного чата. */
    private static MutableText format(String nick, String phrase) {
        MutableText text = Text.literal("Ⓖ ").setStyle(colored(COLOR_GLOBE));
        text.append(Text.literal("<").setStyle(colored(COLOR_BRACKET)));
        text.append(Text.literal(CHANNEL).setStyle(colored(COLOR_CHANNEL)));
        text.append(Text.literal("> ").setStyle(colored(COLOR_BRACKET)));
        text.append(Text.literal("[").setStyle(colored(COLOR_WHITE)));
        text.append(Text.literal(PREFIX).setStyle(colored(COLOR_PREFIX)));
        text.append(Text.literal("] ").setStyle(colored(COLOR_WHITE)));
        text.append(Text.literal(nick).setStyle(colored(COLOR_WHITE)));
        text.append(Text.literal(" ⇨ ").setStyle(colored(COLOR_ARROW)));
        text.append(Text.literal(phrase).setStyle(colored(COLOR_TEXT).withItalic(true)));
        return text;
    }

    private static Style colored(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
    }
}
