package com.thronecraft.examples.advanced.homes;

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
import java.util.UUID;

public class HomesShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private static final int DEFAULT_MAX_HOMES = 5;
    private static final int PAGE_SIZE = 45;
    private static final int MENU_SIZE = 54;
    private static final String DATA_FILE = "homes.yml";
    private final List<PluginCommand> commands = new ArrayList<>();
    private JavaPlugin plugin;
    private YamlConfiguration data;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        data = AdvancedShardSupport.loadData(plugin, DATA_FILE);
        commands.add(AdvancedShardSupport.register(plugin, "sethome", this));
        commands.add(AdvancedShardSupport.register(plugin, "home", this));
        commands.add(AdvancedShardSupport.register(plugin, "homes", this));
        commands.add(AdvancedShardSupport.register(plugin, "delhome", this));
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
        if (commandName.equals("homes")) {
            Player player = AdvancedShardSupport.requirePlayer(sender);
            if (player != null) {
                openHomes(player, args);
            }
            return true;
        }

        Player player = AdvancedShardSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        switch (commandName) {
            case "sethome" -> setHome(player, args);
            case "home" -> teleportHome(player, args);
            case "delhome" -> deleteHome(player, args);
            default -> AdvancedShardSupport.send(sender, "&cUnknown homes command.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && name.equals("home")) {
            List<String> suggestions = new ArrayList<>(homeNames(sender));
            suggestions.addAll(savedPlayerNames());
            return suggestions.stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT)
                            .startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length == 1 && name.equals("delhome")) {
            return AdvancedShardSupport.matching(homeNames(sender), args[0]);
        }
        if (args.length == 2 && name.equals("home")) {
            UUID ownerId = findPlayerId(args[0]);
            if (ownerId == null) {
                return List.of();
            }
            List<String> publicHomes = AdvancedShardSupport.keys(
                    data.getConfigurationSection(ownerId.toString()));
            publicHomes.removeIf(home -> !data.getBoolean(ownerId + "." + home + ".public", false));
            return AdvancedShardSupport.matching(publicHomes, args[1]);
        }
        if (args.length == 1 && name.equals("homes")) {
            return AdvancedShardSupport.matching(savedPlayerNames(), args[0]);
        }
        if (args.length == 2 && name.equals("sethome")) {
            return AdvancedShardSupport.matching(List.of("public", "private"), args[1]);
        }
        if (args.length == 3 && name.equals("sethome")) {
            return AdvancedShardSupport.matching(
                    List.of("RED_BED", "BLUE_BED", "COMPASS", "NETHER_STAR"), args[2]);
        }
        return List.of();
    }

    private void setHome(Player player, String[] args) {
        if (args.length > 3) {
            usage(player, "/sethome [name] [public|private] [icon]");
            return;
        }
        String name = args.length == 0 ? "home" : args[0];
        if (!AdvancedShardSupport.validName(name)) {
            AdvancedShardSupport.send(player, "&cNames must be 1-16 letters, numbers, _ or -.");
            return;
        }

        String base = AdvancedShardSupport.playerPath(player.getUniqueId());
        String path = base + "." + name;
        List<String> homes = AdvancedShardSupport.keys(data.getConfigurationSection(base));
        int limit = homeLimit(player);
        if (!homes.contains(name) && homes.size() >= limit) {
            AdvancedShardSupport.send(player, "&cYou have reached your home limit of &f" + limit + "&c.");
            return;
        }

        boolean isPublic = data.getBoolean(path + ".public", false);
        if (args.length >= 2) {
            if (!args[1].equalsIgnoreCase("public") && !args[1].equalsIgnoreCase("private")) {
                AdvancedShardSupport.send(player, "&cChoose whether the home is public or private.");
                usage(player, "/sethome [name] [public|private] [icon]");
                return;
            }
            isPublic = args[1].equalsIgnoreCase("public");
        }
        Material icon = Material.matchMaterial(data.getString(path + ".icon", "RED_BED"));
        if (args.length == 3) {
            icon = Material.matchMaterial(args[2]);
        }
        if (icon == null || !icon.isItem() || icon.isAir()) {
            AdvancedShardSupport.send(player, "&cChoose a valid item material for the home icon.");
            return;
        }

        AdvancedShardSupport.writeLocation(data, path, player.getLocation());
        data.set(path + ".public", isPublic);
        data.set(path + ".icon", icon.name());
        data.set("player-names." + player.getUniqueId(), player.getName());
        save();
        AdvancedShardSupport.send(player, "&aSaved " + (isPublic ? "public" : "private")
                + " home &f" + name + "&a.");
    }

    private void teleportHome(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) {
            usage(player, "/home [player] <name>");
            return;
        }

        UUID ownerId = player.getUniqueId();
        String homeName;
        if (args.length == 1) {
            homeName = args[0];
        } else {
            ownerId = findPlayerId(args[0]);
            homeName = args[1];
            if (ownerId == null) {
                AdvancedShardSupport.send(player, "&cNo saved homes were found for &f" + args[0] + "&c.");
                return;
            }
            if (ownerId.equals(player.getUniqueId())) {
                AdvancedShardSupport.send(player, "&eUse &f/home <name> &eto visit your own home.");
                return;
            }
            if (!data.getBoolean(ownerId + "." + homeName + ".public", false)) {
                AdvancedShardSupport.send(player, "&cThat home is private or does not exist.");
                return;
            }
        }
        if (!AdvancedShardSupport.validName(homeName)) {
            AdvancedShardSupport.send(player, "&cInvalid home name.");
            return;
        }
        Location location = AdvancedShardSupport.readLocation(data, ownerId + "." + homeName);
        if (location == null) {
            AdvancedShardSupport.send(player, "&cHome not found or its world is unavailable: &f" + homeName);
            return;
        }
        if (player.teleport(location)) {
            String displayName = ownerId.equals(player.getUniqueId())
                    ? homeName : savedPlayerName(ownerId) + ":" + homeName;
            AdvancedShardSupport.send(player, "&aTeleported to home &f" + displayName + "&a.");
        } else {
            AdvancedShardSupport.send(player, "&cCould not teleport you to that home.");
        }
    }

    private void openHomes(Player viewer, String[] args) {
        if (args.length > 1) {
            usage(viewer, "/homes [player]");
            return;
        }
        UUID ownerId = viewer.getUniqueId();
        String ownerName = viewer.getName();
        boolean publicOnly = false;
        if (args.length == 1) {
            ownerId = findPlayerId(args[0]);
            if (ownerId == null) {
                AdvancedShardSupport.send(viewer, "&cNo saved homes were found for &f" + args[0] + "&c.");
                return;
            }
            ownerName = savedPlayerName(ownerId);
            publicOnly = !ownerId.equals(viewer.getUniqueId());
        }

        List<String> names = AdvancedShardSupport.keys(data.getConfigurationSection(ownerId.toString()));
        if (publicOnly) {
            UUID selectedOwner = ownerId;
            names.removeIf(home -> !data.getBoolean(selectedOwner + "." + home + ".public", false));
        }
        if (names.isEmpty()) {
            AdvancedShardSupport.send(viewer, publicOnly
                    ? "&e" + ownerName + " has no public homes."
                    : "&7You have no homes. Use &f/sethome [name]&7.");
            return;
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        openHomePage(viewer, ownerId, ownerName, names, 0, publicOnly);
    }

    private void openHomePage(Player viewer, UUID ownerId, String ownerName,
                              List<String> names, int page, boolean publicOnly) {
        HomeMenu menu = new HomeMenu(viewer.getUniqueId(), ownerId, ownerName, names, page, publicOnly);
        Inventory inventory = Bukkit.createInventory(menu, MENU_SIZE,
                AdvancedShardSupport.color("&8" + ownerName + "'s Homes"));
        menu.inventory = inventory;
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, names.size());
        for (int index = start; index < end; index++) {
            String name = names.get(index);
            String path = ownerId + "." + name;
            Material material = Material.matchMaterial(data.getString(path + ".icon", "RED_BED"));
            if (material == null || !material.isItem() || material.isAir()) {
                material = Material.RED_BED;
            }
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(AdvancedShardSupport.color("&b" + name));
            Location location = AdvancedShardSupport.readLocation(data, path);
            List<String> lore = new ArrayList<>();
            lore.add(AdvancedShardSupport.color(data.getBoolean(path + ".public", false)
                    ? "&aPublic home" : "&7Private home"));
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
        if (!(event.getView().getTopInventory().getHolder() instanceof HomeMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)
                || !viewer.getUniqueId().equals(menu.viewerId)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 45 && menu.page > 0) {
            openHomePage(viewer, menu.ownerId, menu.ownerName, menu.names, menu.page - 1, menu.publicOnly);
            return;
        }
        if (slot == 53 && (menu.page + 1) * PAGE_SIZE < menu.names.size()) {
            openHomePage(viewer, menu.ownerId, menu.ownerName, menu.names, menu.page + 1, menu.publicOnly);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        int index = menu.page * PAGE_SIZE + slot;
        if (index >= menu.names.size()) {
            return;
        }
        String home = menu.names.get(index);
        if (!menu.ownerId.equals(viewer.getUniqueId())
                && !data.getBoolean(menu.ownerId + "." + home + ".public", false)) {
            AdvancedShardSupport.send(viewer, "&cThat home is private or no longer available.");
            viewer.closeInventory();
            return;
        }
        Location location = AdvancedShardSupport.readLocation(data, menu.ownerId + "." + home);
        if (location == null) {
            AdvancedShardSupport.send(viewer, "&cThat home or its world is no longer available.");
            viewer.closeInventory();
            return;
        }
        viewer.closeInventory();
        if (viewer.teleport(location)) {
            AdvancedShardSupport.send(viewer, "&aTeleported to home &f" + home + "&a.");
        } else {
            AdvancedShardSupport.send(viewer, "&cCould not teleport you to that home.");
        }
    }

    private void deleteHome(Player player, String[] args) {
        if (args.length != 1) {
            usage(player, "/delhome <name>");
            return;
        }
        if (!AdvancedShardSupport.validName(args[0])) {
            AdvancedShardSupport.send(player, "&cInvalid home name.");
            return;
        }
        String path = AdvancedShardSupport.playerPath(player.getUniqueId()) + "." + args[0];
        if (!data.contains(path)) {
            AdvancedShardSupport.send(player, "&cHome not found: &f" + args[0]);
            return;
        }
        data.set(path, null);
        save();
        AdvancedShardSupport.send(player, "&aDeleted home &f" + args[0] + "&a.");
    }

    private List<String> homeNames(CommandSender sender) {
        if (sender instanceof Player player) {
            return AdvancedShardSupport.keys(data.getConfigurationSection(
                    AdvancedShardSupport.playerPath(player.getUniqueId())));
        }
        return List.of();
    }

    private List<String> savedPlayerNames() {
        ConfigurationSection section = data.getConfigurationSection("player-names");
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .filter(id -> !id.isBlank())
                .map(id -> data.getString("player-names." + id))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private UUID findPlayerId(String name) {
        ConfigurationSection section = data.getConfigurationSection("player-names");
        if (section == null) {
            return null;
        }
        for (String id : section.getKeys(false)) {
            if (name.equalsIgnoreCase(data.getString("player-names." + id, ""))) {
                try {
                    return UUID.fromString(id);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Skipped invalid player UUID in homes.yml: " + id);
                }
            }
        }
        return null;
    }

    private String savedPlayerName(UUID id) {
        return data.getString("player-names." + id, id.toString());
    }

    private int homeLimit(Player player) {
        if (player.hasPermission("shard.homes.unlimited")) {
            return Integer.MAX_VALUE;
        }
        if (player.hasPermission("shard.homes.limit.25")) {
            return 25;
        }
        if (player.hasPermission("shard.homes.limit.10")) {
            return 10;
        }
        return DEFAULT_MAX_HOMES;
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

    private static final class HomeMenu implements InventoryHolder {
        private final UUID viewerId;
        private final UUID ownerId;
        private final String ownerName;
        private final List<String> names;
        private final int page;
        private final boolean publicOnly;
        private Inventory inventory;

        private HomeMenu(UUID viewerId, UUID ownerId, String ownerName, List<String> names,
                         int page, boolean publicOnly) {
            this.viewerId = viewerId;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.names = List.copyOf(names);
            this.page = page;
            this.publicOnly = publicOnly;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
