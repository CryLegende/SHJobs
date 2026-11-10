package org.Cry_Legende.sHJobs.network;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Limites quotidiennes de la boutique en base : un compteur d'achats et de reventes par jour, joueur et article.
 * Les ajouts sont des incréments atomiques, jamais un compteur lu plus tôt puis réécrit.
 *
 * <p>Toutes les méthodes bloquent : à appeler hors du fil principal.</p>
 */
public final class SharedLimits {

    private final SharedDb db;

    public SharedLimits(SharedDb db) {
        this.db = db;
    }

    /** Compteurs du joueur pour ce jour : article {@code ->} {achats, reventes}. */
    public Map<String, int[]> load(String day, UUID player) throws SQLException {
        Map<String, int[]> perItem = new HashMap<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT item_id, bought, sold FROM shjobs_daily_limits WHERE reset_day = ? AND uuid = ?")) {
            ps.setString(1, day);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    perItem.put(rs.getString(1), new int[]{rs.getInt(2), rs.getInt(3)});
                }
            }
        }
        return perItem;
    }

    /** Ajoute {@code amount} achats ({@code sold} faux) ou reventes ({@code sold} vrai) à l'article du joueur. */
    public void add(String day, UUID player, String itemId, boolean sold, int amount) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO shjobs_daily_limits (reset_day, uuid, item_id, bought, sold) VALUES (?, ?, ?, ?, ?) "
                 + "ON DUPLICATE KEY UPDATE bought = bought + VALUES(bought), sold = sold + VALUES(sold)")) {
            ps.setString(1, day);
            ps.setString(2, player.toString());
            ps.setString(3, itemId);
            ps.setInt(4, sold ? 0 : amount);
            ps.setInt(5, sold ? amount : 0);
            ps.executeUpdate();
        }
    }

    /** Supprime les compteurs des jours passés. */
    public int purgeBefore(String day) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM shjobs_daily_limits WHERE reset_day < ?")) {
            ps.setString(1, day);
            return ps.executeUpdate();
        }
    }
}
