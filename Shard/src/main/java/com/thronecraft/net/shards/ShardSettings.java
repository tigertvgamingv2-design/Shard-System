package com.thronecraft.net.shards;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class ShardSettings implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final String commandName;
    private final String defaultMessage;
    private final File configFile;
    private YamlConfiguration config;
    private PluginCommand command;
    private Consumer<CommandSender> commandAction;

    public ShardSettings(JavaPlugin plugin, String commandName, String defaultMessage) {
        this.plugin = plugin;
        this.commandName = commandName;
        this.defaultMessage = defaultMessage;

        File settingsFolder = new File(plugin.getDataFolder(), "shard-settings");
        if (!settingsFolder.exists() && !settingsFolder.mkdirs()) {
            throw new IllegalStateException("Could not create shard settings folder: " + settingsFolder);
        }
        if (!settingsFolder.isDirectory()) {
            throw new IllegalStateException("Shard settings path is not a directory: " + settingsFolder);
        }
        configFile = new File(settingsFolder, commandName.toLowerCase(Locale.ROOT) + ".yml");
        reloadConfiguration();
    }

    public void registerCommand(Consumer<CommandSender> commandAction) {
        command = plugin.getCommand(commandName);
        if (command == null) {
            throw new IllegalStateException("Command '" + commandName + "' is missing from plugin.yml.");
        }
        this.commandAction = commandAction;
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    public void unregisterCommand() {
        if (command != null) {
            if (command.getExecutor() == this) {
                command.setExecutor(null);
            }
            if (command.getTabCompleter() == this) {
                command.setTabCompleter(null);
            }
            command = null;
            commandAction = null;
        }
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    public String formatMessage(Map<String, String> placeholders) {
        if (defaultMessage == null) {
            throw new IllegalStateException("This shard does not have a configurable message.");
        }
        String message = config.getString("message", defaultMessage);
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message = message.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return ShardMessages.format(message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (args.length == 0) {
            if (commandAction == null) {
                sendHelp(sender, label);
            } else if (!isEnabled()) {
                ShardMessages.send(sender, ChatColor.RED + commandName + " is disabled.");
            } else {
                commandAction.accept(sender);
            }
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!subcommand.equals("help") && !subcommand.equals("preview")
                && !sender.hasPermission("shard.admin")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to change shard settings.");
            return true;
        }

        switch (subcommand) {
            case "help" -> sendHelp(sender, label);
            case "status" -> ShardMessages.send(sender, ChatColor.AQUA + commandName
                    + ChatColor.GRAY + " is " + (isEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled")
                    + (defaultMessage == null ? "" : ChatColor.GRAY + "; message: "
                    + ChatColor.WHITE + config.getString("message", defaultMessage)));
            case "enable", "on" -> setEnabled(sender, true);
            case "disable", "off" -> setEnabled(sender, false);
            case "reload" -> {
                reloadConfiguration();
                ShardMessages.send(sender, ChatColor.GREEN + "Reloaded " + commandName + " settings.");
            }
            case "set" -> setMessage(sender, args);
            case "reset" -> resetMessage(sender);
            case "preview" -> preview(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
        if (!sender.hasPermission("shard.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "status", "enable", "disable", "on", "off", "reload"));
            if (defaultMessage != null) {
                options.addAll(List.of("set", "reset", "preview"));
            }
            return filter(options, args[0]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        ShardMessages.send(sender, ChatColor.AQUA + "/" + label + " - Run this shard's feature");
        ShardMessages.send(sender, ChatColor.GRAY + "/" + label + " status|enable|disable|reload");
        if (defaultMessage != null) {
            ShardMessages.send(sender, ChatColor.GRAY + "/" + label + " set <message> - Set a colored message");
            ShardMessages.send(sender, ChatColor.GRAY + "/" + label + " reset|preview");
        }
    }

    private void setEnabled(CommandSender sender, boolean enabled) {
        config.set("enabled", enabled);
        saveConfiguration();
        ShardMessages.send(sender, (enabled ? ChatColor.GREEN : ChatColor.YELLOW)
                + commandName + (enabled ? " enabled." : " disabled."));
    }

    private void setMessage(CommandSender sender, String[] args) {
        if (defaultMessage == null) {
            ShardMessages.send(sender, ChatColor.RED + "This shard has no configurable message.");
            return;
        }
        if (args.length < 2) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /" + commandName + " set <message>");
            return;
        }
        config.set("message", String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        saveConfiguration();
        ShardMessages.send(sender, ChatColor.GREEN + "Updated message for " + commandName + ".");
    }

    private void resetMessage(CommandSender sender) {
        if (defaultMessage == null) {
            ShardMessages.send(sender, ChatColor.RED + "This shard has no configurable message.");
            return;
        }
        config.set("message", defaultMessage);
        saveConfiguration();
        ShardMessages.send(sender, ChatColor.GREEN + "Reset message for " + commandName + ".");
    }

    private void preview(CommandSender sender) {
        if (defaultMessage == null) {
            ShardMessages.send(sender, ChatColor.RED + "This shard has no configurable message.");
            return;
        }
        sender.sendMessage(formatMessage(Map.of(
                "player", sender.getName(),
                "x", "100",
                "y", "64",
                "z", "-20",
                "world", "world",
                "time", "12:00",
                "death_message", sender.getName() + " died",
                "players", "5")));
    }

    private void reloadConfiguration() {
        config = YamlConfiguration.loadConfiguration(configFile);
        boolean changed = false;
        if (!config.contains("enabled")) {
            config.set("enabled", true);
            changed = true;
        }
        if (defaultMessage != null && !config.contains("message")) {
            config.set("message", defaultMessage);
            changed = true;
        }
        if (changed) {
            saveConfiguration();
        }
    }

    private void saveConfiguration() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save shard settings to " + configFile, exception);
        }
    }

    private List<String> filter(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

}
