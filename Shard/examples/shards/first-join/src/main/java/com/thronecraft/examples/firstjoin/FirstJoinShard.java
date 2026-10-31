package com.thronecraft.examples.firstjoin;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class FirstJoinShard implements ShardModule {
    private Listener listener;
    private ShardSettings settings;

    @Override
    public void onEnable(JavaPlugin plugin) {
        settings = new ShardSettings(plugin, "firstjoin",
                "&8[&6Welcome&8] &e{player}&6, welcome! &7Have fun exploring.");
        settings.registerCommand(sender -> {
            if (sender instanceof Player player) {
                com.thronecraft.net.shards.ShardMessages.send(player, settings.formatMessage(Map.of("player", player.getName())));
            } else {
                com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player.");
            }
        });
        listener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                if (settings.isEnabled() && !event.getPlayer().hasPlayedBefore()) {
                    event.getPlayer().sendMessage(settings.formatMessage(
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
