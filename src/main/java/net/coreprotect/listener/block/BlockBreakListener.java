package net.coreprotect.listener.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rail.Shape;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.utility.BlockUtils;

public final class BlockBreakListener extends Queue implements Listener {

    private static boolean isAttached(Block block, Block scanBlock, int scanMin) {
        BlockData blockData = scanBlock.getBlockData();
        if (blockData instanceof Directional && !(blockData instanceof Bisected) && scanMin != BlockUtil.BOTTOM && scanMin != BlockUtil.TOP) {
            Directional directional = (Directional) blockData;
            BlockFace blockFace = directional.getFacing();
            if (blockData instanceof Bed) {
                blockFace = ((Bed) blockData).getPart() == Bed.Part.FOOT ? blockFace.getOppositeFace() : blockFace;
            }
            return scanBlock.getRelative(blockFace.getOppositeFace()).getLocation().equals(block.getLocation());
        }
        else if (blockData instanceof MultipleFacing) {
            MultipleFacing multipleFacing = (MultipleFacing) blockData;
            for (BlockFace blockFace : multipleFacing.getFaces()) {
                boolean adjacent = scanBlock.getRelative(blockFace).getLocation().equals(block.getLocation());
                if (adjacent) {
                    return true;
                }
            }

            return false;
        }
        else if (blockData instanceof Lantern) {
            boolean scan = false;
            switch (scanMin) {
                case BlockUtil.TOP:
                    scan = !((Lantern) blockData).isHanging();
                    break;
                case BlockUtil.BOTTOM:
                    scan = ((Lantern) blockData).isHanging();
                    break;
                default:
                    break;
            }

            return scan;
        }
        else if (!BukkitAdapter.ADAPTER.isAttached(block, scanBlock, blockData, scanMin)) {
            return false;
        }

        return true;
    }

    protected static void processBlockBreak(Player player, String user, Block block, boolean logBreak, int skipScan) {
        List<Block> placementMap = new ArrayList<>();
        Material type = block.getType();
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        int physics = 0;

        Location[] locationMap = new Location[6];
        locationMap[0] = new Location(world, x + 1, y, z);
        locationMap[1] = new Location(world, x - 1, y, z);
        locationMap[2] = new Location(world, x, y, z + 1);
        locationMap[3] = new Location(world, x, y, z - 1);
        locationMap[4] = new Location(world, x, y + 1, z);
        locationMap[5] = new Location(world, x, y - 1, z);

        int scanMin = 1;
        int scanMax = 8;
        if (!Config.getConfig(world).NATURAL_BREAK) {
            scanMin = 7;
        }
        if (!logBreak) { // log base block breakage
            scanMax = 7;
        }
        while (scanMin < scanMax) {
            Block blockLog = block;
            boolean scanDown = false;
            boolean log = true;

            if (scanMin == skipScan) {
                scanMin++;
                continue;
            }

            if (scanMin < 7) {
                Location scanLocation = locationMap[scanMin - 1];
                Block scanBlock = world.getBlockAt(scanLocation);
                Material scanType = scanBlock.getType();
                if (scanMin == 5) {
                    if (scanType.hasGravity() || BukkitAdapter.ADAPTER.isSuspiciousBlock(scanType)) {
                        if (Config.getConfig(world).BLOCK_MOVEMENT) {
                            // log the top-most sand/gravel block as being removed
                            int scanY = y + 2;
                            boolean topFound = false;
                            while (!topFound) {
                                Block topBlock = world.getBlockAt(x, scanY, z);
                                Material topMaterial = topBlock.getType();
                                if (!topMaterial.hasGravity() && !BukkitAdapter.ADAPTER.isSuspiciousBlock(topMaterial)) {
                                    scanLocation = new Location(world, x, (scanY - 1), z);
                                    topFound = true;
                                }
                                scanY++;
                            }
                            placementMap.add(scanBlock);
                        }
                    }
                }
                if (!BlockGroup.TRACK_ANY.contains(scanType)) {
                    if (scanMin != 5 && scanMin != 6 && !scanDown) { // side block
                        if (!BlockGroup.TRACK_SIDE.contains(scanType)) {
                            log = false;

                            /*
                            if (physics == 0 && scanBlock.getBlockData() instanceof MultipleFacing) {
                                physics = 1;
                            }
                            */
                        }
                        else {
                            // determine if side block is attached
                            if (scanType.equals(Material.RAIL) || scanType.equals(Material.POWERED_RAIL) || scanType.equals(Material.DETECTOR_RAIL) || scanType.equals(Material.ACTIVATOR_RAIL)) {
                                BlockData blockData = scanBlock.getBlockData();
                                Rail rail = (Rail) blockData;
                                Shape shape = rail.getShape();

                                if (scanMin == 1 && shape != Shape.ASCENDING_WEST) {
                                    log = false;
                                }
                                else if (scanMin == 2 && shape != Shape.ASCENDING_EAST) {
                                    log = false;
                                }
                                else if (scanMin == 3 && shape != Shape.ASCENDING_NORTH) {
                                    log = false;
                                }
                                else if (scanMin == 4 && shape != Shape.ASCENDING_SOUTH) {
                                    log = false;
                                }
                            }
                            else if (scanType.name().endsWith("_BED") && !type.name().endsWith("_BED")) {
                                log = false;
                            }
                            else if (!isAttached(block, scanBlock, scanMin)) {
                                log = false;
                            }
                        }
                    }
                    else { // top/bottom block
                        if (BlockUtil.verticalBreakScan(player, user, block, scanBlock, scanType, scanMin)) {
                            log = false;
                        }
                        else if (scanMin == 5 && (!BlockGroup.TRACK_TOP.contains(scanType) && !BlockGroup.TRACK_TOP_BOTTOM.contains(scanType))) {
                            // top
                            log = false;
                        }
                        else if (scanMin == 6 && (!BlockGroup.TRACK_BOTTOM.contains(scanType) && !BlockGroup.TRACK_TOP_BOTTOM.contains(scanType))) {
                            // bottom
                            log = false;
                        }
                        else if (scanMin == 4 && !BlockGroup.TRACK_TOP.contains(scanType)) {
                            // checking block below for door
                            log = false;
                        }
                        else if (!isAttached(block, scanBlock, scanMin)) {
                            log = false;
                        }
                    }
                    if (!log) {
                        if (type.equals(Material.PISTON_HEAD)) {// broke a piston extension
                            if (scanType.equals(Material.STICKY_PISTON) || scanType.equals(Material.PISTON)) { // adjacent piston
                                log = true;
                            }
                        }
                        else if (scanMin == 5) {
                            if (scanType.hasGravity() || BukkitAdapter.ADAPTER.isSuspiciousBlock(scanType)) {
                                log = true;
                            }
                        }
                    }
                }
                else {
                    // determine if side block is attached
                    if (scanType.equals(Material.PISTON_HEAD)) {
                        if (!type.equals(Material.STICKY_PISTON) && !type.equals(Material.PISTON)) {
                            log = false;
                        }
                    }
                    else if (scanType.equals(Material.BELL)) {
                        boolean scanBell = false;
                        BlockData blockData = scanBlock.getBlockData();
                        Bell bell = (Bell) blockData;
                        switch (bell.getAttachment()) {
                            case SINGLE_WALL:
                                scanBell = (scanMin < 5 && scanBlock.getRelative(bell.getFacing()).getLocation().equals(block.getLocation()));
                                break;
                            case FLOOR:
                                scanBell = (scanMin == 5);
                                break;
                            case CEILING:
                                scanBell = (scanMin == 6);
                                break;
                            default:
                                break;
                        }
                        if (!scanBell) {
                            log = false;
                        }
                    }
                    else if (BlockGroup.BUTTONS.contains(scanType) || scanType == Material.LEVER) {
                        boolean scanButton = BukkitAdapter.ADAPTER.isAttached(block, scanBlock, scanBlock.getBlockData(), scanMin);
                        if (!scanButton) {
                            log = false;
                        }
                    }
                    else if (!isAttached(block, scanBlock, scanMin)) {
                        log = false;
                    }
                }
                if (log) {
                    blockLog = world.getBlockAt(scanLocation);
                }
            }

            int blockNumber = scanMin;
            Material blockType = blockLog.getType();
            BlockState blockState = blockLog.getState();

            if (log && (blockType.name().toUpperCase(Locale.ROOT).endsWith("_BANNER") || blockType.equals(Material.SKELETON_SKULL) || blockType.equals(Material.SKELETON_WALL_SKULL) || blockType.equals(Material.WITHER_SKELETON_SKULL) || blockType.equals(Material.WITHER_SKELETON_WALL_SKULL) || blockType.equals(Material.ZOMBIE_HEAD) || blockType.equals(Material.ZOMBIE_WALL_HEAD) || blockType.equals(Material.PLAYER_HEAD) || blockType.equals(Material.PLAYER_WALL_HEAD) || blockType.equals(Material.CREEPER_HEAD) || blockType.equals(Material.CREEPER_WALL_HEAD) || blockType.equals(Material.DRAGON_HEAD) || blockType.equals(Material.DRAGON_WALL_HEAD))) {
                try {
                    if (blockState instanceof Banner || blockState instanceof Skull) {
                        Queue.queueAdvancedBreak(user, blockState, blockType, blockState.getBlockData().getAsString(), 0, type, blockNumber);
                    }
                    log = false;
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (log && BukkitAdapter.ADAPTER.isSign(blockType)) {
                if (Config.getConfig(world).SIGN_TEXT) {
                    try {
                        Location location = blockState.getLocation();
                        Sign sign = (Sign) blockLog.getState();
                        String line1 = PaperAdapter.ADAPTER.getLine(sign, 0);
                        String line2 = PaperAdapter.ADAPTER.getLine(sign, 1);
                        String line3 = PaperAdapter.ADAPTER.getLine(sign, 2);
                        String line4 = PaperAdapter.ADAPTER.getLine(sign, 3);
                        String line5 = PaperAdapter.ADAPTER.getLine(sign, 4);
                        String line6 = PaperAdapter.ADAPTER.getLine(sign, 5);
                        String line7 = PaperAdapter.ADAPTER.getLine(sign, 6);
                        String line8 = PaperAdapter.ADAPTER.getLine(sign, 7);

                        boolean isFront = true;
                        int color = BukkitAdapter.ADAPTER.getColor(sign, isFront);
                        int colorSecondary = BukkitAdapter.ADAPTER.getColor(sign, !isFront);
                        boolean frontGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, isFront);
                        boolean backGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, !isFront);
                        boolean isWaxed = BukkitAdapter.ADAPTER.isWaxed(sign);

                        Queue.queueSignText(user, location, 0, color, colorSecondary, frontGlowing, backGlowing, isWaxed, isFront, line1, line2, line3, line4, line5, line6, line7, line8, 5);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (log) {
                Database.containerBreakCheck(user, blockType, blockLog, null, blockLog.getLocation());
                Queue.queueBlockBreak(user, blockState, blockType, blockState.getBlockData().getAsString(), type, physics, blockNumber);

                if (player != null && BlockUtils.iceBreakCheck(blockState, user, blockType)) {
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (!(player.getGameMode().equals(GameMode.CREATIVE)) && !(handItem != null && handItem.containsEnchantment(Enchantment.SILK_TOUCH))) {
                        Queue.queueBlockPlaceValidate(user, blockState, blockLog, null, Material.WATER, -1, 0, null, 0);
                    }
                }

                BlockData blockDataB1 = blockState.getBlockData();
                if (blockDataB1 instanceof Waterlogged) {
                    Waterlogged waterlogged = (Waterlogged) blockDataB1;
                    if (waterlogged.isWaterlogged()) {
                        Queue.queueBlockPlace(user, blockState, blockLog.getType(), null, Material.WATER, -1, 0, null);
                    }
                }
            }

            scanMin++;
        }

        for (Block placementBlock : placementMap) {
            Material placementType = placementBlock.getType();
            if (placementType.hasGravity()) {
                queueBlockPlace(user, block.getState(), placementType, null, null, -1, 0, placementBlock.getBlockData().getAsString());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        try {
            final Block block = event.getBlock();
            final Player player = event.getPlayer();

            if (!Config.getConfig(block.getWorld()).BLOCK_BREAK) {
                return;
            }

            // Add our new container handling right before block break is processed
            if (BlockGroup.CONTAINERS.contains(block.getType()) && Config.getGlobal().PRESERVE_CONTAINER_SLOTS) {
                handleContainerBreak(block, player.getName());
            }

            // Rest of the original method continues unchanged
            String user = player.getName();
            processBlockBreak(player, user, event.getBlock(), Config.getConfig(block.getWorld()).BLOCK_BREAK, BlockUtil.NONE);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleContainerBreak(Block block, String user) {
        if (BlockGroup.CONTAINERS.contains(block.getType())) {
            try {
                // Debug log the container break
                Bukkit.getLogger().info("[CoreProtect Debug] Container " + block.getType() + " broken at " + 
                    block.getX() + "," + block.getY() + "," + block.getZ() + " by " + user);
                    
                BlockState blockState = block.getState();
                if (blockState instanceof InventoryHolder) {
                    InventoryHolder holder = (InventoryHolder) blockState;
                    Inventory inventory = holder.getInventory();
                    
                    if (inventory != null) {
                        // Generate a unique identifier for this container
                        String loggingContainerId = user.toLowerCase(Locale.ROOT) + "." + 
                            block.getX() + "." + block.getY() + "." + block.getZ();
                        
                        // Get all items in the inventory
                        ItemStack[] contents = inventory.getContents();
                        
                        // Count non-empty slots for debugging
                        int nonEmptySlots = 0;
                        for (ItemStack item : contents) {
                            if (item != null && item.getType() != Material.AIR) {
                                nonEmptySlots++;
                            }
                        }
                        
                        Bukkit.getLogger().info("[CoreProtect Debug] Container break: Found " + nonEmptySlots + 
                            " non-empty slots out of " + contents.length + " total slots");
                        
                        // Create a complete map of slots to items
                        Map<Integer, ItemStack> slotMap = new HashMap<>();
                        for (int i = 0; i < contents.length; i++) {
                            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                                // Clone the item to ensure we have an independent copy
                                ItemStack itemClone = contents[i].clone();
                                slotMap.put(i, itemClone);
                                
                                // Debug logging
                                Bukkit.getLogger().info("[CoreProtect Debug] Saved container item in slot " + i + ": " + 
                                    itemClone.getType() + " x" + itemClone.getAmount());
                            }
                        }
                        
                        // Only proceed if we found items
                        if (!slotMap.isEmpty()) {
                            // Store the slot map for future rollbacks
                            List<Map<Integer, ItemStack>> existingMaps = ConfigHandler.oldContainerWithSlots.get(loggingContainerId);
                            
                            if (existingMaps != null) {
                                // If we already have maps for this container, add this one to the front (most recent)
                                existingMaps.add(0, slotMap);
                                Bukkit.getLogger().info("[CoreProtect Debug] Added to existing container state list, now have " + 
                                    existingMaps.size() + " states");
                            } else {
                                // Otherwise create a new list
                                List<Map<Integer, ItemStack>> newList = new ArrayList<>();
                                newList.add(slotMap);
                                ConfigHandler.oldContainerWithSlots.put(loggingContainerId, newList);
                                Bukkit.getLogger().info("[CoreProtect Debug] Created new container state list");
                            }
                            
                            // Double-check we stored it correctly
                            List<Map<Integer, ItemStack>> checkMaps = ConfigHandler.oldContainerWithSlots.get(loggingContainerId);
                            if (checkMaps != null) {
                                Bukkit.getLogger().info("[CoreProtect Debug] Successfully stored " + checkMaps.size() + 
                                    " container states with " + checkMaps.get(0).size() + " items in the most recent state");
                            } else {
                                Bukkit.getLogger().severe("[CoreProtect Debug] Failed to store container state!");
                            }
                        } else {
                            Bukkit.getLogger().info("[CoreProtect Debug] Container was empty, no state to store");
                        }
                    } else {
                        Bukkit.getLogger().info("[CoreProtect Debug] Could not get inventory for container");
                    }
                } else {
                    Bukkit.getLogger().info("[CoreProtect Debug] Block state is not an inventory holder: " + blockState.getClass().getName());
                }
            } catch (Exception e) {
                Bukkit.getLogger().severe("[CoreProtect] Error processing container break: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

}
