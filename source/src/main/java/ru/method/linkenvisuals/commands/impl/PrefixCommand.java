package ru.method.linkenvisuals.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public class PrefixCommand extends Command {
    private static final String DESCRIPTION_KEY = "commands.help.desc.prefix";

    public PrefixCommand() {
        super("prefix");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    String currentPrefix = LinkenVisuals.getInstance().getCommandManager().getPrefix();
                    MutableText message = CommandUi.commandCard("prefix", I18n.translate(DESCRIPTION_KEY));
                    CommandUi.appendLine(message, CommandUi.valueLine(I18n.translate("commands.prefix.current"), currentPrefix));
                    ChatUtils.sendMessage(message);
                    return SINGLE_SUCCESS;
                })
                .then(arg("newPrefix", greedyString())
                        .executes(context -> {
                            String newPrefix = context.getArgument("newPrefix", String.class);
                            String oldPrefix = LinkenVisuals.getInstance().getCommandManager().getPrefix();

                            if (newPrefix.isEmpty()) {
                                sendMessage("prefix <новый_префикс>", I18n.translate("commands.prefix.empty"), CommandUi.ERROR);
                                return SINGLE_SUCCESS;
                            }

                            if (newPrefix.length() > 5) {
                                sendMessage("prefix " + newPrefix, I18n.translate("commands.prefix.too_long"), CommandUi.ERROR);
                                return SINGLE_SUCCESS;
                            }

                            sendMessage("prefix " + newPrefix, I18n.translate("commands.prefix.changed", oldPrefix, newPrefix), CommandUi.PRIMARY);
                            LinkenVisuals.getInstance().getCommandManager().setPrefix(newPrefix);
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private void sendMessage(String usage, String message, int color) {
        CommandUi.sendCommandMessage(usage, I18n.translate(DESCRIPTION_KEY), message, color);
    }
}
