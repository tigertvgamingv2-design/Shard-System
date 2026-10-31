package com.thronecraft.essentials.chat.msg;

import com.thronecraft.net.shards.ShardMessages;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class MsgShard implements ShardModule, CommandExecutor, TabCompleter {
    private static final long COOLDOWN_NANOS = 750_000_000L;
    private static final int MAX_MESSAGE_LENGTH = 256;

    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    private JavaPlugin plugin;
    private PluginCommand command;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        registerPermission(plugin);
        command = plugin.getCommand("msg");
        if (command == null) {
            throw new IllegalStateException("Missing /msg command in plugin.yml.");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        if (command != null) {
            if (command.getExecutor() == this) {
                command.setExecutor(null);
            }
            if (command.getTabCompleter() == this) {
                command.setTabCompleter(null);
            }
        }
        lastMessageAt.clear();
        command = null;
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.msg")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to message players.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            ShardMessages.send(sender, "Only players can use /msg.");
            return true;
        }
        if (args.length < 2) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /msg <player> <message...>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            ShardMessages.send(sender, ChatColor.RED + "That player is not online.");
            return true;
        }
        if (target.equals(player)) {
            ShardMessages.send(sender, ChatColor.RED + "You cannot message yourself.");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH
                || message.chars().anyMatch(Character::isISOControl)) {
            ShardMessages.send(sender, ChatColor.RED + "Messages must be 1-" + MAX_MESSAGE_LENGTH
                    + " characters and cannot contain control characters.");
            return true;
        }
        if (!checkCooldown(player)) {
            ShardMessages.send(sender, ChatColor.RED + "Please wait a moment before sending another message.");
            return true;
        }

        YamlConfiguration data = load();
        String senderId = player.getUniqueId().toString();
        String targetId = target.getUniqueId().toString();
        if (ignored(data, targetId, senderId)) {
            ShardMessages.send(sender, ChatColor.RED + target.getName() + " is ignoring you.");
            return true;
        }
        if (ignored(data, senderId, targetId)) {
            ShardMessages.send(sender, ChatColor.RED + "Unignore " + target.getName()
                    + " before sending them a message.");
            return true;
        }

        ShardMessages.send(player, ChatColor.GRAY + "[you -> " + target.getName() + "] "
                + ChatColor.WHITE + message);
        ShardMessages.send(target, ChatColor.GRAY + "[" + player.getName() + " -> you] "
                + ChatColor.WHITE + message);
        lastMessageAt.put(player.getUniqueId(), System.nanoTime());

        data.set("reply." + senderId, targetId);
        data.set("reply." + targetId, senderId);
        if (!save(data)) {
            ShardMessages.send(player, ChatColor.YELLOW
                    + "Message delivered, but reply data could not be saved.");
        }
        return true;
    }

    private boolean checkCooldown(Player player) {
        long now = System.nanoTime();
        Long previous = lastMessageAt.get(player.getUniqueId());
        return previous == null || now - previous >= COOLDOWN_NANOS;
    }

    private boolean ignored(YamlConfiguration data, String ownerId, String otherId) {
        return data.getStringList("ignored." + ownerId).contains(otherId);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        YamlConfiguration data = load();
        List<String> ignoredIds = data.getStringList("ignored." + player.getUniqueId());
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != player
                    && !ignoredIds.contains(online.getUniqueId().toString())
                    && !ignored(data, online.getUniqueId().toString(), player.getUniqueId().toString())
                    && online.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                matches.add(online.getName());
            }
        }
        return matches;
    }

    private void registerPermission(JavaPlugin plugin) {
        String node = "shard.essentials.msg";
        if (Bukkit.getPluginManager().getPermission(node) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(node, PermissionDefault.TRUE));
        }
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "chat.yml"));
    }

    private boolean save(YamlConfiguration data) {
        try {
            data.save(new File(plugin.getDataFolder(), "chat.yml"));
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save chat data after a private message.", exception);
            return false;
        }
    }
}
