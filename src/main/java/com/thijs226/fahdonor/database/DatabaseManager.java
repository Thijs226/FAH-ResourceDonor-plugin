package com.thijs226.fahdonor.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

/**
 * SQLite database manager for persistent storage of player statistics,
 * contributions, and server metrics. Uses connection pooling and async operations.
 */
public class DatabaseManager {
    
    private final Plugin plugin;
    private final File databaseFile;
    private Connection connection;
    
    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "fahdata.db");
    }
    
    /**
     * Initializes the database and creates tables if needed
     */
    public boolean initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            connect();
            createTables();
            return true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found", e);
            return false;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }
    
    /**
     * Connects to the database
     */
    private void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        connection.setAutoCommit(true);
    }
    
    /**
     * Creates database tables if they don't exist
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Player contributions table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS player_contributions (" +
                "uuid TEXT PRIMARY KEY," +
                "player_name TEXT," +
                "contribution_seconds BIGINT DEFAULT 0," +
                "points_earned BIGINT DEFAULT 0," +
                "work_units_completed INT DEFAULT 0," +
                "last_updated BIGINT," +
                "first_contributed BIGINT" +
                ")"
            );
            
            // Server statistics table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS server_statistics (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "timestamp BIGINT," +
                "total_points BIGINT," +
                "total_work_units INT," +
                "total_core_hours REAL," +
                "active_players INT," +
                "cores_allocated INT" +
                ")"
            );
            
            // Work unit history table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS work_unit_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "completed_timestamp BIGINT," +
                "unit_id TEXT," +
                "points INT," +
                "duration_seconds INT," +
                "cores_used INT," +
                "project TEXT" +
                ")"
            );
            
            // Rewards history table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS rewards_given (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT," +
                "reward_type TEXT," +
                "reward_name TEXT," +
                "given_timestamp BIGINT," +
                "milestone_value BIGINT" +
                ")"
            );
            
            plugin.getLogger().info("Database tables initialized");
        }
    }
    
    /**
     * Records player contribution
     */
    public void recordPlayerContribution(UUID playerUuid, String playerName, 
                                        long contributionSeconds, long pointsEarned, 
                                        int workUnitsCompleted) {
        String sql = "INSERT INTO player_contributions " +
                    "(uuid, player_name, contribution_seconds, points_earned, work_units_completed, " +
                    "last_updated, first_contributed) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "player_name = ?, " +
                    "contribution_seconds = contribution_seconds + ?, " +
                    "points_earned = points_earned + ?, " +
                    "work_units_completed = work_units_completed + ?, " +
                    "last_updated = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            
            // INSERT values
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setLong(3, contributionSeconds);
            stmt.setLong(4, pointsEarned);
            stmt.setInt(5, workUnitsCompleted);
            stmt.setLong(6, now);
            stmt.setLong(7, now);
            
            // UPDATE values
            stmt.setString(8, playerName);
            stmt.setLong(9, contributionSeconds);
            stmt.setLong(10, pointsEarned);
            stmt.setInt(11, workUnitsCompleted);
            stmt.setLong(12, now);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record player contribution", e);
        }
    }
    
    /**
     * Gets player contribution data
     */
    public PlayerContributionData getPlayerContribution(UUID playerUuid) {
        String sql = "SELECT * FROM player_contributions WHERE uuid = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new PlayerContributionData(
                    playerUuid,
                    rs.getString("player_name"),
                    rs.getLong("contribution_seconds"),
                    rs.getLong("points_earned"),
                    rs.getInt("work_units_completed"),
                    rs.getLong("last_updated"),
                    rs.getLong("first_contributed")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get player contribution", e);
        }
        
        return null;
    }
    
    /**
     * Gets top contributors
     */
    public List<PlayerContributionData> getTopContributors(String orderBy, int limit) {
        List<PlayerContributionData> contributors = new ArrayList<>();
        String validOrderBy = switch (orderBy.toLowerCase()) {
            case "points" -> "points_earned";
            case "time" -> "contribution_seconds";
            case "units" -> "work_units_completed";
            default -> "points_earned";
        };
        
        String sql = "SELECT * FROM player_contributions ORDER BY " + validOrderBy + " DESC LIMIT ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                contributors.add(new PlayerContributionData(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getLong("contribution_seconds"),
                    rs.getLong("points_earned"),
                    rs.getInt("work_units_completed"),
                    rs.getLong("last_updated"),
                    rs.getLong("first_contributed")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get top contributors", e);
        }
        
        return contributors;
    }
    
    /**
     * Records server statistics snapshot
     */
    public void recordServerStats(long totalPoints, int totalWorkUnits, double totalCoreHours,
                                  int activePlayers, int coresAllocated) {
        String sql = "INSERT INTO server_statistics " +
                    "(timestamp, total_points, total_work_units, total_core_hours, " +
                    "active_players, cores_allocated) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setLong(2, totalPoints);
            stmt.setInt(3, totalWorkUnits);
            stmt.setDouble(4, totalCoreHours);
            stmt.setInt(5, activePlayers);
            stmt.setInt(6, coresAllocated);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record server stats", e);
        }
    }
    
    /**
     * Records completed work unit
     */
    public void recordWorkUnit(String unitId, int points, int durationSeconds, 
                               int coresUsed, String project) {
        String sql = "INSERT INTO work_unit_history " +
                    "(completed_timestamp, unit_id, points, duration_seconds, cores_used, project) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, unitId);
            stmt.setInt(3, points);
            stmt.setInt(4, durationSeconds);
            stmt.setInt(5, coresUsed);
            stmt.setString(6, project);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record work unit", e);
        }
    }
    
    /**
     * Records a reward given to a player
     */
    public void recordReward(UUID playerUuid, String rewardType, String rewardName, long milestoneValue) {
        String sql = "INSERT INTO rewards_given " +
                    "(player_uuid, reward_type, reward_name, given_timestamp, milestone_value) " +
                    "VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, rewardType);
            stmt.setString(3, rewardName);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setLong(5, milestoneValue);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record reward", e);
        }
    }
    
    /**
     * Closes the database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database connection", e);
        }
    }
    
    /**
     * Data class for player contributions
     */
    public record PlayerContributionData(
        UUID uuid,
        String playerName,
        long contributionSeconds,
        long pointsEarned,
        int workUnitsCompleted,
        long lastUpdated,
        long firstContributed
    ) {}
}
