package com.thronecraft.essentials.chat.reply;

import com.thronecraft.net.shards.ShardMessages;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ReplyShard implements ShardModule, CommandExecutor {
    private static final long COOLDOWN_NANOS = 750_000_000L;
    private static final int MAX_MESSAGE_LENGTH = 256;

    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    private JavaPlugin plugin;
    private PluginCommand command;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        String permission = "shard.essentials.reply";
        if (Bukkit.getPluginManager().getPermission(permission) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(permission, PermissionDefault.TRUE));
        }
        command = plugin.getCommand("reply");
        if (command == null) {
            throw new IllegalStateException("Missing /reply command in plugin.yml.");
        }
        command.setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
        }
        lastMessageAt.clear();
        command = null;
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.reply")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to reply.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            ShardMessages.send(sender, "Only players can use /reply.");
            return true;
        }
        if (args.length == 0) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /reply <message...>");
            return true;
        }
        String message = String.join(" ", args).trim();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH
                || message.chars().anyMatch(Character::isISOControl)) {
            ShardMessages.send(sender, ChatColor.RED + "Messages must be 1-" + MAX_MESSAGE_LENGTH
                    + " characters and cannot contain control characters.");
            return true;
        }
        long now = System.nanoTime();
        Long previous = lastMessageAt.get(player.getUniqueId());
        if (previous != null && now - previous < COOLDOWN_NANOS) {
            ShardMessages.send(sender, ChatColor.RED + "Please wait a moment before sending another message.");
            return true;
        }

        YamlConfiguration data = load();
        UUID targetId;
        try {
            targetId = UUID.fromString(data.getString("reply." + player.getUniqueId(), ""));
        } catch (IllegalArgumentException exception) {
            ShardMessages.send(sender, ChatColor.RED + "You have nobody to reply to.");
            return true;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            ShardMessages.send(sender, ChatColor.RED + "That player is no longer online.");
            return true;
        }
        if (target.equals(player)) {
            ShardMessages.send(sender, ChatColor.RED + "You cannot message yourself.");
            return true;
        }
        if (ignored(data, target.getUniqueId(), player.getUniqueId())) {
            ShardMessages.send(sender, ChatColor.RED + target.getName() + " is ignoring you.");
            return true;
        }
        if (ignored(data, player.getUniqueId(), target.getUniqueId())) {
            ShardMessages.send(sender, ChatColor.RED + "Unignore " + target.getName()
                    + " before sending them a message.");
            return true;
        }

        ShardMessages.send(player, ChatColor.GRAY + "[you -> " + target.getName() + "] "
                + ChatColor.WHITE + message);
        ShardMessages.send(target, ChatColor.GRAY + "[" + player.getName() + " -> you] "
                + ChatColor.WHITE + message);
        lastMessageAt.put(player.getUniqueId(), now);
        data.set("reply." + player.getUniqueId(), target.getUniqueId().toString());
        data.set("reply." + target.getUniqueId(), player.getUniqueId().toString());
        try {
            data.save(new File(plugin.getDataFolder(), "chat.yml"));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save chat data after a private reply.", exception);
            ShardMessages.send(player, ChatColor.YELLOW
                    + "Message delivered, but reply data could not be saved.");
        }
        return true;
    }

    private boolean ignored(YamlConfiguration data, UUID owner, UUID other) {
        return data.getStringList("ignored." + owner).contains(other.toString());
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "chat.yml"));
    }
}
