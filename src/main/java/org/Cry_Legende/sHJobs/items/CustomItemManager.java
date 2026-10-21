package org.Cry_Legende.sHJobs.items;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class CustomItemManager {

    private final SHJobs plugin;
    private File itemsFile;
    private FileConfiguration itemsConfig;
    private final Map<String, ItemStack> customItemsCache = new HashMap<>();

    public CustomItemManager(SHJobs plugin) {
        this.plugin = plugin;
        setupItemsFile();
        loadItems();
    }

    private void setupItemsFile() {
        itemsFile = new File(plugin.getDataFolder(), "custom_items.yml");
        if (!itemsFile.exists()) {
            try {
                // Crée un fichier vide avec un commentaire d'en-tête
                itemsFile.createNewFile();
                FileConfiguration newConfig = YamlConfiguration.loadConfiguration(itemsFile);
                newConfig.save(itemsFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create custom_items.yml!", e);
            }
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }

    public void loadItems() {
        customItemsCache.clear();
        ConfigurationSection itemsSection = itemsConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                // Bukkit gère la désérialisation de l'ItemStack
                ItemStack item = itemsSection.getItemStack(key);
                if (item != null) {
                    customItemsCache.put(key, item);
                } else {
                    plugin.getLogger().warning("Failed to load custom item '" + key + "' from custom_items.yml. It might be corrupted or from an older Minecraft version.");
                }
            }
        }
        plugin.getLogger().info("Loaded " + customItemsCache.size() + " custom items from registry.");
    }

    /**
     * Récupère une copie de l'item custom depuis le cache.
     *
     * @param id L'ID de l'item à récupérer.
     * @return Un Optional contenant une copie de l'ItemStack, ou un Optional vide si non trouvé.
     */
    public Optional<ItemStack> getItem(String id) {
        // Retourne une copie pour éviter que le cache ne soit modifié accidentellement.
        return Optional.ofNullable(customItemsCache.get(id)).map(ItemStack::clone);
    }

    /**
     * Sauvegarde un ItemStack dans le fichier de registre et dans le cache.
     *
     * @param id   L'ID à assigner à l'item.
     * @param item L'ItemStack à sauvegarder.
     * @return true si la sauvegarde a réussi, false sinon.
     */
    public boolean saveItem(String id, ItemStack item) {
        if (id == null || id.isBlank() || item == null) {
            return false;
        }
        // Bukkit gère la sérialisation de l'ItemStack
        itemsConfig.set("items." + id, item);
        try {
            itemsConfig.save(itemsFile);
            customItemsCache.put(id, item.clone()); // Stocker une copie dans le cache
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save custom item '" + id + "' to custom_items.yml", e);
            return false;
        }
    }
}
