package com.thijs226.fahdonor.environment;

import com.thijs226.fahdonor.FAHResourceDonor;
import com.thijs226.fahdonor.environment.ServerEnvironmentDetector.*;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Platform-aware resource manager that respects environment-specific limits
 * and provides safe resource allocation across different hosting environments.
 */
public class PlatformResourceManager {
    
    private final FAHResourceDonor plugin;
    private final EnvironmentInfo environmentInfo;
    private final ResourceLimits resourceLimits;
    private final boolean enforceStrictLimits;
    
    public PlatformResourceManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        this.environmentInfo = ServerEnvironmentDetector.detectEnvironment();
        this.resourceLimits = ServerEnvironmentDetector.getResourceLimits(environmentInfo);
        this.enforceStrictLimits = resourceLimits.isStrict();
        
        logEnvironmentInfo();
        validateConfiguration();
    }
    
    /**
     * Logs detected environment information
     */
    private void logEnvironmentInfo() {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("DETECTED ENVIRONMENT: " + environmentInfo.getType().getDisplayName());
        
        if (environmentInfo.isContainerized()) {
            plugin.getLogger().info("Running in containerized environment");
        }
        
        if (environmentInfo.isResourceRestricted()) {
            plugin.getLogger().info("Resource restrictions detected - using conservative limits");
        }
        
        plugin.getLogger().info("Resource Limits:");
        plugin.getLogger().info("  Max Cores: " + resourceLimits.getMaxCores());
        plugin.getLogger().info("  Recommended Cores: " + resourceLimits.getRecommendedCores());
        plugin.getLogger().info("  Max Memory: " + resourceLimits.getMaxMemoryMB() + "MB");
        
        // Log specific environment details
        switch (environmentInfo.getType()) {
            case PTERODACTYL:
                logPterodactylInfo();
                break;
            case DOCKER:
                logDockerInfo();
                break;
            case SHARED_HOSTING:
                logSharedHostingInfo();
                break;
            case VPS_DEDICATED:
                plugin.getLogger().info("VPS/Dedicated server detected - full resource access available");
                break;
        }
        
        plugin.getLogger().info("========================================");
    }
    
    private void logPterodactylInfo() {
        plugin.getLogger().info("Pterodactyl Panel detected:");
        String serverUuid = environmentInfo.getMetadata("server_uuid");
        if (serverUuid != null) {
            plugin.getLogger().info("  Server UUID: " + serverUuid);
        }
        String memLimit = environmentInfo.getMetadata("memory_limit_mb");
        if (memLimit != null) {
            plugin.getLogger().info("  Memory Limit: " + memLimit + "MB");
        }
        plugin.getLogger().info("  Using conservative resource allocation for panel compatibility");
    }
    
    private void logDockerInfo() {
        plugin.getLogger().info("Docker container detected:");
        String containerId = environmentInfo.getMetadata("container_id");
        if (containerId != null) {
            plugin.getLogger().info("  Container ID: " + containerId);
        }
        String memLimit = environmentInfo.getMetadata("memory_limit_mb");
        if (memLimit != null) {
            plugin.getLogger().info("  Memory Limit: " + memLimit + "MB");
        }
        plugin.getLogger().info("  Respecting container resource limits");
    }
    
    private void logSharedHostingInfo() {
        plugin.getLogger().warning("Shared hosting environment detected:");
        plugin.getLogger().warning("  Using minimal resource allocation to prevent host issues");
        plugin.getLogger().warning("  FAH will use only 1 CPU core maximum");
        plugin.getLogger().warning("  Memory usage capped at 512MB");
        plugin.getLogger().warning("  Consider upgrading to VPS for better FAH performance");
    }
    
    /**
     * Validates and adjusts configuration based on environment
     */
    private void validateConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        // Get current configuration values
        int configuredTotalCores = config.getInt("server.total-cores", 8);
        int configuredReservedCores = config.getInt("server.reserved-cores", 1);
        
        // Apply environment-specific adjustments
        int safeTotalCores = Math.min(configuredTotalCores, resourceLimits.getMaxCores() + 1);
        int safeReservedCores = Math.max(configuredReservedCores, 1);
        
        boolean configChanged = false;
        
        // Update total cores if needed
        if (configuredTotalCores > safeTotalCores) {
            plugin.getLogger().warning("Configured total cores (" + configuredTotalCores + 
                                     ") exceeds environment limits. Adjusting to " + safeTotalCores);
            config.set("server.total-cores", safeTotalCores);
            configChanged = true;
        }
        
        // Ensure reserved cores don't exceed total cores
        if (safeReservedCores >= safeTotalCores) {
            safeReservedCores = Math.max(1, safeTotalCores - 1);
            plugin.getLogger().warning("Adjusting reserved cores to " + safeReservedCores + 
                                     " to ensure FAH can use at least 1 core");
            config.set("server.reserved-cores", safeReservedCores);
            configChanged = true;
        }
        
        // Apply environment-specific port settings
        applyEnvironmentPortSettings(config);
        
        // Save configuration if modified
        if (configChanged) {
            plugin.saveConfig();
            plugin.getLogger().info("Configuration automatically adjusted for environment compatibility");
        }
    }
    
    /**
     * Applies environment-specific port configuration
     */
    private void applyEnvironmentPortSettings(FileConfiguration config) {
        switch (environmentInfo.getType()) {
            case PTERODACTYL:
            case SHARED_HOSTING:
                // Disable ports for restrictive environments
                if (config.getInt("folding-at-home.ports.control-port", 36330) != 0) {
                    plugin.getLogger().info("Disabling FAH control port for " + 
                                          environmentInfo.getType().getDisplayName() + " compatibility");
                    config.set("folding-at-home.ports.control-port", 0);
                }
                if (config.getInt("folding-at-home.ports.web-port", 7396) != 0) {
                    plugin.getLogger().info("Disabling FAH web port for " + 
                                          environmentInfo.getType().getDisplayName() + " compatibility");
                    config.set("folding-at-home.ports.web-port", 0);
                }
                config.set("folding-at-home.ports.no-port-mode", "file-based");
                break;
            case DOCKER:
                // Docker may or may not allow additional ports - be conservative
                plugin.getLogger().info("Docker environment: Consider mapping FAH ports in container configuration");
                break;
            case VPS_DEDICATED:
                // Full port access available
                break;
        }
    }
    
    /**
     * Calculates safe core allocation for FAH based on current player count and environment
     */
    public int calculateFAHCores(int playerCount) {
        FileConfiguration config = plugin.getConfig();
        
        // Get configuration values
        int totalCores = config.getInt("server.total-cores", 8);
        int reservedCores = config.getInt("server.reserved-cores", 1);
        String mode = config.getString("allocation.mode", "dynamic");
        
        int fahCores;
        
        switch (mode.toLowerCase()) {
            case "dynamic":
                fahCores = calculateDynamicCores(playerCount, totalCores, reservedCores, config);
                break;
            case "tiered":
                fahCores = calculateTieredCores(playerCount, config);
                break;
            case "percentage":
                fahCores = calculatePercentageCores(playerCount, totalCores, reservedCores, config);
                break;
            default:
                fahCores = calculateDynamicCores(playerCount, totalCores, reservedCores, config);
        }
        
        // Apply environment-specific limits
        fahCores = Math.min(fahCores, resourceLimits.getMaxCores());
        
        // Ensure we don't exceed safe limits in restricted environments
        if (enforceStrictLimits) {
            fahCores = Math.min(fahCores, resourceLimits.getRecommendedCores());
        }
        
        // Always ensure at least 1 core remains for Minecraft
        fahCores = Math.min(fahCores, totalCores - 1);
        
        return Math.max(0, fahCores);
    }
    
    private int calculateDynamicCores(int playerCount, int totalCores, int reservedCores, FileConfiguration config) {
        double coresPerPlayer = config.getDouble("allocation.dynamic.cores-per-player", 0.5);
        int minCoresForMC = config.getInt("allocation.dynamic.min-cores-for-minecraft", 1);
        int maxCoresForFAH = config.getInt("allocation.dynamic.max-cores-for-fah", 7);
        
        double neededForMC = reservedCores + (playerCount * coresPerPlayer);
        neededForMC = Math.max(neededForMC, minCoresForMC);
        
        int availableForFAH = (int) Math.floor(totalCores - neededForMC);
        return Math.min(Math.max(0, availableForFAH), maxCoresForFAH);
    }
    
    private int calculateTieredCores(int playerCount, FileConfiguration config) {
        // Implementation for tiered allocation would go here
        // For now, fall back to dynamic
        return calculateDynamicCores(playerCount, 
                                   config.getInt("server.total-cores", 8),
                                   config.getInt("server.reserved-cores", 1),
                                   config);
    }
    
    private int calculatePercentageCores(int playerCount, int totalCores, int reservedCores, FileConfiguration config) {
        // Implementation for percentage allocation would go here
        // For now, fall back to dynamic
        return calculateDynamicCores(playerCount, totalCores, reservedCores, config);
    }
    
    /**
     * Checks if the environment supports the requested resource allocation
     */
    public boolean isResourceAllocationSafe(int requestedCores, long requestedMemoryMB) {
        if (requestedCores > resourceLimits.getMaxCores()) {
            plugin.getLogger().warning("Requested cores (" + requestedCores + 
                                     ") exceeds environment limit (" + resourceLimits.getMaxCores() + ")");
            return false;
        }
        
        if (requestedMemoryMB > resourceLimits.getMaxMemoryMB()) {
            plugin.getLogger().warning("Requested memory (" + requestedMemoryMB + 
                                     "MB) exceeds environment limit (" + resourceLimits.getMaxMemoryMB() + "MB)");
            return false;
        }
        
        // In strict environments, use recommended limits
        if (enforceStrictLimits) {
            if (requestedCores > resourceLimits.getRecommendedCores()) {
                plugin.getLogger().info("Limiting cores to recommended value (" + 
                                      resourceLimits.getRecommendedCores() + 
                                      ") for environment stability");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Gets environment-specific warnings or recommendations
     */
    public void logEnvironmentRecommendations() {
        switch (environmentInfo.getType()) {
            case PTERODACTYL:
                plugin.getLogger().info("Pterodactyl Panel Tips:");
                plugin.getLogger().info("  - FAH will automatically restart when changing core allocation");
                plugin.getLogger().info("  - Web interface disabled for panel compatibility");
                plugin.getLogger().info("  - Monitor server performance and adjust if needed");
                break;
                
            case DOCKER:
                plugin.getLogger().info("Docker Container Tips:");
                plugin.getLogger().info("  - Ensure adequate CPU and memory limits in container config");
                plugin.getLogger().info("  - Consider mounting /tmp as tmpfs for better performance");
                plugin.getLogger().info("  - Map ports 36330 and 7396 if you want FAH web access");
                break;
                
            case SHARED_HOSTING:
                plugin.getLogger().warning("Shared Hosting Limitations:");
                plugin.getLogger().warning("  - Only 1 CPU core will be used to avoid host issues");
                plugin.getLogger().warning("  - FAH performance will be limited");
                plugin.getLogger().warning("  - Consider upgrading to VPS for better contribution potential");
                plugin.getLogger().warning("  - Monitor for any hosting provider warnings");
                break;
                
            case VPS_DEDICATED:
                plugin.getLogger().info("VPS/Dedicated Server Tips:");
                plugin.getLogger().info("  - Full FAH features available");
                plugin.getLogger().info("  - Consider adjusting allocation based on server usage patterns");
                plugin.getLogger().info("  - Web interface available at http://yourserver:7396");
                break;
        }
    }
    
    // Getters
    public EnvironmentInfo getEnvironmentInfo() { return environmentInfo; }
    public ResourceLimits getResourceLimits() { return resourceLimits; }
    public boolean isStrictLimitsEnforced() { return enforceStrictLimits; }
}