package ru.method.linkenvisuals.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;
import ru.method.linkenvisuals.utils.input.KeyNameUtils;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class BindCommand extends Command {
    private static final String DESCRIPTION_KEY = "commands.help.desc.bind";
    private static final List<String> EXAMPLES = List.of(
            "bind add <модуль> <клавиша>",
            "bind remove <модуль>",
            "bind list"
    );

    public BindCommand() {
        super("bind");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    CommandUi.sendCommandDetails(HelpCommand.getTitle(getCommand()), I18n.translate(DESCRIPTION_KEY), getAliases(), EXAMPLES);
                    return SINGLE_SUCCESS;
                })
                .then(literal("add")
                        .then(arg("module", word())
                                .suggests((context, builder1) -> {
                                    for (Module module : LinkenVisuals.getInstance().getModuleManager().getModules()) {
                                        builder1.suggest(module.getName());
                                    }
                                    return builder1.buildFuture();
                                })
                                .then(arg("bind", word())
                                        .suggests((context, builder1) -> {
                                            for (String suggestion : KeyNameUtils.suggestions(builder1.getRemaining())) {
                                                builder1.suggest(suggestion);
                                            }
                                            return builder1.buildFuture();
                                        })
                                        .executes(context -> {
                                            String moduleName = context.getArgument("module", String.class);
                                            String bindRaw = context.getArgument("bind", String.class);
                                            String usage = "bind add " + moduleName + " " + bindRaw;
                                            Module module = findModule(moduleName);
                                            if (module == null) {
                                                sendMessage(usage, I18n.translate("commands.bind.notfound", moduleName), CommandUi.ERROR);
                                                return SINGLE_SUCCESS;
                                            }
                                            Bind bind = KeyNameUtils.parseBind(bindRaw);
                                            if (bind == null) {
                                                sendMessage(usage, I18n.translate("commands.bind.unknown", bindRaw), CommandUi.ERROR);
                                                return SINGLE_SUCCESS;
                                            }
                                            module.setBind(bind);
                                            sendMessage(usage, I18n.translate("commands.bind.set", module.getName(), formatKey(bind)), CommandUi.SECONDARY);
                                            return SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(literal("remove")
                        .then(arg("module", word())
                                .suggests((context, builder1) -> {
                                    for (Module module : LinkenVisuals.getInstance().getModuleManager().getModules()) {
                                        builder1.suggest(module.getName());
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String moduleName = context.getArgument("module", String.class);
                                    String usage = "bind remove " + moduleName;
                                    Module module = findModule(moduleName);
                                    if (module == null) {
                                        sendMessage(usage, I18n.translate("commands.bind.notfound", moduleName), CommandUi.ERROR);
                                        return SINGLE_SUCCESS;
                                    }
                                    module.setBind(new Bind(-1, false));
                                    sendMessage(usage, I18n.translate("commands.bind.cleared", module.getName()), CommandUi.SECONDARY);
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            List<Module> bound = new ArrayList<>();
                            for (Module module : LinkenVisuals.getInstance().getModuleManager().getModules()) {
                                if (module.getBind() != null && !module.getBind().isEmpty()) {
                                    bound.add(module);
                                }
                            }
                            if (bound.isEmpty()) {
                                sendMessage("bind list", I18n.translate("commands.bind.empty"), CommandUi.SECONDARY);
                                return SINGLE_SUCCESS;
                            }

                            bound.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
                            MutableText message = CommandUi.commandCard("bind list", I18n.translate(DESCRIPTION_KEY));
                            CommandUi.appendLine(message, CommandUi.valueLine(I18n.translate("commands.ui.entries"), String.valueOf(bound.size())));
                            for (Module module : bound) {
                                String keyName = formatKey(module.getBind());
                                CommandUi.appendLine(message, CommandUi.listLine(module.getName(), " [" + keyName + "]"));
                            }
                            ChatUtils.sendMessage(message);
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private Module findModule(String name) {
        if (name == null) {
            return null;
        }
        for (Module module : LinkenVisuals.getInstance().getModuleManager().getModules()) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    private String formatKey(Bind bind) {
        return KeyNameUtils.formatBind(bind);
    }

    private void sendMessage(String usage, String message, int color) {
        CommandUi.sendCommandMessage(usage, I18n.translate(DESCRIPTION_KEY), message, color);
    }
}
