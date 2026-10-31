package com.thronecraft.examples.worldtime;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class WorldTimeShard implements ShardModule {
    private Listener listener;
    private ShardSettings settings;

    @Override
    public void onEnable(JavaPlugin plugin) {
        settings = new ShardSettings(plugin, "worldtime",
                "&8[&bTime&8] &7World time: &f{time}&7.");
        settings.registerCommand(sender -> {
            if (!(sender instanceof Player player)) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player.");
                return;
            }
            long ticks = player.getWorld().getTime();
            long totalMinutes = (ticks * 60) / 1000;
            long hours = (6 + totalMinutes / 60) % 24;
            long minutes = totalMinutes % 60;
            com.thronecraft.net.shards.ShardMessages.send(player, settings.formatMessage(Map.of(
                    "player", player.getName(),
                    "time", String.format("%02d:%02d", hours, minutes))));
        });
        listener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                if (settings.isEnabled()) {
                    long ticks = event.getPlayer().getWorld().getTime();
                    long totalMinutes = (ticks * 60) / 1000;
                    long hours = (6 + totalMinutes / 60) % 24;
                    long minutes = totalMinutes % 60;
                    event.getPlayer().sendMessage(settings.formatMessage(Map.of(
                            "player", event.getPlayer().getName(),
                            "time", String.format("%02d:%02d", hours, minutes))));
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
