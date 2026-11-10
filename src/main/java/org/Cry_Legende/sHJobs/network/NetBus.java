package org.Cry_Legende.sHJobs.network;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ce dont le mode réseau a besoin de SHNetwork. Seule {@link ShNetworkBus} connaît ses classes : sans lui, aucune
 * classe de ce paquet n'est chargée.
 */
public interface NetBus {

    /** Candidature à une tâche de meneur. */
    interface Leader {

        boolean isLeader();

        void release();
    }

    /** Nom de ce serveur sur le réseau. */
    String serverName();

    /** Publie sur un canal, pour les autres serveurs. Ne bloque jamais. */
    void publish(String channel, JsonObject message);

    /**
     * Écoute un canal.
     *
     * @param mainThread vrai pour recevoir sur le fil principal
     */
    void listen(String channel, Consumer<JsonObject> handler, boolean mainThread);

    /** Attend que le serveur de départ ait sauvegardé le joueur. Faux au délai dépassé ou si la sauvegarde est incomplète. */
    CompletableFuture<Boolean> awaitSaved(UUID uuid, Duration timeout);

    /** Sauvegarde lancée sur le fil principal au départ de chaque joueur, avant qu'il arrive sur l'autre serveur. */
    void onDeparture(String plugin, Function<Player, CompletableFuture<Void>> save);

    /** Serveur où le joueur est connecté, vide s'il n'est connecté nulle part ou sur celui-ci. */
    Optional<String> otherServerOf(String name);

    /** Exécute une commande console là où se trouve le joueur, ou à sa prochaine connexion s'il est hors ligne. */
    void runFor(String player, String command);

    /** Se porte candidat pour une tâche qui ne tourne que sur un serveur à la fois. */
    Leader leader(String task, Runnable onAcquire, Runnable onLose);

    /** Exécute sur le fil principal : tout de suite si on y est déjà, sinon au prochain tick. */
    void sync(Runnable task);
}
