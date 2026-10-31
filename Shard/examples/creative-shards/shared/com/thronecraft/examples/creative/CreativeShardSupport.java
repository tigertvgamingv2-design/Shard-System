package com.thronecraft.examples.creative;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CreativeShardSupport {
    private static final Pattern HEX_COLOR = Pattern.compile("&#([a-fA-F0-9]{6})");

    private CreativeShardSupport() {
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
        if (command.getExecutor() == executor) {
            command.setExecutor(null);
        }
        if (command.getTabCompleter() == executor) {
            command.setTabCompleter(null);
        }
    }

    public static YamlConfiguration loadData(JavaPlugin plugin, String fileName) {
        File directory = new File(plugin.getDataFolder(), "creative");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create creative shard data folder: " + directory);
        }
        if (!directory.isDirectory()) {
            throw new IllegalStateException("Creative shard data path is not a directory: " + directory);
        }
        return YamlConfiguration.loadConfiguration(new File(directory, fileName));
    }

    public static void saveData(JavaPlugin plugin, YamlConfiguration data, String fileName) {
        File directory = new File(plugin.getDataFolder(), "creative");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create creative shard data folder: " + directory);
        }
        try {
            data.save(new File(directory, fileName));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save creative shard data: " + fileName, exception);
        }
    }

    public static void send(CommandSender sender, String message) {
        com.thronecraft.net.shards.ShardMessages.send(sender, color("&8[&#B47CFFShard&8] &r" + message));
    }

    public static String color(String message) {
        Matcher matcher = HEX_COLOR.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(result);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', result.toString());
    }

    public static String biomeKey(Biome biome) {
        return "minecraft:" + biome.name().toLowerCase(Locale.ROOT);
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

    public static String playerPath(UUID playerId) {
        return playerId.toString();
    }

    public static List<String> matching(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    public static Location blockLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX() + 0.5,
                location.getBlockY() + 0.2, location.getBlockZ() + 0.5);
    }

    public static World world(String name) {
        return name == null ? null : Bukkit.getWorld(name);
    }
}
