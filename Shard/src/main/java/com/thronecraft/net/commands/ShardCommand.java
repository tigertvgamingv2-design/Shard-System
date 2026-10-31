package com.thronecraft.net.commands;

import com.thronecraft.net.managers.ShardManager;
import com.thronecraft.net.managers.ShardManager.ShardInfo;
import com.thronecraft.net.web.DashboardServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class ShardCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = ChatColor.DARK_AQUA + "[Shard] " + ChatColor.RESET;
    private final JavaPlugin plugin;
    private final ShardManager shardManager;
    private final DashboardServer dashboardServer;

    public ShardCommand(JavaPlugin plugin, ShardManager shardManager, DashboardServer dashboardServer) {
        this.plugin = plugin;
        this.shardManager = shardManager;
        this.dashboardServer = dashboardServer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("dashboard")) {
            linkDashboard(sender, args);
            return true;
        }
        if (!sender.hasPermission("shard.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "help" -> sendHelp(sender, label);
                case "list" -> listShards(sender);
                case "status" -> showStatus(sender);
                case "folder" -> showFolder(sender);
                case "info" -> showInfo(sender, args);
                case "validate" -> validateShard(sender, args);
                case "load" -> loadShard(sender, args);
                case "unload" -> unloadShard(sender, args);
                case "enable" -> setShardEnabled(sender, args, true);
                case "disable" -> setShardEnabled(sender, args, false);
                case "reload" -> reloadShards(sender, args);
                case "reloadplugin" -> reloadPlugin(sender);
                default -> sendHelp(sender, label);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Shard command failed.", exception);
            sender.sendMessage(PREFIX + ChatColor.RED + "Operation failed: "
                    + (exception.getMessage() == null ? "see console for details." : exception.getMessage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission("shard.admin")) {
                suggestions.addAll(List.of("help", "list", "info", "status", "folder", "validate",
                        "load", "unload", "enable", "disable", "reload", "reloadplugin"));
            }
            if (sender.hasPermission("shard.dashboard.admin")
                    || sender.hasPermission("shard.dashboard.moderator")) {
                suggestions.add("dashboard");
            }
            return filter(suggestions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("dashboard")
                && (sender.hasPermission("shard.dashboard.admin")
                || sender.hasPermission("shard.dashboard.moderator"))) {
            return filter(List.of("link"), args[1]);
        }
        if (!sender.hasPermission("shard.admin")) {
            return List.of();
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (List.of("info", "unload", "enable", "disable").contains(subcommand)) {
                return filter(shardManager.getShards().stream().map(shard -> shard.name()).toList(), args[1]);
            }
            if (subcommand.equals("reload")) {
                List<String> suggestions = new ArrayList<>(
                        shardManager.getShards().stream().map(shard -> shard.name()).toList());
                suggestions.add("all");
                return filter(suggestions, args[1]);
            }
            if (List.of("load", "validate").contains(subcommand)) {
                try {
                    File[] jars = shardManager.getShardFolder().listFiles((directory, name) ->
                            name.toLowerCase(Locale.ROOT).endsWith(".jar"));
                    if (jars != null) {
                        List<String> jarNames = new ArrayList<>();
                        for (File jar : jars) {
                            if (jar != null) {
                                jarNames.add(jar.getName());
                            }
                        }
                        return filter(jarNames, args[1]);
                    }
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.WARNING, "Could not list shard JARs for tab completion.", exception);
                }
            }
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.DARK_AQUA + "Shard commands:");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " list" + ChatColor.GRAY + " - List loaded shards");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " info <shard>" + ChatColor.GRAY + " - Show shard details");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " status" + ChatColor.GRAY + " - Show loader status");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " folder" + ChatColor.GRAY + " - Show the shard folder");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " validate <file.jar>" + ChatColor.GRAY + " - Check a shard JAR");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " load <file.jar>" + ChatColor.GRAY + " - Load a shard JAR");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " unload <shard>" + ChatColor.GRAY + " - Unload a shard");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " enable|disable <shard>" + ChatColor.GRAY
                + " - Change shard state");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " reload [shard|all]" + ChatColor.GRAY
                + " - Reload one or all shards");
        sender.sendMessage(ChatColor.AQUA + "/" + label + " reloadplugin" + ChatColor.GRAY
                + " - Disable and re-enable Shard");
        if (sender.hasPermission("shard.dashboard.admin")
                || sender.hasPermission("shard.dashboard.moderator")) {
            sender.sendMessage(ChatColor.AQUA + "/" + label + " dashboard link" + ChatColor.GRAY
                    + " - Create a staff sign-in code");
        }
    }

    private void linkDashboard(CommandSender sender, String[] args) {
        if (args.length != 2 || !args[1].equalsIgnoreCase("link")) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Use /shard dashboard link in-game to create a staff sign-in code.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Run this command in-game as a staff player.");
            return;
        }
        if (!player.hasPermission("shard.dashboard.admin")
                && !player.hasPermission("shard.dashboard.moderator")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You do not have dashboard staff permission.");
            return;
        }
        if (dashboardServer == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "The web dashboard is not available.");
            return;
        }
        try {
            String code = dashboardServer.issueLoginCode(player);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Dashboard link code (expires in 5 minutes): "
                    + ChatColor.WHITE + code);
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Enter it on the dashboard sign-in page. Do not share it.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage(PREFIX + ChatColor.RED + exception.getMessage());
        }
    }

    private void listShards(CommandSender sender) {
        var shards = shardManager.getShards();
        if (shards.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No shards are currently loaded.");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Loaded shards (" + shards.size() + "):");
        for (ShardInfo shard : shards) {
            ChatColor stateColor = shard.enabled() ? ChatColor.GREEN : ChatColor.RED;
            String state = shard.enabled() ? "enabled" : "disabled";
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + shard.name()
                    + ChatColor.GRAY + " [" + stateColor + state + ChatColor.GRAY + "]");
        }
    }

    private void showInfo(CommandSender sender, String[] args) {
        if (!requireArgument(sender, args, "info <shard>")) {
            return;
        }
        ShardInfo shard = shardManager.getShard(args[1]).orElse(null);
        if (shard == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Shard not found: " + args[1]);
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.AQUA + shard.name());
        sender.sendMessage(ChatColor.GRAY + "File: " + ChatColor.WHITE + shard.fileName());
        sender.sendMessage(ChatColor.GRAY + "State: "
                + (shard.enabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
    }

    private void showStatus(CommandSender sender) {
        var shards = shardManager.getShards();
        long enabled = 0;
        for (ShardInfo shard : shards) {
            if (shard != null && shard.enabled()) {
                enabled++;
            }
        }
        sender.sendMessage(PREFIX + ChatColor.AQUA + "Shard loader status");
        sender.sendMessage(ChatColor.GRAY + "Loaded: " + ChatColor.WHITE + shards.size());
        sender.sendMessage(ChatColor.GRAY + "Enabled: " + ChatColor.GREEN + enabled);
        sender.sendMessage(ChatColor.GRAY + "Disabled: " + ChatColor.RED + (shards.size() - enabled));
    }

    private void showFolder(CommandSender sender) throws Exception {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Shard JAR folder: " + ChatColor.WHITE
                + shardManager.getShardFolder().getAbsolutePath());
    }

    private void validateShard(CommandSender sender, String[] args) throws Exception {
        if (!requireArgument(sender, args, "validate <file.jar>")) {
            return;
        }
        shardManager.validateShard(args[1]);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Shard JAR is valid: " + args[1]);
    }

    private void loadShard(CommandSender sender, String[] args) throws Exception {
        if (!requireArgument(sender, args, "load <file.jar>")) {
            return;
        }
        shardManager.loadShard(args[1]);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Loaded shard JAR: " + args[1]);
    }

    private void unloadShard(CommandSender sender, String[] args) throws Exception {
        if (!requireArgument(sender, args, "unload <shard>")) {
            return;
        }
        shardManager.unloadShard(args[1]);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Unloaded shard: " + args[1]);
    }

    private void setShardEnabled(CommandSender sender, String[] args, boolean enable) throws Exception {
        String verb = enable ? "enable" : "disable";
        if (!requireArgument(sender, args, verb + " <shard>")) {
            return;
        }
        if (enable) {
            shardManager.enableShard(args[1]);
        } else {
            shardManager.disableShard(args[1]);
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + (enable ? "Enabled" : "Disabled")
                + " shard: " + args[1]);
    }

    private void reloadShards(CommandSender sender, String[] args) throws Exception {
        if (args.length > 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /shard reload [shard|all]");
            return;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("all")) {
            int loadedCount = shardManager.reloadAllShards();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Reloaded shard JARs. Loaded "
                    + loadedCount + " shard(s). Check console for any load failures.");
            return;
        }
        shardManager.reloadShard(args[1]);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Reloaded shard: " + args[1]);
    }

    private void reloadPlugin(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Reloading Shard plugin...");
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getPluginManager().disablePlugin(plugin);
            Bukkit.getPluginManager().enablePlugin(plugin);
        });
    }

    private boolean requireArgument(CommandSender sender, String[] args, String usage) {
        if (args.length >= 2 && args[1] != null && !args[1].isBlank()) {
            return true;
        }
        sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /shard " + usage);
        return false;
    }

    private List<String> filter(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
