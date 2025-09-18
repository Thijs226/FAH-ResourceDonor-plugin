package com.thijs226.fahdonor.environment;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Detects the server environment to enable platform-specific optimizations
 * and safety features for resource allocation.
 */
public class ServerEnvironmentDetector {
    
    public enum EnvironmentType {
        PTERODACTYL("Pterodactyl Panel"),
        DOCKER("Docker Container"),
        SHARED_HOSTING("Shared Hosting"),
        VPS_DEDICATED("VPS/Dedicated Server"),
        UNKNOWN("Unknown Environment");
        
        private final String displayName;
        
        EnvironmentType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public static class EnvironmentInfo {
        private final EnvironmentType type;
        private final Map<String, String> metadata;
        private final boolean containerized;
        private final boolean resourceRestricted;
        
        public EnvironmentInfo(EnvironmentType type, Map<String, String> metadata, 
                             boolean containerized, boolean resourceRestricted) {
            this.type = type;
            this.metadata = new HashMap<>(metadata);
            this.containerized = containerized;
            this.resourceRestricted = resourceRestricted;
        }
        
        public EnvironmentType getType() { return type; }
        public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }
        public boolean isContainerized() { return containerized; }
        public boolean isResourceRestricted() { return resourceRestricted; }
        
        public String getMetadata(String key) { return metadata.get(key); }
    }
    
    private static final Logger logger = Logger.getLogger(ServerEnvironmentDetector.class.getName());
    
    /**
     * Detects the current server environment
     */
    public static EnvironmentInfo detectEnvironment() {
        Map<String, String> metadata = new HashMap<>();
        
        // Check for Pterodactyl Panel
        if (isPterodactyl()) {
            metadata.put("panel_type", "pterodactyl");
            collectPterodactylMetadata(metadata);
            return new EnvironmentInfo(EnvironmentType.PTERODACTYL, metadata, true, true);
        }
        
        // Check for Docker
        if (isDocker()) {
            metadata.put("container_type", "docker");
            collectDockerMetadata(metadata);
            return new EnvironmentInfo(EnvironmentType.DOCKER, metadata, true, true);
        }
        
        // Check for shared hosting indicators
        if (isSharedHosting()) {
            metadata.put("hosting_type", "shared");
            collectSharedHostingMetadata(metadata);
            return new EnvironmentInfo(EnvironmentType.SHARED_HOSTING, metadata, false, true);
        }
        
        // Default to VPS/Dedicated
        collectSystemMetadata(metadata);
        return new EnvironmentInfo(EnvironmentType.VPS_DEDICATED, metadata, false, false);
    }
    
    /**
     * Checks if running on Pterodactyl Panel
     */
    private static boolean isPterodactyl() {
        // Check for Pterodactyl environment variables
        String serverUuid = System.getenv("P_SERVER_UUID");
        String serverLocation = System.getenv("P_SERVER_LOCATION");
        
        if (serverUuid != null || serverLocation != null) {
            return true;
        }
        
        // Check for .pteroignore file or pterodactyl-specific files
        String workingDir = System.getProperty("user.dir");
        return Files.exists(Paths.get(workingDir, ".pteroignore"));
    }
    
    /**
     * Checks if running in Docker container
     */
    private static boolean isDocker() {
        // Check for Docker-specific files
        if (Files.exists(Paths.get("/.dockerenv"))) {
            return true;
        }
        
        // Check cgroup for docker
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/1/cgroup"));
            for (String line : lines) {
                if (line.contains("docker") || line.contains("containerd")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore - not critical
        }
        
        // Check environment variables
        return System.getenv("DOCKER_CONTAINER") != null;
    }
    
    /**
     * Checks if running on shared hosting
     */
    private static boolean isSharedHosting() {
        // Check for common shared hosting indicators
        String javaHome = System.getProperty("java.home");
        String userDir = System.getProperty("user.dir");
        String userName = System.getProperty("user.name");
        
        // Common shared hosting patterns
        if (javaHome != null && (javaHome.contains("shared") || javaHome.contains("cpanel"))) {
            return true;
        }
        
        if (userDir != null && userDir.contains("public_html")) {
            return true;
        }
        
        // Check for limited write permissions
        try {
            String tmpDir = System.getProperty("java.io.tmpdir");
            if (tmpDir != null && !Files.isWritable(Paths.get(tmpDir))) {
                return true;
            }
        } catch (Exception e) {
            // Might indicate restricted environment
            return true;
        }
        
        // Check for common shared hosting usernames
        if (userName != null && (userName.startsWith("u") || userName.length() > 10)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Collects Pterodactyl-specific metadata
     */
    private static void collectPterodactylMetadata(Map<String, String> metadata) {
        metadata.put("server_uuid", System.getenv("P_SERVER_UUID"));
        metadata.put("server_location", System.getenv("P_SERVER_LOCATION"));
        metadata.put("server_memory", System.getenv("SERVER_MEMORY"));
        metadata.put("server_jarfile", System.getenv("SERVER_JARFILE"));
        
        // Check for memory limits
        String memoryEnv = System.getenv("SERVER_MEMORY");
        if (memoryEnv != null) {
            metadata.put("memory_limit_mb", memoryEnv);
        }
    }
    
    /**
     * Collects Docker-specific metadata
     */
    private static void collectDockerMetadata(Map<String, String> metadata) {
        // Read Docker container info
        try {
            // Try to read container ID
            List<String> lines = Files.readAllLines(Paths.get("/proc/self/cgroup"));
            for (String line : lines) {
                if (line.contains("docker/")) {
                    String[] parts = line.split("/");
                    if (parts.length > 0) {
                        String containerId = parts[parts.length - 1];
                        if (containerId.length() >= 12) {
                            metadata.put("container_id", containerId.substring(0, 12));
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // Check for memory limits in cgroup
        try {
            String memLimit = Files.readString(Paths.get("/sys/fs/cgroup/memory/memory.limit_in_bytes"));
            if (!memLimit.trim().equals("9223372036854775807")) { // Not unlimited
                long limitBytes = Long.parseLong(memLimit.trim());
                metadata.put("memory_limit_mb", String.valueOf(limitBytes / (1024 * 1024)));
            }
        } catch (Exception e) {
            // Try newer cgroup v2 format
            try {
                String memMax = Files.readString(Paths.get("/sys/fs/cgroup/memory.max"));
                if (!memMax.trim().equals("max")) {
                    long limitBytes = Long.parseLong(memMax.trim());
                    metadata.put("memory_limit_mb", String.valueOf(limitBytes / (1024 * 1024)));
                }
            } catch (Exception e2) {
                // Ignore
            }
        }
    }
    
    /**
     * Collects shared hosting metadata
     */
    private static void collectSharedHostingMetadata(Map<String, String> metadata) {
        metadata.put("user_name", System.getProperty("user.name"));
        metadata.put("user_dir", System.getProperty("user.dir"));
        metadata.put("java_home", System.getProperty("java.home"));
        
        // Check available memory
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        metadata.put("max_memory_mb", String.valueOf(maxMemory / (1024 * 1024)));
    }
    
    /**
     * Collects general system metadata
     */
    private static void collectSystemMetadata(Map<String, String> metadata) {
        Runtime runtime = Runtime.getRuntime();
        
        metadata.put("available_processors", String.valueOf(runtime.availableProcessors()));
        metadata.put("max_memory_mb", String.valueOf(runtime.maxMemory() / (1024 * 1024)));
        metadata.put("os_name", System.getProperty("os.name"));
        metadata.put("os_arch", System.getProperty("os.arch"));
        metadata.put("java_version", System.getProperty("java.version"));
    }
    
    /**
     * Gets safe resource limits for the detected environment
     */
    public static ResourceLimits getResourceLimits(EnvironmentInfo envInfo) {
        Runtime runtime = Runtime.getRuntime();
        int availableProcessors = runtime.availableProcessors();
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        
        switch (envInfo.getType()) {
            case PTERODACTYL:
                return getPterodactylLimits(envInfo, availableProcessors, maxMemoryMB);
            case DOCKER:
                return getDockerLimits(envInfo, availableProcessors, maxMemoryMB);
            case SHARED_HOSTING:
                return getSharedHostingLimits(envInfo, availableProcessors, maxMemoryMB);
            default:
                return getDefaultLimits(availableProcessors, maxMemoryMB);
        }
    }
    
    private static ResourceLimits getPterodactylLimits(EnvironmentInfo envInfo, int processors, long memoryMB) {
        // Pterodactyl often has strict limits - be very conservative
        int maxCores = Math.max(1, processors - 1); // Always reserve 1 core
        int recommendedCores = Math.max(1, processors / 2); // Use at most half
        
        String memLimit = envInfo.getMetadata("memory_limit_mb");
        if (memLimit != null) {
            try {
                long limitMB = Long.parseLong(memLimit);
                memoryMB = Math.min(memoryMB, limitMB);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        return new ResourceLimits(maxCores, recommendedCores, memoryMB, true);
    }
    
    private static ResourceLimits getDockerLimits(EnvironmentInfo envInfo, int processors, long memoryMB) {
        // Docker containers may have resource limits
        int maxCores = Math.max(1, processors - 1);
        int recommendedCores = Math.max(1, (int)(processors * 0.7)); // Use 70%
        
        String memLimit = envInfo.getMetadata("memory_limit_mb");
        if (memLimit != null) {
            try {
                long limitMB = Long.parseLong(memLimit);
                memoryMB = Math.min(memoryMB, limitMB);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        return new ResourceLimits(maxCores, recommendedCores, memoryMB, true);
    }
    
    private static ResourceLimits getSharedHostingLimits(EnvironmentInfo envInfo, int processors, long memoryMB) {
        // Shared hosting - be extremely conservative
        int maxCores = 1; // Only allow 1 core on shared hosting
        int recommendedCores = 1;
        
        // Use much less memory on shared hosting
        long safeMem = Math.min(memoryMB, 512); // Cap at 512MB
        
        return new ResourceLimits(maxCores, recommendedCores, safeMem, true);
    }
    
    private static ResourceLimits getDefaultLimits(int processors, long memoryMB) {
        // VPS/Dedicated - more liberal limits
        int maxCores = Math.max(1, processors - 1);
        int recommendedCores = Math.max(2, (int)(processors * 0.8)); // Use 80%
        
        return new ResourceLimits(maxCores, recommendedCores, memoryMB, false);
    }
    
    public static class ResourceLimits {
        private final int maxCores;
        private final int recommendedCores;
        private final long maxMemoryMB;
        private final boolean strict;
        
        public ResourceLimits(int maxCores, int recommendedCores, long maxMemoryMB, boolean strict) {
            this.maxCores = maxCores;
            this.recommendedCores = recommendedCores;
            this.maxMemoryMB = maxMemoryMB;
            this.strict = strict;
        }
        
        public int getMaxCores() { return maxCores; }
        public int getRecommendedCores() { return recommendedCores; }
        public long getMaxMemoryMB() { return maxMemoryMB; }
        public boolean isStrict() { return strict; }
    }
}