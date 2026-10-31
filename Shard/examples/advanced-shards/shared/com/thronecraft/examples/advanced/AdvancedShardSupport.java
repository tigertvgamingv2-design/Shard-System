package com.thronecraft.examples.advanced;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AdvancedShardSupport {
    private AdvancedShardSupport() {
    }

    public static PluginCommand register(JavaPlugin plugin, String name, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Command '" + name + "' is missing from plugin.yml.");
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
        return command;
    }

    public static void unregister(PluginCommand command, CommandExecutor executor) {
        if (command != null) {
            if (command.getExecutor() == executor) {
                command.setExecutor(null);
            }
            if (command.getTabCompleter() == executor) {
                command.setTabCompleter(null);
            }
        }
    }

    public static YamlConfiguration loadData(JavaPlugin plugin, String fileName) {
        File folder = new File(plugin.getDataFolder(), "advanced");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Could not create advanced shard data folder: " + folder);
        }
        if (!folder.isDirectory()) {
            throw new IllegalStateException("Advanced shard data path is not a directory: " + folder);
        }
        return YamlConfiguration.loadConfiguration(new File(folder, fileName));
    }

    public static void saveData(JavaPlugin plugin, YamlConfiguration data, String fileName) {
        File folder = new File(plugin.getDataFolder(), "advanced");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Could not create advanced shard data folder: " + folder);
        }
        try {
            data.save(new File(folder, fileName));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save advanced shard data: " + fileName, exception);
        }
    }

    public static void send(CommandSender sender, String message) {
        com.thronecraft.net.shards.ShardMessages.send(sender, message);
    }

    public static String color(String message) {
        StringBuilder colored = new StringBuilder();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("&#([a-fA-F0-9]{6})").matcher(message);
        while (matcher.find()) {
            matcher.appendReplacement(colored, java.util.regex.Matcher.quoteReplacement(
                    net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(colored);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', colored.toString());
    }

    public static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "&cThis command can only be used by a player.");
        return null;
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,16}");
    }

    public static List<String> matching(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    public static List<String> keys(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public static void writeLocation(YamlConfiguration data, String path, Location location) {
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
    }

    public static Location readLocation(YamlConfiguration data, String path) {
        String worldName = data.getString(path + ".world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null || !data.contains(path + ".x")
                || !data.contains(path + ".y") || !data.contains(path + ".z")) {
            return null;
        }
        return new Location(world,
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"),
                (float) data.getDouble(path + ".pitch"));
    }

    public static String playerPath(UUID playerId) {
        return playerId.toString();
    }

    public static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    public static String commandUsage(Command command) {
        return command.getUsage().isBlank() ? "/" + command.getName() : command.getUsage();
    }
}
