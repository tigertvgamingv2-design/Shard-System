package com.thronecraft.essentials.chat.mailread;

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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class MailReadShard implements ShardModule, CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 5;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ROOT);

    private JavaPlugin plugin;
    private PluginCommand command;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        String permission = "shard.essentials.mailread";
        if (Bukkit.getPluginManager().getPermission(permission) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(permission, PermissionDefault.TRUE));
        }
        command = plugin.getCommand("mailread");
        if (command == null) {
            throw new IllegalStateException("Missing /mailread command in plugin.yml.");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if ("clear".startsWith(prefix)) {
            options.add("clear");
        }
        if ("1".startsWith(prefix)) {
            options.add("1");
        }
        return options;
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
        if (!sender.hasPermission("shard.essentials.mailread")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to read your mail.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            ShardMessages.send(sender, "Only players can use /mailread.");
            return true;
        }
        if (args.length > 1) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /mailread [page|clear]");
            return true;
        }

        YamlConfiguration data = load();
        String path = "mail." + player.getUniqueId();
        List<String> messages = new ArrayList<>(data.getStringList(path));
        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            if (messages.isEmpty()) {
                ShardMessages.send(player, ChatColor.GRAY + "Your mailbox is already empty.");
                return true;
            }
            data.set(path, null);
            try {
                data.save(new File(plugin.getDataFolder(), "chat.yml"));
                ShardMessages.send(player, ChatColor.GREEN + "Cleared " + messages.size()
                        + (messages.size() == 1 ? " message" : " messages") + " from your mailbox.");
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not clear a player's mailbox.", exception);
                ShardMessages.send(player, ChatColor.RED + "Could not clear your mailbox. Please try again.");
            }
            return true;
        }

        int page = 1;
        if (args.length == 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException exception) {
                ShardMessages.send(sender, ChatColor.RED + "Usage: /mailread [page|clear]");
                return true;
            }
            if (page < 1) {
                ShardMessages.send(sender, ChatColor.RED + "Page numbers start at 1.");
                return true;
            }
        }
        if (messages.isEmpty()) {
            ShardMessages.send(player, ChatColor.GRAY + "Your mailbox is empty.");
            return true;
        }

        int pageCount = (messages.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > pageCount) {
            ShardMessages.send(player, ChatColor.RED + "Your mailbox only has " + pageCount
                    + (pageCount == 1 ? " page." : " pages."));
            return true;
        }
        int end = messages.size() - (page - 1) * PAGE_SIZE;
        int start = Math.max(end - PAGE_SIZE, 0);
        ShardMessages.send(player, ChatColor.GOLD + "Mailbox - page " + page + "/" + pageCount
                + " - " + messages.size() + (messages.size() == 1 ? " message" : " messages"));
        for (int index = end - 1; index >= start; index--) {
            displayMessage(player, messages.size() - index, messages.get(index));
        }
        if (page < pageCount) {
            ShardMessages.send(player, ChatColor.GRAY + "Use /mailread " + (page + 1) + " for older mail.");
        }
        ShardMessages.send(player, ChatColor.GRAY + "Use /mailread clear to empty your mailbox.");
        return true;
    }

    private void displayMessage(Player player, int index, String entry) {
        String[] fields = entry.split("\\|", 3);
        if (fields.length == 3) {
            String timestamp;
            try {
                timestamp = DATE_FORMAT.withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(Long.parseLong(fields[0])));
            } catch (IllegalArgumentException exception) {
                timestamp = "date unavailable";
            }
            ShardMessages.send(player, ChatColor.YELLOW + "#" + index + " - " + fields[1]
                    + ChatColor.DARK_GRAY + " - " + timestamp);
            ShardMessages.send(player, ChatColor.WHITE + fields[2]);
            return;
        }
        ShardMessages.send(player, ChatColor.YELLOW + "#" + index + ChatColor.WHITE + " " + entry);
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "chat.yml"));
    }
}
