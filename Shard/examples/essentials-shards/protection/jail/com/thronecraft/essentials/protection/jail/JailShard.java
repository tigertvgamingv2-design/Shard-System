package com.thronecraft.essentials.protection.jail;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JailShard implements ShardModule, CommandExecutor, Listener {
    private JavaPlugin plugin; private PluginCommand jailCommand, unjailCommand; private Permission jailPermission, unjailPermission;
    @Override public void onEnable(JavaPlugin p) {
        plugin=p; jailPermission=permission("jail"); unjailPermission=permission("unjail");
        jailCommand=p.getCommand("jail"); unjailCommand=p.getCommand("unjail");
        if(jailCommand==null||unjailCommand==null)throw new IllegalStateException("Missing /jail or /unjail command");
        jailCommand.setExecutor(this); unjailCommand.setExecutor(this); p.getServer().getPluginManager().registerEvents(this,p);
    }
    @Override public void onDisable() {
        HandlerList.unregisterAll(this);
        if(jailCommand!=null&&jailCommand.getExecutor()==this)jailCommand.setExecutor(null);
        if(unjailCommand!=null&&unjailCommand.getExecutor()==this)unjailCommand.setExecutor(null);
        jailCommand=null;unjailCommand=null;jailPermission=null;unjailPermission=null;plugin=null;
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(cmd.getName().equalsIgnoreCase("unjail")) {
            if(!sender.hasPermission("shard.essentials.unjail")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission.");return true;}
            if(args.length!=1){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /unjail <player>");return true;}
            Player target=Bukkit.getPlayerExact(args[0]);if(target==null){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online.");return true;}
            YamlConfiguration d=load();List<String> jailed=new ArrayList<>(d.getStringList("jailed"));
            jailed.remove(target.getUniqueId().toString());d.set("jailed",jailed);save(d);
            com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+target.getName()+" has been released.");com.thronecraft.net.shards.ShardMessages.send(target, ChatColor.GREEN+"You have been released from jail.");return true;
        }
        if(!sender.hasPermission("shard.essentials.jail")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission.");return true;}
        if(args.length<1){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /jail <player> [reason...]");return true;}
        Player target=Bukkit.getPlayerExact(args[0]);if(target==null){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online.");return true;}
        YamlConfiguration d=load();Location hold=readLocation(d);
        if(hold==null) { hold=target.getLocation().clone(); writeLocation(d,hold); }
        List<String> jailed=new ArrayList<>(d.getStringList("jailed"));
        String id=target.getUniqueId().toString();if(!jailed.contains(id))jailed.add(id);
        d.set("jailed",jailed);save(d);
        target.teleport(hold);com.thronecraft.net.shards.ShardMessages.send(target, ChatColor.RED+"You have been jailed."+(args.length>1?" Reason: "+String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length)):""));
        com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+"Jailed "+target.getName()+".");return true;
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if(!isJailed(e.getPlayer()))return;
        Location to=e.getTo(), from=e.getFrom();
        if(to!=null&&(to.getBlockX()!=from.getBlockX()||to.getBlockY()!=from.getBlockY()||to.getBlockZ()!=from.getBlockZ()))e.setCancelled(true);
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) { if(isJailed(e.getPlayer())) { Location hold=readLocation(load()); if(hold!=null)e.getPlayer().teleport(hold); } }
    private boolean isJailed(Player p){return load().getStringList("jailed").contains(p.getUniqueId().toString());}
    private Location readLocation(YamlConfiguration d) {
        String name=d.getString("jail.location.world");if(name==null)return null;World w=Bukkit.getWorld(name);if(w==null)return null;
        return new Location(w,d.getDouble("jail.location.x"),d.getDouble("jail.location.y"),d.getDouble("jail.location.z"),(float)d.getDouble("jail.location.yaw"),(float)d.getDouble("jail.location.pitch"));
    }
    private void writeLocation(YamlConfiguration d,Location l){d.set("jail.location.world",l.getWorld().getName());d.set("jail.location.x",l.getX());d.set("jail.location.y",l.getY());d.set("jail.location.z",l.getZ());d.set("jail.location.yaw",l.getYaw());d.set("jail.location.pitch",l.getPitch());}
    private YamlConfiguration load(){return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"protection.yml"));}
    private void save(YamlConfiguration d){try{d.save(new File(plugin.getDataFolder(),"protection.yml"));}catch(Exception e){plugin.getLogger().warning("Could not save protection.yml: "+e.getMessage());}}
    private Permission permission(String name){String n="shard.essentials."+name;Permission old=Bukkit.getPluginManager().getPermission(n);if(old!=null)return old;Permission p=new Permission(n,PermissionDefault.OP);Bukkit.getPluginManager().addPermission(p);return p;}
}
