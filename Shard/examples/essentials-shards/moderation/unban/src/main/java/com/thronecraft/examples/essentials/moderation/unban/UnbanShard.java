package com.thronecraft.examples.essentials.moderation.unban;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class UnbanShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public UnbanShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("unban");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.unban")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length != 1) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /unban <player>");
            return true;
        }
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        if (!banList.isBanned(args[0])) {
            com.thronecraft.net.shards.ShardMessages.send(sender, args[0] + " is not banned.");
            return true;
        }
        banList.pardon(args[0]);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Unbanned " + args[0] + ".");
        return true;
    }
}
