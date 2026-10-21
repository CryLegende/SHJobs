package org.Cry_Legende.sHJobs.data;

import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.jobs.DailyJob;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet; // Import pour Set
import java.util.Map;
import java.util.Optional;
import java.util.Set; // Import pour Set
import java.util.UUID;

public class PlayerData {

    private final UUID playerUUID;
    private final Map<JobType, Long> experience;
    private final Map<JobType, Integer> levels;
    private long jobPoints;
    private Map<String, DailyJob> activeDailyJobs;
    private Set<String> dailyPurchasedShopItemIds; // IDs des items de boutique achetés aujourd'hui

    public PlayerData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.experience = new EnumMap<>(JobType.class);
        this.levels = new EnumMap<>(JobType.class);
        this.jobPoints = 0;
        this.activeDailyJobs = new HashMap<>();
        this.dailyPurchasedShopItemIds = new HashSet<>();

        for (JobType type : JobType.values()) {
            this.experience.put(type, 0L);
            this.levels.put(type, 1);
        }
    }

    // Constructeur pour le chargement depuis la base de données
    // dailyPurchasedShopItemIds sera peuplé par StorageManager si stocké, sinon initialisé vide ici
    public PlayerData(UUID playerUUID, Map<JobType, Long> experience, Map<JobType, Integer> levels, long jobPoints, Map<String, DailyJob> activeDailyJobs, @org.jetbrains.annotations.Nullable Set<String> dailyPurchasedShopItemIds) {
        this.playerUUID = playerUUID;
        this.experience = new EnumMap<>(experience);
        this.levels = new EnumMap<>(levels);
        this.jobPoints = jobPoints;
        this.activeDailyJobs = activeDailyJobs != null ? new HashMap<>(activeDailyJobs) : new HashMap<>();
        this.dailyPurchasedShopItemIds = dailyPurchasedShopItemIds != null ? new HashSet<>(dailyPurchasedShopItemIds) : new HashSet<>();


        for (JobType type : JobType.values()) {
            this.experience.putIfAbsent(type, 0L);
            this.levels.putIfAbsent(type, 1);
        }
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public long getExperience(JobType jobType) {
        return experience.getOrDefault(jobType, 0L);
    }

    public void setExperience(JobType jobType, long exp) {
        this.experience.put(jobType, Math.max(0, exp));
        recalculateLevel(jobType);
    }

    public void setRawExperience(JobType jobType, long exp) {
        this.experience.put(jobType, Math.max(0, exp));
    }

    public void addExperience(JobType jobType, long expToAdd) {
        if (expToAdd <= 0) return;

        long currentExp = getExperience(jobType);
        long newExp = currentExp + expToAdd;
        int currentLevel = getLevel(jobType);
        int maxLevel = jobType.getMaxLevel();

        if (currentLevel >= maxLevel && currentExp >= getTotalExpRequiredForLevel(jobType, maxLevel)) {
            this.experience.put(jobType, newExp);
            return;
        }

        this.experience.put(jobType, newExp);
        checkLevelUp(jobType);
    }

    public void removeExperience(JobType jobType, long expToRemove) {
        if (expToRemove <= 0) return;
        long currentExp = getExperience(jobType);
        long newExp = Math.max(0, currentExp - expToRemove);
        this.experience.put(jobType, newExp);
        recalculateLevel(jobType);
    }

    public int getLevel(JobType jobType) {
        return levels.getOrDefault(jobType, 1);
    }

    public void setLevel(JobType jobType, int level) {
        int maxLevel = jobType.getMaxLevel();
        int newLevel = Math.max(1, Math.min(level, maxLevel));
        this.levels.put(jobType, newLevel);
        long xpForNewLevel = getTotalExpRequiredForLevel(jobType, newLevel);
        this.experience.put(jobType, xpForNewLevel);
    }

    public void setRawLevel(JobType jobType, int level) {
        int maxLevel = jobType.getMaxLevel();
        this.levels.put(jobType, Math.max(1, Math.min(level, maxLevel)));
    }

    public long getTotalExpRequiredForLevel(JobType jobType, int targetLevel) {
        if (targetLevel <= 1) return 0;
        long totalXp = 0;
        for (int i = 2; i <= targetLevel; i++) {
            totalXp += jobType.getExpForLevel(i);
        }
        return totalXp;
    }

    public long getExpToNextLevel(JobType jobType) {
        int currentLevel = getLevel(jobType);
        int maxLevel = jobType.getMaxLevel();
        if (currentLevel >= maxLevel) {
            return Long.MAX_VALUE;
        }
        return jobType.getExpForLevel(currentLevel + 1);
    }

    public long getCurrentLevelProgressExp(JobType jobType) {
        int currentLevel = getLevel(jobType);
        if (currentLevel >= jobType.getMaxLevel()) {
            return Math.max(0, getExperience(jobType) - getTotalExpRequiredForLevel(jobType, jobType.getMaxLevel()));
        }
        long totalCurrentExp = getExperience(jobType);
        long expRequiredForCurrentLevelStart = getTotalExpRequiredForLevel(jobType, currentLevel);
        return totalCurrentExp - expRequiredForCurrentLevelStart;
    }

    public void checkLevelUp(JobType jobType) {
        int currentLevel = getLevel(jobType);
        long currentTotalExp = getExperience(jobType);
        int maxLevel = jobType.getMaxLevel();

        if (currentLevel >= maxLevel) return;

        long expNeededForNextLevelStart = getTotalExpRequiredForLevel(jobType, currentLevel + 1);

        while (currentTotalExp >= expNeededForNextLevelStart && currentLevel < maxLevel) {
            currentLevel++;
            this.levels.put(jobType, currentLevel);
            // TODO: Event pour level up
            if (currentLevel >= maxLevel) {
                break;
            }
            expNeededForNextLevelStart = getTotalExpRequiredForLevel(jobType, currentLevel + 1);
        }
    }

    public void recalculateLevel(JobType jobType) {
        long currentTotalExp = getExperience(jobType);
        int newCalculatedLevel = 1;
        long cumulativeXpForLevel = 0;

        for (int lvl = 2; lvl <= jobType.getMaxLevel(); lvl++) {
            long xpForThisStep = jobType.getExpForLevel(lvl);
            if (xpForThisStep == Long.MAX_VALUE) break;
            cumulativeXpForLevel += xpForThisStep;
            if (currentTotalExp >= cumulativeXpForLevel) {
                newCalculatedLevel = lvl;
            } else {
                break;
            }
        }
        this.levels.put(jobType, newCalculatedLevel);
    }

    public long getJobPoints() {
        return jobPoints;
    }

    public void setJobPoints(long points) {
        this.jobPoints = Math.max(0, points);
    }

    public void addJobPoints(long pointsToAdd) {
        this.jobPoints += pointsToAdd;
        if (this.jobPoints < 0) this.jobPoints = 0;
    }

    public boolean removeJobPoints(long pointsToRemove) {
        if (pointsToRemove < 0) return true;
        if (this.jobPoints >= pointsToRemove) {
            this.jobPoints -= pointsToRemove;
            return true;
        }
        return false;
    }

    public Map<String, DailyJob> getActiveDailyJobs() {
        return activeDailyJobs;
    }

    public void setActiveDailyJobs(Map<String, DailyJob> activeDailyJobs) {
        this.activeDailyJobs = activeDailyJobs != null ? new HashMap<>(activeDailyJobs) : new HashMap<>();
    }

    public Optional<DailyJob> getDailyJobByTemplateId(String templateId) {
        return Optional.ofNullable(this.activeDailyJobs.get(templateId));
    }

    public void addOrUpdateDailyJob(DailyJob job) {
        if (job != null && job.getTemplate() != null) {
            this.activeDailyJobs.put(job.getTemplate().getId(), job);
        }
    }

    public void removeDailyJob(String templateId) {
        this.activeDailyJobs.remove(templateId);
    }

    public void clearDailyJobs() {
        this.activeDailyJobs.clear();
    }

    public Set<String> getDailyPurchasedShopItemIds() {
        return dailyPurchasedShopItemIds;
    }

    public boolean hasPurchasedToday(String shopItemId) {
        return dailyPurchasedShopItemIds.contains(shopItemId);
    }

    public void addDailyPurchase(String shopItemId) {
        dailyPurchasedShopItemIds.add(shopItemId);
    }

    public void clearDailyPurchases() {
        dailyPurchasedShopItemIds.clear();
    }
}