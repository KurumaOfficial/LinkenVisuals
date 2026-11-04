package ru.method.linkenvisuals.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.util.math.BlockPos;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;
import ru.method.linkenvisuals.utils.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

public class WaypointCommand extends Command {
    private static final String DESCRIPTION_KEY = "commands.help.desc.way";
    private static final List<String> EXAMPLES = List.of(
            "way add <название> <x> <y> <z>",
            "way add <название> me",
            "way remove <название>",
            "way list",
            "way clear"
    );

    public WaypointCommand() {
        super("way", "waypoint");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    CommandUi.sendCommandDetails(HelpCommand.getTitle(getCommand()), I18n.translate(DESCRIPTION_KEY), getAliases(), EXAMPLES);
                    return SINGLE_SUCCESS;
                })
                .then(literal("add")
                        .then(arg("name", string())
                                .then(arg("X", integer())
                                        .then(arg("Y", integer())
                                                .then(arg("Z", integer())
                                                        .executes(context -> {
                                                            String name = context.getArgument("name", String.class);
                                                            int x = context.getArgument("X", Integer.class);
                                                            int y = context.getArgument("Y", Integer.class);
                                                            int z = context.getArgument("Z", Integer.class);
                                                            if (LinkenVisuals.getInstance().getWaypointManager().getWaypoint(name) == null) {
                                                                LinkenVisuals.getInstance().getWaypointManager().add(new Waypoint(name, x, y, z));
                                                                sendMessage("way add " + name + " " + x + " " + y + " " + z, I18n.translate("commands.waypoint.added", name, x, y, z), CommandUi.SECONDARY);
                                                            } else {
                                                                sendMessage("way add " + name + " " + x + " " + y + " " + z, I18n.translate("commands.waypoint.already", name), CommandUi.ALIAS);
                                                            }
                                                            return SINGLE_SUCCESS;
                                                        })
                                                )
                                        )
                                )
                        )
                )
                .then(literal("add")
                        .then(arg("name", string())
                                .then(literal("me")
                                        .executes(context -> addCurrentPosition(context.getArgument("name", String.class))))
                        )
                )
                .then(literal("remove")
                        .then(arg("name", greedyString())
                                .executes(context -> {
                                    if (LinkenVisuals.getInstance().getWaypointManager().isEmpty()) {
                                        sendMessage("way remove " + context.getArgument("name", String.class), I18n.translate("commands.waypoint.empty"), CommandUi.SECONDARY);
                                        return SINGLE_SUCCESS;
                                    }
                                    String name = context.getArgument("name", String.class);
                                    Waypoint waypoint = LinkenVisuals.getInstance().getWaypointManager().getWaypoint(name);
                                    if (waypoint != null) {
                                        LinkenVisuals.getInstance().getWaypointManager().remove(waypoint);
                                        sendMessage("way remove " + name, I18n.translate("commands.waypoint.removed", name), CommandUi.SECONDARY);
                                    } else {
                                        sendMessage("way remove " + name, I18n.translate("commands.waypoint.notfound", name), CommandUi.ERROR);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            if (LinkenVisuals.getInstance().getWaypointManager().isEmpty()) {
                                sendMessage("way list", I18n.translate("commands.waypoint.empty"), CommandUi.SECONDARY);
                                return SINGLE_SUCCESS;
                            }

                            List<Waypoint> waypoints = new ArrayList<>(LinkenVisuals.getInstance().getWaypointManager().getWaypoints());
                            waypoints.sort(Comparator.comparing(Waypoint::getName, String::compareToIgnoreCase));
                            String prefix = LinkenVisuals.getInstance().getCommandManager().getPrefix();
                            MutableText message = CommandUi.commandCard("way list", I18n.translate(DESCRIPTION_KEY));
                            CommandUi.appendLine(message, CommandUi.valueLine(I18n.translate("commands.ui.entries"), String.valueOf(waypoints.size())));

                            for (Waypoint waypoint : waypoints) {
                                String secondary = String.format("  X: %d, Y: %d, Z: %d", (int) waypoint.getX(), (int) waypoint.getY(), (int) waypoint.getZ());
                                MutableText line = CommandUi.listLine(waypoint.getName(), secondary);
                                line.append(CommandUi.removeAction(prefix + "way remove " + waypoint.getName()));
                                CommandUi.appendLine(message, line);
                            }

                            ChatUtils.sendMessage(message);
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("clear")
                        .executes(context -> {
                            if (!LinkenVisuals.getInstance().getWaypointManager().isEmpty()) {
                                LinkenVisuals.getInstance().getWaypointManager().clear();
                                sendMessage("way clear", I18n.translate("commands.waypoint.cleared"), CommandUi.SECONDARY);
                            } else {
                                sendMessage("way clear", I18n.translate("commands.waypoint.empty"), CommandUi.SECONDARY);
                            }
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private int addCurrentPosition(String name) {
        if (mc.player == null) {
            sendMessage("way add " + name + " me", I18n.translate("commands.waypoint.empty"), CommandUi.SECONDARY);
            return SINGLE_SUCCESS;
        }

        BlockPos pos = mc.player.getBlockPos();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (LinkenVisuals.getInstance().getWaypointManager().getWaypoint(name) == null) {
            LinkenVisuals.getInstance().getWaypointManager().add(new Waypoint(name, x, y, z));
            sendMessage("way add " + name + " me", I18n.translate("commands.waypoint.added", name, x, y, z), CommandUi.SECONDARY);
        } else {
            sendMessage("way add " + name + " me", I18n.translate("commands.waypoint.already", name), CommandUi.ALIAS);
        }
        return SINGLE_SUCCESS;
    }

    private void sendMessage(String usage, String message, int color) {
        CommandUi.sendCommandMessage(usage, I18n.translate(DESCRIPTION_KEY), message, color);
    }
}
