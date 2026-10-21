package org.Cry_Legende.sHJobs.listeners;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.jobs.DailyJob;
import org.Cry_Legende.sHJobs.jobs.JobAction;
import org.Cry_Legende.sHJobs.jobs.JobObjective;
import org.Cry_Legende.sHJobs.jobs.JobType; // Pour les récompenses
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable; // Pour les cultures
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockCookEvent; // Pour SMELT (remplace FurnaceSmeltEvent)
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent; // Potentiellement pour STRIP si pas d'event dédié
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent; // Pour WALK (attention, très coûteux)
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class JobProgressListener implements Listener {

    private final SHJobs plugin;

    public JobProgressListener(SHJobs plugin) {
        this.plugin = plugin;
    }

    private void handleObjectiveProgress(Player player, JobAction actionType, Object primaryTarget, Object secondaryTarget, int amount) {
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null || playerData.getActiveDailyJobs().isEmpty()) {
            return; // Pas de données ou pas de jobs actifs
        }

        boolean changed = false;
        for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
            if (dailyJob.isCompleted()) continue; // Job déjà complété

            for (JobObjective objective : dailyJob.getTemplate().getObjectives()) {
                if (dailyJob.isObjectiveCompleted(objective.getId())) continue; // Objectif déjà complété

                if (objective.getAction() == actionType) {
                    boolean conditionMet = false;
                    switch (actionType) {
                        case BREAK:
                            if (primaryTarget instanceof Material && secondaryTarget instanceof Integer) { // secondaryTarget est l'âge
                                conditionMet = objective.checkBreakCondition((Material) primaryTarget, (Integer) secondaryTarget);
                            }
                            break;
                        case PLACE:
                            if (primaryTarget instanceof Material) {
                                conditionMet = objective.checkPlaceCondition((Material) primaryTarget);
                            }
                            break;
                        case CRAFT:
                            if (primaryTarget instanceof ItemStack) {
                                conditionMet = objective.checkCraftCondition((ItemStack) primaryTarget);
                            }
                            break;
                        case FISH:
                            if (primaryTarget instanceof Material) { // Material de l'item pêché
                                conditionMet = objective.checkFishCondition((Material) primaryTarget);
                            }
                            break;
                        case KILL:
                            if (primaryTarget instanceof org.bukkit.entity.EntityType) {
                                conditionMet = objective.checkKillCondition((org.bukkit.entity.EntityType) primaryTarget);
                            }
                            break;
                        case SMELT: // primaryTarget est l'ItemStack résultat
                            if (primaryTarget instanceof ItemStack) {
                                conditionMet = objective.checkSmeltCondition(((ItemStack) primaryTarget).getType());
                            }
                            break;
                        case BREW: // primaryTarget est PotionData
                            if (primaryTarget instanceof PotionData) {
                                conditionMet = objective.checkBrewCondition((PotionData) primaryTarget);
                            }
                            break;
                        case STRIP:
                            if (primaryTarget instanceof Material) { // Le material du bloc après strip
                                // La logique de STRIP dans JobObjective.checkStripCondition doit être adaptée
                                // pour savoir si on compare avec le log original ou le log strippé.
                                // Ici, on suppose que JobObjective s'attend au Material du bloc qui a été strippé.
                                // Le listener devra fournir le Material original si la config est pour le log non-strippé.
                                // Ou, plus simple: on configure le Material strippé dans le job_template.
                                conditionMet = objective.checkStripCondition((Material) primaryTarget);
                            }
                            break;
                        case WALK:
                            // WALK est spécial, la progression est incrémentée directement par le listener
                            // car l'amount est géré différemment (distance vs. items).
                            // On pourrait le gérer ici si 'amount' passé est la distance parcourue.
                            break;
                    }

                    if (conditionMet) {
                        if (dailyJob.incrementProgress(objective.getId(), amount)) {
                            // L'objectif est complété
                            plugin.getMessages().sendPlayerMessage(player, "jobs.objective_completed",
                                "%objective_name%", objective.getObjectiveDisplayName(),
                                "%job_name%", dailyJob.getTemplate().getDisplayName());
                            // Le son/message de complétion du job entier est géré quand dailyJob.isCompleted() devient true.
                        }
                        changed = true;
                        if (dailyJob.isCompleted()) {
                            plugin.getMessages().sendPlayerMessage(player, "jobs.job_completed",
                                "%job_name%", dailyJob.getTemplate().getDisplayName());
                            plugin.getJobManager().giveJobCompletionRewards(player, dailyJob);
                            // Le job sera retiré de playerData.activeDailyJobs par giveJobCompletionRewards
                            // Donc, pas besoin de le retirer ici pour éviter ConcurrentModificationException
                            // Si on ne le retire pas, il faut un mécanisme pour ne pas redonner les récompenses.
                            // Pour l'instant, giveJobCompletionRewards le retire.
                            return; // Sortir tôt si un job est complété et retiré
                        }
                    }
                }
            }
        }
        if (changed) {
            // Si une progression a eu lieu, on pourrait vouloir mettre à jour un ActionBar ou un BossBar.
            // Pour le lore du GUI, il sera mis à jour à la prochaine ouverture.
        }
    }

    // --- Listeners pour chaque JobAction ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();
        int age = -1;

        if (block.getBlockData() instanceof Ageable) {
            Ageable ageable = (Ageable) block.getBlockData();
            age = ageable.getAge();
        }
        handleObjectiveProgress(player, JobAction.BREAK, material, age, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material material = event.getBlockPlaced().getType();
        handleObjectiveProgress(player, JobAction.PLACE, material, null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        Recipe recipe = event.getRecipe();
        ItemStack resultItemFromRecipe = recipe.getResult();

        if (resultItemFromRecipe == null || resultItemFromRecipe.getType() == Material.AIR) {
            return;
        }

        int amountCrafted;
        int recipeResultAmount = resultItemFromRecipe.getAmount();

        if (recipeResultAmount <= 0) {
            amountCrafted = 0;
        } else if (event.isShiftClick()) {
            CraftingInventory craftingInv = event.getInventory();
            int maxPossibleCraftsBasedOnIngredients = Integer.MAX_VALUE;

            ItemStack[] matrix = craftingInv.getMatrix();

            if (matrix == null || matrix.length == 0) {
                maxPossibleCraftsBasedOnIngredients = 0;
            } else {
                boolean ingredientsFoundInMatrix = false;
                for (ItemStack itemInMatrix : matrix) {
                    if (itemInMatrix != null && itemInMatrix.getType() != Material.AIR) {
                        maxPossibleCraftsBasedOnIngredients = Math.min(maxPossibleCraftsBasedOnIngredients, itemInMatrix.getAmount());
                        ingredientsFoundInMatrix = true;
                    }
                }
                if (!ingredientsFoundInMatrix) {
                    maxPossibleCraftsBasedOnIngredients = 0;
                }
            }

            if (maxPossibleCraftsBasedOnIngredients == 0) {
                amountCrafted = 0;
            } else {
                PlayerInventory playerInventory = player.getInventory();
                int freeSpaceInInventoryForItem = 0;

                for (ItemStack itemStackInPlayerInv : playerInventory.getStorageContents()) {
                    if (itemStackInPlayerInv == null || itemStackInPlayerInv.getType() == Material.AIR) {
                        freeSpaceInInventoryForItem += resultItemFromRecipe.getMaxStackSize();
                    } else if (itemStackInPlayerInv.isSimilar(resultItemFromRecipe)) {
                        freeSpaceInInventoryForItem += Math.max(0, resultItemFromRecipe.getMaxStackSize() - itemStackInPlayerInv.getAmount());
                    }
                }

                int numCraftsLimitedByInventorySpace = freeSpaceInInventoryForItem / recipeResultAmount;

                int actualCraftOperations = Math.min(maxPossibleCraftsBasedOnIngredients, numCraftsLimitedByInventorySpace);

                amountCrafted = actualCraftOperations * recipeResultAmount;
            }
        } else {
            amountCrafted = recipeResultAmount;
        }

        if (amountCrafted > 0) {
            handleObjectiveProgress(player, JobAction.CRAFT, resultItemFromRecipe, null, amountCrafted);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            if (event.getCaught() instanceof org.bukkit.entity.Item) {
                Player player = event.getPlayer();
                ItemStack fishedItem = ((org.bukkit.entity.Item) event.getCaught()).getItemStack();
                handleObjectiveProgress(player, JobAction.FISH, fishedItem.getType(), null, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();
            org.bukkit.entity.EntityType entityType = event.getEntityType();
            handleObjectiveProgress(killer, JobAction.KILL, entityType, null, 1);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        InventoryType invType = event.getInventory().getType();

        JobAction actionType;
        Object targetObject;

        // Déterminer le type d'action et l'objet cible en fonction de l'inventaire
        switch (invType) {
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
                actionType = JobAction.SMELT;
                targetObject = currentItem.getType();
                break;
            case BREWING:
                actionType = JobAction.BREW;
                if (currentItem.getItemMeta() instanceof PotionMeta) {
                    targetObject = ((PotionMeta) currentItem.getItemMeta()).getBasePotionData();
                } else {
                    return; // Ce n'est pas une potion standard
                }
                break;
            default:
                return; // Inventaire non pertinent
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            // Pour les shift-clics, le calcul de la quantité est complexe.
            // La méthode la plus fiable est de vérifier la différence dans l'inventaire du joueur juste après l'action.
            handleShiftClick(player, currentItem, actionType, targetObject);
        } else {
            // Pour un clic normal, la quantité est simplement celle de l'itemstack dans le slot de résultat.
            int amount = currentItem.getAmount();
            handleObjectiveProgress(player, actionType, targetObject, null, amount);
        }
    }

    private void handleShiftClick(Player player, ItemStack resultItem, JobAction action, Object target) {
        int amountBefore = countItemsInInventory(player.getInventory(), resultItem);

        // On exécute la tâche au prochain tick pour laisser le temps au serveur de déplacer les items.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int amountAfter = countItemsInInventory(player.getInventory(), resultItem);
            int amountMoved = amountAfter - amountBefore;

            if (amountMoved > 0) {
                handleObjectiveProgress(player, action, target, null, amountMoved);
            }
        });
    }

    private int countItemsInInventory(PlayerInventory inventory, ItemStack itemToMatch) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.isSimilar(itemToMatch)) {
                count += item.getAmount();
            }
        }
        return count;
    }


    private static final Map<Material, Material> STRIPPABLE_LOGS = new HashMap<>();

    static {
        // Overworld Logs & Woods
        STRIPPABLE_LOGS.put(Material.OAK_LOG, Material.STRIPPED_OAK_LOG);
        STRIPPABLE_LOGS.put(Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD);
        STRIPPABLE_LOGS.put(Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG);
        STRIPPABLE_LOGS.put(Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD);
        STRIPPABLE_LOGS.put(Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG);
        STRIPPABLE_LOGS.put(Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD);
        STRIPPABLE_LOGS.put(Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG);
        STRIPPABLE_LOGS.put(Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD);
        STRIPPABLE_LOGS.put(Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG);
        STRIPPABLE_LOGS.put(Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD);
        STRIPPABLE_LOGS.put(Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG);
        STRIPPABLE_LOGS.put(Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD);
        STRIPPABLE_LOGS.put(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG);
        STRIPPABLE_LOGS.put(Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD);
        STRIPPABLE_LOGS.put(Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG);
        STRIPPABLE_LOGS.put(Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD);

        // Bamboo Block (1.20)
        STRIPPABLE_LOGS.put(Material.BAMBOO_BLOCK, Material.STRIPPED_BAMBOO_BLOCK);

        // Nether Stems & Hyphae
        STRIPPABLE_LOGS.put(Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM);
        STRIPPABLE_LOGS.put(Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE);
        STRIPPABLE_LOGS.put(Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM);
        STRIPPABLE_LOGS.put(Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractStrip(PlayerInteractEvent event) {
        // On ne traite que le clic droit sur un bloc
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // On s'assure que l'événement vient de la main principale pour éviter le double traitement
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Le joueur doit tenir une hache
        if (itemInHand.getType().name().endsWith("_AXE")) {
            Material originalMaterial = clickedBlock.getType();
            Material strippedMaterial = STRIPPABLE_LOGS.get(originalMaterial);

            // Si le bloc cliqué est un bloc écorçable
            if (strippedMaterial != null) {
                // L'événement est déclenché AVANT que le bloc ne change.
                // On doit vérifier 1 tick plus tard si le changement a bien eu lieu.
                // C'est la seule manière fiable de confirmer l'action.
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    // On vérifie que le bloc à cet emplacement est maintenant le bloc écorcé.
                    // Cela confirme que l'action a réussi (et n'a pas été annulée par un autre plugin, par exemple).
                    if (clickedBlock.getType() == strippedMaterial) {
                        handleObjectiveProgress(player, JobAction.STRIP, strippedMaterial, null, 1);
                    }
                }, 1L); // 1 tick de délai
            }
        }
    }


    // Listener pour WALK
    // Attention: PlayerMoveEvent est appelé TRÈS fréquemment. À utiliser avec précaution.
    // Il est préférable de vérifier la distance toutes les X secondes plutôt qu'à chaque mouvement.
    // Pour cet exemple, je vais le faire à chaque mouvement, mais ce n'est PAS recommandé pour la performance.
    private final Map<UUID, Double> playerWalkDistanceTracker = new HashMap<>(); // Stocke la distance accumulée pour l'objectif WALK

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return; // N'a pas changé de bloc, ignore pour la performance (simple check)
        }

        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null || playerData.getActiveDailyJobs().isEmpty()) return;

        boolean needsWalkObjectiveCheck = false;
        for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
            if (dailyJob.isCompleted()) continue;
            for (JobObjective objective : dailyJob.getTemplate().getObjectives()) {
                if (objective.getAction() == JobAction.WALK && !dailyJob.isObjectiveCompleted(objective.getId())) {
                    needsWalkObjectiveCheck = true;
                    break;
                }
            }
            if (needsWalkObjectiveCheck) break;
        }

        if (!needsWalkObjectiveCheck) return;

        double distanceMoved = event.getFrom().distance(event.getTo());
        if (distanceMoved < 0.1) return; // Trop petit mouvement

        // Mettre à jour la distance pour tous les objectifs WALK actifs
        for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
            if (dailyJob.isCompleted()) continue;
            for (JobObjective objective : dailyJob.getTemplate().getObjectives()) {
                if (objective.getAction() == JobAction.WALK && !dailyJob.isObjectiveCompleted(objective.getId())) {

                    // La progression pour WALK est stockée en tant que distance.
                    // L' "amount" de l'objectif est la distance totale à parcourir.
                    // On incrémente la progression par la distance parcourue.
                    int currentProgressInt = dailyJob.getProgress(objective.getId());
                    double newTotalWalkedForObjective = currentProgressInt + distanceMoved;

                    // On ne peut pas directement utiliser dailyJob.incrementProgress car il attend un int.
                    // On doit gérer la conversion double -> int pour la progression stockée.
                    // On stocke la progression en tant qu'entier (mètres arrondis).

                    if ((int) newTotalWalkedForObjective > currentProgressInt) { // Si on a au moins marché 1m de plus
                        int amountToAdd = (int) newTotalWalkedForObjective - currentProgressInt;

                        if (dailyJob.incrementProgress(objective.getId(), amountToAdd)) {
                            plugin.getMessages().sendPlayerMessage(player, "jobs.objective_completed",
                                "%objective_name%", objective.getObjectiveDisplayName(),
                                "%job_name%", dailyJob.getTemplate().getDisplayName());
                        }
                        if (dailyJob.isCompleted()) {
                            plugin.getMessages().sendPlayerMessage(player, "jobs.job_completed",
                                "%job_name%", dailyJob.getTemplate().getDisplayName());
                            plugin.getJobManager().giveJobCompletionRewards(player, dailyJob);
                            return; // Job complété et retiré
                        }
                    }
                }
            }
        }
    }
}
