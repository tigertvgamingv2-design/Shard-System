package com.thronecraft.examples.essentials.moderation.kick;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class KickShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public KickShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("kick");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.kick")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /kick <player> [reason...]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "That player is not online.");
            return true;
        }
        if (sender instanceof Player player && !player.isOp() && target.isOp()) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You cannot target an operator.");
            return true;
        }
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Kicked by staff";
        target.kickPlayer(reason);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Kicked " + target.getName() + ": " + reason);
        return true;
    }
}
