package org.Cry_Legende.sHJobs.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.MySQLStorage;
import org.Cry_Legende.sHJobs.data.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * SHJobs sur plusieurs serveurs de jeu (SHNetwork). Les joueurs, la rotation du jour et les limites de la boutique
 * sont dans la base partagée :
 *
 * <ul>
 *   <li>un joueur est lu à sa connexion, une fois que le serveur qu'il quitte a fini de le sauvegarder, et écrit à son
 *   départ ;</li>
 *   <li>la rotation du jour est une seule ligne ({@code shjobs_state}) : le serveur meneur la prend à l'heure de
 *   rotation, les autres l'appliquent quand le bus la leur annonce (ou à la relecture de la ligne si le message
 *   s'est perdu) ;</li>
 *   <li>les limites de la boutique sont des compteurs par jour, joueur et article ({@code shjobs_daily_limits}).</li>
 * </ul>
 *
 * <pre>
 * canal   tous -> tous   rotation {day, jobs[6], shop[6], version}
 * </pre>
 *
 * Seule {@link ShNetworkBus} connaît SHNetwork : sans lui, aucune classe de ce paquet n'est chargée.
 */
public final class NetworkMode {

    private static final String TASK = "shjobs";

    private final SHJobs plugin;
    private final NetBus bus;
    private final SharedDb db;
    private final SharedState state;
    private final String channel;

    private volatile NetBus.Leader leader;
    private BukkitTask pollTask;
    private volatile boolean closed;

    // Rotation appliquée sur ce serveur (fil principal)
    private long appliedVersion = -1L;
    private LocalDate appliedDay;

    private NetworkMode(SHJobs plugin, NetBus bus, SharedDb db, String channel) {
        this.plugin = plugin;
        this.bus = bus;
        this.db = db;
        this.state = new SharedState(db);
        this.channel = channel;
    }

    /** Ouvre la base partagée et le pont vers SHNetwork. Une base injoignable lève une exception. */
    public static NetworkMode open(SHJobs plugin) throws SQLException {
        FileConfiguration config = plugin.getConfigManager().getRawConfig();
        String base = "reseau.base-de-donnees.";
        SharedDb db = new SharedDb(
            config.getString(base + "url"),
            config.getString(base + "utilisateur"),
            config.getString(base + "mot-de-passe"),
            Math.max(250, config.getInt(base + "delai-ms", 5000)));
        try {
            db.createTables();
            NetBus bus = ShNetworkBus.open();
            return new NetworkMode(plugin, bus, db, config.getString("reseau.canal", "shjobs"));
        } catch (SQLException | RuntimeException e) {
            db.close();
            throw e;
        }
    }

    public SharedDb db() {
        return db;
    }

    /** Stockage des joueurs dans la base partagée (mêmes tables que le stockage MySQL). */
    public StorageManager createStorage() {
        return new MySQLStorage(plugin, db);
    }

    public boolean isLeader() {
        NetBus.Leader current = leader;
        return current != null && current.isLeader();
    }

    /** Fin de l'activation : écoute du bus, sauvegarde au départ des joueurs, candidature au rôle de meneur. */
    public void start() {
        bus.listen(channel, this::onMessage, true);
        bus.onDeparture("SHJobs", player -> plugin.getPlayerCache().saveOnDeparture(player.getUniqueId()));
        leader = bus.leader(TASK, this::becameLeader, this::lostLeader);
        long every = Math.max(5, plugin.getConfigManager().getRawConfig().getInt("reseau.relecture-rotation-secondes", 30)) * 20L;
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, every, every);
    }

    /** Arrêt du plugin : rend le rôle de meneur et ferme la base (les écritures en cours sont déjà attendues). */
    public void close() {
        closed = true;
        if (pollTask != null) {
            pollTask.cancel();
        }
        NetBus.Leader current = leader;
        if (current != null) {
            current.release();
        }
        db.close();
    }

    // ------------------------------------------------------------------ rotation du jour

    /**
     * Au démarrage (et au rechargement) : lit la rotation partagée, la prend si elle date d'un autre jour ou manque,
     * puis l'applique. Bloque sur la base.
     */
    public void loadDailyState() throws SQLException {
        LocalDate today = LocalDate.now();
        SharedState.Row row = state.read();
        if (row == null) {
            row = adoptLocalRotation(today);
        }
        if (row == null || !today.equals(row.day()) || !row.isComplete()) {
            SharedState.Row won = claim(row, today, plugin.getJobManager().pickDailyJobIds(), plugin.getShopManager().pickDailyShopItemIds());
            row = won != null ? won : state.read();
        }
        if (row != null) {
            applyRow(row);
        }
    }

    /**
     * Première activation en mode réseau : si ce serveur a déjà une rotation du jour dans shjobs_server_state.yml,
     * elle devient celle du réseau, pour que les quêtes du jour des joueurs ne repartent pas de zéro.
     */
    private SharedState.Row adoptLocalRotation(LocalDate today) throws SQLException {
        File file = new File(plugin.getDataFolder(), "shjobs_server_state.yml");
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try {
            LocalDate day = LocalDate.parse(String.valueOf(yaml.getString("last_reset_date")));
            List<String> jobs = new ArrayList<>(yaml.getStringList("current_daily_job_ids"));
            List<String> shop = new ArrayList<>(yaml.getStringList("current_daily_shop_item_ids"));
            if (!day.equals(today) || jobs.size() != 6 || shop.size() != 6) {
                return null;
            }
            if (state.insert(day, jobs, shop)) {
                plugin.getLogger().info("Rotation du jour de ce serveur reprise comme rotation du réseau.");
                return new SharedState.Row(day, jobs, shop, 1L);
            }
            return state.read();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Écrit la rotation si personne ne l'a écrite avant nous : la ligne publiée, ou null si un autre serveur l'a prise. */
    private SharedState.Row claim(SharedState.Row current, LocalDate day, List<String> jobs, List<String> shop) throws SQLException {
        if (current == null) {
            return state.insert(day, jobs, shop) ? new SharedState.Row(day, jobs, shop, 1L) : null;
        }
        return state.claim(current.version(), day, jobs, shop) ? new SharedState.Row(day, jobs, shop, current.version() + 1) : null;
    }

    private record Outcome(SharedState.Row row, boolean won) {
    }

    /**
     * Rotation du jour : si la ligne partagée est d'un autre jour (ou avec {@code force}), tire une nouvelle rotation
     * et la prend ; sinon applique la ligne. Le serveur qui la prend la publie. Fil principal.
     *
     * @param force vrai pour la commande /sja resetdaily, qui tourne sur n'importe quel serveur
     */
    public void roll(boolean force) {
        roll(force, LocalDate.now());
    }

    /**
     * Rotation lancée à l'heure de rotation par la tâche du meneur. Cette tâche part parfois une fraction de seconde
     * avant l'heure (le délai est compté en secondes entières) : la date de la rotation est celle de dans quelques secondes.
     */
    public void rollScheduled() {
        roll(false, LocalDateTime.now().plusSeconds(5).toLocalDate());
    }

    private void roll(boolean force, LocalDate today) {
        if (closed || (!force && !isLeader())) {
            return;
        }
        List<String> jobs = plugin.getJobManager().pickDailyJobIds();
        List<String> shop = plugin.getShopManager().pickDailyShopItemIds();
        db.async(() -> {
            SharedState.Row row = state.read();
            if (!force && row != null && today.equals(row.day()) && row.isComplete()) {
                return new Outcome(row, false);
            }
            SharedState.Row won = claim(row, today, jobs, shop);
            return won != null ? new Outcome(won, true) : new Outcome(state.read(), false);
        }).whenComplete((outcome, error) -> bus.sync(() -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Rotation du jour impossible, base partagée injoignable.", error);
                return;
            }
            if (outcome.row() == null) {
                return;
            }
            applyRow(outcome.row());
            if (outcome.won()) {
                publish(outcome.row());
                purgeOldLimits(today);
            }
        }));
    }

    /** Un admin a changé un emplacement : la rotation courante de ce serveur devient celle du réseau. Fil principal. */
    public void publishRotation() {
        if (closed) {
            return;
        }
        LocalDate day = appliedDay != null ? appliedDay : LocalDate.now();
        List<String> jobs = plugin.getJobManager().getCurrentDailyJobTemplateIds();
        List<String> shop = plugin.getShopManager().getCurrentDailyShopItemIds();
        db.async(() -> new SharedState.Row(day, jobs, shop, state.write(day, jobs, shop)))
            .whenComplete((row, error) -> bus.sync(() -> {
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE, "Rotation du jour non enregistrée, base partagée injoignable.", error);
                    return;
                }
                appliedVersion = Math.max(appliedVersion, row.version());
                publish(row);
            }));
    }

    /** Applique la rotation sur ce serveur. Ignorée si une version plus récente est déjà appliquée. Fil principal. */
    private void applyRow(SharedState.Row row) {
        if (row.version() <= appliedVersion || !row.isComplete()) {
            return;
        }
        appliedVersion = row.version();
        boolean newDay = !row.day().equals(appliedDay);
        boolean jobsChanged = !row.jobIds().equals(plugin.getJobManager().getCurrentDailyJobTemplateIds());
        appliedDay = row.day();
        plugin.setLastResetDate(row.day());
        plugin.getJobManager().setCurrentDailyJobIds(row.jobIds());
        plugin.getShopManager().setCurrentDailyShopItemIds(row.shopIds());
        if (newDay || jobsChanged) {
            plugin.getPlayerCache().assignNewDailyJobsToAll();
        }
        plugin.getLogger().info("Rotation du jour appliquée (version " + row.version() + ", " + row.day() + ") : jobs="
            + row.jobIds() + " boutique=" + row.shopIds());
    }

    private void publish(SharedState.Row row) {
        JsonObject message = new JsonObject();
        message.addProperty("event", "rotation");
        message.addProperty("day", row.day().toString());
        message.add("jobs", toArray(row.jobIds()));
        message.add("shop", toArray(row.shopIds()));
        message.addProperty("version", row.version());
        bus.publish(channel, message);
    }

    private static JsonArray toArray(List<String> ids) {
        JsonArray array = new JsonArray();
        for (String id : ids) {
            array.add(id);
        }
        return array;
    }

    private static List<String> fromArray(JsonElement element) {
        List<String> ids = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                ids.add(item.isJsonNull() ? null : item.getAsString());
            }
        }
        return ids;
    }

    /** Fil principal : message du bus. */
    private void onMessage(JsonObject o) {
        try {
            JsonElement event = o.get("event");
            if (event == null || !"rotation".equals(event.getAsString())) {
                return;
            }
            SharedState.Row row = new SharedState.Row(
                LocalDate.parse(o.get("day").getAsString()),
                fromArray(o.get("jobs")),
                fromArray(o.get("shop")),
                o.get("version").getAsLong());
            applyRow(row);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Message illisible sur " + channel + " : " + e);
        }
    }

    /** Fil asynchrone : si un message du bus s'est perdu, la ligne en base a une version plus récente. */
    private void poll() {
        if (closed) {
            return;
        }
        try {
            SharedState.Row row = state.read();
            if (row != null) {
                bus.sync(() -> applyRow(row));
            }
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().fine("Relecture de la rotation impossible : " + e);
        }
    }

    private void purgeOldLimits(LocalDate today) {
        db.async(() -> new SharedLimits(db).purgeBefore(today.toString())).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Limites des jours passés non purgées.", error);
            return 0;
        });
    }

    private void becameLeader() {
        plugin.scheduleDailyResetTask();
        // Un meneur qui reprend après une panne rattrape une rotation manquée
        roll(false);
    }

    private void lostLeader() {
        plugin.cancelDailyResetTask();
    }

    // ------------------------------------------------------------------ joueurs

    /**
     * Avant de lire un joueur à sa connexion : attend que le serveur qu'il quitte ait fini de le sauvegarder, puis lit
     * ses limites de la boutique. Se termine toujours sans erreur : une sauvegarde non confirmée est signalée, les
     * données sont alors lues telles qu'elles sont en base.
     */
    public CompletableFuture<Void> beforeLoad(UUID uuid) {
        long seconds = Math.max(1, plugin.getConfigManager().getRawConfig().getInt("reseau.attente-sauvegarde-secondes", 10));
        return bus.awaitSaved(uuid, Duration.ofSeconds(seconds))
            .handle((saved, error) -> {
                if (error != null || !Boolean.TRUE.equals(saved)) {
                    plugin.getLogger().warning("Sauvegarde du serveur de départ non confirmée pour " + uuid + " : données lues telles qu'en base.");
                }
                return (Void) null;
            })
            .thenCompose(ignored -> plugin.getDailyLimits().loadPlayer(uuid))
            .exceptionally(error -> {
                plugin.getLogger().log(Level.SEVERE, "Limites de la boutique illisibles pour " + uuid + ".", error);
                return null;
            });
    }

    /** Serveur où le joueur est connecté, s'il l'est sur un autre que celui-ci. */
    public Optional<String> otherServerOf(String name) {
        return bus.otherServerOf(name);
    }

    /** Exécute la commande console là où se trouve le joueur. */
    public void runFor(String player, String command) {
        bus.runFor(player, command);
    }

    // ------------------------------------------------------------------ reprise des données locales

    /** Reprend dans la base partagée les données de ce serveur (SQLite et daily_limits.yml). Répond à {@code sender}. */
    public void importLocal(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            LocalImport.Result result = null;
            Exception failure = null;
            try {
                result = new LocalImport(plugin, db).run();
            } catch (Exception e) {
                failure = e;
            }
            LocalImport.Result done = result;
            Exception failed = failure;
            bus.sync(() -> {
                if (failed != null) {
                    plugin.getLogger().log(Level.SEVERE, "Reprise des données locales impossible.", failed);
                    plugin.getMessages().sendMessage(sender, "admin.import.failure", "%error%", String.valueOf(failed.getMessage()));
                } else {
                    plugin.getMessages().sendMessage(sender, "admin.import.done",
                        "%players%", String.valueOf(done.players()),
                        "%present%", String.valueOf(done.alreadyThere()),
                        "%limits%", String.valueOf(done.limits()));
                }
            });
        });
    }
}
