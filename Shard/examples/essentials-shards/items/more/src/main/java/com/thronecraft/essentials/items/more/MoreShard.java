package com.thronecraft.essentials.items.more;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MoreShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getCommand("more") != null) plugin.getCommand("more").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (args.length > 1) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /more [amount]"); return true; }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) { com.thronecraft.net.shards.ShardMessages.send(sender, "Hold an item first."); return true; }
        int requested = held.getMaxStackSize();
        if (args.length == 1) {
            try { requested = Integer.parseInt(args[0]); } catch (NumberFormatException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Amount must be a whole number."); return true; }
        }
        if (requested < 1 || requested > held.getMaxStackSize()) { com.thronecraft.net.shards.ShardMessages.send(sender, "Amount must be between 1 and " + held.getMaxStackSize() + "."); return true; }
        int needed = requested - held.getAmount();
        if (needed <= 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Your held stack already has at least that many items."); return true; }
        ItemStack[] contents = player.getInventory().getStorageContents();
        int available = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(held) && i != player.getInventory().getHeldItemSlot()) available += item.getAmount();
        }
        int moved = Math.min(needed, available);
        if (moved == 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "You have no matching items to refill from."); return true; }
        int remove = moved;
        for (int i = 0; i < contents.length && remove > 0; i++) {
            ItemStack item = contents[i];
            if (i == player.getInventory().getHeldItemSlot() || item == null || !item.isSimilar(held)) continue;
            int taken = Math.min(remove, item.getAmount());
            item.setAmount(item.getAmount() - taken);
            if (item.getAmount() == 0) contents[i] = null;
            remove -= taken;
        }
        player.getInventory().setStorageContents(contents);
        held.setAmount(held.getAmount() + moved);
        player.getInventory().setItemInMainHand(held);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Refilled your held stack to " + held.getAmount() + ".");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("more") != null) plugin.getCommand("more").setExecutor(null);
        plugin = null;
    }
}
