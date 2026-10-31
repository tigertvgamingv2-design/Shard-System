package com.thronecraft.essentials.economy.worth;

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
import java.util.Map;

public final class WorthShard implements ShardModule, CommandExecutor {
    private static final Map<Material, Double> PRICES = Map.of(
            Material.DIAMOND, 100.0, Material.EMERALD, 25.0, Material.GOLD_INGOT, 15.0,
            Material.IRON_INGOT, 10.0, Material.COAL, 2.0, Material.REDSTONE, 3.0,
            Material.LAPIS_LAZULI, 3.0, Material.COPPER_INGOT, 5.0, Material.NETHERITE_INGOT, 500.0);
    private JavaPlugin plugin;
    private File file;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "economy.yml");
        if (plugin.getCommand("worth") != null) plugin.getCommand("worth").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (args.length != 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /worth"); return true; }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) { com.thronecraft.net.shards.ShardMessages.send(sender, "Hold an item first."); return true; }
        Double price = PRICES.get(item.getType());
        if (price == null) { com.thronecraft.net.shards.ShardMessages.send(sender, "That item has no sell value."); return true; }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        com.thronecraft.net.shards.ShardMessages.send(sender, item.getAmount() + " " + item.getType().name() + " are worth "
                + String.format(java.util.Locale.US, "%.2f", price * item.getAmount()) + " coins.");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("worth") != null) plugin.getCommand("worth").setExecutor(null);
        plugin = null; file = null;
    }
}
