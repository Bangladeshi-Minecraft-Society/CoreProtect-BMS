package net.coreprotect.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.thread.InspectorStatusTask;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;

public class InspectCommand {
    protected static void runCommand(CommandSender player, boolean permission, String[] args) {
        if (permission) {

            int command = -1;
            ConfigHandler.inspecting.putIfAbsent(player.getName(), false);
            
            // Check if player has only the restricted permission
            boolean hasFullInspect = player.hasPermission("coreprotect.inspect");
            boolean hasBlocksInspect = player.hasPermission("coreprotect.inspect.blocks");
            // Store whether this is restricted mode in the player's session
            ConfigHandler.inspectBlocksOnly.put(player.getName(), !hasFullInspect && hasBlocksInspect);

            if (args.length > 1) {
                String action = args[1];
                if (action.equalsIgnoreCase("on")) {
                    command = 1;
                }
                else if (action.equalsIgnoreCase("off")) {
                    command = 0;
                }
            }

            if (!ConfigHandler.inspecting.get(player.getName())) {
                if (command == 0) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INSPECTOR_ERROR, Selector.SECOND)); // already disabled
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INSPECTOR_TOGGLED, Selector.FIRST)); // now enabled
                    ConfigHandler.inspecting.put(player.getName(), true);
                    
                    // Start the inspector status task for players only
                    if (player instanceof Player) {
                        InspectorStatusTask.startTask((Player) player);
                    }
                }
            }
            else {
                if (command == 1) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INSPECTOR_ERROR, Selector.FIRST)); // already enabled
                }
                else {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.INSPECTOR_TOGGLED, Selector.SECOND)); // now disabled
                    ConfigHandler.inspecting.put(player.getName(), false);
                    
                    // Stop the inspector status task for players only
                    if (player instanceof Player) {
                        InspectorStatusTask.stopTask((Player) player);
                    }
                }
            }

        }
        else {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
        }
    }
}
