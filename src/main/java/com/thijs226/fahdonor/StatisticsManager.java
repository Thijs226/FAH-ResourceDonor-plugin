package com.thijs226.fahdonor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.time.Instant;

public class StatisticsManager {
    private final FAHResourceDonor plugin;
    private final FAHClient fahClient;
    private long totalWorkUnits = 0;
    private long totalPoints = 0;
    private Instant startTime;
    private double totalCoreHours = 0.0;
    private long totalFailures = 0;
    private int consecutiveFailures = 0;
    
    public StatisticsManager(FAHResourceDonor plugin, FAHClient fahClient) {
        this.plugin = plugin;
        this.fahClient = fahClient;
        this.startTime = Instant.now();
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::fetchStats, 0L, 12000L);
    }
    
    private void fetchStats() {
        if (fahClient == null) {
            return;
        }
        try {
            totalWorkUnits = fahClient.getCompletedWorkUnits();
            totalPoints = fahClient.getPointsEarned();
            totalCoreHours = fahClient.getTotalCoreHours();
            totalFailures = fahClient.getTotalFailures();
            consecutiveFailures = fahClient.getConsecutiveFailures();
        } catch (RuntimeException ignored) {
            // Stats are informational; ignore transient issues
        }
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
        sender.sendMessage(ChatColor.GRAY + "Core Hours Donated: " + ChatColor.WHITE + 
            String.format("%.2f", totalCoreHours));
        if (totalFailures > 0) {
            sender.sendMessage(ChatColor.GRAY + "Work Unit Failures: " + ChatColor.WHITE + totalFailures +
                (consecutiveFailures > 0 ? ChatColor.DARK_RED + " (" + consecutiveFailures + " consecutive)" : ""));
            if (fahClient.isAutoRestartSuppressed()) {
                sender.sendMessage(ChatColor.RED + "Auto-restart disabled due to repeated failures. Investigate FAH logs and use /fah start to resume once fixed.");
            }
        }
        
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