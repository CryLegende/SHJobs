package org.Cry_Legende.sHJobs.shop;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Limites quotidiennes d'achat et de revente de la boutique des métiers, par joueur et par article.
 * <p>
 * Limites lues sur chaque article ({@code purchase_limit_per_day}, {@code sell_limit_per_day}), sinon dans
 * {@code general.default_purchase_limit_per_day} / {@code general.default_sell_limit_per_day} ; 0 = illimité.
 * Une revente compte une « unité » de l'article (ex. redstone_stack = 3 redstone). Les compteurs repartent à zéro
 * avec la rotation quotidienne de la boutique (date {@code last_reset_date}) et sont gardés dans
 * {@code daily_limits.yml} : se reconnecter ou redémarrer le serveur ne les remet pas à zéro.
 */
public final class DailyLimits {

    public enum Kind {
        BOUGHT("bought"), SOLD("sold");

        final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    private static final long SAVE_DELAY_TICKS = 100L;

    private final SHJobs plugin;
    private final File file;
    private final Map<UUID, Map<String, int[]>> counts = new HashMap<>();
    private String day;
    private boolean saveScheduled;

    public DailyLimits(SHJobs plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily_limits.yml");
        load();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        day = yaml.getString("day");
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String uuidKey : players.getKeys(false)) {
            ConfigurationSection items = players.getConfigurationSection(uuidKey);
            if (items == null) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(uuidKey);
                Map<String, int[]> perItem = new HashMap<>();
                for (String itemId : items.getKeys(false)) {
                    perItem.put(itemId, new int[]{items.getInt(itemId + ".bought"), items.getInt(itemId + ".sold")});
                }
                counts.put(uuid, perItem);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("daily_limits.yml : UUID invalide ignoré (" + uuidKey + ").");
            }
        }
    }

    /** Nouveau jour de boutique : les compteurs de la veille sont oubliés. */
    private void roll() {
        String today = plugin.getLastResetDate().toString();
        if (!today.equals(day)) {
            counts.clear();
            day = today;
            scheduleSave();
        }
    }

    private static int limit(ShopItem item, Kind kind) {
        return kind == Kind.BOUGHT ? item.getPurchaseLimitPerDay() : item.getSellLimitPerDay();
    }

    public int used(UUID player, String itemId, Kind kind) {
        roll();
        int[] c = counts.getOrDefault(player, Map.of()).get(itemId);
        return c == null ? 0 : c[kind.ordinal()];
    }

    /** Unités encore autorisées aujourd'hui ({@link Integer#MAX_VALUE} si l'article n'a pas de limite). */
    public int remaining(UUID player, ShopItem item, Kind kind) {
        int limit = limit(item, kind);
        if (limit <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, limit - used(player, item.getId(), kind));
    }

    public void add(UUID player, String itemId, Kind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        roll();
        counts.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(itemId, k -> new int[2])[kind.ordinal()] += amount;
        scheduleSave();
    }

    private void scheduleSave() {
        if (saveScheduled || !plugin.isEnabled()) {
            return;
        }
        saveScheduled = true;
        plugin.getServer().getScheduler().runTaskLater(plugin, this::save, SAVE_DELAY_TICKS);
    }

    /** Écrit le fichier (thread principal, fichier de quelques Ko). Appelé aussi à l'arrêt du plugin. */
    public void save() {
        saveScheduled = false;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("day", day);
        for (Map.Entry<UUID, Map<String, int[]>> player : counts.entrySet()) {
            for (Map.Entry<String, int[]> item : player.getValue().entrySet()) {
                String path = "players." + player.getKey() + "." + item.getKey();
                yaml.set(path + "." + Kind.BOUGHT.key, item.getValue()[0]);
                yaml.set(path + "." + Kind.SOLD.key, item.getValue()[1]);
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible d'enregistrer daily_limits.yml", e);
        }
    }
}
