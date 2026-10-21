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
import org.Cry_Legende.sHJobs.jobs.JobManager;
import org.Cry_Legende.sHJobs.jobs.LeaderboardManager;
import org.Cry_Legende.sHJobs.listeners.JobProgressListener;
import org.Cry_Legende.sHJobs.listeners.PlayerConnectionListener;
import org.Cry_Legende.sHJobs.placeholders.SHJobsExpansion;
import org.Cry_Legende.sHJobs.shop.ShopManager;
import org.Cry_Legende.sHJobs.tasks.DailyResetTask;
import org.Cry_Legende.sHJobs.tasks.RealTimeUpdateTask;

import org.bukkit.Bukkit;
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
import java.util.logging.Level;

public final class SHJobs extends JavaPlugin {

    private static SHJobs instance;

    private ConfigManager configManager;
    private Messages messages;
    private MenuConfig menuConfig;
    private StorageManager storageManager;
    private PlayerCache playerCache;
    private JobManager jobManager;
    private ShopManager shopManager;
    private MenuManager menuManager;
    private CustomItemManager customItemManager;
    private LeaderboardManager leaderboardManager;

    private BukkitTask dailyResetTaskScheduler;
    private BukkitTask realTimeUpdateTaskScheduler;

    private File serverStateFile;
    private FileConfiguration serverStateConfig;
    private LocalDate lastResetDate;

    @Override
    public void onEnable() {
        instance = this;
        this.getLogger().info("SHJobs is enabling...");

        setupServerStateFile();

        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);
        this.menuConfig = new MenuConfig(this);
        this.customItemManager = new CustomItemManager(this);

        if (!setupStorage()) {
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.playerCache = new PlayerCache(this);
        this.jobManager = new JobManager(this);
        this.shopManager = new ShopManager(this);
        this.menuManager = new MenuManager(this);
        this.leaderboardManager = new LeaderboardManager(this);

        this.jobManager.loadJobTemplates();
        this.shopManager.loadShopItems();
        this.menuManager.loadMenus();

        loadOrSelectDailyServerState();

        getCommand("jobs").setExecutor(new JobsCommand(this));
        getCommand("shjobsadmin").setExecutor(new AdminJobsCommand(this));
        getCommand("shjobsadmin").setTabCompleter(new JobsTabCompleter(this));
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

        this.getLogger().info("SHJobs has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("SHJobs is disabling...");

        if (this.leaderboardManager != null) {
            this.leaderboardManager.stopUpdateTask();
        }

        saveServerState();

        if (this.playerCache != null) {
            this.playerCache.saveAllOnlinePlayersSync();
        }

        if (this.storageManager != null) {
            try {
                this.storageManager.close();
                this.getLogger().info("Database connection closed.");
            } catch (SQLException e) {
                this.getLogger().log(Level.SEVERE, "Error closing database connection.", e);
            }
        }

        if (dailyResetTaskScheduler != null && !dailyResetTaskScheduler.isCancelled()) {
            dailyResetTaskScheduler.cancel();
        }
        if (realTimeUpdateTaskScheduler != null && !realTimeUpdateTaskScheduler.isCancelled()) {
            realTimeUpdateTaskScheduler.cancel();
        }

        this.getLogger().info("SHJobs has been disabled.");
    }

    private boolean setupStorage() {
        String storageType = configManager.getStorageType().toUpperCase();
        if (storageType.equals("MYSQL")) {
            this.storageManager = new MySQLStorage(this);
        } else if (storageType.equals("SQLITE")) {
            File dataFolder = new File(this.getDataFolder(), "database");
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                this.getLogger().severe("Could not create database directory for SQLite. Disabling plugin.");
                return false;
            }
            this.storageManager = new SQLiteStorage(this, new File(dataFolder, "shjobs_data.db").getAbsolutePath());
        } else {
            this.getLogger().severe("Invalid storage type specified in config.yml: " + configManager.getStorageType() + ". Disabling plugin.");
            return false;
        }

        try {
            this.storageManager.init();
            this.getLogger().info("Database initialized successfully using " + storageType + ".");
            return true;
        } catch (SQLException e) {
            this.getLogger().log(Level.SEVERE, "Failed to initialize database. Disabling plugin.", e);
            return false;
        }
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

    private void scheduleDailyResetTask() {
        if (dailyResetTaskScheduler != null && !dailyResetTaskScheduler.isCancelled()) {
            dailyResetTaskScheduler.cancel();
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

    public void reloadPluginLogic() {
        getLogger().info("Reloading SHJobs...");

        if (dailyResetTaskScheduler != null && !dailyResetTaskScheduler.isCancelled()) dailyResetTaskScheduler.cancel();
        if (realTimeUpdateTaskScheduler != null && !realTimeUpdateTaskScheduler.isCancelled())
            realTimeUpdateTaskScheduler.cancel();
        if (leaderboardManager != null) leaderboardManager.stopUpdateTask();

        this.configManager.reloadConfig();
        this.messages.reloadMessages();
        this.menuConfig.reloadMenuConfig();
        this.customItemManager.loadItems();

        if (this.storageManager != null) {
            try {
                this.storageManager.close();
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Error closing storage during reload.", e);
            }
        }
        if (!setupStorage()) {
            getLogger().severe("Failed to re-initialize storage during reload.");
        }

        this.jobManager.loadJobTemplates();
        this.shopManager.loadShopItems();
        this.menuManager.loadMenus();

        loadOrSelectDailyServerState();

        scheduleDailyResetTask();
        this.realTimeUpdateTaskScheduler = new RealTimeUpdateTask(this).runTaskTimer(this, 20L, 20L);
        this.leaderboardManager.startUpdateTask();

        getLogger().info("SHJobs reloaded successfully.");
    }

    // --- Getters ---
    public static SHJobs getInstance() {
        return instance;
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
