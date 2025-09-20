package com.thijs226.fahdonor.environment;

import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;

import com.thijs226.fahdonor.FAHResourceDonor;
import com.thijs226.fahdonor.environment.ServerEnvironmentDetector.*;
import com.thijs226.fahdonor.environment.ServerEnvironmentDetector.EnvironmentInfo;
import com.thijs226.fahdonor.environment.ServerEnvironmentDetector.ResourceLimits;

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
        plugin.getLogger().info(() -> String.format("DETECTED ENVIRONMENT: %s", environmentInfo.getType().getDisplayName()));
        
        if (environmentInfo.isContainerized()) {
            plugin.getLogger().info("Running in containerized environment");
        }
        
        if (environmentInfo.isResourceRestricted()) {
            plugin.getLogger().info("Resource restrictions detected - using conservative limits");
        }
        
        plugin.getLogger().info("Resource Limits:");
        plugin.getLogger().info(() -> String.format("  Max Cores: %d", resourceLimits.getMaxCores()));
        plugin.getLogger().info(() -> String.format("  Recommended Cores: %d", resourceLimits.getRecommendedCores()));
        plugin.getLogger().info(() -> String.format("  Max Memory: %dMB", resourceLimits.getMaxMemoryMB()));
        
        // Log specific environment details
        switch (environmentInfo.getType()) {
            case PTERODACTYL -> logPterodactylInfo();
            case DOCKER -> logDockerInfo();
            case SHARED_HOSTING -> logSharedHostingInfo();
            case VPS_DEDICATED -> plugin.getLogger().info("VPS/Dedicated server detected - full resource access available");
            case UNKNOWN -> { /* no extra details */ }
        }
        
        plugin.getLogger().info("========================================");
    }
    
    private void logPterodactylInfo() {
        plugin.getLogger().info("Pterodactyl Panel detected:");
        String serverUuid = environmentInfo.getMetadata("server_uuid");
        if (serverUuid != null) {
            final String uuid = serverUuid;
            plugin.getLogger().info(() -> String.format("  Server UUID: %s", uuid));
        }
        String memLimit = environmentInfo.getMetadata("memory_limit_mb");
        if (memLimit != null) {
            final String ml = memLimit;
            plugin.getLogger().info(() -> String.format("  Memory Limit: %sMB", ml));
        }
        plugin.getLogger().info("  Using conservative resource allocation for panel compatibility");
    }
    
    private void logDockerInfo() {
        plugin.getLogger().info("Docker container detected:");
        String containerId = environmentInfo.getMetadata("container_id");
        if (containerId != null) {
            final String cid = containerId;
            plugin.getLogger().info(() -> String.format("  Container ID: %s", cid));
        }
        String memLimit = environmentInfo.getMetadata("memory_limit_mb");
        if (memLimit != null) {
            final String ml = memLimit;
            plugin.getLogger().info(() -> String.format("  Memory Limit: %sMB", ml));
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
            final int ctc = configuredTotalCores;
            final int stc = safeTotalCores;
            plugin.getLogger().warning(() -> String.format("Configured total cores (%d) exceeds environment limits. Adjusting to %d", ctc, stc));
            config.set("server.total-cores", safeTotalCores);
            configChanged = true;
        }
        
        // Ensure reserved cores don't exceed total cores
        if (safeReservedCores >= safeTotalCores) {
            safeReservedCores = Math.max(1, safeTotalCores - 1);
            final int src = safeReservedCores;
            plugin.getLogger().warning(() -> String.format("Adjusting reserved cores to %d to ensure FAH can use at least 1 core", src));
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
            case PTERODACTYL, SHARED_HOSTING -> {
                // Disable ports for restrictive environments
                if (config.getInt("folding-at-home.ports.control-port", 36330) != 0) {
                    plugin.getLogger().info(() -> String.format("Disabling FAH control port for %s compatibility", environmentInfo.getType().getDisplayName()));
                    config.set("folding-at-home.ports.control-port", 0);
                }
                if (config.getInt("folding-at-home.ports.web-port", 7396) != 0) {
                    plugin.getLogger().info(() -> String.format("Disabling FAH web port for %s compatibility", environmentInfo.getType().getDisplayName()));
                    config.set("folding-at-home.ports.web-port", 0);
                }
                config.set("folding-at-home.ports.no-port-mode", "file-based");
            }
            case DOCKER -> plugin.getLogger().info("Docker environment: Consider mapping FAH ports in container configuration");
            case VPS_DEDICATED, UNKNOWN -> { /* no-op */ }
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
        String modeRaw = config.getString("allocation.mode");
        String mode = (modeRaw == null ? "dynamic" : modeRaw).toLowerCase(Locale.ROOT);
        int fahCores = switch (mode) {
            case "dynamic" -> calculateDynamicCores(playerCount, totalCores, reservedCores, config);
            case "tiered" -> calculateTieredCores(playerCount, config);
            case "percentage" -> calculatePercentageCores(playerCount, totalCores, reservedCores, config);
            default -> calculateDynamicCores(playerCount, totalCores, reservedCores, config);
        };
        
        // Remove environment-imposed max caps to allow auto-detection (user requested "unlimited")
        // Always ensure at least 1 core remains for Minecraft
        fahCores = Math.min(fahCores, totalCores - 1);

        // Enforce FAH minimum: if FAH would use only 1 core, pause instead (return 0)
        if (fahCores == 1) {
            return 0;
        }

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
            final int rc = requestedCores;
            plugin.getLogger().warning(() -> String.format("Requested cores (%d) exceeds environment limit (%d)", rc, resourceLimits.getMaxCores()));
            return false;
        }
        
        if (requestedMemoryMB > resourceLimits.getMaxMemoryMB()) {
            final long rm = requestedMemoryMB;
            plugin.getLogger().warning(() -> String.format("Requested memory (%dMB) exceeds environment limit (%dMB)", rm, resourceLimits.getMaxMemoryMB()));
            return false;
        }
        
        // In strict environments, use recommended limits
        if (enforceStrictLimits) {
            if (requestedCores > resourceLimits.getRecommendedCores()) {
                plugin.getLogger().info(() -> String.format("Limiting cores to recommended value (%d) for environment stability", resourceLimits.getRecommendedCores()));
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
            case PTERODACTYL -> {
                plugin.getLogger().info("Pterodactyl Panel Tips:");
                plugin.getLogger().info("  - FAH will automatically restart when changing core allocation");
                plugin.getLogger().info("  - Web interface disabled for panel compatibility");
                plugin.getLogger().info("  - Monitor server performance and adjust if needed");
            }
            case DOCKER -> {
                plugin.getLogger().info("Docker Container Tips:");
                plugin.getLogger().info("  - Ensure adequate CPU and memory limits in container config");
                plugin.getLogger().info("  - Consider mounting /tmp as tmpfs for better performance");
                plugin.getLogger().info("  - Map ports 36330 and 7396 if you want FAH web access");
            }
            case SHARED_HOSTING -> {
                plugin.getLogger().warning("Shared Hosting Limitations:");
                plugin.getLogger().warning("  - Only 1 CPU core will be used to avoid host issues");
                plugin.getLogger().warning("  - FAH performance will be limited");
                plugin.getLogger().warning("  - Consider upgrading to VPS for better contribution potential");
                plugin.getLogger().warning("  - Monitor for any hosting provider warnings");
            }
            case VPS_DEDICATED -> {
                plugin.getLogger().info("VPS/Dedicated Server Tips:");
                plugin.getLogger().info("  - Full FAH features available");
                plugin.getLogger().info("  - Consider adjusting allocation based on server usage patterns");
                plugin.getLogger().info("  - Web interface available at http://yourserver:7396");
            }
            case UNKNOWN -> { /* no additional tips */ }
        }
    }
    
    // Getters
    public EnvironmentInfo getEnvironmentInfo() { return environmentInfo; }
    public ResourceLimits getResourceLimits() { return resourceLimits; }
    public boolean isStrictLimitsEnforced() { return enforceStrictLimits; }
}