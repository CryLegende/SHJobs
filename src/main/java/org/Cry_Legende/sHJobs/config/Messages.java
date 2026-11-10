package org.Cry_Legende.sHJobs.config;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Messages {

    // Textes utilisés quand la clé est absente de messages.yml
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
        Map.entry("common.player_data_load_error_menu", "&7&l« &c&lSH &8&l» &7Impossible de charger vos &cdonnées de métier &7pour ce menu."),
        Map.entry("common.player_data_load_error_critical", "&7&l« &c&lSH &8&l» &7Vos &cdonnées de métier &7n'ont pas pu être chargées. Reconnectez-vous ou contactez un administrateur."),
        Map.entry("common.random", "aléatoire"),
        Map.entry("admin.customitem.no_item_in_hand", "&7&l« &c&lSH &8&l» &7Vous devez tenir un item dans votre main."),
        Map.entry("admin.customitem.save_failure", "&7&l« &c&lSH &8&l» &7Erreur lors de la sauvegarde de l'item avec l'ID '&c%id%&7'."),
        Map.entry("menu.population_error", "&7&l« &c&lSH &8&l» &7Le menu '&c%menu%&7' n'a pas pu être affiché."),
        Map.entry("menu.item_section_missing", "&cErreur : section de l'item introuvable"),
        Map.entry("menu.placeholder_job_empty", "&cEmplacement de Job Vide"),
        Map.entry("menu.placeholder_job_unavailable", "&cJob Non Disponible"),
        Map.entry("menu.placeholder_job_invalid", "&cMétier Invalide"),
        Map.entry("menu.placeholder_shop_unavailable", "&7Article Indisponible"),
        Map.entry("menu.placeholder_shop_empty", "&7Emplacement Vide"),
        Map.entry("menu.placeholder_shop_lore", "&cCet article n'est pas disponible actuellement."),
        Map.entry("shop.purchase_successful_quantity", "&7&l« &a&lSH &8&l» &7Vous avez acheté &a%amount%x %item_name% &7pour &a%cost% points &7!"),
        Map.entry("shop.sell_config_error", "&7&l« &c&lSH &8&l» &7Erreur de configuration du serveur : vente &cimpossible&7."),
        Map.entry("admin.help.import", "&e/shjobsadmin import &7- Reprend les données de ce serveur dans la base partagée (SHNetwork)."),
        Map.entry("admin.remote_sent", "&7&l« &e&lSH &8&l» &a%player% &7est connecté sur &e%server%&7 : commande envoyée sur ce serveur."),
        Map.entry("admin.import.not_network", "&7&l« &c&lSH &8&l» &7Cette commande demande SHNetwork et le mode réseau actif."),
        Map.entry("admin.import.started", "&7&l« &e&lSH &8&l» &7Reprise des données de ce serveur dans la base partagée..."),
        Map.entry("admin.import.done", "&7&l« &a&lSH &8&l» &7Reprise terminée : &a%players% joueurs &7repris, &a%present% &7déjà en base, &a%limits% &7compteurs de limites."),
        Map.entry("admin.import.failure", "&7&l« &c&lSH &8&l» &7Reprise impossible : &c%error%&7.")
    );

    private final SHJobs plugin;
    private File messagesFile;
    private FileConfiguration messagesConfig;
    private boolean loaded = false;
    private String prefix = "";

    public Messages(SHJobs plugin) {
        this.plugin = plugin;
        setupMessagesFile();
        loadMessages();
    }

    private void setupMessagesFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.getLogger().info("messages.yml not found, creating default...");
            plugin.saveResource("messages.yml", false);
        }
    }

    public void loadMessages() {
        messagesConfig = new YamlConfiguration();
        try {
            messagesConfig.load(messagesFile);
            loaded = true;
            prefix = messagesConfig.getString("plugin_prefix", "&8[&6SHJobs&8] &r");
            plugin.getLogger().info("messages.yml loaded successfully.");
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load messages.yml!", e);
            loaded = false;
            prefix = ChatColor.translateAlternateColorCodes('&', "&8[&6SHJobs&8] &r"); // Fallback prefix
        }
    }

    public void reloadMessages() {
        if (messagesFile == null) {
            setupMessagesFile();
        }
        loadMessages();
    }

    public String getPrefix() {
        return colorize(prefix);
    }

    public String getMessage(String path, boolean usePrefix, String... replacements) {
        if (!loaded) {
            return ChatColor.RED + "Error: messages.yml not loaded. Message path: " + path;
        }
        String message = messagesConfig.getString(path, DEFAULTS.get(path));
        if (message == null) {
            plugin.getLogger().warning("Missing message in messages.yml for path: " + path);
            return ChatColor.RED + "Missing message: " + path;
        }

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }

        message = plugin.getMenuManager().applyPlaceholders(message, null, null); // Appliquer les placeholders globaux et PAPI si pas de joueur

        return colorize((usePrefix ? prefix : "") + message);
    }

    public String getMessage(String path, String... replacements) {
        return getMessage(path, true, replacements); // Par défaut, utilise le préfixe
    }

    public List<String> getMessageList(String path, String... replacements) {
        if (!loaded) {
            return List.of(ChatColor.RED + "Error: messages.yml not loaded. Message list path: " + path);
        }
        List<String> messages = messagesConfig.getStringList(path);
        if (messages.isEmpty() && messagesConfig.contains(path)) { // Peut-être une seule ligne
            String singleLine = messagesConfig.getString(path);
            if (singleLine != null && !singleLine.isEmpty()) messages.add(singleLine);
        }

        if (messages.isEmpty()) {
            plugin.getLogger().warning("Missing message list in messages.yml for path: " + path);
            return List.of(ChatColor.RED + "Missing message list: " + path);
        }

        return messages.stream().map(line -> {
            String processedLine = line;
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    processedLine = processedLine.replace(replacements[i], replacements[i + 1]);
                }
            }
            return colorize(processedLine);
        }).collect(Collectors.toList());
    }


    public void sendMessage(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(getMessage(path, replacements));
    }

    public void sendMessageList(CommandSender sender, String path, String... replacements) {
        for (String line : getMessageList(path, replacements)) {
            sender.sendMessage(line); // Le préfixe est géré par getMessageList si le message commence par le préfixe dans le yml
        }
    }

    public void sendPlayerMessage(Player player, String path, String... replacements) {
        // Utiliser applyPlaceholders de MenuManager pour PAPI
        String rawMessage = messagesConfig.getString(path, DEFAULTS.getOrDefault(path, ChatColor.RED + "Missing message: " + path));
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                rawMessage = rawMessage.replace(replacements[i], replacements[i + 1]);
            }
        }
        String processedMessage = plugin.getMenuManager().applyPlaceholders(rawMessage, player, null);
        player.sendMessage(colorize(prefix + processedMessage));
    }

    public String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String generateProgressBar(long currentValue, long maxValue, int barLength) {
        if (maxValue <= 0)
            return String.valueOf(ChatColor.GRAY) + String.valueOf(ChatColor.STRIKETHROUGH) + "-".repeat(barLength);
        if (currentValue >= maxValue) return String.valueOf(ChatColor.GREEN) + "|".repeat(barLength);
        if (currentValue < 0) currentValue = 0;

        float percent = (float) currentValue / maxValue;
        int progressChars = (int) (barLength * percent);

        return ChatColor.GREEN + "|".repeat(progressChars) +
            ChatColor.GRAY + "|".repeat(barLength - progressChars);
    }
}
