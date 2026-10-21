package org.Cry_Legende.sHJobs.tasks;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate; // Import LocalDate
import java.util.logging.Level;

public class DailyResetTask extends BukkitRunnable {

    private final SHJobs plugin;

    public DailyResetTask(SHJobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getLogger().info("Performing daily reset for SHJobs...");
        try {
            // 1. Sélectionner de nouveaux jobs journaliers
            // selectNewDailyJobs appelle PlayerCache.assignNewDailyJobsToAll qui clear les purchases
            plugin.getJobManager().selectNewDailyJobs();

            // 2. Sélectionner de nouveaux items pour la boutique des métiers
            plugin.getShopManager().selectNewDailyShopItems();
            plugin.getLogger().info("Daily shop items have been reset.");

            // 3. Mettre à jour la date du dernier reset et sauvegarder l'état du serveur
            plugin.setLastResetDate(LocalDate.now()); // Important pour la persistance du "jour"
            plugin.saveServerState();

            plugin.getLogger().info("Daily reset completed successfully.");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An error occurred during the daily reset task!", e);
        }
    }
}