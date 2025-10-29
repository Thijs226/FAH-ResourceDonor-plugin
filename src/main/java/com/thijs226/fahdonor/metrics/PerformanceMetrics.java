package com.thijs226.fahdonor.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.thijs226.fahdonor.FAHResourceDonor;

/**
 * Tracks performance metrics for the Folding@home client including CPU usage,
 * memory consumption, work unit efficiency, and overall health statistics.
 */
public class PerformanceMetrics {
    
    // CPU Metrics
    private final AtomicLong totalCpuTimeMillis = new AtomicLong(0);
    private final AtomicLong fahCpuTimeMillis = new AtomicLong(0);
    private final AtomicReference<Double> currentCpuUsagePercent = new AtomicReference<>(0.0);
    
    // Memory Metrics
    private final AtomicLong peakMemoryUsageMB = new AtomicLong(0);
    private final AtomicLong currentMemoryUsageMB = new AtomicLong(0);
    private final AtomicLong averageMemoryUsageMB = new AtomicLong(0);
    private final AtomicLong memorySampleCount = new AtomicLong(0);
    
    // Work Unit Efficiency
    private final AtomicLong totalWorkUnitsStarted = new AtomicLong(0);
    private final AtomicLong totalWorkUnitsCompleted = new AtomicLong(0);
    private final AtomicLong totalWorkUnitsFailed = new AtomicLong(0);
    private final AtomicLong totalPointsEarned = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeSeconds = new AtomicLong(0);
    
    // Uptime & Session Tracking
    private final AtomicReference<Instant> sessionStartTime = new AtomicReference<>(Instant.now());
    private final AtomicLong totalUptimeSeconds = new AtomicLong(0);
    private final AtomicLong totalDowntimeSeconds = new AtomicLong(0);
    private final AtomicLong restartCount = new AtomicLong(0);
    
    // Health Metrics
    private final AtomicLong consecutiveSuccessfulUnits = new AtomicLong(0);
    private final AtomicLong consecutiveFailedUnits = new AtomicLong(0);
    private final AtomicReference<String> lastHealthStatus = new AtomicReference<>("Initializing");
    private final AtomicLong lastHealthCheckTimestamp = new AtomicLong(System.currentTimeMillis());
    
    public PerformanceMetrics(FAHResourceDonor plugin) {
        // Plugin reference not needed for metrics
    }
    
    // CPU Metrics
    public void updateCpuUsage(double percentUsage) {
        currentCpuUsagePercent.set(percentUsage);
    }
    
    public void addCpuTime(long millis) {
        totalCpuTimeMillis.addAndGet(millis);
    }
    
    public void addFahCpuTime(long millis) {
        fahCpuTimeMillis.addAndGet(millis);
    }
    
    public double getCurrentCpuUsage() {
        return currentCpuUsagePercent.get();
    }
    
    public long getTotalCpuTimeMillis() {
        return totalCpuTimeMillis.get();
    }
    
    public long getFahCpuTimeMillis() {
        return fahCpuTimeMillis.get();
    }
    
    // Memory Metrics
    public void updateMemoryUsage(long usageMB) {
        currentMemoryUsageMB.set(usageMB);
        
        // Update peak
        long currentPeak = peakMemoryUsageMB.get();
        if (usageMB > currentPeak) {
            peakMemoryUsageMB.compareAndSet(currentPeak, usageMB);
        }
        
        // Update rolling average
        long count = memorySampleCount.incrementAndGet();
        long currentAvg = averageMemoryUsageMB.get();
        long newAvg = ((currentAvg * (count - 1)) + usageMB) / count;
        averageMemoryUsageMB.set(newAvg);
    }
    
    public long getCurrentMemoryUsageMB() {
        return currentMemoryUsageMB.get();
    }
    
    public long getPeakMemoryUsageMB() {
        return peakMemoryUsageMB.get();
    }
    
    public long getAverageMemoryUsageMB() {
        return averageMemoryUsageMB.get();
    }
    
    // Work Unit Metrics
    public void recordWorkUnitStarted() {
        totalWorkUnitsStarted.incrementAndGet();
    }
    
    public void recordWorkUnitCompleted(long points, long durationSeconds) {
        totalWorkUnitsCompleted.incrementAndGet();
        totalPointsEarned.addAndGet(points);
        totalProcessingTimeSeconds.addAndGet(durationSeconds);
        consecutiveSuccessfulUnits.incrementAndGet();
        consecutiveFailedUnits.set(0);
    }
    
    public void recordWorkUnitFailed() {
        totalWorkUnitsFailed.incrementAndGet();
        consecutiveFailedUnits.incrementAndGet();
        consecutiveSuccessfulUnits.set(0);
    }
    
    public long getTotalWorkUnitsStarted() {
        return totalWorkUnitsStarted.get();
    }
    
    public long getTotalWorkUnitsCompleted() {
        return totalWorkUnitsCompleted.get();
    }
    
    public long getTotalWorkUnitsFailed() {
        return totalWorkUnitsFailed.get();
    }
    
    public long getTotalPointsEarned() {
        return totalPointsEarned.get();
    }
    
    public double getSuccessRate() {
        long total = totalWorkUnitsCompleted.get() + totalWorkUnitsFailed.get();
        if (total == 0) return 100.0;
        return (totalWorkUnitsCompleted.get() * 100.0) / total;
    }
    
    public double getAveragePointsPerUnit() {
        long completed = totalWorkUnitsCompleted.get();
        if (completed == 0) return 0.0;
        return (double) totalPointsEarned.get() / completed;
    }
    
    public double getPointsPerHour() {
        long totalHours = getTotalUptimeSeconds() / 3600;
        if (totalHours == 0) return 0.0;
        return (double) totalPointsEarned.get() / totalHours;
    }
    
    // Session & Uptime
    public void recordRestart() {
        restartCount.incrementAndGet();
        sessionStartTime.set(Instant.now());
    }
    
    public void addUptime(long seconds) {
        totalUptimeSeconds.addAndGet(seconds);
    }
    
    public void addDowntime(long seconds) {
        totalDowntimeSeconds.addAndGet(seconds);
    }
    
    public long getTotalUptimeSeconds() {
        return totalUptimeSeconds.get();
    }
    
    public long getTotalDowntimeSeconds() {
        return totalDowntimeSeconds.get();
    }
    
    public long getRestartCount() {
        return restartCount.get();
    }
    
    public Duration getSessionDuration() {
        return Duration.between(sessionStartTime.get(), Instant.now());
    }
    
    public double getUptimePercentage() {
        long total = totalUptimeSeconds.get() + totalDowntimeSeconds.get();
        if (total == 0) return 100.0;
        return (totalUptimeSeconds.get() * 100.0) / total;
    }
    
    // Health Status
    public void updateHealthStatus(String status) {
        lastHealthStatus.set(status);
        lastHealthCheckTimestamp.set(System.currentTimeMillis());
    }
    
    public String getHealthStatus() {
        return lastHealthStatus.get();
    }
    
    public long getConsecutiveSuccessfulUnits() {
        return consecutiveSuccessfulUnits.get();
    }
    
    public long getConsecutiveFailedUnits() {
        return consecutiveFailedUnits.get();
    }
    
    public boolean isHealthy() {
        // Consider unhealthy if:
        // - 3+ consecutive failures
        // - Success rate below 50%
        // - No health check in 10 minutes
        if (consecutiveFailedUnits.get() >= 3) return false;
        if (getSuccessRate() < 50.0 && getTotalWorkUnitsCompleted() + getTotalWorkUnitsFailed() > 5) return false;
        return System.currentTimeMillis() - lastHealthCheckTimestamp.get() <= 600_000;
    }
    
    /**
     * Generates a comprehensive performance report
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== FAH Performance Report ===\n\n");
        
        // Session Info
        Duration sessionDuration = getSessionDuration();
        long hours = sessionDuration.toHours();
        long minutes = sessionDuration.toMinutesPart();
        report.append(String.format("Session Duration: %dh %dm\n", hours, minutes));
        report.append(String.format("Total Uptime: %.1f%% (%d seconds)\n", 
            getUptimePercentage(), getTotalUptimeSeconds()));
        report.append(String.format("Restarts: %d\n\n", getRestartCount()));
        
        // Work Unit Stats
        report.append("Work Units:\n");
        report.append(String.format("  Started: %d\n", getTotalWorkUnitsStarted()));
        report.append(String.format("  Completed: %d\n", getTotalWorkUnitsCompleted()));
        report.append(String.format("  Failed: %d\n", getTotalWorkUnitsFailed()));
        report.append(String.format("  Success Rate: %.1f%%\n\n", getSuccessRate()));
        
        // Points & Efficiency
        report.append("Performance:\n");
        report.append(String.format("  Total Points: %,d\n", getTotalPointsEarned()));
        report.append(String.format("  Avg Points/Unit: %.0f\n", getAveragePointsPerUnit()));
        report.append(String.format("  Points/Hour: %.0f\n\n", getPointsPerHour()));
        
        // Resource Usage
        report.append("Resource Usage:\n");
        report.append(String.format("  Current CPU: %.1f%%\n", getCurrentCpuUsage()));
        report.append(String.format("  Current Memory: %d MB\n", getCurrentMemoryUsageMB()));
        report.append(String.format("  Peak Memory: %d MB\n", getPeakMemoryUsageMB()));
        report.append(String.format("  Avg Memory: %d MB\n\n", getAverageMemoryUsageMB()));
        
        // Health Status
        report.append("Health Status:\n");
        report.append(String.format("  Status: %s\n", getHealthStatus()));
        report.append(String.format("  Healthy: %s\n", isHealthy() ? "Yes" : "No"));
        report.append(String.format("  Consecutive Success: %d\n", getConsecutiveSuccessfulUnits()));
        report.append(String.format("  Consecutive Failures: %d\n", getConsecutiveFailedUnits()));
        
        return report.toString();
    }
    
    /**
     * Reset all metrics (useful for testing or starting fresh)
     */
    public void reset() {
        totalCpuTimeMillis.set(0);
        fahCpuTimeMillis.set(0);
        currentCpuUsagePercent.set(0.0);
        peakMemoryUsageMB.set(0);
        currentMemoryUsageMB.set(0);
        averageMemoryUsageMB.set(0);
        memorySampleCount.set(0);
        totalWorkUnitsStarted.set(0);
        totalWorkUnitsCompleted.set(0);
        totalWorkUnitsFailed.set(0);
        totalPointsEarned.set(0);
        totalProcessingTimeSeconds.set(0);
        sessionStartTime.set(Instant.now());
        totalUptimeSeconds.set(0);
        totalDowntimeSeconds.set(0);
        restartCount.set(0);
        consecutiveSuccessfulUnits.set(0);
        consecutiveFailedUnits.set(0);
        lastHealthStatus.set("Reset");
        lastHealthCheckTimestamp.set(System.currentTimeMillis());
    }
}
