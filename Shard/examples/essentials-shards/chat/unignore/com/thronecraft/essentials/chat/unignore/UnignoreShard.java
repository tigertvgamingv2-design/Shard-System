package com.thronecraft.essentials.chat.unignore;

import com.thronecraft.net.shards.ShardMessages;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class UnignoreShard implements ShardModule, CommandExecutor, TabCompleter {
    private JavaPlugin plugin;
    private PluginCommand command;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        String permission = "shard.essentials.unignore";
        if (Bukkit.getPluginManager().getPermission(permission) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(permission, PermissionDefault.TRUE));
        }
        command = plugin.getCommand("unignore");
        if (command == null) {
            throw new IllegalStateException("Missing /unignore command in plugin.yml.");
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
        command = null;
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.unignore")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to manage your ignore list.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            ShardMessages.send(sender, "Only players can use /unignore.");
            return true;
        }
        if (args.length != 1) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /unignore <player>");
            return true;
        }

        if (!args[0].matches("[A-Za-z0-9_]{1,16}")) {
            ShardMessages.send(sender, ChatColor.RED + "Enter a valid Minecraft player name.");
            return true;
        }
        OfflinePlayer target = resolveTarget(args[0]);
        String path = "ignored." + player.getUniqueId();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "chat.yml"));
        List<String> ignoredIds = new ArrayList<>(data.getStringList(path));
        UUID targetId = target.getUniqueId();
        String targetName = target.getName() == null ? args[0] : target.getName();
        if (!ignoredIds.remove(targetId.toString())) {
            ShardMessages.send(sender, ChatColor.YELLOW + "You do not ignore " + targetName + ".");
            return true;
        }
        data.set(path, ignoredIds);
        try {
            data.save(new File(plugin.getDataFolder(), "chat.yml"));
            ShardMessages.send(sender, ChatColor.GREEN + "You are no longer ignoring "
                    + targetName + ".");
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save chat data after an ignore-list change.", exception);
            ShardMessages.send(sender, ChatColor.RED + "Could not save your ignore list. Please try again.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String id : loadIgnored(player)) {
            try {
                OfflinePlayer ignoredPlayer = Bukkit.getOfflinePlayer(UUID.fromString(id));
                String name = ignoredPlayer.getName();
                if (name != null && prefixMatch(name, prefix)) {
                    matches.add(name);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipped invalid player UUID in chat.yml ignore list: " + id);
            }
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    private boolean prefixMatch(String name, String prefix) {
        return name.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private List<String> loadIgnored(Player player) {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "chat.yml"));
        return data.getStringList("ignored." + player.getUniqueId());
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolveTarget(String name) {
        return Bukkit.getOfflinePlayer(name);
    }
}
