package com.thronecraft.examples.creative.trail;

import com.thronecraft.examples.creative.CreativeShardSupport;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TrailEchoShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private static final int MAX_POINTS = 32;
    private static final double SAMPLE_DISTANCE_SQUARED = 64;

    private final Map<UUID, Trail> trails = new HashMap<>();
    private final Map<UUID, BukkitTask> previews = new HashMap<>();
    private PluginCommand command;
    private JavaPlugin plugin;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        command = CreativeShardSupport.register(plugin, "trail", this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        for (BukkitTask preview : previews.values()) {
            preview.cancel();
        }
        previews.clear();
        trails.clear();
        if (command != null) {
            CreativeShardSupport.unregister(command, this);
            command = null;
        }
        plugin = null;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }
        Trail trail = trails.get(event.getPlayer().getUniqueId());
        if (trail == null || !trail.recording()
                || event.getFrom().distanceSquared(event.getTo()) < SAMPLE_DISTANCE_SQUARED) {
            return;
        }
        Location point = CreativeShardSupport.blockLocation(event.getTo());
        if (trail.points().isEmpty()
                || trail.points().get(trail.points().size() - 1).distanceSquared(point) >= SAMPLE_DISTANCE_SQUARED) {
            if (trail.points().size() == MAX_POINTS) {
                trail.points().remove(0);
            }
            trail.points().add(point);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        trails.remove(playerId);
        BukkitTask preview = previews.remove(playerId);
        if (preview != null) {
            preview.cancel();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CreativeShardSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 1) {
            usage(player);
            return true;
        }
        UUID playerId = player.getUniqueId();
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "start" -> start(player, playerId);
            case "stop" -> stop(player, playerId);
            case "show" -> show(player, playerId);
            case "clear" -> clear(player, playerId);
            default -> usage(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1
                ? CreativeShardSupport.matching(List.of("start", "stop", "show", "clear"), args[0])
                : List.of();
    }

    private void start(Player player, UUID playerId) {
        Trail trail = trails.computeIfAbsent(playerId, ignored -> new Trail());
        trail.points().clear();
        trail.points().add(CreativeShardSupport.blockLocation(player.getLocation()));
        trail.setRecording(true);
        CreativeShardSupport.send(player, "&aTrail recording started. Walk at least 8 blocks between markers.");
    }

    private void stop(Player player, UUID playerId) {
        Trail trail = trails.get(playerId);
        if (trail == null || !trail.recording()) {
            CreativeShardSupport.send(player, "&eYou are not recording a trail.");
            return;
        }
        trail.setRecording(false);
        CreativeShardSupport.send(player, "&eTrail recording stopped with &f"
                + trail.points().size() + " &emarkers.");
    }

    private void show(Player player, UUID playerId) {
        Trail trail = trails.get(playerId);
        if (trail == null || trail.points().size() < 2) {
            CreativeShardSupport.send(player, "&7Record at least two trail markers first: &f/trail start");
            return;
        }
        BukkitTask existing = previews.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }
        List<Location> points = List.copyOf(trail.points());
        BukkitTask task = new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (!player.isOnline() || index >= points.size()) {
                    previews.remove(playerId);
                    cancel();
                    return;
                }
                Location point = points.get(index++);
                point.getWorld().spawnParticle(Particle.END_ROD, point, 3, 0.15, 0.15, 0.15, 0);
            }
        }.runTaskTimer(plugin, 0L, 3L);
        previews.put(playerId, task);
        CreativeShardSupport.send(player, "&dShowing your last &f" + points.size()
                + " &dtrail markers. They are temporary and reset when you leave.");
    }

    private void clear(Player player, UUID playerId) {
        Trail trail = trails.remove(playerId);
        BukkitTask preview = previews.remove(playerId);
        if (preview != null) {
            preview.cancel();
        }
        if (trail == null) {
            CreativeShardSupport.send(player, "&7You do not have a recorded trail.");
            return;
        }
        CreativeShardSupport.send(player, "&eYour trail was cleared.");
    }

    private void usage(Player player) {
        CreativeShardSupport.send(player, "&7Use &f/trail start|stop|show|clear");
    }

    private static final class Trail {
        private final List<Location> points = new ArrayList<>();
        private boolean recording;

        private List<Location> points() {
            return points;
        }

        private boolean recording() {
            return recording;
        }

        private void setRecording(boolean recording) {
            this.recording = recording;
        }
    }
}
