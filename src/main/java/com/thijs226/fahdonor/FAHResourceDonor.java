package com.thijs226.fahdonor;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.thijs226.fahdonor.commands.FAHCommands;
import com.thijs226.fahdonor.environment.PlatformResourceManager;
import com.thijs226.fahdonor.voting.CauseVotingManager;

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

    // Resolved account configuration (single source of truth)
    private record AccountConfig(String username, String teamId, String token) {}

    private String safeGet(String path) {
        String v = getConfig().getString(path, "");
        return v != null ? v.trim() : "";
    }

    private AccountConfig resolveAccountConfig() {
        // New format
        String accUsername = safeGet("folding-at-home.account.username");
        String accTeam = safeGet("folding-at-home.account.team-id");
        String accPasskey = safeGet("folding-at-home.account.passkey");
        String accToken = safeGet("folding-at-home.account.account-token");
        boolean anonymous = getConfig().getBoolean("folding-at-home.account.anonymous", false);

        // Fallbacks
        String defTeam = safeGet("folding-at-home.default-account.team-id");
        String legacyToken = safeGet("fah.token");
        String legacyTeam = safeGet("fah.team");
        String legacyDonor = safeGet("fah.donor-name");

        // Choose team: account -> default-account -> legacy
        String team = !accTeam.isEmpty() ? accTeam : (!defTeam.isEmpty() ? defTeam : legacyTeam);

        // Choose username: account -> legacy -> Anonymous
        String username = !accUsername.isEmpty() ? accUsername : (!legacyDonor.isEmpty() ? legacyDonor : "Anonymous");
        if (anonymous) {
            username = "Anonymous";
        }

        // Choose token: account-token -> passkey -> legacy token
        String token = !accToken.isEmpty() ? accToken : (!accPasskey.isEmpty() ? accPasskey : legacyToken);

        // Log what will be used (without exposing passkey/token value)
        final String usernameLog = username;
        final String teamLog = team.isEmpty() ? "<none>" : team;
        getLogger().info(() -> String.format("Using F@H account: username='%s', team='%s'", usernameLog, teamLog));
        if (!accToken.isEmpty()) {
            getLogger().info(() -> "Auth method: account-token (overrides passkey)");
        } else if (!accPasskey.isEmpty()) {
            getLogger().info(() -> "Auth method: passkey");
        } else if (!legacyToken.isEmpty()) {
            getLogger().info(() -> "Auth method: legacy token");
        } else {
            getLogger().warning(() -> "No auth token/passkey configured; running in simulation mode");
        }

        return new AccountConfig(username, team, token);
    }
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Load configuration
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        
        // Initialize platform/environment detection and management
        platformManager = new PlatformResourceManager(this);
        
    // Resolve account early for visibility and consistency
    AccountConfig account = resolveAccountConfig();
    this.teamId = account.teamId();
    this.userName = account.username();
        
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
    org.bukkit.command.PluginCommand fahCommand = getCommand("fah");
        if (fahCommand != null) {
            fahCommand.setExecutor(new FAHCommands(this));
        } else {
            getLogger().warning(() -> "Command 'fah' not defined in plugin.yml; commands will be unavailable");
        }
        
        // Start monitoring
        playerMonitor.start();
        
        // Start status checker for FAH client
        startStatusChecker();
        
        if (fahAvailable) {
            getLogger().info("FAH Resource Donor enabled! Contributing to medical research.");
            getLogger().info(() -> String.format("Environment: %s", platformManager.getEnvironmentInfo().getType().getDisplayName()));
        } else {
            getLogger().info("FAH Resource Donor enabled in LIMITED MODE - Install FAH to activate donations");
        }
    }
    
    private void checkAccountConfiguration() {
        AccountConfig acc = resolveAccountConfig();
        boolean hasToken = acc.token() != null && !acc.token().isEmpty();
        boolean hasUsername = acc.username() != null && !acc.username().isEmpty() && !"Anonymous".equalsIgnoreCase(acc.username());

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
            String machineName = safeGet("folding-at-home.account.machine-name");
            if (machineName.isEmpty()) machineName = "Minecraft-Server";
            getLogger().info(() -> "F@H configured with account token");
            final String machineNameLog = machineName;
            getLogger().info(() -> String.format("Machine name: %s", machineNameLog));
            getLogger().info(() -> "This server is linked to your F@H account");
        } else if (hasUsername) {
            String passkey = safeGet("folding-at-home.account.passkey");
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
                String effectiveTeam = acc.teamId() == null || acc.teamId().isEmpty() ? "<none>" : acc.teamId();
                getLogger().info(String.format("F@H Account: %s (Team: %s) [Passkey: Set]", acc.username(), effectiveTeam));
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
        // Resolve a single consistent account configuration
        AccountConfig acc = resolveAccountConfig();
        final String teamIdToUse = acc.teamId() == null ? "" : acc.teamId();
        final String donorNameToUse = acc.username() == null || acc.username().isEmpty() ? "Anonymous" : acc.username();
        final String tokenToUse = (acc.token() == null || acc.token().isEmpty()) ? "simulation_mode_token" : acc.token();
        if (!"simulation_mode_token".equals(tokenToUse)) {
            getLogger().info("Using configured authentication for F@H");
        } else {
            getLogger().warning("No FAH authentication configured! Running in simulation mode for testing...");
        }
        
        // Initialize and start FAH client
        CompletableFuture.runAsync(() -> {
            try {
                boolean success = fahClient.initialize(tokenToUse, teamIdToUse, donorNameToUse);
                if (success) {
                    isRunning = true;
                    getLogger().info(() -> "FAH client successfully initialized and started!");
                    getLogger().info(() -> String.format("Donor: %s (Team: %s)", donorNameToUse, teamIdToUse));
                } else {
                    getLogger().warning(() -> "Failed to initialize FAH client - check configuration");
                }
            } catch (RuntimeException e) {
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
                                getLogger().info(() -> "FAH Status Check:");
                                getLogger().info(() -> String.format("  Status: %s", status));
                                getLogger().info(() -> String.format("  Progress: %s", progress));
                                getLogger().info(() -> String.format("  Points earned: %d", fahClient.getPointsEarned()));
                            }
                            
                            // Check if we're actually processing work
                            if (!fahClient.isProcessingWork()) {
                                getLogger().info(() -> "FAH client requesting new work unit...");
                                fahClient.requestWorkUnit();
                            }
                            
                        } catch (RuntimeException e) {
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
        if (fahManager != null) {
            // Regenerate config.xml immediately with latest settings
            fahManager.reconfigureFAHClient();
        }
        
        getLogger().info("Configuration reloaded!");
    }
}