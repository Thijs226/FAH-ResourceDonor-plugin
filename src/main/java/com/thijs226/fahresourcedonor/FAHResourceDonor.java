package com.thijs226.fahresourcedonor;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class FAHResourceDonor extends JavaPlugin {
    
    private FAHClient fahClient;
    private boolean isRunning = false;
    private String fahToken;
    private String fahTeamId;
    private String donorName;
    private int checkInterval;
    private boolean debugMode;
    private BukkitRunnable statusChecker;
    
    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        loadConfiguration();
        
        // Initialize FAH client
        fahClient = new FAHClient(this);
        
        getLogger().info("FAH ResourceDonor plugin has been enabled!");
        
        if (debugMode) {
            getLogger().info("Debug mode is enabled");
        }
        
        // Start the FAH service
        startFAHService();
        
        // Register commands
        getCommand("fah").setExecutor(new FAHCommand(this));
        
        // Start status checker
        startStatusChecker();
    }
    
    @Override
    public void onDisable() {
        if (statusChecker != null) {
            statusChecker.cancel();
        }
        
        if (fahClient != null) {
            fahClient.shutdown();
        }
        
        getLogger().info("FAH ResourceDonor plugin has been disabled!");
    }
    
    private void loadConfiguration() {
        FileConfiguration config = getConfig();
        
        fahToken = config.getString("fah.token", "");
        fahTeamId = config.getString("fah.team", "0");
        donorName = config.getString("fah.donor-name", "MinecraftServer");
        checkInterval = config.getInt("fah.check-interval", 300); // 5 minutes default
        debugMode = config.getBoolean("debug", false);
        
        if (fahToken.isEmpty()) {
            getLogger().warning("FAH token is not configured! Please set 'fah.token' in config.yml");
            getLogger().warning("Get your token from: https://apps.foldingathome.org/getpasskey");
        }
        
        if (debugMode) {
            getLogger().info("Configuration loaded:");
            getLogger().info("  Token: " + (fahToken.isEmpty() ? "NOT SET" : "***configured***"));
            getLogger().info("  Team: " + fahTeamId);
            getLogger().info("  Donor Name: " + donorName);
            getLogger().info("  Check Interval: " + checkInterval + " seconds");
        }
    }
    
    private void startFAHService() {
        if (fahToken.isEmpty()) {
            getLogger().severe("Cannot start FAH service: No token configured!");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                boolean success = fahClient.initialize(fahToken, fahTeamId, donorName);
                if (success) {
                    isRunning = true;
                    getLogger().info("FAH client successfully initialized and started!");
                    
                    // Verify connection to FAH servers
                    CompletableFuture.runAsync(this::verifyFAHConnection);
                } else {
                    getLogger().severe("Failed to initialize FAH client!");
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error starting FAH service", e);
            }
        });
    }
    
    private void verifyFAHConnection() {
        try {
            boolean connected = fahClient.testConnection();
            if (connected) {
                getLogger().info("Successfully connected to Folding@Home servers!");
                
                // Get and log current work unit status
                String status = fahClient.getWorkUnitStatus();
                if (debugMode) {
                    getLogger().info("Current work unit status: " + status);
                }
                
                // Verify stats are being reported
                boolean statsVerified = verifyStatsReporting();
                if (statsVerified) {
                    getLogger().info("Stats reporting verified - contributions should appear in your FAH account");
                } else {
                    getLogger().warning("Stats reporting verification failed - check your token and team settings");
                }
            } else {
                getLogger().warning("Could not connect to Folding@Home servers - check internet connection");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error verifying FAH connection", e);
        }
    }
    
    private boolean verifyStatsReporting() {
        try {
            // Check if our donor name appears in the team stats
            String apiUrl = String.format("https://stats.foldingathome.org/api/donor/%s", donorName);
            
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = reader.lines().reduce("", String::concat);
                    if (debugMode) {
                        getLogger().info("Donor stats response: " + response);
                    }
                    return !response.isEmpty() && !response.contains("\"name\":null");
                }
            } else {
                if (debugMode) {
                    getLogger().info("Donor stats API returned code: " + responseCode);
                }
                return false;
            }
        } catch (Exception e) {
            if (debugMode) {
                getLogger().log(Level.INFO, "Could not verify stats reporting", e);
            }
            return false;
        }
    }
    
    private void startStatusChecker() {
        statusChecker = new BukkitRunnable() {
            @Override
            public void run() {
                if (isRunning && fahClient != null) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            String status = fahClient.getWorkUnitStatus();
                            String progress = fahClient.getProgress();
                            
                            if (debugMode) {
                                getLogger().info("FAH Status Check:");
                                getLogger().info("  Status: " + status);
                                getLogger().info("  Progress: " + progress);
                                getLogger().info("  Points earned: " + fahClient.getPointsEarned());
                            }
                            
                            // Check if we're actually processing work
                            if (!fahClient.isProcessingWork()) {
                                getLogger().warning("FAH client is not processing work units. Attempting to restart...");
                                fahClient.requestWorkUnit();
                            }
                            
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING, "Error during status check", e);
                        }
                    });
                }
            }
        };
        
        statusChecker.runTaskTimer(this, 20L * 60L, 20L * checkInterval); // First run after 1 minute, then every checkInterval
    }
    
    public FAHClient getFAHClient() {
        return fahClient;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public String getFahToken() {
        return fahToken;
    }
    
    public String getFahTeamId() {
        return fahTeamId;
    }
    
    public String getDonorName() {
        return donorName;
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
    
    public void reloadConfiguration() {
        reloadConfig();
        loadConfiguration();
        
        if (fahClient != null) {
            fahClient.updateConfiguration(fahToken, fahTeamId, donorName);
        }
        
        getLogger().info("Configuration reloaded!");
    }
}