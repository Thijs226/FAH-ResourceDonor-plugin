package com.thijs226.fahdonor.leaderboard;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.thijs226.fahdonor.FAHResourceDonor;

/**
 * Manages leaderboards for tracking top contributors by various metrics.
 */
public class LeaderboardManager {
    
    private final FAHResourceDonor plugin;
    private final Map<UUID, LeaderboardEntry> entries = new ConcurrentHashMap<>();
    
    public LeaderboardManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        loadLeaderboard();
    }
    
    public static class LeaderboardEntry {
        private final UUID playerId;
        private String playerName;
        private long totalPoints;
        private long totalTimeSeconds;
        private long workUnitsCompleted;
        private long lastUpdateTime;
        
        public LeaderboardEntry(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.totalPoints = 0;
            this.totalTimeSeconds = 0;
            this.workUnitsCompleted = 0;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public UUID getPlayerId() {
            return playerId;
        }
        
        public String getPlayerName() {
            return playerName;
        }
        
        public void setPlayerName(String name) {
            this.playerName = name;
        }
        
        public long getTotalPoints() {
            return totalPoints;
        }
        
        public void addPoints(long points) {
            this.totalPoints += points;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public long getTotalTimeSeconds() {
            return totalTimeSeconds;
        }
        
        public void addTime(long seconds) {
            this.totalTimeSeconds += seconds;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public long getWorkUnitsCompleted() {
            return workUnitsCompleted;
        }
        
        public void incrementWorkUnits() {
            this.workUnitsCompleted++;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
        
        public double getPointsPerHour() {
            if (totalTimeSeconds == 0) return 0;
            return (totalPoints * 3600.0) / totalTimeSeconds;
        }
    }
    
    public LeaderboardEntry getOrCreateEntry(UUID playerId, String playerName) {
        return entries.computeIfAbsent(playerId, k -> new LeaderboardEntry(playerId, playerName));
    }
    
    public void recordPoints(UUID playerId, String playerName, long points) {
        LeaderboardEntry entry = getOrCreateEntry(playerId, playerName);
        entry.addPoints(points);
    }
    
    public void recordTime(UUID playerId, String playerName, long seconds) {
        LeaderboardEntry entry = getOrCreateEntry(playerId, playerName);
        entry.addTime(seconds);
    }
    
    public void recordWorkUnit(UUID playerId, String playerName) {
        LeaderboardEntry entry = getOrCreateEntry(playerId, playerName);
        entry.incrementWorkUnits();
    }
    
    public List<LeaderboardEntry> getTopByPoints(int limit) {
        return entries.values().stream()
            .sorted(Comparator.comparingLong(LeaderboardEntry::getTotalPoints).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public List<LeaderboardEntry> getTopByTime(int limit) {
        return entries.values().stream()
            .sorted(Comparator.comparingLong(LeaderboardEntry::getTotalTimeSeconds).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public List<LeaderboardEntry> getTopByWorkUnits(int limit) {
        return entries.values().stream()
            .sorted(Comparator.comparingLong(LeaderboardEntry::getWorkUnitsCompleted).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public List<LeaderboardEntry> getTopByEfficiency(int limit) {
        return entries.values().stream()
            .filter(e -> e.getTotalTimeSeconds() > 0)
            .sorted(Comparator.comparingDouble(LeaderboardEntry::getPointsPerHour).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public String formatLeaderboard(LeaderboardType type, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append("=== ").append(type.getDisplayName())
          .append(" Leaderboard ===\n");
        
        List<LeaderboardEntry> topEntries;
        switch (type) {
            case POINTS:
                topEntries = getTopByPoints(limit);
                break;
            case TIME:
                topEntries = getTopByTime(limit);
                break;
            case WORK_UNITS:
                topEntries = getTopByWorkUnits(limit);
                break;
            case EFFICIENCY:
                topEntries = getTopByEfficiency(limit);
                break;
            default:
                topEntries = Collections.emptyList();
        }
        
        int rank = 1;
        for (LeaderboardEntry entry : topEntries) {
            sb.append(ChatColor.YELLOW).append(rank).append(". ")
              .append(ChatColor.WHITE).append(entry.getPlayerName())
              .append(ChatColor.GRAY).append(" - ");
            
            switch (type) {
                case POINTS:
                    sb.append(ChatColor.GREEN).append(String.format("%,d points", entry.getTotalPoints()));
                    break;
                case TIME:
                    long hours = entry.getTotalTimeSeconds() / 3600;
                    long minutes = (entry.getTotalTimeSeconds() % 3600) / 60;
                    sb.append(ChatColor.GREEN).append(String.format("%dh %dm", hours, minutes));
                    break;
                case WORK_UNITS:
                    sb.append(ChatColor.GREEN).append(String.format("%d units", entry.getWorkUnitsCompleted()));
                    break;
                case EFFICIENCY:
                    sb.append(ChatColor.GREEN).append(String.format("%.0f pts/hr", entry.getPointsPerHour()));
                    break;
            }
            
            sb.append("\n");
            rank++;
        }
        
        if (topEntries.isEmpty()) {
            sb.append(ChatColor.GRAY).append("No contributors yet!\n");
        }
        
        return sb.toString();
    }
    
    public void displayLeaderboard(Player player, LeaderboardType type, int limit) {
        player.sendMessage(formatLeaderboard(type, limit));
    }
    
    public void broadcastLeaderboard(LeaderboardType type, int limit) {
        String formatted = formatLeaderboard(type, limit);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formatted);
        }
    }
    
    public int getPlayerRank(UUID playerId, LeaderboardType type) {
        List<LeaderboardEntry> sorted;
        switch (type) {
            case POINTS:
                sorted = getTopByPoints(Integer.MAX_VALUE);
                break;
            case TIME:
                sorted = getTopByTime(Integer.MAX_VALUE);
                break;
            case WORK_UNITS:
                sorted = getTopByWorkUnits(Integer.MAX_VALUE);
                break;
            case EFFICIENCY:
                sorted = getTopByEfficiency(Integer.MAX_VALUE);
                break;
            default:
                return -1;
        }
        
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getPlayerId().equals(playerId)) {
                return i + 1;
            }
        }
        return -1;
    }
    
    private void loadLeaderboard() {
        // Load from config
        var section = plugin.getConfig().getConfigurationSection("data.leaderboard");
        if (section == null) {
            return;
        }
        
        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                var playerData = section.getConfigurationSection(uuidStr);
                if (playerData == null) continue;
                
                String name = playerData.getString("name", "Unknown");
                LeaderboardEntry entry = new LeaderboardEntry(uuid, name);
                entry.totalPoints = playerData.getLong("points", 0);
                entry.totalTimeSeconds = playerData.getLong("time", 0);
                entry.workUnitsCompleted = playerData.getLong("units", 0);
                entry.lastUpdateTime = playerData.getLong("lastUpdate", System.currentTimeMillis());
                
                entries.put(uuid, entry);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in leaderboard data: " + uuidStr);
            }
        }
    }
    
    public void saveLeaderboard() {
        var section = plugin.getConfig().createSection("data.leaderboard");
        
        for (Map.Entry<UUID, LeaderboardEntry> entry : entries.entrySet()) {
            String uuidStr = entry.getKey().toString();
            LeaderboardEntry lbEntry = entry.getValue();
            
            var playerData = section.createSection(uuidStr);
            playerData.set("name", lbEntry.getPlayerName());
            playerData.set("points", lbEntry.getTotalPoints());
            playerData.set("time", lbEntry.getTotalTimeSeconds());
            playerData.set("units", lbEntry.getWorkUnitsCompleted());
            playerData.set("lastUpdate", lbEntry.getLastUpdateTime());
        }
        
        plugin.saveConfig();
    }
    
    public enum LeaderboardType {
        POINTS("Top Contributors (Points)"),
        TIME("Top Contributors (Time)"),
        WORK_UNITS("Top Contributors (Work Units)"),
        EFFICIENCY("Most Efficient Contributors");
        
        private final String displayName;
        
        LeaderboardType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
