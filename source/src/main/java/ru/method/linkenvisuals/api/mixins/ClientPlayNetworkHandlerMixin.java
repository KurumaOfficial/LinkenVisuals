package ru.method.linkenvisuals.api.mixins;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.commands.impl.HelpCommand;
import ru.method.linkenvisuals.managers.system.CommandManager;
import ru.method.linkenvisuals.modules.impl.misc.PvpSave;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    public void sendChatMessage(@NotNull String message, CallbackInfo ci) {
        // На случай, если команда ушла обычным сообщением, а не командным пакетом.
        if (message.startsWith("/") && ru.method.linkenvisuals.modules.impl.misc.FakeCosmetic.noteCommand(message)) {
            ci.cancel();
            return;
        }
        if (handleClientCommand(message, false)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    public void sendChatCommand(@NotNull String command, CallbackInfo ci) {
        // Игрок вызвал аукцион (/ah, /auction …) — следующее открытое сервером GUI считаем аукционом.
        ru.method.linkenvisuals.modules.impl.misc.AuctionHelper.noteCommand(command);
        if (ru.method.linkenvisuals.modules.impl.misc.FakeCosmetic.noteCommand(command)) {
            ci.cancel();
            return;
        }
        PvpSave pvpSave = LinkenVisuals.getInstance().getModuleManager().getModule(PvpSave.class);
        if (pvpSave != null && pvpSave.isToggled() && pvpSave.shouldBlockHubCommand(command)) {
            ci.cancel();
            return;
        }
        if (handleClientCommand(command, true)) {
            ci.cancel();
        }
    }

    private boolean handleClientCommand(String message, boolean commandPacket) {
        CommandManager commandManager = LinkenVisuals.getInstance().getCommandManager();
        String prefix = commandManager.getPrefix();
        if (!message.startsWith(prefix)) {
            return false;
        }
        if (LinkenVisuals.getInstance().isPanic()) {
            return false;
        }

        String rawInput = extractRawClientCommand(message, prefix, commandPacket);
        if (rawInput == null) {
            return false;
        }

        rawInput = rawInput.stripLeading();
        if (rawInput.isBlank()) {
            return false;
        }

        String raw = commandManager.normalizeInput(rawInput);
        CommandDispatcher<CommandSource> dispatcher = commandManager.getDispatcher();
        CommandSource source = commandManager.getSource();

        try {
            dispatcher.execute(raw, source);
        } catch (CommandSyntaxException e) {
            sendUsageMessage(raw, dispatcher, source, e);
        }

        return true;
    }

    private String extractRawClientCommand(String message, String prefix, boolean commandPacket) {
        if (message == null || prefix == null) {
            return null;
        }

        String normalizedMessage = message;
        boolean hadLeadingSlash = commandPacket && normalizedMessage.startsWith("/");
        if (hadLeadingSlash) {
            normalizedMessage = normalizedMessage.substring(1);
        }

        String dotPrefixCommand = tryStandaloneDotPrefixCommand(message, commandPacket);
        if (dotPrefixCommand != null) {
            return dotPrefixCommand;
        }

        String direct = tryStripPrefix(normalizedMessage, prefix);
        if (direct != null) {
            return direct;
        }

        if (commandPacket && prefix.startsWith("/")) {
            String slashlessPrefix = prefix.substring(1);
            if (slashlessPrefix.isEmpty()) {
                return normalizedMessage;
            }
            String slashless = tryStripPrefix(normalizedMessage, slashlessPrefix);
            if (slashless != null) {
                return slashless;
            }
        }

        // Fallback for RUN_COMMAND/sendChatCommand flows where the click value is passed
        // as a plain command body (e.g. "config remove test" or "/config remove test").
        String localCommandCandidate = resolveKnownLocalCommandCandidate(normalizedMessage, commandPacket, hadLeadingSlash);
        if (localCommandCandidate != null) {
            return localCommandCandidate;
        }

        return null;
    }

    private String tryStripPrefix(String message, String prefix) {
        if (message == null || prefix == null) {
            return null;
        }
        if (prefix.isEmpty()) {
            return message;
        }
        if (!message.startsWith(prefix)) {
            return null;
        }
        return message.substring(prefix.length());
    }

    private String tryStandaloneDotPrefixCommand(String message, boolean commandPacket) {
        if (commandPacket || message == null) {
            return null;
        }
        String candidate = message.stripLeading();
        return ".prefix".equalsIgnoreCase(candidate.stripTrailing()) ? "prefix" : null;
    }

    private String resolveKnownLocalCommandCandidate(String message, boolean commandPacket, boolean hadLeadingSlash) {
        if (message == null) {
            return null;
        }
        String candidate = message.stripLeading();
        if (candidate.isEmpty()) {
            return null;
        }
        if ((commandPacket || hadLeadingSlash) && "prefix".equalsIgnoreCase(firstToken(candidate))) {
            return null;
        }
        return LinkenVisuals.getInstance().getCommandManager().findCommand(firstToken(candidate)) != null ? candidate : null;
    }

    private String firstToken(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int space = trimmed.indexOf(' ');
        return space >= 0 ? trimmed.substring(0, space) : trimmed;
    }

    private void sendUsageMessage(String input, CommandDispatcher<CommandSource> dispatcher, CommandSource source, CommandSyntaxException exception) {
        CommandManager commandManager = LinkenVisuals.getInstance().getCommandManager();
        String[] parts = input.trim().split("\\s+");
        String rootName = parts.length > 0 ? parts[0] : "";

        List<String> usages = new ArrayList<>();
        CommandNode<CommandSource> rootNode = dispatcher.getRoot().getChild(rootName);
        if (rootNode != null) {
            Collection<String> smartUsages = dispatcher.getSmartUsage(rootNode, source).values();
            if (!smartUsages.isEmpty()) {
                smartUsages.forEach(usage -> usages.add(rootName + " " + usage));
            } else {
                for (String usage : dispatcher.getAllUsage(rootNode, source, false)) {
                    usages.add(rootName + " " + usage);
                }
            }
        }

        if (usages.isEmpty()) {
            dispatcher.getSmartUsage(dispatcher.getRoot(), source).values().forEach(usages::add);
        }

        Command command = commandManager.findCommand(rootName);
        if (command != null) {
            CommandUi.sendCommandDetails(
                    HelpCommand.getTitle(command.getCommand()),
                    HelpCommand.getDescription(command.getCommand()),
                    command.getAliases(),
                    HelpCommand.getExamples(command.getCommand()),
                    exception.getRawMessage().getString(),
                    CommandUi.ERROR
            );
            return;
        }

        if (rootName.isBlank()) {
            HelpCommand.sendOverview(null, CommandUi.PRIMARY);
            return;
        }

        Command closestCommand = commandManager.findClosestCommand(rootName);
        if (closestCommand != null) {
            CommandUi.sendCommandDetails(
                    HelpCommand.getTitle(closestCommand.getCommand()),
                    HelpCommand.getDescription(closestCommand.getCommand()),
                    closestCommand.getAliases(),
                    HelpCommand.getExamples(closestCommand.getCommand()),
                    I18n.translate(
                            "commands.help.suggest",
                            commandManager.getPrefix() + rootName,
                            commandManager.getPrefix() + closestCommand.getCommand()
                    ),
                    CommandUi.ERROR
            );
            return;
        }

        HelpCommand.sendOverview(
                I18n.translate("commands.help.unknown", commandManager.getPrefix() + rootName),
                CommandUi.ERROR
        );
    }
}
