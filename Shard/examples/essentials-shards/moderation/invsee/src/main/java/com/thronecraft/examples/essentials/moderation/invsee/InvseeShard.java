package com.thronecraft.examples.essentials.moderation.invsee;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class InvseeShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public InvseeShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("invsee");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.invsee")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Only players can use this command.");
            return true;
        }
        if (args.length != 1) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /invsee <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "That player is not online.");
            return true;
        }
        if (!viewer.isOp() && target.isOp()) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You cannot inspect an operator's inventory.");
            return true;
        }
        viewer.openInventory(target.getInventory());
        return true;
    }
}
