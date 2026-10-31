package com.thronecraft.essentials.protection.god;

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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class GodShard implements ShardModule, CommandExecutor, Listener {
    private JavaPlugin plugin; private PluginCommand command; private Permission permission;
    @Override public void onEnable(JavaPlugin p) { plugin=p; permission=registerPermission(); command=p.getCommand("god"); if(command==null)throw new IllegalStateException("Missing /god command"); command.setExecutor(this); p.getServer().getPluginManager().registerEvents(this,p); }
    @Override public void onDisable() { HandlerList.unregisterAll(this); if(command!=null&&command.getExecutor()==this)command.setExecutor(null); command=null;permission=null;plugin=null; }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(!sender.hasPermission("shard.essentials.god")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission.");return true;}
        if(args.length>1){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /god [player]");return true;}
        Player target;
        if(args.length==0){if(!(sender instanceof Player p)){com.thronecraft.net.shards.ShardMessages.send(sender, "Console must specify a player.");return true;}target=p;}
        else {target=Bukkit.getPlayerExact(args[0]);if(target==null){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online.");return true;}}
        YamlConfiguration data=load();String path="god."+target.getUniqueId();boolean enabled=!data.getBoolean(path);data.set(path,enabled);save(data);
        com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+(enabled?"Enabled":"Disabled")+" god mode for "+target.getName()+".");
        if(target!=sender)com.thronecraft.net.shards.ShardMessages.send(target, ChatColor.YELLOW+"God mode "+(enabled?"enabled.":"disabled."));
        return true;
    }
    @EventHandler public void onDamage(EntityDamageEvent event) { if(event.getEntity() instanceof Player p && YamlConfiguration.loadConfiguration(file()).getBoolean("god."+p.getUniqueId())) event.setCancelled(true); }
    private File file(){return new File(plugin.getDataFolder(),"protection.yml");}
    private YamlConfiguration load(){return YamlConfiguration.loadConfiguration(file());}
    private void save(YamlConfiguration d){try{d.save(file());}catch(Exception e){plugin.getLogger().warning("Could not save protection.yml: "+e.getMessage());}}
    private Permission registerPermission(){String n="shard.essentials.god";Permission old=Bukkit.getPluginManager().getPermission(n);if(old!=null)return old;Permission p=new Permission(n,PermissionDefault.OP);Bukkit.getPluginManager().addPermission(p);return p;}
}
