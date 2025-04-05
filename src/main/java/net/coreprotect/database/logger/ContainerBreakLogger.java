package net.coreprotect.database.logger;

import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.Map;

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
            
            // First, save the raw container state with exact slot positions
            if (Config.getGlobal().PRESERVE_CONTAINER_SLOTS) {
                // Use the new container system to track the break
                ContainerState breakState = ContainerState.fromItemStackArray(oldInventory, type, 0);
                
                // Store this in both the old and new tracking systems for compatibility
                Map<Integer, ItemStack> slotMap = breakState.getSlotContents();
                if (!slotMap.isEmpty()) {
                    // Store in the oldContainerWithSlots for the rollback process to find
                    ConfigHandler.oldContainerWithSlots.computeIfAbsent(loggingContainerId, k -> new java.util.ArrayList<>())
                        .add(slotMap);
                        
                    // Also log this to the database via the container manager
                    ContainerManager.registerInitialState(player, l, type, oldInventory, null);
                }
            }
            
            // Now continue with the legacy logging process
            ItemUtils.mergeItems(type, oldInventory);
            ContainerLogger.logTransaction(preparedStmt, batchCount, player, type, null, oldInventory, 0, l);

            // If there was a pending chest transaction, it would have already been processed.
            if (ConfigHandler.forceContainer.get(loggingContainerId) != null) {
                ConfigHandler.forceContainer.remove(loggingContainerId);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
