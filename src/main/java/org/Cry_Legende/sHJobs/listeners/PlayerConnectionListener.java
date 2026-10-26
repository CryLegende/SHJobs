package org.Cry_Legende.sHJobs.listeners;

import org.Cry_Legende.sHJobs.SHJobs;
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
        // La logique de chargement des données est déléguée au PlayerCache.
        // PlayerCache.handlePlayerJoin s'occupe de l'asynchronicité et de la mise en cache.
        // Le classement du joueur est mis à jour par PlayerCache une fois ses données chargées.
        plugin.getPlayerCache().handlePlayerJoin(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // La logique de sauvegarde et de déchargement est déléguée au PlayerCache.
        // PlayerCache.handlePlayerQuit s'occupe de l'asynchronicité.
        plugin.getPlayerCache().handlePlayerQuit(event);
    }
}