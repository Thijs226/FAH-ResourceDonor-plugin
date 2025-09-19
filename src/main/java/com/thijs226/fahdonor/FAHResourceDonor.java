package com.thijs226.fahdonor;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import com.thijs226.fahdonor.commands.FAHCommands;
import com.thijs226.fahdonor.voting.CauseVotingManager;
import com.thijs226.fahdonor.environment.PlatformResourceManager;
import java.io.File;
import java.util.logging.Level;

public class FAHResourceDonor extends JavaPlugin {
    private static FAHResourceDonor instance;
    private FAHClientManager fahManager;
    private PlayerMonitor playerMonitor;
    private ConfigManager configManager;
    private StatisticsManager statisticsManager;
    private CauseVotingManager votingManager;
    private PlatformResourceManager platformManager;
    private String teamId = "";
    private String userName = "Thijs226_MCServer";
    
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
                getLogger().warning("FAH Client not available - Plugin running in LIMITED MODE");
                getLogger().warning("Core allocation monitoring is active but FAH won't actually run");
                getLogger().warning("");
                getLogger().warning("To enable FAH:");
                getLogger().warning("1. Install FAH from: https://foldingathome.org/start-folding/");
                getLogger().warning("2. Copy FAHClient binary to: plugins/FAHResourceDonor/folding-at-home/");
                getLogger().warning("3. Restart server");
                getLogger().warning("========================================");
            }
        }
        
        // Initialize managers with platform awareness
        fahManager = new FAHClientManager(this, platformManager);
        playerMonitor = new PlayerMonitor(this, fahManager, platformManager);
        statisticsManager = new StatisticsManager(this);
        votingManager = new CauseVotingManager(this);
        
        // Register commands
        getCommand("fah").setExecutor(new FAHCommands(this));
        
        // Start monitoring
        playerMonitor.start();
        
        if (fahAvailable && isFAHInstalled()) {
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
        if (playerMonitor != null) {
            playerMonitor.stop();
        }
        if (fahManager != null) {
            fahManager.shutdown();
        }
        if (votingManager != null) {
            votingManager.saveVotes();
        }
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
}