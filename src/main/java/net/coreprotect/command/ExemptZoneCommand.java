package net.coreprotect.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.config.Config;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.ExemptZone;
import net.coreprotect.model.ExemptZoneManager;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

import java.util.List;

/**
 * Handles the exempt zone commands
 */
public class ExemptZoneCommand {

    private ExemptZoneCommand() {
        throw new IllegalStateException("Command class");
    }

    /**
     * Process the exempt zone command
     * 
     * @param sender The command sender
     * @param args The command arguments
     * @return true if the command was successful
     */
    public static boolean process(CommandSender sender, String[] args) {
        if (!Config.getGlobal().API_ENABLED) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.EXEMPT_ZONE_API_DISABLED));
            return true;
        }
        
        // Check if user has permission to use exempt zone commands
        if (!sender.hasPermission("coreprotect.exemptzone")) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            return true;
        }
        
        if (args.length < 2) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "pos1":
                return handlePos1(sender);
            case "pos2":
                return handlePos2(sender);
            case "create":
                return handleCreate(sender, args);
            case "list":
                return handleList(sender);
            case "delete":
                return handleDelete(sender, args);
            default:
                showHelp(sender);
                return true;
        }
    }
    
    /**
     * Handle the pos1 command
     * 
     * @param sender The command sender
     * @return true if the command was successful
     */
    private static boolean handlePos1(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.EXEMPT_ZONE_PLAYERS_ONLY));
            return true;
        }
        
        Player player = (Player) sender;
        ExemptZoneManager.setPos1(player, player.getLocation());
        
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        
        Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
            Phrase.build(Phrase.EXEMPT_ZONE_POS1_SET, Color.WHITE, String.valueOf(x), String.valueOf(y), String.valueOf(z)));
        
        return true;
    }
    
    /**
     * Handle the pos2 command
     * 
     * @param sender The command sender
     * @return true if the command was successful
     */
    private static boolean handlePos2(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.EXEMPT_ZONE_PLAYERS_ONLY));
            return true;
        }
        
        Player player = (Player) sender;
        ExemptZoneManager.setPos2(player, player.getLocation());
        
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        
        Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
            Phrase.build(Phrase.EXEMPT_ZONE_POS2_SET, Color.WHITE, String.valueOf(x), String.valueOf(y), String.valueOf(z)));
        
        return true;
    }
    
    /**
     * Handle the create command
     * 
     * @param sender The command sender
     * @param args The command arguments
     * @return true if the command was successful
     */
    private static boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.EXEMPT_ZONE_PLAYERS_ONLY));
            return true;
        }
        
        if (args.length < 3) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.EXEMPT_ZONE_CREATE_HELP));
            return true;
        }
        
        Player player = (Player) sender;
        String zoneName = args[2];
        
        if (ExemptZoneManager.createZone(player, zoneName)) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
                Phrase.build(Phrase.EXEMPT_ZONE_CREATE_SUCCESS, Color.WHITE, zoneName));
        } else {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
                Phrase.build(Phrase.EXEMPT_ZONE_CREATE_FAILED));
        }
        
        return true;
    }
    
    /**
     * Handle the list command
     * 
     * @param sender The command sender
     * @return true if the command was successful
     */
    private static boolean handleList(CommandSender sender) {
        List<ExemptZone> zones = ExemptZoneManager.getAllZones();
        
        if (zones.isEmpty()) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
                Phrase.build(Phrase.EXEMPT_ZONE_LIST_EMPTY));
            return true;
        }
        
        Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
            Phrase.build(Phrase.EXEMPT_ZONE_LIST_HEADER));
        
        for (ExemptZone zone : zones) {
            Chat.sendMessage(sender, Color.AQUA + "- " + zone.toString());
        }
        
        return true;
    }
    
    /**
     * Handle the delete command
     * 
     * @param sender The command sender
     * @param args The command arguments
     * @return true if the command was successful
     */
    private static boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
                Phrase.build(Phrase.EXEMPT_ZONE_DELETE_HELP));
            return true;
        }
        
        String zoneName = args[2];
        
        if (ExemptZoneManager.deleteZone(zoneName)) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
                Phrase.build(Phrase.EXEMPT_ZONE_DELETE_SUCCESS, Color.WHITE, zoneName));
        } else {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
                Phrase.build(Phrase.EXEMPT_ZONE_DELETE_FAILED, Color.WHITE, zoneName));
        }
        
        return true;
    }
    
    /**
     * Show the help message
     * 
     * @param sender The command sender
     */
    private static void showHelp(CommandSender sender) {
        Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + 
            Phrase.build(Phrase.EXEMPT_ZONE_HELP_HEADER));
        
        Chat.sendMessage(sender, Color.AQUA + "/co exemptzone pos1" + Color.WHITE + " - " + 
            Phrase.build(Phrase.HELP_PARAMETER, Color.WHITE, "Set position 1"));
            
        Chat.sendMessage(sender, Color.AQUA + "/co exemptzone pos2" + Color.WHITE + " - " + 
            Phrase.build(Phrase.HELP_PARAMETER, Color.WHITE, "Set position 2"));
            
        Chat.sendMessage(sender, Color.AQUA + "/co exemptzone create <name>" + Color.WHITE + " - " + 
            Phrase.build(Phrase.HELP_PARAMETER, Color.WHITE, "Create a new exempt zone"));
            
        Chat.sendMessage(sender, Color.AQUA + "/co exemptzone list" + Color.WHITE + " - " + 
            Phrase.build(Phrase.HELP_PARAMETER, Color.WHITE, "List all exempt zones"));
            
        Chat.sendMessage(sender, Color.AQUA + "/co exemptzone delete <name>" + Color.WHITE + " - " + 
            Phrase.build(Phrase.HELP_PARAMETER, Color.WHITE, "Delete an exempt zone"));
    }
} 