package org.Cry_Legende.sHJobs.tasks;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.gui.JobsMenu;
import org.Cry_Legende.sHJobs.gui.MenuManager;
import org.Cry_Legende.sHJobs.gui.ShopMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitRunnable;

public class RealTimeUpdateTask extends BukkitRunnable {

    private final SHJobs plugin;
    private final MenuManager menuManager; // Accès plus direct si nécessaire

    public RealTimeUpdateTask(SHJobs plugin) {
        this.plugin = plugin;
        this.menuManager = plugin.getMenuManager();
    }

    @Override
    public void run() {
        // Parcourir tous les joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView openInventoryView = player.getOpenInventory();
            if (openInventoryView == null) continue;

            Inventory topInventory = openInventoryView.getTopInventory();
            if (topInventory.getHolder() instanceof JobsMenu || topInventory.getHolder() instanceof ShopMenu) {
                // Le joueur a un de nos menus ouverts. Mettre à jour les items avec countdown.
                // La logique de mise à jour de l'item spécifique sera dans la classe du menu elle-même
                // ou dans MenuManager.
                // Par exemple, JobsMenu et ShopMenu pourraient avoir une méthode updateCountdownItems().

                // Option 1: Le MenuManager gère la mise à jour
                // menuManager.updateOpenMenuForPlayer(player);

                // Option 2: Le Holder du menu gère sa propre mise à jour
                if (topInventory.getHolder() instanceof JobsMenu) {
                    ((JobsMenu) topInventory.getHolder()).updateCountdownItem(player);
                } else if (topInventory.getHolder() instanceof ShopMenu) {
                    ((ShopMenu) topInventory.getHolder()).updateCountdownItem(player);
                }
            }
        }
    }
}