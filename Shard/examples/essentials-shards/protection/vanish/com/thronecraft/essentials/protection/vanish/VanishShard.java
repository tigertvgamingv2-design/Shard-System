package com.thronecraft.essentials.protection.vanish;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VanishShard implements ShardModule, CommandExecutor, Listener {
    private JavaPlugin plugin; private PluginCommand command; private Permission permission;
    @Override public void onEnable(JavaPlugin p) {
        plugin=p; permission=registerPermission(); command=p.getCommand("vanish");
        if(command==null) throw new IllegalStateException("Missing /vanish command");
        command.setExecutor(this); p.getServer().getPluginManager().registerEvents(this,p);
        for(Player target:Bukkit.getOnlinePlayers()) if(vanished(target)) hide(target);
    }
    @Override public void onDisable() {
        HandlerList.unregisterAll(this);
        for(Player target:Bukkit.getOnlinePlayers()) for(Player viewer:Bukkit.getOnlinePlayers()) viewer.showPlayer(plugin,target);
        if(command!=null && command.getExecutor()==this) command.setExecutor(null);
        command=null; permission=null; plugin=null;
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(!sender.hasPermission("shard.essentials.vanish")) { com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission."); return true; }
        if(args.length>1) { com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /vanish [player]"); return true; }
        Player target;
        if(args.length==0) { if(!(sender instanceof Player p)) { com.thronecraft.net.shards.ShardMessages.send(sender, "Console must specify a player."); return true; } target=p; }
        else { target=Bukkit.getPlayerExact(args[0]); if(target==null) { com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online."); return true; } }
        YamlConfiguration data=load(); List<String> ids=new ArrayList<>(data.getStringList("vanished")); String id=target.getUniqueId().toString();
        if(ids.remove(id)) { data.set("vanished",ids); save(data); for(Player viewer:Bukkit.getOnlinePlayers()) viewer.showPlayer(plugin,target); com.thronecraft.net.shards.ShardMessages.send(target, ChatColor.YELLOW+"You are visible."); }
        else { ids.add(id); data.set("vanished",ids); save(data); hide(target); com.thronecraft.net.shards.ShardMessages.send(target, ChatColor.YELLOW+"You are now vanished."); }
        com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+(vanished(target)?"Vanished ":"Visible ")+target.getName()+"."); return true;
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { for(Player target:Bukkit.getOnlinePlayers()) if(vanished(target)) event.getPlayer().hidePlayer(plugin,target); }
    private boolean vanished(Player p) { return load().getStringList("vanished").contains(p.getUniqueId().toString()); }
    private void hide(Player target) { for(Player viewer:Bukkit.getOnlinePlayers()) if(!viewer.equals(target)) viewer.hidePlayer(plugin,target); }
    private Permission registerPermission() { String n="shard.essentials.vanish"; Permission old=Bukkit.getPluginManager().getPermission(n); if(old!=null)return old; Permission p=new Permission(n,PermissionDefault.OP); Bukkit.getPluginManager().addPermission(p); return p; }
    private YamlConfiguration load() { return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"protection.yml")); }
    private void save(YamlConfiguration d) { try { d.save(new File(plugin.getDataFolder(),"protection.yml")); } catch(Exception e) { plugin.getLogger().warning("Could not save protection.yml: "+e.getMessage()); } }
}
