package com.thronecraft.examples.nocreepergrief;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardSettings;
import org.bukkit.entity.Creeper;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

public class NoCreeperGriefShard implements ShardModule {
    private Listener listener;
    private ShardSettings settings;

    @Override
    public void onEnable(JavaPlugin plugin) {
        settings = new ShardSettings(plugin, "nocreepergrief", null);
        settings.registerCommand(sender -> com.thronecraft.net.shards.ShardMessages.send(sender, "Creeper block protection is "
                + (settings.isEnabled() ? "enabled." : "disabled.")));
        listener = new Listener() {
            @EventHandler
            public void onExplode(EntityExplodeEvent event) {
                if (settings.isEnabled() && event.getEntity() instanceof Creeper) {
                    event.blockList().clear();
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
