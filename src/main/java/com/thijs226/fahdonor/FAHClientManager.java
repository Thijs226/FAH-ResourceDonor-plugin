package com.thijs226.fahdonor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import com.thijs226.fahdonor.environment.PlatformResourceManager;
import com.thijs226.fahdonor.environment.ServerEnvironmentDetector;

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
    private PlatformResourceManager platformManager;

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

            if (username == null || username.isEmpty()) {
                username = defaultConfig.getString("username", "Thijs226_MCServer_Guest");
            }
            if (teamId == null || teamId.isEmpty()) {
                teamId = defaultConfig.getString("team-id", "0");
            }

            return new AccountInfo(username, teamId, passkey, anonymous);
        }
    }

    public FAHClientManager(FAHResourceDonor plugin) {
        this(plugin, null);
    }
    
    public FAHClientManager(FAHResourceDonor plugin, PlatformResourceManager platformManager) {
        this.plugin = plugin;
        this.platformManager = platformManager;
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

        plugin.getLogger().info(() -> "Configured F@H Account: " +
            (currentAccount.anonymous ? "Anonymous" : currentAccount.username) +
            " (Team: " + currentAccount.teamId + ")");
    }

    private void loadCausePreference() {
        ConfigurationSection causeConfig = plugin.getConfig().getConfigurationSection("folding-at-home.cause-preference");
        currentCause = CausePreference.fromConfig(causeConfig);

        plugin.getLogger().info(() -> "Research focus: " + currentCause.primary.description);
        if (!currentCause.primary.equals(FoldingCause.ANY)) {
            plugin.getLogger().info(() -> "Secondary: " + currentCause.secondary.description);
        }
    }

    private void startFAHClient() {
        try {
            // Check if FAH is actually available
            File fahExecutable = new File(fahDirectory, "FAHClient");
            File fahExecutableExe = new File(fahDirectory, "FAHClient.exe");

            if (!fahExecutable.exists() && !fahExecutableExe.exists()) {
                plugin.getLogger().warning("FAH Client binary not found - running in simulation mode");
                return;
            }

            File actualExecutable = fahExecutable.exists() ? fahExecutable : fahExecutableExe;
            plugin.getLogger().info(() -> "Found FAH executable: " + actualExecutable.getAbsolutePath());

            // Check port configuration
            int controlPort = plugin.getConfig().getInt("folding-at-home.ports.control-port", 0);
            int webPort = plugin.getConfig().getInt("folding-at-home.ports.web-port", 0);
            String noPortMode = plugin.getConfig().getString("folding-at-home.ports.no-port-mode", "file-based");

            if (controlPort == 0) {
                plugin.getLogger().info(() -> "Control port disabled - using " + noPortMode + " mode");
                plugin.getLogger().info(() -> "This is normal for shared hosting with single port!");
            }

            // Update config with port settings
            updateConfigXml(currentAccount, currentCause, controlPort, webPort);

            // Build command line arguments
            List<String> command = new ArrayList<>();
            command.add(actualExecutable.getAbsolutePath());
            command.add("--config");
            command.add(new File(fahDirectory, "config.xml").getAbsolutePath());

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
                    boolean gpuEnabled = plugin.getConfig().getBoolean("folding-at-home.gpu-enabled", false);
                    while ((line = reader.readLine()) != null) {
                        final String logLine = line; // Make line effectively final
                        if (debugMode || logLine.contains("ERROR") || logLine.contains("WARNING")) {
                            if (!gpuEnabled && (logLine.contains("OpenCL") || logLine.contains("CUDA"))) {
                                continue; // Suppress GPU-related warnings if GPU folding is disabled
                            }
                            plugin.getLogger().info(() -> "[FAH] " + logLine);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().info(() -> "FAH output reader stopped: " + e.getMessage());
                }
            }, "FAH-Output-Reader").start();

            plugin.getLogger().info("Waiting for FAH to initialize...");
            Thread.sleep(5000);

            // Log additional details about the FAH client state
            if (isFAHRunning()) {
                plugin.getLogger().info("FAH client is running.");
            } else {
                plugin.getLogger().warning("FAH client failed to start.");
            }

        } catch (Exception e) {
            plugin.getLogger().warning(() -> "Could not start FAH Client: " + e.getMessage());
        }
    }

    private int findAvailablePort(int startPort) {
        int port = startPort;
        while (port < 65535) { // Maximum port number
            try (ServerSocket socket = new ServerSocket(port)) {
                socket.setReuseAddress(true);
                return port; // Port is available
            } catch (IOException e) {
                port++; // Try the next port
            }
        }
        throw new IllegalStateException("No available ports found starting from " + startPort);
    }

    private int getMinecraftServerPort() {
        File serverProperties = new File("server.properties");
        if (serverProperties.exists()) {
            try (FileInputStream fis = new FileInputStream(serverProperties)) {
                Properties props = new Properties();
                props.load(fis);
                String portStr = props.getProperty("server-port");
                if (portStr != null) {
                    return Integer.parseInt(portStr.trim());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read server.properties for server-port: " + e.getMessage());
            }
        }
        // Default Minecraft port
        return 25565;
    }

        private void updateConfigXml(AccountInfo account, CausePreference cause, int controlPort, int webPort) throws IOException {
                String controlConfig = "";
                // Control port: if <=0, auto-detect a free one after the Minecraft server port
                if (controlPort <= 0) {
                        int mcPort = getMinecraftServerPort();
                        controlPort = findAvailablePort(mcPort + 1);
                        plugin.getLogger().info("Auto-detected control port: " + controlPort);
                }
                // FAH v8 minimal: do not set command-port/command-allow-no-pass; only allow localhost
                controlConfig = "  <allow>127.0.0.1</allow>\n";

                // Web interface disabled by default for safety (no extra tags needed)

                int initialCores = calculateInitialCores(Bukkit.getOnlinePlayers().size());
                if (initialCores == 1) initialCores = 0; // don't run FAH on 1 core
                String machineName = plugin.getConfig().getString("folding-at-home.account.machine-name", "Minecraft-Server");
                String accountToken = plugin.getConfig().getString("folding-at-home.account.account-token", "");

                String configXml;
                if (accountToken != null && !accountToken.isEmpty()) {
                        // Token-based linking to existing FAH account
                        configXml = String.format(
                                """
                                <config>
                                    <!-- Account Token Configuration -->
                                    <account-token v='%s'/>
                                    <machine-name v='%s'/>

                                    <!-- Slot Configuration -->
                                    <slot id='0' type='CPU'>
                                        <cpus v='%d'/>
                                    </slot>

                                    <!-- Remote Control -->
                                %s
                                </config>
                                """,
                                accountToken,
                                machineName,
                                initialCores,
                                controlConfig
                        );
                } else {
                        // Traditional username/passkey configuration
                        String username = account.anonymous ? "Anonymous" : (account.username == null || account.username.isEmpty() ? "Anonymous" : account.username);
                        String teamId = (account.teamId == null || account.teamId.isEmpty()) ? "0" : account.teamId;
                        String passkey = account.passkey == null ? "" : account.passkey;

                        configXml = String.format(
                                """
                                <config>
                                    <!-- User Configuration -->
                                    <user v='%s'/>
                                    <team v='%s'/>
                                    <passkey v='%s'/>

                                    <!-- Machine Name -->
                                    <machine-name v='%s'/>

                                    <!-- Slot Configuration -->
                                    <slot id='0' type='CPU'>
                                        <cpus v='%d'/>
                                    </slot>

                                    <!-- Remote Control -->
                                %s
                                </config>
                                """,
                                username,
                                teamId,
                                passkey,
                                machineName,
                                initialCores,
                                controlConfig
                        );
                }

                File configFile = new File(fahDirectory, "config.xml");
                // If initialCores==0, still write 2 into config to satisfy FAH minimum when it starts later
                String finalized = (initialCores <= 0) ? configXml.replace("<cpus v='0'/>", "<cpus v='2'/>") : configXml;
                Files.write(configFile.toPath(), finalized.getBytes());
                plugin.getLogger().info("Wrote FAH config.xml");
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

            // verification is performed when a token-based reconfigure runs
        } else {
            plugin.getLogger().info("FAH client is already running");
        }
    }

    public void verifyTokenAppliedAsync(java.util.function.Consumer<Boolean> callback) {
        // Use current configured machine name and token if available
    // token not required for verification here; we'll probe HTTP API and log file instead
        String machineName = plugin.getConfig().getString("folding-at-home.account.machine-name", "Minecraft-Server");
        executor.schedule(() -> {
            boolean success = false;
            try {
                java.net.URI uri = java.net.URI.create("http://127.0.0.1:7396/");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                if (code == 200) {
                    plugin.getLogger().info(() -> "FAH HTTP API reachable at 127.0.0.1:7396 — token likely applied");
                    success = true;
                }
            } catch (IOException ignored) {
                // Ignore — fall back to log inspection
            }

            if (!success) {
                try {
                    File logFile = new File(fahDirectory, "log.txt");
                    if (logFile.exists()) {
                        List<String> lines = Files.readAllLines(logFile.toPath());
                        for (int i = Math.max(0, lines.size() - 200); i < lines.size(); i++) {
                            String ln = lines.get(i);
                            if ((machineName != null && !machineName.isBlank() && ln.contains(machineName)) || ln.toLowerCase().contains("registered") || ln.toLowerCase().contains("linked to your account")) {
                                success = true;
                                break;
                            }
                        }
                    } else {
                        plugin.getLogger().warning(() -> "FAH log not found to verify token application");
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning(() -> "Error while verifying FAH token application: " + e.getMessage());
                }
            }

            boolean finalSuccess = success;
            try {
                // run the callback on the main server thread to avoid threading issues with Bukkit API usage
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalSuccess));
            } catch (Exception e) {
                // If scheduling fails, try to call directly
                try { callback.accept(finalSuccess); } catch (Exception ignored) {}
            }
        }, 3, TimeUnit.SECONDS);
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
                plugin.getLogger().warning(() -> "Failed to unpause: " + e.getMessage());
        }
    }

    public void setCores(int cores) {
        // enforce FAH min 2 cores; use 0 to pause instead of 1
        int clamped = (cores == 1 ? 0 : cores);
        if (clamped == currentCores) return;

    int controlPort = plugin.getConfig().getInt("folding-at-home.ports.control-port", 0);
    String noPortMode = plugin.getConfig().getString("folding-at-home.ports.no-port-mode", "file-based");

        if (controlPort == 0 && noPortMode != null && noPortMode.equals("file-based")) {
            // File-based control for hosts with no ports
            setCoresFileMode(clamped);
            return;
        }

        final int targetCores = clamped;
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
                        } catch (IOException | RuntimeException e) {
                            // Still not connected
                            plugin.getLogger().info(() -> "[No Control] Would set FAH to " + targetCores + " cores");
                            // In file mode, write to control file
                            if ("file-based".equals(noPortMode)) {
                                setCoresFileMode(targetCores);
                            }
                            currentCores = targetCores;
                            return;
                        }
                    } else {
                        // No port configured - use file mode
                        setCoresFileMode(targetCores);
                        currentCores = targetCores;
                        return;
                    }
                }

                // Now we should be connected via socket
                if (targetCores == 0) {
                    sendCommand("pause");
                    plugin.getLogger().info("FAH paused - all cores needed for Minecraft");
                } else {
                    sendCommand("unpause");
                    Thread.sleep(500);
                    sendCommand("slot-modify 0 cpus " + targetCores);
                    plugin.getLogger().info(() -> "FAH set to use " + targetCores + " cores");
                    Thread.sleep(500);
                    sendCommand("unpause");
                }

                currentCores = targetCores;

            } catch (IOException e) {
                plugin.getLogger().warning(() -> String.format("Error occurred: %s", e.getMessage()));
            } catch (InterruptedException e) {
                plugin.getLogger().warning(() -> String.format("Interrupted while setting cores: %s", e.getMessage()));
                Thread.currentThread().interrupt();
            } catch (IllegalStateException | SecurityException e) {
                plugin.getLogger().warning(() -> String.format("Unexpected error: %s", e.getMessage()));
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

                    // Update the cpus value in config; support both v='x' and value="x" styles
                    config = config.replaceAll("<cpus v='\\d+'/>", "<cpus v='" + cores + "'/>");
                    config = config.replaceAll("<cpus value=\"\\d+\"/>", "<cpus value=\"" + cores + "\"/>");
                    Files.write(configFile.toPath(), config.getBytes());

                    // Restart FAH if it was stopped or core count changed significantly
                    if (fahProcess == null || !fahProcess.isAlive() || Math.abs(cores - currentCores) > 2) {
                        if (fahProcess != null && fahProcess.isAlive()) {
                            plugin.getLogger().info(() -> "Restarting FAH with " + cores + " cores");
                            fahProcess.destroy();
                            Thread.sleep(2000);
                        } else {
                            plugin.getLogger().info(() -> "Starting FAH with " + cores + " cores");
                        }

                        // Restart FAH
                        startFAHClient();
                    } else {
                        plugin.getLogger().info(() -> "FAH continues with " + cores + " cores (minor change)");
                    }
                }
            }

            currentCores = cores;

        } catch (IOException e) {
            plugin.getLogger().warning(() -> String.format("Failed to update cores in file mode: %s", e.getMessage()));
        } catch (InterruptedException e) {
            plugin.getLogger().warning(() -> String.format("Interrupted while updating cores in file mode: %s", e.getMessage()));
            Thread.currentThread().interrupt();
        } catch (IllegalStateException | SecurityException e) {
            plugin.getLogger().warning(() -> String.format("Failed to update cores in file mode: %s", e.getMessage()));
        }
    }

        public void reconfigureWithToken(String accountToken, String machineName) {
        try {
                        // Create new config with token (unified with updateConfigXml)
                        int initialCores = calculateInitialCores(Bukkit.getOnlinePlayers().size());
                        if (initialCores == 1) initialCores = 0; // pause rather than run at 1
            // control port not used in minimal config
                        // Minimal FAH v8 compatible remote control section
                        String controlConfig = "  <allow>127.0.0.1</allow>\n";

                        String configXml = String.format(
                                """
                                <config>
                                    <!-- Account Token Configuration -->
                                    <account-token v='%s'/>
                                    <machine-name v='%s'/>

                                    <!-- Slot Configuration -->
                                    <slot id='0' type='CPU'>
                                        <cpus v='%d'/>
                                    </slot>

                                    <!-- Remote Control -->
                                %s
                                </config>
                                """,
                                accountToken,
                                machineName,
                                initialCores,
                                controlConfig
                        );

            File configFile = new File(fahDirectory, "config.xml");
            String finalized = (initialCores <= 0) ? configXml.replace("<cpus v='0'/>", "<cpus v='2'/>") : configXml;
            Files.write(configFile.toPath(), finalized.getBytes());

            plugin.getLogger().info(() -> "Reconfigured FAH with account token for machine: " + machineName);

            // Restart FAH client to apply changes
            if (fahProcess != null && fahProcess.isAlive()) {
                plugin.getLogger().info("Restarting FAH client to apply token...");
                fahProcess.destroy();
                fahProcess.waitFor(10, TimeUnit.SECONDS);
            }
            startFAHClient();

        } catch (IOException | InterruptedException e) {
            plugin.getLogger().warning(() -> String.format("Failed to reconfigure with token: %s", e.getMessage()));
            Thread.currentThread().interrupt();
        } catch (IllegalStateException | SecurityException e) {
            plugin.getLogger().warning(() -> String.format("Failed to reconfigure with token: %s", e.getMessage()));
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

    public void reconfigureFAHClient() {
        try {
            updateConfigXml(currentAccount, currentCause);
            sendCommand("config-rotate");
            plugin.getLogger().info("Updated F@H configuration");
        } catch (Exception e) {
            plugin.getLogger().warning(() -> "Failed to update F@H config: " + e.getMessage());
        }
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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning(() -> "Interrupted while shutting down FAH: " + e.getMessage());
        } catch (IOException e) {
            plugin.getLogger().warning(() -> "IO error while shutting down FAH: " + e.getMessage());
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
        // Use platform manager if available, otherwise fall back to original logic
        if (platformManager != null) {
            plugin.getLogger().info(() -> "Using platform-specific core allocation logic.");
            return platformManager.calculateFAHCores(playerCount);
        }

        // Fallback to original allocation logic
        int total = plugin.getConfig().getInt("server.total-cores", 8);
        int reserved = plugin.getConfig().getInt("server.reserved-cores", 1);
        double cpp = plugin.getConfig().getDouble("allocation.dynamic.cores-per-player", 0.5);

        // Adjust for Pterodactyl environment
        if (ServerEnvironmentDetector.detectEnvironment().getType() == ServerEnvironmentDetector.EnvironmentType.PTERODACTYL) {
            plugin.getLogger().info(() -> "Detected Pterodactyl environment. Adjusting core allocation.");
            cpp = plugin.getConfig().getDouble("allocation.dynamic.cores-per-player-pterodactyl", 0.25);
        }

        double neededForMC = reserved + (playerCount * cpp);
        int availableForFAH = (int) Math.floor(total - neededForMC);
        int allocatedCores = Math.max(1, Math.min(availableForFAH, total - reserved));

    plugin.getLogger().info(() -> "Calculated cores for FAH: " + allocatedCores);
        return allocatedCores;
    }

    // Define connectToControl method to resolve undefined reference
    private void connectToControl(int controlPort) throws IOException {
        if (controlPort <= 0) {
            throw new IOException("Invalid control port: " + controlPort);
        }
        
        try {
            plugin.getLogger().info(() -> "Connecting to FAH control interface on port: " + controlPort);
            controlSocket = new Socket("localhost", controlPort);
            controlWriter = new PrintWriter(controlSocket.getOutputStream(), true);
            controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            
            plugin.getLogger().info(() -> "Successfully connected to FAH control interface");
        } catch (IOException e) {
            plugin.getLogger().warning(() -> "Failed to connect to FAH control interface: " + e.getMessage());
            throw e;
        }
    }
}