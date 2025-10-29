package com.thijs226.fahdonor.health;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.thijs226.fahdonor.FAHResourceDonor;

/**
 * Monitors the health of the Folding@home client and performs automatic
 * recovery actions when issues are detected.
 */
public class HealthMonitor {
    
    private final FAHResourceDonor plugin;
    private BukkitTask monitorTask;
    
    // Health check configuration
    private boolean autoRecoveryEnabled = true;
    private int checkIntervalSeconds = 60;
    private int maxRecoveryAttempts = 3;
    
    // Health tracking
    private final AtomicInteger consecutiveFailedChecks = new AtomicInteger(0);
    private final AtomicInteger recoveryAttempts = new AtomicInteger(0);
    private final AtomicReference<Instant> lastSuccessfulCheck = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastRecoveryAttempt = new AtomicReference<>(null);
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicReference<String> lastHealthIssue = new AtomicReference<>("");
    
    private final List<HealthIssue> recentIssues = new ArrayList<>();
    
    public HealthMonitor(FAHResourceDonor plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }
    
    public void start() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        
        monitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                performHealthCheck();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 30L, 20L * checkIntervalSeconds);
        
        plugin.getLogger().info(() -> "Health monitor started (checking every " + checkIntervalSeconds + " seconds)");
    }
    
    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }
    }
    
    private void performHealthCheck() {
        boolean healthy = true;
        StringBuilder issues = new StringBuilder();
        
        try {
            // Check 1: FAH Client is running
            if (plugin.getFAHManager() != null && !plugin.getFAHManager().isFAHRunning()) {
                healthy = false;
                issues.append("FAH client not running; ");
                recordIssue(HealthIssueType.CLIENT_NOT_RUNNING, "FAH client process not detected");
            }
            
                // Check 2: Recent activity
            if (plugin.getFAHClient() != null) {
                long lastUpdate = plugin.getFAHClient().getLastStatusUpdateEpochSeconds();
                long now = System.currentTimeMillis() / 1000;
                long stalledThreshold = plugin.getConfig()
                    .getLong("health-monitoring.stalled-threshold-seconds", 300);
                
                if (lastUpdate > 0 && (now - lastUpdate) > stalledThreshold) {
                    healthy = false;
                    issues.append(String.format("No updates for %d seconds; ", now - lastUpdate));
                    recordIssue(HealthIssueType.STALLED_ACTIVITY, 
                        String.format("No activity for %d seconds", now - lastUpdate));
                }
                
                // Check 3: Excessive failures
                int consecutive = plugin.getFAHClient().getConsecutiveFailures();
                
                if (consecutive >= 3) {
                    healthy = false;
                    issues.append(String.format("%d consecutive failures; ", consecutive));
                    recordIssue(HealthIssueType.EXCESSIVE_FAILURES, 
                        String.format("%d consecutive work unit failures", consecutive));
                }
                
                // Check 4: Auto-restart suppression
                if (plugin.getFAHClient().isAutoRestartSuppressed()) {
                    healthy = false;
                    issues.append("Auto-restart suppressed; ");
                    recordIssue(HealthIssueType.AUTO_RESTART_SUPPRESSED, 
                        "Automatic restart has been suppressed due to repeated failures");
                }
            }
            
            // Update health status
            if (healthy) {
                handleHealthyState();
            } else {
                handleUnhealthyState(issues.toString());
            }
            
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Error during health check", e);
            recordIssue(HealthIssueType.HEALTH_CHECK_ERROR, e.getMessage());
        }
    }
    
    private void handleHealthyState() {
        consecutiveFailedChecks.set(0);
        lastSuccessfulCheck.set(Instant.now());
        
        if (!isHealthy.getAndSet(true)) {
            // Recovery successful
            recoveryAttempts.set(0);
            plugin.getLogger().info("FAH client health restored!");
            plugin.notifyAdmins("FAH health monitor: System recovered and operating normally", 
                ChatColor.GREEN, true);
        }
        
        lastHealthIssue.set("");
    }
    
    private void handleUnhealthyState(String issues) {
        int failedChecks = consecutiveFailedChecks.incrementAndGet();
        isHealthy.set(false);
        lastHealthIssue.set(issues);
        
        plugin.getLogger().warning(() -> "Health check failed: " + issues);        // Attempt recovery after multiple failed checks
        int threshold = plugin.getConfig().getInt("health-monitoring.recovery-threshold", 3);
        if (failedChecks >= threshold && autoRecoveryEnabled) {
            attemptRecovery(issues);
        } else if (failedChecks >= threshold) {
            plugin.notifyAdmins(
                String.format("FAH health issues detected: %s (Auto-recovery disabled)", issues),
                ChatColor.RED, true);
        }
    }
    
    private void attemptRecovery(String issues) {
        int attempts = recoveryAttempts.get();
        
        if (attempts >= maxRecoveryAttempts) {
            plugin.getLogger().severe("Max recovery attempts reached. Manual intervention required.");
            plugin.notifyAdmins(
                String.format("FAH CRITICAL: Max recovery attempts reached! Issues: %s", issues),
                ChatColor.DARK_RED, true);
            return;
        }
        
        Instant lastAttempt = lastRecoveryAttempt.get();
        if (lastAttempt != null && Duration.between(lastAttempt, Instant.now()).toMinutes() < 5) {
            return; // Wait at least 5 minutes between recovery attempts
        }
        
        recoveryAttempts.incrementAndGet();
        lastRecoveryAttempt.set(Instant.now());
        
        plugin.getLogger().warning(String.format("Attempting automatic recovery (attempt %d/%d)...", 
            attempts + 1, maxRecoveryAttempts));
        plugin.notifyAdmins(
            String.format("FAH auto-recovery initiated (attempt %d/%d)", attempts + 1, maxRecoveryAttempts),
            ChatColor.YELLOW, true);
        
        // Perform recovery actions
        performRecoveryActions();
    }
    
    private void performRecoveryActions() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Action 1: Reset failure counters in FAH client
                    if (plugin.getFAHClient() != null) {
                        plugin.getLogger().info("Recovery step 1: Resuming FAH client...");
                        plugin.getFAHClient().resume();
                        Thread.sleep(2000);
                    }
                    
                    // Action 2: Restart FAH manager
                    if (plugin.getFAHManager() != null) {
                        plugin.getLogger().info("Recovery step 2: Restarting FAH manager...");
                        plugin.getFAHManager().forceStart();
                        Thread.sleep(3000);
                        
                        // Action 3: Request new work
                        plugin.getLogger().info("Recovery step 3: Requesting new work unit...");
                        plugin.getFAHClient().requestWorkUnit();
                    }
                    
                    plugin.getLogger().info("Recovery actions completed. Monitoring for improvement...");
                    
                    // Schedule a follow-up check in 2 minutes
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            performHealthCheck();
                        }
                    }.runTaskLaterAsynchronously(plugin, 20L * 120L);
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error during recovery actions", e);
                    plugin.notifyAdmins("FAH recovery failed: " + e.getMessage(), 
                        ChatColor.RED, true);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    private void recordIssue(HealthIssueType type, String description) {
        HealthIssue issue = new HealthIssue(type, description);
        synchronized (recentIssues) {
            recentIssues.add(issue);
            // Keep only last 50 issues
            while (recentIssues.size() > 50) {
                recentIssues.remove(0);
            }
        }
    }
    
    public boolean isHealthy() {
        return isHealthy.get();
    }
    
    public String getLastHealthIssue() {
        return lastHealthIssue.get();
    }
    
    public int getConsecutiveFailedChecks() {
        return consecutiveFailedChecks.get();
    }
    
    public int getRecoveryAttempts() {
        return recoveryAttempts.get();
    }
    
    public List<HealthIssue> getRecentIssues() {
        synchronized (recentIssues) {
            return new ArrayList<>(recentIssues);
        }
    }
    
    public String getHealthReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== FAH Health Status ===\n\n");
        report.append(String.format("Overall Status: %s\n", isHealthy.get() ? "HEALTHY" : "UNHEALTHY"));
        report.append(String.format("Failed Checks: %d\n", consecutiveFailedChecks.get()));
        report.append(String.format("Recovery Attempts: %d/%d\n", recoveryAttempts.get(), maxRecoveryAttempts));
        
        if (!lastHealthIssue.get().isEmpty()) {
            report.append(String.format("Current Issues: %s\n", lastHealthIssue.get()));
        }
        
        Duration timeSinceSuccess = Duration.between(lastSuccessfulCheck.get(), Instant.now());
        report.append(String.format("Last Successful Check: %d minutes ago\n", timeSinceSuccess.toMinutes()));
        
        Instant lastRecovery = lastRecoveryAttempt.get();
        if (lastRecovery != null) {
            Duration timeSinceRecovery = Duration.between(lastRecovery, Instant.now());
            report.append(String.format("Last Recovery Attempt: %d minutes ago\n", timeSinceRecovery.toMinutes()));
        }
        
        report.append("\nRecent Issues:\n");
        List<HealthIssue> issues = getRecentIssues();
        if (issues.isEmpty()) {
            report.append("  None\n");
        } else {
            int shown = Math.min(10, issues.size());
            for (int i = issues.size() - shown; i < issues.size(); i++) {
                HealthIssue issue = issues.get(i);
                report.append(String.format("  [%s] %s: %s\n", 
                    issue.getTimestamp(), issue.getType(), issue.getDescription()));
            }
        }
        
        return report.toString();
    }
    
    public void setAutoRecoveryEnabled(boolean enabled) {
        this.autoRecoveryEnabled = enabled;
        saveConfiguration();
    }
    
    public boolean isAutoRecoveryEnabled() {
        return autoRecoveryEnabled;
    }
    
    private void loadConfiguration() {
        var config = plugin.getConfig();
        autoRecoveryEnabled = config.getBoolean("health-monitoring.auto-recovery", true);
        checkIntervalSeconds = config.getInt("health-monitoring.check-interval-seconds", 60);
        maxRecoveryAttempts = config.getInt("health-monitoring.max-recovery-attempts", 3);
    }
    
    private void saveConfiguration() {
        var config = plugin.getConfig();
        config.set("health-monitoring.auto-recovery", autoRecoveryEnabled);
        config.set("health-monitoring.check-interval-seconds", checkIntervalSeconds);
        config.set("health-monitoring.max-recovery-attempts", maxRecoveryAttempts);
        plugin.saveConfig();
    }
    
    public enum HealthIssueType {
        CLIENT_NOT_RUNNING,
        STALLED_ACTIVITY,
        EXCESSIVE_FAILURES,
        AUTO_RESTART_SUPPRESSED,
        HEALTH_CHECK_ERROR,
        OTHER
    }
    
    public static class HealthIssue {
        private final HealthIssueType type;
        private final String description;
        private final Instant timestamp;
        
        public HealthIssue(HealthIssueType type, String description) {
            this.type = type;
            this.description = description;
            this.timestamp = Instant.now();
        }
        
        public HealthIssueType getType() {
            return type;
        }
        
        public String getDescription() {
            return description;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
}
