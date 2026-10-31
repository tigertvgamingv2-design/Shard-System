package com.thronecraft.examples.joincoordinates;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class JoinCoordinatesShard implements ShardModule {
    private Listener listener;
    private ShardSettings settings;

    @Override
    public void onEnable(JavaPlugin plugin) {
        settings = new ShardSettings(plugin, "joincoordinates",
                "&8[&bLocation&8] &7You are at &f{x}&7, &f{y}&7, &f{z} &7in &b{world}&7.");
        settings.registerCommand(sender -> {
            if (!(sender instanceof Player player)) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player.");
                return;
            }
            Location location = player.getLocation();
            com.thronecraft.net.shards.ShardMessages.send(player, settings.formatMessage(Map.of(
                    "player", player.getName(),
                    "x", Integer.toString(location.getBlockX()),
                    "y", Integer.toString(location.getBlockY()),
                    "z", Integer.toString(location.getBlockZ()),
                    "world", location.getWorld().getName())));
        });
        listener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                if (settings.isEnabled()) {
                    Location location = event.getPlayer().getLocation();
                    event.getPlayer().sendMessage(settings.formatMessage(Map.of(
                            "player", event.getPlayer().getName(),
                            "x", Integer.toString(location.getBlockX()),
                            "y", Integer.toString(location.getBlockY()),
                            "z", Integer.toString(location.getBlockZ()),
                            "world", location.getWorld().getName())));
                }
            }
        };
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (settings != null) {
            settings.unregisterCommand();
            settings = null;
        }
    }
}
