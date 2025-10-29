package com.thijs226.fahdonor.rewards;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.thijs226.fahdonor.FAHResourceDonor;

/**
 * Manages rewards for players based on their contribution to Folding@home.
 * Tracks contribution time, points earned, and distributes configurable rewards.
 */
public class RewardManager {
    
    private final FAHResourceDonor plugin;
    private final Map<UUID, PlayerContribution> contributions = new ConcurrentHashMap<>();
    
    public RewardManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        loadContributions();
    }
    
    public static class PlayerContribution {
        private long contributionTimeSeconds;
        private long pointsEarned;
        private long workUnitsCompleted;
        private long lastRewardTime;
        
        public PlayerContribution() {
            this.contributionTimeSeconds = 0;
            this.pointsEarned = 0;
            this.workUnitsCompleted = 0;
            this.lastRewardTime = System.currentTimeMillis();
        }
        
        public void addContributionTime(long seconds) {
            this.contributionTimeSeconds += seconds;
        }
        
        public void addPoints(long points) {
            this.pointsEarned += points;
        }
        
        public void incrementWorkUnits() {
            this.workUnitsCompleted++;
        }
        
        public long getContributionTimeSeconds() {
            return contributionTimeSeconds;
        }
        
        public long getPointsEarned() {
            return pointsEarned;
        }
        
        public long getWorkUnitsCompleted() {
            return workUnitsCompleted;
        }
        
        public long getLastRewardTime() {
            return lastRewardTime;
        }
        
        public void updateLastRewardTime() {
            this.lastRewardTime = System.currentTimeMillis();
        }
    }
    
    public void recordContribution(UUID playerId, long seconds) {
        PlayerContribution contrib = contributions.computeIfAbsent(playerId, k -> new PlayerContribution());
        contrib.addContributionTime(seconds);
        checkAndGiveRewards(playerId);
    }
    
    public void recordPoints(UUID playerId, long points) {
        PlayerContribution contrib = contributions.computeIfAbsent(playerId, k -> new PlayerContribution());
        contrib.addPoints(points);
        checkAndGiveRewards(playerId);
    }
    
    public void recordWorkUnitCompleted(UUID playerId) {
        PlayerContribution contrib = contributions.computeIfAbsent(playerId, k -> new PlayerContribution());
        contrib.incrementWorkUnits();
        checkAndGiveRewards(playerId);
    }
    
    public PlayerContribution getContribution(UUID playerId) {
        return contributions.computeIfAbsent(playerId, k -> new PlayerContribution());
    }
    
    private void checkAndGiveRewards(UUID playerId) {
        if (!plugin.getConfig().getBoolean("rewards.enabled", true)) {
            return;
        }
        
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        
        PlayerContribution contrib = contributions.get(playerId);
        if (contrib == null) {
            return;
        }
        
        // Check time-based rewards
        checkTimeRewards(player, contrib);
        
        // Check points-based rewards
        checkPointsRewards(player, contrib);
        
        // Check work unit rewards
        checkWorkUnitRewards(player, contrib);
    }
    
    private void checkTimeRewards(Player player, PlayerContribution contrib) {
        ConfigurationSection timeRewards = plugin.getConfig().getConfigurationSection("rewards.time-based");
        if (timeRewards == null) {
            return;
        }
        
        long hours = contrib.getContributionTimeSeconds() / 3600;
        
        for (String key : timeRewards.getKeys(false)) {
            ConfigurationSection reward = timeRewards.getConfigurationSection(key);
            if (reward == null) continue;
            
            long requiredHours = reward.getLong("hours", 0);
            if (hours >= requiredHours && !hasReceivedReward(player, "time_" + key)) {
                giveReward(player, reward, "time_" + key);
            }
        }
    }
    
    private void checkPointsRewards(Player player, PlayerContribution contrib) {
        ConfigurationSection pointsRewards = plugin.getConfig().getConfigurationSection("rewards.points-based");
        if (pointsRewards == null) {
            return;
        }
        
        long points = contrib.getPointsEarned();
        
        for (String key : pointsRewards.getKeys(false)) {
            ConfigurationSection reward = pointsRewards.getConfigurationSection(key);
            if (reward == null) continue;
            
            long requiredPoints = reward.getLong("points", 0);
            if (points >= requiredPoints && !hasReceivedReward(player, "points_" + key)) {
                giveReward(player, reward, "points_" + key);
            }
        }
    }
    
    private void checkWorkUnitRewards(Player player, PlayerContribution contrib) {
        ConfigurationSection wuRewards = plugin.getConfig().getConfigurationSection("rewards.workunit-based");
        if (wuRewards == null) {
            return;
        }
        
        long units = contrib.getWorkUnitsCompleted();
        
        for (String key : wuRewards.getKeys(false)) {
            ConfigurationSection reward = wuRewards.getConfigurationSection(key);
            if (reward == null) continue;
            
            long requiredUnits = reward.getLong("units", 0);
            if (units >= requiredUnits && !hasReceivedReward(player, "wu_" + key)) {
                giveReward(player, reward, "wu_" + key);
            }
        }
    }
    
    private boolean hasReceivedReward(Player player, String rewardId) {
        return player.hasPermission("fahdonor.reward." + rewardId);
    }
    
    private void giveReward(Player player, ConfigurationSection reward, String rewardId) {
        // Mark as received
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            String.format("lp user %s permission set fahdonor.reward.%s true", player.getName(), rewardId));
        
            // Execute commands
        if (reward.contains("commands")) {
            for (String command : reward.getStringList("commands")) {
                String processedCommand = command
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            }
        }
        
        // Send message
        String message = reward.getString("message");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }        // Log
        String rewardName = reward.getString("name", rewardId);
        plugin.getLogger().info(String.format("Gave reward '%s' to player %s", rewardName, player.getName()));
    }
    
    public Map<UUID, PlayerContribution> getAllContributions() {
        return new HashMap<>(contributions);
    }
    
    private void loadContributions() {
        ConfigurationSection data = plugin.getConfig().getConfigurationSection("data.contributions");
        if (data == null) {
            return;
        }
        
        for (String uuidStr : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection playerData = data.getConfigurationSection(uuidStr);
                if (playerData == null) continue;
                
                PlayerContribution contrib = new PlayerContribution();
                contrib.contributionTimeSeconds = playerData.getLong("time", 0);
                contrib.pointsEarned = playerData.getLong("points", 0);
                contrib.workUnitsCompleted = playerData.getLong("units", 0);
                contrib.lastRewardTime = playerData.getLong("lastReward", System.currentTimeMillis());
                
                contributions.put(uuid, contrib);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(() -> "Invalid UUID in contributions data: " + uuidStr);
            }
        }
    }
    
    public void saveContributions() {
        ConfigurationSection data = plugin.getConfig().createSection("data.contributions");
        
        for (Map.Entry<UUID, PlayerContribution> entry : contributions.entrySet()) {
            String uuidStr = entry.getKey().toString();
            PlayerContribution contrib = entry.getValue();
            
            ConfigurationSection playerData = data.createSection(uuidStr);
            playerData.set("time", contrib.getContributionTimeSeconds());
            playerData.set("points", contrib.getPointsEarned());
            playerData.set("units", contrib.getWorkUnitsCompleted());
            playerData.set("lastReward", contrib.getLastRewardTime());
        }
        
        plugin.saveConfig();
    }
}
