package com.thronecraft.examples.sleepnotice;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardSettings;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class SleepNoticeShard implements ShardModule {
    private Listener listener;
    private ShardSettings settings;

    @Override
    public void onEnable(JavaPlugin plugin) {
        settings = new ShardSettings(plugin, "sleepnotice",
                "&8[&bSleep&8] &f{player} &7is going to sleep.");
        settings.registerCommand(sender -> plugin.getServer().broadcastMessage(
                settings.formatMessage(Map.of("player", sender.getName()))));
        listener = new Listener() {
            @EventHandler
            public void onBedEnter(PlayerBedEnterEvent event) {
                if (settings.isEnabled()
                        && event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
                    plugin.getServer().broadcastMessage(settings.formatMessage(
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
