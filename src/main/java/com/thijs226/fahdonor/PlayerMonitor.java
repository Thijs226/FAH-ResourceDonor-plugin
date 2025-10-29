package com.thijs226.fahdonor;

import java.lang.reflect.Method;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.thijs226.fahdonor.environment.PlatformResourceManager;

public class PlayerMonitor implements Listener {
    private final FAHResourceDonor plugin;
    private final FAHClientManager fahManager;
    private final PlatformResourceManager platformManager;
    private int checkTaskId = -1;
    private int lastPlayerCount = 0;
    private long lastChange = 0;
    private Method tpsMethod = null;
    private boolean registered = false;
    private boolean suppressionNotified = false;
    
    public PlayerMonitor(FAHResourceDonor plugin, FAHClientManager fahManager) {
        this(plugin, fahManager, null);
    }
    
    @SuppressWarnings("LeakingThisInConstructor")
    public PlayerMonitor(FAHResourceDonor plugin, FAHClientManager fahManager, PlatformResourceManager platformManager) {
        this.plugin = plugin;
        this.fahManager = fahManager;
        this.platformManager = platformManager;
        
        // Try to find TPS method (Paper/Spigot specific)
        try {
            tpsMethod = Server.class.getMethod("getTPS");
            plugin.getLogger().info("TPS monitoring enabled (Paper detected)");
        } catch (NoSuchMethodException e) {
            plugin.getLogger().info("TPS monitoring disabled (using Spigot/vanilla)");
        }
    }
    
    public void start() {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        int checkInterval = plugin.getConfig().getInt("monitoring.check-interval", 30) * 20;
        
        checkTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkAndAdjustCores();
        }, 100L, checkInterval).getTaskId();
        
        // Initial check
        checkAndAdjustCores();
    }
    
    public void stop() {
        if (checkTaskId != -1) {
            Bukkit.getScheduler().cancelTask(checkTaskId);
        }
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        lastChange = System.currentTimeMillis();
        
        // Check if player is admin and account isn't configured
        if (event.getPlayer().hasPermission("fahdonor.admin")) {
            String username = plugin.getConfig().getString("folding-at-home.account.username", "");
            String passkey = plugin.getConfig().getString("folding-at-home.account.passkey", "");

            if (username == null || username.isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    event.getPlayer().sendMessage(ChatColor.GOLD + "[FAH] " + ChatColor.YELLOW + 
                        "Folding@home account not configured! Use: /fah setup");
                    event.getPlayer().sendMessage(ChatColor.GRAY + 
                        "Your server's contributions aren't being tracked!");
                }, 60L); // 3 seconds after join
            } else if (passkey == null || passkey.isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    event.getPlayer().sendMessage(ChatColor.GOLD + "[FAH] " + ChatColor.YELLOW + 
                        "No passkey configured! Use: /fah passkey <token>");
                    event.getPlayer().sendMessage(ChatColor.GRAY + 
                        "You're missing out on bonus points! Get one at:");
                    event.getPlayer().sendMessage(ChatColor.AQUA + 
                        "https://apps.foldingathome.org/getpasskey");
                }, 60L);
            }
        }
        
        // Show stats to regular players if configured
        if (plugin.getConfig().getBoolean("statistics.show-on-join", true)) {
            plugin.getStatisticsManager().onPlayerJoin(event.getPlayer());
        }
        
        Bukkit.getScheduler().runTaskLater(plugin, this::checkAndAdjustCores, 20L);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastChange = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskLater(plugin, this::checkAndAdjustCores, 60L);
    }
    
    private void checkAndAdjustCores() {
        int playerCount = Bukkit.getOnlinePlayers().size();
        
        long gracePeriod = plugin.getConfig().getLong("monitoring.grace-period", 60) * 1000;
        if (System.currentTimeMillis() - lastChange < gracePeriod && playerCount != lastPlayerCount) {
            return;
        }
        
        int cores = calculateOptimalCores(playerCount);

        FAHClient client = plugin.getFAHClient();
        if (client != null && client.isAutoRestartSuppressed()) {
            if (cores != 0) {
                plugin.getLogger().warning("FAH auto-restart suppressed; overriding core allocation to 0 until manual intervention.");
            }
            cores = 0;
            if (!suppressionNotified) {
                plugin.notifyAdmins("FAH auto-restart suppressed after repeated failures. Core allocation throttled to 0.", ChatColor.RED, true);
                suppressionNotified = true;
            }
        } else if (suppressionNotified) {
            plugin.notifyAdmins("FAH auto-restart suppression cleared. Automatic core allocation restored.", ChatColor.GREEN, true);
            suppressionNotified = false;
        }
        
        if (plugin.getConfig().getBoolean("monitoring.tps-monitoring", true)) {
            double tps = getTPS();
            double minTps = plugin.getConfig().getDouble("monitoring.min-tps", 18.0);
            
            if (tps < minTps && cores > 0) {
                cores = Math.max(0, cores - 2);
                final double ftps = tps;
                plugin.getLogger().warning(() -> String.format("TPS low (%.1f), reducing FAH cores", ftps));
            }
        }
        
        // Log core adjustment
        if (cores != fahManager.getCurrentCores()) {
            final int old = fahManager.getCurrentCores();
            final int nc = cores;
            final int pc = playerCount;
            plugin.getLogger().info(() -> String.format("Adjusting FAH cores: %d -> %d (Players: %d)", old, nc, pc));
        }
        
        fahManager.setCores(cores);
        lastPlayerCount = playerCount;
    }

    
    private double getTPS() {
        if (tpsMethod != null) {
            try {
                double[] tpsArray = (double[]) tpsMethod.invoke(plugin.getServer());
                return tpsArray[0];
            } catch (ReflectiveOperationException | ClassCastException e) {
                // Fallback if reflection fails
            }
        }
        // Return 20.0 (perfect TPS) if we can't measure it
        return 20.0;
    }
    
    private int calculateOptimalCores(int playerCount) {
        // Prefer centralized logic if available
        if (platformManager != null) {
            return platformManager.calculateFAHCores(playerCount);
        }
        String modeRaw = plugin.getConfig().getString("allocation.mode");
        String mode = (modeRaw == null ? "dynamic" : modeRaw).toLowerCase(Locale.ROOT);
        int totalCores = plugin.getConfig().getInt("server.total-cores", 8);
        int reservedCores = plugin.getConfig().getInt("server.reserved-cores", 1);
        
        return switch (mode) {
            case "dynamic" -> {
                double coresPerPlayer = plugin.getConfig().getDouble("allocation.dynamic.cores-per-player", 0.5);
                double neededForMC = reservedCores + (playerCount * coresPerPlayer);
                int availableForFAH = (int) Math.floor(totalCores - neededForMC);
                yield Math.max(0, Math.min(availableForFAH, totalCores - reservedCores));
            }
            case "tiered" -> {
                var section = plugin.getConfig().getConfigurationSection("allocation.tiered");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        String range = plugin.getConfig().getString("allocation.tiered." + key + ".players");
                        int fahCores = plugin.getConfig().getInt("allocation.tiered." + key + ".fah-cores");
                        if (matchesPlayerRange(playerCount, range)) {
                            yield fahCores;
                        }
                    }
                }
                yield 0;
            }
            default -> 0;
        };
    }
    
    private boolean matchesPlayerRange(int playerCount, String range) {
        if (range == null || range.isEmpty()) return false;
        try {
            if (range.contains("+")) {
                int min = Integer.parseInt(range.replace("+", ""));
                return playerCount >= min;
            } else if (range.contains("-")) {
                String[] parts = range.split("-");
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[1]);
                return playerCount >= min && playerCount <= max;
            } else {
                return playerCount == Integer.parseInt(range);
            }
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}