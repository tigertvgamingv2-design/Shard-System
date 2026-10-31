package com.thronecraft.examples.essentials.moderation.mute;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class MuteShard implements ShardModule, CommandExecutor, Listener {
    private volatile JavaPlugin plugin;
    private org.bukkit.command.PluginCommand command;

    public MuteShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("mute");
        if (command != null) command.setExecutor(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (command != null) command.setExecutor(null);
        command = null;
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.mute")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /mute <player> [reason...]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "That player is not online.");
            return true;
        }
        if (sender instanceof Player player && !player.isOp() && target.isOp()) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You cannot target an operator.");
            return true;
        }
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Muted by staff";
        YamlConfiguration data = loadData();
        data.set("muted." + target.getUniqueId(), reason);
        if (!saveData(data)) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Could not save moderation data.");
            return true;
        }
        com.thronecraft.net.shards.ShardMessages.send(sender, "Muted " + target.getName() + ": " + reason);
        com.thronecraft.net.shards.ShardMessages.send(target, "You have been muted: " + reason);
        return true;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        JavaPlugin activePlugin = plugin;
        if (activePlugin == null) return;
        String reason = YamlConfiguration.loadConfiguration(
                new File(activePlugin.getDataFolder(), "moderation.yml"))
                .getString("muted." + event.getPlayer().getUniqueId());
        if (reason != null) {
            event.setCancelled(true);
            com.thronecraft.net.shards.ShardMessages.send(event.getPlayer(), "You are muted: " + reason);
        }
    }

    private YamlConfiguration loadData() {
        File file = new File(plugin.getDataFolder(), "moderation.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private boolean saveData(YamlConfiguration data) {
        File file = new File(plugin.getDataFolder(), "moderation.yml");
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            data.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save moderation.yml: " + exception.getMessage());
            return false;
        }
    }
}
