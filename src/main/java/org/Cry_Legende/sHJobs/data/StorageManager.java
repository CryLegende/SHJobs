package org.Cry_Legende.sHJobs.data;

import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.jobs.LeaderboardEntry;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StorageManager {

    /**
     * Initialise la connexion à la base de données (pool de connexions pour MySQL, fichier pour SQLite)
     * et crée les tables si elles n'existent pas.
     *
     * @throws SQLException si une erreur SQL survient lors de l'initialisation.
     */
    void init() throws SQLException;

    /**
     * Ferme proprement la connexion à la base de données et les ressources associées.
     *
     * @throws SQLException si une erreur SQL survient lors de la fermeture.
     */
    void close() throws SQLException;

    /**
     * Charge les données d'un joueur de manière asynchrone depuis la base de données.
     *
     * @param playerUUID L'UUID du joueur à charger.
     * @return Un CompletableFuture contenant le PlayerData, ou null s'il n'existe pas.
     */
    CompletableFuture<PlayerData> loadPlayerData(UUID playerUUID);

    /**
     * Sauvegarde les données d'un joueur de manière asynchrone dans la base de données.
     *
     * @param playerData Les données du joueur à sauvegarder.
     * @return Un CompletableFuture qui se termine lorsque la sauvegarde est effectuée.
     */
    CompletableFuture<Void> savePlayerData(PlayerData playerData);

    /**
     * Supprime les données d'un joueur de manière asynchrone de la base de données.
     *
     * @param playerUUID L'UUID du joueur à supprimer.
     * @return Un CompletableFuture qui se termine lorsque la suppression est effectuée.
     */
    CompletableFuture<Void> deletePlayerData(UUID playerUUID);

    /**
     * Récupère de manière asynchrone une liste des meilleurs joueurs pour un métier donné,
     * classés par niveau puis par expérience.
     *
     * @param jobType Le métier pour lequel récupérer le classement.
     * @param limit   Le nombre maximum de joueurs à retourner (ex: 10 pour un Top 10).
     * @return Un CompletableFuture contenant une liste d'objets LeaderboardEntry.
     */
    CompletableFuture<List<LeaderboardEntry>> getTopPlayersForJob(JobType jobType, int limit);

    /**
     * Récupère de manière asynchrone le rang d'un joueur pour un métier donné,
     * en se basant sur son niveau et son expérience actuels.
     *
     * @param jobType          Le métier concerné.
     * @param playerLevel      Le niveau actuel du joueur dans ce métier.
     * @param playerExperience L'expérience totale actuelle du joueur dans ce métier.
     * @return Un CompletableFuture contenant le rang du joueur (1 pour le premier), ou 0 si non classé.
     */
    CompletableFuture<Integer> getPlayerRankForJob(JobType jobType, int playerLevel, long playerExperience);
}