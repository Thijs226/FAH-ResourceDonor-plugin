package com.thijs226.fahresourcedonor;

import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Utility class to diagnose common FAH integration issues
 */
public class FAHDiagnostics {
    
    private final Plugin plugin;
    
    public FAHDiagnostics(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Run comprehensive diagnostics to identify why folding might not be working
     */
    public CompletableFuture<DiagnosticResult> runDiagnostics(String token, String teamId, String donorName) {
        return CompletableFuture.supplyAsync(() -> {
            DiagnosticResult result = new DiagnosticResult();
            
            // Test 1: Token validation
            result.tokenValid = validateToken(token);
            
            // Test 2: Internet connectivity
            result.internetConnected = testInternetConnection();
            
            // Test 3: FAH server connectivity
            result.fahServersReachable = testFAHServers();
            
            // Test 4: Donor name uniqueness
            result.donorNameUnique = checkDonorNameUniqueness(donorName);
            
            // Test 5: Team existence
            result.teamExists = checkTeamExists(teamId);
            
            // Test 6: API accessibility
            result.apiAccessible = testFAHAPI();
            
            return result;
        });
    }
    
    private boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            plugin.getLogger().warning("DIAGNOSTIC: Token is empty or null");
            return false;
        }
        
        if (token.length() < 32) {
            plugin.getLogger().warning("DIAGNOSTIC: Token appears too short (should be 32+ characters)");
            return false;
        }
        
        if (!token.matches("^[a-fA-F0-9]+$")) {
            plugin.getLogger().warning("DIAGNOSTIC: Token contains invalid characters (should be hexadecimal)");
            return false;
        }
        
        plugin.getLogger().info("DIAGNOSTIC: Token format appears valid");
        return true;
    }
    
    private boolean testInternetConnection() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://www.google.com").openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            boolean connected = responseCode == 200;
            
            if (connected) {
                plugin.getLogger().info("DIAGNOSTIC: Internet connection OK");
            } else {
                plugin.getLogger().warning("DIAGNOSTIC: Internet connection issue (response: " + responseCode + ")");
            }
            
            return connected;
            
        } catch (Exception e) {
            plugin.getLogger().warning("DIAGNOSTIC: Internet connection failed - " + e.getMessage());
            return false;
        }
    }
    
    private boolean testFAHServers() {
        try {
            // Test main FAH website
            HttpURLConnection connection = (HttpURLConnection) new URL("https://foldingathome.org").openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            boolean reachable = responseCode == 200;
            
            if (reachable) {
                plugin.getLogger().info("DIAGNOSTIC: FAH servers reachable");
            } else {
                plugin.getLogger().warning("DIAGNOSTIC: FAH servers unreachable (response: " + responseCode + ")");
            }
            
            return reachable;
            
        } catch (Exception e) {
            plugin.getLogger().warning("DIAGNOSTIC: FAH servers unreachable - " + e.getMessage());
            return false;
        }
    }
    
    private boolean checkDonorNameUniqueness(String donorName) {
        try {
            String apiUrl = "https://stats.foldingathome.org/api/donor/" + donorName;
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = reader.lines().reduce("", String::concat);
                    
                    if (response.contains("\"name\":null") || response.trim().equals("{}")) {
                        plugin.getLogger().info("DIAGNOSTIC: Donor name '" + donorName + "' is available");
                        return true;
                    } else {
                        plugin.getLogger().warning("DIAGNOSTIC: Donor name '" + donorName + "' may already be in use by someone else");
                        plugin.getLogger().warning("DIAGNOSTIC: Consider using a more unique name like '" + donorName + "_" + System.currentTimeMillis() % 10000 + "'");
                        return false;
                    }
                }
            } else if (responseCode == 404) {
                plugin.getLogger().info("DIAGNOSTIC: Donor name '" + donorName + "' is available (404 response)");
                return true;
            } else {
                plugin.getLogger().warning("DIAGNOSTIC: Could not check donor name uniqueness (response: " + responseCode + ")");
                return true; // Assume it's fine if we can't check
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("DIAGNOSTIC: Could not check donor name uniqueness - " + e.getMessage());
            return true; // Assume it's fine if we can't check
        }
    }
    
    private boolean checkTeamExists(String teamId) {
        if ("0".equals(teamId)) {
            plugin.getLogger().info("DIAGNOSTIC: No team specified (using team 0)");
            return true;
        }
        
        try {
            String apiUrl = "https://stats.foldingathome.org/api/team/" + teamId;
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            boolean exists = responseCode == 200;
            
            if (exists) {
                plugin.getLogger().info("DIAGNOSTIC: Team " + teamId + " exists");
            } else {
                plugin.getLogger().warning("DIAGNOSTIC: Team " + teamId + " may not exist (response: " + responseCode + ")");
                plugin.getLogger().warning("DIAGNOSTIC: Consider using team 0 or finding a valid team at https://stats.foldingathome.org/teams");
            }
            
            return exists;
            
        } catch (Exception e) {
            plugin.getLogger().warning("DIAGNOSTIC: Could not verify team existence - " + e.getMessage());
            return true; // Assume it's fine if we can't check
        }
    }
    
    private boolean testFAHAPI() {
        try {
            String apiUrl = "https://api.foldingathome.org/project/summary";
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            boolean accessible = responseCode == 200;
            
            if (accessible) {
                plugin.getLogger().info("DIAGNOSTIC: FAH API accessible");
            } else {
                plugin.getLogger().warning("DIAGNOSTIC: FAH API not accessible (response: " + responseCode + ")");
            }
            
            return accessible;
            
        } catch (Exception e) {
            plugin.getLogger().warning("DIAGNOSTIC: FAH API not accessible - " + e.getMessage());
            return false;
        }
    }
    
    public static class DiagnosticResult {
        public boolean tokenValid = false;
        public boolean internetConnected = false;
        public boolean fahServersReachable = false;
        public boolean donorNameUnique = false;
        public boolean teamExists = false;
        public boolean apiAccessible = false;
        
        public boolean isHealthy() {
            return tokenValid && internetConnected && fahServersReachable && donorNameUnique && teamExists;
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("FAH Diagnostic Results:\n");
            sb.append("✓ Token Valid: ").append(tokenValid ? "YES" : "NO").append("\n");
            sb.append("✓ Internet Connected: ").append(internetConnected ? "YES" : "NO").append("\n");
            sb.append("✓ FAH Servers Reachable: ").append(fahServersReachable ? "YES" : "NO").append("\n");
            sb.append("✓ Donor Name Unique: ").append(donorNameUnique ? "YES" : "NO").append("\n");
            sb.append("✓ Team Exists: ").append(teamExists ? "YES" : "NO").append("\n");
            sb.append("✓ API Accessible: ").append(apiAccessible ? "YES" : "NO").append("\n");
            sb.append("Overall Health: ").append(isHealthy() ? "GOOD" : "ISSUES DETECTED");
            return sb.toString();
        }
    }
}