package com.thijs226.fahdonor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class FAHClientManager {
    private final FAHResourceDonor plugin;
    private Process fahProcess;
    private final File fahDirectory;
    private Socket controlSocket;
    private PrintWriter controlWriter;
    private BufferedReader controlReader;
    private int currentCores = 0;
    private final ScheduledExecutorService executor;
    private AccountInfo currentAccount;
    private CausePreference currentCause;
    private final Map<UUID, AccountInfo> playerAccounts = new HashMap<>();

    public enum FoldingCause {
        ANY("ANY", "0", "All diseases - highest priority work"),
        ALZHEIMERS("ALZHEIMERS", "1", "Alzheimer's disease research"),
        CANCER("CANCER", "2", "Cancer research"),
        HUNTINGTONS("HUNTINGTONS", "3", "Huntington's disease research"),
        PARKINSONS("PARKINSONS", "4", "Parkinson's disease research"),
        INFLUENZA("INFLUENZA", "5", "Influenza/Flu research"),
        DIABETES("DIABETES", "6", "Diabetes research"),
        COVID_19("COVID-19", "7", "COVID-19/Coronavirus research"),
        HIGH_PRIORITY("HIGH_PRIORITY", "8", "Current high-priority projects"),
        INFECTIOUS_DISEASES("INFECTIOUS", "9", "Infectious disease research"),
        NEUROLOGICAL("NEUROLOGICAL", "10", "Neurological disorders"),
        RARE_DISEASES("RARE", "11", "Rare disease research");

        private final String name;
        private final String fahCode;
        private final String description;

        FoldingCause(String name, String fahCode, String description) {
            this.name = name;
            this.fahCode = fahCode;
            this.description = description;
        }

        public String getName() { return name; }
        public String getFahCode() { return fahCode; }
        public String getDescription() { return description; }

        public static FoldingCause fromString(String name) {
            for (FoldingCause cause : values()) {
                if (cause.name.equalsIgnoreCase(name)) {
                    return cause;
                }
            }
            return ANY;
        }
    }

    public static class CausePreference {
        public FoldingCause primary;
        public FoldingCause secondary;
        public boolean allowAnyFallback;

        public CausePreference(FoldingCause primary, FoldingCause secondary, boolean allowAnyFallback) {
            this.primary = primary;
            this.secondary = secondary;
            this.allowAnyFallback = allowAnyFallback;
        }

        public static CausePreference fromConfig(ConfigurationSection config) {
            FoldingCause primary = FoldingCause.fromString(
                config.getString("primary", "ANY")
            );
            FoldingCause secondary = FoldingCause.fromString(
                config.getString("secondary", "HIGH_PRIORITY")
            );
            boolean allowFallback = config.getBoolean("allow-any-fallback", true);

            return new CausePreference(primary, secondary, allowFallback);
        }
    }

    public static class AccountInfo {
        public String username;
        public String teamId;
        public String passkey;
        public boolean anonymous;

        public AccountInfo(String username, String teamId, String passkey, boolean anonymous) {
            this.username = username;
            this.teamId = teamId;
            this.passkey = passkey;
            this.anonymous = anonymous;
        }

        public static AccountInfo fromConfig(ConfigurationSection config, ConfigurationSection defaultConfig) {
            String username = config.getString("username", "");
            String teamId = config.getString("team-id", "");
            String passkey = config.getString("passkey", "");
            boolean anonymous = config.getBoolean("anonymous", false);

            if (username.isEmpty()) {
                username = defaultConfig.getString("username", "Thijs226_MCServer_Guest");
            }
            if (teamId.isEmpty()) {
                teamId = defaultConfig.getString("team-id", "0");
            }

            return new AccountInfo(username, teamId, passkey, anonymous);
        }
    }

    public FAHClientManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        this.fahDirectory = new File(plugin.getDataFolder(), "folding-at-home");
        this.executor = Executors.newSingleThreadScheduledExecutor();

        loadAccountConfiguration();
        loadCausePreference();
        startFAHClient();
    }

    private void loadAccountConfiguration() {
        ConfigurationSection accountConfig = plugin.getConfig().getConfigurationSection("folding-at-home.account");
        ConfigurationSection defaultConfig = plugin.getConfig().getConfigurationSection("folding-at-home.default-account");

        currentAccount = AccountInfo.fromConfig(accountConfig, defaultConfig);

        plugin.getLogger().info("Configured F@H Account: " +
            (currentAccount.anonymous ? "Anonymous" : currentAccount.username) +
            " (Team: " + currentAccount.teamId + ")");
    }

    private void loadCausePreference() {
        ConfigurationSection causeConfig = plugin.getConfig().getConfigurationSection("folding-at-home.cause-preference");
        currentCause = CausePreference.fromConfig(causeConfig);

        plugin.getLogger().info("Research focus: " + currentCause.primary.description);
        if (!currentCause.primary.equals(FoldingCause.ANY)) {
            plugin.getLogger().info("Secondary: " + currentCause.secondary.description);
        }
    }

    private void startFAHClient() {
        try {
            // Check if FAH is actually available
            File fahExecutable = new File(fahDirectory, "FAHClient");
            File fahExecutableExe = new File(fahDirectory, "FAHClient.exe");

            if (!fahExecutable.exists() && !fahExecutableExe.exists()) {
                plugin.getLogger().warning("FAH Client binary not found - running in simulation mode");
                plugin.getLogger().warning("Check: " + fahDirectory.getAbsolutePath());
                return;
            }

            File actualExecutable = fahExecutable.exists() ? fahExecutable : fahExecutableExe;
            plugin.getLogger().info("Found FAH executable: " + actualExecutable.getAbsolutePath());

            // Check port configuration
            int controlPort = plugin.getConfig().getInt("folding-at-home.ports.control-port", 0);
            int webPort = plugin.getConfig().getInt("folding-at-home.ports.web-port", 0);
            String noPortMode = plugin.getConfig().getString("folding-at-home.ports.no-port-mode", "file-based");

            if (controlPort == 0) {
                plugin.getLogger().info("Control port disabled - using " + noPortMode + " mode");
                plugin.getLogger().info("This is normal for shared hosting with single port!");
            }

            // Update config with port settings
            updateConfigXml(currentAccount, currentCause, controlPort, webPort);

            // Build command line arguments
            List<String> command = new ArrayList<>();
            command.add(actualExecutable.getAbsolutePath());
            command.add("--config");
            command.add(new File(fahDirectory, "config.xml").getAbsolutePath());
            command.add("--data-directory");
            command.add(fahDirectory.getAbsolutePath());
            command.add("--exec-directory");
            command.add(fahDirectory.getAbsolutePath());

            if (controlPort == 0) {
                // Run in standalone mode without control
                command.add("--command-port");
                command.add("0");
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(fahDirectory);
            pb.redirectErrorStream(true);

            plugin.getLogger().info("Starting FAH client process...");
            fahProcess = pb.start();

            // Start output reader thread
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(fahProcess.getInputStream()))) {
                    String line;
                    boolean debugMode = plugin.getConfig().getBoolean("debug", false);
                    while ((line = reader.readLine()) != null) {
                        if (debugMode || line.contains("ERROR") || line.contains("WARNING") ||
                            line.contains("WU") || line.contains("Complete") || line.contains("Started")) {
                            plugin.getLogger().info("[FAH] " + line);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("FAH output reader stopped: " + e.getMessage());
                }
            }, "FAH-Output-Reader").start();

            plugin.getLogger().info("Waiting for FAH to initialize...");
            Thread.sleep(5000);

            if (controlPort > 0) {
                // Try to connect to control interface if port is configured
                try {
                    connectToControl(controlPort);
                    plugin.getLogger().info("Connected to FAH control interface on port " + controlPort);

                    // Unpause FAH after starting
                    plugin.getLogger().info("Unpausing FAH client...");
                    sendCommand("unpause");

                    // Set initial cores
                    int playerCount = Bukkit.getOnlinePlayers().size();
                    int cores = calculateInitialCores(playerCount);
                    setCores(cores);

                } catch (Exception e) {
                    plugin.getLogger().warning("Could not connect to FAH control on port " + controlPort);
                    plugin.getLogger().warning("Running in standalone mode - using config file settings");
                }
            } else {
                // No control port - using file-based or standalone mode
                if (noPortMode.equals("file-based")) {
                    setupFileBasedControl();
                }
                plugin.getLogger().info("FAH running in " + noPortMode + " mode (no port required)");
            }

            plugin.getLogger().info("FAH Client started successfully!");

        } catch (Exception e) {
            plugin.getLogger().warning("Could not start FAH Client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void connectToControl(int port) throws IOException {
        if (port == 0) {
            throw new IOException("Control port is disabled");
        }
        controlSocket = new Socket("localhost", port);
        controlWriter = new PrintWriter(controlSocket.getOutputStream(), true);
        controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));

        String line;
        while ((line = controlReader.readLine()) != null && !line.contains(">")) {
            // Wait for prompt
        }
    }

    private void connectToControl() throws IOException {
        int port = plugin.getConfig().getInt("folding-at-home.ports.control-port", 36330);
        connectToControl(port);
    }

    private void setupFileBasedControl() {
        // Create a control file that FAH can read
        File controlFile = new File(fahDirectory, "control.txt");
        try {
            if (!controlFile.exists()) {
                controlFile.createNewFile();
            }

            // Write initial settings
            int playerCount = Bukkit.getOnlinePlayers().size();
            int cores = calculateInitialCores(playerCount);

            Files.write(controlFile.toPath(),
                ("cores=" + cores + "\npaused=false\n").getBytes());

            plugin.getLogger().info("Created file-based control at: " + controlFile.getAbsolutePath());

        } catch (IOException e) {
            plugin.getLogger().warning("Could not create control file: " + e.getMessage());
        }
    }

    private void updateConfigXml(AccountInfo account, CausePreference cause, int controlPort, int webPort) throws IOException {
        boolean enableWebControl = webPort > 0;
        String webPassword = plugin.getConfig().getString("folding-at-home.web-control.password", "");

        String controlConfig = "";
        if (controlPort > 0) {
            controlConfig = String.format("""
                  <!-- Remote Control -->
                  <command-port v='%d'/>
                  <allow>127.0.0.1</allow>
                  <command-allow-no-pass>127.0.0.1</command-allow-no-pass>
                """, controlPort);
        } else {
            controlConfig = """
                  <!-- Remote Control DISABLED (no port) -->
                  <command-port v='0'/>
                """;
        }

        String webConfig = "";
        if (webPort > 0) {
            webConfig = String.format("""
                  <!-- Web Interface -->
                  <web-allow>%s</web-allow>
                  <http-addresses>0.0.0.0:%d</http-addresses>
                  %s
                """,
                getAllowedIPs(),
                webPort,
                webPassword.isEmpty() ? "" : "<password v='" + webPassword + "'/>"
            );
        }

        // Get initial core count
        int initialCores = calculateInitialCores(Bukkit.getOnlinePlayers().size());

        String configXml = String.format("""
            <config>
              <!-- User Configuration -->
              <user v='%s'/>
              <team v='%s'/>
              <passkey v='%s'/>
              
              <!-- Machine Name -->
              <machine-name v='Minecraft-Server'/>
              
              <!-- Client Configuration -->
              <power v='medium'/>
              <gpu v='false'/>
              <fold-anon v='false'/>
              
              <!-- Slot Configuration -->
              <slot id='0' type='CPU'>
                <cpus v='%d'/>
              </slot>
              
              %s
              
              %s
              
              <!-- Server Mode -->
              <gui-enabled v='false'/>
              
              <!-- Logging -->
              <log-level v='3'/>
              <log-rotate v='true'/>
              <log-rotate-max v='10'/>
            </config>
            """,
            account.username,
            account.teamId,
            account.passkey,
            initialCores,
            controlConfig,
            webConfig
        );

        File configFile = new File(fahDirectory, "config.xml");
        Files.write(configFile.toPath(), configXml.getBytes());
    }

    private void updateConfigXml(AccountInfo account, CausePreference cause) throws IOException {
        int controlPort = plugin.getConfig().getInt("folding-at-home.ports.control-port", 36330);
        int webPort = plugin.getConfig().getInt("folding-at-home.ports.web-port", 7396);
        updateConfigXml(account, cause, controlPort, webPort);
    }

    public boolean isFAHRunning() {
        return fahProcess != null && fahProcess.isAlive();
    }

    public boolean isConnected() {
        return controlSocket != null && controlSocket.isConnected() && !controlSocket.isClosed();
    }

    public int getCurrentCores() {
        return currentCores;
    }

    public void forceStart() {
        if (!isFAHRunning()) {
            plugin.getLogger().info("Force starting FAH client...");
            startFAHClient();
        } else {
            plugin.getLogger().info("FAH client is already running");
        }
    }

    public void forceUnpause() {
        try {
            if (controlWriter != null) {
                sendCommand("unpause");
                plugin.getLogger().info("Sent unpause command to FAH");
            } else {
                plugin.getLogger().warning("Cannot unpause - not connected to FAH");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unpause: " + e.getMessage());
        }
    }

    public void setCores(int cores) {
        if (cores == currentCores) return;

        int controlPort = plugin.getConfig().getInt("folding-at-home.ports.control-port", 0);
        String noPortMode = plugin.getConfig().getString("folding-at-home.ports.no-port-mode", "file-based");

        if (controlPort == 0 && noPortMode.equals("file-based")) {
            // File-based control for hosts with no ports
            setCoresFileMode(cores);
            return;
        }

        executor.execute(() -> {
            try {
                if (controlWriter == null) {
                    // FAH not connected
                    if (controlPort > 0) {
                        // Try to reconnect
                        plugin.getLogger().warning("FAH not connected, attempting to reconnect...");
                        try {
                            connectToControl(controlPort);
                            plugin.getLogger().info("Reconnected to FAH control interface");
                        } catch (Exception e) {
                            // Still not connected
                            plugin.getLogger().info("[No Control] Would set FAH to " + cores + " cores");
                            // In file mode, write to control file
                            if (noPortMode.equals("file-based")) {
                                setCoresFileMode(cores);
                            }
                            currentCores = cores;
                            return;
                        }
                    } else {
                        // No port configured - use file mode
                        setCoresFileMode(cores);
                        currentCores = cores;
                        return;
                    }
                }

                // Now we should be connected via socket
                if (cores == 0) {
                    sendCommand("pause");
                    plugin.getLogger().info("FAH paused - all cores needed for Minecraft");
                } else {
                    sendCommand("unpause");
                    Thread.sleep(500);
                    sendCommand("slot-modify 0 cpus " + cores);
                    plugin.getLogger().info("FAH set to use " + cores + " cores");
                    Thread.sleep(500);
                    sendCommand("unpause");
                }

                currentCores = cores;

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update FAH cores: " + e.getMessage());
            }
        });
    }

    private void setCoresFileMode(int cores) {
        // For hosts with no ports - restart FAH with new core count
        try {
            plugin.getLogger().info("Updating FAH cores via process restart (no-port mode)");

            if (cores == 0) {
                // Stop FAH completely
                if (fahProcess != null && fahProcess.isAlive()) {
                    plugin.getLogger().info("Stopping FAH - all cores needed for Minecraft");
                    fahProcess.destroy();
                    fahProcess = null;
                }
            } else {
                // Update config and restart FAH with new core count
                File configFile = new File(fahDirectory, "config.xml");
                if (configFile.exists()) {
                    String config = new String(Files.readAllBytes(configFile.toPath()));

                    // Update the cpus value in config
                    config = config.replaceAll("<cpus v='\\d+'/>", "<cpus v='" + cores + "'/>");
                    Files.write(configFile.toPath(), config.getBytes());

                    // Restart FAH if it was stopped or core count changed significantly
                    if (fahProcess == null || !fahProcess.isAlive() || Math.abs(cores - currentCores) > 2) {
                        if (fahProcess != null && fahProcess.isAlive()) {
                            plugin.getLogger().info("Restarting FAH with " + cores + " cores");
                            fahProcess.destroy();
                            Thread.sleep(2000);
                        } else {
                            plugin.getLogger().info("Starting FAH with " + cores + " cores");
                        }

                        // Restart FAH
                        startFAHClient();
                    } else {
                        plugin.getLogger().info("FAH continues with " + cores + " cores (minor change)");
                    }
                }
            }

            currentCores = cores;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update cores in file mode: " + e.getMessage());
        }
    }

    public void reconfigureWithToken(String accountToken, String machineName) {
        try {
            // Create new config with token
            String configXml = String.format("""
                <config>
                  <!-- Account Token Configuration -->
                  <account-token v='%s'/>
                  <machine-name v='%s'/>
                  
                  <!-- Research Cause -->
                  <cause v='%s'/>
                  
                  <!-- Client Configuration -->
                  <power v='light'/>
                  <gpu v='false'/>
                  
                  <!-- Slot Configuration -->
                  <slot id='0' type='CPU'>
                    <cpus v='1'/>
                    <cause v='%s'/>
                  </slot>
                  
                  <!-- Remote Control -->
                  <allow>127.0.0.1</allow>
                  <command-allow-no-pass>127.0.0.1</command-allow-no-pass>
                  <web-allow>%s</web-allow>
                  
                  <!-- HTTP Server -->
                  <http-addresses>0.0.0.0:7396</http-addresses>
                  
                  <!-- Server Mode -->
                  <gui-enabled v='false'/>
                  
                  <!-- Logging -->
                  <log-level v='3'/>
                  <log-rotate v='true'/>
                  <log-rotate-max v='10'/>
                </config>
                """,
                accountToken,
                machineName,
                currentCause.primary.fahCode,
                currentCause.primary.fahCode,
                getAllowedIPs()
            );

            File configFile = new File(fahDirectory, "config.xml");
            Files.write(configFile.toPath(), configXml.getBytes());

            // Restart FAH client with new config
            sendCommand("config-rotate");

            plugin.getLogger().info("Reconfigured FAH with account token for machine: " + machineName);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reconfigure with token: " + e.getMessage());
        }
    }

    public void updateAccount(AccountInfo account) {
        this.currentAccount = account;
        reconfigureFAHClient();
    }

    public void updateCause(FoldingCause newCause) {
        currentCause.primary = newCause;
        reconfigureFAHClient();

        String message = ChatColor.GOLD + "Research focus changed to: " +
                        ChatColor.YELLOW + newCause.description;
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
    }

    private void reconfigureFAHClient() {
        try {
            updateConfigXml(currentAccount, currentCause);
            sendCommand("config-rotate");
            plugin.getLogger().info("Updated F@H configuration");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update F@H config: " + e.getMessage());
        }
    }

    private String getAllowedIPs() {
        StringBuilder ips = new StringBuilder("127.0.0.1");
        for (String ip : plugin.getConfig().getStringList("folding-at-home.web-control.allowed-ips")) {
            ips.append(" ").append(ip);
        }
        return ips.toString();
    }

    private void sendCommand(String command) throws IOException {
        if (controlWriter != null) {
            controlWriter.println(command);
            controlWriter.flush();

            String line;
            while ((line = controlReader.readLine()) != null && !line.contains(">")) {
                // Read response
            }
        }
    }

    public void shutdown() {
        try {
            setCores(0);
            Thread.sleep(2000);

            if (controlSocket != null) {
                sendCommand("shutdown");
                controlSocket.close();
            }

            if (fahProcess != null && fahProcess.isAlive()) {
                fahProcess.destroy();
                fahProcess.waitFor(10, TimeUnit.SECONDS);

                if (fahProcess.isAlive()) {
                    fahProcess.destroyForcibly();
                }
            }

            executor.shutdown();

        } catch (Exception e) {
            plugin.getLogger().warning("Error shutting down FAH: " + e.getMessage());
        }
    }

    public AccountInfo getCurrentAccount() {
        return currentAccount;
    }

    public CausePreference getCurrentCause() {
        return currentCause;
    }

    public void resetToDefaultAccount() {
        loadAccountConfiguration();
        reconfigureFAHClient();
    }

    // Helper used but not defined in original snippet: calculateInitialCores
        private int calculateInitialCores(int playerCount) {
    // Uses allocation logic: delegate to plugin settings (approximate)
        int total = plugin.getConfig().getInt("server.total-cores", 8);
        int reserved = plugin.getConfig().getInt("server.reserved-cores", 1);
        double cpp = plugin.getConfig().getDouble("allocation.dynamic.cores-per-player", 0.5);
        double neededForMC = reserved + (playerCount * cpp);
        int availableForFAH = (int) Math.floor(total - neededForMC);
        return Math.max(0, Math.min(availableForFAH, total - reserved));
    }
}