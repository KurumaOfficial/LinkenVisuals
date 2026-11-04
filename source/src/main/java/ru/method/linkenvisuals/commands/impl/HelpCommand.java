package ru.method.linkenvisuals.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class HelpCommand extends Command {
    private static final Map<String, HelpEntry> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put("help", new HelpEntry("Помощь", "commands.help.desc.help", List.of(
                "help",
                "help <команда>"
        )));
        ENTRIES.put("friend", new HelpEntry("Друзья", "commands.help.desc.friend", List.of(
                "friend add <игрок>",
                "friend remove <игрок>",
                "friend list",
                "friend clear"
        )));
        ENTRIES.put("bind", new HelpEntry("Бинды", "commands.help.desc.bind", List.of(
                "bind add <модуль> <клавиша>",
                "bind remove <модуль>",
                "bind list"
        )));
        ENTRIES.put("config", new HelpEntry("Конфиги", "commands.help.desc.config", List.of(
                "config save <название>",
                "config load <название>",
                "config remove <название>",
                "config list",
                "config dir"
        )));
        ENTRIES.put("macro", new HelpEntry("Макросы", "commands.help.desc.macro", List.of(
                "macro add <название> <клавиша> <команда>",
                "macro remove <название>",
                "macro list",
                "macro clear"
        )));
        ENTRIES.put("gps", new HelpEntry("GPS", "commands.help.desc.gps", List.of(
                "gps add <x> <z>",
                "gps add me",
                "gps remove <x> <z>",
                "gps clear"
        )));
        ENTRIES.put("way", new HelpEntry("Метки", "commands.help.desc.way", List.of(
                "way add <название> <x> <y> <z>",
                "way add <название> me",
                "way remove <название>",
                "way list",
                "way clear"
        )));
        ENTRIES.put("prefix", new HelpEntry("Префикс", "commands.help.desc.prefix", List.of(
                "prefix",
                "prefix <новый_префикс>"
        )));
        ENTRIES.put("theme", new HelpEntry("Темы", "commands.help.desc.theme", List.of(
                "theme save <name>",
                "theme load <name>",
                "theme remove <name>",
                "theme list",
                "theme dir"
        )));
        ENTRIES.put("party", new HelpEntry("Пати", "commands.help.desc.party", List.of(
                "party create",
                "party join <код>",
                "party leave"
        )));
    }

    public HelpCommand() {
        super("help");
    }

    public static String getDescription(String commandName) {
        HelpEntry entry = ENTRIES.get(commandName);
        if (entry == null) {
            return commandName == null ? "" : commandName;
        }
        return I18n.translate(entry.descriptionKey());
    }

    public static String getTitle(String commandName) {
        HelpEntry entry = ENTRIES.get(commandName);
        if (entry == null) {
            return commandName == null ? "" : commandName;
        }
        return entry.title();
    }

    public static List<String> getExamples(String commandName) {
        HelpEntry entry = ENTRIES.get(commandName);
        if (entry == null) {
            return commandName == null || commandName.isBlank() ? List.of() : List.of(commandName);
        }
        return entry.examples();
    }

    public static void sendOverview(String statusMessage, int statusColor) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getCommandManager() == null) {
            return;
        }

        String prefix = instance.getCommandManager().getPrefix();
        List<Command> commands = new ArrayList<>(instance.getCommandManager().getCommands());
        commands.sort((left, right) -> left.getCommand().compareToIgnoreCase(right.getCommand()));

        MutableText message = Text.literal("")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(CommandUi.color(CommandUi.SECONDARY))));

        if (statusMessage != null && !statusMessage.isBlank()) {
            message.append(line(statusMessage, statusColor, false));
            message.append(Text.literal("\n"));
        }

        message.append(line(I18n.translate("commands.help.subtitle", prefix + "help <команда>"), CommandUi.SECONDARY, false));

        for (Command command : commands) {
            message.append(Text.literal("\n"));
            message.append(buildCommandLine(prefix, command));
        }

        ChatUtils.sendMessage(message);
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    sendOverview(null, CommandUi.PRIMARY);
                    return SINGLE_SUCCESS;
                })
                .then(arg("command", word())
                        .suggests((context, suggestions) -> {
                            for (Command command : LinkenVisuals.getInstance().getCommandManager().getCommands()) {
                                suggestions.suggest(command.getCommand());
                                for (String alias : command.getAliases()) {
                                    if (alias != null && !alias.isBlank()) {
                                        suggestions.suggest(alias);
                                    }
                                }
                            }
                            return suggestions.buildFuture();
                        })
                        .executes(context -> {
                            sendDetails(context.getArgument("command", String.class));
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private void sendDetails(String token) {
        Command command = resolveCommand(token);
        if (command == null) {
            ChatUtils.sendMessage(Text.literal(I18n.translate("commands.help.unknown", token))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(CommandUi.color(CommandUi.ERROR)))));
            return;
        }

        CommandUi.sendCommandDetails(
                getTitle(command.getCommand()),
                getDescription(command.getCommand()),
                command.getAliases(),
                getExamples(command.getCommand())
        );
    }

    private Command resolveCommand(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getCommandManager() == null) {
            return null;
        }
        return instance.getCommandManager().findCommand(token.trim().toLowerCase(Locale.ROOT));
    }

    private static MutableText buildCommandLine(String prefix, Command command) {
        String commandText = prefix + command.getCommand();
        String aliasText = formatAliases(command.getAliases(), prefix);
        String description = getDescription(command.getCommand());

        MutableText line = Text.literal("");
        line.append(Text.literal("\u2022 ")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(CommandUi.color(CommandUi.SECONDARY)))));
        line.append(Text.literal(commandText)
                .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(CommandUi.color(CommandUi.ACCENT)))
                        .withClickEvent(new ClickEvent.RunCommand(CommandUi.normalizeRunCommand(prefix + "help " + command.getCommand())))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(I18n.translate("commands.help.click_hint"))))));

        if (!aliasText.isEmpty()) {
            line.append(Text.literal(" (" + aliasText + ")")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(CommandUi.color(CommandUi.ALIAS)))));
        }

        line.append(Text.literal("  " + description)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(CommandUi.color(CommandUi.PRIMARY)))));
        return line;
    }

    private static String formatAliases(String[] aliases, String prefix) {
        if (aliases == null || aliases.length == 0) {
            return "";
        }
        return Arrays.stream(aliases)
                .filter(alias -> alias != null && !alias.isBlank())
                .map(alias -> prefix + alias)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static MutableText line(String text, int color, boolean bold) {
        return CommandUi.line(text, color, bold);
    }

    private record HelpEntry(String title, String descriptionKey, List<String> examples) {
    }
}
