package com.thijs226.fahdonor.voting;

import com.thijs226.fahdonor.FAHResourceDonor;
import com.thijs226.fahdonor.FAHClientManager.FoldingCause;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.*;
import java.util.*;

public class CauseVotingManager {
    private final FAHResourceDonor plugin;
    private final Map<UUID, FoldingCause> playerVotes = new HashMap<>();
    private final Map<FoldingCause, Integer> voteTally = new HashMap<>();
    private FoldingCause currentWinner = FoldingCause.ANY;
    private long lastVotePeriod;
    
    public CauseVotingManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        loadVotes();
        
        if (plugin.getConfig().getBoolean("folding-at-home.democratic-cause.enabled")) {
            startVotingCycle();
        }
    }
    
    private void startVotingCycle() {
        long votePeriodHours = plugin.getConfig().getLong("folding-at-home.democratic-cause.vote-period", 24);
        long votePeriodTicks = votePeriodHours * 60 * 60 * 20;
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            tallyVotes();
            
            if (plugin.getConfig().getBoolean("folding-at-home.democratic-cause.auto-switch")) {
                plugin.getFAHManager().updateCause(currentWinner);
            }
            
            announceResults();
            resetVotes();
            
        }, votePeriodTicks, votePeriodTicks);
    }
    
    public void castVote(Player player, FoldingCause cause) {
        UUID playerId = player.getUniqueId();
        FoldingCause previousVote = playerVotes.get(playerId);
        
        if (previousVote != null && previousVote.equals(cause)) {
            player.sendMessage(ChatColor.YELLOW + "You've already voted for " + cause.getDescription());
            return;
        }
        
        playerVotes.put(playerId, cause);
        player.sendMessage(ChatColor.GREEN + "✓ Your vote for " + ChatColor.YELLOW + 
                          cause.getDescription() + ChatColor.GREEN + " has been recorded!");
        
        showStandings(player);
        saveVotes();
    }
    
    public void removeVote(Player player) {
        if (playerVotes.remove(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.GRAY + "Your vote has been removed.");
            saveVotes();
        } else {
            player.sendMessage(ChatColor.RED + "You haven't voted yet!");
        }
    }
    
    private void tallyVotes() {
        voteTally.clear();
        
        for (FoldingCause cause : FoldingCause.values()) {
            voteTally.put(cause, 0);
        }
        
        for (FoldingCause vote : playerVotes.values()) {
            voteTally.merge(vote, 1, Integer::sum);
        }
        
        currentWinner = voteTally.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(FoldingCause.ANY);
    }
    
    public void showStandings(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Current Research Vote Standings ===");
        
        tallyVotes();
        
        voteTally.entrySet().stream()
            .sorted(Map.Entry.<FoldingCause, Integer>comparingByValue().reversed())
            .limit(5)
            .forEach(entry -> {
                FoldingCause cause = entry.getKey();
                int votes = entry.getValue();
                
                String bar = createProgressBar(votes, playerVotes.size());
                player.sendMessage(ChatColor.GRAY + cause.getName() + ": " + 
                                 bar + ChatColor.WHITE + " " + votes + " votes");
            });
        
        player.sendMessage(ChatColor.GRAY + "Total votes: " + ChatColor.WHITE + playerVotes.size());
        player.sendMessage(ChatColor.GRAY + "Current leader: " + ChatColor.YELLOW + 
                          currentWinner.getDescription());
    }
    
    private String createProgressBar(int votes, int total) {
        int barLength = 20;
        int filled = total > 0 ? (votes * barLength) / total : 0;
        
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GREEN);
        
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append(ChatColor.DARK_GRAY).append("░");
            }
        }
        
        return bar.toString();
    }
    
    private void announceResults() {
        String message = ChatColor.GOLD + "══════════════════════════\n" +
                        ChatColor.YELLOW + "Research Focus Vote Results:\n" +
                        ChatColor.WHITE + "Winner: " + ChatColor.GREEN + currentWinner.getDescription() + "\n" +
                        ChatColor.WHITE + "Votes: " + ChatColor.AQUA + voteTally.get(currentWinner) + 
                        "/" + playerVotes.size() + "\n" +
                        ChatColor.GOLD + "══════════════════════════";
        
        Bukkit.broadcastMessage(message);
    }
    
    private void resetVotes() {
        playerVotes.clear();
        voteTally.clear();
        lastVotePeriod = System.currentTimeMillis();
        saveVotes();
    }
    
    public void saveVotes() {
        File votesFile = new File(plugin.getDataFolder(), "votes.yml");
        YamlConfiguration votes = new YamlConfiguration();
        
        for (Map.Entry<UUID, FoldingCause> entry : playerVotes.entrySet()) {
            votes.set(entry.getKey().toString(), entry.getValue().getName());
        }
        
        votes.set("last-period", lastVotePeriod);
        
        try {
            votes.save(votesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save votes: " + e.getMessage());
        }
    }
    
    private void loadVotes() {
        File votesFile = new File(plugin.getDataFolder(), "votes.yml");
        if (!votesFile.exists()) return;
        
        YamlConfiguration votes = YamlConfiguration.loadConfiguration(votesFile);
        lastVotePeriod = votes.getLong("last-period", System.currentTimeMillis());
        
        for (String key : votes.getKeys(false)) {
            if (key.equals("last-period")) continue;
            
            try {
                UUID playerId = UUID.fromString(key);
                FoldingCause cause = FoldingCause.fromString(votes.getString(key));
                playerVotes.put(playerId, cause);
            } catch (Exception e) {
                // Skip invalid entries
            }
        }
    }
}