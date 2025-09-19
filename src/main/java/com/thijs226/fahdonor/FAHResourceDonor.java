package com.thijs226.fahdonor;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import com.thijs226.fahdonor.commands.FAHCommands;
import com.thijs226.fahdonor.voting.CauseVotingManager;
import com.thijs226.fahdonor.environment.PlatformResourceManager;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class FAHResourceDonor extends JavaPlugin {
    private static FAHResourceDonor instance;
    private FAHClientManager fahManager;
    private FAHClient fahClient; // Add working FAH client
    private PlayerMonitor playerMonitor;
    private ConfigManager configManager;
    private StatisticsManager statisticsManager;
    private CauseVotingManager votingManager;
    private PlatformResourceManager platformManager;
    private BukkitRunnable statusChecker;
    private String teamId = "";
    private String userName = "Thijs226_MCServer";
    private boolean isRunning = false;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Load configuration
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        
        // Initialize platform/environment detection and management
        platformManager = new PlatformResourceManager(this);
        
        teamId = getConfig().getString("folding-at-home.default-account.team-id", "");
        
        // Check for account configuration
        checkAccountConfiguration();
        
        // Log environment-specific recommendations
        platformManager.logEnvironmentRecommendations();
        
        // Initialize FAH Client for actual work unit processing
        fahClient = new FAHClient(this);
        
        // Check and install FAH if needed
        boolean fahAvailable = true;
        if (!isFAHInstalled()) {
            if (getConfig().getBoolean("folding-at-home.auto-install", true)) {
                getLogger().info("Folding@home client not found. Attempting installation...");
                FAHInstaller installer = new FAHInstaller(this);
                fahAvailable = installer.installFAHClient();
            } else {
                getLogger().info("FAH client not found and auto-install is disabled");
                fahAvailable = false;
            }
            
            if (!fahAvailable || !isFAHInstalled()) {
                getLogger().warning("========================================");
                getLogger().warning("FAH Client not available - Running in SIMULATION MODE");
                getLogger().warning("Work unit processing will be simulated for testing");
                getLogger().warning("");
                getLogger().warning("To enable real FAH:");
                getLogger().warning("1. Install FAH from: https://foldingathome.org/start-folding/");
                getLogger().warning("2. Copy FAHClient binary to: plugins/FAHResourceDonor/folding-at-home/");
                getLogger().warning("3. Restart server");
                getLogger().warning("========================================");
                fahAvailable = true; // Allow simulation mode to work
            }
        }
        
        // Initialize managers with platform awareness
        fahManager = new FAHClientManager(this, platformManager);
        playerMonitor = new PlayerMonitor(this, fahManager, platformManager);
        statisticsManager = new StatisticsManager(this);
        votingManager = new CauseVotingManager(this);
        
        // Start the actual FAH service
        startFAHService();
        
        // Register commands
        getCommand("fah").setExecutor(new FAHCommands(this));
        
        // Start monitoring
        playerMonitor.start();
        
        // Start status checker for FAH client
        startStatusChecker();
        
        if (fahAvailable) {
            getLogger().info("FAH Resource Donor enabled! Contributing to medical research.");
            getLogger().info("Environment: " + platformManager.getEnvironmentInfo().getType().getDisplayName());
        } else {
            getLogger().info("FAH Resource Donor enabled in LIMITED MODE - Install FAH to activate donations");
        }
    }
    
    private void checkAccountConfiguration() {
        String username = getConfig().getString("folding-at-home.account.username", "");
        String passkey = getConfig().getString("folding-at-home.account.passkey", "");
        String teamId = getConfig().getString("folding-at-home.account.team-id", "");
        String accountToken = getConfig().getString("folding-at-home.account.account-token", "");
        
        boolean hasToken = !accountToken.isEmpty();
        boolean hasUsername = !username.isEmpty();
        
        if (!hasToken && !hasUsername) {
            getLogger().warning("========================================");
            getLogger().warning("NO FOLDING@HOME ACCOUNT CONFIGURED!");
            getLogger().warning("Your contributions won't be tracked!");
            getLogger().warning("");
            getLogger().warning("Option 1 - Traditional Setup:");
            getLogger().warning("  /fah setup <username> <team-id> <passkey>");
            getLogger().warning("");
            getLogger().warning("Option 2 - Account Token:");
            getLogger().warning("  /fah token <account-token>");
            getLogger().warning("");
            getLogger().warning("Get started at: https://foldingathome.org/start-folding/");
            getLogger().warning("========================================");
        } else if (hasToken) {
            String machineName = getConfig().getString("folding-at-home.account.machine-name", "Minecraft-Server");
            getLogger().info("F@H configured with account token");
            getLogger().info("Machine name: " + machineName);
            getLogger().info("This server is linked to your F@H account");
        } else if (hasUsername) {
            if (passkey.isEmpty()) {
                getLogger().warning("========================================");
                getLogger().warning("NO PASSKEY CONFIGURED!");
                getLogger().warning("You're missing out on bonus points!");
                getLogger().warning("");
                getLogger().warning("Get your passkey at:");
                getLogger().warning("https://apps.foldingathome.org/getpasskey");
                getLogger().warning("Then use: /fah passkey <your-passkey>");
                getLogger().warning("========================================");
            } else {
                getLogger().info("F@H Account: " + username + " (Team: " + teamId + ") [Passkey: Set]");
            }
        }
    }
    
    @Override
    public void onDisable() {
        if (statusChecker != null) {
            statusChecker.cancel();
        }
        if (playerMonitor != null) {
            playerMonitor.stop();
        }
        if (fahManager != null) {
            fahManager.shutdown();
        }
        if (fahClient != null) {
            fahClient.shutdown();
        }
        if (votingManager != null) {
            votingManager.saveVotes();
        }
        getLogger().info("FAH ResourceDonor plugin has been disabled!");
    }
    
    private void startFAHService() {
        // Get configuration for FAH client
        String username = getConfig().getString("folding-at-home.account.username", "");
        String passkey = getConfig().getString("folding-at-home.account.passkey", "");
        String teamId = getConfig().getString("folding-at-home.account.team-id", "");
        String accountToken = getConfig().getString("folding-at-home.account.account-token", "");
        
        // Support old config format for backward compatibility
        String fahToken = getConfig().getString("fah.token", "");
        String fahTeamId = getConfig().getString("fah.team", "");
        String donorName = getConfig().getString("fah.donor-name", "");
        
        // Use new config format if available, fall back to old format
        String finalToken = !passkey.isEmpty() ? passkey : fahToken;
        String finalTeamId = !teamId.isEmpty() ? teamId : fahTeamId;
        String finalDonorName = !username.isEmpty() ? username : 
                               (!donorName.isEmpty() ? donorName : "Thijs226_MCServer");
        
        // Use account token if available (overrides passkey)
        if (!accountToken.isEmpty()) {
            finalToken = accountToken;
            getLogger().info("Using account token for F@H authentication");
        }
        
        if (finalToken.isEmpty()) {
            getLogger().warning("No FAH authentication configured! Please set either:");
            getLogger().warning("- folding-at-home.account.passkey (traditional)");
            getLogger().warning("- folding-at-home.account.account-token (new method)");
            getLogger().warning("- fah.token (legacy support)");
            getLogger().warning("Running in simulation mode for testing...");
            finalToken = "simulation_mode_token";
        }
        
        // Initialize and start FAH client
        CompletableFuture.runAsync(() -> {
            try {
                boolean success = fahClient.initialize(finalToken, finalTeamId, finalDonorName);
                if (success) {
                    isRunning = true;
                    getLogger().info("FAH client successfully initialized and started!");
                    getLogger().info("Donor: " + finalDonorName + " (Team: " + finalTeamId + ")");
                } else {
                    getLogger().warning("Failed to initialize FAH client - check configuration");
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error starting FAH service", e);
            }
        });
    }
    
    private void startStatusChecker() {
        statusChecker = new BukkitRunnable() {
            @Override
            public void run() {
                if (isRunning && fahClient != null) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            String status = fahClient.getWorkUnitStatus();
                            String progress = fahClient.getProgress();
                            boolean debugMode = getConfig().getBoolean("debug", false);
                            
                            if (debugMode) {
                                getLogger().info("FAH Status Check:");
                                getLogger().info("  Status: " + status);
                                getLogger().info("  Progress: " + progress);
                                getLogger().info("  Points earned: " + fahClient.getPointsEarned());
                            }
                            
                            // Check if we're actually processing work
                            if (!fahClient.isProcessingWork()) {
                                getLogger().info("FAH client requesting new work unit...");
                                fahClient.requestWorkUnit();
                            }
                            
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING, "Error during status check", e);
                        }
                    });
                }
            }
        };
        
        // Check every 5 minutes (300 seconds)
        int checkInterval = getConfig().getInt("fah.check-interval", 300);
        statusChecker.runTaskTimer(this, 20L * 60L, 20L * checkInterval);
    }
    
    public boolean isFAHInstalled() {
        File fahDir = new File(getDataFolder(), "folding-at-home");
        return fahDir.exists() && 
            (new File(fahDir, "FAHClient").exists() || 
             new File(fahDir, "FAHClient.exe").exists());
    }
    
    public static FAHResourceDonor getInstance() {
        return instance;
    }
    
    public String getTeamId() {
        return teamId;
    }
    
    public FAHClientManager getFAHManager() {
        return fahManager;
    }
    
    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }
    
    public CauseVotingManager getVotingManager() {
        return votingManager;
    }
    
    public PlatformResourceManager getPlatformManager() {
        return platformManager;
    }
    
    public FAHClient getFAHClient() {
        return fahClient;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public void setRunning(boolean running) {
        this.isRunning = running;
    }
    
    public void reloadConfiguration() {
        reloadConfig();
        configManager.reload();
        
        if (fahClient != null) {
            // Restart FAH service with new configuration
            startFAHService();
        }
        
        getLogger().info("Configuration reloaded!");
    }
}