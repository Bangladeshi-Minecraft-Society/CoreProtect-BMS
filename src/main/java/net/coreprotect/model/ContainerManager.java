package net.coreprotect.model;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.logger.ContainerLogger;
import net.coreprotect.database.statement.ContainerStatement;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.serialize.ItemMetaHandler;

/**
 * Manages container states and handles container rollback operations
 */
public class ContainerManager {
    
    // Maps container IDs to their states (for tracking purposes)
    private static final Map<String, List<ContainerState>> containerStates = new ConcurrentHashMap<>();
    
    // Maps user+location to container ID
    private static final Map<String, String> containerIdMap = new ConcurrentHashMap<>();
    
    /**
     * Generates a container ID for the given user and location
     * 
     * @param user The user who interacted with the container
     * @param location The container location
     * @return A unique container ID
     */
    public static String getContainerId(String user, Location location) {
        String key = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        
        String containerId = containerIdMap.get(key);
        if (containerId == null) {
            // Generate a unique ID for this container
            containerId = UUID.randomUUID().toString();
            containerIdMap.put(key, containerId);
        }
        
        return containerId;
    }
    
    /**
     * Registers an initial container state
     * 
     * @param user The user who interacted with the container
     * @param location The container location
     * @param containerType The container material type
     * @param contents The container contents
     * @param facing The block face (for item frames, etc.)
     * @return The container state that was registered
     */
    public static ContainerState registerInitialState(String user, Location location, Material containerType, ItemStack[] contents, BlockFace facing) {
        String containerId = getContainerId(user, location);
        
        ContainerState initialState = new ContainerState(containerType, 0);
        if (facing != null) {
            initialState.setFacing(facing);
        }
        initialState.loadItems(contents);
        
        List<ContainerState> states = containerStates.computeIfAbsent(containerId, k -> new ArrayList<>());
        states.add(initialState);
        
        return initialState;
    }
    
    /**
     * Registers a container change and logs the differences
     * 
     * @param user The user who changed the container
     * @param location The container location
     * @param containerType The container material type
     * @param newContents The new container contents
     * @param facing The block face (for item frames, etc.)
     * @param batchCount The batch count for database operations
     * @param preparedStmt The prepared statement for database operations
     * @return The difference in container state that was logged
     */
    public static ContainerState registerChange(String user, Location location, Material containerType, 
                                               ItemStack[] newContents, BlockFace facing, 
                                               int batchCount, PreparedStatement preparedStmt) {
        String containerId = getContainerId(user, location);
        
        // Get the previous state
        List<ContainerState> states = containerStates.get(containerId);
        if (states == null || states.isEmpty()) {
            // No previous state, register an initial state with all items removed
            ContainerState initialState = registerInitialState(user, location, containerType, new ItemStack[newContents.length], facing);
            return registerChangeAfterInitial(user, location, containerType, newContents, facing, initialState, batchCount, preparedStmt);
        }
        
        ContainerState previousState = states.get(states.size() - 1);
        return registerChangeAfterInitial(user, location, containerType, newContents, facing, previousState, batchCount, preparedStmt);
    }
    
    /**
     * Registers a container change after an initial state is known
     * 
     * @param user The user who changed the container
     * @param location The container location
     * @param containerType The container material type
     * @param newContents The new container contents
     * @param facing The block face (for item frames, etc.)
     * @param previousState The previous container state
     * @param batchCount The batch count for database operations
     * @param preparedStmt The prepared statement for database operations
     * @return The difference in container state that was logged
     */
    public static ContainerState registerChangeAfterInitial(String user, Location location, Material containerType, 
                                                          ItemStack[] newContents, BlockFace facing,
                                                          ContainerState previousState, 
                                                          int batchCount, PreparedStatement preparedStmt) {
        String containerId = getContainerId(user, location);
        
        // Create a new state for the current contents
        ContainerState currentState = new ContainerState(containerType, 1);
        if (facing != null) {
            currentState.setFacing(facing);
        }
        currentState.loadItems(newContents);
        
        // Calculate the difference (what was added/removed)
        ContainerState addedItems = currentState.diff(previousState);
        ContainerState removedItems = previousState.diff(currentState);
        
        // Store the current state for future changes
        containerStates.computeIfAbsent(containerId, k -> new ArrayList<>()).add(currentState);
        
        // Log the removed and added items to the database
        if (removedItems.getSlotContents().size() > 0) {
            logContainerState(user, location, containerType, 0, removedItems, batchCount, preparedStmt);
        }
        
        if (addedItems.getSlotContents().size() > 0) {
            logContainerState(user, location, containerType, 1, addedItems, batchCount, preparedStmt);
        }
        
        return currentState;
    }
    
    /**
     * Logs a container state to the database
     * 
     * @param user The user who interacted with the container
     * @param location The container location
     * @param containerType The container material type
     * @param action 0 for removal, 1 for addition
     * @param state The container state to log
     * @param batchCount The batch count for database operations
     * @param preparedStmt The prepared statement for database operations
     */
    private static void logContainerState(String user, Location location, Material containerType, 
                                         int action, ContainerState state, 
                                         int batchCount, PreparedStatement preparedStmt) {
        try {
            if (ConfigHandler.blacklist.get(user.toLowerCase(Locale.ROOT)) != null) {
                return;
            }
            
            for (Map.Entry<Integer, ItemStack> entry : state.getSlotContents().entrySet()) {
                int slot = entry.getKey();
                ItemStack item = entry.getValue();
                
                // Process all items regardless of amount, as long as they're not air
                if (item != null && item.getType() != Material.AIR) {
                    // Prepare metadata including the slot information
                    byte[] metadata = null;
                    
                    // Use ItemMetaHandler to create the metadata directly with slot information
                    List<List<Map<String, Object>>> itemMeta = ItemMetaHandler.serialize(item, 
                        containerType, // Pass the container type
                        state.getFacing() != null ? state.getFacing().name() : null, 
                        slot);
                    
                    metadata = ItemUtils.convertByteData(itemMeta);
                    
                    // Fire pre-log event
                    CoreProtectPreLogEvent event = new CoreProtectPreLogEvent(user);
                    if (Config.getGlobal().API_ENABLED && !Bukkit.isPrimaryThread()) {
                        Bukkit.getPluginManager().callEvent(event);
                    }
                    
                    if (event.isCancelled()) {
                        return;
                    }
                    
                    // Log to database
                    int userId = UserStatement.getId(preparedStmt, event.getUser(), true);
                    int wid = WorldUtils.getWorldId(location.getWorld().getName());
                    int time = (int) (System.currentTimeMillis() / 1000L);
                    int x = location.getBlockX();
                    int y = location.getBlockY();
                    int z = location.getBlockZ();
                    int typeId = MaterialUtils.getBlockId(item.getType().name(), true);
                    int data = 0;
                    int amount = item.getAmount();
                    
                    // Ensure amount is at least 1 to prevent items from disappearing
                    if (amount <= 0) {
                        amount = 1;
                    }
                    
                    ContainerStatement.insert(preparedStmt, batchCount, time, userId, wid, x, y, z, typeId, data, amount, metadata, action, 0);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Applies a container state to a container inventory
     * 
     * @param container The container inventory or entity
     * @param state The container state to apply
     * @return True if the state was applied successfully
     */
    public static boolean applyContainerState(Object container, ContainerState state) {
        try {
            if (container instanceof Inventory) {
                Inventory inventory = (Inventory) container;
                
                // First clear any slots we're about to modify to avoid item merging issues
                for (Map.Entry<Integer, ItemStack> entry : state.getSlotContents().entrySet()) {
                    int slot = entry.getKey();
                    if (slot >= 0 && slot < inventory.getSize()) {
                        // Only clear the slot if we have something to put there
                        inventory.setItem(slot, null);
                    }
                }
                
                // Now apply each item to its exact slot
                for (Map.Entry<Integer, ItemStack> entry : state.getSlotContents().entrySet()) {
                    int slot = entry.getKey();
                    ItemStack item = entry.getValue().clone();
                    
                    // Validate the item before setting
                    if (item != null && item.getType() != Material.AIR) {
                        // Ensure the item has a valid amount
                        if (item.getAmount() <= 0) {
                            item.setAmount(1);
                        }
                        
                        // Set the item in the exact slot
                        if (slot >= 0 && slot < inventory.getSize()) {
                            inventory.setItem(slot, item);
                        }
                    }
                }
                
                return true;
            }
            else if (container instanceof ItemFrame && state.getFacing() != null) {
                ItemFrame frame = (ItemFrame) container;
                
                // Check if the facing matches
                if (frame.getFacing() == state.getFacing()) {
                    // Get the first item from the state
                    Map<Integer, ItemStack> items = state.getSlotContents();
                    if (!items.isEmpty()) {
                        ItemStack item = items.values().iterator().next().clone();
                        if (item.getAmount() <= 0) {
                            item.setAmount(1);
                        }
                        frame.setItem(item);
                        return true;
                    }
                }
                
                // Try to find a matching item frame with the right facing
                Location loc = frame.getLocation();
                World world = loc.getWorld();
                
                for (Entity entity : world.getNearbyEntities(loc, 1, 1, 1)) {
                    if (entity instanceof ItemFrame) {
                        ItemFrame otherFrame = (ItemFrame) entity;
                        if (otherFrame.getFacing() == state.getFacing()) {
                            Map<Integer, ItemStack> items = state.getSlotContents();
                            if (!items.isEmpty()) {
                                ItemStack item = items.values().iterator().next().clone();
                                if (item.getAmount() <= 0) {
                                    item.setAmount(1);
                                }
                                otherFrame.setItem(item);
                                return true;
                            }
                        }
                    }
                }
            }
            // Handle other container types as needed
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Creates a container state from a block state and container object
     * 
     * @param state The block state
     * @param container The container object
     * @param action 0 for removal, 1 for addition
     * @return A container state representing the container
     */
    public static ContainerState createFromContainer(BlockState state, Object container, int action) {
        Material type = state.getType();
        BlockFace facing = null;
        ItemStack[] contents = null;
        
        if (container instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) container;
            facing = frame.getFacing();
            contents = new ItemStack[] { frame.getItem() };
        }
        else if (container instanceof Inventory) {
            Inventory inventory = (Inventory) container;
            contents = inventory.getContents();
        }
        
        if (contents != null) {
            ContainerState containerState = new ContainerState(type, action);
            if (facing != null) {
                containerState.setFacing(facing);
            }
            containerState.loadItems(contents);
            return containerState;
        }
        
        return null;
    }
} 