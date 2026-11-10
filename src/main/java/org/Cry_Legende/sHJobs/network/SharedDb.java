package org.Cry_Legende.sHJobs.network;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base MariaDB partagée par tous les serveurs : joueurs, rotation du jour et limites de la boutique. Chargée seulement
 * avec SHNetwork (HikariCP et le pilote MariaDB viennent de {@code libraries:} dans plugin.yml).
 */
public final class SharedDb {

    /** Une requête, exécutée sur un fil de la base. */
    @FunctionalInterface
    public interface SqlTask<T> {
        T run() throws SQLException;
    }

    private final HikariDataSource pool;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "SHJobs-Reseau");
        t.setDaemon(true);
        return t;
    });

    public SharedDb(String url, String user, String password, int timeoutMs) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("SHJobs-Reseau");
        hikari.setJdbcUrl(url);
        hikari.setUsername(user);
        hikari.setPassword(password);
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.addDataSourceProperty("connectTimeout", String.valueOf(timeoutMs));
        hikari.addDataSourceProperty("socketTimeout", String.valueOf(Math.max(timeoutMs, 10_000)));
        hikari.setMaximumPoolSize(8);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(timeoutMs);
        hikari.setKeepaliveTime(60_000);
        this.pool = new HikariDataSource(hikari);
    }

    public DataSource dataSource() {
        return pool;
    }

    public Connection connection() throws SQLException {
        return pool.getConnection();
    }

    /** Lance la requête sur un fil de la base ; une erreur SQL termine le futur en erreur. */
    public <T> CompletableFuture<T> async(SqlTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.run();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Crée les tables de la rotation et des limites si elles manquent. Une table déjà là ne demande aucun droit
     * CREATE à l'utilisateur.
     */
    public void createTables() throws SQLException {
        try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
            ensure(st, "shjobs_state", "CREATE TABLE IF NOT EXISTS shjobs_state ("
                + "id TINYINT NOT NULL, "
                + "reset_day CHAR(10) NOT NULL, "
                + "job_ids TEXT NOT NULL, "
                + "shop_ids TEXT NOT NULL, "
                + "version BIGINT NOT NULL DEFAULT 1, "
                + "PRIMARY KEY (id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            ensure(st, "shjobs_daily_limits", "CREATE TABLE IF NOT EXISTS shjobs_daily_limits ("
                + "reset_day CHAR(10) NOT NULL, "
                + "uuid CHAR(36) NOT NULL, "
                + "item_id VARCHAR(100) NOT NULL, "
                + "bought INT NOT NULL DEFAULT 0, "
                + "sold INT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (reset_day, uuid, item_id), "
                + "KEY idx_uuid (uuid, reset_day)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private static void ensure(Statement st, String table, String ddl) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) {
            return;
        } catch (SQLException missing) {
            // la table n'existe pas encore : création ci-dessous
        }
        st.executeUpdate(ddl);
    }

    public void close() {
        executor.shutdown();
        pool.close();
    }
}
