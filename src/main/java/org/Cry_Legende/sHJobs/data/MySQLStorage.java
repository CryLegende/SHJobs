package org.Cry_Legende.sHJobs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.config.ConfigManager;
import org.Cry_Legende.sHJobs.jobs.DailyJob;
import org.Cry_Legende.sHJobs.jobs.JobTemplate;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.jobs.LeaderboardEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class MySQLStorage implements StorageManager {

    private final SHJobs plugin;
    private HikariDataSource dataSource;
    private final ExecutorService executorService;
    private final Gson gson;

    public MySQLStorage(SHJobs plugin) {
        this.plugin = plugin;
        // Un pool de threads pour gérer les requêtes asynchrones vers MySQL
        int poolSize = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() / 2, 4));
        this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "SHJobs-MySQL-Worker");
            t.setDaemon(true);
            return t;
        });
        this.gson = new GsonBuilder().create();
    }

    @Override
    public void init() throws SQLException {
        ConfigManager cfg = plugin.getConfigManager();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + cfg.getMysqlHost() + ":" + cfg.getMysqlPort() + "/" + cfg.getMysqlDatabase() + "?useSSL=" + cfg.mysqlUseSSL());
        hikariConfig.setUsername(cfg.getMysqlUsername());
        hikariConfig.setPassword(cfg.getMysqlPassword());
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
        hikariConfig.addDataSourceProperty("connectionTimeout", 30000);
        hikariConfig.addDataSourceProperty("maxLifetime", 1800000); // 30 minutes
        hikariConfig.addDataSourceProperty("validationTimeout", 5000);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);

        try {
            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("MySQL HikariCP connection pool established.");
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL HikariCP data source!", e);
            if (e instanceof SQLException) throw (SQLException) e;
            throw new SQLException("Failed to initialize MySQL HikariCP data source!", e);
        }
    }

    private void createTables() throws SQLException {
        String createPlayerDataSQL = "CREATE TABLE IF NOT EXISTS `shjobs_player_data` ("
            + "`uuid` VARCHAR(36) NOT NULL,"
            + "`job_points` BIGINT NOT NULL DEFAULT 0,"
            + "PRIMARY KEY (`uuid`)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        String createPlayerJobsSQL = "CREATE TABLE IF NOT EXISTS `shjobs_player_jobs` ("
            + "`player_uuid` VARCHAR(36) NOT NULL,"
            + "`job_type` VARCHAR(32) NOT NULL,"
            + "`experience` BIGINT NOT NULL DEFAULT 0,"
            + "`level` INT NOT NULL DEFAULT 1,"
            + "PRIMARY KEY (`player_uuid`, `job_type`),"
            + "CONSTRAINT `fk_player_jobs_uuid` FOREIGN KEY (`player_uuid`) REFERENCES `shjobs_player_data`(`uuid`) ON DELETE CASCADE"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        String createPlayerDailyJobsSQL = "CREATE TABLE IF NOT EXISTS `shjobs_player_daily_jobs` ("
            + "`player_uuid` VARCHAR(36) NOT NULL,"
            + "`job_template_id` VARCHAR(255) NOT NULL,"
            + "`daily_job_json` TEXT NOT NULL,"
            + "PRIMARY KEY (`player_uuid`, `job_template_id`),"
            + "CONSTRAINT `fk_player_daily_jobs_uuid` FOREIGN KEY (`player_uuid`) REFERENCES `shjobs_player_data`(`uuid`) ON DELETE CASCADE"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayerDataSQL);
            stmt.execute(createPlayerJobsSQL);
            stmt.execute(createPlayerDailyJobsSQL);
            plugin.getLogger().info("MySQL tables checked/created successfully.");
        }
    }

    @Override
    public void close() {
        executorService.shutdown();
        try {
            // laisse finir les sauvegardes en cours avant de fermer le pool
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL HikariCP connection pool closed.");
        }
    }

    @Override
    public CompletableFuture<PlayerData> loadPlayerData(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData loadedBaseData;
            String sqlPlayerData = "SELECT `job_points` FROM `shjobs_player_data` WHERE `uuid` = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sqlPlayerData)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    loadedBaseData = new PlayerData(playerUUID);
                    loadedBaseData.setJobPoints(rs.getLong("job_points"));
                } else {
                    return null;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading base player data for " + playerUUID, e);
                throw new RuntimeException(e);
            }

            final PlayerData effectivelyFinalBaseData = loadedBaseData;

            String sqlPlayerJobs = "SELECT `job_type`, `experience`, `level` FROM `shjobs_player_jobs` WHERE `player_uuid` = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sqlPlayerJobs)) {
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
            String sqlDailyJobs = "SELECT `job_template_id`, `daily_job_json` FROM `shjobs_player_daily_jobs` WHERE `player_uuid` = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sqlDailyJobs)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String templateId = rs.getString("job_template_id");
                    String dailyJobJson = rs.getString("daily_job_json");
                    JobTemplate template = plugin.getJobManager().getJobTemplate(templateId).orElse(null);
                    if (template == null) {
                        plugin.getLogger().warning("Could not find JobTemplate with ID '" + templateId + "' for player " + playerUUID + " (MySQL). Skipping.");
                        continue;
                    }
                    try {
                        SQLiteStorage.TempDailyJobStorage tempStorage = gson.fromJson(dailyJobJson, SQLiteStorage.TempDailyJobStorage.class);
                        if (tempStorage != null) {
                            DailyJob dj = new DailyJob(template, tempStorage.objectiveProgress, tempStorage.completed);
                            activeDailyJobs.put(template.getId(), dj);
                        } else {
                            plugin.getLogger().warning("Failed to deserialize daily job JSON for player " + playerUUID + ", template " + templateId + " (MySQL). JSON: " + dailyJobJson);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Error deserializing DailyJob JSON for player " + playerUUID + ", template " + templateId + " (MySQL)", e);
                    }
                }
                effectivelyFinalBaseData.setActiveDailyJobs(activeDailyJobs);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading active daily jobs for " + playerUUID + " (MySQL)", e);
                throw new RuntimeException(e);
            }
            return effectivelyFinalBaseData;
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> savePlayerData(PlayerData playerData) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = playerData.getPlayerUUID();
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String sqlPlayerData = "INSERT INTO `shjobs_player_data` (`uuid`, `job_points`) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE `job_points` = VALUES(`job_points`);";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlPlayerData)) {
                        pstmt.setString(1, uuid.toString());
                        pstmt.setLong(2, playerData.getJobPoints());
                        pstmt.executeUpdate();
                    }

                    String sqlPlayerJobs = "INSERT INTO `shjobs_player_jobs` (`player_uuid`, `job_type`, `experience`, `level`) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE `experience` = VALUES(`experience`), `level` = VALUES(`level`);";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlPlayerJobs)) {
                        for (JobType jobType : JobType.values()) {
                            pstmt.setString(1, uuid.toString());
                            pstmt.setString(2, jobType.name());
                            pstmt.setLong(3, playerData.getExperience(jobType));
                            pstmt.setInt(4, playerData.getLevel(jobType));
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }

                    String sqlDeleteOldDailyJobs = "DELETE FROM `shjobs_player_daily_jobs` WHERE `player_uuid` = ?;";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlDeleteOldDailyJobs)) {
                        pstmt.setString(1, uuid.toString());
                        pstmt.executeUpdate();
                    }

                    String sqlDailyJobs = "INSERT INTO `shjobs_player_daily_jobs` (`player_uuid`, `job_template_id`, `daily_job_json`) VALUES (?, ?, ?);";
                    if (!playerData.getActiveDailyJobs().isEmpty()) {
                        try (PreparedStatement pstmt = conn.prepareStatement(sqlDailyJobs)) {
                            for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
                                if (dailyJob.getTemplate() == null) continue;
                                pstmt.setString(1, uuid.toString());
                                pstmt.setString(2, dailyJob.getTemplate().getId());
                                SQLiteStorage.TempDailyJobStorage tempStorage = new SQLiteStorage.TempDailyJobStorage();
                                tempStorage.objectiveProgress = dailyJob.getObjectiveProgress();
                                tempStorage.completed = dailyJob.isCompleted();
                                String dailyJobJson = gson.toJson(tempStorage);
                                pstmt.setString(3, dailyJobJson);
                                pstmt.addBatch();
                            }
                            pstmt.executeBatch();
                        }
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    plugin.getLogger().log(Level.SEVERE, "Error saving player data transaction for " + uuid + " (MySQL). Rolled back.", e);
                    throw new RuntimeException(e);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error obtaining connection or during transaction management for " + uuid + " (MySQL)", e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> deletePlayerData(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            String sqlDelete = "DELETE FROM `shjobs_player_data` WHERE `uuid` = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.executeUpdate();
                plugin.getLogger().info("Deleted data for player " + playerUUID + " (MySQL)");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error deleting player data for " + playerUUID + " (MySQL)", e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getTopPlayersForJob(JobType jobType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            String sql = "SELECT `player_uuid`, `level`, `experience` FROM `shjobs_player_jobs` " +
                "WHERE `job_type` = ? ORDER BY `level` DESC, `experience` DESC LIMIT ?;";
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
                plugin.getLogger().log(Level.SEVERE, "Error fetching top MySQL players for job " + jobType.name(), e);
            }
            return entries;
        }, executorService);
    }

    @Override
    public CompletableFuture<Integer> getPlayerRankForJob(JobType jobType, int playerLevel, long playerExperience) {
        return CompletableFuture.supplyAsync(() -> {
            String rankSql = "SELECT COUNT(*) + 1 AS `player_rank` FROM `shjobs_player_jobs` WHERE `job_type` = ? AND (`level` > ? OR (`level` = ? AND `experience` > ?));";
            try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(rankSql)) {
                pstmt.setString(1, jobType.name());
                pstmt.setInt(2, playerLevel);
                pstmt.setInt(3, playerLevel);
                pstmt.setLong(4, playerExperience);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("player_rank");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error calculating MySQL player rank", e);
            }
            return 0; // 0 = non classé
        }, executorService);
    }
}
