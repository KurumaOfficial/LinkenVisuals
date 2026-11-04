package ru.method.linkenvisuals.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class FriendCommand extends Command {
    private static final String DESCRIPTION_KEY = "commands.help.desc.friend";
    private static final List<String> EXAMPLES = List.of(
            "friend add <игрок>",
            "friend remove <игрок>",
            "friend list",
            "friend clear"
    );

    public FriendCommand() {
        super("friend", "friends");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    CommandUi.sendCommandDetails(HelpCommand.getTitle(getCommand()), I18n.translate(DESCRIPTION_KEY), getAliases(), EXAMPLES);
                    return SINGLE_SUCCESS;
                })
                .then(literal("add")
                        .then(arg("player", word())
                                .suggests((context, builder1) -> {
                                    for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                                        String name = entry.getProfile().name();
                                        if (name.toLowerCase().startsWith(builder1.getRemaining().toLowerCase())) {
                                            builder1.suggest(name);
                                        }
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String player = context.getArgument("player", String.class);
                                    if (!LinkenVisuals.getInstance().getFriendManager().isFriend(player)) {
                                        LinkenVisuals.getInstance().getFriendManager().add(player);
                                        sendMessage("friend add " + player, I18n.translate("commands.friend.added", player), CommandUi.PRIMARY);
                                    } else {
                                        sendMessage("friend add " + player, I18n.translate("commands.friend.already", player), CommandUi.ALIAS);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("remove")
                        .then(arg("player", word())
                                .suggests((context, builder1) -> {
                                    LinkenVisuals.getInstance().getFriendManager().getFriends().stream()
                                            .sorted(String::compareTo)
                                            .filter(name -> name.startsWith(builder1.getRemaining()))
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String player = context.getArgument("player", String.class);
                                    if (LinkenVisuals.getInstance().getFriendManager().isFriend(player)) {
                                        LinkenVisuals.getInstance().getFriendManager().remove(player);
                                        sendMessage("friend remove " + player, I18n.translate("commands.friend.removed", player), CommandUi.PRIMARY);
                                    } else {
                                        sendMessage("friend remove " + player, I18n.translate("commands.friend.notfound", player), CommandUi.ERROR);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            List<String> friends = new ArrayList<>(LinkenVisuals.getInstance().getFriendManager().getFriends());
                            if (friends.isEmpty()) {
                                sendMessage("friend list", I18n.translate("commands.friend.empty"), CommandUi.SECONDARY);
                                return SINGLE_SUCCESS;
                            }

                            friends.sort(String::compareToIgnoreCase);
                            String prefix = LinkenVisuals.getInstance().getCommandManager().getPrefix();
                            MutableText message = CommandUi.commandCard("friend list", I18n.translate(DESCRIPTION_KEY));
                            CommandUi.appendLine(message, CommandUi.valueLine(I18n.translate("commands.ui.entries"), String.valueOf(friends.size())));

                            for (String friend : friends) {
                                MutableText line = CommandUi.listLine(friend, "");
                                line.append(CommandUi.removeAction(prefix + "friend remove " + friend));
                                CommandUi.appendLine(message, line);
                            }

                            ChatUtils.sendMessage(message);
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("clear")
                        .executes(context -> {
                            if (!LinkenVisuals.getInstance().getFriendManager().isEmpty()) {
                                LinkenVisuals.getInstance().getFriendManager().clear();
                                sendMessage("friend clear", I18n.translate("commands.friend.cleared"), CommandUi.PRIMARY);
                            } else {
                                sendMessage("friend clear", I18n.translate("commands.friend.empty"), CommandUi.SECONDARY);
                            }
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private void sendMessage(String usage, String message, int color) {
        CommandUi.sendCommandMessage(usage, I18n.translate(DESCRIPTION_KEY), message, color);
    }
}
