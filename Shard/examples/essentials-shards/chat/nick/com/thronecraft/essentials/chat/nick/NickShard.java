package com.thronecraft.essentials.chat.nick;

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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class NickShard implements ShardModule, CommandExecutor, TabCompleter, Listener {
    private final Map<UUID, String> appliedNicknames = new ConcurrentHashMap<>();
    private JavaPlugin plugin;
    private PluginCommand command;

    @Override
    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        registerPermission("shard.essentials.nick", PermissionDefault.TRUE);
        registerPermission("shard.essentials.nick.others", PermissionDefault.OP);
        registerPermission("shard.essentials.nick.color", PermissionDefault.OP);
        command = plugin.getCommand("nick");
        if (command == null) {
            throw new IllegalStateException("Missing /nick command in plugin.yml.");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyStoredNickname(player);
        }
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
        org.bukkit.event.HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            String applied = appliedNicknames.remove(player.getUniqueId());
            if (applied != null && applied.equals(player.getDisplayName())) {
                player.setDisplayName(player.getName());
            }
            if (applied != null && applied.equals(player.getPlayerListName())) {
                player.setPlayerListName(player.getName());
            }
        }
        appliedNicknames.clear();
        command = null;
        plugin = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) {
        if (!sender.hasPermission("shard.essentials.nick")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to change nicknames.");
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            ShardMessages.send(sender, ChatColor.RED + "Usage: /nick [player] <name|off>");
            return true;
        }

        Player target;
        String rawNickname;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                ShardMessages.send(sender, ChatColor.RED + "Usage: /nick <player> <name|off>");
                return true;
            }
            target = player;
            rawNickname = args[0];
        } else {
            if (!sender.hasPermission("shard.essentials.nick.others")) {
                ShardMessages.send(sender, ChatColor.RED + "You cannot change other players' nicknames.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                ShardMessages.send(sender, ChatColor.RED + "That player is not online.");
                return true;
            }
            rawNickname = args[1];
        }

        if (rawNickname.equalsIgnoreCase("off")) {
            YamlConfiguration data = load();
            data.set("nick." + target.getUniqueId(), null);
            if (!save(data, sender)) {
                return true;
            }
            appliedNicknames.remove(target.getUniqueId());
            target.setDisplayName(target.getName());
            target.setPlayerListName(target.getName());
            ShardMessages.send(sender, ChatColor.GREEN + "Reset " + target.getName() + "'s nickname.");
            return true;
        }
        if (!isValidNickname(rawNickname)) {
            ShardMessages.send(sender, ChatColor.RED
                    + "Nicknames may contain letters, numbers, underscores, hyphens, and valid color codes.");
            return true;
        }
        boolean usesColors = rawNickname.indexOf('&') >= 0;
        if (usesColors && !sender.hasPermission("shard.essentials.nick.color")) {
            ShardMessages.send(sender, ChatColor.RED + "You do not have permission to use nickname colors.");
            return true;
        }

        String nickname = ChatColor.translateAlternateColorCodes('&', rawNickname);
        String visible = ChatColor.stripColor(nickname);
        if (visible == null || visible.isBlank() || visible.length() > 16) {
            ShardMessages.send(sender, ChatColor.RED + "Nicknames must have 1-16 visible characters.");
            return true;
        }

        YamlConfiguration data = load();
        data.set("nick." + target.getUniqueId(), rawNickname);
        if (!save(data, sender)) {
            return true;
        }
        target.setDisplayName(nickname);
        target.setPlayerListName(nickname);
        appliedNicknames.put(target.getUniqueId(), nickname);
        ShardMessages.send(sender, ChatColor.GREEN + "Set " + target.getName() + "'s nickname to "
                + nickname + ChatColor.GREEN + ".");
        if (target != sender) {
            ShardMessages.send(target, ChatColor.GREEN + "Your nickname is now "
                    + nickname + ChatColor.GREEN + ".");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if (sender instanceof Player && "off".startsWith(prefix)) {
                suggestions.add("off");
            }
            if (sender.hasPermission("shard.essentials.nick.others")) {
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(suggestions::add);
            }
            return suggestions;
        }
        if (args.length == 2 && sender.hasPermission("shard.essentials.nick.others")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if ("off".startsWith(prefix)) {
                suggestions.add("off");
            }
            return suggestions;
        }
        return List.of();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyStoredNickname(event.getPlayer());
    }

    private void applyStoredNickname(Player player) {
        String rawNickname = load().getString("nick." + player.getUniqueId());
        if (rawNickname == null) {
            return;
        }
        if (!isValidNickname(rawNickname)) {
            plugin.getLogger().warning("Skipped invalid saved nickname for " + player.getUniqueId() + ".");
            return;
        }
        String nickname = ChatColor.translateAlternateColorCodes('&', rawNickname);
        String visible = ChatColor.stripColor(nickname);
        if (visible == null || visible.isBlank() || visible.length() > 16) {
            plugin.getLogger().warning("Skipped invalid saved nickname for " + player.getUniqueId() + ".");
            return;
        }
        player.setDisplayName(nickname);
        player.setPlayerListName(nickname);
        appliedNicknames.put(player.getUniqueId(), nickname);
    }

    private boolean isValidNickname(String nickname) {
        return nickname.length() <= 64
                && nickname.matches("(?i)(?:[A-Za-z0-9_-]|&[0-9A-FK-OR])+");
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "chat.yml"));
    }

    private boolean save(YamlConfiguration data, CommandSender sender) {
        try {
            data.save(new File(plugin.getDataFolder(), "chat.yml"));
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save chat data after a nickname change.", exception);
            ShardMessages.send(sender, ChatColor.RED + "Your nickname could not be saved. Please try again.");
            return false;
        }
    }

    private void registerPermission(String name, PermissionDefault defaultValue) {
        if (Bukkit.getPluginManager().getPermission(name) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(name, defaultValue));
        }
    }
}
