package com.thronecraft.examples.essentials.utility.time;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TimeShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public TimeShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("time");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.time")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /time day|night|<ticks> [world]");
            return true;
        }
        World world;
        if (args.length == 2) {
            world = Bukkit.getWorld(args[1]);
            if (world == null) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "World not found: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Specify a world when using this command from the console.");
            return true;
        }
        long ticks;
        if (args[0].equalsIgnoreCase("day")) {
            ticks = 1000L;
        } else if (args[0].equalsIgnoreCase("night")) {
            ticks = 13000L;
        } else {
            try {
                ticks = Long.parseLong(args[0]);
            } catch (NumberFormatException exception) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "Time must be day, night, or a non-negative tick value.");
                return true;
            }
            if (ticks < 0) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "Time ticks cannot be negative.");
                return true;
            }
        }
        world.setTime(ticks);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Set time in " + world.getName() + " to " + ticks + " ticks.");
        return true;
    }
}
