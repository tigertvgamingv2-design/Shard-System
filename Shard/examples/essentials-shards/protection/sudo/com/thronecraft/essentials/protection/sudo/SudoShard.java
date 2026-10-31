package com.thronecraft.essentials.protection.sudo;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Locale;
import java.util.Set;

public class SudoShard implements ShardModule,CommandExecutor {
    private static final Set<String> DANGEROUS=Set.of("op","deop","stop","restart","reload","plugins","pl","permission","permissions","pardon","ban","kick");
    private PluginCommand command;private Permission permission;
    @Override public void onEnable(JavaPlugin p){permission=registerPermission();command=p.getCommand("sudo");if(command==null)throw new IllegalStateException("Missing /sudo command");command.setExecutor(this);}
    @Override public void onDisable(){if(command!=null&&command.getExecutor()==this)command.setExecutor(null);command=null;permission=null;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!(sender instanceof Player) || !sender.isOp() || !sender.hasPermission("shard.essentials.sudo")){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"You must be an operator with shard.essentials.sudo.");return true;}
        if(args.length<2){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Usage: /sudo <player> <command...>");return true;}
        Player target=Bukkit.getPlayerExact(args[0]);if(target==null){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"That player is not online.");return true;}
        String requested=String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length)).trim();
        while(requested.startsWith("/"))requested=requested.substring(1);
        String token=requested.split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        if(token.contains(":"))token=token.substring(token.indexOf(':')+1);
        if(DANGEROUS.contains(token)){com.thronecraft.net.shards.ShardMessages.send(sender, ChatColor.RED+"Rejected dangerous command: "+token);return true;}
        boolean accepted=target.performCommand(requested);
        com.thronecraft.net.shards.ShardMessages.send(sender, accepted?ChatColor.GREEN+"Executed command as "+target.getName()+".":ChatColor.YELLOW+"The target command was not handled.");
        return true;
    }
    private Permission registerPermission(){String n="shard.essentials.sudo";Permission old=Bukkit.getPluginManager().getPermission(n);if(old!=null)return old;Permission p=new Permission(n,PermissionDefault.OP);Bukkit.getPluginManager().addPermission(p);return p;}
}
