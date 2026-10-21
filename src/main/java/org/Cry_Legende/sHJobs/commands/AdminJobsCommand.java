package org.Cry_Legende.sHJobs.commands;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.jobs.JobTemplate;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.shop.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack; // --- NOUVEAUTÉ ---
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminJobsCommand implements CommandExecutor {

    private final SHJobs plugin;

    public AdminJobsCommand(SHJobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shjobs.admin.base")) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("shjobs.admin.reload")) {
                    plugin.getMessages().sendMessage(sender, "common.no_permission");
                    return true;
                }
                plugin.reloadPluginLogic();
                plugin.getMessages().sendMessage(sender, "admin.reloaded");
                break;

            // --- NOUVEAUTÉ ---
            case "customitem":
                if (!sender.hasPermission("shjobs.admin.customitem")) {
                    plugin.getMessages().sendMessage(sender, "common.no_permission");
                    return true;
                }
                handleCustomItemCommand(sender, label, args);
                break;
            // --- FIN NOUVEAUTÉ ---

            case "exp":
                handleExpCommand(sender, label, args);
                break;

            case "level":
                handleLevelCommand(sender, label, args);
                break;

            case "points":
                handlePointsCommand(sender, label, args);
                break;

            case "playerinfo":
            case "info":
                handlePlayerInfoCommand(sender, label, args);
                break;

            case "job":
                handleJobManagementCommand(sender, label, args);
                break;

            case "shopitem":
                handleShopItemManagementCommand(sender, label, args);
                break;

            case "resetdaily":
                if (!sender.hasPermission("shjobs.admin.resetdaily")) {
                    plugin.getMessages().sendMessage(sender, "common.no_permission");
                    return true;
                }
                plugin.getJobManager().selectNewDailyJobs();
                plugin.getShopManager().selectNewDailyShopItems();
                plugin.getMessages().sendMessage(sender, "admin.daily_reset_forced");
                break;

            default:
                plugin.getMessages().sendMessage(sender, "common.unknown_command_arg", "%command%", "/" + label + " " + subCommand);
                sendAdminHelp(sender, label);
                break;
        }
        return true;
    }

    private void sendAdminHelp(CommandSender sender, String label) {
        plugin.getMessages().sendMessage(sender, "admin.help.header", "%label%", label);
        plugin.getMessages().sendMessage(sender, "admin.help.reload");
        plugin.getMessages().sendMessage(sender, "admin.help.exp");
        plugin.getMessages().sendMessage(sender, "admin.help.level");
        plugin.getMessages().sendMessage(sender, "admin.help.points");
        plugin.getMessages().sendMessage(sender, "admin.help.playerinfo");
        plugin.getMessages().sendMessage(sender, "admin.help.job_change");
        plugin.getMessages().sendMessage(sender, "admin.help.shopitem_change");
        plugin.getMessages().sendMessage(sender, "admin.help.resetdaily");
        // --- NOUVEAUTÉ ---
        // Pensez à ajouter la ligne suivante à votre fichier messages.yml
        // admin.help.customitem: "&e/%label% customitem save <id> &7- Sauvegarde l'item en main."
        plugin.getMessages().sendMessage(sender, "admin.help.customitem", "%label%", label);
        // --- FIN NOUVEAUTÉ ---
        plugin.getMessages().sendMessage(sender, "admin.help.footer");
    }

    // --- NOUVEAUTÉ : Méthode de gestion pour /sja customitem ---
    private void handleCustomItemCommand(CommandSender sender, String label, String[] args) {
        // Usage: /sja customitem save <id>
        if (!(sender instanceof Player)) {
            plugin.getMessages().sendMessage(sender, "common.player_only");
            return;
        }

        if (args.length < 3 || !args[1].equalsIgnoreCase("save")) {
            // Pensez à ajouter ce message à messages.yml
            // admin.customitem.usage: "&7&l« &c&lSH &8&l» &7Usage: &c/%label% customitem save <item_id>"
            plugin.getMessages().sendMessage(sender, "admin.customitem.usage", "%label%", label);
            return;
        }

        if (!sender.hasPermission("shjobs.admin.customitem.save")) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return;
        }

        Player player = (Player) sender;
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType().isAir()) {
            // Pensez à ajouter ce message à messages.yml
            // admin.customitem.no_item_in_hand: "&7&l« &c&lSH &8&l» &7Vous devez tenir un item dans votre main."
            plugin.getMessages().sendMessage(sender, "admin.customitem.no_item_in_hand");
            return;
        }

        String itemId = args[2];
        if (plugin.getCustomItemManager().saveItem(itemId, itemInHand)) {
            // Pensez à ajouter ce message à messages.yml
            // admin.customitem.save_success: "&7&l« &a&lSH &8&l» &7Item sauvegardé avec l'ID '&a%id%&7' avec succès."
            plugin.getMessages().sendMessage(sender, "admin.customitem.save_success", "%id%", itemId);
        } else {
            // Pensez à ajouter ce message à messages.yml
            // admin.customitem.save_failure: "&7&l« &c&lSH &8&l» &7Erreur lors de la sauvegarde de l'item avec l'ID '&c%id%&7'."
            plugin.getMessages().sendMessage(sender, "admin.customitem.save_failure", "%id%", itemId);
        }
    }
    // --- FIN NOUVEAUTÉ ---


    // --- EXP Sub-commands (inchangé) ---
    private void handleExpCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 5) {
            plugin.getMessages().sendMessage(sender, "admin.exp.usage", "%label%", label);
            return;
        }
        String action = args[1].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        Optional<JobType> jobTypeOpt = JobType.fromString(args[3]);
        long amount;

        if (!jobTypeOpt.isPresent()) {
            plugin.getMessages().sendMessage(sender, "common.invalid_job_type", "%type%", args[3]);
            return;
        }
        try {
            amount = Long.parseLong(args[4]);
            if (amount < 0) throw new NumberFormatException("Amount cannot be negative.");
        } catch (NumberFormatException e) {
            plugin.getMessages().sendMessage(sender, "common.invalid_amount", "%amount%", args[4]);
            return;
        }

        JobType jobType = jobTypeOpt.get();
        plugin.getPlayerCache().loadPlayerData(target.getUniqueId()).thenAcceptAsync(playerData -> {
            if (playerData == null) {
                plugin.getMessages().sendMessage(sender, "common.player_not_found_or_data_error", "%player%", target.getName());
                return;
            }

            switch (action) {
                case "give":
                    if (!sender.hasPermission("shjobs.admin.exp.give")) {
                        plugin.getMessages().sendMessage(sender, "common.no_permission");
                        return;
                    }
                    playerData.addExperience(jobType, amount);
                    plugin.getMessages().sendMessage(sender, "admin.exp.give.success",
                        "%player%", target.getName(), "%amount%", String.valueOf(amount), "%job_type%", jobType.getDisplayName());
                    break;
                case "remove":
                    if (!sender.hasPermission("shjobs.admin.exp.remove")) {
                        plugin.getMessages().sendMessage(sender, "common.no_permission");
                        return;
                    }
                    playerData.removeExperience(jobType, amount);
                    plugin.getMessages().sendMessage(sender, "admin.exp.remove.success",
                        "%player%", target.getName(), "%amount%", String.valueOf(amount), "%job_type%", jobType.getDisplayName());
                    break;
                case "set":
                    if (!sender.hasPermission("shjobs.admin.exp.set")) {
                        plugin.getMessages().sendMessage(sender, "common.no_permission");
                        return;
                    }
                    playerData.setExperience(jobType, amount);
                    playerData.recalculateLevel(jobType);
                    plugin.getMessages().sendMessage(sender, "admin.exp.set.success",
                        "%player%", target.getName(), "%amount%", String.valueOf(amount), "%job_type%", jobType.getDisplayName());
                    break;
                default:
                    plugin.getMessages().sendMessage(sender, "admin.exp.usage", "%label%", label);
                    return;
            }
            if (!target.isOnline()) {
                plugin.getPlayerCache().savePlayerData(target.getUniqueId())
                    .thenRun(() -> plugin.getPlayerCache().unloadPlayerData(target.getUniqueId()));
            } else {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    plugin.getMessages().sendPlayerMessage(onlineTarget, "player.exp_changed_by_admin",
                        "%job_type%", jobType.getDisplayName(), "%action%", action);
                }
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // --- LEVEL Sub-commands (inchangé) ---
    private void handleLevelCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 5 || !args[1].equalsIgnoreCase("set")) {
            plugin.getMessages().sendMessage(sender, "admin.level.usage", "%label%", label);
            return;
        }
        if (!sender.hasPermission("shjobs.admin.level.set")) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        Optional<JobType> jobTypeOpt = JobType.fromString(args[3]);
        int level;

        if (!jobTypeOpt.isPresent()) {
            plugin.getMessages().sendMessage(sender, "common.invalid_job_type", "%type%", args[3]);
            return;
        }
        try {
            level = Integer.parseInt(args[4]);
            if (level < 1) throw new NumberFormatException("Level must be at least 1.");
        } catch (NumberFormatException e) {
            plugin.getMessages().sendMessage(sender, "common.invalid_level_format", "%level%", args[4]);
            return;
        }

        JobType jobType = jobTypeOpt.get();
        if (level > jobType.getMaxLevel()) {
            plugin.getMessages().sendMessage(sender, "admin.level.set.too_high", "%level%", String.valueOf(level), "%max_level%", String.valueOf(jobType.getMaxLevel()));
            level = jobType.getMaxLevel();
        }
        final int finalLevel = level;

        plugin.getPlayerCache().loadPlayerData(target.getUniqueId()).thenAcceptAsync(playerData -> {
            if (playerData == null) {
                plugin.getMessages().sendMessage(sender, "common.player_not_found_or_data_error", "%player%", target.getName());
                return;
            }
            playerData.setLevel(jobType, finalLevel);
            plugin.getMessages().sendMessage(sender, "admin.level.set.success",
                "%player%", target.getName(), "%level%", String.valueOf(finalLevel), "%job_type%", jobType.getDisplayName());

            if (!target.isOnline()) {
                plugin.getPlayerCache().savePlayerData(target.getUniqueId())
                    .thenRun(() -> plugin.getPlayerCache().unloadPlayerData(target.getUniqueId()));
            } else {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    plugin.getMessages().sendPlayerMessage(onlineTarget, "player.level_changed_by_admin",
                        "%job_type%", jobType.getDisplayName(), "%level%", String.valueOf(finalLevel));
                }
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // --- POINTS Sub-commands (inchangé) ---
    private void handlePointsCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            plugin.getMessages().sendMessage(sender, "admin.points.usage", "%label%", label);
            return;
        }
        String action = args[1].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        long amount;

        try {
            amount = Long.parseLong(args[3]);
            if (amount < 0) throw new NumberFormatException("Amount cannot be negative for give/set.");
        } catch (NumberFormatException e) {
            plugin.getMessages().sendMessage(sender, "common.invalid_amount", "%amount%", args[3]);
            return;
        }

        plugin.getPlayerCache().loadPlayerData(target.getUniqueId()).thenAcceptAsync(playerData -> {
            if (playerData == null) {
                plugin.getMessages().sendMessage(sender, "common.player_not_found_or_data_error", "%player%", target.getName());
                return;
            }

            switch (action) {
                case "give":
                    if (!sender.hasPermission("shjobs.admin.points.give")) {
                        plugin.getMessages().sendMessage(sender, "common.no_permission");
                        return;
                    }
                    playerData.addJobPoints(amount);
                    plugin.getMessages().sendMessage(sender, "admin.points.give.success",
                        "%player%", target.getName(), "%amount%", String.valueOf(amount));
                    break;
                case "remove":
                    if (!sender.hasPermission("shjobs.admin.points.remove")) {
                        plugin.getMessages().sendMessage(sender, "common.no_permission");
                        return;
                    }
                    if (!playerData.removeJobPoints(amount)) {
                        plugin.getMessages().sendMessage(sender, "admin.points.remove.not_enough", "%player%", target.getName());
                    } else {
                        plugin.getMessages().sendMessage(sender, "admin.points.remove.success",
                            "%player%", target.getName(), "%amount%", String.valueOf(amount));
                    }
                    break;
                case "set":
                    if (!sender.hasPermission("shjobs.admin.points.set")) {
                        plugin.getMessages().sendMessage(sender, "common.no_permission");
                        return;
                    }
                    playerData.setJobPoints(amount);
                    plugin.getMessages().sendMessage(sender, "admin.points.set.success",
                        "%player%", target.getName(), "%amount%", String.valueOf(amount));
                    break;
                default:
                    plugin.getMessages().sendMessage(sender, "admin.points.usage", "%label%", label);
                    return;
            }
            if (!target.isOnline()) {
                plugin.getPlayerCache().savePlayerData(target.getUniqueId())
                    .thenRun(() -> plugin.getPlayerCache().unloadPlayerData(target.getUniqueId()));
            } else {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    plugin.getMessages().sendPlayerMessage(onlineTarget, "player.points_changed_by_admin", "%action%", action);
                }
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // --- PLAYERINFO Sub-command (inchangé) ---
    private void handlePlayerInfoCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().sendMessage(sender, "admin.playerinfo.usage", "%label%", label);
            return;
        }
        if (!sender.hasPermission("shjobs.admin.playerinfo")) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        plugin.getPlayerCache().loadPlayerData(target.getUniqueId()).thenAcceptAsync(playerData -> {
            if (playerData == null) {
                plugin.getMessages().sendMessage(sender, "common.player_not_found_or_data_error", "%player%", target.getName());
                return;
            }

            plugin.getMessages().sendMessage(sender, "admin.playerinfo.header", "%player%", target.getName());
            plugin.getMessages().sendMessage(sender, "admin.playerinfo.points", "%points%", String.valueOf(playerData.getJobPoints()));
            for (JobType jobType : JobType.values()) {
                long currentExp = playerData.getExperience(jobType);
                int currentLevel = playerData.getLevel(jobType);
                long expToNext = playerData.getExpToNextLevel(jobType);
                String expStr = (expToNext == Long.MAX_VALUE) ? "MAX" : String.valueOf(expToNext - playerData.getCurrentLevelProgressExp(jobType));
                String progressExp = playerData.getCurrentLevelProgressExp(jobType) + "/" + jobType.getExpForLevel(currentLevel + 1);
                if (currentLevel >= jobType.getMaxLevel()) progressExp = "MAX";


                plugin.getMessages().sendMessage(sender, "admin.playerinfo.job_entry",
                    "%job_type%", jobType.getDisplayName(),
                    "%level%", String.valueOf(currentLevel),
                    "%exp%", String.valueOf(currentExp),
                    "%exp_progress%", progressExp);
            }
            plugin.getMessages().sendMessage(sender, "admin.playerinfo.footer");
            if (!target.isOnline()) {
                plugin.getPlayerCache().unloadPlayerData(target.getUniqueId());
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // --- JOB MANAGEMENT Sub-command (inchangé) ---
    private void handleJobManagementCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("change")) {
            plugin.getMessages().sendMessage(sender, "admin.job_change.usage", "%label%", label);
            return;
        }
        if (!sender.hasPermission("shjobs.admin.job.change")) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return;
        }

        int slot;
        try {
            slot = Integer.parseInt(args[2]);
            if (slot < 1 || slot > 6) throw new NumberFormatException("Slot must be between 1 and 6.");
        } catch (NumberFormatException e) {
            plugin.getMessages().sendMessage(sender, "common.invalid_slot", "%slot%", args[2]);
            return;
        }

        String templateId = (args.length > 3) ? args[3] : null;

        if (templateId != null && plugin.getJobManager().getJobTemplate(templateId).isEmpty()) {
            plugin.getMessages().sendMessage(sender, "common.invalid_job_template_id", "%id%", templateId);
            return;
        }

        if (plugin.getJobManager().changeDailyJob(slot - 1, templateId)) {
            plugin.getMessages().sendMessage(sender, "admin.job_change.success",
                "%slot%", String.valueOf(slot),
                "%template_id%", (templateId != null ? templateId : "aléatoire"));
        } else {
            plugin.getMessages().sendMessage(sender, "admin.job_change.failure", "%slot%", String.valueOf(slot));
        }
    }

    // --- SHOP ITEM MANAGEMENT Sub-command (inchangé) ---
    private void handleShopItemManagementCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("change")) {
            plugin.getMessages().sendMessage(sender, "admin.shopitem_change.usage", "%label%", label);
            return;
        }
        if (!sender.hasPermission("shjobs.admin.shop.changeitem")) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return;
        }

        int slot;
        try {
            slot = Integer.parseInt(args[2]);
            if (slot < 1 || slot > 6) throw new NumberFormatException("Slot must be between 1 and 6.");
        } catch (NumberFormatException e) {
            plugin.getMessages().sendMessage(sender, "common.invalid_slot_shop", "%slot%", args[2]);
            return;
        }

        String itemId = (args.length > 3) ? args[3] : null;

        if (itemId != null && plugin.getShopManager().getShopItemTemplate(itemId).isEmpty()) {
            plugin.getMessages().sendMessage(sender, "common.invalid_shop_item_id", "%id%", itemId);
            return;
        }

        if (plugin.getShopManager().changeShopItem(slot - 1, itemId)) {
            plugin.getMessages().sendMessage(sender, "admin.shopitem_change.success",
                "%slot%", String.valueOf(slot),
                "%item_id%", (itemId != null ? itemId : "aléatoire"));
        } else {
            plugin.getMessages().sendMessage(sender, "admin.shopitem_change.failure", "%slot%", String.valueOf(slot));
        }
    }
}
