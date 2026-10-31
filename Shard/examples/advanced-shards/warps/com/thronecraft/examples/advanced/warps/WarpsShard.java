package com.thronecraft.examples.advanced.warps;

import com.thronecraft.examples.advanced.AdvancedShardSupport;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class WarpsShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private static final String DATA_FILE = "warps.yml";
    private static final int PAGE_SIZE = 45;
    private static final int MENU_SIZE = 54;
    private final List<PluginCommand> commands = new ArrayList<>();
    private JavaPlugin plugin;
    private YamlConfiguration data;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        data = AdvancedShardSupport.loadData(plugin, DATA_FILE);
        commands.add(AdvancedShardSupport.register(plugin, "warp", this));
        commands.add(AdvancedShardSupport.register(plugin, "warps", this));
        commands.add(AdvancedShardSupport.register(plugin, "setwarp", this));
        commands.add(AdvancedShardSupport.register(plugin, "delwarp", this));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        for (PluginCommand command : commands) {
            AdvancedShardSupport.unregister(command, this);
        }
        commands.clear();
        if (data != null) {
            AdvancedShardSupport.saveData(plugin, data, DATA_FILE);
            data = null;
        }
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        switch (commandName) {
            case "warp" -> teleport(sender, args);
            case "warps" -> listWarps(sender, args);
            case "setwarp" -> setWarp(sender, args);
            case "delwarp" -> deleteWarp(sender, args);
            default -> AdvancedShardSupport.send(sender, "&cUnknown warp command.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && commandName.equals("warp")) {
            return AdvancedShardSupport.matching(warpNames(), args[0]);
        }
        if (args.length == 1 && commandName.equals("warps")) {
            return AdvancedShardSupport.matching(categories(), args[0]);
        }
        if (args.length == 2 && commandName.equals("setwarp")) {
            return AdvancedShardSupport.matching(categories(), args[1]);
        }
        if (args.length == 3 && commandName.equals("setwarp")) {
            return AdvancedShardSupport.matching(
                    List.of("COMPASS", "NETHER_STAR", "GRASS_BLOCK", "DIAMOND", "OAK_SAPLING"), args[2]);
        }
        return List.of();
    }

    private void teleport(CommandSender sender, String[] args) {
        Player player = AdvancedShardSupport.requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length != 1) {
            usage(player, "/warp <name>");
            return;
        }
        if (!AdvancedShardSupport.validName(args[0])) {
            AdvancedShardSupport.send(player, "&cInvalid warp name.");
            return;
        }
        teleportToWarp(player, args[0]);
    }

    private void teleportToWarp(Player player, String name) {
        Location location = AdvancedShardSupport.readLocation(data, "warps." + name);
        if (location == null) {
            AdvancedShardSupport.send(player, "&cWarp not found or its world is unavailable: &f" + name);
            return;
        }
        if (player.teleport(location)) {
            AdvancedShardSupport.send(player, "&aTeleported to warp &f" + name + "&a.");
        } else {
            AdvancedShardSupport.send(player, "&cCould not teleport you to that warp.");
        }
    }

    private void listWarps(CommandSender sender, String[] args) {
        if (args.length > 1 || (args.length == 1 && !AdvancedShardSupport.validName(args[0]))) {
            usage(sender, "/warps [category]");
            return;
        }
        String category = args.length == 0 ? null : args[0];
        List<String> warps = warpNames(category);
        if (warps.isEmpty()) {
            AdvancedShardSupport.send(sender, category == null
                    ? "&7No warps have been created yet."
                    : "&7No warps were found in category &f" + category + "&7.");
            return;
        }
        if (sender instanceof Player player) {
            openWarpPage(player, warps, category, 0);
            return;
        }
        AdvancedShardSupport.send(sender, "&bWarps &8(&f" + warps.size() + "&8): &f"
                + String.join("&7, &f", warps));
    }

    private void setWarp(CommandSender sender, String[] args) {
        Player player = AdvancedShardSupport.requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!sender.hasPermission("shard.warps.admin")) {
            AdvancedShardSupport.send(sender, "&cYou need shard.warps.admin to create warps.");
            return;
        }
        if (args.length < 1 || args.length > 3 || !AdvancedShardSupport.validName(args[0])) {
            usage(sender, "/setwarp <name> [category] [icon]");
            return;
        }

        String path = "warps." + args[0];
        String category = data.getString(path + ".category", "General");
        if (args.length >= 2) {
            category = args[1];
        }
        if (!AdvancedShardSupport.validName(category)) {
            AdvancedShardSupport.send(sender, "&cCategory must be 1-16 letters, numbers, _ or -.");
            return;
        }
        Material icon = Material.matchMaterial(data.getString(path + ".icon", "COMPASS"));
        if (args.length == 3) {
            icon = Material.matchMaterial(args[2]);
        }
        if (icon == null || !icon.isItem() || icon.isAir()) {
            AdvancedShardSupport.send(sender, "&cChoose a valid item material for the warp icon.");
            return;
        }

        AdvancedShardSupport.writeLocation(data, path, player.getLocation());
        data.set(path + ".category", category);
        data.set(path + ".icon", icon.name());
        save();
        AdvancedShardSupport.send(sender, "&aSaved warp &f" + args[0] + " &ain &f" + category + "&a.");
    }

    private void deleteWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shard.warps.admin")) {
            AdvancedShardSupport.send(sender, "&cYou need shard.warps.admin to delete warps.");
            return;
        }
        if (args.length != 1 || !AdvancedShardSupport.validName(args[0])) {
            usage(sender, "/delwarp <name>");
            return;
        }
        String path = "warps." + args[0];
        if (!data.contains(path)) {
            AdvancedShardSupport.send(sender, "&cWarp not found: &f" + args[0]);
            return;
        }
        data.set(path, null);
        save();
        AdvancedShardSupport.send(sender, "&aDeleted warp &f" + args[0] + "&a.");
    }

    private void openWarpPage(Player viewer, List<String> names, String category, int page) {
        WarpMenu menu = new WarpMenu(viewer.getUniqueId(), names, category, page);
        String title = category == null ? "&8Public Warps" : "&8Warps: " + category;
        Inventory inventory = Bukkit.createInventory(menu, MENU_SIZE, AdvancedShardSupport.color(title));
        menu.inventory = inventory;
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, names.size());
        for (int index = start; index < end; index++) {
            String name = names.get(index);
            String path = "warps." + name;
            Material material = Material.matchMaterial(data.getString(path + ".icon", "COMPASS"));
            if (material == null || !material.isItem() || material.isAir()) {
                material = Material.COMPASS;
            }
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(AdvancedShardSupport.color("&b" + name));
            Location location = AdvancedShardSupport.readLocation(data, path);
            List<String> lore = new ArrayList<>();
            lore.add(AdvancedShardSupport.color("&7Category: &f"
                    + data.getString(path + ".category", "General")));
            if (location != null) {
                lore.add(AdvancedShardSupport.color("&7World: &f" + location.getWorld().getName()));
                lore.add(AdvancedShardSupport.color("&7Location: &f"
                        + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()));
            }
            lore.add(AdvancedShardSupport.color("&eClick to teleport"));
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(index - start, item);
        }
        if (page > 0) {
            inventory.setItem(45, menuItem(Material.ARROW, "&ePrevious page"));
        }
        if (end < names.size()) {
            inventory.setItem(53, menuItem(Material.ARROW, "&eNext page"));
        }
        inventory.setItem(49, menuItem(Material.BOOK,
                "&bPage &f" + (page + 1) + "&7/&f" + ((names.size() + PAGE_SIZE - 1) / PAGE_SIZE)));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WarpMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !viewer.getUniqueId().equals(menu.viewerId)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 45 && menu.page > 0) {
            openWarpPage(viewer, menu.names, menu.category, menu.page - 1);
            return;
        }
        if (slot == 53 && (menu.page + 1) * PAGE_SIZE < menu.names.size()) {
            openWarpPage(viewer, menu.names, menu.category, menu.page + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        int index = menu.page * PAGE_SIZE + slot;
        if (index >= menu.names.size()) {
            return;
        }
        String name = menu.names.get(index);
        if (!data.contains("warps." + name)) {
            AdvancedShardSupport.send(viewer, "&cThat warp is no longer available.");
            viewer.closeInventory();
            return;
        }
        viewer.closeInventory();
        teleportToWarp(viewer, name);
    }

    private List<String> warpNames() {
        return warpNames(null);
    }

    private List<String> warpNames(String category) {
        List<String> names = AdvancedShardSupport.keys(data.getConfigurationSection("warps"));
        if (category != null) {
            names.removeIf(name -> !data.getString("warps." + name + ".category", "General")
                    .equalsIgnoreCase(category));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private List<String> categories() {
        ConfigurationSection section = data.getConfigurationSection("warps");
        if (section == null) {
            return List.of();
        }
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : section.getKeys(false)) {
            categories.add(data.getString("warps." + name + ".category", "General"));
        }
        return List.copyOf(categories);
    }

    private ItemStack menuItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(AdvancedShardSupport.color(displayName));
        item.setItemMeta(meta);
        return item;
    }

    private void save() {
        AdvancedShardSupport.saveData(plugin, data, DATA_FILE);
    }

    private void usage(CommandSender sender, String usage) {
        AdvancedShardSupport.send(sender, "&eUsage: &f" + usage);
    }

    private static final class WarpMenu implements InventoryHolder {
        private final UUID viewerId;
        private final List<String> names;
        private final String category;
        private final int page;
        private Inventory inventory;

        private WarpMenu(UUID viewerId, List<String> names, String category, int page) {
            this.viewerId = viewerId;
            this.names = List.copyOf(names);
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
