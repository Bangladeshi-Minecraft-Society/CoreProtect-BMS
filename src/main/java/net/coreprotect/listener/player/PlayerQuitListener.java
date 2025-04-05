package net.coreprotect.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.thread.InspectorStatusTask;

public final class PlayerQuitListener extends Queue implements Listener {

    public static void queuePlayerQuit(Player player) {
        if (Config.getConfig(player.getWorld()).PLAYER_SESSIONS) {
            int time = (int) (System.currentTimeMillis() / 1000L);
            Queue.queuePlayerQuit(player, time);
        }
        
        // Stop the inspector status task if the player had it active
        if (ConfigHandler.inspecting.containsKey(player.getName()) && 
            Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
            InspectorStatusTask.stopTask(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        queuePlayerQuit(event.getPlayer());
    }

}
