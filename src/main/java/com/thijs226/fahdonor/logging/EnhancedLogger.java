package com.thijs226.fahdonor.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

/**
 * Enhanced logging system with categories, file output, and async writing.
 * Provides structured logging for different components of the plugin.
 */
public class EnhancedLogger {
    
    private final Plugin plugin;
    private final ConcurrentLinkedQueue<LogEntry> logQueue = new ConcurrentLinkedQueue<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final File logDirectory;
    private Thread logWriterThread;
    private volatile boolean running = false;
    
    public enum LogCategory {
        FAH_CLIENT("FAHClient"),
        PERFORMANCE("Performance"),
        DATABASE("Database"),
        PLAYER("Player"),
        REWARD("Reward"),
        GUI("GUI"),
        NOTIFICATION("Notification"),
        CONFIG("Config"),
        GENERAL("General");
        
        private final String displayName;
        
        LogCategory(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private record LogEntry(LogCategory category, Level level, String message, Throwable throwable, long timestamp) {}
    
    public EnhancedLogger(Plugin plugin) {
        this.plugin = plugin;
        this.logDirectory = new File(plugin.getDataFolder(), "logs");
        if (!logDirectory.exists()) {
            logDirectory.mkdirs();
        }
    }
    
    /**
     * Starts the async log writer
     */
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        logWriterThread = new Thread(this::processLogQueue, "FAH-LogWriter");
        logWriterThread.setDaemon(true);
        logWriterThread.start();
    }
    
    /**
     * Stops the async log writer
     */
    public void stop() {
        running = false;
        if (logWriterThread != null) {
            logWriterThread.interrupt();
            try {
                logWriterThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Flush remaining logs
        flushLogs();
    }
    
    /**
     * Logs a message with category
     */
    public void log(LogCategory category, Level level, String message) {
        log(category, level, message, null);
    }
    
    /**
     * Logs a message with category and throwable
     */
    public void log(LogCategory category, Level level, String message, Throwable throwable) {
        LogEntry entry = new LogEntry(category, level, message, throwable, System.currentTimeMillis());
        logQueue.offer(entry);
        
        // Also log to console for important messages
        if (level.intValue() >= Level.WARNING.intValue()) {
            String consoleMessage = String.format("[%s] %s", category.getDisplayName(), message);
            if (throwable != null) {
                plugin.getLogger().log(level, consoleMessage, throwable);
            } else {
                plugin.getLogger().log(level, consoleMessage);
            }
        }
    }
    
    /**
     * Convenience methods for each category
     */
    public void logFAHClient(Level level, String message) {
        log(LogCategory.FAH_CLIENT, level, message);
    }
    
    public void logFAHClient(Level level, String message, Throwable throwable) {
        log(LogCategory.FAH_CLIENT, level, message, throwable);
    }
    
    public void logPerformance(Level level, String message) {
        log(LogCategory.PERFORMANCE, level, message);
    }
    
    public void logDatabase(Level level, String message) {
        log(LogCategory.DATABASE, level, message);
    }
    
    public void logDatabase(Level level, String message, Throwable throwable) {
        log(LogCategory.DATABASE, level, message, throwable);
    }
    
    public void logPlayer(Level level, String message) {
        log(LogCategory.PLAYER, level, message);
    }
    
    public void logReward(Level level, String message) {
        log(LogCategory.REWARD, level, message);
    }
    
    public void logGUI(Level level, String message) {
        log(LogCategory.GUI, level, message);
    }
    
    public void logNotification(Level level, String message) {
        log(LogCategory.NOTIFICATION, level, message);
    }
    
    public void logConfig(Level level, String message) {
        log(LogCategory.CONFIG, level, message);
    }
    
    /**
     * Processes the log queue asynchronously
     */
    private void processLogQueue() {
        while (running) {
            try {
                if (!logQueue.isEmpty()) {
                    flushLogs();
                }
                Thread.sleep(5000); // Flush every 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Flushes logs to file
     */
    private void flushLogs() {
        if (logQueue.isEmpty()) {
            return;
        }
        
        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File logFile = new File(logDirectory, "fah-" + dateStr + ".log");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            LogEntry entry;
            while ((entry = logQueue.poll()) != null) {
                String timestamp = dateFormat.format(new Date(entry.timestamp));
                String logLine = String.format("[%s] [%s] [%s] %s",
                    timestamp,
                    entry.level.getName(),
                    entry.category.getDisplayName(),
                    entry.message
                );
                
                writer.println(logLine);
                
                if (entry.throwable != null) {
                    entry.throwable.printStackTrace(writer);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write to log file", e);
        }
    }
    
    /**
     * Gets the size of the log queue
     */
    public int getQueueSize() {
        return logQueue.size();
    }
}
