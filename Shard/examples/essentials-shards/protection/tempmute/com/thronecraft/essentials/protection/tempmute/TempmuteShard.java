package com.thronecraft.essentials.protection.tempmute;

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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class TempmuteShard implements ShardModule,CommandExecutor,Listener {
    private JavaPlugin plugin;private PluginCommand command;private Permission permission;
    @Override public void onEnable(JavaPlugin p){plugin=p;permission=registerPermission();command=p.getCommand("tempmute");if(command==null)throw new IllegalStateException("Missing /tempmute command");command.setExecutor(this);p.getServer().getPluginManager().registerEvents(this,p);}
    @Override public void onDisable(){HandlerList.unregisterAll(this);if(command!=null&&command.getExecutor()==this)command.setExecutor(null);command=null;permission=null;plugin=null;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("shard.essentials.tempmute")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission.");return true;}
        if(args.length<2){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /tempmute <player> <minutes> [reason...]");return true;}
        Player target=Bukkit.getPlayerExact(args[0]);if(target==null){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online.");return true;}
        long minutes;try{minutes=Long.parseLong(args[1]);}catch(NumberFormatException ex){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Duration must be a whole number of minutes.");return true;}
        if(minutes<1||minutes>525600){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Duration must be between 1 and 525600 minutes.");return true;}
        long until;try{until=Math.addExact(System.currentTimeMillis(),Math.multiplyExact(minutes,60_000L));}catch(ArithmeticException ex){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That duration is too long.");return true;}
        YamlConfiguration d=load();d.set("muted-until."+target.getUniqueId(),until);save(d);
        String reason=args.length>2?": "+String.join(" ",java.util.Arrays.copyOfRange(args,2,args.length)):"";
        com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+"Muted "+target.getName()+" for "+minutes+" minute(s).");com.thronecraft.net.shards.ShardMessages.send(target, ChatColor.RED+"You are muted for "+minutes+" minute(s)"+reason+".");return true;
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent e){
        long until=YamlConfiguration.loadConfiguration(file()).getLong("muted-until."+e.getPlayer().getUniqueId());
        if(until>System.currentTimeMillis()){e.setCancelled(true);com.thronecraft.net.shards.ShardMessages.send(e.getPlayer(), ChatColor.RED+"You are temporarily muted.");}
    }
    private File file(){return new File(plugin.getDataFolder(),"protection.yml");}
    private YamlConfiguration load(){return YamlConfiguration.loadConfiguration(file());}
    private void save(YamlConfiguration d){try{d.save(file());}catch(Exception e){plugin.getLogger().warning("Could not save protection.yml: "+e.getMessage());}}
    private Permission registerPermission(){String n="shard.essentials.tempmute";Permission old=Bukkit.getPluginManager().getPermission(n);if(old!=null)return old;Permission p=new Permission(n,PermissionDefault.OP);Bukkit.getPluginManager().addPermission(p);return p;}
}
