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
import net.minecraft.util.Util;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class ConfigCommand extends Command {
    private static final String CONFIG_EXTENSION = ".linken";
    private static final String LEGACY_CONFIG_EXTENSION = ".prime";
    private static final String DESCRIPTION_KEY = "commands.help.desc.config";
    private static final java.util.List<String> EXAMPLES = java.util.List.of(
            "config save <название>",
            "config load <название>",
            "config remove <название>",
            "config list",
            "config dir"
    );

    public ConfigCommand() {
        super("config", "cfg");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    CommandUi.sendCommandDetails(HelpCommand.getTitle(getCommand()), I18n.translate(DESCRIPTION_KEY), getAliases(), EXAMPLES);
                    return SINGLE_SUCCESS;
                })
                .then(literal("save")
                        .then(arg("config", word())
                                .suggests((context, builder1) -> {
                                    if (LinkenVisuals.getInstance().getConfigsDir().exists() && LinkenVisuals.getInstance().getConfigsDir().isDirectory()) {
                                        File[] files = listConfigFiles();
                                        if (files != null) {
                                            Arrays.stream(files)
                                                    .map(File::getName)
                                                    .map(ConfigCommand::stripConfigExtension)
                                                    .forEach(builder1::suggest);
                                        }
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String config = LinkenVisuals.getInstance().getConfigManager()
                                            .normalizeConfigName(context.getArgument("config", String.class));
                                    try {
                                        LinkenVisuals.getInstance().getConfigManager().saveConfig(config);
                                        sendMessage("config save " + config, I18n.translate("commands.config.save", config), CommandUi.SECONDARY);
                                    } catch (Exception e) {
                                        LinkenVisuals.LOGGER.error("[ConfigCommand] Failed to save config {}", config, e);
                                        sendMessage("config save " + config, I18n.translate("commands.config.remove_failed", config), CommandUi.ERROR);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("load")
                        .then(arg("config", word())
                                .suggests((context, builder1) -> {
                                    if (LinkenVisuals.getInstance().getConfigsDir().exists() && LinkenVisuals.getInstance().getConfigsDir().isDirectory()) {
                                        File[] files = listConfigFiles();
                                        if (files != null) {
                                            Arrays.stream(files)
                                                    .map(File::getName)
                                                    .map(ConfigCommand::stripConfigExtension)
                                                    .forEach(builder1::suggest);
                                        }
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String config = LinkenVisuals.getInstance().getConfigManager()
                                            .normalizeConfigName(context.getArgument("config", String.class));
                                    try {
                                        boolean loaded = LinkenVisuals.getInstance().getConfigManager().loadConfig(config);
                                        if (loaded) {
                                            sendMessage("config load " + config, I18n.translate("commands.config.load", config), CommandUi.SECONDARY);
                                        } else {
                                            sendMessage("config load " + config, I18n.translate("commands.config.notfound", config), CommandUi.ERROR);
                                        }
                                    } catch (Exception e) {
                                        LinkenVisuals.LOGGER.error("[ConfigCommand] Failed to load config {}", config, e);
                                        sendMessage("config load " + config, I18n.translate("commands.config.notfound", config), CommandUi.ERROR);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("remove")
                        .then(arg("config", word())
                                .suggests((context, builder1) -> {
                                    if (LinkenVisuals.getInstance().getConfigsDir().exists() && LinkenVisuals.getInstance().getConfigsDir().isDirectory()) {
                                        File[] files = listConfigFiles();
                                        if (files != null) {
                                            Arrays.stream(files)
                                                    .map(File::getName)
                                                    .map(ConfigCommand::stripConfigExtension)
                                                    .forEach(builder1::suggest);
                                        }
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    var configManager = LinkenVisuals.getInstance().getConfigManager();
                                    String config = configManager.normalizeConfigName(context.getArgument("config", String.class));
                                    if ("default".equalsIgnoreCase(config)) {
                                        sendMessage("config remove " + config, I18n.translate("commands.config.default_locked"), CommandUi.WARNING);
                                        return SINGLE_SUCCESS;
                                    }

                                    String currentConfig = configManager.normalizeConfigName(configManager.getCurrentConfigName());
                                    if (config.equalsIgnoreCase(currentConfig)) {
                                        try {
                                            configManager.saveConfig("default");
                                        } catch (Exception e) {
                                            LinkenVisuals.LOGGER.error("[ConfigCommand] Failed to switch current config before deleting {}", config, e);
                                            sendMessage("config remove " + config, I18n.translate("commands.config.remove_failed", config), CommandUi.ERROR);
                                            return SINGLE_SUCCESS;
                                        }
                                    }

                                    File target = resolveConfigFile(config);
                                    if (!target.exists()) {
                                        sendMessage("config remove " + config, I18n.translate("commands.config.notfound", config), CommandUi.ERROR);
                                        return SINGLE_SUCCESS;
                                    }

                                    try {
                                        Files.deleteIfExists(target.toPath());
                                        sendMessage("config remove " + config, I18n.translate("commands.config.removed", config), CommandUi.PRIMARY);
                                    } catch (IOException ioException) {
                                        LinkenVisuals.LOGGER.error("[ConfigCommand] Failed to remove config {}", config, ioException);
                                        sendMessage("config remove " + config, I18n.translate("commands.config.remove_failed", config), CommandUi.ERROR);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("dir")
                        .executes(context -> {
                            openConfigDir();
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("list")
                        .executes(context -> {
                            File[] files = listConfigFiles();
                            String prefix = LinkenVisuals.getInstance().getCommandManager().getPrefix();

                            if (files == null || files.length == 0) {
                                sendMessage("config list", I18n.translate("commands.config.empty"), CommandUi.SECONDARY);
                            } else {
                                Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
                                MutableText message = CommandUi.commandCard("config list", I18n.translate(DESCRIPTION_KEY));
                                CommandUi.appendLine(message, CommandUi.valueLine(I18n.translate("commands.ui.entries"), String.valueOf(files.length)));

                                for (File file : files) {
                                    String fileName = stripConfigExtension(file.getName());
                                    MutableText line = CommandUi.listLine(fileName, "");
                                    if (!"default".equalsIgnoreCase(fileName)) {
                                        line.append(CommandUi.removeAction(prefix + "config remove " + fileName));
                                    } else {
                                        line.append(CommandUi.action(
                                                I18n.translate("commands.ui.default"),
                                                CommandUi.SECONDARY,
                                                null,
                                                null,
                                                I18n.translate("commands.config.default_locked")
                                        ));
                                    }
                                    CommandUi.appendLine(message, line);
                                }

                                ChatUtils.sendMessage(message);
                            }

                            return SINGLE_SUCCESS;
                        })
                );
    }

    private void openConfigDir() {
        File dir = LinkenVisuals.getInstance().getConfigsDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Util.getOperatingSystem().open(dir);

        MutableText path = Text.literal(dir.getAbsolutePath())
                .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(CommandUi.color(CommandUi.ACCENT)))
                        .withClickEvent(new ClickEvent.OpenFile(dir.getAbsolutePath()))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(dir.getAbsolutePath()))));

        MutableText message = CommandUi.commandCard("config dir", I18n.translate(DESCRIPTION_KEY));
        MutableText line = CommandUi.valueLine(I18n.translate("commands.ui.folder"), "");
        line.append(path);
        CommandUi.appendLine(message, line);

        ChatUtils.sendMessage(message);
    }

    private void sendMessage(String usage, String message, int color) {
        CommandUi.sendCommandMessage(usage, I18n.translate(DESCRIPTION_KEY), message, color);
    }

    private static File[] listConfigFiles() {
        return LinkenVisuals.getInstance().getConfigsDir().listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(CONFIG_EXTENSION) || lower.endsWith(LEGACY_CONFIG_EXTENSION);
        });
    }

    private static String stripConfigExtension(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(CONFIG_EXTENSION)) {
            return name.substring(0, name.length() - CONFIG_EXTENSION.length());
        }
        if (lower.endsWith(LEGACY_CONFIG_EXTENSION)) {
            return name.substring(0, name.length() - LEGACY_CONFIG_EXTENSION.length());
        }
        return name;
    }

    private static File resolveConfigFile(String config) {
        File dir = LinkenVisuals.getInstance().getConfigsDir();
        File current = new File(dir, config + CONFIG_EXTENSION);
        if (current.exists()) return current;
        return new File(dir, config + LEGACY_CONFIG_EXTENSION);
    }
}
