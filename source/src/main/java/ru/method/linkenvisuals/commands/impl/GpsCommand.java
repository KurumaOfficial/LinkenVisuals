package ru.method.linkenvisuals.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.utils.chat.CommandUi;
import ru.method.linkenvisuals.utils.gps.Gps;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

public class GpsCommand extends Command {
    private static final String DESCRIPTION_KEY = "commands.help.desc.gps";
    private static final java.util.List<String> EXAMPLES = java.util.List.of(
            "gps add <x> <z>",
            "gps add me",
            "gps remove <x> <z>",
            "gps clear"
    );

    public GpsCommand() {
        super("gps");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    CommandUi.sendCommandDetails(HelpCommand.getTitle(getCommand()), I18n.translate(DESCRIPTION_KEY), getAliases(), EXAMPLES);
                    return SINGLE_SUCCESS;
                })
                .then(literal("add")
                        .then(arg("X", integer())
                                .then(arg("Z", integer())
                                        .executes(context -> {
                                            int x = context.getArgument("X", Integer.class);
                                            int z = context.getArgument("Z", Integer.class);
                                            Gps current = LinkenVisuals.getInstance().getGpsManager().getFirst();
                                            if (current != null && current.getX() == x && current.getZ() == z) {
                                                sendMessage("gps add " + x + " " + z, I18n.translate("commands.gps.already", x, z), CommandUi.ALIAS);
                                            } else {
                                                LinkenVisuals.getInstance().getGpsManager().add(new Gps(x, z));
                                                sendMessage("gps add " + x + " " + z, I18n.translate("commands.gps.added", x, z), CommandUi.SECONDARY);
                                            }
                                            return SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(literal("add")
                        .then(literal("me")
                                .executes(context -> setCurrentPosition()))
                )
                .then(literal("remove")
                        .then(arg("X", integer())
                                .then(arg("Z", integer())
                                        .executes(context -> {
                                            int x = context.getArgument("X", Integer.class);
                                            int z = context.getArgument("Z", Integer.class);
                                            if (LinkenVisuals.getInstance().getGpsManager().isEmpty()) {
                                                sendMessage("gps remove " + x + " " + z, I18n.translate("commands.gps.empty"), CommandUi.SECONDARY);
                                                return SINGLE_SUCCESS;
                                            }
                                            Gps current = LinkenVisuals.getInstance().getGpsManager().getFirst();
                                            if (current != null && current.getX() == x && current.getZ() == z) {
                                                LinkenVisuals.getInstance().getGpsManager().clear();
                                                sendMessage("gps remove " + x + " " + z, I18n.translate("commands.gps.removed", x, z), CommandUi.SECONDARY);
                                            } else {
                                                sendMessage("gps remove " + x + " " + z, I18n.translate("commands.gps.notfound", x, z), CommandUi.ERROR);
                                            }
                                            return SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(literal("clear")
                        .executes(context -> {
                            if (!LinkenVisuals.getInstance().getGpsManager().isEmpty()) {
                                LinkenVisuals.getInstance().getGpsManager().clear();
                                sendMessage("gps clear", I18n.translate("commands.gps.cleared"), CommandUi.SECONDARY);
                            } else {
                                sendMessage("gps clear", I18n.translate("commands.gps.empty"), CommandUi.SECONDARY);
                            }
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private int setCurrentPosition() {
        if (mc.player == null) {
            sendMessage("gps add me", I18n.translate("commands.gps.empty"), CommandUi.SECONDARY);
            return SINGLE_SUCCESS;
        }

        BlockPos pos = mc.player.getBlockPos();
        int x = pos.getX();
        int z = pos.getZ();

        Gps current = LinkenVisuals.getInstance().getGpsManager().getFirst();
        if (current != null && current.getX() == x && current.getZ() == z) {
            sendMessage("gps add me", I18n.translate("commands.gps.already", x, z), CommandUi.ALIAS);
            return SINGLE_SUCCESS;
        }

        LinkenVisuals.getInstance().getGpsManager().add(new Gps(x, z));
        sendMessage("gps add me", I18n.translate("commands.gps.added", x, z), CommandUi.SECONDARY);
        return SINGLE_SUCCESS;
    }

    private void sendMessage(String usage, String message, int color) {
        CommandUi.sendCommandMessage(usage, I18n.translate(DESCRIPTION_KEY), message, color);
    }
}
