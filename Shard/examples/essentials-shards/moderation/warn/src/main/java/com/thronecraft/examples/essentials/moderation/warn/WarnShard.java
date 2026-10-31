package com.thronecraft.examples.essentials.moderation.warn;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WarnShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private org.bukkit.command.PluginCommand command;

    public WarnShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("warn");
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
        if (!sender.hasPermission("shard.essentials.warn")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /warn <player> <reason...>");
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
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String warning = Instant.now() + " — " + sender.getName() + ": " + reason;
        File file = new File(plugin.getDataFolder(), "moderation.yml");
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String path = "warnings." + target.getUniqueId();
        List<String> warnings = new ArrayList<>(data.getStringList(path));
        warnings.add(warning);
        data.set(path, warnings);
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save moderation.yml: " + exception.getMessage());
            com.thronecraft.net.shards.ShardMessages.send(sender, "Could not save moderation data.");
            return true;
        }
        com.thronecraft.net.shards.ShardMessages.send(sender, "Warned " + target.getName() + ": " + reason);
        com.thronecraft.net.shards.ShardMessages.send(target, "You have been warned: " + reason);
        return true;
    }
}
