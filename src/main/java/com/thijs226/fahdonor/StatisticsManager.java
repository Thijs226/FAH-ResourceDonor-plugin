package com.thijs226.fahdonor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.*;
import java.time.Duration;
import java.time.Instant;

public class StatisticsManager {
    private final FAHResourceDonor plugin;
    private long totalWorkUnits = 0;
    private long totalPoints = 0;
    private Instant startTime;
    private long totalCoreHours = 0;
    
    public StatisticsManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        this.startTime = Instant.now();
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::fetchStats, 0L, 12000L);
    }
    
    private void fetchStats() {
        try {
            parseLocalStats();
        } catch (Exception e) {
            // Silently fail, stats are non-critical
        }
    }
    
    private void parseLocalStats() {
        File logFile = new File(plugin.getDataFolder(), "folding-at-home/log.txt");
        if (!logFile.exists()) return;
        
        // Parse F@H log for completed work units
        // This would parse the actual F@H log format in production
    }
    
    public void displayStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Folding@home Contribution Stats ===");
        
        Duration uptime = Duration.between(startTime, Instant.now());
        long hours = uptime.toHours();
        
        FAHClientManager.AccountInfo account = plugin.getFAHManager().getCurrentAccount();
        
        sender.sendMessage(ChatColor.GRAY + "Account: " + ChatColor.WHITE + account.username);
        sender.sendMessage(ChatColor.GRAY + "Team: " + ChatColor.WHITE + account.teamId);
        sender.sendMessage(ChatColor.GRAY + "Running for: " + ChatColor.WHITE + hours + " hours");
        sender.sendMessage(ChatColor.GRAY + "Work Units: " + ChatColor.WHITE + totalWorkUnits);
        sender.sendMessage(ChatColor.GRAY + "Points Earned: " + ChatColor.WHITE + 
            String.format("%,d", totalPoints));
        sender.sendMessage(ChatColor.GRAY + "Core Hours Donated: " + ChatColor.WHITE + totalCoreHours);
        
        long nextMilestone = ((totalPoints / 100000) + 1) * 100000;
        double progress = (totalPoints % 100000) / 1000.0;
        sender.sendMessage(ChatColor.GRAY + "Next Milestone: " + ChatColor.YELLOW + 
            String.format("%,d points (%.1f%% complete)", nextMilestone, progress));
            
        sender.sendMessage(ChatColor.DARK_GRAY + "View online: " + 
            ChatColor.AQUA + ChatColor.UNDERLINE + 
            "https://stats.foldingathome.org/donor/" + account.username);
    }
    
    public void onPlayerJoin(Player player) {
        if (!plugin.getConfig().getBoolean("statistics.show-on-join", true)) return;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (totalPoints > 0) {
                player.sendMessage(ChatColor.GRAY + "This server has donated " + 
                    ChatColor.GOLD + String.format("%,d", totalPoints) + 
                    ChatColor.GRAY + " points to medical research via Folding@home!");
            }
        }, 60L);
    }
    
    public void checkMilestones() {
        if (totalPoints > 0 && totalPoints % 100000 == 0) {
            if (plugin.getConfig().getBoolean("statistics.broadcast-milestones", true)) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "⭐ " + ChatColor.GREEN + 
                    "Server Milestone! We've contributed " + 
                    ChatColor.YELLOW + String.format("%,d", totalPoints) + 
                    ChatColor.GREEN + " points to Folding@home!");
            }
        }
    }
}