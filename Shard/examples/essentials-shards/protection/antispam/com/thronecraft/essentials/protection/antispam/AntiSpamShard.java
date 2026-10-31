package com.thronecraft.essentials.protection.antispam;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AntiSpamShard implements ShardModule,CommandExecutor,Listener {
    private static final long COOLDOWN_MILLIS=2000L;
    private volatile JavaPlugin plugin;private PluginCommand command;private Permission permission;
    private final Map<UUID,Long> lastMessage=new HashMap<>();
    @Override public void onEnable(JavaPlugin p){plugin=p;permission=registerPermission();command=p.getCommand("antispam");if(command==null)throw new IllegalStateException("Missing /antispam command");command.setExecutor(this);p.getServer().getPluginManager().registerEvents(this,p);}
    @Override public void onDisable(){HandlerList.unregisterAll(this);if(command!=null&&command.getExecutor()==this)command.setExecutor(null);synchronized(lastMessage){lastMessage.clear();}command=null;permission=null;plugin=null;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("shard.essentials.antispam")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission.");return true;}
        if(args.length!=1){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /antispam <player>");return true;}
        Player target=Bukkit.getPlayerExact(args[0]);if(target==null){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online.");return true;}
        YamlConfiguration data=load();Set<String> ids=new HashSet<>(data.getStringList("antispam.enabled"));String id=target.getUniqueId().toString();
        if(ids.remove(id)){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+"Chat rate limit disabled for "+target.getName()+".");}
        else{ids.add(id);com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+"Chat rate limit enabled for "+target.getName()+".");}
        data.set("antispam.enabled",ids.stream().sorted().toList());save(data);
        synchronized(lastMessage){lastMessage.remove(target.getUniqueId());}
        return true;
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent event){
        JavaPlugin activePlugin=plugin;if(activePlugin==null)return;
        Player player=event.getPlayer();if(player.isOp())return;
        File dataFile=new File(activePlugin.getDataFolder(),"protection.yml");
        if(!YamlConfiguration.loadConfiguration(dataFile).getStringList("antispam.enabled").contains(player.getUniqueId().toString()))return;
        long now=System.currentTimeMillis();synchronized(lastMessage){
            Long previous=lastMessage.put(player.getUniqueId(),now);
            if(previous!=null&&now-previous<COOLDOWN_MILLIS){event.setCancelled(true);com.thronecraft.net.shards.ShardMessages.send(player, ChatColor.RED+"Please wait before chatting again.");}
        }
    }
    private File file(){return new File(plugin.getDataFolder(),"protection.yml");}
    private YamlConfiguration load(){return YamlConfiguration.loadConfiguration(file());}
    private void save(YamlConfiguration d){try{d.save(file());}catch(Exception e){plugin.getLogger().warning("Could not save protection.yml: "+e.getMessage());}}
    private Permission registerPermission(){String n="shard.essentials.antispam";Permission old=Bukkit.getPluginManager().getPermission(n);if(old!=null)return old;Permission p=new Permission(n,PermissionDefault.OP);Bukkit.getPluginManager().addPermission(p);return p;}
}
