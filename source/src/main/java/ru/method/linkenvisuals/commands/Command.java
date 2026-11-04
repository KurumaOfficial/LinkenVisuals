package ru.method.linkenvisuals.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import ru.method.linkenvisuals.utils.Wrapper;
import lombok.Getter;
import net.minecraft.command.CommandSource;

@Getter
public abstract class Command implements Wrapper {
    private final String command;
    private final String[] aliases;

    public Command(String command) {
        this(command, new String[0]);
    }

    public Command(String command, String... aliases) {
        this.command = command;
        this.aliases = aliases == null ? new String[0] : aliases;
    }

    public abstract void execute(LiteralArgumentBuilder<CommandSource> builder);

    public void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.literal(command);
        execute(builder);
        LiteralCommandNode<CommandSource> primary = dispatcher.register(builder);

        for (String alias : aliases) {
            if (alias == null) continue;
            String trimmed = alias.trim();
            if (trimmed.isEmpty() || trimmed.equals(command)) continue;

            dispatcher.register(LiteralArgumentBuilder.<CommandSource>literal(trimmed).redirect(primary));
        }
    }

    protected <T> RequiredArgumentBuilder<CommandSource, T> arg(String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected LiteralArgumentBuilder<CommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}
