package net.coreprotect.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.coreprotect.event.CoreProtectPreLogEvent;
import net.coreprotect.model.ExemptZoneManager;

/**
 * Listener that checks if logging events occur in exempt zones
 */
public class ZoneExemptListener implements Listener {

    /**
     * Handle CoreProtectPreLogEvent to check if the location is in an exempt zone
     * 
     * @param event The CoreProtectPreLogEvent to check
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLog(CoreProtectPreLogEvent event) {
        Location location = event.getLocation();
        if (location != null && ExemptZoneManager.isExempt(location)) {
            event.setCancelled(true);
        }
    }
} 