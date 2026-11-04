package ru.method.linkenvisuals.managers.system;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.commands.impl.*;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;
import lombok.*;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

@Getter

public class CommandManager implements Wrapper {

    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
    private final CommandSource source = new ClientCommandSource(null, mc, net.minecraft.command.permission.PermissionPredicate.ALL);
    private final List<Command> commands = new ArrayList<>();
    @Setter private String prefix = ".";

    public CommandManager() {
        addCommands(
                new HelpCommand(),
                new FriendCommand(),
                new BindCommand(),
                new ConfigCommand(),
                new ThemeCommand(),
                new MacroCommand(),
                new GpsCommand(),
                new WaypointCommand(),
                new PrefixCommand(),
                new PartyCommand()
        );
    }

    private void addCommands(Command... command) {
        for (Command cmd : command) {
            cmd.register(dispatcher);
            commands.add(cmd);
        }
    }

    public Command findCommand(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        for (Command command : commands) {
            if (command.getCommand().equalsIgnoreCase(token)) {
                return command;
            }

            for (String alias : command.getAliases()) {
                if (alias != null && alias.equalsIgnoreCase(token)) {
                    return command;
                }
            }
        }

        return null;
    }

    public String resolveLiteral(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }

        for (Command command : commands) {
            if (command.getCommand().equalsIgnoreCase(token)) {
                return command.getCommand();
            }

            for (String alias : command.getAliases()) {
                if (alias != null && alias.equalsIgnoreCase(token)) {
                    return alias;
                }
            }
        }

        return token;
    }

    public String normalizeInput(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        int start = 0;
        while (start < input.length() && Character.isWhitespace(input.charAt(start))) {
            start++;
        }

        if (start >= input.length()) {
            return input;
        }

        int end = start;
        while (end < input.length() && !Character.isWhitespace(input.charAt(end))) {
            end++;
        }

        String resolvedLiteral = resolveLiteral(input.substring(start, end));
        if (resolvedLiteral == null || resolvedLiteral.isBlank()) {
            return input;
        }

        return input.substring(0, start) + resolvedLiteral + input.substring(end);
    }

    public Command findClosestCommand(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3) {
            return null;
        }

        Command bestCommand = null;
        int bestScore = Integer.MAX_VALUE;
        for (Command command : commands) {
            int commandScore = bestScoreFor(normalized, command.getCommand());
            if (commandScore < bestScore) {
                bestScore = commandScore;
                bestCommand = command;
            }

            for (String alias : command.getAliases()) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                int aliasScore = bestScoreFor(normalized, alias);
                if (aliasScore < bestScore) {
                    bestScore = aliasScore;
                    bestCommand = command;
                }
            }
        }

        if (bestCommand == null) {
            return null;
        }

        if (bestScore <= 1) {
            return bestCommand;
        }

        if (bestScore == 2 && normalized.length() >= 4) {
            return bestCommand;
        }

        return null;
    }

    private int bestScoreFor(String input, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Integer.MAX_VALUE;
        }

        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        if (normalizedCandidate.equals(input)) {
            return 0;
        }

        if (normalizedCandidate.startsWith(input) || input.startsWith(normalizedCandidate)) {
            return Math.abs(normalizedCandidate.length() - input.length());
        }

        return levenshteinDistance(input, normalizedCandidate);
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }
}
