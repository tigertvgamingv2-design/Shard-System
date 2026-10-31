package com.thronecraft.essentials.economy.paytoggle;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class PayToggleShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File file;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "economy.yml");
        if (plugin.getCommand("paytoggle") != null) plugin.getCommand("paytoggle").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (args.length != 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /paytoggle"); return true; }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String path = "payments." + player.getUniqueId();
        boolean enabled = !data.getBoolean(path, true);
        data.set(path, enabled);
        try { data.save(file); } catch (IOException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Payment preference could not be saved."); plugin.getLogger().severe("Could not save economy.yml: " + ex.getMessage()); return true; }
        com.thronecraft.net.shards.ShardMessages.send(sender, "Payments are now " + (enabled ? "enabled." : "disabled."));
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("paytoggle") != null) plugin.getCommand("paytoggle").setExecutor(null);
        plugin = null; file = null;
    }
}
