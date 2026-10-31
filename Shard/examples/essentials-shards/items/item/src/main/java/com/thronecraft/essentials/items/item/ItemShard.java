package com.thronecraft.essentials.items.item;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getCommand("item") != null) plugin.getCommand("item").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (args.length != 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /item"); return true; }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) { com.thronecraft.net.shards.ShardMessages.send(sender, "Your hand is empty."); return true; }
        Material material = item.getType();
        String durability = "not applicable";
        if (material.getMaxDurability() > 0) {
            int damage = item.getItemMeta() instanceof Damageable d ? d.getDamage() : 0;
            durability = damage + "/" + material.getMaxDurability() + " damage";
        }
        com.thronecraft.net.shards.ShardMessages.send(sender, "Held item: " + material.name() + " x" + item.getAmount() + " (durability: " + durability + ").");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("item") != null) plugin.getCommand("item").setExecutor(null);
        plugin = null;
    }
}
