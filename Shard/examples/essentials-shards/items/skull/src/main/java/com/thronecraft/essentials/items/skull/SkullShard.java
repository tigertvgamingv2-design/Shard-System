package com.thronecraft.essentials.items.skull;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class SkullShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getCommand("skull") != null) plugin.getCommand("skull").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /skull [player]"); return true; }
        if (!(sender instanceof Player recipient)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        String ownerName = args.length == 1 ? args[0] : recipient.getName();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerName);
            meta.setOwningPlayer(owner);
            head.setItemMeta(meta);
        }
        Map<Integer, ItemStack> leftovers = recipient.getInventory().addItem(head);
        leftovers.values().forEach(item -> recipient.getWorld().dropItemNaturally(recipient.getLocation(), item));
        com.thronecraft.net.shards.ShardMessages.send(sender, "Gave " + recipient.getName() + " a player head for " + ownerName + ".");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("skull") != null) plugin.getCommand("skull").setExecutor(null);
        plugin = null;
    }
}
