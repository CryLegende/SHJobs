package org.Cry_Legende.sHJobs.listeners;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.jobs.DailyJob;
import org.Cry_Legende.sHJobs.jobs.JobAction;
import org.Cry_Legende.sHJobs.jobs.JobObjective;
import org.Cry_Legende.sHJobs.jobs.JobType; // Pour les récompenses
import org.Cry_Legende.sHJobs.jobs.PlacedBlocks;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable; // Pour les cultures
import org.bukkit.entity.HumanEntity;
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
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
                if (objectiveDone(dailyJob, objective)) continue; // Objectif déjà complété

                if (objective.getAction() == actionType) {
                    boolean conditionMet = false;
                    switch (actionType) {
                        case BREAK:
                            if (primaryTarget instanceof Material material && secondaryTarget instanceof Block block) {
                                // L'âge n'est lu que si l'objectif l'exige (getBlockData copie l'état du bloc à chaque casse)
                                int age = objective.getRequiredAge() == -1 ? -1 : ageOf(block);
                                conditionMet = objective.checkBreakCondition(material, age);
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
                                boolean byProjectile = Boolean.TRUE.equals(secondaryTarget);
                                conditionMet = objective.checkKillCondition((org.bukkit.entity.EntityType) primaryTarget, byProjectile);
                            }
                            break;
                        case SMITH: // primaryTarget : TRIM ou UPGRADE
                            if (primaryTarget instanceof String kind) {
                                conditionMet = objective.checkSmithCondition(kind);
                            }
                            break;
                        case HARVEST: // primaryTarget : objet cueilli
                            if (primaryTarget instanceof Material harvested) {
                                conditionMet = objective.checkHarvestCondition(harvested);
                            }
                            break;
                        case SMELT: // primaryTarget est l'ItemStack résultat
                            if (primaryTarget instanceof ItemStack) {
                                conditionMet = objective.checkSmeltCondition(((ItemStack) primaryTarget).getType());
                            }
                            break;
                        case BREW: // primaryTarget est le PotionType de la potion
                            if (primaryTarget instanceof PotionType potionType) {
                                conditionMet = objective.checkBrewCondition(potionType);
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

    private static boolean objectiveDone(DailyJob dailyJob, JobObjective objective) {
        return dailyJob.getProgress(objective.getId()) >= dailyJob.getRequired(objective);
    }

    private static int ageOf(Block block) {
        return block.getBlockData() instanceof Ageable ageable ? ageable.getAge() : -1;
    }

    // --- Listeners pour chaque JobAction ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // Bloc posé par un joueur (general.placed_blocks_count: false) : la marque part avec lui, la casse ne compte pas
        PlacedBlocks placed = plugin.getJobManager().getPlacedBlocks();
        if (placed.isTracked(block.getType()) && placed.consume(block)) {
            return;
        }
        handleObjectiveProgress(event.getPlayer(), JobAction.BREAK, block.getType(), block, 1);
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
            boolean byProjectile = event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damage
                && damage.getDamager() instanceof Projectile;
            handleObjectiveProgress(killer, JobAction.KILL, entityType, byProjectile, 1);
        }
    }

    /** Table de forgeron : une garniture (TRIM) ou une amélioration en netherite (UPGRADE) récupérée. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getAction() == InventoryAction.NOTHING) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }
        Recipe recipe = event.getInventory().getRecipe();
        String kind = recipe instanceof SmithingTrimRecipe ? "TRIM" : recipe instanceof SmithingTransformRecipe ? "UPGRADE" : null;
        if (kind != null) {
            handleObjectiveProgress(player, JobAction.SMITH, kind, null, 1);
        }
    }

    /** Cueillette au clic droit (baies sucrées, baies lumineuses) : chaque objet cueilli compte. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        for (ItemStack harvested : event.getItemsHarvested()) {
            if (harvested != null && harvested.getType() != Material.AIR && harvested.getAmount() > 0) {
                handleObjectiveProgress(event.getPlayer(), JobAction.HARVEST, harvested.getType(), null, harvested.getAmount());
            }
        }
    }


    // --- BREW : les potions comptent à la fin de l'infusion, pour le dernier joueur à avoir cliqué dans l'alambic ---

    /** Position d'un alambic, sans référence au monde. */
    private record StandPos(UUID world, int x, int y, int z) {
        static StandPos of(Location location) {
            return new StandPos(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    // Seuls les joueurs qui ont un objectif BREW en cours sont retenus. Lu et écrit par les fils des mondes.
    private final Map<StandPos, UUID> brewers = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        rememberBrewer(event.getWhoClicked(), event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        rememberBrewer(event.getWhoClicked(), event.getView().getTopInventory());
    }

    private void rememberBrewer(HumanEntity clicker, Inventory top) {
        if (!(top instanceof BrewerInventory) || !(clicker instanceof Player player) || !hasPendingObjective(player, JobAction.BREW)) {
            return;
        }
        Location location = top.getLocation();
        if (location != null && location.getWorld() != null) {
            brewers.put(StandPos.of(location), player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (brewers.isEmpty()) {
            return;
        }
        UUID brewer = brewers.get(StandPos.of(event.getBlock().getLocation()));
        if (brewer == null) {
            return;
        }
        // Au moment de l'événement, l'alambic contient encore les potions de départ
        BrewerInventory before = event.getContents();
        List<ItemStack> results = event.getResults();
        Map<PotionType, Integer> brewed = new HashMap<>();
        for (int slot = 0; slot < 3 && slot < results.size(); slot++) {
            PotionType type = potionTypeOf(results.get(slot));
            // Même type avant et après : la bouteille n'a pas réagi, ou elle est seulement devenue jetable ou persistante
            if (type != null && type != potionTypeOf(before.getItem(slot))) {
                brewed.merge(type, results.get(slot).getAmount(), Integer::sum);
            }
        }
        if (brewed.isEmpty()) {
            return;
        }
        // L'infusion se termine sur le fil du monde de l'alambic, le joueur peut être ailleurs : fil principal
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(brewer);
            if (player != null) {
                brewed.forEach((type, amount) -> handleObjectiveProgress(player, JobAction.BREW, type, null, amount));
            }
        });
    }

    private static PotionType potionTypeOf(ItemStack item) {
        return item != null && item.getItemMeta() instanceof PotionMeta meta ? meta.getBasePotionType() : null;
    }

    private boolean hasPendingObjective(Player player, JobAction action) {
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return false;
        }
        for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
            if (dailyJob.isCompleted()) continue;
            for (JobObjective objective : dailyJob.getTemplate().getObjectives()) {
                if (objective.getAction() == action && !objectiveDone(dailyJob, objective)) {
                    return true;
                }
            }
        }
        return false;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return; // N'a pas changé de bloc, ignore pour la performance (simple check)
        }

        // Aucun objectif WALK dans la config : rien à suivre
        if (!plugin.getJobManager().hasWalkObjectives()) return;

        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null || playerData.getActiveDailyJobs().isEmpty()) return;

        boolean needsWalkObjectiveCheck = false;
        for (DailyJob dailyJob : playerData.getActiveDailyJobs().values()) {
            if (dailyJob.isCompleted()) continue;
            for (JobObjective objective : dailyJob.getTemplate().getObjectives()) {
                if (objective.getAction() == JobAction.WALK && !objectiveDone(dailyJob, objective)) {
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
                if (objective.getAction() == JobAction.WALK && !objectiveDone(dailyJob, objective)) {

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
