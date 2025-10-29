package com.thijs226.fahdonor.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import com.thijs226.fahdonor.FAHResourceDonor;

/**
 * GUI showing detailed FAH statistics and contribution information
 */
public class StatisticsGUI extends BaseGUI {
    
    private final FAHResourceDonor fahPlugin;
    
    public StatisticsGUI(FAHResourceDonor plugin, Player player) {
        super(plugin, player, "&6&lFolding@home Statistics", 5);
        this.fahPlugin = plugin;
    }
    
    @Override
    protected void buildInventory() {
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Server statistics
        addServerStats();
        
        // Personal statistics
        addPersonalStats();
        
        // Current status
        addCurrentStatus();
        
        // Close button
        ItemStack closeButton = createItem(Material.BARRIER, 
            "&cClose",
            "&7Click to close");
        inventory.setItem(40, closeButton);
    }
    
    private void addServerStats() {
        if (fahPlugin.getFAHClient() == null) {
            return;
        }
        
        long totalPoints = fahPlugin.getFAHClient().getPointsEarned();
        int completedUnits = fahPlugin.getFAHClient().getCompletedWorkUnits();
        double coreHours = fahPlugin.getFAHClient().getTotalCoreHours();
        
        ItemStack serverStats = createItem(Material.DIAMOND, 
            "&b&lServer Statistics",
            "&7Total Points: &e" + String.format("%,d", totalPoints),
            "&7Completed Units: &e" + completedUnits,
            "&7Core Hours: &e" + String.format("%.1f", coreHours),
            "",
            "&7These are cumulative statistics",
            "&7for all players on this server");
        
        inventory.setItem(11, serverStats);
    }
    
    private void addPersonalStats() {
        if (fahPlugin.getRewardManager() == null) {
            return;
        }
        
        var contribution = fahPlugin.getRewardManager().getContribution(player.getUniqueId());
        long hours = contribution.getContributionTimeSeconds() / 3600;
        long minutes = (contribution.getContributionTimeSeconds() % 3600) / 60;
        
        ItemStack personalStats = createItem(Material.PLAYER_HEAD, 
            "&a&lYour Contribution",
            "&7Contribution Time: &e" + hours + "h " + minutes + "m",
            "&7Points Earned: &e" + String.format("%,d", contribution.getPointsEarned()),
            "&7Work Units: &e" + contribution.getWorkUnitsCompleted(),
            "",
            "&7Thank you for contributing to",
            "&7medical research!");
        
        inventory.setItem(13, personalStats);
    }
    
    private void addCurrentStatus() {
        if (fahPlugin.getFAHClient() == null || fahPlugin.getFAHManager() == null) {
            return;
        }
        
        boolean isRunning = fahPlugin.getFAHManager().isFAHRunning();
        boolean isProcessing = fahPlugin.getFAHClient().isProcessingWork();
        String status = fahPlugin.getFAHClient().getWorkUnitStatus();
        String progress = fahPlugin.getFAHClient().getProgress();
        int cores = fahPlugin.getFAHManager().getCurrentCores();
        
        Material material = isProcessing ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String statusColor = isProcessing ? "&a" : "&c";
        
        ItemStack currentStatus = createItem(material, 
            "&e&lCurrent Status",
            statusColor + "Status: " + (isRunning ? "Running" : "Stopped"),
            "&7Processing: " + (isProcessing ? "&aYes" : "&cNo"),
            "&7Progress: &e" + progress,
            "&7Allocated Cores: &e" + cores,
            "",
            "&7" + status);
        
        inventory.setItem(15, currentStatus);
    }
    
    @Override
    protected void handleClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        
        // Close button
        if (clicked.getType() == Material.BARRIER) {
            close();
            player.sendMessage(ChatColor.GRAY + "Statistics menu closed");
        }
    }
}
