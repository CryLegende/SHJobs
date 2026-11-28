package org.Cry_Legende.sHJobs.jobs;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
    // general.level_tiers_ignored_jobs : métiers dont les quêtes ne suivent jamais les paliers
    private volatile Set<JobType> tierIgnoredJobs = Set.of();

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

    /** Multiplicateur de palier d'une quête du métier pour ce niveau (1 sous le premier palier ou pour un métier ignoré). */
    public double tierMultiplier(JobType jobType, int level) {
        if (tierIgnoredJobs.contains(jobType)) {
            return 1.0;
        }
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
        Set<JobType> ignored = EnumSet.noneOf(JobType.class);
        if (general != null) {
            for (String name : general.getStringList("level_tiers_ignored_jobs")) {
                try {
                    ignored.add(JobType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown job in general.level_tiers_ignored_jobs: " + name);
                }
            }
        }
        tierIgnoredJobs = Set.copyOf(ignored);
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
        checkRequirements();
        loadGeneralRules();
        plugin.getLogger().info("Loaded " + jobTemplates.size() + " job templates (" + (oneJobPerType ? "one per job type" : "random")
            + ", " + levelTiers.size() + " level tier(s)" + (tierIgnoredJobs.isEmpty() ? "" : " except " + tierIgnoredJobs)
            + ", " + placedBlocks.trackedCount() + " block type(s) ignored when player-placed).");
    }

    /** Quêtes à condition : leur quête de remplacement doit exister, sinon la condition est ignorée (quête donnée à tous). */
    private void checkRequirements() {
        int count = 0;
        for (JobTemplate template : jobTemplates.values()) {
            if (template.getRequirement().isEmpty()) continue;
            String fallbackId = template.getRequirement().get().getFallbackId();
            JobTemplate fallback = jobTemplates.get(fallbackId);
            if (fallback == null) {
                plugin.getLogger().warning("JobTemplate '" + template.getId() + "': fallback '" + fallbackId + "' not found, the job is given to everyone.");
                continue;
            }
            if (fallback.getJobType() != template.getJobType()) {
                plugin.getLogger().warning("JobTemplate '" + template.getId() + "': fallback '" + fallbackId + "' is a " + fallback.getJobType()
                    + " job, not " + template.getJobType() + ".");
            }
            count++;
        }
        long fallbackOnly = jobTemplates.values().stream().filter(t -> t.getSelectionWeight() == 0).count();
        if (count > 0 || fallbackOnly > 0) {
            plugin.getLogger().info(count + " job template(s) with a requirement (players who don't meet it get the fallback job), "
                + fallbackOnly + " fallback-only (selection_weight 0).");
        }
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

        List<JobTemplate> availableTemplates = drawable();
        Set<String> selectedInThisBatch = new HashSet<>();

        for (int i = 0; i < 6; i++) {
            if (availableTemplates.isEmpty()) {
                if (jobTemplates.isEmpty()) break;
                availableTemplates = drawable();
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
                List<JobTemplate> rest = drawable().stream().filter(t -> !used.contains(t.getId())).collect(Collectors.toList());
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

    /** Quêtes qui peuvent sortir au tirage : selection_weight 0 = quête de remplacement seulement (version débutant…). */
    private List<JobTemplate> drawable() {
        return jobTemplates.values().stream().filter(t -> t.getSelectionWeight() > 0).collect(Collectors.toList());
    }

    private List<JobTemplate> templatesOf(JobType type, Set<String> excluded) {
        return jobTemplates.values().stream()
            .filter(t -> t.getSelectionWeight() > 0)
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
                JobTemplate given = templateFor(playerData, template);
                playerData.addOrUpdateDailyJob(newDailyJob(playerData, given));
            });
        }
    }

    private DailyJob newDailyJob(PlayerData playerData, JobTemplate template) {
        double scale = tierMultiplier(template.getJobType(), playerData.getLevel(template.getJobType()));
        return new DailyJob(template, scale);
    }

    /** Au plus autant de quêtes dans une chaîne de remplacement (quête, remplacement, remplacement du remplacement…). */
    private static final int MAX_CHAIN = 5;

    /**
     * La quête que reçoit ce joueur pour une quête de la rotation : la première de sa chaîne (elle, puis ses
     * remplacements requirement.fallback successifs) dont il remplit la condition, sinon la dernière de la chaîne.
     * Ex. Roches Variées (île 1 500) → Excavation Pierre (métier niveau 5) → Excavation Pierre débutant.
     */
    public JobTemplate templateFor(PlayerData playerData, JobTemplate rotationTemplate) {
        List<JobTemplate> chain = chainOf(rotationTemplate);
        if (chain.size() <= 1) {
            return rotationTemplate;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerData.getPlayerUUID());
        for (int i = 0; i < chain.size() - 1; i++) {
            if (chain.get(i).getRequirement().get().isMetBy(player, playerData)) {
                return chain.get(i);
            }
        }
        return chain.get(chain.size() - 1);
    }

    /**
     * La quête de la rotation suivie de ses remplacements successifs. La chaîne s'arrête sur un remplacement
     * introuvable, déjà vu (boucle) ou faisant partie de la rotation du jour (le joueur l'a dans une autre case).
     */
    private List<JobTemplate> chainOf(JobTemplate rotationTemplate) {
        if (rotationTemplate == null) {
            return List.of();
        }
        List<JobTemplate> chain = new ArrayList<>(2);
        chain.add(rotationTemplate);
        Set<String> seen = new HashSet<>();
        seen.add(rotationTemplate.getId());
        JobTemplate current = rotationTemplate;
        while (chain.size() < MAX_CHAIN && current.getRequirement().isPresent()) {
            String fallbackId = current.getRequirement().get().getFallbackId();
            JobTemplate fallback = jobTemplates.get(fallbackId);
            if (fallback == null || !seen.add(fallbackId) || currentDailyJobTemplateIds.contains(fallbackId)) {
                break;
            }
            chain.add(fallback);
            current = fallback;
        }
        return chain;
    }

    /**
     * Quête du jour du joueur dans une case (0-5) : la quête de la rotation, ou celle de sa chaîne de remplacement
     * qu'il a reçue à sa place. C'est par là que passent le menu, les placeholders et le bonus des 6 quêtes.
     */
    public Optional<DailyJob> dailyJobForSlot(PlayerData playerData, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= currentDailyJobTemplateIds.size()) {
            return Optional.empty();
        }
        String templateId = currentDailyJobTemplateIds.get(slotIndex);
        if (templateId == null) {
            return Optional.empty();
        }
        DailyJob job = playerData.getActiveDailyJobs().get(templateId);
        if (job != null) {
            return Optional.of(job);
        }
        for (JobTemplate template : chainOf(jobTemplates.get(templateId))) {
            job = playerData.getActiveDailyJobs().get(template.getId());
            if (job != null) {
                return Optional.of(job);
            }
        }
        return Optional.empty();
    }

    /**
     * Le joueur a bien une quête par case de la rotation du jour (la quête ou une de sa chaîne de remplacement), et
     * rien d'autre. Sinon ses quêtes datent d'une autre rotation et sont à refaire.
     */
    public boolean isInSyncWithRotation(PlayerData playerData) {
        Map<String, DailyJob> held = playerData.getActiveDailyJobs();
        Set<String> accepted = new HashSet<>();
        int slots = 0;
        for (int i = 0; i < currentDailyJobTemplateIds.size(); i++) {
            String templateId = currentDailyJobTemplateIds.get(i);
            if (templateId == null) continue;
            slots++;
            accepted.add(templateId);
            if (dailyJobForSlot(playerData, i).isEmpty()) {
                return false;
            }
            chainOf(jobTemplates.get(templateId)).forEach(template -> accepted.add(template.getId()));
        }
        return held.size() == slots && accepted.containsAll(held.keySet());
    }

    /**
     * Une quête encore jamais commencée suit les conditions du moment : le joueur reçoit la quête de sa chaîne qui
     * lui correspond maintenant (île agrandie, niveau de métier monté, lecture ratée à la connexion). Une quête
     * commencée ou finie ne change plus de la journée. Thread principal.
     */
    public void reviewRequirements(PlayerData playerData) {
        for (int i = 0; i < currentDailyJobTemplateIds.size(); i++) {
            String templateId = currentDailyJobTemplateIds.get(i);
            JobTemplate rotation = templateId != null ? jobTemplates.get(templateId) : null;
            if (chainOf(rotation).size() <= 1) continue;
            Optional<DailyJob> current = dailyJobForSlot(playerData, i);
            if (current.isEmpty() || !isUntouched(current.get())) continue;
            JobTemplate wanted = templateFor(playerData, rotation);
            if (!wanted.getId().equals(current.get().getTemplate().getId())) {
                playerData.removeDailyJob(current.get().getTemplate().getId());
                playerData.addOrUpdateDailyJob(newDailyJob(playerData, wanted));
                plugin.getLogger().info("Daily job slot " + (i + 1) + " of " + playerData.getPlayerUUID() + ": "
                    + current.get().getTemplate().getId() + " -> " + wanted.getId() + ".");
            }
        }
    }

    /**
     * Niveaux rattrapés sur l'XP déjà gagnée quand un palier a baissé dans config.yml (experience.*) : le joueur prend
     * tout de suite les niveaux que son XP lui donne déjà, sans attendre sa prochaine quête. Ne baisse jamais un niveau.
     */
    public void catchUpLevels(PlayerData playerData) {
        for (JobType type : JobType.values()) {
            int before = playerData.getLevel(type);
            playerData.checkLevelUp(type);
            int after = playerData.getLevel(type);
            if (after > before) {
                plugin.getLogger().info("Job level of " + playerData.getPlayerUUID() + " caught up on its XP: " + type + " " + before + " -> " + after + ".");
            }
        }
    }

    private static boolean isUntouched(DailyJob job) {
        return !job.isCompleted() && job.getObjectiveProgress().values().stream().allMatch(progress -> progress == null || progress <= 0);
    }

    /**
     * Les quêtes du jour d'un joueur passent sur les modèles chargés (totaux modifiés dans config.yml puis rechargés),
     * avec leur progression et leur état. Une quête d'un métier sorti des paliers (general.level_tiers_ignored_jobs)
     * revient à x1 tout de suite, sans attendre la rotation suivante.
     */
    public void rebindDailyJobs(PlayerData playerData) {
        for (DailyJob job : List.copyOf(playerData.getActiveDailyJobs().values())) {
            JobTemplate fresh = jobTemplates.getOrDefault(job.getTemplate().getId(), job.getTemplate());
            double scale = tierIgnoredJobs.contains(fresh.getJobType()) ? 1.0 : job.getScale();
            if (fresh != job.getTemplate() || scale != job.getScale()) {
                playerData.addOrUpdateDailyJob(new DailyJob(fresh, job.getObjectiveProgress(), job.isCompleted(), scale));
            }
        }
    }

    /**
     * Termine et paie les quêtes dont chaque objectif a déjà atteint son total : un total baissé dans config.yml ne
     * laisse pas une quête bloquée au-dessus de son nouveau total (les objectifs atteints ne comptent plus d'action).
     * Thread principal, joueur connecté.
     */
    public void settleReachedGoals(Player player, PlayerData playerData) {
        for (DailyJob job : List.copyOf(playerData.getActiveDailyJobs().values())) {
            if (job.isCompleted()) {
                continue;
            }
            job.checkOverallCompletion();
            if (job.isCompleted()) {
                plugin.getMessages().sendPlayerMessage(player, "jobs.job_completed", "%job_name%", job.getTemplate().getDisplayName());
                giveJobCompletionRewards(player, job);
            }
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
            List<JobTemplate> availableForSlot = drawable();
            for (int i = 0; i < 6; i++) {
                if (i == slotIndex) continue;
                String existingId = this.currentDailyJobTemplateIds.get(i);
                if (existingId != null) {
                    availableForSlot.removeIf(jt -> jt.getId().equals(existingId));
                }
            }
            if (availableForSlot.isEmpty() && !jobTemplates.isEmpty()) {
                availableForSlot = drawable();
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
        for (int i = 0; i < currentDailyJobTemplateIds.size(); i++) {
            if (currentDailyJobTemplateIds.get(i) == null) continue;
            Optional<DailyJob> job = dailyJobForSlot(playerData, i);
            if (job.isEmpty() || !job.get().isCompleted()) {
                return;
            }
        }
        playerData.addJobPoints(bonus);
        plugin.getMessages().sendPlayerMessage(player, "rewards.all_daily_completed",
            "%amount%", String.valueOf(bonus),
            "%count%", String.valueOf(today.size()));
    }
}
