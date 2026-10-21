package org.Cry_Legende.sHJobs.jobs;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class LeaderboardEntry {
    private final UUID playerUUID;
    private final String playerName;
    private final int level;
    private final long totalExperience;
    private final int rank;

    public LeaderboardEntry(UUID playerUUID, int level, long totalExperience, int rank) {
        this.playerUUID = playerUUID;
        this.level = level;
        this.totalExperience = totalExperience;
        this.rank = rank;

        // C'est mieux de récupérer l'OfflinePlayer une seule fois
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        this.playerName = player.getName();
    }

    /**
     * Retourne l'UUID du joueur de cette entrée de classement.
     *
     * @return L'UUID du joueur.
     */
    public UUID getPlayerUUID() { // <-- MÉTHODE AJOUTÉE
        return playerUUID;
    }

    public String getPlayerName() {
        // Retourne "Inconnu" si le nom est null (peut arriver pour des profils corrompus)
        return playerName != null ? playerName : "Inconnu";
    }

    public int getLevel() {
        return level;
    }

    public long getTotalExperience() {
        return totalExperience;
    }

    public int getRank() {
        return rank;
    }
}
