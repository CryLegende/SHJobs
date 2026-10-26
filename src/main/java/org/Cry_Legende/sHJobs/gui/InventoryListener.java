package org.Cry_Legende.sHJobs.gui; // Placé dans le package gui car il est lié aux inventaires

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.jobs.DailyJob;
import org.Cry_Legende.sHJobs.jobs.JobAction; // Pour SMELT
import org.Cry_Legende.sHJobs.jobs.JobObjective;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class InventoryListener implements Listener {

    private final SHJobs plugin;

    public InventoryListener(SHJobs plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory(); // L'inventaire où le clic a eu lieu
        Inventory topInventory = event.getView().getTopInventory(); // L'inventaire supérieur (le menu custom, le four, etc.)

        if (clickedInventory == null) return;

        // Gérer les clics dans les menus du plugin (JobsMenu, ShopMenu)
        // On vérifie si l'inventaire supérieur a un Holder qui est une instance de nos classes de menu
        InventoryHolder holder = topInventory.getHolder();

        if (holder instanceof JobsMenu || holder instanceof ShopMenu) {
            event.setCancelled(true); // Annuler l'interaction par défaut (prise d'item, etc.)

            if (clickedInventory.equals(topInventory)) { // Clic dans le menu custom lui-même
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                    return;
                }

                // Déléguer la gestion du clic au MenuManager ou directement au Holder
                if (holder instanceof JobsMenu) {
                    ((JobsMenu) holder).handleClick(player, event.getSlot(), clickedItem, event.getClick());
                } else if (holder instanceof ShopMenu) {
                    ((ShopMenu) holder).handleClick(player, event.getSlot(), clickedItem, event.getClick());
                }
            }
            // Si on veut autoriser certaines interactions avec l'inventaire du joueur pendant qu'un menu est ouvert
            // (par exemple, shift-clic pour vendre/acheter), on ne le cancel pas ici pour clickedInventory == player.getInventory().
            // Mais pour un menu de jobs/boutique simple, on annule tout.
        }

        // Gérer l'action SMELT (retrait d'item d'un four : Furnace, BlastFurnace, Smoker)
        if (event.getSlotType() == InventoryType.SlotType.RESULT && topInventory instanceof FurnaceInventory furnace) {
            ItemStack itemTaken = event.getCurrentItem(); // Item dans le slot de résultat AVANT le clic
            if (itemTaken != null && itemTaken.getType() != Material.AIR) {
                InventoryAction action = event.getAction();
                if (action == InventoryAction.PICKUP_ALL ||
                    action == InventoryAction.PICKUP_HALF ||
                    action == InventoryAction.PICKUP_ONE ||
                    action == InventoryAction.PICKUP_SOME ||
                    action == InventoryAction.MOVE_TO_OTHER_INVENTORY) { // Shift-click
                    // La quantité réellement prise se lit au tick suivant, dans le slot de résultat : un clic sans effet
                    // (inventaire plein, curseur plein, clic annulé) ne doit pas faire avancer l'objectif.
                    Material smelted = itemTaken.getType();
                    int before = itemTaken.getAmount();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        ItemStack after = furnace.getResult();
                        int remaining = after != null && after.getType() == smelted ? after.getAmount() : 0;
                        int taken = before - remaining;
                        if (taken > 0 && player.isOnline()) {
                            progressSmelt(player, smelted, taken);
                        }
                    });
                }
            }
        }
    }

    /**
     * Fait avancer les objectifs SMELT du joueur (les autres actions passent par JobProgressListener).
     */
    private void progressSmelt(Player player, Material smelted, int amount) {
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null || playerData.getActiveDailyJobs().isEmpty()) {
            return;
        }
        for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
            if (dailyJob.isCompleted()) continue;
            for (JobObjective objective : dailyJob.getTemplate().getObjectives()) {
                if (objective.getAction() == JobAction.SMELT && !dailyJob.isObjectiveCompleted(objective.getId())
                    && objective.checkSmeltCondition(smelted)) {
                    if (dailyJob.incrementProgress(objective.getId(), amount)) {
                        plugin.getMessages().sendPlayerMessage(player, "jobs.objective_completed",
                            "%objective_name%", objective.getObjectiveDisplayName(),
                            "%job_name%", dailyJob.getTemplate().getDisplayName());
                    }
                    if (dailyJob.isCompleted()) {
                        plugin.getMessages().sendPlayerMessage(player, "jobs.job_completed",
                            "%job_name%", dailyJob.getTemplate().getDisplayName());
                        plugin.getJobManager().giveJobCompletionRewards(player, dailyJob);
                        return; // la collection de jobs peut changer : on sort
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Un glisser-déposer sur le menu y laisserait des objets, perdus à la fermeture
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CustomMenuHolder && event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        // Player player = (Player) event.getPlayer();
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof JobsMenu) {
            // ((JobsMenu) holder).onClose(player); // Si des actions sont nécessaires à la fermeture
            // Par exemple, si on veut sauvegarder un état ou nettoyer des choses.
        } else if (holder instanceof ShopMenu) {
            // ((ShopMenu) holder).onClose(player);
        }
        // Pas d'action spécifique à la fermeture pour l'instant.
        // Le RealTimeUpdateTask s'occupera de ne plus mettre à jour les inventaires fermés.
    }
}
