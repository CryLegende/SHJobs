package org.Cry_Legende.sHJobs.commands;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.gui.JobsMenu;
import org.Cry_Legende.sHJobs.gui.ShopMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level; // Ajout pour le logging

public class JobsCommand implements CommandExecutor {

    private final SHJobs plugin;

    public JobsCommand(SHJobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessages().sendMessage(sender, "common.player_only");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            if (!player.hasPermission("shjobs.player.menu")) {
                plugin.getMessages().sendPlayerMessage(player, "common.no_permission");
                return true;
            }
            openJobsMenuForPlayer(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("shop")) {
            if (!player.hasPermission("shjobs.player.shop")) {
                plugin.getMessages().sendPlayerMessage(player, "common.no_permission");
                return true;
            }
            openShopMenuForPlayer(player);
            return true;
        }

        plugin.getMessages().sendPlayerMessage(player, "common.unknown_command_arg", "%command%", "/" + label + " " + String.join(" ", args));
        return true;
    }

    private void openJobsMenuForPlayer(Player player) {
        PlayerData cachedData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        plugin.getLogger().info("[JobsCommand] openJobsMenuForPlayer for " + player.getName() + ". CachedData is " + (cachedData == null ? "null" : "not null") + ". FullyLoaded: " + plugin.getPlayerCache().isPlayerDataFullyLoaded(player.getUniqueId()));

        if (cachedData != null && plugin.getPlayerCache().isPlayerDataFullyLoaded(player.getUniqueId())) {
            plugin.getLogger().info("[JobsCommand] Opening JobsMenu with cached and fully loaded data for " + player.getName());
            plugin.getMenuManager().openJobsMenu(player);
        } else {
            plugin.getLogger().info("[JobsCommand] PlayerData not fully loaded for " + player.getName() + ". Initiating load or waiting.");
            player.sendMessage(plugin.getMessages().getMessage("menu.loading_data", false));

            plugin.getPlayerCache().loadPlayerData(player.getUniqueId()).thenAcceptAsync(playerData -> {
                    plugin.getLogger().info("[JobsCommand] Callback for loadPlayerData (JobsMenu) executed for " + player.getName() + ". PlayerData is " + (playerData == null ? "null" : "not null"));
                    if (!player.isOnline()) {
                        plugin.getLogger().info("[JobsCommand] Player " + player.getName() + " logged off during data load (JobsMenu).");
                        return;
                    }
                    if (playerData != null) {
                        // markPlayerDataAsFullyLoaded est déjà appelé dans le callback de loadPlayerData
                        plugin.getLogger().info("[JobsCommand] PlayerData loaded successfully for " + player.getName() + ", opening JobsMenu.");
                        plugin.getMenuManager().openJobsMenu(player);
                    } else {
                        plugin.getLogger().warning("[JobsCommand] PlayerData is NULL for " + player.getName() + " after load attempt (JobsMenu).");
                        player.sendMessage(plugin.getMessages().getMessage("common.player_data_load_error_menu", true));
                    }
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "[JobsCommand] EXCEPTION in loadPlayerData callback (JobsMenu) for " + player.getName(), ex);
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getMessages().getMessage("common.player_data_load_error_critical", true));
                    }
                    return null;
                });
        }
    }

    private void openShopMenuForPlayer(Player player) {
        PlayerData cachedData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        plugin.getLogger().info("[JobsCommand] openShopMenuForPlayer for " + player.getName() + ". CachedData is " + (cachedData == null ? "null" : "not null") + ". FullyLoaded: " + plugin.getPlayerCache().isPlayerDataFullyLoaded(player.getUniqueId()));

        if (cachedData != null && plugin.getPlayerCache().isPlayerDataFullyLoaded(player.getUniqueId())) {
            plugin.getLogger().info("[JobsCommand] Opening ShopMenu with cached and fully loaded data for " + player.getName());
            plugin.getMenuManager().openShopMenu(player);
        } else {
            plugin.getLogger().info("[JobsCommand] PlayerData not fully loaded for " + player.getName() + ". Initiating load or waiting (ShopMenu).");
            player.sendMessage(plugin.getMessages().getMessage("menu.loading_data", false));
            plugin.getPlayerCache().loadPlayerData(player.getUniqueId()).thenAcceptAsync(playerData -> {
                    plugin.getLogger().info("[JobsCommand] Callback for loadPlayerData (ShopMenu) executed for " + player.getName() + ". PlayerData is " + (playerData == null ? "null" : "not null"));
                    if (!player.isOnline()) {
                        plugin.getLogger().info("[JobsCommand] Player " + player.getName() + " logged off during data load (ShopMenu).");
                        return;
                    }
                    if (playerData != null) {
                        plugin.getLogger().info("[JobsCommand] PlayerData loaded successfully for " + player.getName() + ", opening ShopMenu.");
                        plugin.getMenuManager().openShopMenu(player);
                    } else {
                        plugin.getLogger().warning("[JobsCommand] PlayerData is NULL for " + player.getName() + " after load attempt (ShopMenu).");
                        player.sendMessage(plugin.getMessages().getMessage("common.player_data_load_error_menu", true));
                    }
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "[JobsCommand] EXCEPTION in loadPlayerData callback (ShopMenu) for " + player.getName(), ex);
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getMessages().getMessage("common.player_data_load_error_critical", true));
                    }
                    return null;
                });
        }
    }
}
