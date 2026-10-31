package com.thronecraft.essentials.economy.sellall;

import com.thronecraft.net.managers.EconomyTransactionLogger;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class SellAllShard implements ShardModule, CommandExecutor {
    private static final Map<Material, Double> PRICES = Map.of(
            Material.DIAMOND, 100.0, Material.EMERALD, 25.0, Material.GOLD_INGOT, 15.0,
            Material.IRON_INGOT, 10.0, Material.COAL, 2.0, Material.REDSTONE, 3.0,
            Material.LAPIS_LAZULI, 3.0, Material.COPPER_INGOT, 5.0, Material.NETHERITE_INGOT, 500.0);
    private JavaPlugin plugin;
    private File file;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "economy.yml");
        if (plugin.getCommand("sellall") != null) plugin.getCommand("sellall").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (args.length != 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /sellall"); return true; }
        ItemStack[] contents = player.getInventory().getStorageContents();
        double total = 0;
        for (ItemStack item : contents) if (item != null) {
            Double price = PRICES.get(item.getType());
            if (price != null) total += price * item.getAmount();
        }
        if (total <= 0 || !Double.isFinite(total)) { com.thronecraft.net.shards.ShardMessages.send(sender, "You have no sellable items."); return true; }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String path = "balances." + player.getUniqueId();
        double balance = data.getDouble(path, 0);
        double updated = balance + total;
        if (!Double.isFinite(updated)) { com.thronecraft.net.shards.ShardMessages.send(sender, "Sale could not be completed."); return true; }
        data.set(path, updated);
        try { data.save(file); } catch (IOException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Sale could not be saved."); plugin.getLogger().severe("Could not save economy.yml: " + ex.getMessage()); return true; }
        EconomyTransactionLogger.recordOrReport(plugin, sender, player.getName(),
                "sellall", player.getName(), total, balance, updated);
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && PRICES.containsKey(item.getType())) contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Sold your sellable inventory items for " + String.format(java.util.Locale.US, "%.2f", total) + " coins.");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("sellall") != null) plugin.getCommand("sellall").setExecutor(null);
        plugin = null; file = null;
    }
}
