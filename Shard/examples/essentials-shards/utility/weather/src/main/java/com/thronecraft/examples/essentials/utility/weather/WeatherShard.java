package com.thronecraft.examples.essentials.utility.weather;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class WeatherShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public WeatherShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("weather");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.weather")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /weather clear|rain|thunder [world]");
            return true;
        }
        String weather = args[0].toLowerCase(java.util.Locale.ROOT);
        if (!weather.equals("clear") && !weather.equals("rain") && !weather.equals("thunder")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Weather must be clear, rain, or thunder.");
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
        switch (weather) {
            case "clear" -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "thunder" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
        }
        com.thronecraft.net.shards.ShardMessages.send(sender, "Set weather in " + world.getName() + " to " + weather + ".");
        return true;
    }
}
