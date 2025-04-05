package net.coreprotect.utility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.coreprotect.config.ConfigHandler;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Utility class for sending action bar messages to players.
 */
public final class ActionBar {
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private ActionBar() {
        throw new IllegalStateException("Utility class");
    }
    
    /**
     * Sends a message to a player's action bar.
     * 
     * @param player The player to send the message to
     * @param message The message to send
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        try {
            // Use the Spigot/Paper API
            if (ConfigHandler.isSpigot || ConfigHandler.isPaper) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            } 
            // Fallback to the Bukkit API (1.19+)
            else {
                try {
                    // This is using reflection to support different Minecraft versions
                    Class<?> craftPlayerClass = player.getClass();
                    Object craftPlayer = craftPlayerClass.cast(player);
                    
                    // Get the getHandle method
                    Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
                    
                    // Get the connection field
                    Object playerConnection = entityPlayer.getClass().getField("b").get(entityPlayer);
                    
                    // Create the chat component from the message
                    Class<?> chatComponentTextClass = Class.forName("net.minecraft.network.chat.ChatComponentText");
                    Object chatComponent = chatComponentTextClass.getConstructor(String.class).newInstance(message);
                    
                    // Create the packet
                    Class<?> packetPlayOutChatClass = Class.forName("net.minecraft.network.protocol.game.PacketPlayOutChat");
                    Object packetPlayOutChat = packetPlayOutChatClass.getConstructor(
                            Class.forName("net.minecraft.network.chat.IChatBaseComponent"),
                            Class.forName("net.minecraft.network.chat.ChatMessageType"),
                            Class.forName("java.util.UUID")
                    ).newInstance(
                            chatComponent,
                            Enum.valueOf(
                                    (Class<Enum>) Class.forName("net.minecraft.network.chat.ChatMessageType"),
                                    "c"
                            ),
                            player.getUniqueId()
                    );
                    
                    // Send the packet
                    playerConnection.getClass().getMethod("a", Class.forName("net.minecraft.network.protocol.Packet"))
                            .invoke(playerConnection, packetPlayOutChat);
                } catch (Exception e) {
                    // Silently fail if reflection fails
                    // In some server implementations, the action bar may not be available
                }
            }
        } catch (Exception e) {
            // Log if there's a serious error
            Bukkit.getLogger().warning("[CoreProtect] Failed to send action bar message: " + e.getMessage());
        }
    }
    
    /**
     * Sends a CoreProtect-styled message to a player's action bar.
     * 
     * @param player The player to send the message to
     * @param message The message to send (without color codes)
     */
    public static void sendCoreProtectMessage(Player player, String message) {
        sendMessage(player, Color.DARK_AQUA + "CoreProtect: " + Color.WHITE + message);
    }
} 