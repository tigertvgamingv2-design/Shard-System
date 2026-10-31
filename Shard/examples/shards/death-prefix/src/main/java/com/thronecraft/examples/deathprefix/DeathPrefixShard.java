package com.thronecraft.examples.deathprefix;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardSettings;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class DeathPrefixShard implements ShardModule {
    private Listener listener;
    private ShardSettings settings;

    @Override
    public void onEnable(JavaPlugin plugin) {
        settings = new ShardSettings(plugin, "deathprefix",
                "&8[&cDeath&8] &r{death_message}");
        settings.registerCommand(sender -> com.thronecraft.net.shards.ShardMessages.send(sender, settings.formatMessage(Map.of(
                "player", sender.getName(),
                "death_message", sender.getName() + " died"))));
        listener = new Listener() {
            @EventHandler
            public void onDeath(PlayerDeathEvent event) {
                String deathMessage = event.getDeathMessage();
                if (settings.isEnabled() && deathMessage != null && !deathMessage.isBlank()) {
                    event.setDeathMessage(settings.formatMessage(Map.of(
                            "player", event.getEntity().getName(),
                            "death_message", deathMessage)));
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
