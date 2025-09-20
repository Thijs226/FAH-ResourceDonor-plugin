package com.thijs226.fahdonor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

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
            
            final String fos = os;
            plugin.getLogger().info(() -> String.format("Operating System: %s", fos));
            
            // First check if FAH is already installed manually
            if (checkManualInstallation()) {
                plugin.getLogger().info("Found manually installed FAH client!");
                return true;
            }
            
            // Try to download and install
            String downloadUrl = getFAHDownloadUrl(os);
            
            if (downloadUrl == null) {
                final String nos = os;
                plugin.getLogger().warning(() -> String.format("Automatic installation not supported for: %s", nos));
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
            
        } catch (IOException | InterruptedException e) {
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
        } catch (IOException | InterruptedException e) {
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
    plugin.getLogger().warning(() -> String.format("   %s", fahDirectory.getAbsolutePath()));
    plugin.getLogger().warning("");
    plugin.getLogger().warning("Or for headless Linux servers:");
    plugin.getLogger().warning("  wget https://download.foldingathome.org/releases/public/fah-client/debian-10-64bit/release/fah-client_8.3.18_amd64.deb");
    plugin.getLogger().warning(() -> String.format("  dpkg -x fah-client_8.3.18_amd64.deb %s", fahDirectory.getAbsolutePath()));
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
            
            final String furl = url;
            plugin.getLogger().info(() -> String.format("Downloading from: %s", furl));
            
            // Check if URL is accessible
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                final int code = responseCode;
                plugin.getLogger().warning(() -> String.format("Download URL returned code: %d", code));
                
                // Try alternative URLs
                if (os.contains("linux")) {
                    // Try v7 as fallback
                    url = "https://download.foldingathome.org/releases/v7/public/fahclient/centos-6.7-64bit/release/fahclient_7.6.21-64bit-release.tar.bz2";
                    final String f2 = url;
                    plugin.getLogger().info(() -> String.format("Trying fallback URL: %s", f2));
                }
            }
            
            // Download file
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                final long len = tempFile.length();
                plugin.getLogger().info(() -> String.format("Downloaded %d bytes", len));
            }
            
            return tempFile;
            
        } catch (IOException e) {
            plugin.getLogger().warning(() -> String.format("Download failed: %s", e.getMessage()));
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
        // Get account details from config
        String username = plugin.getConfig().getString("folding-at-home.account.username", "");
        String teamId = plugin.getConfig().getString("folding-at-home.account.team-id", "0");
        String passkey = plugin.getConfig().getString("folding-at-home.account.passkey", "");
        String accountToken = plugin.getConfig().getString("folding-at-home.account.account-token", "");
        String machineName = plugin.getConfig().getString("folding-at-home.account.machine-name", "Minecraft-Server");
        
        // Check if using token-based authentication
    boolean usingToken = accountToken != null && !accountToken.isEmpty();
        
        // Use defaults if not configured
        if (!usingToken && (username == null || username.isEmpty())) {
            username = plugin.getConfig().getString("folding-at-home.default-account.username", "Thijs226_MCServer_Guest");
        }
        if (!usingToken && (teamId == null || teamId.isEmpty() || teamId.equals("0"))) {
            teamId = plugin.getConfig().getString("folding-at-home.default-account.team-id", "0");
        }
        
        if (usingToken) {
            final String mn = machineName;
            plugin.getLogger().info(() -> String.format("Configuring FAH with account token for machine: %s", mn));
            plugin.getLogger().info("This server will be linked to your F@H account");
        } else {
            final String un = username;
            final String tid = teamId;
            plugin.getLogger().info(() -> String.format("Configuring FAH with account: %s (Team: %s)", un, tid));
            if (passkey != null && !passkey.isEmpty()) {
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

                                    <!-- Slot Configuration -->
                                    <slot id='0' type='CPU'>
                                        <cpus v='1'/>
                                    </slot>

                                    <!-- Remote Control (localhost only) -->
                                    <allow>127.0.0.1</allow>
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

                                    <!-- Slot Configuration -->
                                    <slot id='0' type='CPU'>
                                        <cpus v='1'/>
                                    </slot>

                                    <!-- Remote Control (localhost only) -->
                                    <allow>127.0.0.1</allow>
                                </config>
                                """, username, teamId, passkey, machineName);
                }
        
        File configFile = new File(fahDirectory, "config.xml");
        Files.write(configFile.toPath(), configXml.getBytes());
        
        if (usingToken) {
            plugin.getLogger().info("Created FAH config.xml with account token");
        } else {
            final String un2 = username;
            plugin.getLogger().info(() -> String.format("Created FAH config.xml with user: %s", un2));
        }
    }
}