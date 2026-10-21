package org.Cry_Legende.sHJobs.shop;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.logging.Level.SEVERE;

public class ShopItem {

    // --- Champs pour l'achat (existants et conservés) ---
    private final String id;
    private final String displayName;
    private final List<String> lore;
    private final Material material;
    private final int customModelData;
    private final int cost;
    private final JobType requiredJobType;
    private final int requiredJobLevel;
    private final List<String> commandsOnPurchase;
    private final int selectionWeight;

    // --- NOUVEAUTÉ : Champs pour la vente ---
    private final boolean sellable;
    private final int sellPoints;
    private final String sellItemId; // L'ID de l'item dans custom_items.yml

    private ShopItem(Builder builder) {
        // Achat
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.lore = List.copyOf(builder.lore);
        this.material = builder.material;
        this.customModelData = builder.customModelData;
        this.cost = builder.cost;
        this.requiredJobType = builder.requiredJobType;
        this.requiredJobLevel = builder.requiredJobLevel;
        this.commandsOnPurchase = List.copyOf(builder.commandsOnPurchase);
        this.selectionWeight = builder.selectionWeight;

        // --- NOUVEAUTÉ : Initialisation des champs de vente ---
        this.sellable = builder.sellable;
        this.sellPoints = builder.sellPoints;
        this.sellItemId = builder.sellItemId;
    }

    // --- Getters pour l'achat (inchangés) ---
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return new ArrayList<>(lore);
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public int getCost() {
        return cost;
    }

    public Optional<JobType> getRequiredJobType() {
        return Optional.ofNullable(requiredJobType);
    }

    public int getRequiredJobLevel() {
        return requiredJobLevel;
    }

    public List<String> getCommandsOnPurchase() {
        return new ArrayList<>(commandsOnPurchase);
    }

    public int getSelectionWeight() {
        return selectionWeight;
    }

    // --- NOUVEAUTÉ : Getters pour la vente ---
    public boolean isSellable() {
        return sellable;
    }

    public int getSellPoints() {
        return sellPoints;
    }

    public String getSellItemId() {
        return sellItemId;
    }


    public static Optional<ShopItem> fromConfig(SHJobs plugin, String id, ConfigurationSection section) {
        try {
            // --- Logique d'achat (inchangée) ---
            String displayName = section.getString("display_name", "Unnamed Item");
            List<String> lore = section.getStringList("lore");
            String materialName = section.getString("material");
            if (materialName == null) {
                plugin.getLogger().warning("ShopItem '" + id + "' is missing 'material'. Skipping.");
                return Optional.empty();
            }
            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("ShopItem '" + id + "' has invalid 'material': " + materialName + ". Skipping.");
                return Optional.empty();
            }
            int customModelData = section.getInt("custom_model_data", 0);
            int cost = section.getInt("cost", 0);
            if (cost < 0) {
                plugin.getLogger().warning("ShopItem '" + id + "' has negative cost. Setting to 0.");
                cost = 0;
            }
            JobType requiredJobType = null;
            int requiredJobLevel = 1;
            if (section.contains("required_job_type")) {
                String jobTypeStr = section.getString("required_job_type");
                Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
                if (jobTypeOpt.isPresent()) {
                    requiredJobType = jobTypeOpt.get();
                    requiredJobLevel = section.getInt("required_job_level", 1);
                    if (requiredJobLevel < 1) requiredJobLevel = 1;
                } else {
                    plugin.getLogger().warning("ShopItem '" + id + "' has invalid 'required_job_type': " + jobTypeStr + ". Requirement ignored.");
                }
            }
            List<String> commands = section.getStringList("commands_on_purchase");
            int selectionWeight = section.getInt("selection_weight", 1);
            if (selectionWeight <= 0) {
                plugin.getLogger().warning("ShopItem '" + id + "' has non-positive selection_weight. Setting to 1.");
                selectionWeight = 1;
            }

            // --- NOUVEAUTÉ : Chargement des données de vente ---
            boolean sellable = section.getBoolean("sellable", false);
            int sellPoints = section.getInt("sell_points", 0);
            String sellItemId = section.getString("sell_item_id", "");
            if (sellable && (sellItemId.isEmpty() || sellPoints <= 0)) {
                plugin.getLogger().warning("ShopItem '" + id + "' is marked as sellable but is missing 'sell_item_id' or has invalid 'sell_points'. Selling is disabled for this item.");
                sellable = false;
            }
            // --- FIN NOUVEAUTÉ ---

            ShopItem item = new ShopItem.Builder(id, displayName, material)
                .lore(lore)
                .customModelData(customModelData)
                .cost(cost)
                .requiredJob(requiredJobType, requiredJobLevel)
                .commandsOnPurchase(commands)
                .selectionWeight(selectionWeight)
                .sellInfo(sellable, sellPoints, sellItemId) // --- NOUVEAUTÉ ---
                .build();
            return Optional.of(item);

        } catch (Exception e) {
            plugin.getLogger().log(SEVERE, "Critical error parsing ShopItem '" + id + "'. Skipping.", e);
            return Optional.empty();
        }
    }

    // --- Builder mis à jour ---
    public static class Builder {
        // Achat
        private final String id;
        private final String displayName;
        private final Material material;
        private List<String> lore = new ArrayList<>();
        private int customModelData = 0;
        private int cost = 0;
        private JobType requiredJobType = null;
        private int requiredJobLevel = 1;
        private List<String> commandsOnPurchase = new ArrayList<>();
        private int selectionWeight = 1;
        // --- NOUVEAUTÉ : Vente ---
        private boolean sellable = false;
        private int sellPoints = 0;
        private String sellItemId = "";

        public Builder(String id, String displayName, Material material) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
        }

        public Builder lore(List<String> lore) {
            this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
            return this;
        }

        public Builder customModelData(int cmd) {
            this.customModelData = cmd;
            return this;
        }

        public Builder cost(int cost) {
            this.cost = Math.max(0, cost);
            return this;
        }

        public Builder requiredJob(JobType type, int level) {
            this.requiredJobType = type;
            this.requiredJobLevel = Math.max(1, level);
            return this;
        }

        public Builder commandsOnPurchase(List<String> commands) {
            this.commandsOnPurchase = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
            return this;
        }

        public Builder selectionWeight(int weight) {
            this.selectionWeight = Math.max(1, weight);
            return this;
        }

        // --- NOUVEAUTÉ ---
        public Builder sellInfo(boolean isSellable, int points, String sellId) {
            this.sellable = isSellable;
            this.sellPoints = points;
            this.sellItemId = sellId;
            return this;
        }

        public ShopItem build() {
            if (id == null || id.isEmpty()) throw new IllegalStateException("ShopItem ID cannot be null or empty.");
            if (displayName == null || displayName.isEmpty())
                throw new IllegalStateException("ShopItem displayName cannot be null or empty.");
            if (material == null) throw new IllegalStateException("ShopItem Material cannot be null.");
            return new ShopItem(this);
        }
    }
}
