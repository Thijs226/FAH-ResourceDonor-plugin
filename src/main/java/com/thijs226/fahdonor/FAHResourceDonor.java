package com.thijs226.fahdonor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.thijs226.fahdonor.async.AsyncTaskManager;
import com.thijs226.fahdonor.cache.ConfigCache;
import com.thijs226.fahdonor.commands.FAHCommands;
import com.thijs226.fahdonor.database.DatabaseManager;
import com.thijs226.fahdonor.environment.PlatformResourceManager;
import com.thijs226.fahdonor.health.HealthMonitor;
import com.thijs226.fahdonor.leaderboard.LeaderboardManager;
import com.thijs226.fahdonor.logging.EnhancedLogger;
import com.thijs226.fahdonor.metrics.PerformanceMetrics;
import com.thijs226.fahdonor.notifications.NotificationManager;
import com.thijs226.fahdonor.rewards.RewardManager;
import com.thijs226.fahdonor.scheduling.ScheduleManager;
import com.thijs226.fahdonor.voting.CauseVotingManager;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class FAHResourceDonor extends JavaPlugin {
    private static FAHResourceDonor instance;
    private FAHClientManager fahManager;
    private FAHClient fahClient; // Add working FAH client
    private PlayerMonitor playerMonitor;
    private ConfigManager configManager;
    private StatisticsManager statisticsManager;
    private CauseVotingManager votingManager;
    private PlatformResourceManager platformManager;
    private PerformanceMetrics performanceMetrics;
    private RewardManager rewardManager;
    private LeaderboardManager leaderboardManager;
    private ScheduleManager scheduleManager;
    private HealthMonitor healthMonitor;
    private BukkitRunnable statusChecker;
    
    // Enhanced systems
    private AsyncTaskManager asyncTaskManager;
    private NotificationManager notificationManager;
    private ConfigCache<String, Object> configCache;
    private DatabaseManager databaseManager;
    private EnhancedLogger enhancedLogger;
    
    private String teamId = "";
    private boolean isRunning = false;
    private boolean startupCompleted = false;
    private LicenseAgreementListener licenseAgreementListener;

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

        if (!ensureLicenseAccepted()) {
            return;
        }

        continueStartup();
    }

    private void continueStartup() {
        if (startupCompleted) {
            return;
        }
        startupCompleted = true;

        // Initialize enhanced systems first
        getLogger().info("Initializing enhanced systems...");
        enhancedLogger = new EnhancedLogger(this);
        enhancedLogger.start();
        enhancedLogger.logConfig(Level.INFO, "Enhanced logging system started");
        
        asyncTaskManager = new AsyncTaskManager(this, 4); // 4 thread pool
        notificationManager = new NotificationManager(this);
        configCache = new ConfigCache<>(5, java.util.concurrent.TimeUnit.MINUTES);
        
        // Initialize database
        databaseManager = new DatabaseManager(this);
        if (databaseManager.initialize()) {
            enhancedLogger.logDatabase(Level.INFO, "Database initialized successfully");
        } else {
            enhancedLogger.logDatabase(Level.SEVERE, "Failed to initialize database");
        }
        
        // Initialize platform/environment detection and management
        platformManager = new PlatformResourceManager(this);

        // Resolve account early for visibility and consistency
        AccountConfig account = resolveAccountConfig();
        this.teamId = account.teamId();

        // Check for account configuration
        checkAccountConfiguration();
        
    // Log environment-specific recommendations
    platformManager.logEnvironmentRecommendations();
        
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
    fahClient = new FAHClient(this, fahManager);
    playerMonitor = new PlayerMonitor(this, fahManager, platformManager);
    statisticsManager = new StatisticsManager(this, fahClient);
        votingManager = new CauseVotingManager(this);
        
        // Initialize new enhancement systems
        performanceMetrics = new PerformanceMetrics(this);
        rewardManager = new RewardManager(this);
        leaderboardManager = new LeaderboardManager(this);
        scheduleManager = new ScheduleManager(this);
        healthMonitor = new HealthMonitor(this);
        
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
        
        // Start enhancement systems
        if (getConfig().getBoolean("scheduling.enabled", false)) {
            scheduleManager.start();
            getLogger().info("Schedule manager started");
        }
        
        if (getConfig().getBoolean("health-monitoring.enabled", true)) {
            healthMonitor.start();
            getLogger().info("Health monitor started");
        }
        
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
        getLogger().info("Shutting down FAH ResourceDonor...");
        
        // Cleanup enhanced systems first
        if (enhancedLogger != null) {
            enhancedLogger.logConfig(Level.INFO, "Shutting down plugin...");
            enhancedLogger.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (notificationManager != null) {
            notificationManager.cleanup();
        }
        if (asyncTaskManager != null) {
            asyncTaskManager.shutdown();
        }
        if (configCache != null) {
            configCache.invalidateAll();
        }
        
        if (licenseAgreementListener != null) {
            HandlerList.unregisterAll(licenseAgreementListener);
            licenseAgreementListener = null;
        }
        if (statusChecker != null) {
            statusChecker.cancel();
        }
        if (playerMonitor != null) {
            playerMonitor.stop();
        }
        if (scheduleManager != null) {
            scheduleManager.stop();
        }
        if (healthMonitor != null) {
            healthMonitor.stop();
        }
        if (rewardManager != null) {
            rewardManager.saveContributions();
        }
        if (leaderboardManager != null) {
            leaderboardManager.saveLeaderboard();
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

    private boolean ensureLicenseAccepted() {
        if (isLicenseAccepted()) {
            return true;
        }

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Unable to create plugin data folder to record licence acceptance.");
        }

        getLogger().warning("================================================================================");
        getLogger().warning("FAH Resource Donor needs your consent before installing Folding@home components.");
        getLogger().warning("By typing 'I agree' in the server console you confirm that:");
        getLogger().warning(" - Folding@home may be installed on this machine by this plugin");
        getLogger().warning(" - You accept the Folding@home client GPL-3.0 licence:" +
                " https://github.com/FoldingAtHome/fah-client-bastet?tab=GPL-3.0-1-ov-file#readme");
        getLogger().warning("");
    getLogger().warning("Please type exactly: I agree");
    getLogger().warning("Startup will continue automatically once acceptance is recorded.");
        getLogger().warning("================================================================================");

        if (licenseAgreementListener == null) {
            licenseAgreementListener = new LicenseAgreementListener();
            getServer().getPluginManager().registerEvents(licenseAgreementListener, this);
        }

        return false;
    }

    private boolean isLicenseAccepted() {
        Path acceptanceFile = getLicenseAcceptanceFile();
        return Files.exists(acceptanceFile);
    }

    private void markLicenseAccepted() {
        Path acceptanceFile = getLicenseAcceptanceFile();
        try {
            Files.createDirectories(acceptanceFile.getParent());
            Files.writeString(acceptanceFile,
                    "I agree to install Folding@home and accept the GPL-3.0 licence." + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to persist licence acceptance.", e);
        }
    }

    private Path getLicenseAcceptanceFile() {
        return getDataFolder().toPath().resolve("license.accepted");
    }

    private class LicenseAgreementListener implements Listener {
        @EventHandler
        public void onServerCommand(ServerCommandEvent event) {
            if (!(event.getSender() instanceof ConsoleCommandSender)) {
                return;
            }

            String command = event.getCommand().trim();
            if (!command.equalsIgnoreCase("I agree")) {
                return;
            }

            event.setCancelled(true);
            HandlerList.unregisterAll(this);
            licenseAgreementListener = null;

            markLicenseAccepted();
            getLogger().info("Licence acceptance recorded. Continuing Folding@home setup...");

            Bukkit.getScheduler().runTask(FAHResourceDonor.this, () -> {
                if (!startupCompleted) {
                    continueStartup();
                }
            });
        }
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
                                long points = fahClient.getPointsEarned();
                                int completedUnits = fahClient.getCompletedWorkUnits();
                                double coreHours = fahClient.getTotalCoreHours();
                                getLogger().info(() -> "FAH Status Check:");
                                getLogger().info(() -> String.format("  Status: %s", status));
                                getLogger().info(() -> String.format("  Progress: %s", progress));
                                getLogger().info(() -> String.format("  Completed units: %d", completedUnits));
                                getLogger().info(() -> String.format("  Points earned: %,d", points));
                                getLogger().info(() -> String.format("  Core hours contributed: %.2f", coreHours));
                            }

                            if (!fahClient.isProcessingWork() && fahManager != null && fahManager.isFAHRunning()) {
                                if (fahClient.isAutoRestartSuppressed()) {
                                    if (debugMode) {
                                        getLogger().info("FAH auto-restart suppressed after repeated failures. Waiting for admin intervention.");
                                    }
                                } else {
                                    if (debugMode) {
                                        getLogger().info("FAH client idle; requesting new work assignment.");
                                    }
                                    fahClient.requestWorkUnit();
                                }
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

    public void notifyAdmins(String message, ChatColor color, boolean logToConsole) {
        Runnable task = () -> {
            String chatMessage = ChatColor.GOLD + "[FAH] " + color + message;
            boolean chatEnabled = getConfig().getBoolean("monitoring.admin-alerts.broadcast-messages", true);
            boolean actionbarEnabled = getConfig().getBoolean("monitoring.admin-alerts.actionbar-enabled", false);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("fahdonor.admin")) {
                    continue;
                }
                if (chatEnabled) {
                    player.sendMessage(chatMessage);
                }
                if (actionbarEnabled) {
                    sendActionBar(player, chatMessage);
                }
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }

        if (logToConsole) {
            getLogger().info(ChatColor.stripColor(ChatColor.GOLD + "[FAH] " + color + message));
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (NoSuchMethodError | NoClassDefFoundError ex) {
            // Ignore if action bar API not available on this server version.
        }
    }

    public boolean isStructuredLoggingEnabled() {
        return getConfig().getBoolean("monitoring.structured-logging.enabled", false);
    }
    
    // Getters for enhancement systems
    public PerformanceMetrics getPerformanceMetrics() {
        return performanceMetrics;
    }
    
    public RewardManager getRewardManager() {
        return rewardManager;
    }
    
    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }
    
    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }
    
    public HealthMonitor getHealthMonitor() {
        return healthMonitor;
    }
    
    // Getters for new enhanced systems
    public AsyncTaskManager getAsyncTaskManager() {
        return asyncTaskManager;
    }
    
    public NotificationManager getNotificationManager() {
        return notificationManager;
    }
    
    public ConfigCache<String, Object> getConfigCache() {
        return configCache;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public EnhancedLogger getEnhancedLogger() {
        return enhancedLogger;
    }
}
    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }
    
    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }
    
    public HealthMonitor getHealthMonitor() {
        return healthMonitor;
    }
}