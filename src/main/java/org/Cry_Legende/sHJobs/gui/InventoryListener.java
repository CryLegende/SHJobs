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

        // Gérer l'action SMELT (retrait d'item d'un four)
        // On vérifie si le joueur clique sur le slot de résultat d'un four
        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.RESULT) {
            if (topInventory instanceof FurnaceInventory) { // Furnace, BlastFurnace, Smoker
                ItemStack itemTaken = event.getCurrentItem(); // Item dans le slot de résultat AVANT le clic

                // L'item est retiré APRÈS cet event si non annulé.
                // Pour SMELT, on veut compter ce que le joueur PREND.
                // Si le clic est un shift-click, la quantité peut être plus grande.
                if (itemTaken != null && itemTaken.getType() != Material.AIR) {
                    // On doit déterminer la quantité réellement prise.
                    // Si c'est un clic normal, c'est itemTaken.getAmount().
                    // Si c'est un shift-click, tous les items correspondants dans le slot de résultat sont déplacés.
                    // L'event.getCurrentItem() est ce qui EST dans le slot.
                    // L'event.getCursor() est ce qui est sur le curseur.

                    // Si le joueur prend l'item (pas un clic pour mettre un item, bien que ce soit un slot de résultat)
                    // Les actions typiques pour prendre : CLICK, SHIFT_LEFT, SHIFT_RIGHT
                    if (event.getAction() == InventoryAction.PICKUP_ALL ||
                        event.getAction() == InventoryAction.PICKUP_HALF ||
                        event.getAction() == InventoryAction.PICKUP_ONE ||
                        event.getAction() == InventoryAction.PICKUP_SOME ||
                        event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) { // Shift-click

                        // La quantité prise est dans itemTaken.getAmount() pour les clics simples.
                        // Pour shift-click (MOVE_TO_OTHER_INVENTORY), event.getCurrentItem() est le stack qui sera déplacé.
                        int amountTaken = itemTaken.getAmount();

                        // Déléguer à JobProgressListener.handleObjectiveProgress
                        // Note: JobProgressListener n'a pas de méthode publique directe pour ça.
                        // On pourrait appeler une méthode spécifique ou passer par la méthode générique.
                        // Pour la cohérence, on pourrait simuler l'appel à une méthode dans JobProgressListener
                        // ou, mieux, JobProgressListener pourrait avoir une méthode publique `processSmelt`.
                        // Ou on réutilise `handleObjectiveProgress` de JobProgressListener en passant les bons params.
                        // C'est un peu un hack, il serait plus propre d'avoir une méthode dédiée dans JPL.

                        // Pour l'instant, on va appeler une méthode dans JobProgressListener (qui n'existe pas encore comme ça)
                        // ou on appelle une méthode statique/via instance de plugin
                        // ((JobProgressListener) plugin.getJobProgressListenerInstance())
                        //      .processSmeltAction(player, itemTaken, amountTaken);
                        // Pour cet exemple, on va juste loguer et on créera la méthode dans JPL plus tard si besoin.

                        // Réutilisation de la logique existante (peut nécessiter adaptation de handleObjectiveProgress)
                        // On suppose que JobProgressListener est enregistré et accessible via plugin.
                        // On peut aussi créer une instance ici, mais c'est moins propre.
                        // Si JobProgressListener est bien enregistré:
                        // SHJobs.getInstance().getJobProgressListener().handleSmelt(...) ; // si méthode publique

                        // Appel direct via le plugin si on a une instance du listener
                        // (on n'en a pas directement, mais on pourrait ajouter un getter).
                        // Le plus simple est de refaire un appel à la logique ici pour SMELT
                        // ou d'exposer une méthode dans PlayerCache/JobManager qui transmet.

                        // Pour l'exemple, on va utiliser une méthode hypothétique sur JobProgressListener
                        // (ou on pourrait copier/coller une version simplifiée de handleObjectiveProgress ici pour SMELT)

                        // Solution: on utilise un `primaryTarget` qui est l'ItemStack lui-même
                        // et `secondaryTarget` peut être null. `amount` est la quantité.
                        // Le JobProgressListener.handleObjectiveProgress doit être capable de gérer un ItemStack pour SMELT.
                        // On a déjà prévu ça : case SMELT: if (primaryTarget instanceof ItemStack)
                        // Donc, c'est bon.

                        // On doit s'assurer que le listener de JobProgress est accessible.
                        // SHJobs.getInstance().getSomeManager().getJobProgressListener().handle(...)
                        // Pour éviter la dépendance circulaire ou un couplage trop fort,
                        // on peut passer l'instance du listener ou le joueur et l'item au JobManager qui s'en occupe.

                        // Appel à la logique de progression des jobs :
                        // Cela suppose que JobProgressListener est instancié et enregistré.
                        // Pour appeler une méthode de JobProgressListener, on doit soit:
                        // 1. Le rendre accessible via un getter dans SHJobs (pas top pour l'encapsulation).
                        // 2. Créer une méthode dans SHJobs ou JobManager qui relaie l'appel.
                        // 3. Faire la logique de progression pour SMELT directement ici (moins DRY).

                        // Option 2 : Méthode dans JobManager (ou SHJobs)
                        // plugin.getJobManager().processPlayerSmelt(player, itemTaken, amountTaken);
                        // Et JobManager appellerait handleObjectiveProgress sur son instance de JPL (qu'il n'a pas)
                        // ou directement la logique.

                        // Option la plus simple pour l'instant: appeler directement la logique de progression ici.
                        // Cela duplique un peu, mais évite de complexifier les appels entre managers/listeners.
                        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
                        if (playerData != null && !playerData.getActiveDailyJobs().isEmpty()) {
                            // Recréer une mini-boucle de handleObjectiveProgress pour SMELT
                            for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
                                if (dailyJob.isCompleted()) continue;
                                for (JobObjective objective : dailyJob.getTemplate().getObjectives()) {
                                    if (objective.getAction() == JobAction.SMELT && !dailyJob.isObjectiveCompleted(objective.getId())) {
                                        if (objective.checkSmeltCondition(itemTaken.getType())) {
                                            if (dailyJob.incrementProgress(objective.getId(), amountTaken)) {
                                                plugin.getMessages().sendPlayerMessage(player, "jobs.objective_completed",
                                                    "%objective_name%", objective.getObjectiveDisplayName(),
                                                    "%job_name%", dailyJob.getTemplate().getDisplayName());
                                            }
                                            if (dailyJob.isCompleted()) {
                                                plugin.getMessages().sendPlayerMessage(player, "jobs.job_completed",
                                                    "%job_name%", dailyJob.getTemplate().getDisplayName());
                                                plugin.getJobManager().giveJobCompletionRewards(player, dailyJob);
                                                // Le job est retiré par giveJobCompletionRewards
                                                // Si on avait plusieurs jobs de smelt, il faudrait continuer la boucle
                                                // mais comme giveJobCompletionRewards peut modifier la collection de jobs,
                                                // il vaut mieux retourner ou re-vérifier.
                                                // Pour la simplicité, si un job est complété, on sort.
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
