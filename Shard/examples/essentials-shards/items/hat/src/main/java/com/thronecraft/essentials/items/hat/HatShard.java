package com.thronecraft.essentials.items.hat;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class HatShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getCommand("hat") != null) plugin.getCommand("hat").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (args.length != 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /hat"); return true; }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) { com.thronecraft.net.shards.ShardMessages.send(sender, "Hold an item first."); return true; }
        ItemStack helmet = player.getInventory().getHelmet();
        player.getInventory().setHelmet(held.clone());
        player.getInventory().setItemInMainHand(helmet);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Equipped the held item as your helmet.");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("hat") != null) plugin.getCommand("hat").setExecutor(null);
        plugin = null;
    }
}
