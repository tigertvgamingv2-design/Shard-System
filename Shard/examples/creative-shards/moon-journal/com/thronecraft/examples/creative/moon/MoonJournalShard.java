package com.thronecraft.examples.creative.moon;

import com.thronecraft.examples.creative.CreativeShardSupport;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MoonJournalShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private static final String DATA_FILE = "moon-journal.yml";
    private static final String[] MOON_PHASES = {
            "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous",
            "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent"
    };

    private PluginCommand command;
    private JavaPlugin plugin;
    private YamlConfiguration data;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        data = CreativeShardSupport.loadData(plugin, DATA_FILE);
        command = CreativeShardSupport.register(plugin, "moonjournal", this);
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
        plugin = null;
    }

    @EventHandler
    public void onSpyglass(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.SPYGLASS
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        World world = player.getWorld();
        long worldTime = world.getTime();
        if (worldTime < 13000 || worldTime > 23000 || world.hasStorm() || world.isThundering()
                || world.getHighestBlockYAt(player.getLocation()) >= player.getLocation().getBlockY()) {
            CreativeShardSupport.send(player, "&7You need a clear night sky to make an observation.");
            return;
        }

        int phaseIndex = (int) ((world.getFullTime() / 24000) % MOON_PHASES.length);
        String phase = MOON_PHASES[phaseIndex];
        String observation = world.getName() + "|" + phaseIndex;
        String path = CreativeShardSupport.playerPath(player.getUniqueId());
        List<String> observations = data.getStringList(path);
        if (observations.contains(observation)) {
            CreativeShardSupport.send(player, "&7You already recorded this moon phase in &f"
                    + world.getName() + "&7.");
            return;
        }

        observations.add(observation);
        data.set(path, observations);
        save();
        CreativeShardSupport.send(player, "&dNew sky note: &f" + phase + " &7over &f"
                + world.getName() + "&7. (&f" + observations.size() + "&7 observations)");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            CreativeShardSupport.send(sender, "&cUsage: /moonjournal");
            return true;
        }
        Player player = CreativeShardSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        List<String> observations = data.getStringList(
                CreativeShardSupport.playerPath(player.getUniqueId()));
        if (observations.isEmpty()) {
            CreativeShardSupport.send(player, "&7No entries yet. Use a spyglass under the clear night sky.");
            return true;
        }
        List<String> lines = new ArrayList<>();
        for (String observation : observations) {
            String[] parts = observation.split("\\|", 2);
            if (parts.length == 2) {
                try {
                    int phase = Integer.parseInt(parts[1]);
                    if (phase >= 0 && phase < MOON_PHASES.length) {
                        lines.add(MOON_PHASES[phase] + " in " + parts[0]);
                    }
                } catch (NumberFormatException exception) {
                    plugin.getLogger().warning("Skipping invalid moon journal entry for " + player.getName());
                }
            }
        }
        CreativeShardSupport.send(player, "&dMoon journal &7(" + lines.size() + " entries): &f"
                + String.join("&7, &f", lines));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private void save() {
        CreativeShardSupport.saveData(plugin, data, DATA_FILE);
    }
}
