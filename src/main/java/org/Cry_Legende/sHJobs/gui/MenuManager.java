package org.Cry_Legende.sHJobs.gui;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.config.MenuConfig;
import org.Cry_Legende.sHJobs.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MenuManager {

    private final SHJobs plugin;
    private final MenuConfig menuConfig;

    public MenuManager(SHJobs plugin) {
        this.plugin = plugin;
        this.menuConfig = plugin.getMenuConfig();
    }

    public void loadMenus() {
        if (menuConfig.isLoaded()) {
            plugin.getLogger().info("[MenuManager] Menu configurations are loaded.");
        } else {
            plugin.getLogger().warning("[MenuManager] Menu configurations (menus.yml) NOT LOADED! GUIs might not work.");
        }
    }

    public void openJobsMenu(Player player) {
        plugin.getLogger().info("[MenuManager] Attempting to open JobsMenu for " + player.getName());
        ConfigurationSection menuData = menuConfig.getMenuConfiguration("main_jobs_menu");
        if (menuData == null) {
            plugin.getLogger().severe("[MenuManager] Configuration for 'main_jobs_menu' NOT FOUND in menus.yml for " + player.getName());
            player.sendMessage(plugin.getMessages().getMessage("menu.config_not_found", "%menu%", "Jobs"));
            return;
        }

        String title = plugin.getMessages().colorize(menuData.getString("title", "&cJobs Menu"));
        int size = menuData.getInt("size", 54);
        if (size % 9 != 0 || size > 54 || size < 9) {
            plugin.getLogger().warning("[MenuManager] Invalid size " + size + " for 'main_jobs_menu'. Must be a multiple of 9 (9-54). Defaulting to 54.");
            size = 54;
        }

        JobsMenu jobsMenu = new JobsMenu(plugin, title, size);
        Inventory inv = Bukkit.createInventory(jobsMenu, size, title);
        jobsMenu.setInventory(inv);
        plugin.getLogger().info("[MenuManager] JobsMenu inventory created for " + player.getName() + " with title: '" + title + "', size: " + size);

        try {
            jobsMenu.populateItems(player, menuData);
            plugin.getLogger().info("[MenuManager] JobsMenu items populated for " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MenuManager] EXCEPTION during JobsMenu.populateItems for " + player.getName(), e);
            player.sendMessage(plugin.getMessages().getMessage("menu.population_error", true, "%menu%", "Jobs"));
            player.closeInventory();
            return;
        }

        player.openInventory(inv);
        plugin.getLogger().info("[MenuManager] JobsMenu opened for " + player.getName());
    }

    public void openShopMenu(Player player) {
        plugin.getLogger().info("[MenuManager] Attempting to open ShopMenu for " + player.getName());
        ConfigurationSection menuData = menuConfig.getMenuConfiguration("shop_menu");
        if (menuData == null) {
            plugin.getLogger().severe("[MenuManager] Configuration for 'shop_menu' NOT FOUND in menus.yml for " + player.getName());
            player.sendMessage(plugin.getMessages().getMessage("menu.config_not_found", "%menu%", "Shop"));
            return;
        }

        String title = plugin.getMessages().colorize(menuData.getString("title", "&cShop Menu"));
        int size = menuData.getInt("size", 36);
        if (size % 9 != 0 || size > 54 || size < 9) {
            plugin.getLogger().warning("[MenuManager] Invalid size " + size + " for 'shop_menu'. Must be a multiple of 9 (9-54). Defaulting to 36.");
            size = 36;
        }

        ShopMenu shopMenu = new ShopMenu(plugin, title, size);
        Inventory inv = Bukkit.createInventory(shopMenu, size, title);
        shopMenu.setInventory(inv);
        plugin.getLogger().info("[MenuManager] ShopMenu inventory created for " + player.getName() + " with title: '" + title + "', size: " + size);


        try {
            shopMenu.populateItems(player, menuData);
            plugin.getLogger().info("[MenuManager] ShopMenu items populated for " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MenuManager] EXCEPTION during ShopMenu.populateItems for " + player.getName(), e);
            player.sendMessage(plugin.getMessages().getMessage("menu.population_error", true, "%menu%", "Shop"));
            player.closeInventory();
            return;
        }

        player.openInventory(inv);
        plugin.getLogger().info("[MenuManager] ShopMenu opened for " + player.getName());
    }

    public ItemStack buildItemFromConfig(ConfigurationSection itemSection, @Nullable Player player, @Nullable Map<String, String> globalPlaceholders) {
        if (itemSection == null) {
            return new ItemBuilder(Material.BARRIER).setName("&cError: Item section null").build();
        }

        String materialName = itemSection.getString("material", "STONE");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[MenuManager] Invalid material '" + materialName + "' in menu item config: " + itemSection.getCurrentPath() + ". Defaulting to BARRIER.");
            material = Material.BARRIER;
        }

        ItemBuilder builder = new ItemBuilder(material);

        String displayName = itemSection.getString("name");
        if (displayName != null) {
            builder.setName(applyPlaceholders(displayName, player, globalPlaceholders));
        }

        List<String> lore = itemSection.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> processedLore = lore.stream()
                .map(line -> applyPlaceholders(line, player, globalPlaceholders))
                .collect(Collectors.toList());
            builder.setLore(processedLore);
        }

        if (itemSection.contains("custom_model_data")) {
            builder.setCustomModelData(itemSection.getInt("custom_model_data"));
        }

        if (itemSection.getBoolean("glowing", false)) {
            builder.setGlowing(true);
        }

        if (material == Material.PLAYER_HEAD && itemSection.contains("skull_texture")) {
            builder.setSkullTexture(itemSection.getString("skull_texture"));
        } else if (material == Material.PLAYER_HEAD && itemSection.contains("skull_owner_name")) {
            String ownerName = itemSection.getString("skull_owner_name");
            if (player != null && "%player_name%".equals(ownerName)) {
                builder.setSkullOwner(player.getUniqueId());
            } else {
                builder.setSkullOwner(ownerName);
            }
        }

        return builder.build();
    }

    public String applyPlaceholders(String text, @Nullable Player player, @Nullable Map<String, String> additionalPlaceholders) {
        String result = text;
        if (result == null) return ""; // Éviter NPE si text est null

        if (additionalPlaceholders != null) {
            for (Map.Entry<String, String> entry : additionalPlaceholders.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result = result.replace(entry.getKey(), entry.getValue());
                }
            }
        }

        if (player != null && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[MenuManager] Error applying PAPI placeholders for text: " + text, e);
                // Continuer sans les placeholders PAPI si une erreur survient
            }
        }

        result = result.replace("%plugin_prefix%", plugin.getMessages().getPrefix());

        return plugin.getMessages().colorize(result);
    }
}
