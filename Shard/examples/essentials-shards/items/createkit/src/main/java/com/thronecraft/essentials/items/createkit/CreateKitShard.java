package com.thronecraft.essentials.items.createkit;

import com.thronecraft.net.shards.ShardModule;
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

public final class CreateKitShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File kitsFile;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (plugin.getCommand("createkit") != null) plugin.getCommand("createkit").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (!player.isOp()) { com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission."); return true; }
        if (args.length != 1) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /createkit <name>"); return true; }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}") || name.equals("starter")) { com.thronecraft.net.shards.ShardMessages.send(sender, "Use a valid name other than starter."); return true; }
        YamlConfiguration kits = YamlConfiguration.loadConfiguration(kitsFile);
        kits.set("kits." + name + ".contents", snapshot(player.getInventory().getStorageContents()));
        kits.set("kits." + name + ".armor", snapshot(player.getInventory().getArmorContents()));
        kits.set("kits." + name + ".extra", snapshot(player.getInventory().getExtraContents()));
        kits.set("kits." + name + ".created-by", player.getUniqueId().toString());
        try { kits.save(kitsFile); } catch (IOException ex) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Kit could not be saved."); plugin.getLogger().severe("Could not save kits.yml: " + ex.getMessage()); return true;
        }
        com.thronecraft.net.shards.ShardMessages.send(sender, "Saved kit " + name + ".");
        return true;
    }
    private List<ItemStack> snapshot(ItemStack[] items) {
        List<ItemStack> saved = new ArrayList<>();
        for (ItemStack item : items) if (item != null && !item.getType().isAir()) saved.add(item.clone());
        return saved;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("createkit") != null) plugin.getCommand("createkit").setExecutor(null);
        plugin = null; kitsFile = null;
    }
}
