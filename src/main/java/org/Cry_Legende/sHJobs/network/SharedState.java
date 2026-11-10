package org.Cry_Legende.sHJobs.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rotation du jour partagée : une seule ligne (jobs et articles de la boutique du jour, date de la rotation) et un
 * numéro de version. Une rotation se prend par un {@code UPDATE ... WHERE version = ?} : le serveur qui touche la
 * ligne est le seul à l'avoir faite, les autres relisent la ligne.
 *
 * <p>Toutes les méthodes bloquent : à appeler hors du fil principal, sauf au démarrage.</p>
 */
public final class SharedState {

    /** Rotation en base. Les listes ont 6 éléments, un emplacement sans modèle est {@code null}. */
    public record Row(LocalDate day, List<String> jobIds, List<String> shopIds, long version) {

        /** Vrai si les deux listes ont bien leurs 6 emplacements. */
        public boolean isComplete() {
            return jobIds.size() == 6 && shopIds.size() == 6;
        }
    }

    private static final Gson GSON = new Gson();
    private static final Type LIST = new TypeToken<List<String>>() {
    }.getType();

    private final SharedDb db;

    public SharedState(SharedDb db) {
        this.db = db;
    }

    public static String toJson(List<String> ids) {
        return GSON.toJson(ids);
    }

    public static List<String> fromJson(String json) {
        List<String> ids = GSON.fromJson(json, LIST);
        return ids == null ? new ArrayList<>() : ids;
    }

    /** Rotation en base, ou null si personne n'en a encore écrit. */
    public Row read() throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT reset_day, job_ids, shop_ids, version FROM shjobs_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new Row(LocalDate.parse(rs.getString(1)), fromJson(rs.getString(2)), fromJson(rs.getString(3)), rs.getLong(4));
        }
    }

    /** Écrit la toute première rotation. Vrai si cette instance l'a écrite, faux si une ligne existait déjà. */
    public boolean insert(LocalDate day, List<String> jobIds, List<String> shopIds) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO shjobs_state (id, reset_day, job_ids, shop_ids, version) VALUES (1, ?, ?, ?, 1)")) {
            ps.setString(1, day.toString());
            ps.setString(2, toJson(jobIds));
            ps.setString(3, toJson(shopIds));
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Prend la rotation : n'écrit que si la ligne est toujours à la version lue. Vrai si cette instance l'a prise
     * (la nouvelle version est alors {@code version + 1}), faux si un autre serveur l'a prise avant.
     */
    public boolean claim(long version, LocalDate day, List<String> jobIds, List<String> shopIds) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE shjobs_state SET reset_day = ?, job_ids = ?, shop_ids = ?, version = version + 1 WHERE id = 1 AND version = ?")) {
            ps.setString(1, day.toString());
            ps.setString(2, toJson(jobIds));
            ps.setString(3, toJson(shopIds));
            ps.setLong(4, version);
            return ps.executeUpdate() == 1;
        }
    }

    /** Écrit la rotation sans condition (changement d'un emplacement par un admin). Rend la nouvelle version. */
    public long write(LocalDate day, List<String> jobIds, List<String> shopIds) throws SQLException {
        try (Connection c = db.connection()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement("UPDATE shjobs_state SET reset_day = ?, job_ids = ?, shop_ids = ?, version = LAST_INSERT_ID(version + 1) WHERE id = 1")) {
                ps.setString(1, day.toString());
                ps.setString(2, toJson(jobIds));
                ps.setString(3, toJson(shopIds));
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                insert(day, jobIds, shopIds);
                return 1L;
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
                return rs.next() ? rs.getLong(1) : 1L;
            }
        }
    }
}
