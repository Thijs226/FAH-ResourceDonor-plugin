package com.thijs226.fahdonor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

/**
 * Folding@home client bridge that derives live status and statistics from the
 * official FAHClient log output. All information exposed by this class is based
 * on real telemetry written by the Folding@home binary instead of simulated
 * placeholders.
 */
public class FAHClient {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final java.util.regex.Pattern SLOT_PATTERN = java.util.regex.Pattern.compile("(WU\\d{2}:FS\\d{2})");
    private static final java.util.regex.Pattern PROGRESS_PATTERN = java.util.regex.Pattern.compile(
            "Completed\\s+\\d+\\s+out of\\s+\\d+\\s+steps\\s+\\((\\d+)%\\)");
    private static final java.util.regex.Pattern CREDIT_PATTERN = java.util.regex.Pattern.compile(
            "(Final credit estimate|Server reports credit)[:,]?\\s*([0-9,]+)\\s+points",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern PROJECT_PATTERN = java.util.regex.Pattern.compile("Project:\\s*(.+)");
    private static final java.util.regex.Pattern CORE_FAILURE_PATTERN = java.util.regex.Pattern.compile(
        "Core returned\\s+([^\\s]+)(?:\\s*\\((\\d+)\\))?");

    private final FAHResourceDonor plugin;
    private final FAHClientManager manager;
    private final Path fahDirectory;
    private final Path logFile;

    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AtomicInteger progressPercent = new AtomicInteger(0);
    private final AtomicLong totalPoints = new AtomicLong(0);
    private final AtomicInteger completedUnits = new AtomicInteger(0);
    private final AtomicLong totalCoreSeconds = new AtomicLong(0);
    private final AtomicLong lastStatusUpdateSeconds = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicBoolean autoRestartSuppressed = new AtomicBoolean(false);
    private final AtomicBoolean failureAlertLogged = new AtomicBoolean(false);
    private final AtomicInteger consecutiveLogFailures = new AtomicInteger(0);
    private final AtomicBoolean logFailureAlerted = new AtomicBoolean(false);
    private final AtomicLong lastPollSuccessMillis = new AtomicLong(System.currentTimeMillis());
    private final AtomicReference<String> lastStructuredStatusKey = new AtomicReference<>("");

    private final Map<String, WorkUnitState> activeWorkUnits = new ConcurrentHashMap<>();
    private final Set<String> creditedWorkUnits = ConcurrentHashMap.newKeySet();

    private BukkitTask monitorTask;
    private long lastLogPointer = 0L;
    private long dayOffsetSeconds = 0L;
    private long lastTimestampSeconds = -1L;

    private volatile String currentWorkUnitId = "";
    private volatile String currentProjectSummary = "";
    private volatile String statusMessage = "Initializing Folding@home monitoring";

    private static final class WorkUnitState {
        final long startSeconds;
        final int allocatedCores;
        volatile String projectSummary;

        WorkUnitState(long startSeconds, int allocatedCores) {
            this.startSeconds = startSeconds;
            this.allocatedCores = Math.max(allocatedCores, 1);
        }
    }

    public FAHClient(FAHResourceDonor plugin, FAHClientManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.fahDirectory = plugin.getDataFolder().toPath().resolve("folding-at-home");
        this.logFile = fahDirectory.resolve("log.txt");
    }

    public boolean initialize(String token, String teamId, String donorName) {
        validatePasskey(donorName, token);

        try {
            Files.createDirectories(fahDirectory);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to prepare Folding@home data directory", e);
            return false;
        }

        if (manager != null && !manager.isFAHRunning()) {
            manager.forceStart();
        }

        startMonitoring();
        updateStatus("Waiting for Folding@home log", null, null);
        return true;
    }

    private void startMonitoring() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        // Start after a short delay to allow FAHClient to create its log file
        monitorTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::pollLog,
                60L,
                plugin.getConfig().getLong("monitoring.status-refresh-interval", 200L));
    }

    private void pollLog() {
        try {
            if (!Files.exists(logFile)) {
                processing.set(false);
                updateStatus("Waiting for Folding@home log", null, null);
                markLogPollFailure();
                return;
            }

            checkForStalledLog();

            long size = Files.size(logFile);
            if (size < lastLogPointer) {
                // Log rotated or truncated
                resetLogState();
            }

            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                raf.seek(lastLogPointer);
                String rawLine;
                while ((rawLine = raf.readLine()) != null) {
                    if (rawLine.isEmpty()) continue;
                    String line = normalizeLine(rawLine);
                    long timestampSeconds = extractTimestampSeconds(line);
                    handleLogLine(line, timestampSeconds);
                }
                lastLogPointer = raf.getFilePointer();
            }

            // If nothing is active, mark idle after the last update settles
            long nowSeconds = System.currentTimeMillis() / 1000L;
            if (!processing.get() && activeWorkUnits.isEmpty()
                    && nowSeconds - lastStatusUpdateSeconds.get() > 5) {
                currentWorkUnitId = "";
                currentProjectSummary = "";
                progressPercent.set(0);
                updateStatus("Idle", "Waiting for next Folding@home assignment", null);
            }

            markLogPollSuccess();

        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Unable to read Folding@home log", e);
            markLogPollFailure();
        }
    }

    private void resetLogState() {
        lastLogPointer = 0L;
        dayOffsetSeconds = 0L;
        lastTimestampSeconds = -1L;
        activeWorkUnits.clear();
        creditedWorkUnits.clear();
        totalPoints.set(0L);
        completedUnits.set(0);
        totalCoreSeconds.set(0L);
        progressPercent.set(0);
        processing.set(false);
        consecutiveFailures.set(0);
        totalFailures.set(0L);
        autoRestartSuppressed.set(false);
        failureAlertLogged.set(false);
        consecutiveLogFailures.set(0);
        logFailureAlerted.set(false);
        lastPollSuccessMillis.set(System.currentTimeMillis());
    }

    private void handleLogLine(String line, long timestampSeconds) {
        if (line.startsWith("***")) {
            // Log header / rotation marker
            return;
        }

        String slot = extractSlot(line);

        if (line.contains("Paused")) {
            processing.set(false);
            updateStatus("Paused", describeWorkUnit(slot), null);
            return;
        }

        if (line.contains("Resumed")) {
            processing.set(true);
            updateStatus("Resumed", describeWorkUnit(slot), progressPercent.get());
            return;
        }

        if (slot != null && line.contains("Starting")) {
            handleWorkUnitStart(slot, timestampSeconds);
            return;
        }

        if (slot != null) {
            if (line.contains("Project:")) {
                handleProjectLine(slot, line);
            }

            java.util.regex.Matcher progressMatcher = PROGRESS_PATTERN.matcher(line);
            if (progressMatcher.find()) {
                int percent = Integer.parseInt(progressMatcher.group(1));
                handleProgress(slot, percent);
                return;
            }

            java.util.regex.Matcher creditMatcher = CREDIT_PATTERN.matcher(line);
            if (creditMatcher.find()) {
                long credit = parseLong(creditMatcher.group(2));
                handleCredit(slot, credit, timestampSeconds);
                return;
            }

            if (line.contains("Folding@home Core Shutdown")) {
                processing.set(false);
                updateStatus("Core shutdown", describeWorkUnit(slot), null);
                return;
            }

            if (line.contains("Core returned")) {
                handleCoreFailure(slot, line);
                return;
            }
        }
    }

    private void handleWorkUnitStart(String slot, long timestampSeconds) {
        int allocatedCores = manager != null ? Math.max(1, manager.getCurrentCores()) : 1;
        WorkUnitState state = new WorkUnitState(timestampSeconds, allocatedCores);
        activeWorkUnits.put(slot, state);
        currentWorkUnitId = slot;
        currentProjectSummary = "";
        progressPercent.set(0);
        processing.set(true);
        updateStatus("Starting work unit", describeWorkUnit(slot), 0);
    }

    private void handleProjectLine(String slot, String line) {
        java.util.regex.Matcher matcher = PROJECT_PATTERN.matcher(line);
        if (!matcher.find()) {
            return;
        }
        String summary = matcher.group(1).trim();
        WorkUnitState state = activeWorkUnits.computeIfAbsent(slot,
                k -> new WorkUnitState(lastTimestampSeconds >= 0 ? lastTimestampSeconds : 0L,
                        manager != null ? Math.max(1, manager.getCurrentCores()) : 1));
        state.projectSummary = summary;
        if (slot.equals(currentWorkUnitId)) {
            currentProjectSummary = summary;
            updateStatus("Assigned project", describeWorkUnit(slot), null);
        }
    }

    private void handleProgress(String slot, int percent) {
        currentWorkUnitId = slot;
        WorkUnitState state = activeWorkUnits.get(slot);
        if (state != null && state.projectSummary != null) {
            currentProjectSummary = state.projectSummary;
        }
        progressPercent.set(percent);
        processing.set(true);
        consecutiveFailures.set(0);
        autoRestartSuppressed.set(false);
        failureAlertLogged.set(false);
        markLogPollSuccess();
        updateStatus("Processing", describeWorkUnit(slot), percent);
    }

    private void handleCredit(String slot, long credit, long timestampSeconds) {
        String key = slot + "#" + credit + "#" + timestampSeconds;
        if (creditedWorkUnits.add(key)) {
            WorkUnitState state = activeWorkUnits.remove(slot);
            if (state != null) {
                long durationSeconds = Math.max(0L, timestampSeconds - state.startSeconds);
                totalCoreSeconds.addAndGet(durationSeconds * (long) state.allocatedCores);
                if (state.projectSummary != null && !state.projectSummary.isBlank()) {
                    currentProjectSummary = state.projectSummary;
                }
            }

            totalPoints.addAndGet(credit);
            completedUnits.incrementAndGet();
            currentWorkUnitId = slot;
            progressPercent.set(100);
            processing.set(false);
            consecutiveFailures.set(0);
            autoRestartSuppressed.set(false);
            failureAlertLogged.set(false);
            markLogPollSuccess();

            String detail = describeWorkUnit(slot);
            String message = String.format("Completed %s (+%,d pts)",
                    detail.isBlank() ? slot : detail,
                    credit);
            updateStatus(message, null, null);
            plugin.getLogger().info(message);
        }
    }

    private String describeWorkUnit(String slot) {
        String slotId = slot != null ? slot : currentWorkUnitId;
        WorkUnitState state = slotId != null ? activeWorkUnits.get(slotId) : null;
        String project = (state != null && state.projectSummary != null && !state.projectSummary.isBlank())
                ? state.projectSummary
                : currentProjectSummary;

        if (project != null && !project.isBlank() && slotId != null && !slotId.isBlank()) {
            return project + " [" + slotId + "]";
        }
        if (project != null && !project.isBlank()) {
            return project;
        }
        return slotId != null ? slotId : "";
    }

    private String normalizeLine(String rawLine) {
        byte[] isoBytes = rawLine.getBytes(StandardCharsets.ISO_8859_1);
        return new String(isoBytes, StandardCharsets.UTF_8).trim();
    }

    private String extractSlot(String line) {
        java.util.regex.Matcher matcher = SLOT_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private long extractTimestampSeconds(String line) {
        if (line.length() < 8 || !Character.isDigit(line.charAt(0))) {
            return lastTimestampSeconds >= 0 ? lastTimestampSeconds : 0L;
        }
        try {
            LocalTime time = LocalTime.parse(line.substring(0, 8), LOG_TIME_FORMAT);
            long seconds = time.toSecondOfDay();
            if (lastTimestampSeconds >= 0 && seconds + dayOffsetSeconds < lastTimestampSeconds) {
                dayOffsetSeconds += 24 * 3600L;
            }
            long absoluteSeconds = seconds + dayOffsetSeconds;
            lastTimestampSeconds = absoluteSeconds;
            return absoluteSeconds;
        } catch (DateTimeParseException ex) {
            return lastTimestampSeconds >= 0 ? lastTimestampSeconds : 0L;
        }
    }

    private void updateStatus(String headline, String detail, Integer percent) {
        StringBuilder builder = new StringBuilder(headline);
        String resolvedDetail = (detail != null && !detail.isBlank()) ? detail : "";
        if (!resolvedDetail.isBlank()) {
            builder.append(" - ").append(resolvedDetail);
        }
        if (percent != null) {
            builder.append(" (").append(percent).append("%)");
        }
        statusMessage = builder.toString();
        lastStatusUpdateSeconds.set(System.currentTimeMillis() / 1000L);
        maybeEmitStructuredStatus(headline, resolvedDetail, percent);
    }

    private long parseLong(String value) {
        return Long.parseLong(value.replace(",", ""));
    }

    private void handleCoreFailure(String slot, String line) {
        processing.set(false);
        java.util.regex.Matcher matcher = CORE_FAILURE_PATTERN.matcher(line);
        String reason = "FAILED";
        String code = null;
        if (matcher.find()) {
            reason = matcher.group(1);
            code = matcher.group(2);
        }

        totalFailures.incrementAndGet();
        int consecutive = consecutiveFailures.incrementAndGet();

        String detail = describeWorkUnit(slot);
        String message = String.format("Work unit failure: %s%s%s",
                detail.isBlank() ? "" : detail + " ",
                reason,
                code != null ? " (" + code + ")" : "");

        updateStatus(message, null, null);
        plugin.getLogger().warning(message);

        if (consecutive >= 3 && autoRestartSuppressed.compareAndSet(false, true)) {
            if (failureAlertLogged.compareAndSet(false, true)) {
                plugin.getLogger().severe(() -> "Multiple consecutive Folding@home core failures detected. "
                        + "FAH auto-restart is now disabled until an administrator intervenes.");
            }
            if (manager != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        manager.setCores(0);
                    } catch (RuntimeException ex) {
                        plugin.getLogger().log(Level.WARNING, "Unable to pause FAH after repeated failures", ex);
                    }
                });
            }
        }
        markLogPollSuccess();
    }

    private void checkForStalledLog() {
        long lastSuccess = lastPollSuccessMillis.get();
        long now = System.currentTimeMillis();
        long thresholdMillis = plugin.getConfig().getLong("monitoring.log-watchdog.stall-threshold-seconds", 180) * 1000L;
        if (thresholdMillis <= 0) {
            thresholdMillis = 180_000L;
        }

        if (now - lastSuccess > thresholdMillis && logFailureAlerted.compareAndSet(false, true)) {
            plugin.notifyAdmins("No new Folding@home log entries detected in " + (thresholdMillis / 1000) + "s. Check if FAH is still running.", ChatColor.RED, true);
        }
    }

    private void markLogPollSuccess() {
        lastPollSuccessMillis.set(System.currentTimeMillis());
        consecutiveLogFailures.set(0);
        if (logFailureAlerted.compareAndSet(true, false)) {
            plugin.notifyAdmins("Folding@home log monitoring has recovered.", ChatColor.GREEN, true);
        }
    }

    private void markLogPollFailure() {
        int failures = consecutiveLogFailures.incrementAndGet();
        if (failures >= 3 && logFailureAlerted.compareAndSet(false, true)) {
            plugin.notifyAdmins("Unable to read Folding@home log. Monitoring paused until the file becomes accessible again.", ChatColor.RED, true);
        }
    }

    private void maybeEmitStructuredStatus(String headline, String detail, Integer percent) {
        if (!plugin.isStructuredLoggingEnabled()) {
            return;
        }

        int progressValue = percent != null ? percent : -1;
        String detailKey = detail == null ? "" : detail;
        String key = headline + '|' + detailKey + '|' + progressValue + '|' + processing.get() + '|' + autoRestartSuppressed.get() + '|' + totalFailures.get();
        String previous = lastStructuredStatusKey.get();
        if (key.equals(previous)) {
            return;
        }
        lastStructuredStatusKey.set(key);

        StringBuilder json = new StringBuilder();
        json.append("{\"event\":\"fah-status\"");
        json.append(",\"timestamp\":").append(System.currentTimeMillis());
        json.append(",\"status\":\"").append(escapeJson(headline)).append('\"');

        if (detail != null && !detail.isBlank()) {
            json.append(",\"detail\":\"").append(escapeJson(detail)).append('\"');
        }
        if (percent != null) {
            json.append(",\"progress\":").append(percent);
        }

        json.append(",\"isProcessing\":").append(processing.get());
        json.append(",\"autoRestartSuppressed\":").append(autoRestartSuppressed.get());
        json.append(",\"totalFailures\":").append(totalFailures.get());
        json.append(",\"completedUnits\":").append(completedUnits.get());
        json.append(",\"points\":").append(totalPoints.get());

        if (!currentWorkUnitId.isBlank()) {
            json.append(",\"workUnitId\":\"").append(escapeJson(currentWorkUnitId)).append('\"');
        }
        if (!currentProjectSummary.isBlank()) {
            json.append(",\"project\":\"").append(escapeJson(currentProjectSummary)).append('\"');
        }
        if (manager != null) {
            json.append(",\"allocatedCores\":").append(manager.getCurrentCores());
        }
        json.append('}');

        plugin.getLogger().info(json.toString());
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // Public API -------------------------------------------------------------

    public boolean isProcessingWork() {
        return processing.get();
    }

    public String getWorkUnitStatus() {
        return statusMessage;
    }

    public String getProgress() {
        return progressPercent.get() + "%";
    }

    public long getPointsEarned() {
        return totalPoints.get();
    }

    public int getCompletedWorkUnits() {
        return completedUnits.get();
    }

    public double getTotalCoreHours() {
        return totalCoreSeconds.get() / 3600.0;
    }

    public long getLastStatusUpdateEpochSeconds() {
        return lastStatusUpdateSeconds.get();
    }

    public long getTotalFailures() {
        return totalFailures.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public boolean isAutoRestartSuppressed() {
        return autoRestartSuppressed.get();
    }

    public void pause() {
        processing.set(false);
        if (manager != null) {
            manager.setCores(0);
        }
        updateStatus("Pause requested", null, null);
        plugin.getLogger().info("Requested FAH pause (core allocation set to 0).");
    }

    public void resume() {
        if (manager != null) {
            manager.forceStart();
            manager.forceUnpause();
        }
        consecutiveFailures.set(0);
        autoRestartSuppressed.set(false);
        failureAlertLogged.set(false);
        updateStatus("Resume requested", null, null);
        plugin.getLogger().info("Requested FAH resume.");
    }

    public boolean requestWorkUnit() {
        if (manager != null && !autoRestartSuppressed.get()) {
            manager.forceStart();
            manager.forceUnpause();
            updateStatus("Requested new work from Folding@home", null, null);
            return true;
        }
        return false;
    }

    public void shutdown() {
        processing.set(false);
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }
        activeWorkUnits.clear();
    }

    public boolean testConnection() {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create("https://foldingathome.org/").toURL()
                    .openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(5000);
            return connection.getResponseCode() == 200;
        } catch (IOException e) {
            plugin.getLogger().log(Level.INFO, "Connection test failed", e);
            return false;
        }
    }

    private void validatePasskey(String username, String passkey) {
        if (passkey == null || passkey.isEmpty()) {
            plugin.getLogger().warning(
                    "No passkey provided. You can still fold, but points may be limited. Get one at https://apps.foldingathome.org/getpasskey");
            return;
        }
        if (!passkey.matches("[A-Za-z0-9]{32}")) {
            plugin.getLogger().warning("Passkey format looks unusual (expected 32 alphanumeric chars). Double-check your token.");
        } else {
            plugin.getLogger().info("Passkey format looks valid.");
        }
        if (username == null || username.isBlank()) {
            plugin.getLogger().warning("Username is empty. Set a donor name to receive proper credit.");
        }
    }
}