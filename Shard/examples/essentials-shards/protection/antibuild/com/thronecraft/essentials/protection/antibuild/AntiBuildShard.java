package com.thronecraft.essentials.protection.antibuild;

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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class AntiBuildShard implements ShardModule,CommandExecutor,Listener {
    private JavaPlugin plugin;private PluginCommand command;private Permission permission;
    @Override public void onEnable(JavaPlugin p){plugin=p;permission=registerPermission();command=p.getCommand("antibuild");if(command==null)throw new IllegalStateException("Missing /antibuild command");command.setExecutor(this);p.getServer().getPluginManager().registerEvents(this,p);}
    @Override public void onDisable(){HandlerList.unregisterAll(this);if(command!=null&&command.getExecutor()==this)command.setExecutor(null);command=null;permission=null;plugin=null;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("shard.essentials.antibuild")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission.");return true;}
        if(args.length!=1){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /antibuild <player>");return true;}
        Player target=Bukkit.getPlayerExact(args[0]);if(target==null){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online.");return true;}
        YamlConfiguration d=load();String path="antibuild."+target.getUniqueId();boolean denied=!d.getBoolean(path);d.set(path,denied);save(d);
        com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+(denied?"Build denied for ":"Build restored for ")+target.getName()+".");return true;
    }
    @EventHandler public void onBreak(BlockBreakEvent e){if(denied(e.getPlayer())){e.setCancelled(true);com.thronecraft.net.shards.ShardMessages.send(e.getPlayer(), ChatColor.RED+"You may not build here.");}}
    @EventHandler public void onPlace(BlockPlaceEvent e){if(denied(e.getPlayer())){e.setCancelled(true);com.thronecraft.net.shards.ShardMessages.send(e.getPlayer(), ChatColor.RED+"You may not build here.");}}
    private boolean denied(Player p){return YamlConfiguration.loadConfiguration(file()).getBoolean("antibuild."+p.getUniqueId());}
    private File file(){return new File(plugin.getDataFolder(),"protection.yml");}
    private YamlConfiguration load(){return YamlConfiguration.loadConfiguration(file());}
    private void save(YamlConfiguration d){try{d.save(file());}catch(Exception e){plugin.getLogger().warning("Could not save protection.yml: "+e.getMessage());}}
    private Permission registerPermission(){String n="shard.essentials.antibuild";Permission old=Bukkit.getPluginManager().getPermission(n);if(old!=null)return old;Permission p=new Permission(n,PermissionDefault.OP);Bukkit.getPluginManager().addPermission(p);return p;}
}
