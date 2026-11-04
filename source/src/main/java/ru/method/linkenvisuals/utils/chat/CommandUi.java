package ru.method.linkenvisuals.utils.chat;

import lombok.experimental.UtilityClass;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.render.ColorUtils;

import java.util.Arrays;
import java.util.List;

@UtilityClass
public class CommandUi {
    public static final int ACCENT = 0x7DD3FC;
    public static final int PRIMARY = 0xF8FAFC;
    public static final int SECONDARY = 0x94A3B8;
    public static final int ALIAS = 0xF59E0B;
    public static final int EXAMPLE = 0xA78BFA;
    public static final int SUCCESS = PRIMARY;
    public static final int WARNING = ALIAS;
    public static final int ERROR = 0xF87171;

    public MutableText commandCard(String usage, String description) {
        MutableText message = Text.literal("")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(SECONDARY))));

        message.append(Text.literal(commandPrefix() + usage)
                .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(resolveColor(ACCENT)))
                        .withBold(true)));

        if (description != null && !description.isBlank()) {
            message.append(Text.literal("\n"));
            message.append(Text.literal(description)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(SECONDARY)))));
        }

        return message;
    }

    public MutableText listLine(String primary, String secondary) {
        MutableText line = Text.literal("");
        line.append(Text.literal("\u2022 ")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(SECONDARY)))));
        line.append(Text.literal(primary == null ? "" : primary)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(ACCENT)))));
        if (secondary != null && !secondary.isBlank()) {
            line.append(Text.literal(secondary)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(PRIMARY)))));
        }
        return line;
    }

    public MutableText valueLine(String label, String value) {
        MutableText line = listLine(label, "");
        line.append(Text.literal(": ")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(SECONDARY)))));
        line.append(Text.literal(value == null ? "" : value)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(PRIMARY)))));
        return line;
    }

    public MutableText messageCard(String usage, String description, String message, int color) {
        MutableText card = commandCard(usage, description);
        if (message != null && !message.isBlank()) {
            appendLine(card, line(message, color, false));
        }
        return card;
    }

    public void sendCommandMessage(String usage, String description, String message, int color) {
        ChatUtils.sendMessage(messageCard(usage, description, message, color));
    }

    public void sendCommandDetails(String command, String description, String[] aliases, List<String> examples) {
        ChatUtils.sendMessage(commandDetails(command, description, aliases, examples));
    }

    public MutableText commandDetails(String command, String description, String[] aliases, List<String> examples) {
        return commandDetails(command, description, aliases, examples, null, PRIMARY);
    }

    public void sendCommandDetails(String command, String description, String[] aliases, List<String> examples, String statusMessage, int statusColor) {
        ChatUtils.sendMessage(commandDetails(command, description, aliases, examples, statusMessage, statusColor));
    }

    public MutableText commandDetails(String command, String description, String[] aliases, List<String> examples, String statusMessage, int statusColor) {
        MutableText message = Text.literal("")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(SECONDARY))));

        message.append(line(command, ACCENT, true));

        if (statusMessage != null && !statusMessage.isBlank()) {
            message.append(Text.literal("\n"));
            message.append(line(statusMessage, statusColor, false));
        }

        if (examples != null && !examples.isEmpty()) {
            for (String usage : examples) {
                if (usage == null || usage.isBlank()) {
                    continue;
                }
                String full = commandPrefix() + usage;
                message.append(Text.literal("\n"));
                message.append(Text.literal("\u2022 ")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(SECONDARY)))));
                message.append(Text.literal(full)
                        .setStyle(Style.EMPTY
                                .withColor(TextColor.fromRgb(resolveColor(EXAMPLE)))
                                .withClickEvent(new ClickEvent.SuggestCommand(full + " "))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal(I18n.translate("commands.help.click_hint"))))));
            }
        }

        return message;
    }

    public MutableText action(String label, int color, ClickEvent.Action action, String value, String hoverText) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(color)));
        if (action != null && value != null && !value.isBlank()) {
            style = style.withClickEvent(clickEvent(action, value));
        }
        if (hoverText != null && !hoverText.isBlank()) {
            style = style.withHoverEvent(new HoverEvent.ShowText(Text.literal(hoverText)));
        }
        return Text.literal(" [" + label + "]").setStyle(style);
    }

    public MutableText removeAction(String command) {
        String runCommand = normalizeRunCommand(command);
        return action("\u0423\u0434\u0430\u043B\u0438\u0442\u044C", ERROR, ClickEvent.Action.RUN_COMMAND, runCommand, command);
    }

    public String normalizeRunCommand(String command) {
        String runCommand = command == null ? "" : command.trim();
        if (!runCommand.isEmpty() && !runCommand.startsWith("/")) {
            runCommand = "/" + runCommand;
        }
        return runCommand;
    }

    public void appendLine(MutableText message, MutableText line) {
        if (message == null || line == null) {
            return;
        }
        message.append(Text.literal("\n"));
        message.append(line);
    }

    public void sendInfo(String text) {
        ChatUtils.sendMessage(status(text, PRIMARY));
    }

    public void sendSuccess(String text) {
        ChatUtils.sendMessage(status(text, SUCCESS));
    }

    public void sendWarning(String text) {
        ChatUtils.sendMessage(status(text, WARNING));
    }

    public void sendError(String text) {
        ChatUtils.sendMessage(status(text, ERROR));
    }

    public MutableText line(String text, int color, boolean bold) {
        return Text.literal(text == null ? "" : text)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(color))).withBold(bold));
    }

    private MutableText status(String text, int color) {
        return Text.literal(text == null ? "" : text)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(resolveColor(color))));
    }

    public int color(int color) {
        return resolveColor(color);
    }

    private String formatAliases(String[] aliases) {
        if (aliases == null || aliases.length == 0) {
            return "";
        }
        return Arrays.stream(aliases)
                .filter(alias -> alias != null && !alias.isBlank())
                .map(alias -> commandPrefix() + alias)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private int resolveColor(int color) {
        if (color == ACCENT || color == EXAMPLE) {
            return ColorUtils.getSecondaryColor().getRGB() & 0xFFFFFF;
        }
        return color;
    }

    private String commandPrefix() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getCommandManager() == null) {
            return ".";
        }
        return instance.getCommandManager().getPrefix();
    }

    private static ClickEvent clickEvent(ClickEvent.Action action, String value) {
        if (action == null || value == null || value.isBlank()) return null;
        return switch (action) {
            case RUN_COMMAND -> new ClickEvent.RunCommand(value);
            case SUGGEST_COMMAND -> new ClickEvent.SuggestCommand(value);
            case COPY_TO_CLIPBOARD -> new ClickEvent.CopyToClipboard(value);
            case OPEN_URL -> {
                try { yield new ClickEvent.OpenUrl(java.net.URI.create(value)); } catch (Throwable t) { yield null; }
            }
            case CHANGE_PAGE -> {
                try { yield new ClickEvent.ChangePage(Integer.parseInt(value)); } catch (Throwable t) { yield null; }
            }
            default -> null;
        };
    }
}
