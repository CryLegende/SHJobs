package org.Cry_Legende.sHJobs.jobs;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class JobManager {

    private final SHJobs plugin;
    private final Map<String, JobTemplate> jobTemplates;
    private List<String> currentDailyJobTemplateIds;

    public JobManager(SHJobs plugin) {
        this.plugin = plugin;
        this.jobTemplates = new HashMap<>();
        this.currentDailyJobTemplateIds = new ArrayList<>(Collections.nCopies(6, null));
    }

    /**
     * Charge tous les modèles de jobs depuis le fichier de configuration.
     */
    public void loadJobTemplates() {
        jobTemplates.clear();
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
        plugin.getLogger().info("Loaded " + jobTemplates.size() + " job templates.");
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
        List<String> newJobIds = new ArrayList<>(Collections.nCopies(6, null));
        if (jobTemplates.isEmpty()) {
            plugin.getLogger().warning("No job templates loaded. Cannot select daily jobs.");
            this.currentDailyJobTemplateIds = newJobIds;
            return;
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
        this.currentDailyJobTemplateIds = newJobIds;

        plugin.getLogger().info("New daily jobs selected: " +
            this.currentDailyJobTemplateIds.stream()
                .map(id -> id != null ? id : "EMPTY")
                .collect(Collectors.joining(", "))
        );
        plugin.getPlayerCache().assignNewDailyJobsToAll();
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
                DailyJob dailyJob = new DailyJob(template);
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
        if (player == null || !player.isOnline() || dailyJob == null || !dailyJob.isCompleted()) {
            return;
        }
        JobTemplate template = dailyJob.getTemplate();
        PlayerData playerData = plugin.getPlayerCache().getPlayerData(player.getUniqueId());
        if (playerData == null) return;

        if (template.getRewardJobExp() > 0) {
            playerData.addExperience(template.getJobType(), template.getRewardJobExp());
            plugin.getMessages().sendPlayerMessage(player, "rewards.exp_gained",
                "%job_type%", template.getJobType().getDisplayName(),
                "%amount%", String.valueOf(template.getRewardJobExp()));
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
        template.getRewardSoundOnComplete().ifPresent(sound -> {
            player.playSound(player.getLocation(), sound, template.getRewardSoundVolume(), template.getRewardSoundPitch());
        });
    }
}
