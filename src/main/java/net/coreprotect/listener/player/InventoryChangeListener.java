package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.Validate;
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI;
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest;
import net.coreprotect.model.ContainerState;
import net.coreprotect.model.ContainerManager;

public final class InventoryChangeListener extends Queue implements Listener {

    protected static AtomicLong tasksStarted = new AtomicLong();
    protected static AtomicLong tasksCompleted = new AtomicLong();
    private static ConcurrentHashMap<String, Boolean> inventoryProcessing = new ConcurrentHashMap<>();
    private static final Object taskCompletionLock = new Object();
    private static final long TASK_WAIT_MAX_MS = 50; // Maximum wait time in milliseconds

    protected static void checkTasks(long taskStarted) {
        try {
            // Skip checking if this is the first task or we're already caught up
            if (taskStarted <= 1 || tasksCompleted.get() >= (taskStarted - 1L)) {
                tasksCompleted.set(taskStarted);
                return;
            }

            // Try to update without waiting if possible
            if (tasksCompleted.compareAndSet(taskStarted - 1L, taskStarted)) {
                return;
            }

            // Use proper synchronization instead of busy waiting
            synchronized (taskCompletionLock) {
                if (tasksCompleted.get() < (taskStarted - 1L)) {
                    taskCompletionLock.wait(TASK_WAIT_MAX_MS);
                }
                tasksCompleted.set(taskStarted);
                taskCompletionLock.notifyAll(); // Notify other waiting threads
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean inventoryTransaction(String user, Location location, ItemStack[] inventoryData) {
        if (user != null && location != null) {
            if (user.length() > 0) {
                BlockState blockState = location.getBlock().getState();
                Material type = blockState.getType();

                if (BlockGroup.CONTAINERS.contains(type) && blockState instanceof InventoryHolder) {
                    InventoryHolder inventoryHolder = (InventoryHolder) blockState;
                    return onInventoryInteract(user, inventoryHolder.getInventory(), inventoryData, null, location, false);
                }
            }
        }
        return false;
    }

    static boolean onInventoryInteract(String user, final Inventory inventory, ItemStack[] inventoryData, Material containerType, Location location, boolean aSync) {
        try {
            if (Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS && BlockGroup.CONTAINERS.contains(containerType)) {
                String transactingChestId = location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
                String loggingChestId = user.toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
                int chestId = getChestId(loggingChestId);

                if (chestId > 0) {
                    // Add to existing transaction records
                    if (ConfigHandler.oldContainer.get(loggingChestId) != null) { // player has pending consumer item
                        int sizeOld = ConfigHandler.oldContainer.get(loggingChestId).size();
                        ConfigHandler.forceContainer.computeIfAbsent(loggingChestId, k -> new ArrayList<>());
                        List<ItemStack[]> list = ConfigHandler.forceContainer.get(loggingChestId);

                        if (list != null && list.size() < sizeOld) {
                            ItemStack[] containerState = ItemUtils.getContainerState(inventoryData);

                            // If items have been removed by a hopper, merge into containerState
                            List<Object> transactingChest = ConfigHandler.transactingChest.get(transactingChestId);
                            if (transactingChest != null && transactingChest.size() > 0) {
                                ItemStack[] newMerge = new ItemStack[containerState.length + transactingChest.size()];
                                int count = 0;
                                for (int i = 0; i < containerState.length; i++) {
                                    newMerge[i] = containerState[i];
                                    count++;
                                }
                                for (Object item : transactingChest) {
                                    ItemStack addItem = null;
                                    ItemStack removeItem = null;
                                    if (item instanceof ItemStack) {
                                        addItem = (ItemStack) item;
                                    }
                                    else if (item != null) {
                                        addItem = ((ItemStack[]) item)[0];
                                        removeItem = ((ItemStack[]) item)[1];
                                    }

                                    // item was removed by hopper, add back to state
                                    if (addItem != null) {
                                        newMerge[count] = addItem;
                                        count++;
                                    }

                                    // item was added by hopper, remove from state
                                    if (removeItem != null) {
                                        for (ItemStack check : newMerge) {
                                            if (check != null && check.isSimilar(removeItem)) {
                                                check.setAmount(check.getAmount() - 1);
                                                break;
                                            }
                                        }
                                    }
                                }
                                containerState = newMerge;
                            }

                            list.add(containerState);
                            ConfigHandler.forceContainer.put(loggingChestId, list);
                            
                            // Also track with the new system
                            if (Config.getGlobal().PRESERVE_CONTAINER_SLOTS) {
                                // Register the container state change with new system
                                ContainerManager.registerInitialState(user, location, containerType, containerState, null);
                            }
                        }
                    }

                    int chestId2 = getChestId(loggingChestId);
                    if (chestId2 > 0) {
                        chestId = chestId2;
                    }
                    Queue.queueContainerTransaction(user, location, containerType, inventory, chestId);
                    return true;
                }
                else {
                    List<ItemStack[]> list = new ArrayList<>();
                    list.add(ItemUtils.getContainerState(inventoryData));
                    ConfigHandler.oldContainer.put(loggingChestId, list);
                    
                    // Also track with the new system
                    if (Config.getGlobal().PRESERVE_CONTAINER_SLOTS) {
                        // Register the initial container state with new system
                        ContainerManager.registerInitialState(user, location, containerType, inventoryData, null);
                    }

                    ConfigHandler.transactingChest.computeIfAbsent(transactingChestId, k -> Collections.synchronizedList(new ArrayList<>()));
                    Queue.queueContainerTransaction(user, location, containerType, inventory, chestId);
                    return true;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    static void onInventoryInteractAsync(Player player, Inventory inventory, boolean enderChest) {
        if (inventory == null) {
            return;
        }

        Location location = null;
        try {
            location = inventory.getLocation();
        }
        catch (Exception e) {
            return;
        }

        if (location == null && !CoreProtect.getInstance().isAdvancedChestsEnabled()) {
            return;
        }
        if (CoreProtect.getInstance().isAdvancedChestsEnabled()) {
            AdvancedChest<?, ?> chest = AdvancedChestsAPI.getInventoryManager().getAdvancedChest(inventory);
            if (chest != null) {
                location = chest.getLocation();
            }
        }

        if (location == null) {
            return;
        }

        if (!Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        Location inventoryLocation = location;
        ItemStack[] containerState = ItemUtils.getContainerState(inventory.getContents());

        String loggingChestId = player.getName() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        Boolean lastTransaction = inventoryProcessing.get(loggingChestId);
        if (lastTransaction != null) {
            return;
        }
        inventoryProcessing.put(loggingChestId, true);

        final long taskStarted = InventoryChangeListener.tasksStarted.incrementAndGet();
        Scheduler.runTaskAsynchronously(CoreProtect.getInstance(), () -> {
            try {
                Material containerType = (enderChest != true ? null : Material.ENDER_CHEST);
                InventoryChangeListener.checkTasks(taskStarted);
                inventoryProcessing.remove(loggingChestId);
                onInventoryInteract(player.getName(), inventory, containerState, containerType, inventoryLocation, true);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Checks for anvil operations to properly track enchanted item results
     * 
     * @param event
     *            The inventory click event
     * @return true if this was an anvil result operation that was handled, false otherwise
     */
    private boolean checkAnvilOperation(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return false;
        }

        // Only process result slot clicks in anvils (slot 2)
        if (event.getRawSlot() != 2) {
            return false;
        }

        // Ensure we have a valid player and item
        Player player = (Player) event.getWhoClicked();
        ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || resultItem.getType() == Material.AIR) {
            return false;
        }

        // Get the input items (slots 0 and 1 in the anvil)
        ItemStack firstItem = event.getInventory().getItem(0);
        ItemStack secondItem = event.getInventory().getItem(1);

        if (firstItem == null || secondItem == null) {
            return false;
        }

        // Process the enchantment operation
        Location location = player.getLocation();
        String loggingItemId = player.getName().toLowerCase(Locale.ROOT) + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
        int itemId = getItemId(loggingItemId);

        // Log the input items as removed
        List<ItemStack> removedItems = new ArrayList<>();
        removedItems.add(firstItem.clone());
        removedItems.add(secondItem.clone());
        ConfigHandler.itemsDestroy.put(loggingItemId, removedItems);

        // Log the output item as created
        List<ItemStack> createdItems = new ArrayList<>();
        createdItems.add(resultItem.clone());
        ConfigHandler.itemsCreate.put(loggingItemId, createdItems);

        int time = (int) (System.currentTimeMillis() / 1000L) + 1;
        Queue.queueItemTransaction(player.getName(), location.clone(), time, 0, itemId);

        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onInventoryClick(InventoryClickEvent event) {
        InventoryAction inventoryAction = event.getAction();
        if (inventoryAction == InventoryAction.NOTHING) {
            return;
        }

        // Check if this is an anvil operation first
        if (checkAnvilOperation(event)) {
            return;
        }

        boolean enderChest = false;
        boolean advancedChest;
        if (inventoryAction != InventoryAction.MOVE_TO_OTHER_INVENTORY && inventoryAction != InventoryAction.COLLECT_TO_CURSOR && inventoryAction != InventoryAction.UNKNOWN) {
            // Perform this check to prevent triggering onInventoryInteractAsync when a user is just clicking items in their own inventory
            Inventory inventory = null;
            try {
                try {
                    inventory = event.getView().getInventory(event.getRawSlot());
                }
                catch (IncompatibleClassChangeError e) {
                    inventory = event.getClickedInventory();
                }
            }
            catch (Exception e) {
                return;
            }
            if (inventory == null) {
                return;
            }

            InventoryHolder inventoryHolder = inventory.getHolder();
            enderChest = inventory.equals(event.getWhoClicked().getEnderChest());
            advancedChest = isAdvancedChest(inventory);
            if ((!(inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) && !enderChest && !advancedChest) {
                return;
            }
            if (advancedChest && event.getSlot() > inventory.getSize() - 10) {
                return;
            }
        }
        else {
            // Perform standard inventory holder check on primary inventory
            Inventory inventory = event.getInventory();
            if (inventory == null) {
                return;
            }

            InventoryHolder inventoryHolder = inventory.getHolder();
            enderChest = inventory.equals(event.getWhoClicked().getEnderChest());
            advancedChest = isAdvancedChest(inventory);
            if ((!(inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) && !enderChest && !advancedChest) {
                return;
            }
            if (advancedChest && event.getSlot() > inventory.getSize() - 10) {
                return;
            }
        }

        Player player = (Player) event.getWhoClicked();
        onInventoryInteractAsync(player, event.getInventory(), enderChest);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onInventoryDragEvent(InventoryDragEvent event) {
        boolean movedItem = false;
        boolean enderChest = false;

        Inventory inventory = event.getInventory();
        InventoryHolder inventoryHolder = inventory.getHolder();
        if (inventory == null || inventoryHolder != null && inventoryHolder.equals(event.getWhoClicked())) {
            return;
        }

        enderChest = inventory.equals(event.getWhoClicked().getEnderChest());
        if (((inventoryHolder instanceof BlockInventoryHolder || inventoryHolder instanceof DoubleChest)) || enderChest || isAdvancedChest(inventory)) {
            movedItem = true;
        }

        if (!movedItem) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        onInventoryInteractAsync(player, event.getInventory(), enderChest);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    protected void onInventoryMoveItemEvent(InventoryMoveItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Inventory sourceInventory = event.getSource();
        if (sourceInventory == null) {
            return;
        }

        Location location = sourceInventory.getLocation();
        if (location == null) {
            return;
        }

        boolean hopperTransactions = Config.getConfig(location.getWorld()).HOPPER_TRANSACTIONS;
        if (!hopperTransactions && !Config.getConfig(location.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        InventoryHolder sourceHolder = PaperAdapter.ADAPTER.getHolder(sourceInventory, false);
        if (sourceHolder == null) {
            return;
        }

        InventoryHolder destinationHolder = PaperAdapter.ADAPTER.getHolder(event.getDestination(), false);
        if (destinationHolder == null) {
            return;
        }

        if (hopperTransactions) {
            if (Validate.isHopper(destinationHolder) && (Validate.isContainer(sourceHolder) && !Validate.isHopper(sourceHolder))) {
                HopperPullListener.processHopperPull(location, "#hopper", sourceHolder, destinationHolder, event.getItem());
            }
            else if (Validate.isHopper(sourceHolder) && (Validate.isContainer(destinationHolder) && !Validate.isHopper(destinationHolder))) {
                HopperPushListener.processHopperPush(location, "#hopper", sourceHolder, destinationHolder, event.getItem());
            }
            else if (Validate.isDropper(sourceHolder) && (Validate.isContainer(destinationHolder))) {
                HopperPullListener.processHopperPull(location, "#dropper", sourceHolder, destinationHolder, event.getItem());
                if (!Validate.isHopper(destinationHolder)) {
                    HopperPushListener.processHopperPush(location, "#dropper", sourceHolder, destinationHolder, event.getItem());
                }
            }

            return;
        }

        if (destinationHolder instanceof Player || (!(sourceHolder instanceof BlockInventoryHolder) && !(sourceHolder instanceof DoubleChest))) {
            return;
        }

        List<Object> list = ConfigHandler.transactingChest.get(location.getWorld().getUID().toString() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ());
        if (list == null) {
            return;
        }

        HopperPullListener.processHopperPull(location, "#hopper", sourceHolder, destinationHolder, event.getItem());
    }

    private boolean isAdvancedChest(Inventory inventory) {
        return CoreProtect.getInstance().isAdvancedChestsEnabled() && AdvancedChestsAPI.getInventoryManager().getAdvancedChest(inventory) != null;
    }

}
