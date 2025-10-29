package com.thijs226.fahdonor.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.thijs226.fahdonor.FAHResourceDonor;

/**
 * Manages time-based scheduling for Folding@home operations.
 * Allows folding during specific hours and days to optimize resource usage.
 */
public class ScheduleManager {
    
    private final FAHResourceDonor plugin;
    private BukkitTask schedulerTask;
    private boolean currentlyEnabled = true;
    
    // Schedule configuration
    private boolean scheduleEnabled = false;
    private LocalTime startTime = LocalTime.of(22, 0); // 10 PM
    private LocalTime endTime = LocalTime.of(6, 0);    // 6 AM
    private Set<DayOfWeek> activeDays = EnumSet.allOf(DayOfWeek.class);
    private int coresOffPeak = 4;
    private int coresPeak = 1;
    
    public ScheduleManager(FAHResourceDonor plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }
    
    public void start() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }
        
        if (!scheduleEnabled) {
            return;
        }
        
        // Check schedule every minute
        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkAndApplySchedule();
            }
        }.runTaskTimer(plugin, 20L, 20L * 60L); // Every 60 seconds
        
        // Apply schedule immediately
        checkAndApplySchedule();
    }
    
    public void stop() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }
    
    private void checkAndApplySchedule() {
        boolean shouldBeActive = isWithinSchedule();
        
        if (shouldBeActive != currentlyEnabled) {
            currentlyEnabled = shouldBeActive;
            applyScheduleChange(shouldBeActive);
        }
    }
    
    private boolean isWithinSchedule() {
        if (!scheduleEnabled) {
            return true;
        }
        
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek currentDay = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();
        
        // Check if current day is in active days
        if (!activeDays.contains(currentDay)) {
            return false;
        }
        
        // Handle schedules that cross midnight
        if (startTime.isAfter(endTime)) {
            // Example: 22:00 to 06:00 (overnight)
            return currentTime.isAfter(startTime) || currentTime.isBefore(endTime);
        } else {
            // Example: 08:00 to 17:00 (same day)
            return currentTime.isAfter(startTime) && currentTime.isBefore(endTime);
        }
    }
    
    private void applyScheduleChange(boolean activate) {
        if (plugin.getFAHManager() == null) {
            return;
        }
        
        try {
            if (activate) {
                // Off-peak hours - use more cores
                plugin.getLogger().info("Entering off-peak hours. Increasing FAH resource allocation.");
                plugin.getFAHManager().setCores(coresOffPeak);
                
                if (plugin.getFAHClient() != null) {
                    plugin.getFAHClient().resume();
                }
                
                plugin.notifyAdmins(
                    String.format("FAH schedule: Now in OFF-PEAK mode (%d cores)", coresOffPeak),
                    org.bukkit.ChatColor.GREEN,
                    false
                );
            } else {
                // Peak hours - use fewer cores
                plugin.getLogger().info("Entering peak hours. Reducing FAH resource allocation.");
                plugin.getFAHManager().setCores(coresPeak);
                
                if (coresPeak == 0 && plugin.getFAHClient() != null) {
                    plugin.getFAHClient().pause();
                }
                
                plugin.notifyAdmins(
                    String.format("FAH schedule: Now in PEAK mode (%d cores)", coresPeak),
                    org.bukkit.ChatColor.YELLOW,
                    false
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning(() -> "Failed to apply schedule change: " + e.getMessage());
        }
    }
    
    public void setSchedule(LocalTime start, LocalTime end, Set<DayOfWeek> days, int offPeakCores, int peakCores) {
        this.startTime = start;
        this.endTime = end;
        this.activeDays = days;
        this.coresOffPeak = offPeakCores;
        this.coresPeak = peakCores;
        
        saveConfiguration();
        checkAndApplySchedule();
    }
    
    public void setEnabled(boolean enabled) {
        this.scheduleEnabled = enabled;
        saveConfiguration();
        
        if (enabled) {
            start();
        } else {
            stop();
            // Reset to normal operation
            if (plugin.getFAHManager() != null) {
                int defaultCores = plugin.getConfig().getInt("server.total-cores", 4) - 
                                  plugin.getConfig().getInt("server.reserved-cores", 1);
                try {
                    plugin.getFAHManager().setCores(Math.max(1, defaultCores));
                } catch (Exception e) {
                    plugin.getLogger().warning(() -> "Failed to reset cores: " + e.getMessage());
                }
            }
        }
    }
    
    public boolean isEnabled() {
        return scheduleEnabled;
    }
    
    public boolean isCurrentlyActive() {
        return currentlyEnabled;
    }
    
    public LocalTime getStartTime() {
        return startTime;
    }
    
    public LocalTime getEndTime() {
        return endTime;
    }
    
    public Set<DayOfWeek> getActiveDays() {
        return EnumSet.copyOf(activeDays);
    }
    
    public int getCoresOffPeak() {
        return coresOffPeak;
    }
    
    public int getCoresPeak() {
        return coresPeak;
    }
    
    public String getScheduleStatus() {
        if (!scheduleEnabled) {
            return "Schedule: Disabled (always active)";
        }
        
        StringBuilder status = new StringBuilder();
        status.append("Schedule: Enabled\n");
        status.append(String.format("Active Hours: %s - %s\n", startTime, endTime));
        status.append("Active Days: ");
        status.append(activeDays.stream()
            .map(DayOfWeek::toString)
            .reduce((a, b) -> a + ", " + b)
            .orElse("None"));
        status.append("\n");
        status.append(String.format("Off-Peak Cores: %d\n", coresOffPeak));
        status.append(String.format("Peak Cores: %d\n", coresPeak));
        status.append(String.format("Current Mode: %s\n", currentlyEnabled ? "OFF-PEAK" : "PEAK"));
        
        return status.toString();
    }
    
    private void loadConfiguration() {
        var config = plugin.getConfig();
        scheduleEnabled = config.getBoolean("scheduling.enabled", false);
        
        String startTimeStr = config.getString("scheduling.off-peak-start", "22:00");
        String endTimeStr = config.getString("scheduling.off-peak-end", "06:00");
        
        try {
            startTime = LocalTime.parse(startTimeStr);
            endTime = LocalTime.parse(endTimeStr);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid time format in schedule configuration. Using defaults.");
            startTime = LocalTime.of(22, 0);
            endTime = LocalTime.of(6, 0);
        }
        
        // Load active days
        var daysList = config.getStringList("scheduling.active-days");
        if (!daysList.isEmpty()) {
            activeDays = EnumSet.noneOf(DayOfWeek.class);
            for (String day : daysList) {
                try {
                    activeDays.add(DayOfWeek.valueOf(day.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    String dayName = day;
                    plugin.getLogger().warning(() -> "Invalid day in schedule: " + dayName);
                }
            }
        }
        
        coresOffPeak = config.getInt("scheduling.cores-off-peak", 4);
        coresPeak = config.getInt("scheduling.cores-peak", 1);
    }
    
    private void saveConfiguration() {
        var config = plugin.getConfig();
        config.set("scheduling.enabled", scheduleEnabled);
        config.set("scheduling.off-peak-start", startTime.toString());
        config.set("scheduling.off-peak-end", endTime.toString());
        config.set("scheduling.cores-off-peak", coresOffPeak);
        config.set("scheduling.cores-peak", coresPeak);
        
        var dayNames = activeDays.stream()
            .map(DayOfWeek::toString)
            .toArray(String[]::new);
        config.set("scheduling.active-days", dayNames);
        
        plugin.saveConfig();
    }
}
