package net.coreprotect.thread;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.ActionBar;

/**
 * Task manager for inspector mode status display.
 * Sends periodic action bar messages to players with inspector mode enabled.
 */
public class InspectorStatusTask {
    
    private static final String DEFAULT_MESSAGE = "Inspector Enabled";
    private static final Map<UUID, BukkitTask> ACTIVE_TASKS = new ConcurrentHashMap<>();
    private static Plugin plugin;
    
    /**
     * Initialize the task manager with the plugin instance.
     * 
     * @param plugin The CoreProtect plugin instance
     */
    public static void initialize(Plugin plugin) {
        InspectorStatusTask.plugin = plugin;
    }
    
    /**
     * Start the inspector status task for a player.
     * 
     * @param player The player to start the task for
     */
    public static void startTask(Player player) {
        if (player == null || !Config.getGlobal().SHOW_INSPECTOR_STATUS) {
            return;
        }
        
        // Cancel any existing task first
        stopTask(player);
        
        // Start a new repeating task
        UUID playerUUID = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Make sure player is still online
            Player onlinePlayer = Bukkit.getPlayer(playerUUID);
            if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                stopTask(onlinePlayer);
                return;
            }
            
            // Check if player still has inspector mode enabled
            Boolean inspecting = ConfigHandler.inspecting.get(onlinePlayer.getName());
            if (inspecting == null || !inspecting) {
                stopTask(onlinePlayer);
                return;
            }
            
            // Send the action bar message
            ActionBar.sendCoreProtectMessage(onlinePlayer, DEFAULT_MESSAGE);
        }, 0L, 20L); // Run immediately, then every second (20 ticks)
        
        // Store the task
        ACTIVE_TASKS.put(playerUUID, task);
    }
    
    /**
     * Stop the inspector status task for a player.
     * 
     * @param player The player to stop the task for
     */
    public static void stopTask(Player player) {
        if (player == null) {
            return;
        }
        
        UUID playerUUID = player.getUniqueId();
        BukkitTask task = ACTIVE_TASKS.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * Stop all active inspector status tasks.
     * Should be called when the plugin is disabled.
     */
    public static void stopAllTasks() {
        for (BukkitTask task : ACTIVE_TASKS.values()) {
            task.cancel();
        }
        ACTIVE_TASKS.clear();
    }
} 