package org.Cry_Legende.sHJobs.jobs;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class JobObjective {

    private final String id; // Identifiant unique de l'objectif au sein d'un JobTemplate
    private final JobAction action;
    private final List<Material> materials; // Pour BREAK, PLACE, CRAFT, FISH (item_type), SMELT (input/output)
    private final EntityType entityType;    // Pour KILL
    private final PotionType potionType;    // Pour BREW (type de base de la potion)
    private final boolean potionExtended;   // Pour BREW
    private final boolean potionUpgraded;   // Pour BREW
    private final PotionType requiredPotionType; // Pour BREW : variante exacte (SWIFTNESS, LONG_SWIFTNESS ou STRONG_SWIFTNESS), null si elle n'existe pas
    private final int amount;
    private final int requiredAge;          // Pour BREAK (cultures)
    private final String objectiveDisplayName; // Nom affichable pour cet objectif si différent de l'ID

    // Constructeur principal
    private JobObjective(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Objective ID cannot be null");
        this.action = Objects.requireNonNull(builder.action, "JobAction cannot be null");
        this.materials = builder.materials != null ? List.copyOf(builder.materials) : Collections.emptyList();
        this.entityType = builder.entityType;
        this.potionType = builder.potionType;
        this.potionExtended = builder.potionExtended;
        this.potionUpgraded = builder.potionUpgraded;
        this.requiredPotionType = variantOf(builder.potionType, builder.potionExtended, builder.potionUpgraded);
        this.amount = builder.amount;
        this.requiredAge = builder.requiredAge;
        this.objectiveDisplayName = builder.objectiveDisplayName != null ? builder.objectiveDisplayName : this.id;

        // Validations
        if (amount <= 0) {
            throw new IllegalArgumentException("Objective amount must be positive: " + amount);
        }
        if (action == JobAction.BREAK || action == JobAction.PLACE || action == JobAction.STRIP || action == JobAction.CRAFT || action == JobAction.FISH || action == JobAction.SMELT) {
            if (this.materials.isEmpty()) {
                throw new IllegalArgumentException("Materials list cannot be empty for action: " + action);
            }
        }
        if (action == JobAction.KILL && this.entityType == null) {
            throw new IllegalArgumentException("EntityType cannot be null for KILL action");
        }
        if (action == JobAction.BREW && this.potionType == null) {
            // Note: Minecraft default potion is AWKWARD, MUNDANE, THICK, or WATER.
            // We might require a specific effect type.
            throw new IllegalArgumentException("PotionType cannot be null for BREW action if specific potion is required. Use WATER for any potion if not specific.");
        }
    }

    public String getId() {
        return id;
    }

    public JobAction getAction() {
        return action;
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public boolean matchesMaterial(Material material) {
        return materials.contains(material);
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public PotionType getPotionType() {
        return potionType;
    }

    public boolean isPotionExtended() {
        return potionExtended;
    }

    public boolean isPotionUpgraded() {
        return potionUpgraded;
    }

    /**
     * Potion attendue pour BREW. Depuis 1.20.5 les variantes prolongée et renforcée sont des types à part entière
     * (LONG_x, STRONG_x) : potion_extended / potion_upgraded de la config y sont ramenés.
     */
    public PotionType getRequiredPotionType() {
        if (action != JobAction.BREW) {
            return null;
        }
        return requiredPotionType;
    }

    private static PotionType variantOf(PotionType base, boolean extended, boolean upgraded) {
        if (base == null) {
            return null;
        }
        if (extended && upgraded) {
            return null; // PotionData refusait déjà cette combinaison
        }
        if (!extended && !upgraded) {
            return base;
        }
        try {
            return PotionType.valueOf((upgraded ? "STRONG_" : "LONG_") + base.name());
        } catch (IllegalArgumentException e) {
            return null; // pas de variante pour cette potion
        }
    }

    /**
     * Type de potion lu en config : nom actuel (SWIFTNESS), ancien nom (SPEED) ou clé (minecraft:swiftness).
     *
     * @return null si inconnu
     */
    public static PotionType potionTypeFromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.indexOf(':') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.POTION.get(key);
        }
        String name = switch (s.toUpperCase(Locale.ROOT)) {
            // noms d'avant 1.20.5
            case "SPEED" -> "SWIFTNESS";
            case "JUMP" -> "LEAPING";
            case "INSTANT_HEAL" -> "HEALING";
            case "INSTANT_DAMAGE" -> "HARMING";
            case "REGEN" -> "REGENERATION";
            default -> s.toUpperCase(Locale.ROOT);
        };
        try {
            return PotionType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int getAmount() {
        return amount;
    }

    public int getRequiredAge() {
        return requiredAge;
    }

    public String getObjectiveDisplayName() {
        return objectiveDisplayName;
    }

    // --- Méthodes de vérification de la progression ---
    // Ces méthodes seront appelées par JobProgressListener

    public boolean checkBreakCondition(Material brokenMaterial, int age) {
        if (action != JobAction.BREAK) return false;
        if (!materials.contains(brokenMaterial)) return false;
        return requiredAge == -1 || age == requiredAge; // -1 signifie que l'âge n'est pas pertinent
    }

    public boolean checkPlaceCondition(Material placedMaterial) {
        if (action != JobAction.PLACE) return false;
        return materials.contains(placedMaterial);
    }

    public boolean checkCraftCondition(ItemStack craftedItem) {
        if (action != JobAction.CRAFT) return false;
        return materials.contains(craftedItem.getType()); // Simplifié, pourrait vérifier NBT plus tard
    }

    public boolean checkFishCondition(Material fishedItemMaterial) {
        if (action != JobAction.FISH) return false;
        return materials.contains(fishedItemMaterial);
    }

    public boolean checkKillCondition(EntityType killedEntityType) {
        if (action != JobAction.KILL) return false;
        return this.entityType == killedEntityType;
    }

    public boolean checkSmeltCondition(Material smeltedResultMaterial) { // Ou Material inputMaterial
        if (action != JobAction.SMELT) return false;
        return materials.contains(smeltedResultMaterial); // Suppose que 'materials' contient le résultat attendu
    }

    public boolean checkBrewCondition(PotionType brewedPotionType) {
        if (action != JobAction.BREW) return false;
        if (potionType == null) return true; // Si aucun type spécifique, n'importe quelle potion compte
        return brewedPotionType != null && brewedPotionType == requiredPotionType;
    }

    public boolean checkStripCondition(Material strippedMaterial) {
        if (action != JobAction.STRIP) return false;
        // Le matériau après strip est généralement Material.STRIPPED_OAK_LOG, etc.
        // On s'attend à ce que 'materials' dans la config contienne le *type de log original*.
        // Le listener devra convertir le Material.STRIPPED_X_LOG en X_LOG pour la comparaison.
        // Ou alors, on configure directement le STRIPPED_X_LOG.
        // Pour l'instant, supposons que 'materials' contient le type de log *non-strippé*.
        // La logique de correspondance sera dans le listener.
        // Si 'materials' contient le log strippé :
        return materials.contains(strippedMaterial);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(action).append(" ").append(amount).append(" ");
        if (!materials.isEmpty()) {
            sb.append(materials.stream().map(Material::name).collect(Collectors.joining(",")));
        } else if (entityType != null) {
            sb.append(entityType.name());
        } else if (potionType != null) {
            sb.append(potionType.name())
                .append(potionExtended ? " (Extended)" : "")
                .append(potionUpgraded ? " (Upgraded)" : "");
        }
        if (requiredAge != -1) {
            sb.append(" [Age: ").append(requiredAge).append("]");
        }
        return sb.toString();
    }

    // Builder Pattern
    public static class Builder {
        private String id;
        private JobAction action;
        private List<Material> materials;
        private EntityType entityType;
        private PotionType potionType;
        private boolean potionExtended = false;
        private boolean potionUpgraded = false;
        private int amount = 1;
        private int requiredAge = -1; // -1 signifie non applicable
        private String objectiveDisplayName;

        public Builder(String id, JobAction action, int amount) {
            this.id = id;
            this.action = action;
            this.amount = amount;
        }

        public Builder materials(List<Material> materials) {
            this.materials = materials;
            return this;
        }

        public Builder material(Material material) {
            this.materials = Collections.singletonList(material);
            return this;
        }

        public Builder entityType(EntityType entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder potionData(PotionType potionType, boolean extended, boolean upgraded) {
            this.potionType = potionType;
            this.potionExtended = extended;
            this.potionUpgraded = upgraded;
            return this;
        }

        public Builder potionType(PotionType potionType) {
            this.potionType = potionType;
            return this;
        }

        public Builder requiredAge(int requiredAge) {
            this.requiredAge = requiredAge;
            return this;
        }

        public Builder objectiveDisplayName(String displayName) {
            this.objectiveDisplayName = displayName;
            return this;
        }

        public JobObjective build() {
            return new JobObjective(this);
        }
    }
}
