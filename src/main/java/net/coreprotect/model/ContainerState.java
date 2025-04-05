package net.coreprotect.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a complete container state with precise slot tracking
 */
public class ContainerState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Maps slot indices to item stacks
    private final Map<Integer, ItemStack> slotContents = new HashMap<>();
    
    // Container metadata
    private final Material containerType;
    private BlockFace facing;
    private final int action; // 0 = removed, 1 = added
    private final long timestamp;
    
    /**
     * Creates a new container state
     * 
     * @param containerType The material type of the container
     * @param action 0 for removal, 1 for addition
     */
    public ContainerState(Material containerType, int action) {
        this.containerType = containerType;
        this.action = action;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Sets the block face for item frames, etc.
     * 
     * @param face The block face
     * @return This container state instance for chaining
     */
    public ContainerState setFacing(BlockFace face) {
        this.facing = face;
        return this;
    }
    
    /**
     * Sets an item in a specific slot
     * 
     * @param slot The slot index
     * @param item The item stack (will be cloned)
     * @return This container state instance for chaining
     */
    public ContainerState setItem(int slot, ItemStack item) {
        // Always store the item if it exists, regardless of amount
        // This fixes issues with partial stacks not being restored
        if (item != null && item.getType() != Material.AIR) {
            slotContents.put(slot, item.clone());
        }
        return this;
    }
    
    /**
     * Loads items from an array into this container state
     * 
     * @param items The array of items
     * @return This container state instance for chaining
     */
    public ContainerState loadItems(ItemStack[] items) {
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && items[i].getType() != Material.AIR) {
                    slotContents.put(i, items[i].clone());
                }
            }
        }
        return this;
    }
    
    /**
     * Gets all items in this container state
     * 
     * @return A map of slot indices to item stacks
     */
    public Map<Integer, ItemStack> getSlotContents() {
        return new HashMap<>(slotContents);
    }
    
    /**
     * Gets the container type
     * 
     * @return The material type of this container
     */
    public Material getContainerType() {
        return containerType;
    }
    
    /**
     * Gets the action type
     * 
     * @return 0 for removal, 1 for addition
     */
    public int getAction() {
        return action;
    }
    
    /**
     * Gets the block face, if applicable
     * 
     * @return The block face, or null if not applicable
     */
    public BlockFace getFacing() {
        return facing;
    }
    
    /**
     * Gets the timestamp when this state was created
     * 
     * @return The timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Converts this container state to an array of item stacks for backward compatibility
     * 
     * @param size The size of the array to create
     * @return An array of item stacks with items in their correct slots
     */
    public ItemStack[] toItemStackArray(int size) {
        ItemStack[] result = new ItemStack[size];
        for (Map.Entry<Integer, ItemStack> entry : slotContents.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < size) {
                result[slot] = entry.getValue().clone();
            }
        }
        return result;
    }
    
    /**
     * Creates a diff between this container state and another
     * 
     * @param other The other container state
     * @return A new container state containing only the differences
     */
    public ContainerState diff(ContainerState other) {
        ContainerState result = new ContainerState(containerType, action);
        
        // Find items that were added or had their amounts increased
        for (Map.Entry<Integer, ItemStack> entry : slotContents.entrySet()) {
            int slot = entry.getKey();
            ItemStack currentItem = entry.getValue();
            ItemStack otherItem = other.slotContents.get(slot);
            
            if (otherItem == null) {
                // Item was added to this slot
                result.setItem(slot, currentItem);
            } 
            else if (!itemStacksEqual(currentItem, otherItem)) {
                // Items are different types
                result.setItem(slot, currentItem);
            } 
            else if (currentItem.getAmount() != otherItem.getAmount()) {
                // Amount changed (either increased or decreased)
                ItemStack diffItem = currentItem.clone();
                // Just store the actual amount rather than the diff
                // This ensures partial stacks are preserved correctly
                result.setItem(slot, diffItem);
            }
        }
        
        // Find items that were removed entirely
        for (Map.Entry<Integer, ItemStack> entry : other.slotContents.entrySet()) {
            int slot = entry.getKey();
            ItemStack otherItem = entry.getValue();
            
            if (!slotContents.containsKey(slot)) {
                // Item was completely removed from this slot
                result.setItem(slot, otherItem);
            }
        }
        
        return result;
    }
    
    /**
     * Checks if two item stacks are equal (ignoring amount)
     */
    private boolean itemStacksEqual(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        ItemStack copyA = a.clone();
        ItemStack copyB = b.clone();
        
        // Make amounts equal to compare everything else
        copyA.setAmount(1);
        copyB.setAmount(1);
        
        return copyA.equals(copyB);
    }
    
    /**
     * Creates a container state from an array of item stacks
     * 
     * @param items The item stacks
     * @param containerType The container type
     * @param action The action (0 for removal, 1 for addition)
     * @return A new container state
     */
    public static ContainerState fromItemStackArray(ItemStack[] items, Material containerType, int action) {
        return new ContainerState(containerType, action).loadItems(items);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerState that = (ContainerState) o;
        return action == that.action && 
               Objects.equals(containerType, that.containerType) &&
               Objects.equals(facing, that.facing) &&
               Objects.equals(slotContents, that.slotContents);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(containerType, facing, action, slotContents);
    }
} 