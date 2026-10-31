package com.thronecraft.examples.essentials.moderation.warnings;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class WarningsShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private org.bukkit.command.PluginCommand command;

    public WarningsShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("warnings");
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
        if (!sender.hasPermission("shard.essentials.warnings")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length > 1) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /warnings [player]");
            return true;
        }
        Player target;
        if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "That player is not online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /warnings <online player>");
            return true;
        }
        File file = new File(plugin.getDataFolder(), "moderation.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        List<String> warnings = data.getStringList("warnings." + target.getUniqueId());
        com.thronecraft.net.shards.ShardMessages.send(sender, "Warnings for " + target.getName() + " (" + warnings.size() + "):");
        if (warnings.isEmpty()) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "No warnings found.");
        } else {
            for (int index = 0; index < warnings.size(); index++) {
                com.thronecraft.net.shards.ShardMessages.send(sender, (index + 1) + ". " + warnings.get(index));
            }
        }
        return true;
    }
}
