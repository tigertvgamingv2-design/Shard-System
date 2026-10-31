package com.thronecraft.examples.advanced.spawnback;

import com.thronecraft.examples.advanced.AdvancedShardSupport;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SpawnBackShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private static final String SPAWN_FILE = "spawn.yml";
    private static final String BACK_FILE = "back.yml";
    private final List<PluginCommand> commands = new ArrayList<>();
    private JavaPlugin plugin;
    private YamlConfiguration spawnData;
    private YamlConfiguration backData;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        spawnData = AdvancedShardSupport.loadData(plugin, SPAWN_FILE);
        backData = AdvancedShardSupport.loadData(plugin, BACK_FILE);
        commands.add(AdvancedShardSupport.register(plugin, "spawn", this));
        commands.add(AdvancedShardSupport.register(plugin, "setspawn", this));
        commands.add(AdvancedShardSupport.register(plugin, "back", this));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        for (PluginCommand command : commands) {
            AdvancedShardSupport.unregister(command, this);
        }
        commands.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
        if (spawnData != null) {
            AdvancedShardSupport.saveData(plugin, spawnData, SPAWN_FILE);
            spawnData = null;
        }
        if (backData != null) {
            AdvancedShardSupport.saveData(plugin, backData, BACK_FILE);
            backData = null;
        }
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.equals("setspawn")) {
            setSpawn(sender, args);
        } else if (name.equals("spawn")) {
            teleportSpawn(sender, args);
        } else {
            teleportBack(sender, args);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        String path = AdvancedShardSupport.playerPath(event.getEntity().getUniqueId());
        AdvancedShardSupport.writeLocation(backData, path, event.getEntity().getLocation());
        AdvancedShardSupport.saveData(plugin, backData, BACK_FILE);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && !event.getTo().equals(event.getFrom())) {
            String path = AdvancedShardSupport.playerPath(event.getPlayer().getUniqueId());
            AdvancedShardSupport.writeLocation(backData, path, event.getFrom());
            AdvancedShardSupport.saveData(plugin, backData, BACK_FILE);
        }
    }

    private void setSpawn(CommandSender sender, String[] args) {
        Player player = AdvancedShardSupport.requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!sender.hasPermission("shard.spawn.admin")) {
            AdvancedShardSupport.send(sender, "&cYou need shard.spawn.admin to set the server spawn.");
            return;
        }
        if (args.length != 0) {
            AdvancedShardSupport.send(sender, "&cUsage: /setspawn");
            return;
        }
        AdvancedShardSupport.writeLocation(spawnData, "spawn", player.getLocation());
        AdvancedShardSupport.saveData(plugin, spawnData, SPAWN_FILE);
        AdvancedShardSupport.send(sender, "&aServer spawn updated.");
    }

    private void teleportSpawn(CommandSender sender, String[] args) {
        Player player = AdvancedShardSupport.requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length != 0) {
            AdvancedShardSupport.send(sender, "&cUsage: /spawn");
            return;
        }
        Location location = AdvancedShardSupport.readLocation(spawnData, "spawn");
        if (location == null) {
            AdvancedShardSupport.send(sender, "&cServer spawn has not been set or its world is unavailable.");
            return;
        }
        if (player.teleport(location)) {
            AdvancedShardSupport.send(player, "&aTeleported to spawn.");
        } else {
            AdvancedShardSupport.send(player, "&cCould not teleport you to spawn.");
        }
    }

    private void teleportBack(CommandSender sender, String[] args) {
        Player player = AdvancedShardSupport.requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length != 0) {
            AdvancedShardSupport.send(sender, "&cUsage: /back");
            return;
        }
        String path = AdvancedShardSupport.playerPath(player.getUniqueId());
        Location location = AdvancedShardSupport.readLocation(backData, path);
        if (location == null) {
            AdvancedShardSupport.send(sender, "&cNo previous location is available.");
            return;
        }
        if (player.teleport(location)) {
            backData.set(path, null);
            AdvancedShardSupport.saveData(plugin, backData, BACK_FILE);
            AdvancedShardSupport.send(player, "&aReturned to your previous location.");
        } else {
            AdvancedShardSupport.send(player, "&cCould not teleport you back.");
        }
    }
}
