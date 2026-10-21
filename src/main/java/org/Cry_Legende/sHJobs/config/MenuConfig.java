package org.Cry_Legende.sHJobs.config;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class MenuConfig {

    private final SHJobs plugin;
    private File menuConfigFile;
    private FileConfiguration menuConfiguration;
    private boolean loaded = false;

    public MenuConfig(SHJobs plugin) {
        this.plugin = plugin;
        setupMenuFile();
        loadMenuConfig();
    }

    private void setupMenuFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        menuConfigFile = new File(plugin.getDataFolder(), "menus.yml");
        if (!menuConfigFile.exists()) {
            plugin.getLogger().info("menus.yml not found, creating default...");
            // Copier le fichier par défaut depuis les ressources du JAR
            // ou utiliser saveResource si vous avez une version par défaut dans votre JAR
            plugin.saveResource("menus.yml", false); // false pour ne pas remplacer s'il existe déjà (ne devrait pas arriver ici)
        }
    }

    public void loadMenuConfig() {
        menuConfiguration = new YamlConfiguration();
        try {
            menuConfiguration.load(menuConfigFile);
            loaded = true;
            plugin.getLogger().info("menus.yml loaded successfully.");
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load menus.yml!", e);
            loaded = false;
        }
    }

    public void reloadMenuConfig() {
        if (menuConfigFile == null) {
            setupMenuFile(); // Au cas où il aurait été supprimé ou non initialisé
        }
        loadMenuConfig();
        if (plugin.getMenuManager() != null) { // S'assurer que MenuManager existe
            plugin.getMenuManager().loadMenus(); // Informer MenuManager de recharger
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public FileConfiguration getRawConfiguration() {
        return menuConfiguration;
    }

    /**
     * Récupère la section de configuration pour un menu spécifique.
     *
     * @param menuKey La clé du menu dans menus.yml (ex: "main_jobs_menu").
     * @return La ConfigurationSection ou null si non trouvée.
     */
    public ConfigurationSection getMenuConfiguration(String menuKey) {
        if (!loaded || menuConfiguration == null) {
            plugin.getLogger().warning("Attempted to get menu configuration but menus.yml is not loaded.");
            return null;
        }
        return menuConfiguration.getConfigurationSection("menus." + menuKey);
    }

    // Vous pourriez ajouter ici des getters spécifiques si vous avez des paramètres globaux de menu
    // par exemple, un son de clic par défaut, etc.
}
