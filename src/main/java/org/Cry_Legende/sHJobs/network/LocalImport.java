package org.Cry_Legende.sHJobs.network;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reprend dans la base partagée les données d'un serveur qui tournait seul : joueurs (base SQLite de
 * {@code database/shjobs_data.db}) et limites du jour ({@code daily_limits.yml}). Un joueur déjà présent dans la base
 * partagée n'est pas touché, donc la reprise peut être relancée sans rien compter deux fois.
 *
 * <p>Bloque : à lancer hors du fil principal.</p>
 */
final class LocalImport {

    /** @param players joueurs repris ; @param alreadyThere joueurs déjà en base ; @param limits compteurs de limites repris */
    record Result(int players, int alreadyThere, int limits) {
    }

    private final SHJobs plugin;
    private final SharedDb db;

    LocalImport(SHJobs plugin, SharedDb db) {
        this.plugin = plugin;
        this.db = db;
    }

    Result run() throws Exception {
        File sqlite = new File(plugin.getDataFolder(), "database/shjobs_data.db");
        if (!sqlite.isFile()) {
            throw new FileNotFoundException("database/shjobs_data.db introuvable");
        }
        Class.forName("org.sqlite.JDBC");

        int imported = 0;
        int present = 0;
        Set<String> fresh = new HashSet<>();
        try (Connection lite = DriverManager.getConnection("jdbc:sqlite:" + sqlite.getAbsolutePath());
             Connection shared = db.connection()) {
            shared.setAutoCommit(false);

            try (Statement read = lite.createStatement();
                 ResultSet rs = read.executeQuery("SELECT uuid, job_points FROM shjobs_player_data");
                 PreparedStatement insert = shared.prepareStatement("INSERT IGNORE INTO shjobs_player_data (uuid, job_points) VALUES (?, ?)")) {
                while (rs.next()) {
                    String uuid = rs.getString(1);
                    insert.setString(1, uuid);
                    insert.setLong(2, rs.getLong(2));
                    if (insert.executeUpdate() == 1) {
                        fresh.add(uuid);
                        imported++;
                    } else {
                        present++;
                    }
                }
            }

            try (Statement read = lite.createStatement();
                 ResultSet rs = read.executeQuery("SELECT player_uuid, job_type, experience, level FROM shjobs_player_jobs");
                 PreparedStatement insert = shared.prepareStatement("INSERT IGNORE INTO shjobs_player_jobs (player_uuid, job_type, experience, level) VALUES (?, ?, ?, ?)")) {
                while (rs.next()) {
                    if (!fresh.contains(rs.getString(1))) {
                        continue;
                    }
                    insert.setString(1, rs.getString(1));
                    insert.setString(2, rs.getString(2));
                    insert.setLong(3, rs.getLong(3));
                    insert.setInt(4, rs.getInt(4));
                    insert.addBatch();
                }
                insert.executeBatch();
            }

            try (Statement read = lite.createStatement();
                 ResultSet rs = read.executeQuery("SELECT player_uuid, job_template_id, daily_job_json FROM shjobs_player_daily_jobs");
                 PreparedStatement insert = shared.prepareStatement("INSERT IGNORE INTO shjobs_player_daily_jobs (player_uuid, job_template_id, daily_job_json) VALUES (?, ?, ?)")) {
                while (rs.next()) {
                    if (!fresh.contains(rs.getString(1))) {
                        continue;
                    }
                    insert.setString(1, rs.getString(1));
                    insert.setString(2, rs.getString(2));
                    insert.setString(3, rs.getString(3));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            shared.commit();
        }
        return new Result(imported, present, importLimits());
    }

    /** Limites du jour de daily_limits.yml : au plus grand de ce qui est déjà en base et de ce qui est dans le fichier. */
    private int importLimits() throws SQLException {
        File file = new File(plugin.getDataFolder(), "daily_limits.yml");
        if (!file.isFile()) {
            return 0;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String day = yaml.getString("day");
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (day == null || players == null || !day.equals(plugin.getLastResetDate().toString())) {
            return 0;
        }
        int rows = 0;
        try (Connection shared = db.connection();
             PreparedStatement insert = shared.prepareStatement("INSERT INTO shjobs_daily_limits (reset_day, uuid, item_id, bought, sold) VALUES (?, ?, ?, ?, ?) "
                 + "ON DUPLICATE KEY UPDATE bought = GREATEST(bought, VALUES(bought)), sold = GREATEST(sold, VALUES(sold))")) {
            for (String uuidKey : players.getKeys(false)) {
                ConfigurationSection items = players.getConfigurationSection(uuidKey);
                if (items == null) {
                    continue;
                }
                try {
                    UUID.fromString(uuidKey);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                for (String itemId : items.getKeys(false)) {
                    insert.setString(1, day);
                    insert.setString(2, uuidKey);
                    insert.setString(3, itemId);
                    insert.setInt(4, items.getInt(itemId + ".bought"));
                    insert.setInt(5, items.getInt(itemId + ".sold"));
                    insert.addBatch();
                    rows++;
                }
            }
            insert.executeBatch();
        }
        return rows;
    }
}
