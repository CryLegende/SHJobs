package org.Cry_Legende.sHJobs.jobs;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.utils.SoundUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class JobManager {

    private final SHJobs plugin;
    private final Map<String, JobTemplate> jobTemplates;
    private List<String> currentDailyJobTemplateIds;
    // Vrai si un modèle contient un objectif WALK (évite de suivre chaque déplacement pour rien)
    private volatile boolean walkObjectives;
    private final PlacedBlocks placedBlocks;
    // general.one_job_per_type : une quête par métier, dans l'ordre des métiers
    private volatile boolean oneJobPerType = true;
    // general.level_tiers, triés par niveau croissant : {niveau, multiplicateur}
    private volatile List<double[]> levelTiers = List.of();

    public JobManager(SHJobs plugin) {
        this.plugin = plugin;
        this.jobTemplates = new HashMap<>();
        this.currentDailyJobTemplateIds = new ArrayList<>(Collections.nCopies(6, null));
        this.placedBlocks = new PlacedBlocks(plugin);
    }

    public PlacedBlocks getPlacedBlocks() {
        return placedBlocks;
    }

    public boolean isOneJobPerType() {
        return oneJobPerType;
    }

    /** Multiplicateur de palier pour un niveau de métier (1 sous le premier palier). */
    public double tierMultiplier(int level) {
        double multiplier = 1.0;
        for (double[] tier : levelTiers) {
            if (level >= tier[0]) {
                multiplier = tier[1];
            }
        }
        return multiplier;
    }

    /** Niveau à partir duquel s'applique un multiplicateur (pour le menu), 0 si aucun palier ne le donne. */
    public int tierLevel(double multiplier) {
        for (double[] tier : levelTiers) {
            if (tier[1] == multiplier) {
                return (int) tier[0];
            }
        }
        return 0;
    }

    private void loadGeneralRules() {
        ConfigurationSection general = plugin.getConfigManager().getRawConfig().getConfigurationSection("general");
        oneJobPerType = general == null || general.getBoolean("one_job_per_type", true);
        List<double[]> tiers = new ArrayList<>();
        if (general != null) {
            for (Map<?, ?> raw : general.getMapList("level_tiers")) {
                Object level = raw.get("level");
                Object multiplier = raw.get("multiplier");
                if (level instanceof Number l && multiplier instanceof Number m && l.intValue() > 0 && m.doubleValue() > 0) {
                    tiers.add(new double[]{l.intValue(), m.doubleValue()});
                } else {
                    plugin.getLogger().warning("Invalid entry in general.level_tiers (level and multiplier expected): " + raw);
                }
            }
        }
        tiers.sort(Comparator.comparingDouble(t -> t[0]));
        levelTiers = List.copyOf(tiers);
        boolean placedCount = general != null && general.getBoolean("placed_blocks_count", false);
        placedBlocks.refresh(jobTemplates.values(), !placedCount);
    }

    /**
     * Charge tous les modèles de jobs depuis le fichier de configuration.
     */
    public void loadJobTemplates() {
        jobTemplates.clear();
        walkObjectives = false;
        ConfigurationSection templatesSection = plugin.getConfigManager().getJobTemplatesSection();
        if (templatesSection == null) {
            plugin.getLogger().warning("No 'job_templates' section found in config.yml.");
            return;
        }

        for (String templateId : templatesSection.getKeys(false)) {
            ConfigurationSection templateConfig = templatesSection.getConfigurationSection(templateId);
            if (templateConfig == null) continue;

            Optional<JobTemplate> templateOpt = JobTemplate.fromConfig(plugin, templateId, templateConfig);
            templateOpt.ifPresent(template -> jobTemplates.put(template.getId(), template));
        }
        walkObjectives = jobTemplates.values().stream()
            .anyMatch(template -> template.getObjectives().stream().anyMatch(obj -> obj.getAction() == JobAction.WALK));
        loadGeneralRules();
        plugin.getLogger().info("Loaded " + jobTemplates.size() + " job templates (" + (oneJobPerType ? "one per job type" : "random")
            + ", " + levelTiers.size() + " level tier(s), " + placedBlocks.trackedCount() + " block type(s) ignored when player-placed).");
    }

    public boolean hasWalkObjectives() {
        return walkObjectives;
    }

    public Optional<JobTemplate> getJobTemplate(String templateId) {
        return Optional.ofNullable(jobTemplates.get(templateId));
    }

    public Collection<JobTemplate> getAllJobTemplates() {
        return Collections.unmodifiableCollection(jobTemplates.values());
    }

    /**
     * Sélectionne 6 nouveaux jobs journaliers de manière aléatoire pondérée.
     * Cette méthode notifie ensuite le PlayerCache pour mettre à jour les jobs des joueurs.
     */
    public void selectNewDailyJobs() {
        List<String> newJobIds = pickDailyJobIds();
        this.currentDailyJobTemplateIds = newJobIds;
        if (jobTemplates.isEmpty()) {
            return;
        }

        plugin.getLogger().info("New daily jobs selected: " +
            this.currentDailyJobTemplateIds.stream()
                .map(id -> id != null ? id : "EMPTY")
                .collect(Collectors.joining(", "))
        );
        plugin.getPlayerCache().assignNewDailyJobsToAll();
    }

    /**
     * Tire 6 jobs journaliers de manière aléatoire pondérée, sans rien appliquer : avec SHNetwork, le serveur
     * qui gagne la rotation en base publie ce tirage, les autres serveurs ne tirent pas.
     */
    public List<String> pickDailyJobIds() {
        List<String> newJobIds = new ArrayList<>(Collections.nCopies(6, null));
        if (jobTemplates.isEmpty()) {
            plugin.getLogger().warning("No job templates loaded. Cannot select daily jobs.");
            return newJobIds;
        }

        if (oneJobPerType) {
            return pickOnePerType();
        }

        List<JobTemplate> availableTemplates = new ArrayList<>(jobTemplates.values());
        Set<String> selectedInThisBatch = new HashSet<>();

        for (int i = 0; i < 6; i++) {
            if (availableTemplates.isEmpty()) {
                if (jobTemplates.isEmpty()) break;
                availableTemplates = new ArrayList<>(jobTemplates.values());
            }

            JobTemplate selected = selectWeightedRandom(availableTemplates);
            if (selected != null) {
                newJobIds.set(i, selected.getId());
                selectedInThisBatch.add(selected.getId());

                if (jobTemplates.size() >= 6) {
                    availableTemplates.remove(selected);
                } else if (jobTemplates.size() > selectedInThisBatch.size()) {
                    availableTemplates.remove(selected);
                }
            } else {
                plugin.getLogger().warning("Failed to select a daily job for slot " + (i + 1) + ". The slot will be empty.");
            }
        }
        return newJobIds;
    }

    /**
     * Une quête par métier, case i = i-ème métier (ordre de JobType, celui du menu). Un métier sans quête prend une
     * quête d'un autre métier, jamais deux fois la même.
     */
    private List<String> pickOnePerType() {
        List<String> ids = new ArrayList<>(Collections.nCopies(6, null));
        Set<String> used = new HashSet<>();
        JobType[] types = JobType.values();
        for (int i = 0; i < 6 && i < types.length; i++) {
            JobType type = types[i];
            JobTemplate selected = selectWeightedRandom(templatesOf(type, used));
            if (selected == null) {
                List<JobTemplate> rest = jobTemplates.values().stream().filter(t -> !used.contains(t.getId())).collect(Collectors.toList());
                selected = selectWeightedRandom(rest);
                plugin.getLogger().warning("No job template for " + type + ": slot " + (i + 1) + " takes " + (selected != null ? selected.getId() : "nothing") + ".");
            }
            if (selected != null) {
                ids.set(i, selected.getId());
                used.add(selected.getId());
            }
        }
        return ids;
    }

    private List<JobTemplate> templatesOf(JobType type, Set<String> excluded) {
        return jobTemplates.values().stream()
            .filter(t -> t.getJobType() == type && !excluded.contains(t.getId()))
            .sorted(Comparator.comparing(JobTemplate::getId))
            .collect(Collectors.toList());
    }

    private JobTemplate selectWeightedRandom(List<JobTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            return null;
        }
        double totalWeight = templates.stream().mapToDouble(JobTemplate::getSelectionWeight).sum();
        if (totalWeight <= 0) {
            return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        }
        double randomValue = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulativeWeight = 0.0;
        for (JobTemplate template : templates) {
            cumulativeWeight += template.getSelectionWeight();
            if (randomValue < cumulativeWeight) {
                return template;
            }
        }
        return templates.get(templates.size() - 1);
    }

    public List<String> getCurrentDailyJobTemplateIds() {
        return new ArrayList<>(currentDailyJobTemplateIds);
    }

    public void setCurrentDailyJobIds(List<String> ids) {
        if (ids != null && ids.size() == 6) {
            this.currentDailyJobTemplateIds = new ArrayList<>(ids);
        } else if (ids != null) {
            plugin.getLogger().warning("Attempted to set current daily job IDs with an invalid list (size: " + ids.size() + "). Retaining old or default.");
            if (this.currentDailyJobTemplateIds.stream().allMatch(Objects::isNull)) {
                selectNewDailyJobs();
            }
        } else {
            plugin.getLogger().warning("Attempted to set current daily job IDs with a null list. Selecting new jobs.");
            selectNewDailyJobs();
        }
    }

    /**
     * Assigne les jobs journaliers actuels du serveur à un joueur spécifique.
     *
     * @param playerData Les données du joueur à qui assigner les jobs.
     */
    public void assignDailyJobsToPlayer(PlayerData playerData) {
        playerData.clearDailyJobs();
        for (String templateId : currentDailyJobTemplateIds) {
            if (templateId == null) continue;
            getJobTemplate(templateId).ifPresent(template -> {
                double scale = tierMultiplier(playerData.getLevel(template.getJobType()));
                DailyJob dailyJob = new DailyJob(template, scale);
                playerData.addOrUpdateDailyJob(dailyJob);
            });
        }
    }

    /**
     * Change un job journalier pour un slot donné (commande admin).
     *
     * @param slotIndex     L'index du slot (0-5).
     * @param newTemplateId L'ID du nouveau job, ou null pour en choisir un aléatoire.
     * @return true si le changement a réussi.
     */
    public boolean changeDailyJob(int slotIndex, String newTemplateId) {
        if (slotIndex < 0 || slotIndex >= 6) {
            plugin.getLogger().warning("Invalid slot index for changing daily job: " + slotIndex);
            return false;
        }

        if (newTemplateId != null) {
            if (!jobTemplates.containsKey(newTemplateId)) {
                plugin.getLogger().warning("Cannot change daily job: Template ID '" + newTemplateId + "' not found.");
                return false;
            }
            this.currentDailyJobTemplateIds.set(slotIndex, newTemplateId);
        } else if (oneJobPerType && this.currentDailyJobTemplateIds.get(slotIndex) != null
            && jobTemplates.containsKey(this.currentDailyJobTemplateIds.get(slotIndex))) {
            // Tirage dans le même métier que la quête remplacée
            String current = this.currentDailyJobTemplateIds.get(slotIndex);
            Set<String> excluded = new HashSet<>(this.currentDailyJobTemplateIds.stream().filter(Objects::nonNull).toList());
            List<JobTemplate> sameType = templatesOf(jobTemplates.get(current).getJobType(), excluded);
            JobTemplate selected = selectWeightedRandom(sameType);
            this.currentDailyJobTemplateIds.set(slotIndex, selected != null ? selected.getId() : current);
        } else {
            List<JobTemplate> availableForSlot = new ArrayList<>(jobTemplates.values());
            for (int i = 0; i < 6; i++) {
                if (i == slotIndex) continue;
                String existingId = this.currentDailyJobTemplateIds.get(i);
                if (existingId != null) {
                    availableForSlot.removeIf(jt -> jt.getId().equals(existingId));
                }
            }
            if (availableForSlot.isEmpty() && !jobTemplates.isEmpty()) {
                availableForSlot = new ArrayList<>(jobTemplates.values());
            }
            JobTemplate selectedRandom = selectWeightedRandom(availableForSlot);
            this.currentDailyJobTemplateIds.set(slotIndex, selectedRandom != null ? selectedRandom.getId() : null);
        }

        plugin.getLogger().info("Daily job for slot " + (slotIndex + 1) + " administratively changed to: " + (this.currentDailyJobTemplateIds.get(slotIndex) != null ? this.currentDailyJobTemplateIds.get(slotIndex) : "EMPTY"));
        plugin.getPlayerCache().assignNewDailyJobsToAll();
        plugin.saveServerState();
        return true;
    }

    /**
     * Donne les récompenses à un joueur pour avoir terminé un job.
     *
     * @param player   Le joueur à récompenser.
     * @param dailyJob Le job terminé.
     */
    public void giveJobCompletionRewards(Player player, DailyJob dailyJob) {
        // Appelé depuis le thread d'un monde (mort d'un mob) : récompenses et commandes console sur le thread principal
        if (!SHJobs.onServerThread()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> giveJobCompletionRewards(player, dailyJob));
            return;
        }
        if (player == null || !player.isOnline() || dailyJob == null || !dailyJob.isCompleted()) {
            return;
        }
        JobTemplate template = dailyJob.getTemplate();
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) return;

        boolean leveledUp = false;
        long rewardExp = dailyJob.getRewardExp();
        if (rewardExp > 0) {
            int levelBefore = playerData.getLevel(template.getJobType());
            playerData.addExperience(template.getJobType(), rewardExp);
            int levelAfter = playerData.getLevel(template.getJobType());
            logLevelUps(player, template.getJobType(), levelBefore, levelAfter);
            plugin.getMessages().sendPlayerMessage(player, "rewards.exp_gained",
                "%job_type%", template.getJobType().getDisplayName(),
                "%amount%", String.valueOf(rewardExp));
            leveledUp = levelAfter > levelBefore;
            if (leveledUp) {
                plugin.getMessages().sendPlayerMessage(player, "rewards.level_up",
                    "%job_type%", template.getJobType().getDisplayName(),
                    "%level%", String.valueOf(levelAfter));
            }
        }
        if (template.getRewardJobPoints() > 0) {
            playerData.addJobPoints(template.getRewardJobPoints());
            plugin.getMessages().sendPlayerMessage(player, "rewards.points_gained",
                "%amount%", String.valueOf(template.getRewardJobPoints()));
        }

        // Déclenche une mise à jour du rang du joueur pour ce métier, car son XP/niveau a changé.
        plugin.getLeaderboardManager().updatePlayerRank(player.getUniqueId(), template.getJobType());

        String playerName = player.getName();
        for (String command : template.getRewardCommands()) {
            String processedCommand = command.replace("%player%", playerName)
                .replace("%job_name%", template.getDisplayName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), processedCommand);
        }
        if (template.getRewardMessageOnComplete() != null && !template.getRewardMessageOnComplete().isEmpty()) {
            String message = template.getRewardMessageOnComplete()
                .replace("%player%", playerName)
                .replace("%job_name%", template.getDisplayName());
            player.sendMessage(plugin.getMessages().colorize(message));
        }
        // Un niveau gagné remplace le son de fin de quête : un seul son par récompense.
        SoundUtil.Spec levelUpSound = leveledUp ? SoundUtil.parse(plugin.getConfigManager().getLevelUpSound()) : null;
        if (levelUpSound != null) {
            player.playSound(player.getLocation(), levelUpSound.sound(), levelUpSound.volume(), levelUpSound.pitch());
        } else {
            template.getRewardSoundOnComplete().ifPresent(sound -> {
                player.playSound(player.getLocation(), sound, template.getRewardSoundVolume(), template.getRewardSoundPitch());
            });
        }

        giveAllDailyCompletedBonus(player, playerData);
    }

    /**
     * Écrit une ligne dans SHLogs par niveau gagné grâce à l'XP d'une quête terminée.
     */
    private void logLevelUps(Player player, JobType jobType, int levelBefore, int levelAfter) {
        for (int level = levelBefore + 1; level <= levelAfter; level++) {
            plugin.getConfigManager().writeLog("level_up", "niveaux",
                "%player% -> monte au niveau %level% (%job%)",
                "%player%", player.getName(),
                "%level%", String.valueOf(level),
                "%job%", plugin.getConfigManager().plainText(jobType.getDisplayName()));
        }
    }

    /**
     * Bonus de points quand le joueur vient de terminer la dernière des quêtes du jour
     * ({@code general.all_daily_jobs_bonus_points}, 0 = désactivé). Appelé à chaque fin de quête :
     * le bonus tombe une seule fois, au moment où la dernière quête se termine.
     */
    private void giveAllDailyCompletedBonus(Player player, PlayerData playerData) {
        int bonus = plugin.getConfigManager().getRawConfig().getInt("general.all_daily_jobs_bonus_points", 0);
        if (bonus <= 0) {
            return;
        }
        List<String> today = currentDailyJobTemplateIds.stream().filter(Objects::nonNull).toList();
        if (today.isEmpty()) {
            return;
        }
        for (String templateId : today) {
            DailyJob job = playerData.getActiveDailyJobs().get(templateId);
            if (job == null || !job.isCompleted()) {
                return;
            }
        }
        playerData.addJobPoints(bonus);
        plugin.getMessages().sendPlayerMessage(player, "rewards.all_daily_completed",
            "%amount%", String.valueOf(bonus),
            "%count%", String.valueOf(today.size()));
    }
}
