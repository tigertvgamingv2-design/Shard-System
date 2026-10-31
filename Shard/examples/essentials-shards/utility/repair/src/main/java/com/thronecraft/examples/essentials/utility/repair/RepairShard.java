package com.thronecraft.examples.essentials.utility.repair;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

public class RepairShard implements ShardModule, CommandExecutor {
    private org.bukkit.command.PluginCommand command;

    public RepairShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("repair");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.repair")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("all"))) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /repair [all]");
            return true;
        }
        if (!(sender instanceof Player player)) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Only players can repair items.");
            return true;
        }
        int repaired;
        if (args.length == 0) {
            repaired = repair(player.getInventory().getItemInMainHand());
        } else {
            PlayerInventory inventory = player.getInventory();
            repaired = 0;
            for (ItemStack item : inventory.getStorageContents()) repaired += repair(item);
            for (ItemStack item : inventory.getArmorContents()) repaired += repair(item);
            repaired += repair(inventory.getItemInOffHand());
        }
        com.thronecraft.net.shards.ShardMessages.send(sender, repaired == 0 ? "No damaged items were found." : "Repaired " + repaired + " item(s).");
        return true;
    }

    private int repair(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return 0;
        if (!(item.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) return 0;
        damageable.setDamage(0);
        item.setItemMeta(damageable);
        return 1;
    }
}
