package net.coreprotect.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages exempt zones where CoreProtect logging is disabled
 */
public class ExemptZoneManager {
    
    private static final Map<String, ExemptZone> zones = new HashMap<>();
    private static final Map<UUID, Location> pos1Selections = new HashMap<>();
    private static final Map<UUID, Location> pos2Selections = new HashMap<>();
    
    private static File configFile;
    private static FileConfiguration config;
    
    /**
     * Initialize the manager and load zones from storage
     * 
     * @param plugin The CoreProtect plugin instance
     */
    public static void initialize(CoreProtect plugin) {
        // Setup the configuration file
        configFile = new File(plugin.getDataFolder(), "exempt_zones.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create exempt_zones.yml", e);
            }
        }
        
        // Load the configuration
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load zones from config
        loadZones();
    }
    
    /**
     * Load exempt zones from configuration
     */
    private static void loadZones() {
        zones.clear();
        
        ConfigurationSection zonesSection = config.getConfigurationSection("zones");
        if (zonesSection == null) {
            return;
        }
        
        for (String zoneName : zonesSection.getKeys(false)) {
            ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneName);
            if (zoneSection != null) {
                String worldName = zoneSection.getString("world");
                int minX = zoneSection.getInt("min_x");
                int minY = zoneSection.getInt("min_y");
                int minZ = zoneSection.getInt("min_z");
                int maxX = zoneSection.getInt("max_x");
                int maxY = zoneSection.getInt("max_y");
                int maxZ = zoneSection.getInt("max_z");
                
                ExemptZone zone = new ExemptZone(zoneName, worldName, minX, minY, minZ, maxX, maxY, maxZ);
                zones.put(zoneName.toLowerCase(), zone);
            }
        }
    }
    
    /**
     * Save exempt zones to configuration
     */
    private static void saveZones() {
        // Clear existing zones section
        config.set("zones", null);
        
        // Create new zones section
        for (ExemptZone zone : zones.values()) {
            String path = "zones." + zone.getName();
            config.set(path + ".world", zone.getWorldName());
            config.set(path + ".min_x", zone.getMinX());
            config.set(path + ".min_y", zone.getMinY());
            config.set(path + ".min_z", zone.getMinZ());
            config.set(path + ".max_x", zone.getMaxX());
            config.set(path + ".max_y", zone.getMaxY());
            config.set(path + ".max_z", zone.getMaxZ());
        }
        
        // Save the config
        try {
            config.save(configFile);
        } catch (IOException e) {
            CoreProtect.getInstance().getLogger().log(Level.SEVERE, "Could not save exempt_zones.yml", e);
        }
    }
    
    /**
     * Set position 1 for a player's selection
     * 
     * @param player The player making the selection
     * @param location The selected location
     */
    public static void setPos1(Player player, Location location) {
        pos1Selections.put(player.getUniqueId(), location.clone());
    }
    
    /**
     * Set position 2 for a player's selection
     * 
     * @param player The player making the selection
     * @param location The selected location
     */
    public static void setPos2(Player player, Location location) {
        pos2Selections.put(player.getUniqueId(), location.clone());
    }
    
    /**
     * Create a new exempt zone from a player's selection
     * 
     * @param player The player creating the zone
     * @param zoneName The name for the new zone
     * @return true if creation was successful
     */
    public static boolean createZone(Player player, String zoneName) {
        // Check if player has made selections
        Location pos1 = pos1Selections.get(player.getUniqueId());
        Location pos2 = pos2Selections.get(player.getUniqueId());
        
        if (pos1 == null || pos2 == null) {
            return false;
        }
        
        // Check if worlds match
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return false;
        }
        
        // Check if name already exists
        if (zones.containsKey(zoneName.toLowerCase())) {
            return false;
        }
        
        // Create the zone
        ExemptZone zone = new ExemptZone(
            zoneName,
            pos1.getWorld().getName(),
            pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
            pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ()
        );
        
        // Add and save the zone
        zones.put(zoneName.toLowerCase(), zone);
        saveZones();
        
        return true;
    }
    
    /**
     * Delete an exempt zone
     * 
     * @param zoneName The name of the zone to delete
     * @return true if deletion was successful
     */
    public static boolean deleteZone(String zoneName) {
        if (zones.remove(zoneName.toLowerCase()) != null) {
            saveZones();
            return true;
        }
        return false;
    }
    
    /**
     * Get all exempt zones
     * 
     * @return A list of all exempt zones
     */
    public static List<ExemptZone> getAllZones() {
        return new ArrayList<>(zones.values());
    }
    
    /**
     * Check if a location is within any exempt zone
     * 
     * @param location The location to check
     * @return true if the location is exempt from logging
     */
    public static boolean isExempt(Location location) {
        if (location == null) {
            return false;
        }
        
        for (ExemptZone zone : zones.values()) {
            if (zone.contains(location)) {
                return true;
            }
        }
        
        return false;
    }
} 