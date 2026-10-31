package com.thronecraft.essentials.chat.broadcast;

import com.thronecraft.net.shards.ShardMessages;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public final class BroadcastShard implements ShardModule, CommandExecutor {
    private static final int MAX_MESSAGE_LENGTH = 256;

    private PluginCommand command;

    @Override
    public void onEnable(JavaPlugin plugin) {
        registerPermission("shard.essentials.broadcast", PermissionDefault.OP);
        registerPermission("shard.essentials.broadcast.color", PermissionDefault.OP);
        command = plugin.getCommand("broadcast");
        if (command == null) {
            throw new IllegalStateException("Missing /broadcast command in plugin.yml.");
        }
        command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
        }
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.broadcast")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to broadcast.");
            return true;
        }
        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /broadcast <message...>");
            return true;
        }
        if (message.length() > MAX_MESSAGE_LENGTH
                || message.chars().anyMatch(Character::isISOControl)) {
            ShardMessages.send(sender, ChatColor.RED + "Broadcasts must be at most "
                    + MAX_MESSAGE_LENGTH + " characters and cannot contain control characters.");
            return true;
        }
        if (!sender.hasPermission("shard.essentials.broadcast.color")) {
            message = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            message = ChatColor.translateAlternateColorCodes('&', message);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Broadcast] " + ChatColor.WHITE + message);
        return true;
    }

    private void registerPermission(String name, PermissionDefault defaultValue) {
        if (Bukkit.getPluginManager().getPermission(name) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(name, defaultValue));
        }
    }
}
