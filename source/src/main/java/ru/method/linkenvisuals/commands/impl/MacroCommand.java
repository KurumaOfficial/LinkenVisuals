package ru.method.linkenvisuals.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;
import ru.method.linkenvisuals.utils.input.KeyNameUtils;
import ru.method.linkenvisuals.utils.macro.Macro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class MacroCommand extends Command {
    private static final String DESCRIPTION_KEY = "commands.help.desc.macro";
    private static final java.util.List<String> EXAMPLES = java.util.List.of(
            "macro add <название> <клавиша> <команда>",
            "macro remove <название>",
            "macro list",
            "macro clear"
    );

    public MacroCommand() {
        super("macro");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    CommandUi.sendCommandDetails(HelpCommand.getTitle(getCommand()), I18n.translate(DESCRIPTION_KEY), getAliases(), EXAMPLES);
                    return SINGLE_SUCCESS;
                })
                .then(literal("add")
                        .then(arg("name", word())
                                .then(arg("bind", word())
                                        .suggests((context, builder1) -> {
                                            for (String suggestion : KeyNameUtils.suggestions(builder1.getRemaining())) {
                                                builder1.suggest(suggestion);
                                            }
                                            return builder1.buildFuture();
                                        })
                                        .then(arg("command", greedyString())
                                                .executes(context -> {
                                                    String name = context.getArgument("name", String.class);
                                                    String rawBind = context.getArgument("bind", String.class);
                                                    Bind parsedBind = KeyNameUtils.parseBind(rawBind);
                                                    String macroCommand = context.getArgument("command", String.class);
                                                    String usage = "macro add " + name + " " + rawBind + " " + macroCommand;
                                                    if (parsedBind == null || parsedBind.isMouse()) {
                                                        sendMessage(usage, I18n.translate("commands.bind.unknown", rawBind), CommandUi.ERROR);
                                                        return SINGLE_SUCCESS;
                                                    }
                                                    if (!LinkenVisuals.getInstance().getMacroManager().getNames().contains(name)) {
                                                        Macro macro = new Macro(name, macroCommand, parsedBind);
                                                        LinkenVisuals.getInstance().getMacroManager().add(macro);
                                                        saveMacros();
                                                        sendMessage(usage, I18n.translate("commands.macro.added", name), CommandUi.PRIMARY);
                                                    } else {
                                                        sendMessage(usage, I18n.translate("commands.macro.already", name), CommandUi.ALIAS);
                                                    }
                                                    return SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                )
                .then(literal("remove")
                        .then(arg("name", word())
                                .suggests((context, builder1) -> {
                                    LinkenVisuals.getInstance().getMacroManager().getNames().stream()
                                            .filter(name -> name.startsWith(builder1.getRemaining()))
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String name = context.getArgument("name", String.class);
                                    String usage = "macro remove " + name;
                                    if (LinkenVisuals.getInstance().getMacroManager().getMacros().isEmpty()
                                            || LinkenVisuals.getInstance().getMacroManager().getNames().isEmpty()) {
                                        return SINGLE_SUCCESS;
                                    }
                                    if (!LinkenVisuals.getInstance().getMacroManager().getNames().contains(name)) {
                                        sendMessage(usage, I18n.translate("commands.macro.notfound", name), CommandUi.ERROR);
                                        return SINGLE_SUCCESS;
                                    }

                                    LinkenVisuals.getInstance().getMacroManager().remove(LinkenVisuals.getInstance().getMacroManager().getMacro(name));
                                    saveMacros();
                                    sendMessage(usage, I18n.translate("commands.macro.removed", name), CommandUi.PRIMARY);
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            if (LinkenVisuals.getInstance().getMacroManager().getMacros().isEmpty()) {
                                sendMessage("macro list", I18n.translate("commands.macro.empty"), CommandUi.SECONDARY);
                                return SINGLE_SUCCESS;
                            }

                            List<Macro> macros = new ArrayList<>(LinkenVisuals.getInstance().getMacroManager().getMacros());
                            macros.sort(Comparator.comparing(Macro::getName, String::compareToIgnoreCase));
                            MutableText message = CommandUi.commandCard("macro list", I18n.translate(DESCRIPTION_KEY));
                            CommandUi.appendLine(message, CommandUi.valueLine(I18n.translate("commands.ui.entries"), String.valueOf(macros.size())));

                            for (Macro macro : macros) {
                                String keyName = formatKey(macro.getBind());
                                MutableText line = CommandUi.listLine(macro.getName(), " [" + keyName + "] " + macro.getCommand());
                                line.append(CommandUi.removeAction(LinkenVisuals.getInstance().getCommandManager().getPrefix() + "macro remove " + macro.getName()));
                                CommandUi.appendLine(message, line);
                            }

                            ChatUtils.sendMessage(message);
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("clear")
                        .executes(context -> {
                            if (!LinkenVisuals.getInstance().getMacroManager().isEmpty()) {
                                LinkenVisuals.getInstance().getMacroManager().clear();
                                saveMacros();
                                sendMessage("macro clear", I18n.translate("commands.macro.cleared"), CommandUi.PRIMARY);
                            } else {
                                sendMessage("macro clear", I18n.translate("commands.macro.empty"), CommandUi.SECONDARY);
                            }
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private void saveMacros() {
        try {
            LinkenVisuals.getInstance().getConfigManager().saveMacros();
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[MacroCommand] Failed to save macros", e);
        }
    }

    private String formatKey(Bind bind) {
        return KeyNameUtils.formatBind(bind);
    }

    private void sendMessage(String usage, String message, int color) {
        CommandUi.sendCommandMessage(usage, I18n.translate(DESCRIPTION_KEY), message, color);
    }
}
