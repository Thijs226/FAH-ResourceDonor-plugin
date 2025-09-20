package com.thijs226.fahdonor;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public class ConfigManager {
    private final FAHResourceDonor plugin;
    private YamlConfiguration config;
    
    public ConfigManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        plugin.saveDefaultConfig();
        config = (YamlConfiguration) plugin.getConfig();
        validateConfig();
    }
    
    private void validateConfig() {
        // Validate core settings
        int totalCores = config.getInt("server.total-cores");
        int reservedCores = config.getInt("server.reserved-cores");
        
        if (reservedCores >= totalCores) {
            plugin.getLogger().warning("Reserved cores >= total cores! Adjusting...");
            config.set("server.reserved-cores", Math.max(1, totalCores - 1));
            plugin.saveConfig();
        }
        
        // Validate team ID
        String teamId = config.getString("folding-at-home.default-account.team-id", "");
        if (teamId.isEmpty()) {
            plugin.getLogger().warning("No team ID configured! Please set one in config.yml");
        }
    }
    
    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }
}