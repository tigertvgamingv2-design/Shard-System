package com.thronecraft.essentials.chat.ignore;

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
import java.util.UUID;
import java.util.logging.Level;

public final class IgnoreShard implements ShardModule, CommandExecutor, TabCompleter {
    private JavaPlugin plugin;
    private PluginCommand command;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        String permission = "shard.essentials.ignore";
        if (Bukkit.getPluginManager().getPermission(permission) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(permission, PermissionDefault.TRUE));
        }
        command = plugin.getCommand("ignore");
        if (command == null) {
            throw new IllegalStateException("Missing /ignore command in plugin.yml.");
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
        if (!sender.hasPermission("shard.essentials.ignore")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to manage your ignore list.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            ShardMessages.send(sender, "Only players can use /ignore.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            showIgnored(player);
            return true;
        }
        if (args.length != 1) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /ignore <player|list>");
            return true;
        }

        if (!args[0].matches("[A-Za-z0-9_]{1,16}")) {
            ShardMessages.send(sender, ChatColor.RED + "Enter a valid Minecraft player name.");
            return true;
        }
        OfflinePlayer target = resolveTarget(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            ShardMessages.send(sender, ChatColor.RED + "You cannot ignore yourself.");
            return true;
        }
        String targetName = target.getName() == null ? args[0] : target.getName();

        YamlConfiguration data = load();
        String path = "ignored." + player.getUniqueId();
        List<String> ignoredIds = new ArrayList<>(data.getStringList(path));
        String targetId = target.getUniqueId().toString();
        if (ignoredIds.contains(targetId)) {
            ShardMessages.send(sender, ChatColor.YELLOW + "You already ignore " + targetName + ".");
            return true;
        }
        ignoredIds.add(targetId);
        data.set(path, ignoredIds);
        if (save(data)) {
            ShardMessages.send(sender, ChatColor.GREEN + "You are now ignoring " + targetName
                    + ". Use /ignore list to view your list.");
        }
        return true;
    }

    private void showIgnored(Player player) {
        List<String> ids = load().getStringList("ignored." + player.getUniqueId());
        if (ids.isEmpty()) {
            ShardMessages.send(player, ChatColor.GRAY + "Your ignore list is empty.");
            return;
        }
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            try {
                OfflinePlayer ignored = Bukkit.getOfflinePlayer(UUID.fromString(id));
                String name = ignored.getName();
                names.add(name == null ? id : name);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipped invalid player UUID in chat.yml ignore list: " + id);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        ShardMessages.send(player, ChatColor.GOLD + "Ignored players (" + names.size() + "): "
                + ChatColor.WHITE + String.join(ChatColor.GRAY + ", " + ChatColor.WHITE, names));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> matches = new ArrayList<>();
        if ("list".startsWith(prefix)) {
            matches.add("list");
        }
        List<String> ignoredIds = load().getStringList("ignored." + ((Player) sender).getUniqueId());
        Bukkit.getOnlinePlayers().stream()
                .filter(target -> target != sender)
                .filter(target -> !ignoredIds.contains(target.getUniqueId().toString()))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(matches::add);
        return matches;
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "chat.yml"));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolveTarget(String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    private boolean save(YamlConfiguration data) {
        try {
            data.save(new File(plugin.getDataFolder(), "chat.yml"));
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save chat data after an ignore-list change.", exception);
            ShardMessages.send(Bukkit.getConsoleSender(), ChatColor.RED + "Could not save the chat ignore list.");
            return false;
        }
    }
}
