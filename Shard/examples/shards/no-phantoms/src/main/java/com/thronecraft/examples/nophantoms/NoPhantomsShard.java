package com.thronecraft.examples.nophantoms;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

public class NoPhantomsShard implements ShardModule {
    private Listener listener;
    private ShardSettings settings;

    @Override
    public void onEnable(JavaPlugin plugin) {
        settings = new ShardSettings(plugin, "nophantoms", null);
        settings.registerCommand(sender -> com.thronecraft.net.shards.ShardMessages.send(sender, "Phantom spawn protection is "
                + (settings.isEnabled() ? "enabled." : "disabled.")));
        listener = new Listener() {
            @EventHandler
            public void onSpawn(CreatureSpawnEvent event) {
                if (settings.isEnabled() && event.getEntityType() == EntityType.PHANTOM) {
                    event.setCancelled(true);
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
