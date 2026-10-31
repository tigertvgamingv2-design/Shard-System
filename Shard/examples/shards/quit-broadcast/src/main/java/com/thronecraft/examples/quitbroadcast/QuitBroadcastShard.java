package com.thronecraft.examples.quitbroadcast;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardSettings;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class QuitBroadcastShard implements ShardModule {
    private Listener listener;
    private ShardSettings settings;

    @Override
    public void onEnable(JavaPlugin plugin) {
        settings = new ShardSettings(plugin, "quitbroadcast",
                "&8[&c-&8] &f{player} &cleft the server.");
        settings.registerCommand(sender -> plugin.getServer().broadcastMessage(
                settings.formatMessage(Map.of("player", sender.getName()))));
        listener = new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                if (settings.isEnabled()) {
                    event.setQuitMessage(settings.formatMessage(
                            Map.of("player", event.getPlayer().getName())));
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
