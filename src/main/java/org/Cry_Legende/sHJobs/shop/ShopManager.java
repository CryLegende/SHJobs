package org.Cry_Legende.sHJobs.shop;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.gui.ShopMenu;
import org.Cry_Legende.sHJobs.items.ItemMatcher;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ShopManager {

    private final SHJobs plugin;
    private final Map<String, ShopItem> allShopItems;
    private List<String> currentDailyShopItemIds;

    /**
     * Enum pour un retour de statut clair lors de la vérification d'un achat,
     * évitant les effets de bord comme l'envoi de messages.
     */
    public enum PurchaseStatus {
        CAN_PURCHASE,
        NOT_ENOUGH_POINTS,
        LEVEL_TOO_LOW
    }

    public ShopManager(SHJobs plugin) {
        this.plugin = plugin;
        this.allShopItems = new HashMap<>();
        this.currentDailyShopItemIds = new ArrayList<>(Collections.nCopies(6, null));
    }

    public void loadShopItems() {
        allShopItems.clear();
        ConfigurationSection itemsSection = plugin.getConfigManager().getShopItemsSection();
        if (itemsSection == null) {
            plugin.getLogger().warning("No 'shop_items' section found in config.yml.");
            return;
        }
        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(itemId);
            if (itemConfig == null) continue;
            Optional<ShopItem> itemOpt = ShopItem.fromConfig(plugin, itemId, itemConfig);
            itemOpt.ifPresent(item -> allShopItems.put(item.getId(), item));
        }
        plugin.getLogger().info("Loaded " + allShopItems.size() + " shop item templates.");
    }

    /**
     * Vérifie si un joueur peut acheter un article SANS envoyer de message.
     * Cette méthode est destinée à être utilisée pour l'affichage dans le GUI.
     *
     * @return Un enum PurchaseStatus indiquant l'état.
     */
    public PurchaseStatus getPurchaseStatus(Player player, ShopItem item) {
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return PurchaseStatus.LEVEL_TOO_LOW; // Fallback sécuritaire
        }

        if (playerData.getJobPoints() < item.getCost()) {
            return PurchaseStatus.NOT_ENOUGH_POINTS;
        }

        Optional<JobType> requiredJobOpt = item.getRequiredJobType();
        if (requiredJobOpt.isPresent()) {
            JobType requiredJob = requiredJobOpt.get();
            if (playerData.getLevel(requiredJob) < item.getRequiredJobLevel()) {
                return PurchaseStatus.LEVEL_TOO_LOW;
            }
        }
        return PurchaseStatus.CAN_PURCHASE;
    }

    /**
     * Valide si un joueur peut acheter un article et lui envoie un message d'erreur approprié si ce n'est pas le cas.
     * Cette méthode est destinée à être utilisée lors de l'action de clic.
     *
     * @return true si l'achat est possible, false sinon.
     */
    public boolean canPlayerPurchase(Player player, ShopItem item) {
        PurchaseStatus status = getPurchaseStatus(player, item);

        if (status == PurchaseStatus.NOT_ENOUGH_POINTS) {
            plugin.getMessages().sendPlayerMessage(player, "shop.not_enough_points", "%cost%", String.valueOf(item.getCost()));
            return false;
        }

        if (status == PurchaseStatus.LEVEL_TOO_LOW) {
            item.getRequiredJobType().ifPresent(requiredJob ->
                plugin.getMessages().sendPlayerMessage(player, "shop.level_too_low",
                    "%required_level%", String.valueOf(item.getRequiredJobLevel()),
                    "%job_name%", requiredJob.getDisplayName())
            );
            return false;
        }

        return true;
    }

    /**
     * Gère la tentative d'achat d'un item par un joueur.
     */
    public void processPurchase(Player player, ShopItem item) {
        processPurchase(player, item, 1);
    }

    /**
     * Gère la tentative d'achat en quantité d'un item par un joueur.
     * La quantité réellement achetée est limitée par le solde disponible.
     */
    public void processPurchase(Player player, ShopItem item, int requestedQuantity) {
        if (requestedQuantity <= 0) {
            return;
        }

        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return;
        }

        // Vérifications de prérequis (niveau, etc.) indépendantes de la quantité
        PurchaseStatus status = getPurchaseStatus(player, item);
        if (status == PurchaseStatus.LEVEL_TOO_LOW) {
            item.getRequiredJobType().ifPresent(requiredJob ->
                plugin.getMessages().sendPlayerMessage(player, "shop.level_too_low",
                    "%required_level%", String.valueOf(item.getRequiredJobLevel()),
                    "%job_name%", requiredJob.getDisplayName())
            );
            return;
        }

        // Limite de sécurité pour éviter des boucles de commandes trop volumineuses
        int maxRequested = Math.min(requestedQuantity, 64);
        long unitCost = item.getCost();
        long currentPoints = playerData.getJobPoints();

        int quantityToBuy = maxRequested;
        if (unitCost > 0) {
            long affordable = currentPoints / unitCost;
            quantityToBuy = (int) Math.min(maxRequested, affordable);
        }

        if (quantityToBuy <= 0) {
            plugin.getMessages().sendPlayerMessage(player, "shop.not_enough_points", "%cost%", String.valueOf(item.getCost()));
            return;
        }

        long totalCostLong = unitCost * (long) quantityToBuy;
        if (totalCostLong > Integer.MAX_VALUE) {
            quantityToBuy = (int) (Integer.MAX_VALUE / Math.max(1L, unitCost));
            if (quantityToBuy <= 0) {
                plugin.getMessages().sendPlayerMessage(player, "shop.not_enough_points", "%cost%", String.valueOf(item.getCost()));
                return;
            }
            totalCostLong = unitCost * (long) quantityToBuy;
        }
        long totalCost = totalCostLong;

        if (currentPoints < totalCost) {
            plugin.getMessages().sendPlayerMessage(player, "shop.not_enough_points", "%cost%", String.valueOf(totalCost));
            return;
        }

        playerData.removeJobPoints(totalCost);

        String playerName = player.getName();
        for (int i = 0; i < quantityToBuy; i++) {
            for (String command : item.getCommandsOnPurchase()) {
                String processedCommand = command.replace("%player%", playerName)
                    .replace("%item_name%", item.getDisplayName())
                    .replace("%amount%", String.valueOf(quantityToBuy));
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), processedCommand);
            }
        }

        if (quantityToBuy == 1) {
            plugin.getMessages().sendPlayerMessage(player, "shop.purchase_successful",
                "%item_name%", item.getDisplayName(),
                "%cost%", String.valueOf(totalCost));
        } else {
            player.sendMessage(plugin.getMessages().colorize(
                "&aAchat effectué: &f" + quantityToBuy + "x " + item.getDisplayName() + " &7pour &e" + totalCost + " points&7."
            ));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        // Rafraîchir le menu pour mettre à jour l'affichage
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu) {
            plugin.getMenuManager().openShopMenu(player);
        }
    }

    /**
     * Gère la tentative de vente d'un item par un joueur.
     */
    public void processSell(Player player, ShopItem shopItemEntry) {
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) return;

        if (!shopItemEntry.isSellable()) {
            plugin.getMessages().sendPlayerMessage(player, "shop.not_sellable", "%item_name%", shopItemEntry.getDisplayName());
            return;
        }

        Optional<ItemStack> itemToSellOpt = plugin.getCustomItemManager().getItem(shopItemEntry.getSellItemId());
        if (itemToSellOpt.isEmpty()) {
            plugin.getLogger().severe("Configuration error: Shop entry '" + shopItemEntry.getId() + "' refers to a non-existent custom item: '" + shopItemEntry.getSellItemId() + "'.");
            player.sendMessage("§cErreur de configuration du serveur. Vente impossible.");
            return;
        }

        ItemStack itemToSell = itemToSellOpt.get();
        int amountToSell = itemToSell.getAmount();

        if (countMatching(player, itemToSell) >= amountToSell) {
            removeMatching(player, itemToSell, amountToSell);
            playerData.addJobPoints(shopItemEntry.getSellPoints());

            String soldItemName = itemToSell.hasItemMeta() && itemToSell.getItemMeta().hasDisplayName() ? itemToSell.getItemMeta().getDisplayName() : shopItemEntry.getDisplayName();
            plugin.getMessages().sendPlayerMessage(player, "shop.sell_successful",
                "%item_name%", soldItemName,
                "%points%", String.valueOf(shopItemEntry.getSellPoints()));

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
        } else {
            plugin.getMessages().sendPlayerMessage(player, "shop.item_not_found_for_sell", "%item_name%", shopItemEntry.getDisplayName());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }

        // Rafraîchir le menu pour mettre à jour le solde de points
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu) {
            plugin.getMenuManager().openShopMenu(player);
        }
    }

    /**
     * Vend d'un seul coup tous les exemplaires de l'article présents dans l'inventaire du joueur.
     */
    public void processSellAll(Player player, ShopItem shopItemEntry) {
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) return;

        if (!shopItemEntry.isSellable()) {
            plugin.getMessages().sendPlayerMessage(player, "shop.not_sellable", "%item_name%", shopItemEntry.getDisplayName());
            return;
        }

        Optional<ItemStack> itemToSellOpt = plugin.getCustomItemManager().getItem(shopItemEntry.getSellItemId());
        if (itemToSellOpt.isEmpty()) {
            plugin.getLogger().severe("Configuration error: Shop entry '" + shopItemEntry.getId() + "' refers to a non-existent custom item: '" + shopItemEntry.getSellItemId() + "'.");
            player.sendMessage("§cErreur de configuration du serveur. Vente impossible.");
            return;
        }

        ItemStack itemToSell = itemToSellOpt.get();
        int unitAmount = Math.max(1, itemToSell.getAmount());

        int sales = countMatching(player, itemToSell) / unitAmount;
        if (sales <= 0) {
            plugin.getMessages().sendPlayerMessage(player, "shop.item_not_found_for_sell", "%item_name%", shopItemEntry.getDisplayName());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        removeMatching(player, itemToSell, sales * unitAmount);

        long totalPoints = (long) shopItemEntry.getSellPoints() * sales;
        playerData.addJobPoints(totalPoints);

        String soldItemName = itemToSell.hasItemMeta() && itemToSell.getItemMeta().hasDisplayName() ? itemToSell.getItemMeta().getDisplayName() : shopItemEntry.getDisplayName();
        plugin.getMessages().sendPlayerMessage(player, "shop.sell_all_successful",
            "%item_name%", soldItemName,
            "%amount%", String.valueOf(sales * unitAmount),
            "%points%", String.valueOf(totalPoints));

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);

        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu) {
            plugin.getMenuManager().openShopMenu(player);
        }
    }

    /**
     * Nombre d'exemplaires de l'article dans l'inventaire du joueur (hors armure et seconde main).
     */
    private int countMatching(Player player, ItemStack reference) {
        int owned = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (ItemMatcher.matches(reference, content)) {
                owned += content.getAmount();
            }
        }
        return owned;
    }

    /**
     * Retire {@code amount} exemplaires de l'article, pile par pile ; l'appelant a vérifié le stock.
     */
    private void removeMatching(Player player, ItemStack reference, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack content = contents[i];
            if (!ItemMatcher.matches(reference, content)) {
                continue;
            }
            int taken = Math.min(left, content.getAmount());
            left -= taken;
            if (taken == content.getAmount()) {
                contents[i] = null;
            } else {
                content.setAmount(content.getAmount() - taken);
            }
        }
        inventory.setStorageContents(contents);
    }

    public Optional<ShopItem> getShopItemTemplate(String itemId) {
        return Optional.ofNullable(allShopItems.get(itemId));
    }

    public Collection<ShopItem> getAllShopItemTemplates() {
        return Collections.unmodifiableCollection(allShopItems.values());
    }

    public void selectNewDailyShopItems() {
        List<String> newShopItemIds = new ArrayList<>(Collections.nCopies(6, null));
        if (allShopItems.isEmpty()) {
            plugin.getLogger().warning("No shop items loaded. Cannot select daily shop items.");
            this.currentDailyShopItemIds = newShopItemIds;
            return;
        }
        List<ShopItem> availableItems = new ArrayList<>(allShopItems.values());
        Set<String> selectedInThisBatch = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            if (availableItems.isEmpty()) {
                if (allShopItems.isEmpty()) break;
                availableItems = new ArrayList<>(allShopItems.values());
            }
            ShopItem selected = selectWeightedRandom(availableItems);
            if (selected != null) {
                newShopItemIds.set(i, selected.getId());
                selectedInThisBatch.add(selected.getId());
                if (allShopItems.size() >= 6) {
                    availableItems.remove(selected);
                } else if (allShopItems.size() > selectedInThisBatch.size()) {
                    availableItems.remove(selected);
                }
            }
        }
        this.currentDailyShopItemIds = newShopItemIds;
        plugin.getLogger().info("New daily shop items selected: " +
            this.currentDailyShopItemIds.stream()
                .map(id -> id != null ? id : "EMPTY")
                .collect(Collectors.joining(", "))
        );
    }

    private ShopItem selectWeightedRandom(List<ShopItem> items) {
        if (items == null || items.isEmpty()) return null;
        double totalWeight = items.stream().mapToDouble(ShopItem::getSelectionWeight).sum();
        if (totalWeight <= 0) return items.get(ThreadLocalRandom.current().nextInt(items.size()));
        double randomValue = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulativeWeight = 0.0;
        for (ShopItem item : items) {
            cumulativeWeight += item.getSelectionWeight();
            if (randomValue < cumulativeWeight) return item;
        }
        return items.get(items.size() - 1);
    }

    public List<String> getCurrentDailyShopItemIds() {
        return new ArrayList<>(currentDailyShopItemIds);
    }

    public void setCurrentDailyShopItemIds(List<String> ids) {
        if (ids != null && ids.size() == 6) {
            this.currentDailyShopItemIds = new ArrayList<>(ids);
        } else if (ids != null) {
            plugin.getLogger().warning("Attempted to set current daily shop item IDs with an invalid list (size: " + ids.size() + "). Retaining old or default.");
            if (this.currentDailyShopItemIds.stream().allMatch(Objects::isNull)) {
                selectNewDailyShopItems();
            }
        } else {
            plugin.getLogger().warning("Attempted to set current daily shop item IDs with a null list. Selecting new shop items.");
            selectNewDailyShopItems();
        }
    }

    public boolean changeShopItem(int slotIndex, String newItemId) {
        if (slotIndex < 0 || slotIndex >= 6) return false;
        ShopItem newItem = null;
        if (newItemId != null) {
            newItem = allShopItems.get(newItemId);
            if (newItem == null) {
                plugin.getLogger().warning("Cannot change shop item: Template ID '" + newItemId + "' not found.");
                return false;
            }
            this.currentDailyShopItemIds.set(slotIndex, newItem.getId());
        } else {
            List<ShopItem> availableForSlot = new ArrayList<>(allShopItems.values());
            for (int i = 0; i < 6; i++) {
                if (i == slotIndex) continue;
                String existingId = this.currentDailyShopItemIds.get(i);
                if (existingId != null) {
                    availableForSlot.removeIf(si -> si.getId().equals(existingId));
                }
            }
            if (availableForSlot.isEmpty() && !allShopItems.isEmpty()) {
                availableForSlot = new ArrayList<>(allShopItems.values());
            }
            ShopItem selectedRandom = selectWeightedRandom(availableForSlot);
            if (selectedRandom != null) {
                this.currentDailyShopItemIds.set(slotIndex, selectedRandom.getId());
            } else {
                this.currentDailyShopItemIds.set(slotIndex, null);
            }
        }
        plugin.getLogger().info("Daily shop item for slot " + slotIndex + " administratively changed to: " + (this.currentDailyShopItemIds.get(slotIndex) != null ? this.currentDailyShopItemIds.get(slotIndex) : "EMPTY"));
        plugin.saveServerState();
        return true;
    }
}
