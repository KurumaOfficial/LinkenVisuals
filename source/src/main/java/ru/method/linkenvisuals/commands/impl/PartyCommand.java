package ru.method.linkenvisuals.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.commands.Command;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.chat.CommandUi;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class PartyCommand extends Command {
    private static final String DESCRIPTION_KEY = "commands.help.desc.party";
    private static final List<String> EXAMPLES = List.of(
            "party create",
            "party join <код>",
            "party info",
            "party leave"
    );

    public PartyCommand() {
        super("party");
    }

    /** Пати работает только на мультиплеер-серверах (ключ пати — адрес сервера). */
    private boolean requireServer() {
        if (mc == null || mc.getCurrentServerEntry() == null) {
            ChatUtils.sendMessage(I18n.translate("commands.party.only_servers"));
            return false;
        }
        return true;
    }

    /** Точная причина ошибки вместо общего «недоступна»: сеть/бэкенд или неготовность клиента. */
    private static String errorKey(String error) {
        return switch (error == null ? "" : error) {
            case "request_failed" -> "commands.party.request_failed";
            case "bad_response", "bad_endpoint" -> "commands.party.bad_backend";
            default -> "commands.party.unavailable";
        };
    }

    /** Сообщение с кодом пати: код подчёркнут, по клику копируется, ховер — «Нажмите, чтобы скопировать». */
    private static MutableText messageWithCopyableCode(String key, String code) {
        MutableText codeText = Text.literal(code).styled(style -> style
                .withColor(TextColor.fromRgb(0xFFFFFF))
                .withUnderline(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(code))
                .withHoverEvent(new HoverEvent.ShowText(
                        Text.literal(I18n.translate("commands.party.copy_hint")))));
        return Text.translatable(key, codeText)
                .styled(style -> style.withColor(TextColor.fromRgb(0xC8C8C8)));
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    CommandUi.sendCommandDetails(HelpCommand.getTitle(getCommand()), I18n.translate(DESCRIPTION_KEY), getAliases(), EXAMPLES);
                    return SINGLE_SUCCESS;
                })
                .then(literal("create")
                        .executes(context -> {
                            if (!requireServer()) return SINGLE_SUCCESS;
                            LinkenVisuals instance = LinkenVisuals.getInstance();
                            if (instance == null || instance.getUserSyncManager() == null) {
                                ChatUtils.sendMessage(I18n.translate("commands.party.unavailable"));
                                return SINGLE_SUCCESS;
                            }
                            instance.getUserSyncManager().requestPartyCreate(
                                    code -> ChatUtils.sendMessage(messageWithCopyableCode("commands.party.created", code)),
                                    error -> ChatUtils.sendMessage(I18n.translate(errorKey(error)))
                            );
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("join")
                        .then(arg("code", word())
                                .executes(context -> {
                                    if (!requireServer()) return SINGLE_SUCCESS;
                                    String code = context.getArgument("code", String.class);
                                    LinkenVisuals instance = LinkenVisuals.getInstance();
                                    if (instance == null || instance.getUserSyncManager() == null) {
                                        ChatUtils.sendMessage(I18n.translate("commands.party.unavailable"));
                                        return SINGLE_SUCCESS;
                                    }
                                    instance.getUserSyncManager().requestPartyJoin(
                                            code,
                                            joined -> ChatUtils.sendMessage(I18n.translate("commands.party.joined", joined)),
                                            error -> {
                                                String key = switch (error == null ? "" : error) {
                                                    case "request_failed" -> "commands.party.request_failed";
                                                    case "bad_endpoint" -> "commands.party.bad_backend";
                                                    case "not_ready" -> "commands.party.unavailable";
                                                    // bad_response: сервер не вернул пати — такого кода нет.
                                                    default -> "commands.party.notfound";
                                                };
                                                ChatUtils.sendMessage(I18n.translate(key));
                                            }
                                    );
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("info")
                        .executes(context -> {
                            if (!requireServer()) return SINGLE_SUCCESS;
                            LinkenVisuals instance = LinkenVisuals.getInstance();
                            if (instance == null || instance.getUserSyncManager() == null) {
                                ChatUtils.sendMessage(I18n.translate("commands.party.unavailable"));
                                return SINGLE_SUCCESS;
                            }
                            var sync = instance.getUserSyncManager();
                            if (!sync.isInParty()) {
                                ChatUtils.sendMessage(I18n.translate("commands.party.not_in_party"));
                                return SINGLE_SUCCESS;
                            }
                            List<String> members = sync.partyMemberNames();
                            ChatUtils.sendMessage(messageWithCopyableCode("commands.party.info.code", sync.getPartyCode()));
                            ChatUtils.sendMessage(I18n.translate("commands.party.info.members",
                                    members.size(), String.join(", ", members)));
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("leave")
                        .executes(context -> {
                            if (!requireServer()) return SINGLE_SUCCESS;
                            LinkenVisuals instance = LinkenVisuals.getInstance();
                            if (instance == null || instance.getUserSyncManager() == null) {
                                ChatUtils.sendMessage(I18n.translate("commands.party.unavailable"));
                                return SINGLE_SUCCESS;
                            }
                            instance.getUserSyncManager().requestPartyLeave(
                                    unused -> ChatUtils.sendMessage(I18n.translate("commands.party.left")),
                                    error -> {
                                        String key = "not_in_party".equals(error)
                                                ? "commands.party.not_in_party"
                                                : errorKey(error);
                                        ChatUtils.sendMessage(I18n.translate(key));
                                    }
                            );
                            return SINGLE_SUCCESS;
                        })
                );
    }
}

