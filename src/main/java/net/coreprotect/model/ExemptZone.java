package net.coreprotect.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Represents a 3D zone where CoreProtect logging is disabled
 */
public class ExemptZone {
    private String name;
    private String worldName;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public ExemptZone(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.worldName = worldName;
        
        // Ensure min is always lower than max
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * Checks if a location is within this exempt zone
     * 
     * @param location The location to check
     * @return true if the location is within this zone
     */
    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }
        
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        return x >= minX && x <= maxX 
            && y >= minY && y <= maxY 
            && z >= minZ && z <= maxZ;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }
    
    @Override
    public String toString() {
        return name + ": " + worldName + " (" + minX + "," + minY + "," + minZ + ") to (" + maxX + "," + maxY + "," + maxZ + ")";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExemptZone that = (ExemptZone) o;
        return Objects.equals(name, that.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
} 