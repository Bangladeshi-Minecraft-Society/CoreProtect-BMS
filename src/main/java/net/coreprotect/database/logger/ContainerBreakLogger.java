package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.model.ContainerManager;
import net.coreprotect.model.ContainerState;
import net.coreprotect.utility.ItemUtils;

public class ContainerBreakLogger {

    private ContainerBreakLogger() {
        throw new IllegalStateException("Database class");
    }

    public static void log(PreparedStatement preparedStmt, int batchCount, String player, Location l, Material type, ItemStack[] oldInventory) {
        try {
            // Create a direct dump of the container contents before merging
            // This ensures we have the exact inventory state to restore later
            String loggingContainerId = player.toLowerCase(Locale.ROOT) + "." + l.getBlockX() + "." + l.getBlockY() + "." + l.getBlockZ();
            
            Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: Processing break at " + 
                l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + " by " + player);
            
            // Debug: Check if we got any items
            if (oldInventory == null) {
                Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: oldInventory is null");
            } else {
                Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: oldInventory has " + oldInventory.length + " slots");
                
                // Count non-empty slots
                int itemCount = 0;
                for (ItemStack item : oldInventory) {
                    if (item != null && item.getType() != Material.AIR) {
                        itemCount++;
                    }
                }
                Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: Found " + itemCount + " items in container");
            }
            
            // First, save the raw container state with exact slot positions
            if (Config.getGlobal().PRESERVE_CONTAINER_SLOTS && oldInventory != null) {
                // Create a map of slot positions to their items
                Map<Integer, ItemStack> slotMap = new HashMap<>();
                for (int i = 0; i < oldInventory.length; i++) {
                    if (oldInventory[i] != null && oldInventory[i].getType() != Material.AIR) {
                        // Clone the item to ensure we have an independent copy
                        ItemStack itemClone = oldInventory[i].clone();
                        slotMap.put(i, itemClone);
                        
                        // Debug logging
                        Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: Saved item in slot " + i + ": " + 
                            itemClone.getType() + " x" + itemClone.getAmount());
                    }
                }
                
                // Only proceed if we found items
                if (!slotMap.isEmpty()) {
                    // Store the slot map for future rollbacks
                    List<Map<Integer, ItemStack>> slotMaps = ConfigHandler.oldContainerWithSlots.get(loggingContainerId);
                    
                    if (slotMaps != null) {
                        // Add this state to the front (most recent)
                        slotMaps.add(0, slotMap);
                        Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: Added to existing container states, now have " + 
                            slotMaps.size() + " states");
                    } else {
                        // Create a new list of states
                        List<Map<Integer, ItemStack>> newList = new ArrayList<>();
                        newList.add(slotMap);
                        ConfigHandler.oldContainerWithSlots.put(loggingContainerId, newList);
                        Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: Created new container state list");
                    }
                    
                    // Log for our database tracking system
                    ContainerManager.registerInitialState(player, l, type, oldInventory, null);
                    
                    // Double-check we stored it correctly
                    List<Map<Integer, ItemStack>> checkMaps = ConfigHandler.oldContainerWithSlots.get(loggingContainerId);
                    if (checkMaps != null) {
                        Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: Successfully stored " + 
                            checkMaps.size() + " container states with " + checkMaps.get(0).size() + 
                            " items in the most recent state");
                    } else {
                        Bukkit.getLogger().severe("[CoreProtect Debug] ContainerBreakLogger: Failed to store container state!");
                    }
                } else {
                    Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: Container was empty, nothing to store");
                }
            }
            
            // Now continue with the legacy logging process (for backward compatibility)
            if (oldInventory != null) {
                ItemUtils.mergeItems(type, oldInventory);
                ContainerLogger.logTransaction(preparedStmt, batchCount, player, type, null, oldInventory, 0, l);
            } else {
                Bukkit.getLogger().info("[CoreProtect Debug] ContainerBreakLogger: Skipping legacy logging - no inventory");
            }

            // If there was a pending chest transaction, it would have already been processed.
            if (ConfigHandler.forceContainer.get(loggingContainerId) != null) {
                ConfigHandler.forceContainer.remove(loggingContainerId);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("[CoreProtect] Error in ContainerBreakLogger: " + e.getMessage());
        }
    }

}
