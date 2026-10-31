package com.thronecraft.examples.essentials.utility.speed;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SpeedShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public SpeedShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("speed");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.speed")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /speed <1-10> [player]");
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Speed must be a whole number from 1 to 10.");
            return true;
        }
        if (level < 1 || level > 10) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Speed must be from 1 to 10.");
            return true;
        }
        Player target;
        if (args.length == 2) {
            target = Bukkit.getPlayerExact(args[1]);
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
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /speed <1-10> <online player>");
            return true;
        }
        float speed = Math.max(0.1f, Math.min(1.0f, level / 10.0f));
        target.setWalkSpeed(speed);
        target.setFlySpeed(speed);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Set walk and flight speed for " + target.getName() + " to " + level + "/10.");
        if (target != sender) com.thronecraft.net.shards.ShardMessages.send(target, "Your walk and flight speed is now " + level + "/10.");
        return true;
    }
}
