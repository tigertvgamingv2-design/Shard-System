package com.thronecraft.examples.creative.living;

import com.thronecraft.examples.creative.CreativeShardSupport;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class LivingWorldShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private static final String DATA_FILE = "living-world.yml";
    private static final int[] MILESTONES = {3, 7, 12, 18, 26, 36, 48};
    private static final int MAX_LEADERBOARD_ENTRIES = 5;

    private final Map<UUID, String> currentBiomes = new HashMap<>();
    private PluginCommand command;
    private JavaPlugin plugin;
    private YamlConfiguration data;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        data = CreativeShardSupport.loadData(plugin, DATA_FILE);
        command = CreativeShardSupport.register(plugin, "worldpulse", this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        if (command != null) {
            CreativeShardSupport.unregister(command, this);
            command = null;
        }
        if (data != null) {
            CreativeShardSupport.saveData(plugin, data, DATA_FILE);
            data = null;
        }
        currentBiomes.clear();
        plugin = null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discover(event.getPlayer(), event.getPlayer().getWorld(),
                CreativeShardSupport.biomeKey(event.getPlayer().getLocation().getBlock().getBiome()));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || (event.getFrom().getWorld() == event.getTo().getWorld()
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ())) {
            return;
        }
        String biome = CreativeShardSupport.biomeKey(event.getTo().getBlock().getBiome());
        UUID playerId = event.getPlayer().getUniqueId();
        String locationKey = event.getTo().getWorld().getUID() + ":" + biome;
        if (locationKey.equals(currentBiomes.get(playerId))) {
            return;
        }
        discover(event.getPlayer(), event.getTo().getWorld(), biome);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        currentBiomes.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CreativeShardSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (args.length > 1) {
                usage(player);
            } else {
                showStatus(player);
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("top")) {
            showLeaderboard(player);
            return true;
        }
        usage(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1
                ? CreativeShardSupport.matching(List.of("status", "top"), args[0])
                : List.of();
    }

    private void discover(Player player, World world, String biome) {
        UUID playerId = player.getUniqueId();
        currentBiomes.put(playerId, world.getUID() + ":" + biome);

        String worldId = world.getUID().toString();
        String playerPath = "players." + playerId + "." + worldId;
        List<String> personalDiscoveries = data.getStringList(playerPath + ".biomes");
        if (personalDiscoveries.contains(biome)) {
            return;
        }

        personalDiscoveries.add(biome);
        data.set(playerPath + ".biomes", personalDiscoveries);
        data.set(playerPath + ".name", player.getName());

        String sharedPath = "worlds." + worldId;
        List<String> worldDiscoveries = data.getStringList(sharedPath + ".biomes");
        if (!worldDiscoveries.contains(biome)) {
            worldDiscoveries.add(biome);
            data.set(sharedPath + ".biomes", worldDiscoveries);
            data.set(sharedPath + ".name", world.getName());
            String contributionPath = "contributors." + worldId + "." + playerId;
            data.set(contributionPath, data.getInt(contributionPath) + 1);
            data.set("contributor-names." + worldId + "." + playerId, player.getName());
            checkMilestones(world, player, worldId, worldDiscoveries.size());
        }
        save();

        String biomeName = biome.substring(biome.indexOf(':') + 1)
                .replace('_', ' ').toLowerCase(Locale.ROOT);
        CreativeShardSupport.send(player, "&bYou discovered &f" + biomeName
                + "&b. Your world has mapped &f" + worldDiscoveries.size() + " unique biomes&b.");
    }

    private void checkMilestones(World world, Player discoverer, String worldId, int discoveryCount) {
        int rank = data.getInt("worlds." + worldId + ".rank");
        while (rank < MILESTONES.length && discoveryCount >= MILESTONES[rank]) {
            rank++;
            data.set("worlds." + worldId + ".rank", rank);
            int rewardExperience = 10 + rank * 5;
            String title = rankName(rank);
            for (Player onlinePlayer : world.getPlayers()) {
                onlinePlayer.giveExp(rewardExperience);
                CreativeShardSupport.send(onlinePlayer, "&dWorld milestone! &f" + world.getName()
                        + " &dreached &f" + title + " &dwith " + discoveryCount
                        + " mapped biomes. &7(+" + rewardExperience + " XP)");
            }
        }
    }

    private void showStatus(Player player) {
        String worldId = player.getWorld().getUID().toString();
        List<String> biomes = data.getStringList("worlds." + worldId + ".biomes");
        int rank = data.getInt("worlds." + worldId + ".rank");
        int nextMilestone = rank < MILESTONES.length ? MILESTONES[rank] : -1;
        int personalCount = data.getStringList("players." + player.getUniqueId() + "."
                + worldId + ".biomes").size();

        CreativeShardSupport.send(player, "&d" + player.getWorld().getName() + " pulse: &f"
                + rankName(rank) + " &7| Shared map: &f" + biomes.size() + " unique biomes");
        CreativeShardSupport.send(player, "&7Your discoveries: &f" + personalCount);
        if (nextMilestone < 0) {
            CreativeShardSupport.send(player, "&aEvery known biome has been charted. The world is fully awakened!");
        } else {
            CreativeShardSupport.send(player, "&7Next milestone: &f" + nextMilestone
                    + " &7biomes (" + progressBar(biomes.size(), nextMilestone) + "&7)");
        }
    }

    private void showLeaderboard(Player player) {
        String worldId = player.getWorld().getUID().toString();
        ConfigurationSection section = data.getConfigurationSection("contributors." + worldId);
        if (section == null || section.getKeys(false).isEmpty()) {
            CreativeShardSupport.send(player, "&7No explorers have mapped a biome here yet.");
            return;
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            entries.add(Map.entry(id, section.getInt(id)));
        }
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(entry -> data.getString(
                        "contributor-names." + worldId + "." + entry.getKey(), entry.getKey())));

        CreativeShardSupport.send(player, "&dTop world explorers — &f" + player.getWorld().getName());
        for (int index = 0; index < Math.min(MAX_LEADERBOARD_ENTRIES, entries.size()); index++) {
            Map.Entry<String, Integer> entry = entries.get(index);
            String name = data.getString(
                    "contributor-names." + worldId + "." + entry.getKey(), "Unknown");
            CreativeShardSupport.send(player, "&7#" + (index + 1) + " &f" + name
                    + " &8— &b" + entry.getValue() + " first discoveries");
        }
    }

    private String progressBar(int discoveries, int target) {
        int filled = Math.min(10, discoveries * 10 / target);
        return "&b" + "|".repeat(filled) + "&8" + "|".repeat(10 - filled);
    }

    private String rankName(int rank) {
        return switch (rank) {
            case 0 -> "Uncharted";
            case 1 -> "Stirring";
            case 2 -> "Awakening";
            case 3 -> "Resonant";
            case 4 -> "Thriving";
            case 5 -> "Radiant";
            case 6 -> "Vibrant";
            default -> "Worldheart";
        };
    }

    private void usage(Player player) {
        CreativeShardSupport.send(player, "&7Use &f/worldpulse [status|top]");
    }

    private void save() {
        CreativeShardSupport.saveData(plugin, data, DATA_FILE);
    }
}
