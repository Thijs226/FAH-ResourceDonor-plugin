package com.thijs226.fahdonor;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.file.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public class FAHInstaller {
    private final JavaPlugin plugin;
    private final File fahDirectory;
    
    public FAHInstaller(JavaPlugin plugin) {
        this.plugin = plugin;
        this.fahDirectory = new File(plugin.getDataFolder(), "folding-at-home");
    }
    
    public boolean installFAHClient() {
        try {
            fahDirectory.mkdirs();
            
            String os = System.getProperty("os.name").toLowerCase();
            
            plugin.getLogger().info("Operating System: " + os);
            
            // First check if FAH is already installed manually
            if (checkManualInstallation()) {
                plugin.getLogger().info("Found manually installed FAH client!");
                return true;
            }
            
            // Try to download and install
            String downloadUrl = getFAHDownloadUrl(os);
            
            if (downloadUrl == null) {
                plugin.getLogger().warning("Automatic installation not supported for: " + os);
                return setupManualInstallation();
            }
            
            plugin.getLogger().info("Attempting to download FAH client...");
            
            File installer = downloadFAHClient(downloadUrl, os);
            
            if (installer == null || !installer.exists()) {
                plugin.getLogger().warning("Could not download FAH client automatically");
                return setupManualInstallation();
            }
            
            if (!installClient(installer, os)) {
                return setupManualInstallation();
            }
            
            configureFAHClient();
            
            installer.delete();
            
            plugin.getLogger().info("Folding@home client installed successfully!");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to auto-install FAH client: " + e.getMessage());
            return setupManualInstallation();
        }
    }
    
    private boolean checkManualInstallation() {
        // Check if FAH client binary exists
        File fahBinary = new File(fahDirectory, "FAHClient");
        if (fahBinary.exists() && fahBinary.canExecute()) {
            return true;
        }
        
        // Windows check
        File fahBinaryExe = new File(fahDirectory, "FAHClient.exe");
        if (fahBinaryExe.exists()) {
            return true;
        }
        
        // Check system-wide installation
        try {
            Process p = Runtime.getRuntime().exec("which FAHClient");
            p.waitFor();
            if (p.exitValue() == 0) {
                plugin.getLogger().info("Found system-wide FAH installation");
                return true;
            }
        } catch (Exception e) {
            // Not found
        }
        
        return false;
    }
    
    private boolean setupManualInstallation() {
        plugin.getLogger().warning("========================================");
        plugin.getLogger().warning("MANUAL INSTALLATION REQUIRED");
        plugin.getLogger().warning("");
        plugin.getLogger().warning("FAH Client could not be downloaded automatically.");
        plugin.getLogger().warning("Please install manually:");
        plugin.getLogger().warning("");
        plugin.getLogger().warning("1. Download FAH v8 from: https://foldingathome.org/start-folding/");
        plugin.getLogger().warning("2. Install FAH Client on your system");
        plugin.getLogger().warning("3. Copy FAHClient binary to:");
        plugin.getLogger().warning("   " + fahDirectory.getAbsolutePath());
        plugin.getLogger().warning("");
        plugin.getLogger().warning("Or for headless Linux servers:");
        plugin.getLogger().warning("  wget https://download.foldingathome.org/releases/public/fah-client/debian-10-64bit/release/fah-client_8.3.18_amd64.deb");
        plugin.getLogger().warning("  dpkg -x fah-client_8.3.18_amd64.deb " + fahDirectory.getAbsolutePath());
        plugin.getLogger().warning("");
        plugin.getLogger().warning("The plugin will work in LIMITED MODE until FAH is installed");
        plugin.getLogger().warning("========================================");
        
        // Create placeholder config
        try {
            configureFAHClient();
        } catch (Exception e) {
            // Ignore
        }
        
        // Return true to allow plugin to continue in limited mode
        return true;
    }
    
    private String getFAHDownloadUrl(String os) {
        // Note: These URLs may need updating as FAH releases new versions
        // v8 client URLs (latest as of 2024/2025)
        if (os.contains("linux")) {
            // Try v8 first, then fallback to v7
            return "https://download.foldingathome.org/releases/public/fah-client/debian-10-64bit/release/fah-client_8.3.18_amd64.deb";
        } else if (os.contains("windows")) {
            return "https://download.foldingathome.org/releases/public/fah-client/windows-10-64bit/release/fah-client_8.4.9_AMD64.exe";
        }
        // macOS and others not supported for auto-download
        return null;
    }
    
    private File downloadFAHClient(String url, String os) {
        try {
            String extension = os.contains("linux") ? ".deb" : 
                              os.contains("windows") ? ".exe" : ".pkg";
            File tempFile = new File(fahDirectory, "fahclient-installer" + extension);
            
            plugin.getLogger().info("Downloading from: " + url);
            
            // Check if URL is accessible
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                plugin.getLogger().warning("Download URL returned code: " + responseCode);
                
                // Try alternative URLs
                if (os.contains("linux")) {
                    // Try v7 as fallback
                    url = "https://download.foldingathome.org/releases/v7/public/fahclient/centos-6.7-64bit/release/fahclient_7.6.21-64bit-release.tar.bz2";
                    plugin.getLogger().info("Trying fallback URL: " + url);
                }
            }
            
            // Download file
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Downloaded " + tempFile.length() + " bytes");
            }
            
            return tempFile;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Download failed: " + e.getMessage());
            return null;
        }
    }
    
    private boolean installClient(File installer, String os) throws IOException, InterruptedException {
        ProcessBuilder pb;
        
        if (os.contains("linux")) {
            String installerPath = installer.getAbsolutePath();
            
            if (installerPath.endsWith(".deb")) {
                // Extract deb package
                pb = new ProcessBuilder(
                    "dpkg-deb", "-x", installerPath, fahDirectory.getAbsolutePath()
                );
                Process p = pb.start();
                p.waitFor();
                
                // Find and move the FAHClient binary
                File[] files = new File(fahDirectory, "usr/bin").listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().contains("FAHClient") || f.getName().contains("fah-client")) {
                            f.renameTo(new File(fahDirectory, "FAHClient"));
                            new File(fahDirectory, "FAHClient").setExecutable(true);
                            return true;
                        }
                    }
                }
                
                // Try alternative locations
                File altLocation = new File(fahDirectory, "usr/local/bin/fah-client");
                if (altLocation.exists()) {
                    altLocation.renameTo(new File(fahDirectory, "FAHClient"));
                    new File(fahDirectory, "FAHClient").setExecutable(true);
                    return true;
                }
                
            } else if (installerPath.endsWith(".tar.bz2")) {
                // Extract tar.bz2
                pb = new ProcessBuilder(
                    "tar", "-xjf", installerPath, "-C", fahDirectory.getAbsolutePath()
                );
                Process p = pb.start();
                p.waitFor();
                
                // Make executable
                File fahBinary = new File(fahDirectory, "FAHClient");
                if (fahBinary.exists()) {
                    fahBinary.setExecutable(true);
                    return true;
                }
            }
            
        } else if (os.contains("windows")) {
            // Windows silent install
            pb = new ProcessBuilder(
                installer.getAbsolutePath(),
                "/S",
                "/D=" + fahDirectory.getAbsolutePath()
            );
            Process p = pb.start();
            p.waitFor();
            return true;
        }
        
        return false;
    }
    
    private void configureFAHClient() throws IOException {
        configureFAHClient(null);
    }
    
    public void configureFAHClient(FAHClientManager.AccountInfo accountInfo) throws IOException {
        // Get account details - use provided info or read from config
        String username, teamId, passkey, accountToken, machineName;
        boolean usingToken;
        
        if (accountInfo != null) {
            // Use provided account info (preferred - ensures consistency)
            username = accountInfo.username;
            teamId = accountInfo.teamId;
            passkey = accountInfo.passkey;
            accountToken = accountInfo.accountToken;
            machineName = accountInfo.machineName;
            usingToken = accountInfo.isUsingToken();
        } else {
            // Fallback: read directly from config (for backward compatibility)
            username = plugin.getConfig().getString("folding-at-home.account.username", "");
            teamId = plugin.getConfig().getString("folding-at-home.account.team-id", "0");
            passkey = plugin.getConfig().getString("folding-at-home.account.passkey", "");
            accountToken = plugin.getConfig().getString("folding-at-home.account.account-token", "");
            machineName = plugin.getConfig().getString("folding-at-home.account.machine-name", "Minecraft-Server");
            
            // Check if using token-based authentication
            usingToken = !accountToken.isEmpty();
            
            // Handle legacy config support
            if (!usingToken) {
                String legacyToken = plugin.getConfig().getString("fah.token", "");
                String legacyTeam = plugin.getConfig().getString("fah.team", "");
                String legacyDonorName = plugin.getConfig().getString("fah.donor-name", "");
                
                if (!legacyToken.isEmpty()) {
                    passkey = legacyToken;
                    plugin.getLogger().info("Using legacy fah.token as passkey");
                }
                if (!legacyTeam.isEmpty() && (teamId.isEmpty() || teamId.equals("0"))) {
                    teamId = legacyTeam;
                    plugin.getLogger().info("Using legacy fah.team");
                }
                if (!legacyDonorName.isEmpty() && username.isEmpty()) {
                    username = legacyDonorName;
                    plugin.getLogger().info("Using legacy fah.donor-name");
                }
            }
            
            // Use defaults if not configured
            if (!usingToken && username.isEmpty()) {
                username = plugin.getConfig().getString("folding-at-home.default-account.username", "Thijs226_MCServer_Guest");
            }
            if (!usingToken && (teamId.isEmpty() || teamId.equals("0"))) {
                teamId = plugin.getConfig().getString("folding-at-home.default-account.team-id", "0");
            }
        }
        
        if (usingToken) {
            plugin.getLogger().info("Configuring FAH with account token for machine: " + machineName);
            plugin.getLogger().info("This server will be linked to your F@H account");
        } else {
            plugin.getLogger().info("Configuring FAH with account: " + username + " (Team: " + teamId + ")");
            if (!passkey.isEmpty()) {
                plugin.getLogger().info("Passkey configured - bonus points enabled!");
            } else {
                plugin.getLogger().warning("No passkey configured - missing bonus points!");
            }
        }
        
        String configXml;
        
        if (usingToken) {
            // Token-based configuration (links to existing account)
            configXml = String.format("""
                <config>
                  <!-- Account Token Configuration -->
                  <account-token v='%s'/>
                  <machine-name v='%s'/>
                  
                  <!-- Client Configuration -->
                  <power v='light'/>
                  <gpu v='false'/>
                  
                  <!-- Slot Configuration -->
                  <slot id='0' type='CPU'>
                    <cpus v='1'/>
                  </slot>
                  
                  <!-- Remote Control -->
                  <allow>127.0.0.1</allow>
                  <command-allow-no-pass>127.0.0.1</command-allow-no-pass>
                  <web-allow>127.0.0.1</web-allow>
                  
                  <!-- Server Mode -->
                  <gui-enabled v='false'/>
                  
                  <!-- Logging -->
                  <log-level v='3'/>
                  <log-rotate v='true'/>
                  <log-rotate-max v='10'/>
                </config>
                """, accountToken, machineName);
        } else {
            // Traditional username/passkey configuration
            configXml = String.format("""
                <config>
                  <!-- User Configuration -->
                  <user v='%s'/>
                  <team v='%s'/>
                  <passkey v='%s'/>
                  
                  <!-- Machine Name -->
                  <machine-name v='%s'/>
                  
                  <!-- Client Configuration -->
                  <power v='light'/>
                  <gpu v='false'/>
                  <fold-anon v='false'/>
                  
                  <!-- Slot Configuration -->
                  <slot id='0' type='CPU'>
                    <cpus v='1'/>
                  </slot>
                  
                  <!-- Remote Control -->
                  <allow>127.0.0.1</allow>
                  <command-allow-no-pass>127.0.0.1</command-allow-no-pass>
                  <web-allow>127.0.0.1</web-allow>
                  
                  <!-- Server Mode -->
                  <gui-enabled v='false'/>
                  
                  <!-- Logging -->
                  <log-level v='3'/>
                  <log-rotate v='true'/>
                  <log-rotate-max v='10'/>
                </config>
                """, username, teamId, passkey, machineName);
        }
        
        File configFile = new File(fahDirectory, "config.xml");
        Files.write(configFile.toPath(), configXml.getBytes());
        
        if (usingToken) {
            plugin.getLogger().info("Created FAH config.xml with account token");
        } else {
            plugin.getLogger().info("Created FAH config.xml with user: " + username);
        }
    }
}