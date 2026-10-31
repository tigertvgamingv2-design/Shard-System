package com.thronecraft.examples.essentials.utility.workbench;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class WorkbenchShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public WorkbenchShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("workbench");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.workbench")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length != 0) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /workbench");
            return true;
        }
        if (!(sender instanceof Player player)) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Only players can open a workbench.");
            return true;
        }
        player.openWorkbench(null, true);
        return true;
    }
}
