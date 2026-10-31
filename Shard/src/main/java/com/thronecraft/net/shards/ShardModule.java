package com.thronecraft.net.shards;

import org.bukkit.plugin.java.JavaPlugin;

public interface ShardModule {
    void onEnable(JavaPlugin plugin) throws Exception;

    void onDisable() throws Exception;
}
