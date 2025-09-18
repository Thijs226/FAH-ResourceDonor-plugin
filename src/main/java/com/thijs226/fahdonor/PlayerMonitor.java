package com.thijs226.fahdonor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.lang.reflect.Method;

public class PlayerMonitor implements Listener {
    private final FAHResourceDonor plugin;
    private final FAHClientManager fahManager;
    private int checkTaskId = -1;
    private int lastPlayerCount = 0;
    private long lastChange = 0;
    private Method tpsMethod = null;
    
    public PlayerMonitor(FAHResourceDonor plugin, FAHClientManager fahManager) {
        this.plugin = plugin;
        this.fahManager = fahManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Try to find TPS method (Paper/Spigot specific)
        try {
            tpsMethod = Server.class.getMethod("getTPS");
            plugin.getLogger().info("TPS monitoring enabled (Paper detected)");
        } catch (NoSuchMethodException e) {
            plugin.getLogger().info("TPS monitoring disabled (using Spigot/vanilla)");
        }
    }
    
    public void start() {
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
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        lastChange = System.currentTimeMillis();
        
        // Check if player is admin and account isn't configured
        if (event.getPlayer().hasPermission("fahdonor.admin")) {
            String username = plugin.getConfig().getString("folding-at-home.account.username", "");
            String passkey = plugin.getConfig().getString("folding-at-home.account.passkey", "");
            
            if (username.isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    event.getPlayer().sendMessage(ChatColor.GOLD + "[FAH] " + ChatColor.YELLOW + 
                        "Folding@home account not configured! Use: /fah setup");
                    event.getPlayer().sendMessage(ChatColor.GRAY + 
                        "Your server's contributions aren't being tracked!");
                }, 60L); // 3 seconds after join
            } else if (passkey.isEmpty()) {
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
        
        if (plugin.getConfig().getBoolean("monitoring.tps-monitoring", true)) {
            double tps = getTPS();
            double minTps = plugin.getConfig().getDouble("monitoring.min-tps", 18.0);
            
            if (tps < minTps && cores > 0) {
                cores = Math.max(0, cores - 2);
                plugin.getLogger().warning("TPS low (" + String.format("%.1f", tps) + "), reducing FAH cores");
            }
        }
        
        // Log core adjustment
        if (cores != fahManager.getCurrentCores()) {
            plugin.getLogger().info("Adjusting FAH cores: " + fahManager.getCurrentCores() + " -> " + cores + 
                " (Players: " + playerCount + ")");
        }
        
        fahManager.setCores(cores);
        lastPlayerCount = playerCount;
    }
    
    private double getTPS() {
        if (tpsMethod != null) {
            try {
                double[] tpsArray = (double[]) tpsMethod.invoke(plugin.getServer());
                return tpsArray[0];
            } catch (Exception e) {
                // Fallback if reflection fails
            }
        }
        // Return 20.0 (perfect TPS) if we can't measure it
        return 20.0;
    }
    
    private int calculateOptimalCores(int playerCount) {
        String mode = plugin.getConfig().getString("allocation.mode", "dynamic");
        int totalCores = plugin.getConfig().getInt("server.total-cores", 8);
        int reservedCores = plugin.getConfig().getInt("server.reserved-cores", 1);
        
        switch (mode) {
            case "dynamic":
                double coresPerPlayer = plugin.getConfig().getDouble("allocation.dynamic.cores-per-player", 0.5);
                double neededForMC = reservedCores + (playerCount * coresPerPlayer);
                int availableForFAH = (int) Math.floor(totalCores - neededForMC);
                return Math.max(0, Math.min(availableForFAH, totalCores - reservedCores));
                
            case "tiered":
                for (String key : plugin.getConfig().getConfigurationSection("allocation.tiered").getKeys(false)) {
                    String range = plugin.getConfig().getString("allocation.tiered." + key + ".players");
                    int fahCores = plugin.getConfig().getInt("allocation.tiered." + key + ".fah-cores");
                    
                    if (matchesPlayerRange(playerCount, range)) {
                        return fahCores;
                    }
                }
                break;
        }
        
        return 0;
    }
    
    private boolean matchesPlayerRange(int playerCount, String range) {
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
    }
}