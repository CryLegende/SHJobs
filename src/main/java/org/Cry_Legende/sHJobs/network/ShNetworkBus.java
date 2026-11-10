package org.Cry_Legende.sHJobs.network;

import com.google.gson.JsonObject;
import org.Cry_Legende.sHNetwork.api.LeaderHandle;
import org.Cry_Legende.sHNetwork.api.NetPlayer;
import org.Cry_Legende.sHNetwork.api.SHNetworkAPI;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Pont vers SHNetwork. Cette classe n'est chargée que si SHNetwork est installé. */
public final class ShNetworkBus implements NetBus {

    private final SHNetworkAPI api;

    private ShNetworkBus() {
        this.api = SHNetworkAPI.get();
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHNetwork. */
    public static NetBus open() {
        return new ShNetworkBus();
    }

    @Override
    public String serverName() {
        return api.serverName();
    }

    @Override
    public void publish(String channel, JsonObject message) {
        api.publish(channel, message.toString());
    }

    @Override
    public void listen(String channel, Consumer<JsonObject> handler, boolean mainThread) {
        api.subscribe(channel, message -> {
            JsonObject json;
            try {
                json = message.object();
            } catch (RuntimeException e) {
                return; // message illisible : le bus ne s'arrête jamais pour lui
            }
            handler.accept(json);
        }, mainThread);
    }

    @Override
    public CompletableFuture<Boolean> awaitSaved(UUID uuid, Duration timeout) {
        return api.awaitSaved(uuid, timeout);
    }

    @Override
    public void onDeparture(String plugin, Function<Player, CompletableFuture<Void>> save) {
        api.registerSaveTask(plugin, save);
    }

    @Override
    public Optional<String> otherServerOf(String name) {
        Optional<NetPlayer> found = api.find(name);
        if (found.isEmpty() || found.get().server().equals(api.serverName())) {
            return Optional.empty();
        }
        return Optional.of(found.get().server());
    }

    @Override
    public void runFor(String player, String command) {
        api.runFor(player, command);
    }

    @Override
    public Leader leader(String task, Runnable onAcquire, Runnable onLose) {
        LeaderHandle handle = api.leader(task, onAcquire, onLose);
        return new Leader() {
            @Override
            public boolean isLeader() {
                return handle.isLeader();
            }

            @Override
            public void release() {
                handle.release();
            }
        };
    }

    @Override
    public void sync(Runnable task) {
        api.sync(task);
    }
}
