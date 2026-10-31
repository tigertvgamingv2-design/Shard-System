package com.thronecraft.examples.essentials.moderation.unmute;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class UnmuteShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private org.bukkit.command.PluginCommand command;

    public UnmuteShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("unmute");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.unmute")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length != 1) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /unmute <player>");
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
        YamlConfiguration data = loadData();
        String path = "muted." + target.getUniqueId();
        if (!data.contains(path)) {
            com.thronecraft.net.shards.ShardMessages.send(sender, target.getName() + " is not muted.");
            return true;
        }
        data.set(path, null);
        if (!saveData(data)) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Could not save moderation data.");
            return true;
        }
        com.thronecraft.net.shards.ShardMessages.send(sender, "Unmuted " + target.getName() + ".");
        com.thronecraft.net.shards.ShardMessages.send(target, "You have been unmuted.");
        return true;
    }

    private YamlConfiguration loadData() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "moderation.yml"));
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
