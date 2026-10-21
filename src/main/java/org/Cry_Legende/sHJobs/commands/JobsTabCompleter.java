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
import java.util.stream.Collectors;

public class JobsTabCompleter implements TabCompleter {

    private final SHJobs plugin;
    // --- NOUVEAUTÉ ---
    private final List<String> adminSubCommands = Arrays.asList("reload", "exp", "level", "points", "playerinfo", "info", "job", "shopitem", "resetdaily", "customitem");
    private final List<String> customItemActions = Collections.singletonList("save");
    // --- FIN NOUVEAUTÉ ---
    private final List<String> expActions = Arrays.asList("give", "remove", "set");
    private final List<String> levelActions = Collections.singletonList("set");
    private final List<String> pointsActions = Arrays.asList("give", "remove", "set");
    private final List<String> jobManagementActions = Collections.singletonList("change");
    private final List<String> shopManagementActions = Collections.singletonList("change");


    public JobsTabCompleter(SHJobs plugin) {
        this.plugin = plugin;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("shjobsadmin")) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], adminSubCommands, completions);
        } else if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                // --- NOUVEAUTÉ ---
                case "customitem":
                    if (args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], customItemActions, completions);
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("save")) {
                        completions.add("<item_id>"); // Suggère à l'admin de taper un ID
                    }
                    break;
                // --- FIN NOUVEAUTÉ ---

                case "exp":
                    if (args.length == 2) StringUtil.copyPartialMatches(args[1], expActions, completions);
                    else if (args.length == 3) return null; // Noms de joueurs (Bukkit gère)
                    else if (args.length == 4)
                        StringUtil.copyPartialMatches(args[3], Arrays.stream(JobType.values()).map(Enum::name).collect(Collectors.toList()), completions);
                    else if (args.length == 5) completions.add("<amount>");
                    break;
                case "level":
                    if (args.length == 2) StringUtil.copyPartialMatches(args[1], levelActions, completions);
                    else if (args.length == 3) return null; // Noms de joueurs
                    else if (args.length == 4)
                        StringUtil.copyPartialMatches(args[3], Arrays.stream(JobType.values()).map(Enum::name).collect(Collectors.toList()), completions);
                    else if (args.length == 5) completions.add("<level>");
                    break;
                case "points":
                    if (args.length == 2) StringUtil.copyPartialMatches(args[1], pointsActions, completions);
                    else if (args.length == 3) return null; // Noms de joueurs
                    else if (args.length == 4) completions.add("<amount>");
                    break;
                case "playerinfo":
                case "info":
                    if (args.length == 2) return null; // Noms de joueurs
                    break;
                case "job":
                    if (args.length == 2) StringUtil.copyPartialMatches(args[1], jobManagementActions, completions);
                    else if (args.length == 3 && args[1].equalsIgnoreCase("change"))
                        StringUtil.copyPartialMatches(args[2], Arrays.asList("1", "2", "3", "4", "5", "6"), completions);
                    else if (args.length == 4 && args[1].equalsIgnoreCase("change")) {
                        List<String> templateIds = plugin.getJobManager().getAllJobTemplates().stream().map(JobTemplate::getId).collect(Collectors.toList());
                        StringUtil.copyPartialMatches(args[3], templateIds, completions);
                    }
                    break;
                case "shopitem":
                    if (args.length == 2) StringUtil.copyPartialMatches(args[1], shopManagementActions, completions);
                    else if (args.length == 3 && args[1].equalsIgnoreCase("change"))
                        StringUtil.copyPartialMatches(args[2], Arrays.asList("1", "2", "3", "4", "5", "6"), completions);
                    else if (args.length == 4 && args[1].equalsIgnoreCase("change")) {
                        List<String> shopItemIds = plugin.getShopManager().getAllShopItemTemplates().stream().map(ShopItem::getId).collect(Collectors.toList());
                        StringUtil.copyPartialMatches(args[3], shopItemIds, completions);
                    }
                    break;
            }
        }
        Collections.sort(completions);
        return completions;
    }
}
