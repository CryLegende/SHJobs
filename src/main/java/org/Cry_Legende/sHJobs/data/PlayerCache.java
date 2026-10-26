package org.Cry_Legende.sHJobs.data;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.gui.CustomMenuHolder; // Import pour instanceof
import org.Cry_Legende.sHJobs.gui.JobsMenu;
import org.Cry_Legende.sHJobs.gui.ShopMenu;
import org.Cry_Legende.sHJobs.jobs.DailyJob;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

public class PlayerCache {

    private final SHJobs plugin;
    private final Map<UUID, PlayerData> playerDataCache;
    private final Map<UUID, Boolean> fullyLoadedData; // Pour suivre si les données initiales sont chargées
    // Joueurs dont le chargement a échoué : leurs données temporaires ne sont jamais écrites (elles effaceraient les vraies)
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();
    // Les rappels des futures reviennent sur le fil principal
    private final Executor mainThread;

    public PlayerCache(SHJobs plugin) {
        this.plugin = plugin;
        // Accédées aussi depuis les fils de la base de données (rappels d'erreur) : tables concurrentes
        this.playerDataCache = new ConcurrentHashMap<>();
        this.fullyLoadedData = new ConcurrentHashMap<>();
        this.mainThread = runnable -> Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID playerUUID) {
        plugin.getLogger().info("[PlayerCache] Request to load PlayerData for " + playerUUID);
        if (playerDataCache.containsKey(playerUUID) && isPlayerDataFullyLoaded(playerUUID)) {
            plugin.getLogger().info("[PlayerCache] PlayerData for " + playerUUID + " already in cache and marked as fully loaded.");
            return CompletableFuture.completedFuture(playerDataCache.get(playerUUID));
        }
        // Marquer comme non chargé (ou en cours de chargement) si on relance le chargement
        fullyLoadedData.put(playerUUID, false);

        return plugin.getStorageManager().loadPlayerData(playerUUID).thenApplyAsync(data -> {
                boolean isNewData = false;
                if (data == null) {
                    plugin.getLogger().info("[PlayerCache] No existing data for " + playerUUID + ", creating new PlayerData.");
                    data = new PlayerData(playerUUID);
                    isNewData = true;
                }

                if (isNewData) {
                    plugin.getJobManager().assignDailyJobsToPlayer(data);
                } else {
                    boolean jobsOutOfSync = false;
                    if (data.getActiveDailyJobs().size() != plugin.getJobManager().getCurrentDailyJobTemplateIds().size()) {
                        jobsOutOfSync = true;
                    } else {
                        for (String serverJobId : plugin.getJobManager().getCurrentDailyJobTemplateIds()) {
                            if (serverJobId == null) continue;
                            if (data.getActiveDailyJobs().values().stream()
                                .noneMatch(dj -> dj != null && dj.getTemplate() != null && dj.getTemplate().getId().equals(serverJobId))) {
                                jobsOutOfSync = true;
                                break;
                            }
                        }
                        if (!jobsOutOfSync) {
                            for (DailyJob playerDailyJob : data.getActiveDailyJobs().values()) {
                                if (playerDailyJob != null && playerDailyJob.getTemplate() != null &&
                                    !plugin.getJobManager().getCurrentDailyJobTemplateIds().contains(playerDailyJob.getTemplate().getId())) {
                                    jobsOutOfSync = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (jobsOutOfSync) {
                        plugin.getLogger().info("[PlayerCache] Player " + playerUUID + " has jobs out of sync. Re-assigning and clearing purchases.");
                        plugin.getJobManager().assignDailyJobsToPlayer(data);
                        data.clearDailyPurchases();
                    }
                }

                playerDataCache.put(playerUUID, data);
                markPlayerDataAsFullyLoaded(playerUUID); // Marquer comme chargé APRÈS l'avoir mis en cache
                plugin.getLogger().info("[PlayerCache] Player data for " + playerUUID + " processed. isNew: " + isNewData + ". Cache size: " + playerDataCache.size());
                return data;
            }, mainThread) // Pour que la mise en cache soit sur le thread principal
            .exceptionallyAsync(ex -> {
                plugin.getLogger().log(Level.SEVERE, "[PlayerCache] EXCEPTION during loadPlayerData for " + playerUUID, ex);
                fullyLoadedData.put(playerUUID, false); // Marquer comme non chargé en cas d'erreur
                PlayerData existing = playerDataCache.get(playerUUID);
                if (existing != null) {
                    return existing; // Données déjà en mémoire : on les garde plutôt que des données vides
                }
                failedLoads.add(playerUUID); // Données temporaires : jamais sauvegardées
                PlayerData tempData = new PlayerData(playerUUID); // Fournir des données temporaires pour éviter NPE
                plugin.getJobManager().assignDailyJobsToPlayer(tempData); // Attribuer quand même les jobs par défaut
                playerDataCache.put(playerUUID, tempData); // Mettre en cache les données temporaires
                // Ne pas marquer comme "fullyLoaded" si c'est une donnée temporaire suite à une erreur.
                // Ou alors, on le marque quand même pour éviter des boucles de chargement, mais on log l'erreur.
                return tempData; // Retourner des données temporaires pour que le plugin ne crashe pas plus loin
            }, mainThread);
    }

    public void markPlayerDataAsFullyLoaded(UUID uuid) {
        failedLoads.remove(uuid);
        fullyLoadedData.put(uuid, true);
        plugin.getLogger().fine("[PlayerCache] PlayerData for " + uuid + " marked as fully loaded.");
    }

    public boolean isPlayerDataFullyLoaded(UUID uuid) {
        return fullyLoadedData.getOrDefault(uuid, false);
    }


    public CompletableFuture<Void> savePlayerData(UUID playerUUID) {
        PlayerData data = playerDataCache.get(playerUUID);
        if (data == null) {
            plugin.getLogger().warning("[PlayerCache] Attempted to save null player data for " + playerUUID + ". Skipping.");
            return CompletableFuture.completedFuture(null);
        }
        if (failedLoads.contains(playerUUID)) {
            plugin.getLogger().warning("[PlayerCache] Data of " + playerUUID + " could not be loaded from the database: not saved, to keep the stored data intact.");
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getStorageManager().savePlayerData(data)
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "[PlayerCache] Failed to save player data for " + playerUUID, ex);
                return null;
            });
    }

    public PlayerData getPlayerData(UUID playerUUID) {
        return playerDataCache.get(playerUUID);
    }

    public void unloadPlayerData(UUID playerUUID) {
        playerDataCache.remove(playerUUID);
        fullyLoadedData.remove(playerUUID); // Nettoyer le statut de chargement
        failedLoads.remove(playerUUID);
        if (plugin.getLeaderboardManager() != null) {
            plugin.getLeaderboardManager().forgetPlayer(playerUUID); // Les rangs ne servent qu'aux joueurs chargés
        }
        plugin.getLogger().info("[PlayerCache] Unloaded PlayerData for " + playerUUID + ". Cache size: " + playerDataCache.size());
    }

    public void assignNewDailyJobsToAll() {
        plugin.getLogger().info("[PlayerCache] Assigning new daily jobs to all cached players...");
        for (PlayerData data : playerDataCache.values()) {
            plugin.getJobManager().assignDailyJobsToPlayer(data);
            data.clearDailyPurchases();

            Player player = Bukkit.getPlayer(data.getPlayerUUID());
            if (player != null && player.isOnline()) {
                plugin.getMessages().sendPlayerMessage(player, "general.daily_jobs_reset");
                InventoryView openInv = player.getOpenInventory();
                if (openInv.getTopInventory().getHolder() instanceof CustomMenuHolder) {
                    if (openInv.getTopInventory().getHolder() instanceof ShopMenu) {
                        plugin.getMenuManager().openShopMenu(player);
                    } else if (openInv.getTopInventory().getHolder() instanceof JobsMenu) {
                        plugin.getMenuManager().openJobsMenu(player);
                    }
                }
            }
        }
        plugin.getLogger().info("[PlayerCache] Finished assigning new daily jobs and clearing purchases.");
    }

    public void clearAllDailyPurchases() {
        for (PlayerData data : playerDataCache.values()) {
            data.clearDailyPurchases();
        }
        plugin.getLogger().info("[PlayerCache] Cleared daily purchases for all cached players (e.g., new day detected at startup).");
    }

    public void saveAllOnlinePlayersSync() {
        plugin.getLogger().info("[PlayerCache] Saving data for all cached players (sync)...");
        // Créer une copie des clés pour itérer afin d'éviter ConcurrentModificationException
        // si un joueur se déconnecte pendant cette sauvegarde (bien que ce soit moins probable lors d'un disable)
        for (UUID uuid : new HashMap<>(playerDataCache).keySet()) {
            PlayerData data = playerDataCache.get(uuid); // Récupérer la dernière version de la donnée
            if (data != null && !failedLoads.contains(uuid)) {
                try {
                    plugin.getStorageManager().savePlayerData(data).join();
                    plugin.getLogger().fine("[PlayerCache] Sync save successful for " + uuid);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[PlayerCache] EXCEPTION during sync save for " + uuid + " during shutdown.", e);
                }
            }
        }
        plugin.getLogger().info("[PlayerCache] Finished sync saving cached player data.");
    }

    public void handlePlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        plugin.getLogger().info("[PlayerCache] Handling PlayerJoinEvent for " + player.getName() + " (" + playerUUID + ")");

        // Marquer que les données ne sont pas encore (ou plus) complètement chargées
        fullyLoadedData.put(playerUUID, false);

        loadPlayerData(playerUUID).thenAcceptAsync(playerData -> {
                if (!player.isOnline()) { // Revérifier si le joueur est toujours en ligne
                    plugin.getLogger().info("[PlayerCache] Player " + player.getName() + " disconnected during data load on join.");
                    unloadPlayerData(playerUUID); // S'assurer de nettoyer si le joueur part vite
                    return;
                }
                if (playerData == null) { // Devrait être tempData en cas d'erreur de loadPlayerData
                    plugin.getLogger().severe("[PlayerCache] PlayerData is NULL for " + player.getName() + " after load on join. This indicates a critical load failure.");
                    player.sendMessage(plugin.getMessages().getMessage("common.player_data_load_error_critical", true));
                    // On pourrait kicker le joueur ici si les données sont absolument nécessaires
                    return;
                }
                // markPlayerDataAsFullyLoaded est déjà appelé dans loadPlayerData
                // Rangs calculés une fois les données en mémoire (à la connexion elles ne l'étaient pas encore)
                for (JobType jobType : JobType.values()) {
                    plugin.getLeaderboardManager().updatePlayerRank(playerUUID, jobType);
                }
                plugin.getLogger().info("[PlayerCache] Player data processed for " + player.getName() + " on join. isPlayerDataFullyLoaded: " + isPlayerDataFullyLoaded(playerUUID));
            }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
            .exceptionally(ex -> { // Intercepter les exceptions du CompletableFuture lui-même
                plugin.getLogger().log(Level.SEVERE, "[PlayerCache] EXCEPTION in loadPlayerData callback chain for " + player.getName() + " on join", ex);
                if (player.isOnline()) {
                    player.sendMessage(plugin.getMessages().getMessage("common.player_data_load_error_critical", true));
                }
                fullyLoadedData.put(playerUUID, false); // S'assurer que c'est marqué comme non chargé
                return null;
            });
    }

    public void handlePlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        plugin.getLogger().info("[PlayerCache] Handling PlayerQuitEvent for " + player.getName() + " (" + playerUUID + ")");

        savePlayerData(playerUUID) // Tenter de sauvegarder
            .whenCompleteAsync((result, throwable) -> { // S'exécute après savePlayerData, que ce soit un succès ou une erreur
                if (throwable != null) {
                    plugin.getLogger().log(Level.SEVERE, "[PlayerCache] EXCEPTION during save for " + player.getName() + " on quit.", throwable);
                } else {
                    plugin.getLogger().info("[PlayerCache] Player data save completed for " + player.getName() + " on quit.");
                }
                // Décharger dans tous les cas (réussi ou échec de la sauvegarde), sauf si le joueur s'est reconnecté
                // entre-temps : les données en cache sont alors celles de sa nouvelle session
                if (Bukkit.getPlayer(playerUUID) == null) {
                    unloadPlayerData(playerUUID);
                }
            }, mainThread); // Exécuter le whenComplete sur le thread principal
    }
}
