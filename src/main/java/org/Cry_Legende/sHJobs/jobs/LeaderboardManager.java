package org.Cry_Legende.sHJobs.jobs;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class LeaderboardManager {

    private final SHJobs plugin;
    private final Map<JobType, List<LeaderboardEntry>> leaderboardCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<JobType, Integer>> playerRankCache = new ConcurrentHashMap<>();
    private BukkitTask updateTask;

    public LeaderboardManager(SHJobs plugin) {
        this.plugin = plugin;
    }

    /**
     * Démarre la tâche asynchrone qui met à jour périodiquement les classements.
     */
    public void startUpdateTask() {
        // Annule la tâche précédente si elle existe pour éviter les doublons lors d'un reload
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
        // Met à jour les classements toutes les 5 minutes (6000 ticks) après un démarrage initial
        long updateInterval = 20L * 60 * 5; // 5 minutes en ticks
        this.updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateAllLeaderboards, 20L, updateInterval);
    }

    /**
     * Arrête la tâche de mise à jour des classements.
     */
    public void stopUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
    }

    /**
     * Met à jour tous les classements en requêtant la base de données.
     * Cette méthode est conçue pour être exécutée de manière asynchrone.
     */
    public void updateAllLeaderboards() {
        plugin.getLogger().info("Updating all job leaderboards...");
        for (JobType jobType : JobType.values()) {
            try {
                // Met à jour le Top 10 pour chaque métier
                plugin.getStorageManager().getTopPlayersForJob(jobType, 10).thenAccept(entries -> {
                    leaderboardCache.put(jobType, entries);

                    // Met à jour le cache de classement pour les joueurs présents dans le top
                    for (LeaderboardEntry entry : entries) {
                        playerRankCache.computeIfAbsent(entry.getPlayerUUID(), k -> new ConcurrentHashMap<>()).put(jobType, entry.getRank());
                    }
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to update leaderboard for " + jobType.name(), ex);
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "An unhandled exception occurred during leaderboard update for " + jobType.name(), e);
            }
        }
        plugin.getLogger().info("Leaderboard update process finished.");
    }

    /**
     * Récupère la liste des meilleurs joueurs pour un métier depuis le cache.
     *
     * @param jobType Le métier concerné.
     * @return Une liste (potentiellement vide) d'objets LeaderboardEntry.
     */
    public List<LeaderboardEntry> getLeaderboard(JobType jobType) {
        return leaderboardCache.getOrDefault(jobType, Collections.emptyList());
    }

    /**
     * Récupère le rang d'un joueur pour un métier depuis le cache.
     *
     * @param playerUUID L'UUID du joueur.
     * @param jobType    Le métier concerné.
     * @return Le rang du joueur, 0 si non classé, ou -1 si en cours de calcul.
     */
    public int getPlayerRank(UUID playerUUID, JobType jobType) {
        return playerRankCache.getOrDefault(playerUUID, Collections.emptyMap()).getOrDefault(jobType, 0);
    }

    /**
     * Oublie les rangs d'un joueur déconnecté (le classement du top 10 est conservé à part).
     */
    public void forgetPlayer(UUID playerUUID) {
        playerRankCache.remove(playerUUID);
    }

    /**
     * Met à jour le rang d'un joueur spécifique pour un métier donné.
     * Utilise les données actuelles du joueur (niveau/xp) pour assurer l'exactitude.
     * Met le rang à -1 temporairement pour signifier "calcul en cours".
     *
     * @param playerUUID L'UUID du joueur à mettre à jour.
     * @param jobType    Le métier concerné.
     */
    public void updatePlayerRank(UUID playerUUID, JobType jobType) {
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(playerUUID);
        if (playerData == null) {
            return; // Données non chargées, impossible de calculer.
        }

        int level = playerData.getLevel(jobType);
        long experience = playerData.getExperience(jobType);

        // Met le rang à -1 dans le cache pour signifier "en cours de calcul".
        playerRankCache.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>()).put(jobType, -1);

        // La requête asynchrone va chercher le vrai rang.
        plugin.getStorageManager().getPlayerRankForJob(jobType, level, experience).thenAccept(rank -> {
            // Met à jour le cache avec le vrai rang une fois le calcul terminé.
            playerRankCache.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>()).put(jobType, rank);
        }).exceptionally(ex -> {
            // En cas d'erreur, on remet à 0 (non classé).
            playerRankCache.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>()).put(jobType, 0);
            plugin.getLogger().log(Level.WARNING, "Failed to update rank for player " + playerUUID + " in job " + jobType.name(), ex);
            return null;
        });
    }
}