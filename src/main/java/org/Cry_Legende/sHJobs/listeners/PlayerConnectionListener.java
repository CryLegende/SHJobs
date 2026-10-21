package org.Cry_Legende.sHJobs.listeners;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final SHJobs plugin;

    public PlayerConnectionListener(SHJobs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // La logique de chargement des données est déléguée au PlayerCache.
        // PlayerCache.handlePlayerJoin s'occupe de l'asynchronicité et de la mise en cache.
        plugin.getPlayerCache().handlePlayerJoin(event);

        // Met à jour le classement du joueur pour tous les métiers en arrière-plan.
        // C'est "fire and forget", la mise à jour se fera de manière asynchrone
        // et sera disponible peu de temps après la connexion du joueur.
        for (JobType jobType : JobType.values()) {
            plugin.getLeaderboardManager().updatePlayerRank(player.getUniqueId(), jobType);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // La logique de sauvegarde et de déchargement est déléguée au PlayerCache.
        // PlayerCache.handlePlayerQuit s'occupe de l'asynchronicité.
        plugin.getPlayerCache().handlePlayerQuit(event);
    }
}