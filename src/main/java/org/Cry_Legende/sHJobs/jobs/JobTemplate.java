package org.Cry_Legende.sHJobs.jobs;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JobTemplate {

    private final String id; // Identifiant unique du template (ex: bucheron_oak_wood_1)
    private final String displayName;
    private final List<String> descriptionLore; // Sera utilisé pour le lore de l'item du job
    private final JobType jobType; // Le métier associé
    private final List<JobObjective> objectives;

    // Récompenses
    private final long rewardJobExp;
    private final int rewardJobPoints;
    private final List<String> rewardCommands;
    private final String rewardMessageOnComplete;
    private final transient Sound rewardSoundOnComplete; // transient car Sound n'est pas directement sérialisable
    private final float rewardSoundVolume;
    private final float rewardSoundPitch;


    private final int selectionWeight; // Pour la sélection aléatoire

    // Constructeur privé pour le Builder
    private JobTemplate(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.descriptionLore = builder.descriptionLore;
        this.jobType = builder.jobType;
        this.objectives = List.copyOf(builder.objectives); // Rendre la liste immuable
        this.rewardJobExp = builder.rewardJobExp;
        this.rewardJobPoints = builder.rewardJobPoints;
        this.rewardCommands = List.copyOf(builder.rewardCommands);
        this.rewardMessageOnComplete = builder.rewardMessageOnComplete;
        this.rewardSoundOnComplete = builder.rewardSoundOnComplete;
        this.rewardSoundVolume = builder.rewardSoundVolume;
        this.rewardSoundPitch = builder.rewardSoundPitch;
        this.selectionWeight = builder.selectionWeight;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescriptionLore() {
        // Retourne une copie pour l'immutabilité si la liste originale l'est déjà pas
        return new ArrayList<>(descriptionLore);
    }

    public JobType getJobType() {
        return jobType;
    }

    public List<JobObjective> getObjectives() {
        // Retourne une copie pour l'immutabilité
        return new ArrayList<>(objectives);
    }

    public Optional<JobObjective> getObjectiveById(String objectiveId) {
        return objectives.stream().filter(obj -> obj.getId().equals(objectiveId)).findFirst();
    }

    public long getRewardJobExp() {
        return rewardJobExp;
    }

    public int getRewardJobPoints() {
        return rewardJobPoints;
    }

    public List<String> getRewardCommands() {
        return new ArrayList<>(rewardCommands);
    }

    public String getRewardMessageOnComplete() {
        return rewardMessageOnComplete;
    }

    public Optional<Sound> getRewardSoundOnComplete() {
        return Optional.ofNullable(rewardSoundOnComplete);
    }

    public float getRewardSoundVolume() {
        return rewardSoundVolume;
    }

    public float getRewardSoundPitch() {
        return rewardSoundPitch;
    }

    public int getSelectionWeight() {
        return selectionWeight;
    }

    // Méthode statique pour charger un JobTemplate depuis une ConfigurationSection
    // Cette méthode sera appelée par JobManager
    public static Optional<JobTemplate> fromConfig(SHJobs plugin, String id, ConfigurationSection section) {
        try {
            String displayName = section.getString("display_name", "Unnamed Job");
            List<String> description = section.getStringList("description");
            if (description.isEmpty() && section.contains("description")) { // Handle single line description
                description.add(section.getString("description"));
            }

            String jobTypeStr = section.getString("job_type");
            if (jobTypeStr == null) {
                plugin.getLogger().warning("JobTemplate '" + id + "' is missing 'job_type'. Skipping.");
                return Optional.empty();
            }
            Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
            if (jobTypeOpt.isEmpty()) {
                plugin.getLogger().warning("JobTemplate '" + id + "' has invalid 'job_type': " + jobTypeStr + ". Skipping.");
                return Optional.empty();
            }

            List<JobObjective> objectives = new ArrayList<>();
            ConfigurationSection objectivesSection = section.getConfigurationSection("objectives");
            if (objectivesSection == null || objectivesSection.getKeys(false).isEmpty()) {
                plugin.getLogger().warning("JobTemplate '" + id + "' has no objectives defined. Skipping.");
                return Optional.empty();
            }

            for (String objectiveKey : objectivesSection.getKeys(false)) {
                ConfigurationSection objSection = objectivesSection.getConfigurationSection(objectiveKey);
                if (objSection == null) continue;

                String objectiveId = objSection.getString("id", objectiveKey); // Utilise la clé de section comme ID par défaut
                String actionStr = objSection.getString("type");
                if (actionStr == null) {
                    plugin.getLogger().warning("Objective '" + objectiveId + "' in JobTemplate '" + id + "' is missing 'type'. Skipping objective.");
                    continue;
                }
                Optional<JobAction> actionOpt = JobAction.fromString(actionStr);
                if (actionOpt.isEmpty()) {
                    plugin.getLogger().warning("Objective '" + objectiveId + "' in JobTemplate '" + id + "' has invalid 'type': " + actionStr + ". Skipping objective.");
                    continue;
                }

                int amount = objSection.getInt("amount", 1);
                JobObjective.Builder objBuilder = new JobObjective.Builder(objectiveId, actionOpt.get(), amount);

                // Matériaux (peut être une liste ou une chaîne unique)
                List<String> materialNames = new ArrayList<>();
                if (objSection.isList("material")) {
                    materialNames.addAll(objSection.getStringList("material"));
                } else if (objSection.isString("material")) {
                    materialNames.add(objSection.getString("material"));
                }
                // Aussi pour item_type (FISH, CRAFT)
                if (objSection.isList("item_type")) {
                    materialNames.addAll(objSection.getStringList("item_type"));
                } else if (objSection.isString("item_type")) {
                    materialNames.add(objSection.getString("item_type"));
                }


                if (!materialNames.isEmpty()) {
                    List<org.bukkit.Material> materials = materialNames.stream()
                        .map(name -> {
                            try {
                                return org.bukkit.Material.valueOf(name.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid material name '" + name + "' in objective '" + objectiveId + "' for job '" + id + "'. Skipping material.");
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
                    if (!materials.isEmpty()) {
                        objBuilder.materials(materials);
                    } else if (actionOpt.get() == JobAction.BREAK || actionOpt.get() == JobAction.PLACE || actionOpt.get() == JobAction.CRAFT || actionOpt.get() == JobAction.FISH || actionOpt.get() == JobAction.SMELT || actionOpt.get() == JobAction.STRIP) {
                        plugin.getLogger().warning("Objective '" + objectiveId + "' of type " + actionOpt.get() + " in JobTemplate '" + id + "' requires valid materials but none were found or parsed. Skipping objective.");
                        continue;
                    }
                }


                if (objSection.contains("entity_type")) {
                    try {
                        objBuilder.entityType(org.bukkit.entity.EntityType.valueOf(objSection.getString("entity_type").toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid entity_type '" + objSection.getString("entity_type") + "' in objective '" + objectiveId + "' for job '" + id + "'.");
                    }
                }

                if (objSection.contains("potion_type")) { // Pour BREW
                    try {
                        org.bukkit.potion.PotionType potionType = org.bukkit.potion.PotionType.valueOf(objSection.getString("potion_type").toUpperCase());
                        boolean extended = objSection.getBoolean("potion_extended", false);
                        boolean upgraded = objSection.getBoolean("potion_upgraded", false);
                        objBuilder.potionData(potionType, extended, upgraded);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid potion_type '" + objSection.getString("potion_type") + "' in objective '" + objectiveId + "' for job '" + id + "'.");
                    }
                }

                if (objSection.contains("required_age")) {
                    objBuilder.requiredAge(objSection.getInt("required_age"));
                }

                if (objSection.contains("display_name")) { // Nom d'affichage optionnel pour l'objectif
                    objBuilder.objectiveDisplayName(objSection.getString("display_name"));
                }

                try {
                    objectives.add(objBuilder.build());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Failed to build objective '" + objectiveId + "' for job '" + id + "': " + e.getMessage() + ". Skipping objective.");
                }
            }

            if (objectives.isEmpty() && (objectivesSection != null && !objectivesSection.getKeys(false).isEmpty())) {
                // S'il y avait une section objectives mais qu'aucun n'a pu être chargé.
                plugin.getLogger().warning("JobTemplate '" + id + "' had an objectives section, but no valid objectives could be loaded. Skipping job template.");
                return Optional.empty();
            } else if (objectives.isEmpty()) {
                plugin.getLogger().warning("JobTemplate '" + id + "' has no valid objectives. Skipping job template.");
                return Optional.empty();
            }


            // Récompenses
            ConfigurationSection rewardsSection = section.getConfigurationSection("rewards");
            long rewardExp = 0;
            int rewardPoints = 0;
            List<String> rewardCmds = Collections.emptyList();
            String rewardMsg = null;
            Sound rewardSnd = null;
            float rewardVol = 1.0f;
            float rewardPitch = 1.0f;

            if (rewardsSection != null) {
                rewardExp = rewardsSection.getLong("job_exp", 0);
                rewardPoints = rewardsSection.getInt("job_points", 0);
                rewardCmds = rewardsSection.getStringList("commands");
                rewardMsg = rewardsSection.getString("message_on_complete");
                String soundStr = rewardsSection.getString("sound_on_complete");
                if (soundStr != null && !soundStr.isEmpty()) {
                    String[] parts = soundStr.split(":");
                    try {
                        rewardSnd = Sound.valueOf(parts[0].toUpperCase());
                        if (parts.length > 1) rewardVol = Float.parseFloat(parts[1]);
                        if (parts.length > 2) rewardPitch = Float.parseFloat(parts[2]);
                    } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                        plugin.getLogger().warning("Invalid sound format '" + soundStr + "' in rewards for job '" + id + "'.");
                    }
                }
            }

            int selectionWeight = section.getInt("selection_weight", 1);
            if (selectionWeight <= 0) {
                plugin.getLogger().warning("JobTemplate '" + id + "' has non-positive selection_weight. Setting to 1.");
                selectionWeight = 1;
            }

            JobTemplate template = new JobTemplate.Builder(id, displayName, jobTypeOpt.get())
                .descriptionLore(description)
                .objectives(objectives)
                .rewardJobExp(rewardExp)
                .rewardJobPoints(rewardPoints)
                .rewardCommands(rewardCmds)
                .rewardMessageOnComplete(rewardMsg)
                .rewardSound(rewardSnd, rewardVol, rewardPitch)
                .selectionWeight(selectionWeight)
                .build();
            return Optional.of(template);

        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Critical error parsing JobTemplate '" + id + "'. Skipping.", e);
            return Optional.empty();
        }
    }


    // Builder Pattern
    public static class Builder {
        private final String id;
        private final String displayName;
        private final JobType jobType;
        private List<String> descriptionLore = new ArrayList<>();
        private List<JobObjective> objectives = new ArrayList<>();
        private long rewardJobExp = 0;
        private int rewardJobPoints = 0;
        private List<String> rewardCommands = new ArrayList<>();
        private String rewardMessageOnComplete;
        private Sound rewardSoundOnComplete;
        private float rewardSoundVolume = 1.0f;
        private float rewardSoundPitch = 1.0f;
        private int selectionWeight = 1;

        public Builder(String id, String displayName, JobType jobType) {
            this.id = id;
            this.displayName = displayName;
            this.jobType = jobType;
        }

        public Builder descriptionLore(List<String> descriptionLore) {
            this.descriptionLore = descriptionLore != null ? new ArrayList<>(descriptionLore) : new ArrayList<>();
            return this;
        }

        public Builder objectives(List<JobObjective> objectives) {
            this.objectives = objectives != null ? new ArrayList<>(objectives) : new ArrayList<>();
            return this;
        }

        public Builder addObjective(JobObjective objective) {
            if (objective != null) {
                this.objectives.add(objective);
            }
            return this;
        }

        public Builder rewardJobExp(long rewardJobExp) {
            this.rewardJobExp = rewardJobExp;
            return this;
        }

        public Builder rewardJobPoints(int rewardJobPoints) {
            this.rewardJobPoints = rewardJobPoints;
            return this;
        }

        public Builder rewardCommands(List<String> rewardCommands) {
            this.rewardCommands = rewardCommands != null ? new ArrayList<>(rewardCommands) : new ArrayList<>();
            return this;
        }

        public Builder rewardMessageOnComplete(String message) {
            this.rewardMessageOnComplete = message;
            return this;
        }

        public Builder rewardSound(Sound sound, float volume, float pitch) {
            this.rewardSoundOnComplete = sound;
            this.rewardSoundVolume = volume;
            this.rewardSoundPitch = pitch;
            return this;
        }

        public Builder selectionWeight(int selectionWeight) {
            this.selectionWeight = Math.max(1, selectionWeight); // Assurer un poids positif
            return this;
        }

        public JobTemplate build() {
            if (id == null || id.isEmpty()) throw new IllegalStateException("JobTemplate ID cannot be null or empty.");
            if (displayName == null || displayName.isEmpty())
                throw new IllegalStateException("JobTemplate displayName cannot be null or empty.");
            if (jobType == null) throw new IllegalStateException("JobTemplate JobType cannot be null.");
            if (objectives.isEmpty()) throw new IllegalStateException("JobTemplate must have at least one objective.");
            return new JobTemplate(this);
        }
    }
}
