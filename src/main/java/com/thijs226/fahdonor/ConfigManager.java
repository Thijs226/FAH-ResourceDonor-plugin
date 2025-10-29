package com.thijs226.fahdonor;

import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigManager {
    private final FAHResourceDonor plugin;
    private YamlConfiguration config;
    private static final int CONFIG_VERSION = 3;
    
    public ConfigManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        plugin.saveDefaultConfig();
        config = (YamlConfiguration) plugin.getConfig();
        applyMigrations();
        validateConfig();
    }
    
    private void applyMigrations() {
        boolean changed = false;
        int currentVersion = config.getInt("config-version", 1);

        if (currentVersion < 2) {
            if (!config.contains("monitoring.status-refresh-interval")) {
                config.set("monitoring.status-refresh-interval", 200);
                changed = true;
            }

            if (!config.contains("monitoring.log-watchdog.stall-threshold-seconds")) {
                config.set("monitoring.log-watchdog.stall-threshold-seconds", 180);
                changed = true;
            }

            plugin.getLogger().info("Applied configuration migration -> v2 (monitoring watchdog defaults).");
        }

        if (currentVersion < 3) {
            if (!config.contains("monitoring.admin-alerts.broadcast-messages")) {
                config.set("monitoring.admin-alerts.broadcast-messages", true);
                changed = true;
            }
            if (!config.contains("monitoring.admin-alerts.actionbar-enabled")) {
                config.set("monitoring.admin-alerts.actionbar-enabled", false);
                changed = true;
            }
            if (!config.contains("monitoring.structured-logging.enabled")) {
                config.set("monitoring.structured-logging.enabled", false);
                changed = true;
            }
            plugin.getLogger().info("Applied configuration migration -> v3 (admin alerts & structured logging toggles).");
        }

        if (currentVersion < CONFIG_VERSION) {
            config.set("config-version", CONFIG_VERSION);
            changed = true;
        }

        if (changed) {
            plugin.saveConfig();
        }
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
        if (teamId == null || teamId.isEmpty()) {
            plugin.getLogger().warning("No team ID configured! Please set one in config.yml");
        }
    }
    
    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }
}