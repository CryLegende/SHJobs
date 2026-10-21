package org.Cry_Legende.sHJobs.gui;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.shop.ShopItem;
import org.Cry_Legende.sHJobs.shop.ShopManager;
import org.Cry_Legende.sHJobs.utils.ItemBuilder;
import org.Cry_Legende.sHJobs.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ShopMenu extends CustomMenuHolder {

    public ShopMenu(SHJobs plugin, String title, int size) {
        super(plugin, title, size);
    }

    public void populateItems(Player player, ConfigurationSection menuConfig) {
        if (inventory == null) {
            plugin.getLogger().severe("Inventory is null in ShopMenu.populateItems. This should not happen.");
            return;
        }
        inventory.clear();

        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Error: Could not load your player data.");
            return;
        }

        Map<String, String> globalPlaceholders = new HashMap<>();
        globalPlaceholders.put("%time_until_reset%", TimeUtil.formatDuration(plugin.getConfigManager().getDailyResetTime()));
        globalPlaceholders.put("%player_points%", String.valueOf(playerData.getJobPoints()));

        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemCfg = itemsSection.getConfigurationSection(key);
                if (itemCfg == null) continue;

                String itemType = itemCfg.getString("type", "STATIC");

                List<Integer> itemSlots = new ArrayList<>();
                if (itemCfg.isInt("slot")) {
                    itemSlots.add(itemCfg.getInt("slot"));
                } else if (itemCfg.isList("slots")) {
                    itemSlots.addAll(itemCfg.getIntegerList("slots"));
                }

                if (itemSlots.isEmpty()) continue;

                for (int slot : itemSlots) {
                    if (slot < 0 || slot >= size) continue;

                    if (itemType.startsWith("SHOP_ITEM_")) {
                        try {
                            int shopItemIndex = Integer.parseInt(itemType.substring("SHOP_ITEM_".length())) - 1;
                            List<String> dailyShopItemIds = plugin.getShopManager().getCurrentDailyShopItemIds();

                            if (shopItemIndex >= 0 && shopItemIndex < dailyShopItemIds.size() && dailyShopItemIds.get(shopItemIndex) != null) {
                                String shopItemId = dailyShopItemIds.get(shopItemIndex);
                                Optional<ShopItem> shopItemOpt = plugin.getShopManager().getShopItemTemplate(shopItemId);
                                if (shopItemOpt.isPresent()) {
                                    inventory.setItem(slot, createShopDisplayItem(player, playerData, shopItemOpt.get()));
                                } else {
                                    inventory.setItem(slot, createPlaceholderShopItem(player, itemCfg.getConfigurationSection("placeholder_config"), "Article Indisponible", globalPlaceholders));
                                }
                            } else {
                                inventory.setItem(slot, createPlaceholderShopItem(player, itemCfg.getConfigurationSection("placeholder_config"), "Emplacement Vide", globalPlaceholders));
                            }
                        } catch (NumberFormatException e) {
                            inventory.setItem(slot, plugin.getMenuManager().buildItemFromConfig(itemCfg, player, globalPlaceholders));
                        }
                    } else {
                        inventory.setItem(slot, plugin.getMenuManager().buildItemFromConfig(itemCfg, player, globalPlaceholders));
                    }
                }
            }
        }
    }

    /**
     * Crée l'ItemStack à afficher dans la boutique.
     * Cette version corrigée utilise getPurchaseStatus pour éviter d'envoyer des messages au joueur lors de la création du menu.
     */
    private ItemStack createShopDisplayItem(Player player, PlayerData playerData, ShopItem shopItem) {

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player_points%", String.valueOf(playerData.getJobPoints()));
        placeholders.put("%item_cost%", String.valueOf(shopItem.getCost()));
        if (shopItem.isSellable()) {
            placeholders.put("%sell_points%", String.valueOf(shopItem.getSellPoints()));
        }
        shopItem.getRequiredJobType().ifPresent(jobType -> {
            placeholders.put("%required_job_level%", String.valueOf(shopItem.getRequiredJobLevel()));
            placeholders.put("%required_job_name%", jobType.getDisplayName());
        });

        ItemBuilder itemBuilder = new ItemBuilder(shopItem.getMaterial());
        itemBuilder.setName(plugin.getMenuManager().applyPlaceholders(shopItem.getDisplayName(), player, placeholders));

        List<String> finalLore = new ArrayList<>();
        for (String line : shopItem.getLore()) {
            if (line.contains("%sell_points%") && !shopItem.isSellable()) {
                continue;
            }
            finalLore.add(plugin.getMenuManager().applyPlaceholders(line, player, placeholders));
        }
        itemBuilder.setLore(finalLore);

        if (shopItem.getCustomModelData() != 0) {
            itemBuilder.setCustomModelData(shopItem.getCustomModelData());
        }

        // --- CORRECTION : Utilise la méthode silencieuse getPurchaseStatus pour l'affichage ---
        ShopManager.PurchaseStatus status = plugin.getShopManager().getPurchaseStatus(player, shopItem);

        if (status != ShopManager.PurchaseStatus.CAN_PURCHASE) {
            // Ajouter un feedback visuel dans le lore si l'achat est impossible
            String errorLine = "";
            if (status == ShopManager.PurchaseStatus.NOT_ENOUGH_POINTS) {
                errorLine = plugin.getMessages().getMessage("shop.lore_status_not_enough_points", false);
            } else if (status == ShopManager.PurchaseStatus.LEVEL_TOO_LOW) {
                errorLine = plugin.getMessages().getMessage("shop.lore_status_level_required", false);
            }

            // On peut ajouter une ligne vide avant le message d'erreur pour une meilleure lisibilité.
            // Vérifions d'abord si la dernière ligne du lore n'est pas déjà vide.
            List<String> currentLore = itemBuilder.build().getItemMeta().getLore();
            if (currentLore != null && !currentLore.isEmpty() && !currentLore.get(currentLore.size() - 1).trim().isEmpty()) {
                itemBuilder.addLoreLine("");
            }
            itemBuilder.addLoreLine(errorLine);
        }

        return itemBuilder.build();
    }

    @Override
    public void handleClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        ConfigurationSection menuConfig = plugin.getMenuConfig().getMenuConfiguration("shop_menu");
        if (menuConfig == null) return;
        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemCfg = itemsSection.getConfigurationSection(key);
            if (itemCfg == null) continue;

            boolean slotMatch = false;
            if (itemCfg.isInt("slot") && itemCfg.getInt("slot") == slot) {
                slotMatch = true;
            } else if (itemCfg.isList("slots") && itemCfg.getIntegerList("slots").contains(slot)) {
                slotMatch = true;
            }

            if (slotMatch) {
                String itemType = itemCfg.getString("type", "STATIC");
                if (itemType.startsWith("SHOP_ITEM_")) {
                    try {
                        int shopItemIndex = Integer.parseInt(itemType.substring("SHOP_ITEM_".length())) - 1;
                        List<String> dailyShopItemIds = plugin.getShopManager().getCurrentDailyShopItemIds();
                        if (shopItemIndex >= 0 && shopItemIndex < dailyShopItemIds.size() && dailyShopItemIds.get(shopItemIndex) != null) {
                            String shopItemId = dailyShopItemIds.get(shopItemIndex);

                            plugin.getShopManager().getShopItemTemplate(shopItemId).ifPresent(itemToInteract -> {
                                if (clickType.isShiftClick() && clickType.isLeftClick()) {
                                    plugin.getShopManager().processPurchase(player, itemToInteract, 64);
                                } else if (clickType.isLeftClick()) {
                                    plugin.getShopManager().processPurchase(player, itemToInteract);
                                } else if (clickType.isShiftClick() && clickType.isRightClick()) {
                                    plugin.getShopManager().processSellAll(player, itemToInteract);
                                } else if (clickType.isRightClick()) {
                                    plugin.getShopManager().processSell(player, itemToInteract);
                                }
                            });
                        }
                    } catch (NumberFormatException e) { /* Ignoré */ }
                    return;
                } else {
                    List<String> actions = itemCfg.getStringList("actions." + clickType.name().toLowerCase());
                    if (actions.isEmpty()) {
                        actions = itemCfg.getStringList("actions.click");
                    }
                    if (!actions.isEmpty()) {
                        for (String actionString : actions) {
                            executeAction(player, actionString);
                        }
                    }
                }
                return;
            }
        }
    }

    private ItemStack createPlaceholderShopItem(Player player, ConfigurationSection placeholderSpecificConfig, String defaultName, Map<String, String> globalPlaceholders) {
        Material mat = Material.GRAY_STAINED_GLASS_PANE;
        String name = "&7" + defaultName;
        List<String> lore = List.of("&cCet article n'est pas disponible actuellement.");
        if (placeholderSpecificConfig != null) {
            mat = Material.matchMaterial(placeholderSpecificConfig.getString("material", "GRAY_STAINED_GLASS_PANE"));
            if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;
            name = placeholderSpecificConfig.getString("name", name);
            if (placeholderSpecificConfig.contains("lore")) {
                lore = placeholderSpecificConfig.getStringList("lore");
            }
        }
        ItemBuilder builder = new ItemBuilder(mat);
        builder.setName(plugin.getMenuManager().applyPlaceholders(name, player, globalPlaceholders));
        if (!lore.isEmpty()) {
            builder.setLore(lore.stream()
                .map(l -> plugin.getMenuManager().applyPlaceholders(l, player, globalPlaceholders))
                .collect(Collectors.toList()));
        }
        return builder.build();
    }

    private void executeAction(Player player, String actionString) {
        if (actionString == null || actionString.isEmpty()) return;
        String action = plugin.getMenuManager().applyPlaceholders(actionString, player, null);
        if (action.startsWith("[close]")) {
            player.closeInventory();
        } else if (action.startsWith("[command]")) {
            player.performCommand(action.substring("[command]".length()).trim());
        } else if (action.startsWith("[consolecommand]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substring("[consolecommand]".length()).trim());
        } else if (action.startsWith("[message]")) {
            player.sendMessage(plugin.getMessages().colorize(action.substring("[message]".length()).trim()));
        } else if (action.startsWith("[openmenu]")) {
            String menuToOpen = action.substring("[openmenu]".length()).trim();
            if (menuToOpen.equalsIgnoreCase("main_jobs_menu")) {
                plugin.getMenuManager().openJobsMenu(player);
            }
        }
    }

    public void updateCountdownItem(Player player) {
        ConfigurationSection menuConfig = plugin.getMenuConfig().getMenuConfiguration("shop_menu");
        if (menuConfig == null || inventory == null) return;
        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection == null) return;

        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) return;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%time_until_reset%", TimeUtil.formatDuration(plugin.getConfigManager().getDailyResetTime()));
        placeholders.put("%player_points%", String.valueOf(playerData.getJobPoints()));

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemCfg = itemsSection.getConfigurationSection(key);
            if (itemCfg != null && "INFO_ITEM".equalsIgnoreCase(itemCfg.getString("type")) && itemCfg.isInt("slot")) {
                int slot = itemCfg.getInt("slot");
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, plugin.getMenuManager().buildItemFromConfig(itemCfg, player, placeholders));
                    break;
                }
            }
        }
    }
}
