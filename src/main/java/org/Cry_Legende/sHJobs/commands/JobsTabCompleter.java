package org.Cry_Legende.sHJobs.commands;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.jobs.JobTemplate;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.shop.ShopItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Complétion de /jobs (shop) et de /shjobs : les sous-commandes d'administration, filtrées par les permissions de
 * l'expéditeur.
 */
public class JobsTabCompleter implements TabCompleter {

    private static final String PERM = "shjobs.admin.";

    private final SHJobs plugin;
    private final List<String> customItemActions = Arrays.asList("save", "list", "give");
    private final List<String> expActions = Arrays.asList("give", "remove", "set");
    private final List<String> pointsActions = Arrays.asList("give", "remove", "set");
    private final List<String> listModes = Arrays.asList("jobs", "shop");

    public JobsTabCompleter(SHJobs plugin) {
        this.plugin = plugin;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> playerSubs = sender.hasPermission("shjobs.shop") ? List.of("shop") : List.of();

        // /jobs : seule la boutique se complète
        if (!command.getName().equalsIgnoreCase("shjobs")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerSubs, completions);
            }
            return completions;
        }

        List<String> allowed = AdminJobsCommand.allowedSubcommands(sender);
        if (allowed.isEmpty()) {
            // Aucun droit d'administration : /shjobs se comporte comme /jobs
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], playerSubs, completions);
            }
            return completions;
        }

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(allowed);
            if (allowed.contains("playerinfo")) {
                subs.add("info");
            }
            subs.addAll(playerSubs);
            StringUtil.copyPartialMatches(args[0], subs, completions);
        } else if (args.length > 1) {
            String subCommand = args[0].toLowerCase(Locale.ROOT);
            switch (subCommand) {
                case "list":
                    if (args.length == 2 && allowed.contains("list")) {
                        StringUtil.copyPartialMatches(args[1], listModes, completions);
                    }
                    break;

                case "customitem":
                    if (!allowed.contains("customitem")) {
                        break;
                    }
                    if (args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], customItemActions.stream()
                            .filter(action -> sender.hasPermission(PERM + "customitem." + action))
                            .collect(Collectors.toList()), completions);
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("save") && sender.hasPermission(PERM + "customitem.save")) {
                        completions.add("<item_id>"); // Suggère à l'admin de taper un ID
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("give") && sender.hasPermission(PERM + "customitem.give")) {
                        StringUtil.copyPartialMatches(args[2], plugin.getCustomItemManager().getItemIds(), completions);
                    } else if (args.length == 4 && args[1].equalsIgnoreCase("give") && sender.hasPermission(PERM + "customitem.give")) {
                        return null; // Noms de joueurs (Bukkit gère)
                    }
                    break;

                case "exp":
                    if (!allowed.contains("exp")) {
                        break;
                    }
                    if (args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], expActions.stream()
                            .filter(action -> sender.hasPermission(PERM + "exp." + action)).collect(Collectors.toList()), completions);
                    } else if (args.length == 3) return null; // Noms de joueurs (Bukkit gère)
                    else if (args.length == 4)
                        StringUtil.copyPartialMatches(args[3], Arrays.stream(JobType.values()).map(Enum::name).collect(Collectors.toList()), completions);
                    else if (args.length == 5) completions.add("<amount>");
                    break;
                case "level":
                    if (!allowed.contains("level")) {
                        break;
                    }
                    if (args.length == 2) StringUtil.copyPartialMatches(args[1], Collections.singletonList("set"), completions);
                    else if (args.length == 3) return null; // Noms de joueurs
                    else if (args.length == 4)
                        StringUtil.copyPartialMatches(args[3], Arrays.stream(JobType.values()).map(Enum::name).collect(Collectors.toList()), completions);
                    else if (args.length == 5) completions.add("<level>");
                    break;
                case "points":
                    if (!allowed.contains("points")) {
                        break;
                    }
                    if (args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], pointsActions.stream()
                            .filter(action -> sender.hasPermission(PERM + "points." + action)).collect(Collectors.toList()), completions);
                    } else if (args.length == 3) return null; // Noms de joueurs
                    else if (args.length == 4) completions.add("<amount>");
                    break;
                case "playerinfo":
                case "info":
                    if (allowed.contains("playerinfo") && args.length == 2) return null; // Noms de joueurs
                    break;
                case "job":
                    if (!allowed.contains("job")) {
                        break;
                    }
                    if (args.length == 2) StringUtil.copyPartialMatches(args[1], Collections.singletonList("change"), completions);
                    else if (args.length == 3 && args[1].equalsIgnoreCase("change"))
                        StringUtil.copyPartialMatches(args[2], Arrays.asList("1", "2", "3", "4", "5", "6"), completions);
                    else if (args.length == 4 && args[1].equalsIgnoreCase("change")) {
                        List<String> templateIds = plugin.getJobManager().getAllJobTemplates().stream().map(JobTemplate::getId).collect(Collectors.toList());
                        StringUtil.copyPartialMatches(args[3], templateIds, completions);
                    }
                    break;
                case "shopitem":
                    if (!allowed.contains("shopitem")) {
                        break;
                    }
                    if (args.length == 2) StringUtil.copyPartialMatches(args[1], Collections.singletonList("change"), completions);
                    else if (args.length == 3 && args[1].equalsIgnoreCase("change"))
                        StringUtil.copyPartialMatches(args[2], Arrays.asList("1", "2", "3", "4", "5", "6"), completions);
                    else if (args.length == 4 && args[1].equalsIgnoreCase("change")) {
                        List<String> shopItemIds = plugin.getShopManager().getAllShopItemTemplates().stream().map(ShopItem::getId).collect(Collectors.toList());
                        StringUtil.copyPartialMatches(args[3], shopItemIds, completions);
                    }
                    break;
                default:
                    break;
            }
        }
        Collections.sort(completions);
        return completions;
    }
}
