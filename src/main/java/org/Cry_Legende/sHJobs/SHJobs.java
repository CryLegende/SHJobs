package org.Cry_Legende.sHJobs;

import org.Cry_Legende.sHJobs.commands.AdminJobsCommand;
import org.Cry_Legende.sHJobs.commands.JobsCommand;
import org.Cry_Legende.sHJobs.commands.JobsTabCompleter;
import org.Cry_Legende.sHJobs.config.ConfigManager;
import org.Cry_Legende.sHJobs.config.MenuConfig;
import org.Cry_Legende.sHJobs.config.Messages;
import org.Cry_Legende.sHJobs.data.PlayerCache;
import org.Cry_Legende.sHJobs.data.StorageManager;
import org.Cry_Legende.sHJobs.data.MySQLStorage;
import org.Cry_Legende.sHJobs.data.SQLiteStorage;
import org.Cry_Legende.sHJobs.gui.InventoryListener;
import org.Cry_Legende.sHJobs.gui.MenuManager;
import org.Cry_Legende.sHJobs.items.CustomItemManager;
import org.Cry_Legende.sHJobs.network.NetworkMode;
import org.Cry_Legende.sHJobs.jobs.JobManager;
import org.Cry_Legende.sHJobs.jobs.LeaderboardManager;
import org.Cry_Legende.sHJobs.listeners.JobProgressListener;
import org.Cry_Legende.sHJobs.listeners.PlayerConnectionListener;
import org.Cry_Legende.sHJobs.placeholders.SHJobsExpansion;
import org.Cry_Legende.sHJobs.shop.ShopManager;
import org.Cry_Legende.sHJobs.tasks.DailyResetTask;
import org.Cry_Legende.sHJobs.tasks.RealTimeUpdateTask;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class SHJobs extends JavaPlugin {

    /**
     * Fil principal du serveur, relevé à l'activation. Avec les ticks de mondes en parallèle (DivineMC), les fils des
     * mondes sont aussi des fils de tick : Bukkit.isPrimaryThread() y répond vrai, il ne sert donc pas à les distinguer.
     */
    private static volatile Thread serverThread;

    /** Vrai uniquement sur le fil principal du serveur (jamais sur le fil d'un monde ou d'une région). */
    public static boolean onServerThread() {
        return Thread.currentThread() == serverThread;
    }


    private static SHJobs instance;

    private ConfigManager configManager;
    private Messages messages;
    private MenuConfig menuConfig;
    private volatile StorageManager storageManager;
    // Réglages de base de données du stockage ouvert : un rechargement ne le rouvre que s'ils ont changé
    private String activeStorageSignature = "";
    private PlayerCache playerCache;
    private JobManager jobManager;
    private ShopManager shopManager;
    private org.Cry_Legende.sHJobs.shop.DailyLimits dailyLimits;
    private MenuManager menuManager;
    private CustomItemManager customItemManager;
    private LeaderboardManager leaderboardManager;

    private BukkitTask dailyResetTaskScheduler;
    private BukkitTask realTimeUpdateTaskScheduler;

    private File serverStateFile;
    private FileConfiguration serverStateConfig;
    private volatile LocalDate lastResetDate;

    // Non nul seulement avec SHNetwork : joueurs, rotation du jour et limites de la boutique en base partagée
    private NetworkMode network;

    @Override
    public void onEnable() {
        serverThread = Thread.currentThread();
        instance = this;
        this.getLogger().info("SHJobs is enabling...");

        setupServerStateFile();

        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);
        this.menuConfig = new MenuConfig(this);
        this.customItemManager = new CustomItemManager(this);

        if (!enableNetwork()) {
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupStorage()) {
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.playerCache = new PlayerCache(this);
        this.jobManager = new JobManager(this);
        this.shopManager = new ShopManager(this);
        this.dailyLimits = new org.Cry_Legende.sHJobs.shop.DailyLimits(this);
        this.menuManager = new MenuManager(this);
        this.leaderboardManager = new LeaderboardManager(this);
        if (network != null) {
            this.dailyLimits.useSharedStore(network.db());
        }

        this.jobManager.loadJobTemplates();
        this.shopManager.loadShopItems();
        this.menuManager.loadMenus();

        loadOrSelectDailyServerState();
        if (!isEnabled()) {
            return;
        }

        JobsCommand jobsCommand = new JobsCommand(this);
        JobsTabCompleter tabCompleter = new JobsTabCompleter(this);
        getCommand("jobs").setExecutor(jobsCommand);
        getCommand("jobs").setTabCompleter(tabCompleter);
        getCommand("shjobs").setExecutor(new AdminJobsCommand(this, jobsCommand));
        getCommand("shjobs").setTabCompleter(tabCompleter);
        this.getLogger().info("Commands registered.");

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new JobProgressListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        this.getLogger().info("Event listeners registered.");

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SHJobsExpansion(this).register();
            this.getLogger().info("PlaceholderAPI expansion registered.");
        } else {
            this.getLogger().warning("PlaceholderAPI not found. Placeholders will not work.");
        }

        scheduleDailyResetTask();
        this.realTimeUpdateTaskScheduler = new RealTimeUpdateTask(this).runTaskTimer(this, 20L, 20L);
        this.leaderboardManager.startUpdateTask();
        if (network != null) {
            network.start();
        }

        this.getLogger().info("SHJobs has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("SHJobs is disabling...");

        if (this.leaderboardManager != null) {
            this.leaderboardManager.stopUpdateTask();
        }

        if (this.network == null) {
            saveServerState();
        }
        if (this.dailyLimits != null) {
            this.dailyLimits.save();
        }

        if (this.playerCache != null) {
            this.playerCache.saveAllOnlinePlayersSync();
        }
        if (this.network != null && this.dailyLimits != null) {
            this.dailyLimits.flushAll(5000L);
        }

        if (this.storageManager != null) {
            try {
                this.storageManager.close();
                this.getLogger().info("Database connection closed.");
            } catch (SQLException e) {
                this.getLogger().log(Level.SEVERE, "Error closing database connection.", e);
            }
        }

        if (this.network != null) {
            this.network.close();
        }

        if (dailyResetTaskScheduler != null && !dailyResetTaskScheduler.isCancelled()) {
            dailyResetTaskScheduler.cancel();
        }
        if (realTimeUpdateTaskScheduler != null && !realTimeUpdateTaskScheduler.isCancelled()) {
            realTimeUpdateTaskScheduler.cancel();
        }

        this.getLogger().info("SHJobs has been disabled.");
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et que reseau.actif est vrai. Les classes qui touchent à
     * SHNetwork ne sont chargées que dans ce cas. Faux si le mode réseau est demandé mais inutilisable : le plugin
     * ne démarre pas avec des données locales qui divergeraient de celles des autres serveurs.
     */
    private boolean enableNetwork() {
        if (!configManager.getRawConfig().getBoolean("reseau.actif", true)) {
            return true;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) {
            return true;
        }
        try {
            this.network = NetworkMode.open(this);
            this.getLogger().info("SHNetwork détecté : joueurs, rotation du jour et limites de la boutique partagés entre les serveurs.");
            return true;
        } catch (Throwable e) {
            this.getLogger().log(Level.SEVERE, "SHNetwork présent mais mode réseau inutilisable, plugin désactivé.", e);
            return false;
        }
    }

    private boolean setupStorage() {
        StorageManager opened = openStorage();
        if (opened == null) {
            return false;
        }
        this.storageManager = opened;
        this.activeStorageSignature = storageSignature();
        return true;
    }

    /** Ouvre et initialise le stockage configuré. Null (erreur dans la console) si c'est impossible. */
    private StorageManager openStorage() {
        if (network != null) {
            try {
                StorageManager shared = network.createStorage();
                shared.init();
                this.getLogger().info("Database initialized successfully using the shared network database.");
                return shared;
            } catch (SQLException e) {
                this.getLogger().log(Level.SEVERE, "Failed to initialize database. Disabling plugin.", e);
                return null;
            }
        }
        String storageType = configManager.getStorageType().toUpperCase();
        StorageManager storage;
        if (storageType.equals("MYSQL")) {
            storage = new MySQLStorage(this);
        } else if (storageType.equals("SQLITE")) {
            File dataFolder = new File(this.getDataFolder(), "database");
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                this.getLogger().severe("Could not create database directory for SQLite. Disabling plugin.");
                return null;
            }
            storage = new SQLiteStorage(this, new File(dataFolder, "shjobs_data.db").getAbsolutePath());
        } else {
            this.getLogger().severe("Invalid storage type specified in config.yml: " + configManager.getStorageType() + ". Disabling plugin.");
            return null;
        }

        try {
            storage.init();
            this.getLogger().info("Database initialized successfully using " + storageType + ".");
            return storage;
        } catch (SQLException e) {
            this.getLogger().log(Level.SEVERE, "Failed to initialize database. Disabling plugin.", e);
            try {
                storage.close();
            } catch (SQLException | RuntimeException ignored) {
                // la connexion n'a jamais été ouverte
            }
            return null;
        }
    }

    private String storageSignature() {
        return String.join("|", configManager.getStorageType(), configManager.getMysqlHost(),
            String.valueOf(configManager.getMysqlPort()), configManager.getMysqlDatabase(), configManager.getMysqlUsername(),
            configManager.getMysqlPassword(), String.valueOf(configManager.mysqlUseSSL()));
    }

    private void setupServerStateFile() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        serverStateFile = new File(getDataFolder(), "shjobs_server_state.yml");
        if (!serverStateFile.exists()) {
            try {
                serverStateFile.createNewFile();
                getLogger().info("Created shjobs_server_state.yml");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not create shjobs_server_state.yml!", e);
            }
        }
        serverStateConfig = YamlConfiguration.loadConfiguration(serverStateFile);
    }

    private void loadOrSelectDailyServerState() {
        if (network != null) {
            try {
                network.loadDailyState();
            } catch (SQLException | RuntimeException e) {
                this.getLogger().log(Level.SEVERE, "Rotation du jour illisible dans la base partagée, plugin désactivé.", e);
                this.getServer().getPluginManager().disablePlugin(this);
            }
            return;
        }
        String lastResetDateStr = serverStateConfig.getString("last_reset_date");
        if (lastResetDateStr != null) {
            try {
                this.lastResetDate = LocalDate.parse(lastResetDateStr);
            } catch (Exception e) {
                this.lastResetDate = LocalDate.now().minusDays(1);
                getLogger().warning("Invalid last_reset_date in shjobs_server_state.yml, assuming reset is needed.");
            }
        } else {
            this.lastResetDate = LocalDate.now().minusDays(1);
        }

        boolean isNewDay = !LocalDate.now().equals(this.lastResetDate);
        boolean forceRegenerate = configManager.getRawConfig().getBoolean("general.regenerate_daily_jobs_on_reload", false) && !Bukkit.getPluginManager().isPluginEnabled(this);

        // --- GESTION DES JOBS ---
        List<String> loadedDailyJobIds = serverStateConfig.getStringList("current_daily_job_ids");
        boolean jobsDataMissing = loadedDailyJobIds == null || loadedDailyJobIds.isEmpty() || loadedDailyJobIds.size() != 6;

        if (isNewDay || forceRegenerate || jobsDataMissing) {
            jobManager.selectNewDailyJobs();
            // Important : Si c'est un nouveau jour, on clear les achats.
            // Le bug précédent empêchait ceci d'être appelé car la logique était dans le bloc 'else'.
            if (isNewDay && playerCache != null) {
                playerCache.clearAllDailyPurchases();
            }
            getLogger().info("Generated new daily jobs. (Reason: NewDay=" + isNewDay + ", Missing=" + jobsDataMissing + ")");
        } else {
            jobManager.setCurrentDailyJobIds(loadedDailyJobIds);
            getLogger().info("Loaded persistent daily jobs from server state: " + String.join(", ", loadedDailyJobIds));
        }

        // --- GESTION DE LA BOUTIQUE ---
        List<String> loadedDailyShopItemIds = serverStateConfig.getStringList("current_daily_shop_item_ids");
        boolean shopDataMissing = loadedDailyShopItemIds == null || loadedDailyShopItemIds.isEmpty() || loadedDailyShopItemIds.size() != 6;

        if (isNewDay || forceRegenerate || shopDataMissing) {
            shopManager.selectNewDailyShopItems();
            getLogger().info("Generated new daily shop items. (Reason: NewDay=" + isNewDay + ", Missing=" + shopDataMissing + ")");
        } else {
            shopManager.setCurrentDailyShopItemIds(loadedDailyShopItemIds);
            getLogger().info("Loaded persistent daily shop items from server state: " + String.join(", ", loadedDailyShopItemIds));
        }

        // Mise à jour de la date si nécessaire
        if (isNewDay) {
            this.lastResetDate = LocalDate.now();
        }

        // On sauvegarde immédiatement pour assurer la persistance de ce qu'on vient de charger/générer
        saveServerState();
    }

    public void saveServerState() {
        if (network != null) {
            // Le tirage du jour est en base : l'enregistrer le publie aux autres serveurs
            network.publishRotation();
            return;
        }
        if (serverStateConfig == null || serverStateFile == null) {
            getLogger().warning("Cannot save server state, config/file is null.");
            return;
        }
        if (jobManager != null) {
            serverStateConfig.set("current_daily_job_ids", jobManager.getCurrentDailyJobTemplateIds());
        }
        if (shopManager != null) {
            serverStateConfig.set("current_daily_shop_item_ids", shopManager.getCurrentDailyShopItemIds());
        }
        if (lastResetDate != null) {
            serverStateConfig.set("last_reset_date", lastResetDate.toString());
        }
        try {
            serverStateConfig.save(serverStateFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save shjobs_server_state.yml!", e);
        }
    }

    public LocalDate getLastResetDate() {
        if (lastResetDate == null) {
            String dateStr = serverStateConfig.getString("last_reset_date");
            if (dateStr != null) {
                try {
                    lastResetDate = LocalDate.parse(dateStr);
                } catch (Exception e) { /* restera null */ }
            }
            if (lastResetDate == null) lastResetDate = LocalDate.now().minusDays(1);
        }
        return lastResetDate;
    }

    public void setLastResetDate(LocalDate date) {
        this.lastResetDate = date;
    }

    /** Retire la tâche de rotation quotidienne (avec SHNetwork : ce serveur n'est plus le meneur). */
    public void cancelDailyResetTask() {
        if (dailyResetTaskScheduler != null && !dailyResetTaskScheduler.isCancelled()) {
            dailyResetTaskScheduler.cancel();
        }
    }

    public void scheduleDailyResetTask() {
        cancelDailyResetTask();
        if (network != null && !network.isLeader()) {
            return; // avec SHNetwork, seul le serveur meneur lance la rotation
        }

        LocalDateTime now = LocalDateTime.now();
        LocalTime resetTime = configManager.getDailyResetTime();
        LocalDateTime nextRun = now.with(resetTime);

        if (now.isAfter(nextRun) || now.equals(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        Duration durationUntilNextRun = Duration.between(now, nextRun);
        long ticksUntilNextRun = Math.max(0, durationUntilNextRun.getSeconds() * 20L);
        long ticksPerDay = 24 * 60 * 60 * 20L;

        dailyResetTaskScheduler = new DailyResetTask(this).runTaskTimer(this, ticksUntilNextRun, ticksPerDay);
        getLogger().info("Daily reset task scheduled. Next reset at: " + nextRun.toString());
    }

    /**
     * Résultat d'un rechargement.
     *
     * @param file           fichier dont la syntaxe est invalide : rien n'a été appliqué (null si tout s'est bien passé)
     * @param error          première ligne de l'erreur de ce fichier
     * @param databaseFailed la nouvelle base n'a pas pu être ouverte : l'ancienne reste utilisée
     * @param networkChanged le bloc reseau a changé : il ne s'applique qu'au redémarrage
     */
    public record ReloadResult(String file, String error, boolean databaseFailed, boolean networkChanged) {
        public boolean failed() {
            return file != null;
        }
    }

    private static String firstLine(Exception e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int end = text.indexOf('\n');
        return (end < 0 ? text : text.substring(0, end)).trim();
    }

    private Map<String, Object> networkSettings() {
        var section = configManager.getRawConfig().getConfigurationSection("reseau");
        return section == null ? Map.of() : section.getValues(true);
    }

    /**
     * Relit config.yml, messages.yml, menus.yml et custom_items.yml. Si l'un d'eux a une erreur de syntaxe, rien n'est
     * appliqué. La base n'est rouverte que si database.* a changé, et l'ancienne est gardée si la nouvelle ne s'ouvre
     * pas. La rotation du jour (shjobs_server_state.yml, base partagée) et les données des joueurs ne sont pas touchées.
     */
    public ReloadResult reloadPluginLogic() {
        for (String name : new String[]{"config.yml", "messages.yml", "menus.yml", "custom_items.yml"}) {
            File file = new File(getDataFolder(), name);
            if (!file.exists()) {
                continue;
            }
            try {
                new YamlConfiguration().load(file);
            } catch (IOException | InvalidConfigurationException e) {
                return new ReloadResult(name, firstLine(e), false, false);
            }
        }

        getLogger().info("Reloading SHJobs...");
        Map<String, Object> networkBefore = networkSettings();

        if (dailyResetTaskScheduler != null && !dailyResetTaskScheduler.isCancelled()) dailyResetTaskScheduler.cancel();
        if (realTimeUpdateTaskScheduler != null && !realTimeUpdateTaskScheduler.isCancelled())
            realTimeUpdateTaskScheduler.cancel();
        if (leaderboardManager != null) leaderboardManager.stopUpdateTask();

        this.configManager.reloadConfig();
        this.messages.reloadMessages();
        this.menuConfig.reloadMenuConfig();
        this.customItemManager.loadItems();

        boolean databaseFailed = false;
        if (network == null && !storageSignature().equals(activeStorageSignature)) {
            StorageManager fresh = openStorage();
            if (fresh == null) {
                databaseFailed = true;
                getLogger().severe("New database settings unusable during reload: the previous connection is kept.");
            } else {
                StorageManager previous = this.storageManager;
                this.storageManager = fresh;
                this.activeStorageSignature = storageSignature();
                if (previous != null) {
                    try {
                        previous.close();
                    } catch (SQLException e) {
                        getLogger().log(Level.WARNING, "Error closing the previous storage during reload.", e);
                    }
                }
            }
        }

        this.jobManager.loadJobTemplates();
        this.shopManager.loadShopItems();
        this.menuManager.loadMenus();

        scheduleDailyResetTask();
        this.realTimeUpdateTaskScheduler = new RealTimeUpdateTask(this).runTaskTimer(this, 20L, 20L);
        this.leaderboardManager.startUpdateTask();

        getLogger().info("SHJobs reloaded successfully.");
        return new ReloadResult(null, null, databaseFailed, !networkBefore.equals(networkSettings()));
    }

    // --- Getters ---
    public static SHJobs getInstance() {
        return instance;
    }

    /** Mode réseau (SHNetwork), nul sur un serveur normal. */
    public NetworkMode getNetwork() {
        return network;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public MenuConfig getMenuConfig() {
        return menuConfig;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public PlayerCache getPlayerCache() {
        return playerCache;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    /** Limites quotidiennes d'achat et de revente de la boutique. */
    public org.Cry_Legende.sHJobs.shop.DailyLimits getDailyLimits() {
        return dailyLimits;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public CustomItemManager getCustomItemManager() {
        return customItemManager;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }
}
