package com.thronecraft.net.shards;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShardMessages {
    private static final String DEFAULT_PREFIX = "&8[&bShard&8] &7";
    private static final Pattern HEX_COLOR = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Pattern FORMATTING_CODE =
            Pattern.compile("(?i)(?:&#[a-f0-9]{6}|&[0-9a-fk-or]|§[0-9a-fk-or])");

    private static volatile JavaPlugin plugin;

    private ShardMessages() {
    }

    public static void initialize(JavaPlugin hostPlugin) {
        plugin = Objects.requireNonNull(hostPlugin, "hostPlugin");
    }

    public static void clear(JavaPlugin hostPlugin) {
        if (plugin == hostPlugin) {
            plugin = null;
        }
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(format(message));
    }

    public static void broadcast(String message) {
        Bukkit.broadcastMessage(format(message));
    }

    public static String format(String message) {
        JavaPlugin hostPlugin = plugin;
        if (hostPlugin == null) {
            throw new IllegalStateException("Shard message formatting is not initialized.");
        }
        String prefix = hostPlugin.getConfig().getString("messages.prefix", DEFAULT_PREFIX);
        return colorize((prefix == null ? "" : prefix) + styleMessage(message));
    }

    private static String styleMessage(String message) {
        if (FORMATTING_CODE.matcher(message).find()) {
            return message;
        }

        String normalized = message.stripLeading().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("usage:")) {
            String trimmed = message.stripLeading();
            return "&eUsage: &f" + trimmed.substring("usage:".length()).stripLeading();
        }
        if (startsWithAny(normalized, "you do not", "you don't", "you cannot", "you can't", "you lack", "only ",
                "invalid ", "unknown ", "could not", "couldn't", "cannot ", "not enough",
                "not found", "must ", "enter a valid", "that player", "that material",
                "that world", "this command", "failed", "error:", "mail could not",
                "your nickname could not")) {
            return "&c" + message;
        }
        if (startsWithAny(normalized, "no ", "already ", "please wait", "request expired",
                "teleport request expired", "page numbers", "your mailbox is empty")) {
            return "&e" + message;
        }
        if (startsWithAny(normalized, "set ", "gave ", "sent ", "paid ", "saved ",
                "created ", "deleted ", "enabled ", "disabled ", "healed ", "repaired ",
                "updated ", "added ", "removed ", "toggled ", "cleared ", "you are now ",
                "you are no longer ", "nickname reset", "nickname set", "teleport request sent",
                "teleport request accepted", "teleport request cancelled", "teleported ")) {
            return "&a" + message;
        }
        return "&f" + message;
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String colorize(String message) {
        Matcher matcher = HEX_COLOR.matcher(message);
        StringBuilder colored = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(colored, Matcher.quoteReplacement(
                    ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(colored);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', colored.toString());
    }
}
