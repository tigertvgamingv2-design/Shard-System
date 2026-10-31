package com.thronecraft.examples.advanced.tpa;

import com.thronecraft.examples.advanced.AdvancedShardSupport;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeleportRequestsShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private static final long REQUEST_LIFETIME_TICKS = 60L * 20L;
    private static final long REQUEST_COOLDOWN_NANOS = 2_000_000_000L;
    private final List<PluginCommand> commands = new ArrayList<>();
    private final Map<UUID, TeleportRequest> requestsByTarget = new HashMap<>();
    private final Map<UUID, Long> lastRequestAt = new HashMap<>();
    private JavaPlugin plugin;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        for (String commandName : List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel")) {
            commands.add(AdvancedShardSupport.register(plugin, commandName, this));
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        for (TeleportRequest request : requestsByTarget.values()) {
            request.expiration().cancel();
        }
        HandlerList.unregisterAll(this);
        requestsByTarget.clear();
        lastRequestAt.clear();
        for (PluginCommand command : commands) {
            AdvancedShardSupport.unregister(command, this);
        }
        commands.clear();
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = AdvancedShardSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        switch (command.getName().toLowerCase(java.util.Locale.ROOT)) {
            case "tpa" -> createRequest(player, args, RequestType.TO_TARGET);
            case "tpahere" -> createRequest(player, args, RequestType.TO_REQUESTER);
            case "tpaccept" -> respond(player, args, true);
            case "tpdeny" -> respond(player, args, false);
            case "tpcancel" -> cancelOwnRequest(player);
            default -> AdvancedShardSupport.send(player, "&cUnknown teleport request command.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && List.of("tpa", "tpahere").contains(command.getName())) {
            return AdvancedShardSupport.matching(
                    AdvancedShardSupport.onlinePlayerNames(), args[0]);
        }
        if (args.length == 1 && List.of("tpaccept", "tpdeny").contains(command.getName())) {
            Player target = sender instanceof Player player ? player : null;
            TeleportRequest request = target == null ? null : requestsByTarget.get(target.getUniqueId());
            return request == null ? List.of()
                    : AdvancedShardSupport.matching(List.of(request.requester().getName()), args[0]);
        }
        return List.of();
    }

    private void createRequest(Player requester, String[] args, RequestType type) {
        if (args.length != 1) {
            AdvancedShardSupport.send(requester, "&cUsage: /" +
                    (type == RequestType.TO_TARGET ? "tpa" : "tpahere") + " <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            AdvancedShardSupport.send(requester, "&cThat player is not online.");
            return;
        }
        if (requester.equals(target)) {
            AdvancedShardSupport.send(requester, "&cYou cannot send a teleport request to yourself.");
            return;
        }
        long now = System.nanoTime();
        Long previous = lastRequestAt.get(requester.getUniqueId());
        if (previous != null && now - previous < REQUEST_COOLDOWN_NANOS) {
            AdvancedShardSupport.send(requester, "&ePlease wait before sending another teleport request.");
            return;
        }
        lastRequestAt.put(requester.getUniqueId(), now);

        cancelOutgoingRequests(requester);
        TeleportRequest existing = requestsByTarget.remove(target.getUniqueId());
        if (existing != null) {
            existing.expiration().cancel();
            if (existing.requester().isOnline()) {
                AdvancedShardSupport.send(existing.requester(), "&eYour previous request was replaced.");
            }
        }

        BukkitTask expiration = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TeleportRequest expired = requestsByTarget.get(target.getUniqueId());
            if (expired != null && expired.requester().equals(requester)) {
                requestsByTarget.remove(target.getUniqueId());
                AdvancedShardSupport.send(requester, "&eYour teleport request expired.");
                if (target.isOnline()) {
                    AdvancedShardSupport.send(target, "&eThe teleport request expired.");
                }
            }
        }, REQUEST_LIFETIME_TICKS);
        requestsByTarget.put(target.getUniqueId(), new TeleportRequest(requester, type, expiration));

        if (type == RequestType.TO_TARGET) {
            AdvancedShardSupport.send(requester, "&aTeleport request sent to &f" + target.getName() + "&a.");
            AdvancedShardSupport.send(target, "&f" + requester.getName()
                    + " &awants to teleport to you. Use &f/tpaccept &aor &f/tpdeny&a.");
        } else {
            AdvancedShardSupport.send(requester, "&aAsked &f" + target.getName() + " &ato teleport to you.");
            AdvancedShardSupport.send(target, "&f" + requester.getName()
                    + " &awants you to teleport to them. Use &f/tpaccept &aor &f/tpdeny&a.");
        }
    }

    private void cancelOutgoingRequests(Player requester) {
        requestsByTarget.entrySet().removeIf(entry -> {
            TeleportRequest request = entry.getValue();
            if (!request.requester().equals(requester)) {
                return false;
            }
            request.expiration().cancel();
            Player oldTarget = Bukkit.getPlayer(entry.getKey());
            if (oldTarget != null && !oldTarget.equals(requester)) {
                AdvancedShardSupport.send(oldTarget, "&e" + requester.getName()
                        + " cancelled their previous teleport request.");
            }
            return true;
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        lastRequestAt.remove(playerId);

        TeleportRequest incoming = requestsByTarget.remove(playerId);
        if (incoming != null) {
            incoming.expiration().cancel();
            if (incoming.requester().isOnline()) {
                AdvancedShardSupport.send(incoming.requester(), "&e" + player.getName()
                        + " left the server; the teleport request was cancelled.");
            }
        }
        cancelOutgoingRequests(player);
    }

    private void respond(Player target, String[] args, boolean accept) {
        if (args.length > 1) {
            AdvancedShardSupport.send(target, "&cUsage: /" + (accept ? "tpaccept" : "tpdeny") + " [player]");
            return;
        }
        TeleportRequest request = requestsByTarget.get(target.getUniqueId());
        if (request == null) {
            AdvancedShardSupport.send(target, "&cYou do not have a pending teleport request.");
            return;
        }
        if (args.length == 1 && !request.requester().getName().equalsIgnoreCase(args[0])) {
            AdvancedShardSupport.send(target, "&cThat player does not have a pending request to you.");
            return;
        }
        requestsByTarget.remove(target.getUniqueId());
        request.expiration().cancel();
        Player requester = request.requester();
        if (!requester.isOnline()) {
            AdvancedShardSupport.send(target, "&cThat requester is no longer online.");
            return;
        }
        if (!accept) {
            AdvancedShardSupport.send(target, "&eTeleport request denied.");
            AdvancedShardSupport.send(requester, "&c" + target.getName() + " denied your teleport request.");
            return;
        }
        Player destination = request.type() == RequestType.TO_TARGET ? target : requester;
        Player traveler = request.type() == RequestType.TO_TARGET ? requester : target;
        if (traveler.teleport(destination.getLocation())) {
            AdvancedShardSupport.send(traveler, "&aTeleported you to &f"
                    + destination.getName() + "&a.");
            Player other = traveler.equals(requester) ? target : requester;
            AdvancedShardSupport.send(other, "&a" + traveler.getName()
                    + " accepted your teleport request.");
        } else {
            AdvancedShardSupport.send(traveler, "&cTeleport failed.");
        }
    }

    private void cancelOwnRequest(Player requester) {
        UUID targetId = requestsByTarget.entrySet().stream()
                .filter(entry -> entry.getValue().requester().equals(requester))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (targetId == null) {
            AdvancedShardSupport.send(requester, "&cYou have no pending teleport request to cancel.");
            return;
        }
        TeleportRequest request = requestsByTarget.remove(targetId);
        request.expiration().cancel();
        AdvancedShardSupport.send(requester, "&eTeleport request cancelled.");
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            AdvancedShardSupport.send(target, "&e" + requester.getName() + " cancelled their request.");
        }
    }

    private enum RequestType {
        TO_TARGET,
        TO_REQUESTER
    }

    private record TeleportRequest(Player requester, RequestType type, BukkitTask expiration) {
    }
}
