package com.thijs226.fahdonor.notifications;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Advanced notification system for FAH plugin with support for:
 * - Boss bars with progress
 * - Title and subtitle messages
 * - Action bar messages
 * - Chat messages with formatting
 */
public class NotificationManager {
    
    private final Plugin plugin;
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private final Map<UUID, PlayerNotificationPreferences> preferences = new HashMap<>();
    
    public NotificationManager(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Player notification preferences
     */
    public static class PlayerNotificationPreferences {
        boolean bossBarEnabled = true;
        boolean actionBarEnabled = true;
        boolean titleEnabled = true;
        boolean chatEnabled = true;
        
        public boolean isBossBarEnabled() { return bossBarEnabled; }
        public boolean isActionBarEnabled() { return actionBarEnabled; }
        public boolean isTitleEnabled() { return titleEnabled; }
        public boolean isChatEnabled() { return chatEnabled; }
        
        public void setBossBarEnabled(boolean enabled) { this.bossBarEnabled = enabled; }
        public void setActionBarEnabled(boolean enabled) { this.actionBarEnabled = enabled; }
        public void setTitleEnabled(boolean enabled) { this.titleEnabled = enabled; }
        public void setChatEnabled(boolean enabled) { this.chatEnabled = enabled; }
    }
    
    /**
     * Gets player's notification preferences
     */
    public PlayerNotificationPreferences getPreferences(Player player) {
        return preferences.computeIfAbsent(player.getUniqueId(), 
            k -> new PlayerNotificationPreferences());
    }
    
    /**
     * Shows a boss bar with progress to a player
     */
    public void showBossBar(Player player, String message, double progress, BarColor color, int durationSeconds) {
        if (!getPreferences(player).isBossBarEnabled()) {
            return;
        }
        
        removeBossBar(player);
        
        BossBar bossBar = Bukkit.createBossBar(
            ChatColor.translateAlternateColorCodes('&', message),
            color,
            BarStyle.SOLID
        );
        
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bossBar.addPlayer(player);
        
        activeBossBars.put(player.getUniqueId(), bossBar);
        
        // Auto-remove after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeBossBar(player), durationSeconds * 20L);
    }
    
    /**
     * Updates an existing boss bar
     */
    public void updateBossBar(Player player, String message, double progress) {
        BossBar bossBar = activeBossBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', message));
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }
    }
    
    /**
     * Removes boss bar from a player
     */
    public void removeBossBar(Player player) {
        BossBar bossBar = activeBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }
    
    /**
     * Shows title and subtitle to a player
     */
    public void showTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (!getPreferences(player).isTitleEnabled()) {
            return;
        }
        
        player.sendTitle(
            ChatColor.translateAlternateColorCodes('&', title),
            subtitle != null ? ChatColor.translateAlternateColorCodes('&', subtitle) : "",
            fadeIn,
            stay,
            fadeOut
        );
    }
    
    /**
     * Shows action bar message to a player
     */
    public void showActionBar(Player player, String message) {
        if (!getPreferences(player).isActionBarEnabled()) {
            return;
        }
        
        try {
            player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message))
            );
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // Fallback if action bar not available
        }
    }
    
    /**
     * Sends a chat message to a player
     */
    public void sendMessage(Player player, String message) {
        if (!getPreferences(player).isChatEnabled()) {
            return;
        }
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
    
    /**
     * Broadcasts a message to all online players with permission
     */
    public void broadcast(String message, String permission) {
        String formatted = ChatColor.translateAlternateColorCodes('&', message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission == null || player.hasPermission(permission)) {
                if (getPreferences(player).isChatEnabled()) {
                    player.sendMessage(formatted);
                }
            }
        }
    }
    
    /**
     * Shows work unit completion notification
     */
    public void notifyWorkUnitComplete(Player player, long points) {
        showTitle(player, 
            "&a&lWork Unit Complete!", 
            "&e+" + String.format("%,d", points) + " points", 
            10, 40, 10);
        
        showActionBar(player, 
            "&a✓ Completed work unit - &e+" + String.format("%,d", points) + " points &a- Thank you for contributing!");
    }
    
    /**
     * Shows progress notification
     */
    public void notifyProgress(Player player, int percent) {
        showBossBar(player, 
            "&aFolding@home Progress: " + percent + "%", 
            percent / 100.0, 
            BarColor.GREEN, 
            60);
    }
    
    /**
     * Shows milestone notification
     */
    public void notifyMilestone(Player player, String milestone, String reward) {
        showTitle(player, 
            "&6&lMilestone Reached!", 
            "&e" + milestone, 
            10, 60, 10);
        
        sendMessage(player, "&6&l[FAH] &a" + milestone + " milestone reached!");
        if (reward != null && !reward.isEmpty()) {
            sendMessage(player, "&7Reward: " + reward);
        }
    }
    
    /**
     * Cleanup all boss bars
     */
    public void cleanup() {
        for (BossBar bossBar : activeBossBars.values()) {
            bossBar.removeAll();
        }
        activeBossBars.clear();
    }
}
