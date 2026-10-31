package com.thronecraft.examples.essentials.utility.heal;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class HealShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public HealShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("heal");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.heal")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length > 1) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /heal [player]");
            return true;
        }
        Player target;
        if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "That player is not online.");
                return true;
            }
            if (sender instanceof Player player && !player.isOp() && target.isOp()) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "You cannot target an operator.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /heal <online player>");
            return true;
        }
        target.setHealth(target.getMaxHealth());
        target.setFireTicks(0);
        target.setFallDistance(0);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Healed " + target.getName() + ".");
        if (target != sender) com.thronecraft.net.shards.ShardMessages.send(target, "You have been healed.");
        return true;
    }
}
