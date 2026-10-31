package com.thronecraft.essentials.protection.tempban;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Date;

public class TempbanShard implements ShardModule,CommandExecutor {
    private PluginCommand command;private Permission permission;
    @Override public void onEnable(JavaPlugin p){permission=registerPermission();command=p.getCommand("tempban");if(command==null)throw new IllegalStateException("Missing /tempban command");command.setExecutor(this);}
    @Override public void onDisable(){if(command!=null&&command.getExecutor()==this)command.setExecutor(null);command=null;permission=null;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("shard.essentials.tempban")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You lack permission.");return true;}
        if(args.length<2){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /tempban <player> <minutes> [reason...]");return true;}
        if(!args[0].matches("[A-Za-z0-9_]{1,16}")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Invalid player name.");return true;}
        long minutes;try{minutes=Long.parseLong(args[1]);}catch(NumberFormatException ex){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Duration must be a whole number of minutes.");return true;}
        if(minutes<1||minutes>525600){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Duration must be between 1 and 525600 minutes.");return true;}
        long millis;try{millis=Math.multiplyExact(minutes,60_000L);Math.addExact(System.currentTimeMillis(),millis);}catch(ArithmeticException ex){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That duration is too long.");return true;}
        String reason=args.length>2?String.join(" ",java.util.Arrays.copyOfRange(args,2,args.length)):"Temporary ban";
        String who=args[0];Date expires=new Date(System.currentTimeMillis()+millis);
        Bukkit.getBanList(BanList.Type.NAME).addBan(who,reason,expires,sender.getName());
        Player online=Bukkit.getPlayerExact(who);if(online!=null)online.kickPlayer(ChatColor.RED+"Banned for "+minutes+" minute(s): "+reason);
        com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.GREEN+"Banned "+who+" for "+minutes+" minute(s).");return true;
    }
    private Permission registerPermission(){String n="shard.essentials.tempban";Permission old=Bukkit.getPluginManager().getPermission(n);if(old!=null)return old;Permission p=new Permission(n,PermissionDefault.OP);Bukkit.getPluginManager().addPermission(p);return p;}
}
