package org.Cry_Legende.sHJobs.gui;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.jobs.DailyJob;
import org.Cry_Legende.sHJobs.jobs.JobTemplate;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.utils.ItemBuilder;
import org.Cry_Legende.sHJobs.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level; // Ajout pour le logging
import java.util.stream.Collectors;

public class JobsMenu extends CustomMenuHolder {

    public JobsMenu(SHJobs plugin, String title, int size) {
        super(plugin, title, size);
    }

    public void populateItems(Player player, ConfigurationSection menuConfig) {
        plugin.getLogger().info("[JobsMenu] Populating items for " + player.getName() + ". Menu config path: " + menuConfig.getCurrentPath());
        if (inventory == null) {
            plugin.getLogger().severe("[JobsMenu] Inventory is null in populateItems for " + player.getName() + ". Aborting.");
            return;
        }
        inventory.clear();

        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            plugin.getLogger().warning("[JobsMenu] PlayerData is NULL for " + player.getName() + " when populating JobsMenu.");
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Error: Could not load your player data for the menu.");
            return;
        }

        Map<String, String> globalPlaceholders = new HashMap<>();
        globalPlaceholders.put("%time_until_reset%", TimeUtil.formatDuration(plugin.getConfigManager().getDailyResetTime()));
        globalPlaceholders.put("%shjobs_player_points%", String.valueOf(playerData.getJobPoints()));

        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("[JobsMenu] 'items' section not found in menu configuration: " + menuConfig.getCurrentPath());
            return;
        }

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemCfg = itemsSection.getConfigurationSection(key);
            if (itemCfg == null) {
                plugin.getLogger().warning("[JobsMenu] Item configuration for key '" + key + "' is null. Skipping.");
                continue;
            }

            plugin.getLogger().fine("[JobsMenu] Processing item config: " + key + " at path " + itemCfg.getCurrentPath());

            String itemType = itemCfg.getString("type", "STATIC");

            List<Integer> itemSlots = new ArrayList<>();
            if (itemCfg.isInt("slot")) {
                itemSlots.add(itemCfg.getInt("slot"));
            } else if (itemCfg.isList("slots")) {
                itemSlots.addAll(itemCfg.getIntegerList("slots"));
            }

            if (itemSlots.isEmpty()) {
                plugin.getLogger().warning("[JobsMenu] No slot(s) defined for item '" + key + "'. Skipping.");
                continue;
            }

            for (int slot : itemSlots) {
                if (slot < 0 || slot >= size) {
                    plugin.getLogger().warning("[JobsMenu] Invalid slot " + slot + " for item '" + key + "'. Max size: " + (size - 1) + ". Skipping this slot.");
                    continue;
                }
                plugin.getLogger().fine("[JobsMenu] Setting item for key '" + key + "' in slot " + slot + " with type " + itemType);

                try { // Try-catch autour de la création de chaque item
                    switch (itemType.toUpperCase()) {
                        case "DAILY_JOB_1":
                        case "DAILY_JOB_2":
                        case "DAILY_JOB_3":
                        case "DAILY_JOB_4":
                        case "DAILY_JOB_5":
                        case "DAILY_JOB_6":
                            int jobIndex = Integer.parseInt(itemType.substring(itemType.length() - 1)) - 1;
                            List<String> dailyJobIds = plugin.getJobManager().getCurrentDailyJobTemplateIds();
                            if (jobIndex < dailyJobIds.size() && dailyJobIds.get(jobIndex) != null) {
                                String templateId = dailyJobIds.get(jobIndex);
                                Optional<JobTemplate> templateOpt = plugin.getJobManager().getJobTemplate(templateId);
                                Optional<DailyJob> dailyJobOpt = playerData.getDailyJobByTemplateId(templateId);

                                if (templateOpt.isPresent() && dailyJobOpt.isPresent()) {
                                    inventory.setItem(slot, createDailyJobItem(player, templateOpt.get(), dailyJobOpt.get(), itemCfg, globalPlaceholders));
                                } else {
                                    inventory.setItem(slot, createPlaceholderItem(player, itemCfg.getConfigurationSection("placeholder_config"), "Emplacement de Job Vide", globalPlaceholders));
                                }
                            } else {
                                inventory.setItem(slot, createPlaceholderItem(player, itemCfg.getConfigurationSection("placeholder_config"), "Job Non Disponible", globalPlaceholders));
                            }
                            break;

                        case "JOB_TYPE_BUCHERON":
                        case "JOB_TYPE_MINEUR":
                        case "JOB_TYPE_AGRICULTEUR":
                        case "JOB_TYPE_CHASSEUR":
                        case "JOB_TYPE_ARTISAN":
                        case "JOB_TYPE_PECHEUR":
                            JobType jobTypeEnum = JobType.fromString(itemType.substring("JOB_TYPE_".length())).orElse(null);
                            if (jobTypeEnum != null) {
                                inventory.setItem(slot, createJobTypeItem(player, playerData, jobTypeEnum, itemCfg, globalPlaceholders));
                            } else {
                                plugin.getLogger().warning("[JobsMenu] Invalid JobType enum for item type: " + itemType);
                                inventory.setItem(slot, createPlaceholderItem(player, itemCfg, "Métier Invalide", globalPlaceholders));
                            }
                            break;

                        case "INFO_ITEM":
                            inventory.setItem(slot, plugin.getMenuManager().buildItemFromConfig(itemCfg, player, globalPlaceholders));
                            break;

                        case "FILLER": // Explicitement gérer FILLER comme STATIC
                        case "STATIC":
                        default:
                            inventory.setItem(slot, plugin.getMenuManager().buildItemFromConfig(itemCfg, player, globalPlaceholders));
                            break;
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[JobsMenu] EXCEPTION while creating/setting item for key '" + key + "' in slot " + slot, e);
                }
            }
        }
        plugin.getLogger().info("[JobsMenu] Finished populating items for " + player.getName());
    }

    private ItemStack createPlaceholderItem(Player player, ConfigurationSection placeholderSpecificConfig, String defaultName, Map<String, String> globalPlaceholders) {
        Material mat = Material.BARRIER;
        String name = "&c" + defaultName;
        List<String> lore = new ArrayList<>();

        if (placeholderSpecificConfig != null) {
            mat = Material.matchMaterial(placeholderSpecificConfig.getString("material", "BARRIER"));
            if (mat == null) mat = Material.BARRIER;
            name = placeholderSpecificConfig.getString("name", name);
            lore = placeholderSpecificConfig.getStringList("lore");
        }

        ItemBuilder builder = new ItemBuilder(mat);
        builder.setName(plugin.getMenuManager().applyPlaceholders(name, player, globalPlaceholders));
        if (!lore.isEmpty()) {
            builder.setLore(lore.stream()
                .map(l -> plugin.getMenuManager().applyPlaceholders(l, player, globalPlaceholders))
                .collect(Collectors.toList()));
        }
        if (mat == Material.PLAYER_HEAD && placeholderSpecificConfig != null) {
            if (Objects.equals(placeholderSpecificConfig.getString("skull_owner_name"), "%player_name%") && player != null) {
                builder.setSkullOwner(player.getUniqueId());
            } else if (placeholderSpecificConfig.contains("skull_texture")) {
                builder.setSkullTexture(placeholderSpecificConfig.getString("skull_texture"));
            }
        }
        return builder.build();
    }

    private ItemStack createDailyJobItem(Player player, JobTemplate template, DailyJob dailyJob,
                                         ConfigurationSection itemConfig, Map<String, String> globalPlaceholders) {
        Map<String, String> placeholders = new HashMap<>(globalPlaceholders);
        placeholders.put("%job_name%", template.getDisplayName());
        placeholders.put("%job_type_name%", template.getJobType().getDisplayName());
        placeholders.put("%job_exp_reward%", String.valueOf(template.getRewardJobExp()));
        placeholders.put("%job_points_reward%", String.valueOf(template.getRewardJobPoints()));

        ItemBuilder itemBuilder = new ItemBuilder(plugin.getMenuManager().buildItemFromConfig(itemConfig, player, placeholders));

        List<String> baseLoreFromConfig = itemConfig.getStringList("lore");
        List<String> finalLore = new ArrayList<>();

        for (String line : baseLoreFromConfig) {
            finalLore.add(plugin.getMenuManager().applyPlaceholders(line, player, placeholders));
        }

        if (!finalLore.isEmpty() && !finalLore.get(finalLore.size() - 1).trim().isEmpty()) {
            finalLore.add(plugin.getMenuManager().applyPlaceholders("", player, placeholders)); // Ligne vide via apply pour colorisation
        }

        String objectivesHeader = itemConfig.getString("objectives_lore_header"); // Pas de fallback ici, si null, pas d'header
        if (objectivesHeader != null && !objectivesHeader.isEmpty()) {
            finalLore.add(plugin.getMenuManager().applyPlaceholders(objectivesHeader, player, placeholders));
        }

        for (org.Cry_Legende.sHJobs.jobs.JobObjective obj : template.getObjectives()) {
            int progress = dailyJob.getProgress(obj.getId());
            int total = obj.getAmount();
            String statusColor = progress >= total ? "&a" : "&e";

            String objectiveLineFormat = itemConfig.getString("objective_line_format", "&7- %obj_display_name% &f(%obj_progress%/%obj_total%)");

            Map<String, String> objectivePlaceholders = new HashMap<>(placeholders);
            objectivePlaceholders.put("%obj_display_name%", obj.getObjectiveDisplayName());
            objectivePlaceholders.put("%obj_progress%", statusColor + progress);
            objectivePlaceholders.put("%obj_total%", String.valueOf(total));

            finalLore.add(plugin.getMenuManager().applyPlaceholders(objectiveLineFormat, player, objectivePlaceholders));
        }

        if (!template.getObjectives().isEmpty() && (finalLore.isEmpty() || !finalLore.get(finalLore.size() - 1).trim().isEmpty())) {
            finalLore.add(plugin.getMenuManager().applyPlaceholders("", player, placeholders));
        }

        List<String> rewardsLore = itemConfig.getStringList("rewards_lore");
        if (!rewardsLore.isEmpty()) {
            for (String rewardLine : rewardsLore) {
                finalLore.add(plugin.getMenuManager().applyPlaceholders(rewardLine, player, placeholders));
            }
        }

        itemBuilder.setLore(finalLore);

        if (dailyJob.isCompleted()) {
            String completedNameFormat = itemConfig.getString("completed_name_format", "&a✔ %job_name% (Terminé)");
            itemBuilder.setName(plugin.getMenuManager().applyPlaceholders(completedNameFormat.replace("%job_name%", template.getDisplayName()), player, globalPlaceholders));

            List<String> completedLoreAdd = itemConfig.getStringList("completed_lore_add");
            if (!completedLoreAdd.isEmpty()) {
                completedLoreAdd.forEach(line -> itemBuilder.addLoreLine(plugin.getMenuManager().applyPlaceholders(line, player, globalPlaceholders)));
            }
            if (itemConfig.getBoolean("completed_glowing", true)) {
                itemBuilder.setGlowing(true);
            }
        }

        return itemBuilder.build();
    }

    private ItemStack createJobTypeItem(Player player, PlayerData playerData, JobType jobType, ConfigurationSection itemConfig, Map<String, String> globalPlaceholders) {
        Map<String, String> placeholders = new HashMap<>(globalPlaceholders);

        ItemBuilder itemBuilder = new ItemBuilder(jobType.getIcon()); // Icône du JobType
        // Nom : celui de itemConfig, sinon celui de JobType
        itemBuilder.setName(plugin.getMenuManager().applyPlaceholders(itemConfig.getString("name", jobType.getDisplayName()), player, placeholders));

        placeholders.put("%job_type_name%", jobType.getDisplayName());
        placeholders.put("%job_level%", String.valueOf(playerData.getLevel(jobType)));
        placeholders.put("%job_exp%", String.valueOf(playerData.getExperience(jobType)));

        long currentExpInLevel = playerData.getCurrentLevelProgressExp(jobType);
        long expForNextLevel = jobType.getExpForLevel(playerData.getLevel(jobType) + 1);

        if (playerData.getLevel(jobType) >= jobType.getMaxLevel()) {
            placeholders.put("%job_exp_progress%", plugin.getMessages().getMessage("jobs.exp_max_level", false));
            placeholders.put("%job_exp_bar%", plugin.getMessages().getMessage("jobs.exp_bar_full", false));
            placeholders.put("%job_exp_percentage%", "100");
        } else {
            placeholders.put("%job_exp_progress%", currentExpInLevel + "/" + expForNextLevel);
            double percentage = (expForNextLevel > 0 && currentExpInLevel >= 0) ? ((double) currentExpInLevel / expForNextLevel) * 100 : 0;
            placeholders.put("%job_exp_percentage%", String.format("%.1f", percentage));
            placeholders.put("%job_exp_bar%", plugin.getMessages().generateProgressBar(currentExpInLevel, expForNextLevel, 20));
        }

        List<String> lore = itemConfig.getStringList("lore");
        if (!lore.isEmpty()) {
            itemBuilder.setLore(lore.stream()
                .map(line -> plugin.getMenuManager().applyPlaceholders(line, player, placeholders))
                .collect(Collectors.toList()));
        }
        if (itemConfig.contains("custom_model_data")) {
            itemBuilder.setCustomModelData(itemConfig.getInt("custom_model_data"));
        }
        if (itemConfig.getBoolean("glowing", false)) {
            itemBuilder.setGlowing(true);
        }
        return itemBuilder.build();
    }

    public void handleClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        plugin.getLogger().fine("[JobsMenu] handleClick for " + player.getName() + ", slot: " + slot + ", item: " + (clickedItem != null ? clickedItem.getType() : "null"));
        ConfigurationSection menuConfig = plugin.getMenuConfig().getMenuConfiguration("main_jobs_menu");
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
                plugin.getLogger().fine("[JobsMenu] Matched item config key: " + key + " for slot " + slot);
                List<String> actions = itemCfg.getStringList("actions." + clickType.name().toLowerCase());
                if (actions.isEmpty() && (clickType.isLeftClick() || clickType.isRightClick())) {
                    actions = itemCfg.getStringList("actions.any_click");
                }
                if (actions.isEmpty()) { // Fallback pour "click" si les autres sont vides
                    actions = itemCfg.getStringList("actions.click");
                }

                if (!actions.isEmpty()) {
                    plugin.getLogger().fine("[JobsMenu] Executing actions for key " + key + ": " + String.join(", ", actions));
                    for (String actionString : actions) {
                        executeAction(player, actionString);
                    }
                } else {
                    plugin.getLogger().fine("[JobsMenu] No actions defined for key " + key + " and click type " + clickType.name());
                }
                return;
            }
        }
        plugin.getLogger().fine("[JobsMenu] No item configuration matched slot " + slot + " for click handling.");
    }

    private void executeAction(Player player, String actionString) {
        if (actionString == null || actionString.isEmpty()) return;
        plugin.getLogger().fine("[JobsMenu] executeAction: " + actionString);
        String action = plugin.getMenuManager().applyPlaceholders(actionString, player, null); // Appliquer PAPI ici aussi

        if (action.startsWith("[close]")) {
            player.closeInventory();
        } else if (action.startsWith("[command]")) {
            String cmd = action.substring("[command]".length()).trim();
            plugin.getLogger().info("[JobsMenu] Player " + player.getName() + " executing command from menu: " + cmd);
            player.performCommand(cmd);
        } else if (action.startsWith("[consolecommand]")) {
            String cmd = action.substring("[consolecommand]".length()).trim();
            plugin.getLogger().info("[JobsMenu] Console executing command from menu (triggered by " + player.getName() + "): " + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else if (action.startsWith("[message]")) {
            String msg = action.substring("[message]".length()).trim();
            player.sendMessage(msg); // Déjà colorisé et processé par applyPlaceholders
        } else if (action.startsWith("[openmenu]")) {
            String menuToOpen = action.substring("[openmenu]".length()).trim();
            plugin.getLogger().info("[JobsMenu] Player " + player.getName() + " opening menu from menu: " + menuToOpen);
            if (menuToOpen.equalsIgnoreCase("shop_menu")) {
                plugin.getMenuManager().openShopMenu(player);
            } else if (menuToOpen.equalsIgnoreCase("main_jobs_menu")) { // Pourrait être un bouton refresh/home
                plugin.getMenuManager().openJobsMenu(player);
            } else {
                plugin.getLogger().warning("[JobsMenu] Unknown menu to open: " + menuToOpen);
            }
        } else if (action.startsWith("[sound]")) {
            try {
                String[] parts = action.substring("[sound]".length()).trim().split(":");
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0].toUpperCase());
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                plugin.getLogger().warning("[JobsMenu] Invalid sound action format: " + action + " Error: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("[JobsMenu] Unknown action string: " + action);
        }
    }

    public void updateCountdownItem(Player player) {
        if (inventory == null) return; // Sécurité
        ConfigurationSection menuConfig = plugin.getMenuConfig().getMenuConfiguration("main_jobs_menu");
        if (menuConfig == null) return;
        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection == null) return;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%time_until_reset%", TimeUtil.formatDuration(plugin.getConfigManager().getDailyResetTime()));
        PlayerData pData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (pData != null) {
            placeholders.put("%shjobs_player_points%", String.valueOf(pData.getJobPoints()));
        } else {
            placeholders.put("%shjobs_player_points%", "N/A"); // Fallback si PlayerData est null
        }

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemCfg = itemsSection.getConfigurationSection(key);
            if (itemCfg != null && "INFO_ITEM".equalsIgnoreCase(itemCfg.getString("type")) && itemCfg.isInt("slot")) {
                int slot = itemCfg.getInt("slot");
                if (slot >= 0 && slot < inventory.getSize()) {
                    ItemStack currentItem = inventory.getItem(slot);
                    ItemStack updatedItem = plugin.getMenuManager().buildItemFromConfig(itemCfg, player, placeholders);
                    if (currentItem == null ||
                        !Objects.equals(currentItem.getType(), updatedItem.getType()) ||
                        (currentItem.hasItemMeta() != updatedItem.hasItemMeta()) || // Vérifier si l'un a meta et pas l'autre
                        (currentItem.hasItemMeta() && updatedItem.hasItemMeta() && (
                            !Objects.equals(currentItem.getItemMeta().getDisplayName(), updatedItem.getItemMeta().getDisplayName()) ||
                                !Objects.equals(currentItem.getItemMeta().getLore(), updatedItem.getItemMeta().getLore())
                        ))
                    ) {
                        inventory.setItem(slot, updatedItem);
                    }
                    return; // Supposons un seul INFO_ITEM
                }
            }
        }
    }
}
