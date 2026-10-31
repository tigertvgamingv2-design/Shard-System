package com.example.customergreeting;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardMessages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomerGreetingShard implements ShardModule, Listener {
    @Override
    public void onEnable(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ShardMessages.send(event.getPlayer(), "&aWelcome to the server!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
    }
}
