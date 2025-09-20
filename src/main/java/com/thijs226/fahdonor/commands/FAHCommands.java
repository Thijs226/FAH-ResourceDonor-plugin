package com.thijs226.fahdonor.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.thijs226.fahdonor.FAHClientManager;
import com.thijs226.fahdonor.FAHClientManager.FoldingCause;
import com.thijs226.fahdonor.FAHResourceDonor;
import com.thijs226.fahdonor.environment.PlatformResourceManager;
import com.thijs226.fahdonor.environment.ServerEnvironmentDetector.EnvironmentInfo;
import com.thijs226.fahdonor.environment.ServerEnvironmentDetector.EnvironmentType;
import com.thijs226.fahdonor.environment.ServerEnvironmentDetector.ResourceLimits;

public class FAHCommands implements CommandExecutor, TabCompleter {
    private final FAHResourceDonor plugin;
    
    public FAHCommands(FAHResourceDonor plugin) {
        this.plugin = plugin;
    }

    // Null-to-empty helper to satisfy static analyzers and avoid NPEs on isEmpty()
    private static String nz(String s) { return s == null ? "" : s; }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "setup" -> handleSetup(sender, args);
            case "token" -> handleToken(sender, args);
            case "showconfig" -> handleShowConfig(sender);
            case "passkey" -> handlePasskey(sender, args);
            case "account" -> handleAccount(sender, args);
            case "status" -> handleStatus(sender);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "info" -> handleInfo(sender);
            case "debug" -> handleDebug(sender, args);
            case "install" -> handleInstall(sender);
            case "stats" -> handleStats(sender);
            case "cause" -> handleCause(sender, args);
            case "vote" -> handleVote(sender, args);
            case "diseases" -> handleDiseases(sender);
            case "pause" -> handlePause(sender);
            case "resume" -> handleResume(sender);
            case "cores" -> handleCores(sender, args);
            case "web" -> handleWeb(sender);
            case "reload" -> handleReload(sender);
            case "environment", "env" -> handleEnvironment(sender);
            case "limits" -> handleLimits(sender);
            case "platform" -> handlePlatform(sender);
            case "verify" -> handleVerify(sender);
            case "optimize" -> handleOptimize(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }
    
    private boolean handleAccount(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fahdonor.account")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to manage F@H accounts!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /fah account <set|info|reset>");
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /fah account set <username> <team-id> [passkey]");
                    sender.sendMessage(ChatColor.GRAY + "Get a passkey at: https://apps.foldingathome.org/getpasskey");
                    return true;
                }

                String username = args[2];
                String teamId = args[3];
                String passkey = args.length > 4 ? args[4] : "";

                try {
                    Integer.parseInt(teamId);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Team ID must be a number!");
                    return true;
                }

                FAHClientManager.AccountInfo newAccount = new FAHClientManager.AccountInfo(username, teamId, passkey, false);
                plugin.getFAHManager().updateAccount(newAccount);

                sender.sendMessage(ChatColor.GREEN + "✓ F@H Account updated!");
                sender.sendMessage(ChatColor.GRAY + "Username: " + ChatColor.WHITE + username);
                sender.sendMessage(ChatColor.GRAY + "Team: " + ChatColor.WHITE + teamId);
                sender.sendMessage(ChatColor.GRAY + "Passkey: " + ChatColor.WHITE +
                        (passkey.isEmpty() ? "Not set" : "Set"));

                if (passkey.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Consider adding a passkey for bonus points!");
                    sender.sendMessage(ChatColor.GRAY + "Get one at: https://apps.foldingathome.org/getpasskey");
                }
            }

            case "info" -> {
                FAHClientManager.AccountInfo current = plugin.getFAHManager().getCurrentAccount();
                sender.sendMessage(ChatColor.GOLD + "=== Current F@H Account ===");
                sender.sendMessage(ChatColor.GRAY + "Username: " + ChatColor.WHITE + current.username);
                sender.sendMessage(ChatColor.GRAY + "Team: " + ChatColor.WHITE + current.teamId);
                sender.sendMessage(ChatColor.GRAY + "Passkey: " + ChatColor.WHITE +
                        (current.passkey.isEmpty() ? "Not set" : "Set"));
                sender.sendMessage(ChatColor.GRAY + "Anonymous: " + ChatColor.WHITE + current.anonymous);

                String statsUrl = "https://stats.foldingathome.org/donor/" + current.username;
                sender.sendMessage(ChatColor.GRAY + "Stats: " + ChatColor.AQUA + ChatColor.UNDERLINE + statsUrl);
            }

            case "reset" -> {
                plugin.getFAHManager().resetToDefaultAccount();
                sender.sendMessage(ChatColor.GREEN + "✓ Reset to default F@H account");
            }
            default -> {
                // No-op for unknown subcommand to preserve prior behavior
            }
        }
        
        return true;
    }
    
    private boolean handleCause(CommandSender sender, String[] args) {
        if (args.length < 2) {
            FoldingCause current = plugin.getFAHManager().getCurrentCause().primary;
            sender.sendMessage(ChatColor.GOLD + "=== Current Research Focus ===");
            sender.sendMessage(ChatColor.GRAY + "Primary: " + ChatColor.YELLOW + current.getDescription());
            
            if (!current.equals(FoldingCause.ANY)) {
                FoldingCause secondary = plugin.getFAHManager().getCurrentCause().secondary;
                sender.sendMessage(ChatColor.GRAY + "Fallback: " + ChatColor.WHITE + secondary.getDescription());
            }
            
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/fah cause set <disease>" + 
                             ChatColor.GRAY + " to change");
            return true;
        }
        
        if (args[1].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("fahdonor.cause")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to change the research cause!");
                return true;
            }
            
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "Available causes:");
                for (FoldingCause cause : FoldingCause.values()) {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + cause.getName() + 
                                     ChatColor.GRAY + ": " + cause.getDescription());
                }
                return true;
            }
            
            String causeName = args[2].toUpperCase();
            
            if (causeName.equals("COVID") || causeName.equals("CORONA")) {
                causeName = "COVID-19";
            }
            
            try {
                FoldingCause cause = FoldingCause.fromString(causeName);
                plugin.getFAHManager().updateCause(cause);

                sender.sendMessage(ChatColor.GREEN + "✓ Research focus updated to: " +
                                 ChatColor.YELLOW + cause.getDescription());

                final String actor = sender.getName();
                final String newCause = cause.getName();
                final String logMsg = actor + " changed research focus to " + newCause;
                plugin.getLogger().info(() -> logMsg);

            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Invalid cause! Use /fah diseases to see available options.");
            }
        }
        
        return true;
    }
    
    private boolean handleVote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can vote!");
            return true;
        }
        
        if (!plugin.getConfig().getBoolean("folding-at-home.democratic-cause.enabled")) {
            sender.sendMessage(ChatColor.RED + "Voting is disabled on this server!");
            return true;
        }
        
        if (args.length < 2) {
            plugin.getVotingManager().showStandings(player);
            player.sendMessage(ChatColor.GRAY + "Vote with: " + ChatColor.YELLOW + "/fah vote <disease>");
            return true;
        }
        
        if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("cancel")) {
            plugin.getVotingManager().removeVote(player);
            return true;
        }
        
        String causeName = args[1].toUpperCase();
        if (causeName.equals("COVID") || causeName.equals("CORONA")) {
            causeName = "COVID-19";
        }
        
        try {
            FoldingCause cause = FoldingCause.fromString(causeName);
            plugin.getVotingManager().castVote(player, cause);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid disease! Use /fah diseases to see options.");
        }
        
        return true;
    }
    
    private boolean handleDiseases(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Available Research Causes ===");
        sender.sendMessage(ChatColor.GRAY + "Choose what diseases to help research:");
        sender.sendMessage("");
        
        for (FoldingCause cause : FoldingCause.values()) {
            record Style(ChatColor color, String emoji) {}

            Style style = switch (cause) {
                case COVID_19 -> new Style(ChatColor.RED, "🦠 ");
                case CANCER -> new Style(ChatColor.LIGHT_PURPLE, "🎗 ");
                case ALZHEIMERS, PARKINSONS, HUNTINGTONS -> new Style(ChatColor.YELLOW, "🧠 ");
                case DIABETES -> new Style(ChatColor.AQUA, "💉 ");
                case HIGH_PRIORITY -> new Style(ChatColor.GOLD, "⭐ ");
                case ANY -> new Style(ChatColor.GREEN, "✅ ");
                default -> new Style(ChatColor.WHITE, "");
            };

            sender.sendMessage(style.emoji() + style.color() + cause.getName() + ChatColor.GRAY + " - " +
                    ChatColor.WHITE + cause.getDescription());
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Set with: " + ChatColor.YELLOW + "/fah cause set <disease>");
        
        if (plugin.getConfig().getBoolean("folding-at-home.democratic-cause.enabled")) {
            sender.sendMessage(ChatColor.GRAY + "Vote with: " + ChatColor.YELLOW + "/fah vote <disease>");
        }
        
        return true;
    }
    
    private boolean handleSetup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fahdonor.account")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "=== Folding@home Account Setup ===");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "Option 1: Traditional Setup (Username + Passkey)");
            sender.sendMessage(ChatColor.YELLOW + "/fah setup <username> <team-id> [passkey]");
            sender.sendMessage(ChatColor.GRAY + "Example: /fah setup MyServer 12345 abc123...");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "Option 2: Account Token (Links to existing account)");
            sender.sendMessage(ChatColor.YELLOW + "/fah setup token <account-token> [machine-name]");
            sender.sendMessage(ChatColor.GRAY + "Example: /fah setup token abc123def456... MinecraftServer");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.WHITE + "Get username at: " + ChatColor.AQUA + "https://foldingathome.org/start-folding/");
            sender.sendMessage(ChatColor.WHITE + "Get passkey at: " + ChatColor.AQUA + "https://apps.foldingathome.org/getpasskey");
            sender.sendMessage(ChatColor.WHITE + "Get token from your F@H account dashboard when logged in");
            return true;
        }
        
        // Check if using token-based setup
        if (args[1].equalsIgnoreCase("token")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /fah setup token <account-token> [machine-name]");
                sender.sendMessage(ChatColor.GRAY + "The token links this server to your F@H account");
                return true;
            }
            
            String accountToken = args[2];
            String machineName = args.length > 3 ? args[3] : "Minecraft-Server";
            
            // Save token configuration
            plugin.getConfig().set("folding-at-home.account.account-token", accountToken);
            plugin.getConfig().set("folding-at-home.account.machine-name", machineName);
            // Clear traditional settings when using token
            plugin.getConfig().set("folding-at-home.account.username", "");
            plugin.getConfig().set("folding-at-home.account.team-id", "");
            plugin.getConfig().set("folding-at-home.account.passkey", "");
            plugin.saveConfig();
            
            // Trigger reconfiguration
            plugin.getFAHManager().reconfigureWithToken(accountToken, machineName);
            
            sender.sendMessage(ChatColor.GREEN + "✓ F@H account linked with token!");
            sender.sendMessage(ChatColor.GRAY + "Machine name: " + ChatColor.WHITE + machineName);
            sender.sendMessage(ChatColor.GRAY + "This server is now linked to your account");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "⚠ Important: Generate a new token after setup");
            sender.sendMessage(ChatColor.GRAY + "This prevents others from using your token");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Monitor this machine in your F@H account dashboard");
            
            return true;
        }
        
        // Traditional username/passkey setup
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /fah setup <username> <team-id> [passkey]");
            return true;
        }
        
        String username = args[1];
        String teamId = args[2];
        String passkey = args.length > 3 ? args[3] : "";
        
        // Validate team ID
        try {
            Integer.parseInt(teamId);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Team ID must be a number! Use 0 for no team.");
            return true;
        }
        
        // Validate passkey if provided
        if (!passkey.isEmpty() && passkey.length() != 32) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ Passkey should be 32 characters. Make sure you copied it correctly!");
        }
        
        // Save to config
        plugin.getConfig().set("folding-at-home.account.username", username);
        plugin.getConfig().set("folding-at-home.account.team-id", teamId);
        plugin.getConfig().set("folding-at-home.account.passkey", passkey);
        // Clear token when using traditional setup
        plugin.getConfig().set("folding-at-home.account.account-token", "");
        plugin.saveConfig();
        
        // Update FAH client
        FAHClientManager.AccountInfo newAccount = new FAHClientManager.AccountInfo(username, teamId, passkey, false);
        plugin.getFAHManager().updateAccount(newAccount);
        
        sender.sendMessage(ChatColor.GREEN + "✓ Folding@home account configured!");
        sender.sendMessage(ChatColor.GRAY + "Username: " + ChatColor.WHITE + username);
        sender.sendMessage(ChatColor.GRAY + "Team: " + ChatColor.WHITE + teamId);
        sender.sendMessage(ChatColor.GRAY + "Passkey: " + ChatColor.WHITE + 
            (passkey.isEmpty() ? ChatColor.RED + "Not set (missing bonus points!)" : ChatColor.GREEN + "Set ✓"));
        
        if (passkey.isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "⚠ Get your passkey for bonus points:");
            sender.sendMessage(ChatColor.AQUA + "https://apps.foldingathome.org/getpasskey");
            sender.sendMessage(ChatColor.YELLOW + "Then use: /fah passkey <your-passkey>");
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "View your stats at:");
        sender.sendMessage(ChatColor.AQUA + "https://stats.foldingathome.org/donor/" + username);
        
        return true;
    }
    
    private boolean handleToken(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fahdonor.account")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "=== Account Token Configuration ===");
            sender.sendMessage(ChatColor.YELLOW + "Usage: /fah token <account-token> [machine-name]");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.WHITE + "Account tokens link this server to your F@H account");
            sender.sendMessage(ChatColor.WHITE + "Get your token from the F@H dashboard when logged in");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "⚠ Security Warning:");
            sender.sendMessage(ChatColor.GRAY + "• Anyone with this token can link machines to your account");
            sender.sendMessage(ChatColor.GRAY + "• Generate a new token after setting up");
            sender.sendMessage(ChatColor.GRAY + "• Already linked machines will remain linked");
            
            String currentToken = plugin.getConfig().getString("folding-at-home.account.account-token", "");
            if (!nz(currentToken).isEmpty()) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.GREEN + "Token is currently configured");
                sender.sendMessage(ChatColor.GRAY + "Machine name: " + 
                    plugin.getConfig().getString("folding-at-home.account.machine-name", "Minecraft-Server"));
            }
            return true;
        }
        
        String accountToken = args[1];
        String machineName = args.length > 2 ? args[2] : "Minecraft-Server";
        
        // Save token configuration
        plugin.getConfig().set("folding-at-home.account.account-token", accountToken);
        plugin.getConfig().set("folding-at-home.account.machine-name", machineName);
        plugin.saveConfig();
        
        // Trigger reconfiguration
        plugin.getFAHManager().reconfigureWithToken(accountToken, machineName);
        
        sender.sendMessage(ChatColor.GREEN + "✓ Account token configured!");
        sender.sendMessage(ChatColor.GRAY + "Machine name: " + ChatColor.WHITE + machineName);
        sender.sendMessage(ChatColor.GRAY + "This server will appear in your F@H account");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Remember to generate a new token in your account");
        sender.sendMessage(ChatColor.GRAY + "This prevents others from using this token");
        
        return true;
    }
    
    private boolean handlePasskey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fahdonor.account")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "=== Passkey Configuration ===");
            sender.sendMessage(ChatColor.YELLOW + "Usage: /fah passkey <your-32-character-passkey>");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.WHITE + "A passkey gives you bonus points for your contributions!");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.WHITE + "Get your passkey at:");
            sender.sendMessage(ChatColor.AQUA + "https://apps.foldingathome.org/getpasskey");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Example passkey: 1234567890abcdef1234567890abcdef");
            
            String currentPasskey = plugin.getConfig().getString("folding-at-home.account.passkey", "");
            if (!nz(currentPasskey).isEmpty()) {
                sender.sendMessage("");
                int pkLen = nz(currentPasskey).length();
                sender.sendMessage(ChatColor.GREEN + "Current passkey is set (" + pkLen + " chars)");
            } else {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.RED + "No passkey currently set - you're missing bonus points!");
            }
            return true;
        }
        
        String passkey = args[1];
        
        // Validate passkey
        if (passkey.length() != 32) {
            sender.sendMessage(ChatColor.RED + "Invalid passkey! It should be exactly 32 characters.");
            sender.sendMessage(ChatColor.YELLOW + "Make sure you copied the entire passkey from the email.");
            return true;
        }
        
        // Check if it looks like a valid passkey (hexadecimal)
        if (!passkey.matches("[0-9a-fA-F]{32}")) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ This doesn't look like a valid passkey.");
            sender.sendMessage(ChatColor.YELLOW + "Passkeys are 32 hexadecimal characters (0-9, a-f)");
        }
        
        // Save passkey
        plugin.getConfig().set("folding-at-home.account.passkey", passkey);
        plugin.saveConfig();
        
        // Update FAH client
        FAHClientManager.AccountInfo current = plugin.getFAHManager().getCurrentAccount();
        FAHClientManager.AccountInfo updated = new FAHClientManager.AccountInfo(
            current.username, current.teamId, passkey, current.anonymous
        );
        plugin.getFAHManager().updateAccount(updated);
        
        sender.sendMessage(ChatColor.GREEN + "✓ Passkey configured successfully!");
        sender.sendMessage(ChatColor.WHITE + "You'll now earn bonus points for your contributions!");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Note: Bonus points activate after completing 10 Work Units");
        sender.sendMessage(ChatColor.GRAY + "Track your progress at: " + ChatColor.AQUA + 
            "https://stats.foldingathome.org/donor/" + current.username);
        
        return true;
    }
    
    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== FAH Resource Donor Status ===");
        
        // Check FAH installation
        boolean fahInstalled = plugin.isFAHInstalled();
        if (fahInstalled) {
            sender.sendMessage(ChatColor.GRAY + "FAH Client: " + ChatColor.GREEN + "Installed ✓");
        } else {
            sender.sendMessage(ChatColor.GRAY + "FAH Client: " + ChatColor.RED + "Not Installed ✗");
            sender.sendMessage(ChatColor.YELLOW + "Run '/fah install' for installation instructions");
        }
        
        // Check account configuration
    String username = plugin.getConfig().getString("folding-at-home.account.username", "");
    String passkey = plugin.getConfig().getString("folding-at-home.account.passkey", "");
    String teamId = plugin.getConfig().getString("folding-at-home.account.team-id", "");
    String accountToken = plugin.getConfig().getString("folding-at-home.account.account-token", "");
    String u = nz(username);
    String p = nz(passkey);
    String t = nz(teamId);
    String tok = nz(accountToken);
        
        if (!tok.isEmpty()) {
            // Token-based configuration
            String machineName = plugin.getConfig().getString("folding-at-home.account.machine-name", "Minecraft-Server");
            sender.sendMessage(ChatColor.GRAY + "Config Type: " + ChatColor.AQUA + "Account Token");
            sender.sendMessage(ChatColor.GRAY + "Machine Name: " + ChatColor.WHITE + machineName);
            sender.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.GREEN + "Linked to your account ✓");
        } else if (!u.isEmpty() && !u.equals("Thijs226_MCServer_Guest")) {
            // Traditional configuration
            sender.sendMessage(ChatColor.GRAY + "Config Type: " + ChatColor.AQUA + "Traditional (Username)");
            sender.sendMessage(ChatColor.GRAY + "Account: " + ChatColor.GREEN + u + " ✓");
            sender.sendMessage(ChatColor.GRAY + "Team: " + ChatColor.WHITE + t);
            sender.sendMessage(ChatColor.GRAY + "Passkey: " + 
                (p.isEmpty() ? ChatColor.RED + "Not set ✗ (missing bonus!)" : ChatColor.GREEN + "Set ✓"));
        } else {
            // Not configured
            sender.sendMessage(ChatColor.GRAY + "Account: " + ChatColor.RED + "Not configured ✗");
            sender.sendMessage(ChatColor.YELLOW + "Use '/fah setup' or '/fah token' to configure");
        }
        
        // Show research focus
        FoldingCause cause = plugin.getFAHManager().getCurrentCause().primary;
        sender.sendMessage(ChatColor.GRAY + "Research: " + ChatColor.YELLOW + cause.getDescription());
        
        // Show current status
        sender.sendMessage(ChatColor.GRAY + "Players Online: " + ChatColor.WHITE + 
            Bukkit.getOnlinePlayers().size());
        sender.sendMessage(ChatColor.GRAY + "Plugin Status: " + ChatColor.GREEN + "Active");
        
        // Quick links
    if (!tok.isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Dashboard: " + ChatColor.AQUA + ChatColor.UNDERLINE +
                "https://app.foldingathome.org/");
    } else if (!u.isEmpty() && !u.equals("Thijs226_MCServer_Guest")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Your stats: " + ChatColor.AQUA + ChatColor.UNDERLINE +
        "https://stats.foldingathome.org/donor/" + u);
        }
        
        return true;
    }
    
    private boolean handleInstall(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== FAH Installation Instructions ===");
        sender.sendMessage(ChatColor.YELLOW + "For Linux servers:");
        sender.sendMessage(ChatColor.WHITE + "cd plugins/FAHResourceDonor/folding-at-home");
        sender.sendMessage(ChatColor.WHITE + "wget https://download.foldingathome.org/releases/public/fah-client/debian-10-64bit/release/fah-client_8.3.18_amd64.deb");
        sender.sendMessage(ChatColor.WHITE + "dpkg -x fah-client_8.3.18_amd64.deb .");
        sender.sendMessage(ChatColor.WHITE + "mv usr/bin/fah-client FAHClient");
        sender.sendMessage(ChatColor.WHITE + "chmod +x FAHClient");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "For Windows:");
        sender.sendMessage(ChatColor.WHITE + "Download from: https://foldingathome.org/start-folding/");
        sender.sendMessage(ChatColor.WHITE + "Install and copy FAHClient.exe to:");
        sender.sendMessage(ChatColor.WHITE + "plugins\\FAHResourceDonor\\folding-at-home\\");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "Then restart the server");
        
        return true;
    }
    
    private boolean handleStats(CommandSender sender) {
        plugin.getStatisticsManager().displayStats(sender);
        return true;
    }
    
    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "=== FAH Debug Commands ===");
            sender.sendMessage(ChatColor.YELLOW + "/fah debug check" + ChatColor.GRAY + " - Check FAH process status");
            sender.sendMessage(ChatColor.YELLOW + "/fah debug start" + ChatColor.GRAY + " - Force start FAH client");
            sender.sendMessage(ChatColor.YELLOW + "/fah debug stop" + ChatColor.GRAY + " - Stop FAH client");
            sender.sendMessage(ChatColor.YELLOW + "/fah debug logs" + ChatColor.GRAY + " - Show FAH logs");
            sender.sendMessage(ChatColor.YELLOW + "/fah debug unpause" + ChatColor.GRAY + " - Force unpause");
            sender.sendMessage(ChatColor.YELLOW + "/fah debug info" + ChatColor.GRAY + " - Show all debug info");
            return true;
        }
        
        FAHClientManager fahManager = plugin.getFAHManager();
        
        switch (args[1].toLowerCase()) {
            case "check" -> {
                sender.sendMessage(ChatColor.GOLD + "=== FAH Process Check ===");

                // Check if FAH binary exists
                File fahDir = new File(plugin.getDataFolder(), "folding-at-home");
                File fahBinary = new File(fahDir, "FAHClient");
                File fahBinaryExe = new File(fahDir, "FAHClient.exe");

                sender.sendMessage(ChatColor.GRAY + "FAH Directory: " + ChatColor.WHITE + fahDir.getAbsolutePath());
                sender.sendMessage(ChatColor.GRAY + "FAH Binary exists: " + ChatColor.WHITE +
                        (fahBinary.exists() || fahBinaryExe.exists() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

                // Check if process is running
                boolean isRunning = fahManager.isFAHRunning();
                sender.sendMessage(ChatColor.GRAY + "FAH Process running: " +
                        (isRunning ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

                // Check control connection
                boolean connected = fahManager.isConnected();
                sender.sendMessage(ChatColor.GRAY + "Control connected: " +
                        (connected ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

                // Show current core allocation
                sender.sendMessage(ChatColor.GRAY + "Current cores: " + ChatColor.WHITE + fahManager.getCurrentCores());

                // Check config.xml
                File configFile = new File(fahDir, "config.xml");
                sender.sendMessage(ChatColor.GRAY + "Config exists: " +
                        (configFile.exists() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

                // Check log file
                File logFile = new File(fahDir, "log.txt");
                sender.sendMessage(ChatColor.GRAY + "Log file exists: " +
                        (logFile.exists() ? ChatColor.GREEN + "Yes (" + logFile.length() + " bytes)" : ChatColor.RED + "No"));
            }

            case "start" -> {
                sender.sendMessage(ChatColor.YELLOW + "Force starting FAH client...");
                fahManager.forceStart();
                sender.sendMessage(ChatColor.GREEN + "Start command sent. Check status in a few seconds.");
            }

            case "stop" -> {
                sender.sendMessage(ChatColor.YELLOW + "Stopping FAH client...");
                fahManager.shutdown();
                sender.sendMessage(ChatColor.GREEN + "Stop command sent.");
            }

            case "unpause" -> {
                sender.sendMessage(ChatColor.YELLOW + "Force unpausing FAH...");
                fahManager.forceUnpause();
                sender.sendMessage(ChatColor.GREEN + "Unpause command sent.");
            }

            case "logs" -> {
                File fahLogFile = new File(plugin.getDataFolder(), "folding-at-home/log.txt");
                if (fahLogFile.exists()) {
                    sender.sendMessage(ChatColor.GOLD + "=== Last 20 lines of FAH log ===");
                    try {
                        List<String> lines = Files.readAllLines(fahLogFile.toPath());
                        int start = Math.max(0, lines.size() - 20);
                        for (int i = start; i < lines.size(); i++) {
                            sender.sendMessage(ChatColor.GRAY + lines.get(i));
                        }
                    } catch (IOException e) {
                        sender.sendMessage(ChatColor.RED + "Could not read log file: " + e.getMessage());
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "No FAH log file found");
                    sender.sendMessage(ChatColor.YELLOW + "FAH might not be running or hasn't created logs yet");
                }
            }

            case "info" -> {
                sender.sendMessage(ChatColor.GOLD + "=== Complete FAH Debug Info ===");

                // System info
                sender.sendMessage(ChatColor.AQUA + "System:");
                sender.sendMessage(ChatColor.GRAY + "OS: " + ChatColor.WHITE + System.getProperty("os.name"));
                sender.sendMessage(ChatColor.GRAY + "Cores: " + ChatColor.WHITE + Runtime.getRuntime().availableProcessors());

                // Config info
                sender.sendMessage(ChatColor.AQUA + "Configuration:");
                sender.sendMessage(ChatColor.GRAY + "Total cores: " + ChatColor.WHITE +
                        plugin.getConfig().getInt("server.total-cores"));
                sender.sendMessage(ChatColor.GRAY + "Reserved cores: " + ChatColor.WHITE +
                        plugin.getConfig().getInt("server.reserved-cores"));

                // Account info
                String token = plugin.getConfig().getString("folding-at-home.account.account-token", "");
                String username = plugin.getConfig().getString("folding-at-home.account.username", "");

                if (!nz(token).isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Auth type: " + ChatColor.WHITE + "Token");
                    sender.sendMessage(ChatColor.GRAY + "Machine: " + ChatColor.WHITE +
                            plugin.getConfig().getString("folding-at-home.account.machine-name", "Minecraft-Server"));
                } else if (!nz(username).isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Auth type: " + ChatColor.WHITE + "Username");
                    sender.sendMessage(ChatColor.GRAY + "User: " + ChatColor.WHITE + username);
                }

                // FAH status
                sender.sendMessage(ChatColor.AQUA + "FAH Status:");
                sender.sendMessage(ChatColor.GRAY + "Process: " +
                        (fahManager.isFAHRunning() ? ChatColor.GREEN + "Running" : ChatColor.RED + "Not running"));
                sender.sendMessage(ChatColor.GRAY + "Connected: " +
                        (fahManager.isConnected() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
                sender.sendMessage(ChatColor.GRAY + "Current cores: " + ChatColor.WHITE + fahManager.getCurrentCores());
            }

            default -> sender.sendMessage(ChatColor.RED + "Unknown debug command. Use /fah debug for help.");
        }
        
        return true;
    }
    
    private boolean handlePause(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.pause")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        plugin.getFAHManager().setCores(0);
        sender.sendMessage(ChatColor.YELLOW + "FAH paused");
        return true;
    }
    
    private boolean handleResume(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.pause")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        // Resume with appropriate core count
        sender.sendMessage(ChatColor.GREEN + "FAH resumed");
        return true;
    }
    
    private boolean handleCores(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fahdonor.cores")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /fah cores <number>");
            return true;
        }
        
        try {
            int cores = Integer.parseInt(args[1]);
            plugin.getFAHManager().setCores(cores);
            sender.sendMessage(ChatColor.GREEN + "Set FAH to use " + cores + " cores");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number!");
        }
        
        return true;
    }
    
    private boolean handleWeb(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        int webPort = plugin.getConfig().getInt("folding-at-home.ports.web-port", 0);
        
        if (webPort == 0) {
            sender.sendMessage(ChatColor.RED + "Web interface is disabled!");
            sender.sendMessage(ChatColor.YELLOW + "This is normal for shared hosting with single port.");
            sender.sendMessage(ChatColor.GRAY + "FAH is running in no-port mode.");
            return true;
        }
        
        boolean webEnabled = plugin.getConfig().getBoolean("folding-at-home.web-control.enabled");
        
        if (!webEnabled) {
            sender.sendMessage(ChatColor.RED + "Web interface is disabled in config!");
            return true;
        }
        
        String serverIP = plugin.getServer().getIp();
        if (serverIP.isEmpty()) serverIP = "your-server-ip";
        
        sender.sendMessage(ChatColor.GOLD + "=== F@H Web Interface ===");
        sender.sendMessage(ChatColor.GRAY + "Local: " + ChatColor.AQUA + "http://localhost:" + webPort);
        sender.sendMessage(ChatColor.GRAY + "Remote: " + ChatColor.AQUA + "http://" + serverIP + ":" + webPort);
        sender.sendMessage(ChatColor.YELLOW + "Make sure port " + webPort + " is open in your firewall!");
        
        return true;
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Reloading FAH configuration...");
        plugin.reloadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== FAH Resource Donor Commands ===");
        
        sender.sendMessage(ChatColor.AQUA + "Quick Setup (Choose One):");
        sender.sendMessage(ChatColor.YELLOW + "/fah setup <user> <team> [passkey]" + ChatColor.GRAY + " - Traditional setup");
        sender.sendMessage(ChatColor.YELLOW + "/fah token <account-token>" + ChatColor.GRAY + " - Link with account token");
        sender.sendMessage(ChatColor.YELLOW + "/fah passkey <token>" + ChatColor.GRAY + " - Set passkey for bonus points");
        sender.sendMessage("");
        
        sender.sendMessage(ChatColor.AQUA + "Basic Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/fah status" + ChatColor.GRAY + " - Check current status");
        sender.sendMessage(ChatColor.YELLOW + "/fah info" + ChatColor.GRAY + " - Detailed information");
        sender.sendMessage(ChatColor.YELLOW + "/fah stats" + ChatColor.GRAY + " - View contribution statistics");
        sender.sendMessage(ChatColor.YELLOW + "/fah diseases" + ChatColor.GRAY + " - List research causes");
        sender.sendMessage(ChatColor.YELLOW + "/fah vote <disease>" + ChatColor.GRAY + " - Vote for research focus");
        
        if (sender.hasPermission("fahdonor.account")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "Account Management:");
            sender.sendMessage(ChatColor.YELLOW + "/fah account info" + ChatColor.GRAY + " - View account details");
            sender.sendMessage(ChatColor.YELLOW + "/fah account set <user> <team> [pass]" + 
                ChatColor.GRAY + " - Detailed setup");
        }
        
        if (sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "Admin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/fah start" + ChatColor.GRAY + " - Start FAH client");
            sender.sendMessage(ChatColor.YELLOW + "/fah stop" + ChatColor.GRAY + " - Stop FAH client");
            sender.sendMessage(ChatColor.YELLOW + "/fah install" + ChatColor.GRAY + " - Installation help");
            sender.sendMessage(ChatColor.YELLOW + "/fah pause" + ChatColor.GRAY + " - Pause folding");
            sender.sendMessage(ChatColor.YELLOW + "/fah resume" + ChatColor.GRAY + " - Resume folding");
            sender.sendMessage(ChatColor.YELLOW + "/fah cores <number>" + ChatColor.GRAY + " - Set core count");
            sender.sendMessage(ChatColor.YELLOW + "/fah web" + ChatColor.GRAY + " - Web interface info");
            sender.sendMessage(ChatColor.YELLOW + "/fah reload" + ChatColor.GRAY + " - Reload config");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "Environment Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/fah environment" + ChatColor.GRAY + " - Show detected environment");
            sender.sendMessage(ChatColor.YELLOW + "/fah limits" + ChatColor.GRAY + " - View resource limits");
            sender.sendMessage(ChatColor.YELLOW + "/fah platform" + ChatColor.GRAY + " - Platform-specific info");
            sender.sendMessage(ChatColor.YELLOW + "/fah optimize" + ChatColor.GRAY + " - Apply environment optimizations");
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Get passkey: " + ChatColor.AQUA + "https://apps.foldingathome.org/getpasskey");
        sender.sendMessage(ChatColor.GRAY + "F@H Dashboard: " + ChatColor.AQUA + "https://app.foldingathome.org/");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(Arrays.asList(
                "setup", "token", "passkey", "status", "info", "start", "stop", "stats", "diseases", "vote", "showconfig", "verify"
            ));
            
            if (sender.hasPermission("fahdonor.account")) {
                commands.add("account");
            }
            
            if (sender.hasPermission("fahdonor.admin")) {
                commands.addAll(Arrays.asList("debug", "install", "pause", "resume", "cores", "web", "reload", "cause",
                    "environment", "env", "limits", "platform", "optimize"));
            }
            
            return commands.stream()
                .filter(c -> c.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return Arrays.asList("check", "start", "stop", "unpause", "logs", "info");
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("account")) {
                return Arrays.asList("set", "info", "reset");
            }
            if (args[0].equalsIgnoreCase("cause")) {
                return Arrays.asList("set", "info");
            }
            if (args[0].equalsIgnoreCase("vote")) {
                List<String> causes = new ArrayList<>();
                for (FoldingCause cause : FoldingCause.values()) {
                    causes.add(cause.getName().toLowerCase());
                }
                causes.add("remove");
                return causes;
            }
        }
        
        if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
            List<String> causes = new ArrayList<>();
            for (FoldingCause cause : FoldingCause.values()) {
                causes.add(cause.getName().toLowerCase());
            }
            return causes;
        }
        
        return null;
    }

    private boolean handleShowConfig(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin") && !sender.hasPermission("fahdonor.account")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view the FAH config!");
            return true;
        }

        File fahDir = new File(plugin.getDataFolder(), "folding-at-home");
        File configFile = new File(fahDir, "config.xml");

        if (!configFile.exists()) {
            sender.sendMessage(ChatColor.RED + "No FAH config found (config.xml) in plugin data folder.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== FAH Config ===");
        try {
            List<String> lines = Files.readAllLines(configFile.toPath());
            String user = "(unknown)";
            String team = "(unknown)";
            String pass = "(not set)";
            for (String line : lines) {
                String l = line.trim();
                if (l.startsWith("<user")) {
                    int s = l.indexOf("v=");
                    if (s != -1) user = l.substring(s + 2).replaceAll("[\'\">/\\s]", "");
                }
                if (l.startsWith("<team")) {
                    int s = l.indexOf("v=");
                    if (s != -1) team = l.substring(s + 2).replaceAll("[\'\">/\\s]", "");
                }
                if (l.startsWith("<passkey")) {
                    pass = "[REDACTED]";
                }
            }
            sender.sendMessage(ChatColor.GRAY + "User: " + ChatColor.WHITE + user);
            sender.sendMessage(ChatColor.GRAY + "Team: " + ChatColor.WHITE + team);
            sender.sendMessage(ChatColor.GRAY + "Passkey: " + ChatColor.WHITE + pass);
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Could not read config.xml: " + e.getMessage());
        }

        return true;
    }
    
    /**
     * Handle environment command - shows detected environment information
     */
    private boolean handleEnvironment(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view environment information!");
            return true;
        }
        
        PlatformResourceManager platformManager = plugin.getPlatformManager();
        if (platformManager == null) {
            sender.sendMessage(ChatColor.RED + "Platform manager not initialized!");
            return true;
        }
        
        EnvironmentInfo envInfo = platformManager.getEnvironmentInfo();
        ResourceLimits limits = platformManager.getResourceLimits();
        
        sender.sendMessage(ChatColor.GOLD + "========= Environment Information =========");
        sender.sendMessage(ChatColor.YELLOW + "Detected Environment: " + ChatColor.WHITE + envInfo.getType().getDisplayName());
        sender.sendMessage(ChatColor.YELLOW + "Containerized: " + ChatColor.WHITE + (envInfo.isContainerized() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.YELLOW + "Resource Restricted: " + ChatColor.WHITE + (envInfo.isResourceRestricted() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.YELLOW + "Strict Limits: " + ChatColor.WHITE + (platformManager.isStrictLimitsEnforced() ? "Enabled" : "Disabled"));
        
        sender.sendMessage(ChatColor.GOLD + "========= Resource Limits =========");
        sender.sendMessage(ChatColor.YELLOW + "Max Cores: " + ChatColor.WHITE + limits.getMaxCores());
        sender.sendMessage(ChatColor.YELLOW + "Recommended Cores: " + ChatColor.WHITE + limits.getRecommendedCores());
        sender.sendMessage(ChatColor.YELLOW + "Max Memory: " + ChatColor.WHITE + limits.getMaxMemoryMB() + "MB");
        
        // Show environment-specific metadata
        if (!envInfo.getMetadata().isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "========= Environment Details =========");
            for (Map.Entry<String, String> entry : envInfo.getMetadata().entrySet()) {
                if (entry.getValue() != null) {
                    sender.sendMessage(ChatColor.YELLOW + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
                }
            }
        }
        
        return true;
    }
    
    /**
     * Handle limits command - shows current resource limits and allocation
     */
    private boolean handleLimits(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view resource limits!");
            return true;
        }
        
        PlatformResourceManager platformManager = plugin.getPlatformManager();
        if (platformManager == null) {
            sender.sendMessage(ChatColor.RED + "Platform manager not initialized!");
            return true;
        }
        
        ResourceLimits limits = platformManager.getResourceLimits();
        int playerCount = Bukkit.getOnlinePlayers().size();
        int currentFAHCores = platformManager.calculateFAHCores(playerCount);
        
        sender.sendMessage(ChatColor.GOLD + "========= Current Resource Allocation =========");
        sender.sendMessage(ChatColor.YELLOW + "Online Players: " + ChatColor.WHITE + playerCount);
        sender.sendMessage(ChatColor.YELLOW + "Current FAH Cores: " + ChatColor.WHITE + currentFAHCores);
        
        sender.sendMessage(ChatColor.GOLD + "========= Environment Limits =========");
        sender.sendMessage(ChatColor.YELLOW + "Max Allowed Cores: " + ChatColor.WHITE + limits.getMaxCores());
        sender.sendMessage(ChatColor.YELLOW + "Recommended Cores: " + ChatColor.WHITE + limits.getRecommendedCores());
        sender.sendMessage(ChatColor.YELLOW + "Max Memory: " + ChatColor.WHITE + limits.getMaxMemoryMB() + "MB");
        
        sender.sendMessage(ChatColor.GOLD + "========= Configuration =========");
        sender.sendMessage(ChatColor.YELLOW + "Configured Total Cores: " + ChatColor.WHITE + 
                          plugin.getConfig().getInt("server.total-cores", 8));
        sender.sendMessage(ChatColor.YELLOW + "Reserved Cores: " + ChatColor.WHITE + 
                          plugin.getConfig().getInt("server.reserved-cores", 1));
        sender.sendMessage(ChatColor.YELLOW + "Allocation Mode: " + ChatColor.WHITE + 
                          plugin.getConfig().getString("allocation.mode", "dynamic"));
        
        return true;
    }
    
    /**
     * Handle platform command - shows platform-specific settings and recommendations
     */
    private boolean handlePlatform(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view platform information!");
            return true;
        }
        
        PlatformResourceManager platformManager = plugin.getPlatformManager();
        if (platformManager == null) {
            sender.sendMessage(ChatColor.RED + "Platform manager not initialized!");
            return true;
        }
        
        EnvironmentInfo envInfo = platformManager.getEnvironmentInfo();
        
        sender.sendMessage(ChatColor.GOLD + "========= Platform Information =========");
        sender.sendMessage(ChatColor.YELLOW + "Platform: " + ChatColor.WHITE + envInfo.getType().getDisplayName());
        
        // Show platform-specific recommendations
        switch (envInfo.getType()) {
            case PTERODACTYL -> {
                sender.sendMessage(ChatColor.GREEN + "Pterodactyl Panel Detected:");
                sender.sendMessage(ChatColor.WHITE + "• File-based control mode enabled");
                sender.sendMessage(ChatColor.WHITE + "• Web interface disabled for compatibility");
                sender.sendMessage(ChatColor.WHITE + "• Conservative resource allocation active");
            }

            case DOCKER -> {
                sender.sendMessage(ChatColor.GREEN + "Docker Container Detected:");
                sender.sendMessage(ChatColor.WHITE + "• Container resource limits respected");
                sender.sendMessage(ChatColor.WHITE + "• Consider mapping FAH ports for web access");
                sender.sendMessage(ChatColor.WHITE + "• Mount /tmp as tmpfs for better performance");
            }

            case SHARED_HOSTING -> {
                sender.sendMessage(ChatColor.YELLOW + "Shared Hosting Detected:");
                sender.sendMessage(ChatColor.WHITE + "• Minimal resource allocation (1 core max)");
                sender.sendMessage(ChatColor.WHITE + "• All ports disabled");
                sender.sendMessage(ChatColor.GOLD + "• Consider upgrading to VPS for better performance");
            }

            case VPS_DEDICATED -> {
                sender.sendMessage(ChatColor.GREEN + "VPS/Dedicated Server:");
                sender.sendMessage(ChatColor.WHITE + "• Full FAH features available");
                sender.sendMessage(ChatColor.WHITE + "• Web interface: http://yourserver:7396");
                sender.sendMessage(ChatColor.WHITE + "• Optimal performance configuration active");
            }
        }
        
        // Show current port configuration
        sender.sendMessage(ChatColor.GOLD + "========= Port Configuration =========");
        int controlPort = plugin.getConfig().getInt("folding-at-home.ports.control-port", 0);
        int webPort = plugin.getConfig().getInt("folding-at-home.ports.web-port", 0);
        
        sender.sendMessage(ChatColor.YELLOW + "Control Port: " + ChatColor.WHITE + 
                          (controlPort == 0 ? "Disabled" : String.valueOf(controlPort)));
        sender.sendMessage(ChatColor.YELLOW + "Web Port: " + ChatColor.WHITE + 
                          (webPort == 0 ? "Disabled" : String.valueOf(webPort)));
        sender.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + 
                          plugin.getConfig().getString("folding-at-home.ports.no-port-mode", "file-based"));
        
        return true;
    }

    private boolean handleVerify(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin") && !sender.hasPermission("fahdonor.account")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to verify FAH status!");
            return true;
        }

        FAHClientManager manager = plugin.getFAHManager();
        sender.sendMessage(ChatColor.YELLOW + "Verifying FAH local status... This may take a few seconds.");
        manager.verifyTokenAppliedAsync((success) -> {
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "FAH verification: token appears applied and reachable.");
            } else {
                sender.sendMessage(ChatColor.RED + "FAH verification: could not confirm token application. Check server logs.");
            }
        });

        return true;
    }
    
    /**
     * Handle optimize command - applies environment-specific optimizations
     */
    private boolean handleOptimize(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to optimize settings!");
            return true;
        }
        
        PlatformResourceManager platformManager = plugin.getPlatformManager();
        if (platformManager == null) {
            sender.sendMessage(ChatColor.RED + "Platform manager not initialized!");
            return true;
        }
        
        EnvironmentInfo envInfo = platformManager.getEnvironmentInfo();
        
        sender.sendMessage(ChatColor.YELLOW + "Applying optimizations for " + envInfo.getType().getDisplayName() + "...");
        
        // The optimization is already done during initialization
        // This command mainly serves to re-trigger it and inform the user
        boolean configChanged = applyEnvironmentOptimizations(envInfo);
        
        if (configChanged) {
            plugin.saveConfig();
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Configuration optimized and saved!");
            sender.sendMessage(ChatColor.YELLOW + "Restart the server to apply all changes.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Configuration is already optimized for your environment!");
        }
        
        // Show what was optimized
        sender.sendMessage(ChatColor.GOLD + "Environment-specific optimizations:");
        switch (envInfo.getType()) {
            case PTERODACTYL -> {
                sender.sendMessage(ChatColor.WHITE + "• Disabled control and web ports");
                sender.sendMessage(ChatColor.WHITE + "• Enabled file-based control mode");
                sender.sendMessage(ChatColor.WHITE + "• Set conservative core allocation");
            }
            case DOCKER -> {
                sender.sendMessage(ChatColor.WHITE + "• Configured for container resource limits");
                sender.sendMessage(ChatColor.WHITE + "• Enabled cgroup monitoring");
            }
            case SHARED_HOSTING -> {
                sender.sendMessage(ChatColor.WHITE + "• Limited to 1 core maximum");
                sender.sendMessage(ChatColor.WHITE + "• Disabled all ports");
                sender.sendMessage(ChatColor.WHITE + "• Minimal resource usage");
            }
            case VPS_DEDICATED -> {
                sender.sendMessage(ChatColor.WHITE + "• Enabled full feature set");
                sender.sendMessage(ChatColor.WHITE + "• Optimized for maximum performance");
            }
        }
        
        return true;
    }
    
    /**
     * Apply environment-specific optimizations to configuration
     */
    private boolean applyEnvironmentOptimizations(EnvironmentInfo envInfo) {
        boolean changed = false;
        
        switch (envInfo.getType()) {
            case PTERODACTYL, SHARED_HOSTING -> {
                if (plugin.getConfig().getInt("folding-at-home.ports.control-port", 36330) != 0) {
                    plugin.getConfig().set("folding-at-home.ports.control-port", 0);
                    changed = true;
                }
                if (plugin.getConfig().getInt("folding-at-home.ports.web-port", 7396) != 0) {
                    plugin.getConfig().set("folding-at-home.ports.web-port", 0);
                    changed = true;
                }
                if (!plugin.getConfig().getString("folding-at-home.ports.no-port-mode", "").equals("file-based")) {
                    plugin.getConfig().set("folding-at-home.ports.no-port-mode", "file-based");
                    changed = true;
                }
            }
            default -> {
                // No specific changes for other environments here
            }
        }
        
        if (envInfo.getType() == EnvironmentType.SHARED_HOSTING) {
            if (plugin.getConfig().getInt("server.total-cores", 8) > 2) {
                plugin.getConfig().set("server.total-cores", 2);
                changed = true;
            }
            if (plugin.getConfig().getInt("allocation.dynamic.max-cores-for-fah", 7) > 1) {
                plugin.getConfig().set("allocation.dynamic.max-cores-for-fah", 1);
                changed = true;
            }
        }
        
        return changed;
    }
    
    private boolean handleStart(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to control FAH!");
            return true;
        }
        
        if (plugin.isRunning()) {
            sender.sendMessage(ChatColor.YELLOW + "FAH is already running!");
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Starting FAH client...");
        
        // Start the FAH client
        if (plugin.getFAHClient() != null) {
            plugin.getFAHClient().resume();
            plugin.setRunning(true);
            sender.sendMessage(ChatColor.GREEN + "FAH client started successfully!");
        } else {
            sender.sendMessage(ChatColor.RED + "FAH client not initialized. Try reloading the plugin.");
        }
        
        return true;
    }
    
    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to control FAH!");
            return true;
        }
        
        if (!plugin.isRunning()) {
            sender.sendMessage(ChatColor.YELLOW + "FAH is not currently running!");
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Stopping FAH client...");
        
        // Stop the FAH client
        if (plugin.getFAHClient() != null) {
            plugin.getFAHClient().pause();
            plugin.setRunning(false);
            sender.sendMessage(ChatColor.GREEN + "FAH client stopped successfully!");
        } else {
            sender.sendMessage(ChatColor.RED + "FAH client not available.");
        }
        
        return true;
    }
    
    private boolean handleInfo(CommandSender sender) {
        if (!sender.hasPermission("fahdonor.stats")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view FAH info!");
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== FAH Resource Donor Info ===");
        
        // Basic status
        if (plugin.getFAHClient() != null) {
            boolean running = plugin.isRunning();
            String status = plugin.getFAHClient().getWorkUnitStatus();
            String progress = plugin.getFAHClient().getProgress();
            long points = plugin.getFAHClient().getPointsEarned();
            
            sender.sendMessage(ChatColor.YELLOW + "Status: " + 
                (running ? ChatColor.GREEN + "Running" : ChatColor.RED + "Stopped"));
            sender.sendMessage(ChatColor.YELLOW + "Work Unit: " + ChatColor.WHITE + status);
            sender.sendMessage(ChatColor.YELLOW + "Progress: " + ChatColor.WHITE + progress);
            sender.sendMessage(ChatColor.YELLOW + "Points Earned: " + ChatColor.WHITE + points);
            
            // Connection test
            boolean connected = plugin.getFAHClient().testConnection();
            sender.sendMessage(ChatColor.YELLOW + "Connection: " + 
                (connected ? ChatColor.GREEN + "Connected" : ChatColor.RED + "Offline"));
        } else {
            sender.sendMessage(ChatColor.RED + "FAH client not initialized");
        }
        
        // Configuration info
        String username = plugin.getConfig().getString("folding-at-home.account.username", "");
        String teamId = plugin.getConfig().getString("folding-at-home.account.team-id", "");
        
        // Support legacy config
        if (username.isEmpty()) {
            username = plugin.getConfig().getString("fah.donor-name", "Not set");
        }
        if (teamId.isEmpty()) {
            teamId = plugin.getConfig().getString("fah.team", "Not set");
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Donor: " + ChatColor.WHITE + username);
        sender.sendMessage(ChatColor.YELLOW + "Team: " + ChatColor.WHITE + teamId);
        
        return true;
    }
}