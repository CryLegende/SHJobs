package org.Cry_Legende.sHJobs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.jobs.DailyJob;
import org.Cry_Legende.sHJobs.jobs.JobTemplate;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.jobs.LeaderboardEntry;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class SQLiteStorage implements StorageManager {

    private final SHJobs plugin;
    private final String filePath;
    private Connection connection;
    private final ExecutorService executorService;
    private final Gson gson;

    // Classe interne pour faciliter la sérialisation/désérialisation JSON de la progression des jobs
    static class TempDailyJobStorage {
        Map<String, Integer> objectiveProgress;
        boolean completed;
    }

    public SQLiteStorage(SHJobs plugin, String filePath) {
        this.plugin = plugin;
        this.filePath = filePath;
        // SQLite est sensible aux accès concurrents, un seul thread est plus sûr.
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SHJobs-SQLite-Thread");
            t.setDaemon(true);
            return t;
        });
        this.gson = new GsonBuilder().create();
    }

    @Override
    public void init() throws SQLException {
        File dbFile = new File(filePath);
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + filePath);
            plugin.getLogger().info("SQLite connection established to " + filePath);
            createTables();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found!", e);
            throw new SQLException("SQLite JDBC driver not found!", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite database!", e);
            throw e;
        }
    }

    private void createTables() throws SQLException {
        String createPlayerDataSQL = "CREATE TABLE IF NOT EXISTS shjobs_player_data ("
            + "uuid VARCHAR(36) PRIMARY KEY NOT NULL,"
            + "job_points BIGINT NOT NULL DEFAULT 0"
            + ");";
        String createPlayerJobsSQL = "CREATE TABLE IF NOT EXISTS shjobs_player_jobs ("
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "job_type VARCHAR(32) NOT NULL,"
            + "experience BIGINT NOT NULL DEFAULT 0,"
            + "level INT NOT NULL DEFAULT 1,"
            + "PRIMARY KEY (player_uuid, job_type),"
            + "FOREIGN KEY (player_uuid) REFERENCES shjobs_player_data(uuid) ON DELETE CASCADE"
            + ");";
        String createPlayerDailyJobsSQL = "CREATE TABLE IF NOT EXISTS shjobs_player_daily_jobs ("
            + "player_uuid VARCHAR(36) NOT NULL,"
            + "job_template_id VARCHAR(255) NOT NULL,"
            + "daily_job_json TEXT NOT NULL,"
            + "PRIMARY KEY (player_uuid, job_template_id),"
            + "FOREIGN KEY (player_uuid) REFERENCES shjobs_player_data(uuid) ON DELETE CASCADE"
            + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayerDataSQL);
            stmt.execute(createPlayerJobsSQL);
            stmt.execute(createPlayerDailyJobsSQL);
            plugin.getLogger().info("SQLite tables checked/created successfully.");
        }
    }

    @Override
    public void close() throws SQLException {
        executorService.shutdown();
        if (connection != null && !connection.isClosed()) {
            connection.close();
            plugin.getLogger().info("SQLite connection closed.");
        }
    }

    @Override
    public CompletableFuture<PlayerData> loadPlayerData(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData loadedBaseData;
            String sqlPlayerData = "SELECT job_points FROM shjobs_player_data WHERE uuid = ?;";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlPlayerData)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    loadedBaseData = new PlayerData(playerUUID);
                    loadedBaseData.setJobPoints(rs.getLong("job_points"));
                } else {
                    return null; // Le joueur n'existe pas dans la base de données
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading base player data for " + playerUUID, e);
                throw new RuntimeException(e);
            }

            final PlayerData effectivelyFinalBaseData = loadedBaseData;

            String sqlPlayerJobs = "SELECT job_type, experience, level FROM shjobs_player_jobs WHERE player_uuid = ?;";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlPlayerJobs)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String jobTypeStr = rs.getString("job_type");
                    long exp = rs.getLong("experience");
                    int level = rs.getInt("level");
                    JobType.fromString(jobTypeStr).ifPresent(jobType -> {
                        effectivelyFinalBaseData.setRawExperience(jobType, exp);
                        effectivelyFinalBaseData.setRawLevel(jobType, level);
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading job-specific data for " + playerUUID, e);
                throw new RuntimeException(e);
            }

            Map<String, DailyJob> activeDailyJobs = new HashMap<>();
            String sqlDailyJobs = "SELECT job_template_id, daily_job_json FROM shjobs_player_daily_jobs WHERE player_uuid = ?;";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlDailyJobs)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String templateId = rs.getString("job_template_id");
                    String dailyJobJson = rs.getString("daily_job_json");
                    JobTemplate template = plugin.getJobManager().getJobTemplate(templateId).orElse(null);
                    if (template == null) {
                        plugin.getLogger().warning("Could not find JobTemplate with ID '" + templateId + "' for player " + playerUUID + " while loading daily jobs. Skipping.");
                        continue;
                    }
                    try {
                        TempDailyJobStorage tempStorage = gson.fromJson(dailyJobJson, TempDailyJobStorage.class);
                        if (tempStorage != null) {
                            DailyJob dj = new DailyJob(template, tempStorage.objectiveProgress, tempStorage.completed);
                            activeDailyJobs.put(template.getId(), dj);
                        } else {
                            plugin.getLogger().warning("Failed to deserialize daily job JSON for player " + playerUUID + ", template " + templateId + ". JSON: " + dailyJobJson);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Error deserializing DailyJob JSON for player " + playerUUID + ", template " + templateId, e);
                    }
                }
                effectivelyFinalBaseData.setActiveDailyJobs(activeDailyJobs);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading active daily jobs for " + playerUUID, e);
                throw new RuntimeException(e);
            }
            return effectivelyFinalBaseData;
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> savePlayerData(PlayerData playerData) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = playerData.getPlayerUUID();
            String sqlPlayerData = "INSERT OR REPLACE INTO shjobs_player_data (uuid, job_points) VALUES (?, ?);";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlPlayerData)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setLong(2, playerData.getJobPoints());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error saving base player data for " + uuid, e);
                throw new RuntimeException(e);
            }

            String sqlPlayerJobs = "INSERT OR REPLACE INTO shjobs_player_jobs (player_uuid, job_type, experience, level) VALUES (?, ?, ?, ?);";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlPlayerJobs)) {
                for (JobType jobType : JobType.values()) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, jobType.name());
                    pstmt.setLong(3, playerData.getExperience(jobType));
                    pstmt.setInt(4, playerData.getLevel(jobType));
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error saving job-specific data for " + uuid, e);
                throw new RuntimeException(e);
            }

            String sqlDeleteOldDailyJobs = "DELETE FROM shjobs_player_daily_jobs WHERE player_uuid = ?;";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlDeleteOldDailyJobs)) {
                pstmt.setString(1, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error clearing old daily jobs for " + uuid, e);
            }

            String sqlDailyJobs = "INSERT INTO shjobs_player_daily_jobs (player_uuid, job_template_id, daily_job_json) VALUES (?, ?, ?);";
            if (!playerData.getActiveDailyJobs().isEmpty()) {
                try (PreparedStatement pstmt = connection.prepareStatement(sqlDailyJobs)) {
                    for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
                        if (dailyJob.getTemplate() == null) {
                            plugin.getLogger().warning("Skipping save of a daily job with null template for player " + uuid);
                            continue;
                        }
                        pstmt.setString(1, uuid.toString());
                        pstmt.setString(2, dailyJob.getTemplate().getId());
                        TempDailyJobStorage tempStorage = new TempDailyJobStorage();
                        tempStorage.objectiveProgress = dailyJob.getObjectiveProgress();
                        tempStorage.completed = dailyJob.isCompleted();
                        String dailyJobJson = gson.toJson(tempStorage);
                        pstmt.setString(3, dailyJobJson);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error saving active daily jobs for " + uuid, e);
                    throw new RuntimeException(e);
                }
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> deletePlayerData(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            String sqlDelete = "DELETE FROM shjobs_player_data WHERE uuid = ?;";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlDelete)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.executeUpdate();
                plugin.getLogger().info("Deleted data for player " + playerUUID);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error deleting player data for " + playerUUID, e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getTopPlayersForJob(JobType jobType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            String sql = "SELECT player_uuid, level, experience FROM shjobs_player_jobs " +
                "WHERE job_type = ? ORDER BY level DESC, experience DESC LIMIT ?;";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, jobType.name());
                pstmt.setInt(2, limit);
                ResultSet rs = pstmt.executeQuery();
                int rank = 1;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    int level = rs.getInt("level");
                    long experience = rs.getLong("experience");
                    entries.add(new LeaderboardEntry(uuid, level, experience, rank++));
                }
            } catch (SQLException | IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Error fetching top players for job " + jobType.name(), e);
            }
            return entries;
        }, executorService);
    }

    @Override
    public CompletableFuture<Integer> getPlayerRankForJob(JobType jobType, int playerLevel, long playerExperience) {
        return CompletableFuture.supplyAsync(() -> {
            String rankSql = "SELECT COUNT(*) + 1 AS rank FROM shjobs_player_jobs WHERE job_type = ? AND (level > ? OR (level = ? AND experience > ?));";
            try (PreparedStatement pstmt = connection.prepareStatement(rankSql)) {
                pstmt.setString(1, jobType.name());
                pstmt.setInt(2, playerLevel);
                pstmt.setInt(3, playerLevel);
                pstmt.setLong(4, playerExperience);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("rank");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error calculating player rank", e);
            }
            return 0; // 0 = non classé
        }, executorService);
    }
}
