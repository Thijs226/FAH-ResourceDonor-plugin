package com.thijs226.fahdonor.test;

import com.thijs226.fahdonor.FAHResourceDonor;

/**
 * Comprehensive test suite for all enhancement features.
 * Tests performance metrics, rewards, leaderboards, scheduling, and health monitoring.
 */
public class EnhancementTestSuite {
    
    private final FAHResourceDonor plugin;
    
    public EnhancementTestSuite(FAHResourceDonor plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Run all tests and generate a comprehensive report
     */
    public TestReport runAllTests() {
        TestReport report = new TestReport();
        
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("Starting Enhancement Features Test Suite");
        plugin.getLogger().info("========================================");
        
        // Test 1: Performance Metrics System
        report.addTest("Performance Metrics", testPerformanceMetrics());
        
        // Test 2: Reward System
        report.addTest("Reward System", testRewardSystem());
        
        // Test 3: Leaderboard System
        report.addTest("Leaderboard System", testLeaderboardSystem());
        
        // Test 4: Scheduling System
        report.addTest("Scheduling System", testSchedulingSystem());
        
        // Test 5: Health Monitor
        report.addTest("Health Monitor", testHealthMonitor());
        
        // Test 6: Configuration Loading
        report.addTest("Configuration", testConfiguration());
        
        // Test 7: Integration Tests
        report.addTest("Integration", testIntegration());
        
        // Test 8: Port Compatibility
        report.addTest("Port Compatibility", testPortCompatibility());
        
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("Test Suite Complete!");
        plugin.getLogger().info(report.getSummary());
        plugin.getLogger().info("========================================");
        
        return report;
    }
    
    private TestResult testPerformanceMetrics() {
        TestResult result = new TestResult();
        
        try {
            if (plugin.getPerformanceMetrics() == null) {
                return result.fail("Performance metrics not initialized");
            }
            
            var metrics = plugin.getPerformanceMetrics();
            
            // Test metric updates
            metrics.updateCpuUsage(50.0);
            if (metrics.getCurrentCpuUsage() != 50.0) {
                return result.fail("CPU usage tracking failed");
            }
            
            metrics.updateMemoryUsage(512);
            if (metrics.getCurrentMemoryUsageMB() != 512) {
                return result.fail("Memory usage tracking failed");
            }
            
            // Test work unit tracking
            metrics.recordWorkUnitStarted();
            if (metrics.getTotalWorkUnitsStarted() == 0) {
                return result.fail("Work unit start tracking failed");
            }
            
            metrics.recordWorkUnitCompleted(1000, 300);
            if (metrics.getTotalWorkUnitsCompleted() == 0) {
                return result.fail("Work unit completion tracking failed");
            }
            
            if (metrics.getTotalPointsEarned() != 1000) {
                return result.fail("Points tracking failed");
            }
            
            // Test report generation
            String report = metrics.generateReport();
            if (report == null || report.isEmpty()) {
                return result.fail("Report generation failed");
            }
            
            // Test health check
            if (!metrics.isHealthy()) {
                result.addWarning("Initial health check returned unhealthy");
            }
            
            // Test reset
            metrics.reset();
            if (metrics.getTotalPointsEarned() != 0) {
                return result.fail("Reset failed");
            }
            
            return result.pass("All performance metrics tests passed");
            
        } catch (Exception e) {
            return result.fail("Exception: " + e.getMessage());
        }
    }
    
    private TestResult testRewardSystem() {
        TestResult result = new TestResult();
        
        try {
            if (plugin.getRewardManager() == null) {
                return result.fail("Reward manager not initialized");
            }
            
            var rewardManager = plugin.getRewardManager();
            java.util.UUID testId = java.util.UUID.randomUUID();
            
            // Test contribution recording
            rewardManager.recordContribution(testId, 3600); // 1 hour
            var contrib = rewardManager.getContribution(testId);
            
            if (contrib.getContributionTimeSeconds() != 3600) {
                return result.fail("Time tracking failed");
            }
            
            // Test points recording
            rewardManager.recordPoints(testId, 1000);
            if (contrib.getPointsEarned() != 1000) {
                return result.fail("Points tracking failed");
            }
            
            // Test work unit recording
            rewardManager.recordWorkUnitCompleted(testId);
            if (contrib.getWorkUnitsCompleted() != 1) {
                return result.fail("Work unit tracking failed");
            }
            
            // Test save/load
            rewardManager.saveContributions();
            result.addWarning("Manual verification needed: Check config.yml for saved contributions");
            
            return result.pass("Reward system tests passed");
            
        } catch (Exception e) {
            return result.fail("Exception: " + e.getMessage());
        }
    }
    
    private TestResult testLeaderboardSystem() {
        TestResult result = new TestResult();
        
        try {
            if (plugin.getLeaderboardManager() == null) {
                return result.fail("Leaderboard manager not initialized");
            }
            
            var leaderboard = plugin.getLeaderboardManager();
            java.util.UUID testId1 = java.util.UUID.randomUUID();
            java.util.UUID testId2 = java.util.UUID.randomUUID();
            
            // Record test data
            leaderboard.recordPoints(testId1, "TestPlayer1", 5000);
            leaderboard.recordPoints(testId2, "TestPlayer2", 3000);
            leaderboard.recordTime(testId1, "TestPlayer1", 7200);
            leaderboard.recordWorkUnit(testId1, "TestPlayer1");
            
            // Test leaderboard retrieval
            var topByPoints = leaderboard.getTopByPoints(10);
            if (topByPoints.isEmpty()) {
                return result.fail("Leaderboard retrieval failed");
            }
            
            if (topByPoints.get(0).getTotalPoints() != 5000) {
                return result.fail("Leaderboard sorting failed");
            }
            
            // Test ranking
            int rank = leaderboard.getPlayerRank(testId1, 
                com.thijs226.fahdonor.leaderboard.LeaderboardManager.LeaderboardType.POINTS);
            if (rank != 1) {
                return result.fail("Ranking calculation failed");
            }
            
            // Test formatting
            String formatted = leaderboard.formatLeaderboard(
                com.thijs226.fahdonor.leaderboard.LeaderboardManager.LeaderboardType.POINTS, 10);
            if (formatted == null || formatted.isEmpty()) {
                return result.fail("Leaderboard formatting failed");
            }
            
            // Test save
            leaderboard.saveLeaderboard();
            
            return result.pass("Leaderboard system tests passed");
            
        } catch (Exception e) {
            return result.fail("Exception: " + e.getMessage());
        }
    }
    
    private TestResult testSchedulingSystem() {
        TestResult result = new TestResult();
        
        try {
            if (plugin.getScheduleManager() == null) {
                return result.fail("Schedule manager not initialized");
            }
            
            var scheduler = plugin.getScheduleManager();
            
            // Test status retrieval
            String status = scheduler.getScheduleStatus();
            if (status == null || status.isEmpty()) {
                return result.fail("Status retrieval failed");
            }
            
            // Test enable/disable
            boolean wasEnabled = scheduler.isEnabled();
            scheduler.setEnabled(!wasEnabled);
            if (scheduler.isEnabled() == wasEnabled) {
                return result.fail("Enable/disable toggle failed");
            }
            scheduler.setEnabled(wasEnabled); // Restore
            
            // Test getters
            if (scheduler.getStartTime() == null) {
                return result.fail("Start time getter failed");
            }
            if (scheduler.getEndTime() == null) {
                return result.fail("End time getter failed");
            }
            if (scheduler.getActiveDays() == null) {
                return result.fail("Active days getter failed");
            }
            
            result.addWarning("Schedule functionality requires time progression to test fully");
            
            return result.pass("Scheduling system tests passed");
            
        } catch (Exception e) {
            return result.fail("Exception: " + e.getMessage());
        }
    }
    
    private TestResult testHealthMonitor() {
        TestResult result = new TestResult();
        
        try {
            if (plugin.getHealthMonitor() == null) {
                return result.fail("Health monitor not initialized");
            }
            
            var health = plugin.getHealthMonitor();
            
            // Test health status
            boolean isHealthy = health.isHealthy();
            result.addWarning("Initial health status: " + (isHealthy ? "Healthy" : "Unhealthy"));
            
            // Test report generation
            String report = health.getHealthReport();
            if (report == null || report.isEmpty()) {
                return result.fail("Health report generation failed");
            }
            
            // Test auto-recovery getter/setter
            boolean wasEnabled = health.isAutoRecoveryEnabled();
            health.setAutoRecoveryEnabled(!wasEnabled);
            if (health.isAutoRecoveryEnabled() == wasEnabled) {
                return result.fail("Auto-recovery toggle failed");
            }
            health.setAutoRecoveryEnabled(wasEnabled); // Restore
            
            // Test issue tracking
            var issues = health.getRecentIssues();
            if (issues == null) {
                return result.fail("Issue tracking failed");
            }
            
            result.addWarning("Health monitoring requires runtime to test recovery actions");
            
            return result.pass("Health monitor tests passed");
            
        } catch (Exception e) {
            return result.fail("Exception: " + e.getMessage());
        }
    }
    
    private TestResult testConfiguration() {
        TestResult result = new TestResult();
        
        try {
            var config = plugin.getConfig();
            
            // Test reward configuration
            if (!config.contains("rewards")) {
                return result.fail("Rewards configuration missing");
            }
            
            // Test leaderboard configuration
            if (!config.contains("leaderboard")) {
                return result.fail("Leaderboard configuration missing");
            }
            
            // Test scheduling configuration
            if (!config.contains("scheduling")) {
                return result.fail("Scheduling configuration missing");
            }
            
            // Test health monitoring configuration
            if (!config.contains("health-monitoring")) {
                return result.fail("Health monitoring configuration missing");
            }
            
            // Test performance tracking configuration
            if (!config.contains("performance-tracking")) {
                return result.fail("Performance tracking configuration missing");
            }
            
            // Validate specific values
            boolean rewardsEnabled = config.getBoolean("rewards.enabled", false);
            result.addWarning("Rewards enabled: " + rewardsEnabled);
            
            boolean healthEnabled = config.getBoolean("health-monitoring.enabled", false);
            result.addWarning("Health monitoring enabled: " + healthEnabled);
            
            return result.pass("Configuration tests passed");
            
        } catch (Exception e) {
            return result.fail("Exception: " + e.getMessage());
        }
    }
    
    private TestResult testIntegration() {
        TestResult result = new TestResult();
        
        try {
            // Test that all systems are accessible from main plugin
            if (plugin.getPerformanceMetrics() == null) {
                return result.fail("Performance metrics not accessible");
            }
            if (plugin.getRewardManager() == null) {
                return result.fail("Reward manager not accessible");
            }
            if (plugin.getLeaderboardManager() == null) {
                return result.fail("Leaderboard manager not accessible");
            }
            if (plugin.getScheduleManager() == null) {
                return result.fail("Schedule manager not accessible");
            }
            if (plugin.getHealthMonitor() == null) {
                return result.fail("Health monitor not accessible");
            }
            
            // Test that existing systems still work
            if (plugin.getFAHClient() == null) {
                result.addWarning("FAH Client not initialized (may be expected)");
            }
            if (plugin.getFAHManager() == null) {
                result.addWarning("FAH Manager not initialized (may be expected)");
            }
            
            return result.pass("Integration tests passed");
            
        } catch (Exception e) {
            return result.fail("Exception: " + e.getMessage());
        }
    }
    
    private TestResult testPortCompatibility() {
        TestResult result = new TestResult();
        
        try {
            var config = plugin.getConfig();
            
            // Check port configuration
            int controlPort = config.getInt("folding-at-home.ports.control-port", -1);
            int webPort = config.getInt("folding-at-home.ports.web-port", -1);
            String noPortMode = config.getString("folding-at-home.ports.no-port-mode", "");
            
            result.addWarning("Control port: " + controlPort);
            result.addWarning("Web port: " + webPort);
            result.addWarning("No-port mode: " + noPortMode);
            
            // Test single-port configuration (shared hosting)
            if (controlPort == 0 && webPort == 0) {
                result.addWarning("Single-port (no-port) mode detected - suitable for shared hosting");
                if (!"file-based".equals(noPortMode)) {
                    result.addWarning("Consider setting no-port-mode to 'file-based' for shared hosting");
                }
            }
            
            // Test multi-port configuration (VPS/dedicated)
            if (controlPort > 0 || webPort > 0) {
                result.addWarning("Multi-port mode detected - suitable for VPS/dedicated server");
            }
            
            // Test environment detection
            boolean autoDetect = config.getBoolean("server.environment.auto-detect", true);
            if (autoDetect) {
                result.addWarning("Environment auto-detection enabled");
                if (plugin.getPlatformManager() != null) {
                    String envType = plugin.getPlatformManager().getEnvironmentInfo().getType().name();
                    result.addWarning("Detected environment: " + envType);
                }
            }
            
            return result.pass("Port compatibility checks completed");
            
        } catch (Exception e) {
            return result.fail("Exception: " + e.getMessage());
        }
    }
    
    /**
     * Test report container
     */
    public static class TestReport {
        private final java.util.Map<String, TestResult> results = new java.util.LinkedHashMap<>();
        
        public void addTest(String name, TestResult result) {
            results.put(name, result);
        }
        
        public String getSummary() {
            int passed = 0;
            int failed = 0;
            int warnings = 0;
            
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== Test Results ===\n");
            
            for (java.util.Map.Entry<String, TestResult> entry : results.entrySet()) {
                TestResult result = entry.getValue();
                String status = result.passed ? "✓ PASS" : "✗ FAIL";
                sb.append(String.format("%s - %s: %s\n", status, entry.getKey(), result.message));
                
                if (result.passed) {
                    passed++;
                } else {
                    failed++;
                }
                
                for (String warning : result.warnings) {
                    sb.append(String.format("    ⚠ %s\n", warning));
                    warnings++;
                }
            }
            
            sb.append("\n=== Summary ===\n");
            sb.append(String.format("Passed: %d\n", passed));
            sb.append(String.format("Failed: %d\n", failed));
            sb.append(String.format("Warnings: %d\n", warnings));
            sb.append(String.format("Total Tests: %d\n", results.size()));
            
            return sb.toString();
        }
        
        public boolean allPassed() {
            return results.values().stream().allMatch(r -> r.passed);
        }
    }
    
    /**
     * Individual test result
     */
    public static class TestResult {
        private boolean passed = false;
        private String message = "";
        private final java.util.List<String> warnings = new java.util.ArrayList<>();
        
        public TestResult pass(String message) {
            this.passed = true;
            this.message = message;
            return this;
        }
        
        public TestResult fail(String message) {
            this.passed = false;
            this.message = message;
            return this;
        }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }
}
