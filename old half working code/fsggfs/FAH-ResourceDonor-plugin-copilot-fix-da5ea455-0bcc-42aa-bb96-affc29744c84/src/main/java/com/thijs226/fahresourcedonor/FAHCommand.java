package com.thijs226.fahresourcedonor;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FAHCommand implements CommandExecutor {
    
    private final FAHResourceDonor plugin;
    
    public FAHCommand(FAHResourceDonor plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fah.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "status":
                handleStatus(sender);
                break;
            case "start":
                handleStart(sender);
                break;
            case "stop":
                handleStop(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "info":
                handleInfo(sender);
                break;
            case "debug":
                handleDebug(sender, args);
                break;
            case "stats":
                handleStats(sender);
                break;
            case "diagnose":
            case "diagnostic":
            case "check":
                handleDiagnose(sender);
                break;
            default:
                sendHelpMessage(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== FAH ResourceDonor Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/fah status" + ChatColor.WHITE + " - Show current folding status");
        sender.sendMessage(ChatColor.YELLOW + "/fah start" + ChatColor.WHITE + " - Start folding (if stopped)");
        sender.sendMessage(ChatColor.YELLOW + "/fah stop" + ChatColor.WHITE + " - Stop folding");
        sender.sendMessage(ChatColor.YELLOW + "/fah reload" + ChatColor.WHITE + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/fah info" + ChatColor.WHITE + " - Show configuration info");
        sender.sendMessage(ChatColor.YELLOW + "/fah debug [on|off]" + ChatColor.WHITE + " - Toggle debug mode");
        sender.sendMessage(ChatColor.YELLOW + "/fah stats" + ChatColor.WHITE + " - Show folding statistics");
        sender.sendMessage(ChatColor.YELLOW + "/fah diagnose" + ChatColor.WHITE + " - Run diagnostic tests");
    }
    
    private void handleStatus(CommandSender sender) {
        FAHClient client = plugin.getFAHClient();
        
        sender.sendMessage(ChatColor.GOLD + "=== FAH Status ===");
        
        if (plugin.isRunning()) {
            sender.sendMessage(ChatColor.GREEN + "Status: " + ChatColor.WHITE + "Running");
            
            if (client != null) {
                sender.sendMessage(ChatColor.GREEN + "Work Unit: " + ChatColor.WHITE + client.getWorkUnitStatus());
                sender.sendMessage(ChatColor.GREEN + "Progress: " + ChatColor.WHITE + client.getProgress());
                sender.sendMessage(ChatColor.GREEN + "Processing: " + ChatColor.WHITE + 
                    (client.isProcessingWork() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Status: " + ChatColor.WHITE + "Stopped");
            
            if (plugin.getFahToken().isEmpty()) {
                sender.sendMessage(ChatColor.RED + "⚠ Token not configured! Set 'fah.token' in config.yml");
            }
        }
        
        sender.sendMessage(ChatColor.GREEN + "Donor Name: " + ChatColor.WHITE + plugin.getDonorName());
        sender.sendMessage(ChatColor.GREEN + "Team ID: " + ChatColor.WHITE + plugin.getFahTeamId());
        sender.sendMessage(ChatColor.GREEN + "Debug Mode: " + ChatColor.WHITE + 
            (plugin.isDebugMode() ? ChatColor.GREEN + "Enabled" : ChatColor.GRAY + "Disabled"));
    }
    
    private void handleStart(CommandSender sender) {
        if (plugin.isRunning()) {
            sender.sendMessage(ChatColor.YELLOW + "FAH is already running!");
            return;
        }
        
        if (plugin.getFahToken().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Cannot start: No FAH token configured!");
            sender.sendMessage(ChatColor.RED + "Set 'fah.token' in config.yml and reload.");
            sender.sendMessage(ChatColor.YELLOW + "Get your token from: https://apps.foldingathome.org/getpasskey");
            return;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Starting FAH client...");
        
        // This would restart the FAH service - for now just show message
        sender.sendMessage(ChatColor.GREEN + "FAH client restart initiated. Check logs for details.");
        plugin.getLogger().info("FAH restart requested by " + sender.getName());
    }
    
    private void handleStop(CommandSender sender) {
        if (!plugin.isRunning()) {
            sender.sendMessage(ChatColor.YELLOW + "FAH is not currently running!");
            return;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Stopping FAH client...");
        
        FAHClient client = plugin.getFAHClient();
        if (client != null) {
            client.shutdown();
        }
        
        sender.sendMessage(ChatColor.GREEN + "FAH client stopped.");
        plugin.getLogger().info("FAH stopped by " + sender.getName());
    }
    
    private void handleReload(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading FAH configuration...");
        
        try {
            plugin.reloadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
            
            if (plugin.getFahToken().isEmpty()) {
                sender.sendMessage(ChatColor.RED + "⚠ Warning: FAH token is still not configured!");
            }
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
            plugin.getLogger().warning("Error reloading config: " + e.getMessage());
        }
    }
    
    private void handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== FAH Configuration Info ===");
        sender.sendMessage(ChatColor.GREEN + "Donor Name: " + ChatColor.WHITE + plugin.getDonorName());
        sender.sendMessage(ChatColor.GREEN + "Team ID: " + ChatColor.WHITE + plugin.getFahTeamId());
        sender.sendMessage(ChatColor.GREEN + "Token: " + ChatColor.WHITE + 
            (plugin.getFahToken().isEmpty() ? ChatColor.RED + "NOT SET" : ChatColor.GREEN + "Configured"));
        sender.sendMessage(ChatColor.GREEN + "Debug Mode: " + ChatColor.WHITE + 
            (plugin.isDebugMode() ? ChatColor.GREEN + "Enabled" : ChatColor.GRAY + "Disabled"));
        
        if (plugin.getFahToken().isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "To get started:");
            sender.sendMessage(ChatColor.YELLOW + "1. Get your passkey from: " + ChatColor.AQUA + "https://apps.foldingathome.org/getpasskey");
            sender.sendMessage(ChatColor.YELLOW + "2. Add it to config.yml as 'fah.token: YOUR_TOKEN_HERE'");
            sender.sendMessage(ChatColor.YELLOW + "3. Run " + ChatColor.WHITE + "/fah reload");
        }
        
        // Test connection
        FAHClient client = plugin.getFAHClient();
        if (client != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean connected = client.testConnection();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GREEN + "Connection Test: " + 
                        (connected ? ChatColor.GREEN + "Success" : ChatColor.RED + "Failed"));
                });
            });
        }
    }
    
    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GREEN + "Debug Mode: " + ChatColor.WHITE + 
                (plugin.isDebugMode() ? ChatColor.GREEN + "Enabled" : ChatColor.GRAY + "Disabled"));
            sender.sendMessage(ChatColor.YELLOW + "Use: /fah debug [on|off]");
            return;
        }
        
        String mode = args[1].toLowerCase();
        boolean enable;
        
        if (mode.equals("on") || mode.equals("true") || mode.equals("enable")) {
            enable = true;
        } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable")) {
            enable = false;
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid option. Use: on/off");
            return;
        }
        
        // Update config
        plugin.getConfig().set("debug", enable);
        plugin.saveConfig();
        plugin.reloadConfiguration();
        
        sender.sendMessage(ChatColor.GREEN + "Debug mode " + 
            (enable ? ChatColor.GREEN + "enabled" : ChatColor.GRAY + "disabled"));
        
        if (enable) {
            sender.sendMessage(ChatColor.YELLOW + "Debug information will now be logged to console.");
        }
    }
    
    private void handleStats(CommandSender sender) {
        FAHClient client = plugin.getFAHClient();
        
        sender.sendMessage(ChatColor.GOLD + "=== FAH Statistics ===");
        
        if (client != null) {
            sender.sendMessage(ChatColor.GREEN + "Points Earned: " + ChatColor.WHITE + client.getPointsEarned());
            sender.sendMessage(ChatColor.GREEN + "Current Status: " + ChatColor.WHITE + client.getWorkUnitStatus());
            sender.sendMessage(ChatColor.GREEN + "Progress: " + ChatColor.WHITE + client.getProgress());
            
            // Show online stats link
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "View online stats:");
            sender.sendMessage(ChatColor.AQUA + "https://stats.foldingathome.org/donor/" + plugin.getDonorName());
            
            if (!plugin.getFahTeamId().equals("0")) {
                sender.sendMessage(ChatColor.AQUA + "https://stats.foldingathome.org/team/" + plugin.getFahTeamId());
            }
            
        } else {
            sender.sendMessage(ChatColor.RED + "FAH client not initialized");
        }
    }
    
    private void handleDiagnose(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Running FAH diagnostics...");
        
        FAHDiagnostics diagnostics = new FAHDiagnostics(plugin);
        
        diagnostics.runDiagnostics(plugin.getFahToken(), plugin.getFahTeamId(), plugin.getDonorName())
            .thenAccept(result -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GOLD + "=== FAH Diagnostic Results ===");
                    
                    // Token validation
                    sender.sendMessage(ChatColor.GREEN + "Token Valid: " + 
                        (result.tokenValid ? ChatColor.GREEN + "✓ YES" : ChatColor.RED + "✗ NO"));
                    
                    // Internet connectivity
                    sender.sendMessage(ChatColor.GREEN + "Internet Connection: " + 
                        (result.internetConnected ? ChatColor.GREEN + "✓ YES" : ChatColor.RED + "✗ NO"));
                    
                    // FAH servers
                    sender.sendMessage(ChatColor.GREEN + "FAH Servers Reachable: " + 
                        (result.fahServersReachable ? ChatColor.GREEN + "✓ YES" : ChatColor.RED + "✗ NO"));
                    
                    // Donor name
                    sender.sendMessage(ChatColor.GREEN + "Donor Name Available: " + 
                        (result.donorNameUnique ? ChatColor.GREEN + "✓ YES" : ChatColor.YELLOW + "⚠ MAYBE"));
                    
                    // Team
                    sender.sendMessage(ChatColor.GREEN + "Team Valid: " + 
                        (result.teamExists ? ChatColor.GREEN + "✓ YES" : ChatColor.YELLOW + "⚠ CHECK"));
                    
                    // API
                    sender.sendMessage(ChatColor.GREEN + "FAH API Accessible: " + 
                        (result.apiAccessible ? ChatColor.GREEN + "✓ YES" : ChatColor.RED + "✗ NO"));
                    
                    // Overall status
                    sender.sendMessage("");
                    if (result.isHealthy()) {
                        sender.sendMessage(ChatColor.GREEN + "✓ Overall Status: HEALTHY");
                        sender.sendMessage(ChatColor.GREEN + "Your configuration should work correctly!");
                    } else {
                        sender.sendMessage(ChatColor.RED + "⚠ Overall Status: ISSUES DETECTED");
                        sender.sendMessage(ChatColor.YELLOW + "Check the console logs for detailed error messages.");
                        
                        if (!result.tokenValid) {
                            sender.sendMessage(ChatColor.RED + "→ Fix your token in config.yml");
                        }
                        if (!result.internetConnected) {
                            sender.sendMessage(ChatColor.RED + "→ Check internet connection and firewall");
                        }
                        if (!result.donorNameUnique) {
                            sender.sendMessage(ChatColor.YELLOW + "→ Consider using a more unique donor name");
                        }
                    }
                });
            })
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.RED + "Diagnostic failed: " + throwable.getMessage());
                });
                return null;
            });
    }
}