package ru.method.linkenvisuals.modules.impl.player.crasher;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import ru.method.linkenvisuals.modules.impl.player.Crasher;

import java.util.Locale;

/**
 * Клиентские «фейковые» команды модуля {@link Crasher}. Ни одна из них не уходит на сервер —
 * весь эффект и все сообщения существуют только в этом клиенте, и всё откатывается при
 * выключении модуля.
 *
 * <p>Команда включения/выключения из оригинального мода убрана: режим включается самим модулем.</p>
 */
public final class CrasherCommands {

    private final Crasher owner;

    private BlockPos pos1;
    private BlockPos pos2;

    public CrasherCommands(Crasher owner) {
        this.owner = owner;
    }

    /** Левый клик топориком. */
    public void setPos1(BlockPos pos) {
        pos1 = pos.toImmutable();
        CrasherMsg.we("First position set to (" + pos1.getX() + ", " + pos1.getY() + ", " + pos1.getZ() + ")"
                + volumeSuffix() + ".");
    }

    /** Правый клик топориком. */
    public void setPos2(BlockPos pos) {
        pos2 = pos.toImmutable();
        CrasherMsg.we("Second position set to (" + pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ() + ")"
                + volumeSuffix() + ".");
    }

    private String volumeSuffix() {
        if (pos1 == null || pos2 == null) {
            return "";
        }
        long volume = (long) (Math.abs(pos1.getX() - pos2.getX()) + 1)
                * (Math.abs(pos1.getY() - pos2.getY()) + 1)
                * (Math.abs(pos1.getZ() - pos2.getZ()) + 1);
        return " (" + volume + " blocks)";
    }

    public void clearSelection() {
        pos1 = null;
        pos2 = null;
    }

    /**
     * @param command команда без ведущего слэша (то, что уходит в CommandExecutionC2SPacket)
     * @return true, если команду перехватили и на сервер её отправлять не нужно
     */
    public boolean handle(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return false;
        }

        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String[] parts = trimmed.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);

        switch (name) {
            case "op" -> {
                return fakeOp(player, parts, true);
            }
            case "deop" -> {
                return fakeOp(player, parts, false);
            }
            case "gm", "gamemode" -> {
                return fakeGameMode(mc, parts);
            }
            case "kickall" -> {
                return fakeKickAll();
            }
            case "broadcast", "bc" -> {
                return fakeBroadcast(player, trimmed, parts[0].length());
            }
            case "/wand" -> {
                owner.giveWand();
                CrasherMsg.we("Left click: select pos #1; Right click: select pos #2");
                CrasherMsg.weGray("(Use //sel to switch modes)");
                return true;
            }
            case "/desel", "/sel" -> {
                clearSelection();
                CrasherMsg.we("Selection cleared.");
                return true;
            }
            case "/pos1" -> {
                setPos1(player.getBlockPos());
                return true;
            }
            case "/pos2" -> {
                setPos2(player.getBlockPos());
                return true;
            }
            case "/set" -> {
                return fakeSet(parts);
            }
            case "/sphere" -> {
                return fakeSphere(player, parts, false);
            }
            case "/hsphere" -> {
                return fakeSphere(player, parts, true);
            }
            case "/undo" -> {
                CrasherMsg.we("Undo successful.");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ----------------------------------------------------------- vanilla

    private boolean fakeOp(ClientPlayerEntity player, String[] parts, boolean op) {
        String target = parts.length > 1 ? parts[1] : player.getGameProfile().name();
        owner.setFakeOp(op);
        CrasherMsg.send(CrasherMsg.lit("Made " + target + (op ? " a server operator" : " no longer a server operator"),
                Formatting.WHITE));
        return true;
    }

    private boolean fakeGameMode(MinecraftClient mc, String[] parts) {
        if (parts.length < 2) {
            CrasherMsg.error("Incorrect argument for command");
            return true;
        }
        GameMode mode = parseGameMode(parts[1]);
        if (mode == null) {
            CrasherMsg.error("Unknown game mode: " + parts[1]);
            return true;
        }
        if (mc.interactionManager != null) {
            mc.interactionManager.setGameMode(mode);
        }
        if (mc.player != null) {
            mc.player.getAbilities().allowFlying = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
            if (!mc.player.getAbilities().allowFlying) {
                mc.player.getAbilities().flying = false;
            }
        }
        String label = translateMode(mode);
        CrasherMsg.send(CrasherMsg.lit("Set own game mode to " + label, Formatting.WHITE));
        CrasherMsg.send(CrasherMsg.lit("Your game mode has been updated to " + label, Formatting.WHITE));
        return true;
    }

    private static GameMode parseGameMode(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static String translateMode(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "Survival Mode";
            case CREATIVE -> "Creative Mode";
            case ADVENTURE -> "Adventure Mode";
            case SPECTATOR -> "Spectator Mode";
        };
    }

    private boolean fakeKickAll() {
        int kicked = owner.hideRealPlayers();
        CrasherMsg.send(CrasherMsg.lit("Kicked " + kicked + " player" + (kicked == 1 ? "" : "s") + " from the server",
                Formatting.WHITE));
        return true;
    }

    /**
     * &lt;green&gt;⦏&lt;b&gt;&lt;dark_red&gt;Объявление&lt;green&gt;⦐&lt;gold&gt; текст
     * &lt;em&gt;&lt;white&gt;[Автор: &lt;yellow&gt;ник&lt;white&gt;]
     */
    private boolean fakeBroadcast(ClientPlayerEntity player, String full, int nameLength) {
        String message = full.length() > nameLength ? full.substring(nameLength).trim() : "";
        if (message.isEmpty()) {
            CrasherMsg.error("Использование: /broadcast <сообщение>");
            return true;
        }
        String author = player.getGameProfile().name();

        MutableText text = Text.literal("⦏").formatted(Formatting.GREEN)
                .append(Text.literal("Объявление").formatted(Formatting.DARK_RED, Formatting.BOLD))
                .append(Text.literal("⦐").formatted(Formatting.GREEN))
                .append(Text.literal(" " + message + " ").formatted(Formatting.GOLD))
                .append(Text.literal("[Автор: ").formatted(Formatting.WHITE, Formatting.ITALIC))
                .append(Text.literal(author).formatted(Formatting.YELLOW, Formatting.ITALIC))
                .append(Text.literal("]").formatted(Formatting.WHITE, Formatting.ITALIC));

        CrasherMsg.send(text);
        return true;
    }

    // --------------------------------------------------------- worldedit

    private boolean fakeSet(String[] parts) {
        if (parts.length < 2) {
            CrasherMsg.error("Too few arguments.");
            CrasherMsg.weGray("Usage: //set <pattern>");
            return true;
        }
        if (pos1 == null || pos2 == null) {
            CrasherMsg.error("Make a region selection first.");
            return true;
        }
        BlockState state = parseBlock(parts[1]);
        if (state == null) {
            CrasherMsg.error("Block not found: " + parts[1]);
            return true;
        }

        long start = System.nanoTime();
        long changed = 0;
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    owner.setFakeBlock(cursor, state);
                    changed++;
                }
            }
        }

        CrasherMsg.weAffected(changed, System.nanoTime() - start);
        owner.scheduleFakeChatReaction();
        return true;
    }

    private boolean fakeSphere(ClientPlayerEntity player, String[] parts, boolean hollow) {
        if (parts.length < 3) {
            CrasherMsg.error("Too few arguments.");
            CrasherMsg.weGray("Usage: " + (hollow ? "//hsphere" : "//sphere") + " <pattern> <radius>");
            return true;
        }
        BlockState state = parseBlock(parts[1]);
        if (state == null) {
            CrasherMsg.error("Block not found: " + parts[1]);
            return true;
        }
        double radius;
        try {
            radius = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            CrasherMsg.error("Expected a number; got '" + parts[2] + "'");
            return true;
        }
        if (radius <= 0 || radius > 128) {
            CrasherMsg.error("Radius must be between 1 and 128.");
            return true;
        }

        long start = System.nanoTime();
        long changed = 0;
        BlockPos center = player.getBlockPos();
        int r = (int) Math.ceil(radius);
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > radius) {
                        continue;
                    }
                    if (hollow && dist < radius - 1.0D) {
                        continue;
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    owner.setFakeBlock(cursor, state);
                    changed++;
                }
            }
        }

        CrasherMsg.weAffected(changed, System.nanoTime() - start);
        owner.scheduleFakeChatReaction();
        return true;
    }

    private static BlockState parseBlock(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT).trim();
        int bracket = cleaned.indexOf('[');
        if (bracket > 0) {
            cleaned = cleaned.substring(0, bracket);
        }

        // Числовые ID старого WorldEdit: "35", "35:14".
        if (cleaned.matches("\\d+(:\\d+)?")) {
            String legacy = LegacyBlocks.resolve(cleaned);
            if (legacy != null) {
                cleaned = legacy;
            } else {
                // Неизвестный легаси-ID — пробуем как сырой ID реестра.
                int rawId = Integer.parseInt(cleaned.split(":")[0]);
                Block byRawId = Registries.BLOCK.get(rawId);
                return byRawId == null ? null : byRawId.getDefaultState();
            }
        }

        Identifier id = Identifier.tryParse(cleaned.contains(":") ? cleaned : "minecraft:" + cleaned);
        if (id == null) {
            return null;
        }
        if (!Registries.BLOCK.containsId(id)) {
            return null;
        }
        Block block = Registries.BLOCK.get(id);
        return block == null ? null : block.getDefaultState();
    }
}
