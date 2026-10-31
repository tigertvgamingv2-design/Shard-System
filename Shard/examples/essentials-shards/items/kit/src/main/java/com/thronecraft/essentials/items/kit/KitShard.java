package com.thronecraft.essentials.items.kit;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KitShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File kitsFile;
    private File cooldownFile;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        cooldownFile = new File(plugin.getDataFolder(), "kit-cooldowns.yml");
        if (plugin.getCommand("kit") != null) plugin.getCommand("kit").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (!player.hasPermission("essentials.kit")) { com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission."); return true; }
        if (args.length > 1) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /kit [name]"); return true; }
        String name = args.length == 0 ? "starter" : args[0].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) { com.thronecraft.net.shards.ShardMessages.send(sender, "Invalid kit name."); return true; }
        List<ItemStack> items;
        if (name.equals("starter")) {
            YamlConfiguration cooldowns = YamlConfiguration.loadConfiguration(cooldownFile);
            long now = System.currentTimeMillis(), last = cooldowns.getLong("cooldowns." + player.getUniqueId(), 0);
            long remaining = 3_600_000L - Math.max(0, now - last);
            if (remaining > 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Starter kit is on cooldown for " + ((remaining + 59_999) / 60_000) + " minute(s)."); return true; }
            cooldowns.set("cooldowns." + player.getUniqueId(), now);
            try { cooldowns.save(cooldownFile); } catch (IOException ex) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "Kit cooldown could not be saved."); plugin.getLogger().severe("Could not save kit cooldowns: " + ex.getMessage()); return true;
            }
            items = List.of(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.BREAD, 16), new ItemStack(Material.TORCH, 16));
        } else {
            YamlConfiguration kits = YamlConfiguration.loadConfiguration(kitsFile);
            if (!kits.contains("kits." + name)) { com.thronecraft.net.shards.ShardMessages.send(sender, "That kit does not exist."); return true; }
            items = new ArrayList<>();
            addSavedItems(items, kits.getList("kits." + name + ".contents"));
            addSavedItems(items, kits.getList("kits." + name + ".armor"));
            addSavedItems(items, kits.getList("kits." + name + ".extra"));
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(ItemStack[]::new));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        com.thronecraft.net.shards.ShardMessages.send(sender, "Kit " + name + " claimed.");
        return true;
    }
    private void addSavedItems(List<ItemStack> target, List<?> saved) {
        if (saved == null) return;
        for (Object value : saved) {
            if (value instanceof ItemStack item && !item.getType().isAir()) target.add(item.clone());
        }
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("kit") != null) plugin.getCommand("kit").setExecutor(null);
        plugin = null; kitsFile = null; cooldownFile = null;
    }
}
