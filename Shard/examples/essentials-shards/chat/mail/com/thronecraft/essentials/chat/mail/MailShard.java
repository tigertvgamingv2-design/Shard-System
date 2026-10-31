package com.thronecraft.essentials.chat.mail;

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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class MailShard implements ShardModule, CommandExecutor, TabCompleter {
    private static final int MAX_MAILBOX_SIZE = 100;
    private static final int MAX_MESSAGE_LENGTH = 512;

    private JavaPlugin plugin;
    private PluginCommand command;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        String permission = "shard.essentials.mail";
        if (Bukkit.getPluginManager().getPermission(permission) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(permission, PermissionDefault.TRUE));
        }
        command = plugin.getCommand("mail");
        if (command == null) {
            throw new IllegalStateException("Missing /mail command in plugin.yml.");
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
    public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
        if (args.length == 1 && "send".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("send");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.mail")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to send mail.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            ShardMessages.send(sender, "Only players can use /mail.");
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("send")) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /mail send <player> <message...>");
            return true;
        }
        String recipientName = args[1];
        if (!recipientName.matches("[A-Za-z0-9_]{1,16}")) {
            ShardMessages.send(sender, ChatColor.RED + "Enter a valid Minecraft player name.");
            return true;
        }
        OfflinePlayer recipient = resolveRecipient(recipientName);
        if (recipient.getUniqueId().equals(player.getUniqueId())) {
            ShardMessages.send(sender, ChatColor.RED + "You cannot send mail to yourself.");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH
                || message.chars().anyMatch(Character::isISOControl)) {
            ShardMessages.send(sender, ChatColor.RED + "Mail must be 1-" + MAX_MESSAGE_LENGTH
                    + " characters and cannot contain control characters.");
            return true;
        }

        YamlConfiguration data = load();
        String path = "mail." + recipient.getUniqueId();
        List<String> mailbox = new ArrayList<>(data.getStringList(path));
        if (mailbox.size() >= MAX_MAILBOX_SIZE) {
            ShardMessages.send(sender, ChatColor.RED + "That player's mailbox is full.");
            return true;
        }
        mailbox.add(System.currentTimeMillis() + "|" + player.getName() + "|" + message);
        data.set(path, mailbox);
        try {
            data.save(new File(plugin.getDataFolder(), "chat.yml"));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player mail.", exception);
            ShardMessages.send(sender, ChatColor.RED + "Mail could not be saved. Please try again.");
            return true;
        }

        String recipientDisplayName = recipient.getName() == null ? recipientName : recipient.getName();
        ShardMessages.send(sender, ChatColor.GREEN + "Mail sent to " + recipientDisplayName + ".");
        Player online = recipient.getPlayer();
        if (online != null) {
            ShardMessages.send(online, ChatColor.YELLOW + "You have new mail. Use /mailread to read it.");
        }
        return true;
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "chat.yml"));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolveRecipient(String name) {
        return Bukkit.getOfflinePlayer(name);
    }
}
