package com.thronecraft.examples.essentials.utility.fly;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlyShard implements ShardModule, CommandExecutor {
    private final Map<UUID, FlightState> changedStates = new HashMap<>();
    private org.bukkit.command.PluginCommand command;

    public FlyShard() {
    }

    @Override
    public void onEnable(JavaPlugin plugin) {
        command = plugin.getCommand("fly");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        for (Map.Entry<UUID, FlightState> entry : changedStates.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            FlightState state = entry.getValue();
            if (player != null) {
                if (state.originalAllowFlight && !player.getAllowFlight()
                        && state.lastAllowFlight == player.getAllowFlight()) {
                    player.setAllowFlight(true);
                }
                if (!state.originalAllowFlight && player.getAllowFlight()
                        && state.lastAllowFlight == player.getAllowFlight()) {
                    player.setAllowFlight(false);
                }
                if (state.originalFlying != player.isFlying()
                        && state.lastFlying == player.isFlying()) {
                    player.setFlying(state.originalFlying);
                }
            }
        }
        changedStates.clear();
        if (command != null) command.setExecutor(null);
        command = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.fly")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission to use this command.");
            return true;
        }
        if (args.length > 1) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /fly [player]");
            return true;
        }
        Player target;
        if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "That player is not online.");
                return true;
            }
            if (sender instanceof Player player && !player.isOp() && target.isOp()) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "You cannot target an operator.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /fly <online player>");
            return true;
        }
        FlightState state = changedStates.computeIfAbsent(target.getUniqueId(),
                ignored -> new FlightState(target.getAllowFlight(), target.isFlying()));
        if (!target.getAllowFlight()) {
            target.setAllowFlight(true);
        }
        target.setFlying(!target.isFlying());
        state.lastAllowFlight = target.getAllowFlight();
        state.lastFlying = target.isFlying();
        com.thronecraft.net.shards.ShardMessages.send(sender, (target.isFlying() ? "Enabled" : "Disabled") + " flight for " + target.getName() + ".");
        if (target != sender) com.thronecraft.net.shards.ShardMessages.send(target, "Flight has been " + (target.isFlying() ? "enabled." : "disabled."));
        return true;
    }

    private static final class FlightState {
        private final boolean originalAllowFlight;
        private final boolean originalFlying;
        private boolean lastAllowFlight;
        private boolean lastFlying;

        private FlightState(boolean originalAllowFlight, boolean originalFlying) {
            this.originalAllowFlight = originalAllowFlight;
            this.originalFlying = originalFlying;
            this.lastAllowFlight = originalAllowFlight;
            this.lastFlying = originalFlying;
        }
    }
}
