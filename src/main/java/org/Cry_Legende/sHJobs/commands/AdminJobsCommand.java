package org.Cry_Legende.sHJobs.commands;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.jobs.JobTemplate;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.shop.ShopItem;
import org.Cry_Legende.sHJobs.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * /shjobs (alias shjobsadmin, sja) : administration de SHJobs. Un expéditeur qui n'a aucun droit d'administration
 * obtient exactement ce que donnait /jobs (menu, boutique), comme avant que /shjobs ne devienne la commande d'admin.
 */
public class AdminJobsCommand implements CommandExecutor {

    private static final String PERM = "shjobs.admin.";

    /** Sous-commandes d'administration dans l'ordre d'affichage de la ligne d'usage, avec les permissions qui les ouvrent. */
    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "exp", "level", "points", "playerinfo",
        "job", "shopitem", "resetdaily", "customitem", "import");

    private final SHJobs plugin;
    private final JobsCommand jobsCommand;

    public AdminJobsCommand(SHJobs plugin, JobsCommand jobsCommand) {
        this.plugin = plugin;
        this.jobsCommand = jobsCommand;
    }

    /** Permissions dont une seule suffit pour utiliser la sous-commande. */
    static List<String> permissionsOf(String sub) {
        return switch (sub) {
            case "reload" -> List.of(PERM + "reload");
            case "list" -> List.of(PERM + "list");
            case "exp" -> List.of(PERM + "exp.give", PERM + "exp.remove", PERM + "exp.set");
            case "level" -> List.of(PERM + "level.set");
            case "points" -> List.of(PERM + "points.give", PERM + "points.remove", PERM + "points.set");
            case "playerinfo", "info" -> List.of(PERM + "playerinfo");
            case "job" -> List.of(PERM + "job.change");
            case "shopitem" -> List.of(PERM + "shop.changeitem");
            case "resetdaily" -> List.of(PERM + "resetdaily");
            case "customitem" -> List.of(PERM + "customitem");
            case "import" -> List.of(PERM + "import");
            default -> List.of();
        };
    }

    /** Sous-commandes d'administration que l'expéditeur a le droit d'utiliser. */
    static List<String> allowedSubcommands(CommandSender sender) {
        List<String> allowed = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (permissionsOf(sub).stream().anyMatch(sender::hasPermission)) {
                allowed.add(sub);
            }
        }
        return allowed;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> allowed = allowedSubcommands(sender);
        String subCommand = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        // Aucun droit d'administration, ou fonction joueur : le comportement de /jobs
        if (allowed.isEmpty() || subCommand.equals("shop")) {
            return jobsCommand.onCommand(sender, command, label, args);
        }

        String canonical = subCommand.equals("info") ? "playerinfo" : subCommand;
        if (!allowed.contains(canonical)) {
            plugin.getMessages().sendMessage(sender, "admin.usage", "%subs%", String.join("&7|&e", allowed));
            return true;
        }

        switch (canonical) {
            case "reload":
                handleReload(sender);
                break;

            case "list":
                handleListCommand(sender, label, args);
                break;

            case "customitem":
                handleCustomItemCommand(sender, label, args);
                break;

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
                handlePlayerInfoCommand(sender, label, args);
                break;

            case "job":
                handleJobManagementCommand(sender, label, args);
                break;

            case "shopitem":
                handleShopItemManagementCommand(sender, label, args);
                break;

            case "resetdaily":
                if (plugin.getNetwork() != null) {
                    // Avec SHNetwork, une seule rotation pour tous les serveurs : elle est prise en base puis publiée
                    plugin.getNetwork().roll(true);
                } else {
                    plugin.getJobManager().selectNewDailyJobs();
                    plugin.getShopManager().selectNewDailyShopItems();
                }
                plugin.getMessages().sendMessage(sender, "admin.daily_reset_forced");
                break;

            case "import":
                if (plugin.getNetwork() == null) {
                    plugin.getMessages().sendMessage(sender, "admin.import.not_network");
                    return true;
                }
                plugin.getMessages().sendMessage(sender, "admin.import.started");
                plugin.getNetwork().importLocal(sender);
                break;

            default:
                plugin.getMessages().sendMessage(sender, "admin.usage", "%subs%", String.join("&7|&e", allowed));
                break;
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        SHJobs.ReloadResult result = plugin.reloadPluginLogic();
        if (result.failed()) {
            plugin.getMessages().sendMessage(sender, "admin.reload_failed", "%file%", result.file(), "%error%", result.error());
            return;
        }
        plugin.getMessages().sendMessage(sender, "admin.reloaded");
        if (result.databaseFailed()) {
            plugin.getMessages().sendMessage(sender, "admin.reload_database_failed");
        }
        if (result.networkChanged()) {
            plugin.getMessages().sendMessage(sender, "admin.reload_network_restart");
        }
    }

    /**
     * Avec SHNetwork : si le joueur visé est connecté sur un autre serveur, ses données sont en mémoire là-bas et ne
     * seront écrites en base qu'à son départ. La commande est donc lancée sur son serveur, avec la permission de
     * l'émetteur vérifiée ici. Vrai si elle a été envoyée.
     */
    private boolean sendToPlayerServer(CommandSender sender, String targetName, String permission, String[] args) {
        if (plugin.getNetwork() == null || !sender.hasPermission(permission)) {
            return false;
        }
        Optional<String> server = plugin.getNetwork().otherServerOf(targetName);
        if (server.isEmpty()) {
            return false;
        }
        plugin.getNetwork().runFor(targetName, "shjobsadmin " + String.join(" ", args));
        plugin.getMessages().sendMessage(sender, "admin.remote_sent", "%player%", targetName, "%server%", server.get());
        return true;
    }

    private static boolean isGiveRemoveSet(String action) {
        return action.equals("give") || action.equals("remove") || action.equals("set");
    }

    // --- LIST Sub-command ---
    private void handleListCommand(CommandSender sender, String label, String[] args) {
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (mode) {
            case "" -> {
                plugin.getMessages().sendMessage(sender, "admin.list.header",
                    "%time%", TimeUtil.formatDuration(plugin.getConfigManager().getDailyResetTime()));
                sendRotation(sender, "admin.list.job_line", plugin.getJobManager().getCurrentDailyJobTemplateIds(),
                    id -> plugin.getJobManager().getJobTemplate(id).map(JobTemplate::getDisplayName).orElse(null));
                sendRotation(sender, "admin.list.shop_line", plugin.getShopManager().getCurrentDailyShopItemIds(),
                    id -> plugin.getShopManager().getShopItemTemplate(id).map(ShopItem::getDisplayName).orElse(null));
            }
            case "jobs" -> sendIds(sender, "admin.list.jobs_header", plugin.getJobManager().getAllJobTemplates().stream()
                .map(JobTemplate::getId).sorted().collect(Collectors.toList()));
            case "shop" -> sendIds(sender, "admin.list.shop_header", plugin.getShopManager().getAllShopItemTemplates().stream()
                .map(ShopItem::getId).sorted().collect(Collectors.toList()));
            default -> plugin.getMessages().sendMessage(sender, "admin.list.usage", "%label%", label);
        }
    }

    private void sendRotation(CommandSender sender, String lineKey, List<String> ids, Function<String, String> nameOf) {
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String name = id == null ? null : nameOf.apply(id);
            plugin.getMessages().sendMessage(sender, lineKey,
                "%slot%", String.valueOf(i + 1),
                "%id%", id == null ? plugin.getMessages().getMessage("admin.list.empty", false) : id,
                "%name%", name == null ? "" : plugin.getConfigManager().plainText(name));
        }
    }

    /** Une en-tête avec le nombre d'ids, puis les ids par lignes de huit. */
    private void sendIds(CommandSender sender, String headerKey, List<String> ids) {
        plugin.getMessages().sendMessage(sender, headerKey, "%count%", String.valueOf(ids.size()));
        for (int i = 0; i < ids.size(); i += 8) {
            plugin.getMessages().sendMessage(sender, "admin.list.ids_line",
                "%ids%", String.join("&8, &7", ids.subList(i, Math.min(ids.size(), i + 8))));
        }
    }

    private void handleCustomItemCommand(CommandSender sender, String label, String[] args) {
        // Usage: /shjobs customitem <save <id>|list|give <id> [joueur]>
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "save" -> {
                if (!sender.hasPermission(PERM + "customitem.save")) {
                    plugin.getMessages().sendMessage(sender, "common.no_permission");
                } else if (!(sender instanceof Player player)) {
                    plugin.getMessages().sendMessage(sender, "common.player_only");
                } else if (args.length < 3) {
                    plugin.getMessages().sendMessage(sender, "admin.customitem.usage", "%label%", label);
                } else {
                    saveCustomItem(player, args[2]);
                }
            }
            case "list" -> {
                if (!sender.hasPermission(PERM + "customitem.list")) {
                    plugin.getMessages().sendMessage(sender, "common.no_permission");
                    return;
                }
                List<String> ids = plugin.getCustomItemManager().getItemIds();
                plugin.getMessages().sendMessage(sender, "admin.customitem.list.header", "%count%", String.valueOf(ids.size()));
                for (int i = 0; i < ids.size(); i += 8) {
                    plugin.getMessages().sendMessage(sender, "admin.list.ids_line",
                        "%ids%", String.join("&8, &7", ids.subList(i, Math.min(ids.size(), i + 8))));
                }
            }
            case "give" -> {
                if (!sender.hasPermission(PERM + "customitem.give")) {
                    plugin.getMessages().sendMessage(sender, "common.no_permission");
                } else {
                    giveCustomItem(sender, label, args);
                }
            }
            default -> plugin.getMessages().sendMessage(sender, "admin.customitem.usage", "%label%", label);
        }
    }

    private void saveCustomItem(Player player, String itemId) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType().isAir()) {
            plugin.getMessages().sendMessage(player, "admin.customitem.no_item_in_hand");
            return;
        }

        if (plugin.getCustomItemManager().saveItem(itemId, itemInHand)) {
            plugin.getMessages().sendMessage(player, "admin.customitem.save_success", "%id%", itemId);
        } else {
            plugin.getMessages().sendMessage(player, "admin.customitem.save_failure", "%id%", itemId);
        }
    }

    /** /shjobs customitem give <id> [joueur] : une copie de l'objet enregistré, à soi ou au joueur connecté nommé. */
    private void giveCustomItem(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            plugin.getMessages().sendMessage(sender, "admin.customitem.usage", "%label%", label);
            return;
        }
        Player target;
        if (args.length > 3) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                plugin.getMessages().sendMessage(sender, "admin.customitem.give.player_offline", "%player%", args[3]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.getMessages().sendMessage(sender, "admin.customitem.give.player_required");
            return;
        }
        Optional<ItemStack> item = plugin.getCustomItemManager().getItem(args[2]);
        if (item.isEmpty()) {
            plugin.getMessages().sendMessage(sender, "admin.customitem.give.unknown", "%id%", args[2]);
            return;
        }
        for (ItemStack left : target.getInventory().addItem(item.get()).values()) {
            target.getWorld().dropItem(target.getLocation(), left);
        }
        plugin.getMessages().sendMessage(sender, "admin.customitem.give.success", "%id%", args[2], "%player%", target.getName());
    }


    // --- EXP Sub-commands ---
    private void handleExpCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 5) {
            plugin.getMessages().sendMessage(sender, "admin.exp.usage", "%label%", label);
            return;
        }
        String action = args[1].toLowerCase();
        if (!isGiveRemoveSet(action)) {
            plugin.getMessages().sendMessage(sender, "admin.exp.usage", "%label%", label);
            return;
        }
        if (!sender.hasPermission(PERM + "exp." + action)) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return;
        }
        if (sendToPlayerServer(sender, args[2], PERM + "exp." + action, args)) {
            return;
        }
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
                    playerData.addExperience(jobType, amount);
                    plugin.getMessages().sendMessage(sender, "admin.exp.give.success",
                        "%player%", target.getName(), "%amount%", String.valueOf(amount), "%job_type%", jobType.getDisplayName());
                    break;
                case "remove":
                    playerData.removeExperience(jobType, amount);
                    plugin.getMessages().sendMessage(sender, "admin.exp.remove.success",
                        "%player%", target.getName(), "%amount%", String.valueOf(amount), "%job_type%", jobType.getDisplayName());
                    break;
                case "set":
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

    // --- LEVEL Sub-commands ---
    private void handleLevelCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 5 || !args[1].equalsIgnoreCase("set")) {
            plugin.getMessages().sendMessage(sender, "admin.level.usage", "%label%", label);
            return;
        }
        if (!sender.hasPermission(PERM + "level.set")) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return;
        }

        if (sendToPlayerServer(sender, args[2], PERM + "level.set", args)) {
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

    // --- POINTS Sub-commands ---
    private void handlePointsCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            plugin.getMessages().sendMessage(sender, "admin.points.usage", "%label%", label);
            return;
        }
        String action = args[1].toLowerCase();
        if (!isGiveRemoveSet(action)) {
            plugin.getMessages().sendMessage(sender, "admin.points.usage", "%label%", label);
            return;
        }
        if (!sender.hasPermission(PERM + "points." + action)) {
            plugin.getMessages().sendMessage(sender, "common.no_permission");
            return;
        }
        if (sendToPlayerServer(sender, args[2], PERM + "points." + action, args)) {
            return;
        }
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
                    playerData.addJobPoints(amount);
                    plugin.getMessages().sendMessage(sender, "admin.points.give.success",
                        "%player%", target.getName(), "%amount%", String.valueOf(amount));
                    break;
                case "remove":
                    if (!playerData.removeJobPoints(amount)) {
                        plugin.getMessages().sendMessage(sender, "admin.points.remove.not_enough", "%player%", target.getName());
                    } else {
                        plugin.getMessages().sendMessage(sender, "admin.points.remove.success",
                            "%player%", target.getName(), "%amount%", String.valueOf(amount));
                    }
                    break;
                case "set":
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

    // --- PLAYERINFO Sub-command ---
    private void handlePlayerInfoCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().sendMessage(sender, "admin.playerinfo.usage", "%label%", label);
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

    // --- JOB MANAGEMENT Sub-command ---
    private void handleJobManagementCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("change")) {
            plugin.getMessages().sendMessage(sender, "admin.job_change.usage", "%label%", label);
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
                "%template_id%", (templateId != null ? templateId : plugin.getMessages().getMessage("common.random", false)));
        } else {
            plugin.getMessages().sendMessage(sender, "admin.job_change.failure", "%slot%", String.valueOf(slot));
        }
    }

    // --- SHOP ITEM MANAGEMENT Sub-command ---
    private void handleShopItemManagementCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("change")) {
            plugin.getMessages().sendMessage(sender, "admin.shopitem_change.usage", "%label%", label);
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
                "%item_id%", (itemId != null ? itemId : plugin.getMessages().getMessage("common.random", false)));
        } else {
            plugin.getMessages().sendMessage(sender, "admin.shopitem_change.failure", "%slot%", String.valueOf(slot));
        }
    }
}
