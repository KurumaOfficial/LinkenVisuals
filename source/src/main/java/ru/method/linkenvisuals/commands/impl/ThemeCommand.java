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
import java.util.Arrays;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class ThemeCommand extends Command {
    private static final String THEME_EXTENSION = ".theme";
    private static final String DESCRIPTION_KEY = "commands.help.desc.theme";
    private static final java.util.List<String> EXAMPLES = java.util.List.of(
            "theme save <name>",
            "theme load <name>",
            "theme remove <name>",
            "theme list",
            "theme dir"
    );

    public ThemeCommand() {
        super("theme");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    CommandUi.sendCommandDetails(HelpCommand.getTitle(getCommand()), I18n.translate(DESCRIPTION_KEY), getAliases(), EXAMPLES);
                    return SINGLE_SUCCESS;
                })
                .then(literal("save")
                        .then(arg("theme", word())
                                .executes(context -> {
                                    String theme = LinkenVisuals.getInstance().getConfigManager()
                                            .normalizeThemeName(context.getArgument("theme", String.class));
                                    try {
                                        LinkenVisuals.getInstance().getConfigManager().saveTheme(theme);
                                        sendMessage("theme save " + theme, I18n.translate("commands.theme.save", theme), CommandUi.SECONDARY);
                                    } catch (Exception exception) {
                                        LinkenVisuals.LOGGER.error("[ThemeCommand] Failed to save theme {}", theme, exception);
                                        sendMessage("theme save " + theme, I18n.translate("commands.theme.save_failed", theme), CommandUi.ERROR);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("load")
                        .then(arg("theme", word())
                                .suggests((context, suggestions) -> {
                                    File[] files = listThemeFiles();
                                    if (files != null) {
                                        Arrays.stream(files).map(File::getName).map(ThemeCommand::stripThemeExtension).forEach(suggestions::suggest);
                                    }
                                    return suggestions.buildFuture();
                                })
                                .executes(context -> {
                                    String theme = LinkenVisuals.getInstance().getConfigManager()
                                            .normalizeThemeName(context.getArgument("theme", String.class));
                                    try {
                                        boolean loaded = LinkenVisuals.getInstance().getConfigManager().loadTheme(theme);
                                        sendMessage("theme load " + theme,
                                                I18n.translate(loaded ? "commands.theme.load" : "commands.theme.notfound", theme),
                                                loaded ? CommandUi.SECONDARY : CommandUi.ERROR);
                                    } catch (Exception exception) {
                                        LinkenVisuals.LOGGER.error("[ThemeCommand] Failed to load theme {}", theme, exception);
                                        sendMessage("theme load " + theme, I18n.translate("commands.theme.notfound", theme), CommandUi.ERROR);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("remove")
                        .then(arg("theme", word())
                                .suggests((context, suggestions) -> {
                                    File[] files = listThemeFiles();
                                    if (files != null) {
                                        Arrays.stream(files).map(File::getName).map(ThemeCommand::stripThemeExtension).forEach(suggestions::suggest);
                                    }
                                    return suggestions.buildFuture();
                                })
                                .executes(context -> {
                                    String theme = LinkenVisuals.getInstance().getConfigManager()
                                            .normalizeThemeName(context.getArgument("theme", String.class));
                                    try {
                                        boolean removed = LinkenVisuals.getInstance().getConfigManager().removeTheme(theme);
                                        sendMessage("theme remove " + theme,
                                                I18n.translate(removed ? "commands.theme.removed" : "commands.theme.notfound", theme),
                                                removed ? CommandUi.PRIMARY : CommandUi.ERROR);
                                    } catch (Exception exception) {
                                        LinkenVisuals.LOGGER.error("[ThemeCommand] Failed to remove theme {}", theme, exception);
                                        sendMessage("theme remove " + theme, I18n.translate("commands.theme.remove_failed", theme), CommandUi.ERROR);
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("dir")
                        .executes(context -> {
                            openThemeDir();
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("list")
                        .executes(context -> {
                            File[] files = listThemeFiles();
                            if (files == null || files.length == 0) {
                                sendMessage("theme list", I18n.translate("commands.theme.empty"), CommandUi.SECONDARY);
                                return SINGLE_SUCCESS;
                            }

                            Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
                            String prefix = LinkenVisuals.getInstance().getCommandManager().getPrefix();
                            MutableText message = CommandUi.commandCard("theme list", I18n.translate(DESCRIPTION_KEY));
                            CommandUi.appendLine(message, CommandUi.valueLine(I18n.translate("commands.ui.entries"), String.valueOf(files.length)));
                            for (File file : files) {
                                String name = stripThemeExtension(file.getName());
                                MutableText line = CommandUi.listLine(name, "");
                                line.append(CommandUi.removeAction(prefix + "theme remove " + name));
                                CommandUi.appendLine(message, line);
                            }
                            ChatUtils.sendMessage(message);
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private void openThemeDir() {
        File dir = LinkenVisuals.getInstance().getThemesDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Util.getOperatingSystem().open(dir);

        MutableText path = Text.literal(dir.getAbsolutePath())
                .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(CommandUi.color(CommandUi.ACCENT)))
                        .withClickEvent(new ClickEvent.OpenFile(dir.getAbsolutePath()))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(dir.getAbsolutePath()))));
        MutableText message = CommandUi.commandCard("theme dir", I18n.translate(DESCRIPTION_KEY));
        MutableText line = CommandUi.valueLine(I18n.translate("commands.ui.folder"), "");
        line.append(path);
        CommandUi.appendLine(message, line);
        ChatUtils.sendMessage(message);
    }

    private void sendMessage(String usage, String message, int color) {
        CommandUi.sendCommandMessage(usage, I18n.translate(DESCRIPTION_KEY), message, color);
    }

    private static File[] listThemeFiles() {
        return LinkenVisuals.getInstance().getConfigManager().listThemeFiles();
    }

    private static String stripThemeExtension(String name) {
        return name.toLowerCase().endsWith(THEME_EXTENSION)
                ? name.substring(0, name.length() - THEME_EXTENSION.length())
                : name;
    }
}
