package com.thronecraft.essentials.protection.commandblock;

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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommandBlockShard implements ShardModule, CommandExecutor, Listener {
    private static final List<String> DEFAULTS=List.of("op","plugins","pl","reload","rl","stop");
    private JavaPlugin plugin; private PluginCommand command; private Permission permission;
    @Override public void onEnable(JavaPlugin p) {
        plugin=p; permission=registerPermission(); command=p.getCommand("commandblock"); if(command==null)throw new IllegalStateException("Missing /commandblock command"); command.setExecutor(this);
        YamlConfiguration d=load(); if(!d.contains("commandblock.blacklist")){d.set("commandblock.blacklist",DEFAULTS);save(d);}
        p.getServer().getPluginManager().registerEvents(this,p);
    }
    @Override public void onDisable(){HandlerList.unregisterAll(this);if(command!=null&&command.getExecutor()==this)command.setExecutor(null);command=null;permission=null;plugin=null;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("shard.essentials.commandblock")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission.");return true;}
        if(args.length!=2){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /commandblock <player> <command>");return true;}
        Player target=Bukkit.getPlayerExact(args[0]);if(target==null){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online.");return true;}
        String base=normalize(args[1]);if(base.isEmpty()){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Invalid command.");return true;}
        YamlConfiguration data=load();List<String> blacklist=data.getStringList("commandblock.blacklist");
        if(!blacklist.stream().anyMatch(x->normalize(x).equals(base))){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Only commands in protection.yml's commandblock.blacklist can be blocked.");return true;}
        String path="commandblock.blocked."+target.getUniqueId();List<String> blocked=new ArrayList<>(data.getStringList(path));
        if(blocked.contains(base)){blocked.remove(base);com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+"Unblocked /"+base+" for "+target.getName()+".");}
        else{blocked.add(base);com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+"Blocked /"+base+" for "+target.getName()+".");}
        data.set(path,blocked);save(data);return true;
    }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event){
        String raw=event.getMessage().substring(1);String base=normalize(raw.split("\\s+",2)[0]);YamlConfiguration data=load();
        List<String> blacklist=data.getStringList("commandblock.blacklist").stream().map(this::normalize).toList();
        if(blacklist.contains(base)&&data.getStringList("commandblock.blocked."+event.getPlayer().getUniqueId()).stream().map(this::normalize).anyMatch(base::equals)){
            event.setCancelled(true);com.thronecraft.net.shards.ShardMessages.send(event.getPlayer(), ChatColor.RED+"That command is blocked for you.");
        }
    }
    private String normalize(String s){String n=s.trim().toLowerCase(Locale.ROOT);if(n.startsWith("/"))n=n.substring(1);if(n.contains(":"))n=n.substring(n.indexOf(':')+1);return n;}
    private YamlConfiguration load(){return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"protection.yml"));}
    private void save(YamlConfiguration d){try{d.save(new File(plugin.getDataFolder(),"protection.yml"));}catch(Exception e){plugin.getLogger().warning("Could not save protection.yml: "+e.getMessage());}}
    private Permission registerPermission(){String n="shard.essentials.commandblock";Permission old=Bukkit.getPluginManager().getPermission(n);if(old!=null)return old;Permission p=new Permission(n,PermissionDefault.OP);Bukkit.getPluginManager().addPermission(p);return p;}
}
