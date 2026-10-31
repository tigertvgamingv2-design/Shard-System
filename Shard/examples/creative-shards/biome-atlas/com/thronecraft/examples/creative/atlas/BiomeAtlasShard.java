package com.thronecraft.examples.creative.atlas;

import com.thronecraft.examples.creative.CreativeShardSupport;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BiomeAtlasShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private static final String DATA_FILE = "biome-atlas.yml";
    private final Set<String> announcedThisSession = new LinkedHashSet<>();
    private PluginCommand command;
    private JavaPlugin plugin;
    private YamlConfiguration data;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        data = CreativeShardSupport.loadData(plugin, DATA_FILE);
        command = CreativeShardSupport.register(plugin, "atlas", this);
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
        announcedThisSession.clear();
        plugin = null;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()
                || (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        String biome = CreativeShardSupport.biomeKey(event.getTo().getBlock().getBiome());
        String path = CreativeShardSupport.playerPath(player.getUniqueId());
        List<String> discoveries = data.getStringList(path);
        if (discoveries.contains(biome)) {
            return;
        }

        discoveries.add(biome);
        data.set(path, discoveries);
        String sessionKey = player.getUniqueId() + ":" + biome;
        if (announcedThisSession.add(sessionKey)) {
            String displayName = biome.substring(biome.indexOf(':') + 1)
                    .replace('_', ' ').toLowerCase(Locale.ROOT);
            CreativeShardSupport.send(player, "&dNew atlas entry: &f" + displayName
                    + " &7(" + discoveries.size() + " discovered)");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String playerPrefix = event.getPlayer().getUniqueId() + ":";
        announcedThisSession.removeIf(key -> key.startsWith(playerPrefix));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("clear"))) {
            CreativeShardSupport.send(sender, "&cUsage: /atlas [clear]");
            return true;
        }
        Player player = CreativeShardSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        String path = CreativeShardSupport.playerPath(player.getUniqueId());
        if (args.length == 1) {
            data.set(path, null);
            save();
            CreativeShardSupport.send(player, "&eYour atlas has been cleared. Explore to discover it again.");
            return true;
        }

        List<String> discoveries = data.getStringList(path);
        if (discoveries.isEmpty()) {
            CreativeShardSupport.send(player, "&7Your atlas is blank. Explore different biomes to fill it.");
            return true;
        }
        List<String> names = new ArrayList<>();
        for (String biome : discoveries) {
            String key = biome.substring(biome.indexOf(':') + 1);
            names.add(key.replace('_', ' ').toLowerCase(Locale.ROOT));
        }
        CreativeShardSupport.send(player, "&dAtlas &7(" + discoveries.size() + " biomes): &f"
                + String.join("&7, &f", names));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? CreativeShardSupport.matching(List.of("clear"), args[0]) : List.of();
    }

    private void save() {
        CreativeShardSupport.saveData(plugin, data, DATA_FILE);
    }
}
