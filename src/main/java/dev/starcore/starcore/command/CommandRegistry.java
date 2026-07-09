package dev.starcore.starcore.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 命令注册表
 */
public final class CommandRegistry {

    private final Map<String, RegisteredCommand> commands = new ConcurrentHashMap<>();

    public CommandRegistry register(
        String name,
        String description,
        String usage,
        List<String> aliases,
        CommandExecutor executor,
        TabExecutor tabCompleter
    ) {
        RegisteredCommand cmd = new RegisteredCommand(name, description, usage, aliases, executor, tabCompleter);
        commands.put(name.toLowerCase(), cmd);
        return this;
    }

    public Optional<RegisteredCommand> get(String name) {
        return Optional.ofNullable(commands.get(name.toLowerCase()));
    }

    public Map<String, RegisteredCommand> getAll() {
        return Collections.unmodifiableMap(commands);
    }

    public List<String> getCommandNames() {
        return new ArrayList<>(commands.keySet());
    }

    public record RegisteredCommand(
        String name,
        String description,
        String usage,
        List<String> aliases,
        CommandExecutor executor,
        TabExecutor tabCompleter
    ) {
        public boolean matches(String input) {
            return name.equalsIgnoreCase(input) || aliases.stream().anyMatch(a -> a.equalsIgnoreCase(input));
        }
    }
}
