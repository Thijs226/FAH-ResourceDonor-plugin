package com.thijs226.fahresourcedonor;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * FAH Resource Donor Plugin
 * A Minecraft plugin for donating computing resources to Folding@Home
 */
public class FAHResourceDonor extends JavaPlugin {
    
    private FileConfiguration config;
    private String fahToken;
    private String fahTeamId;
    private String donorName;
    private boolean debugMode;
    
    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Load configuration
        config = getConfig();
        loadConfigValues();
        
        getLogger().info("FAH Resource Donor Plugin has been enabled!");
        
        if (debugMode) {
            getLogger().info("Debug mode is enabled");
            getLogger().info("FAH Token: " + (fahToken != null ? "***CONFIGURED***" : "NOT SET"));
            getLogger().info("FAH Team ID: " + fahTeamId);
            getLogger().info("Donor Name: " + donorName);
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("FAH Resource Donor Plugin has been disabled!");
    }
    
    /**
     * Load configuration values from config.yml
     */
    private void loadConfigValues() {
        fahToken = config.getString("fah.token", "");
        fahTeamId = config.getString("fah.team-id", "0");
        donorName = config.getString("donor.name", "Anonymous");
        debugMode = config.getBoolean("debug.enabled", false);
    }
    
    /**
     * Reload configuration from file
     */
    public void reloadConfiguration() {
        reloadConfig();
        config = getConfig();
        loadConfigValues();
        
        if (debugMode) {
            getLogger().info("Configuration reloaded");
        }
    }
    
    /**
     * Get the configured FAH token
     * @return The FAH token
     */
    public String getFahToken() {
        return fahToken;
    }
    
    /**
     * Get the configured FAH team ID
     * @return The FAH team ID
     */
    public String getFahTeamId() {
        return fahTeamId;
    }
    
    /**
     * Get the configured donor name
     * @return The donor name
     */
    public String getDonorName() {
        return donorName;
    }
    
    /**
     * Check if debug mode is enabled
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean isDebugMode() {
        return debugMode;
    }
}