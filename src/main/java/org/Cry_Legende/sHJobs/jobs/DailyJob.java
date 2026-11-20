package org.Cry_Legende.sHJobs.jobs;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quête du jour d'un joueur : progression par objectif et multiplicateur de palier.
 * <p>
 * Le multiplicateur ({@code general.level_tiers}) est fixé à l'attribution de la quête, d'après le niveau du joueur
 * dans le métier : il multiplie le total de chaque objectif et l'XP de la récompense, jamais les points. Un joueur
 * qui monte de niveau dans la journée garde le palier de sa quête jusqu'à la rotation suivante.
 */
public class DailyJob {

    private final JobTemplate template;
    // La clé est l'ID de l'objectif (JobObjective.getId()), la valeur est la progression actuelle.
    private final Map<String, Integer> objectiveProgress;
    private final double scale;
    private volatile boolean completed; // Si tous les objectifs sont atteints

    public DailyJob(JobTemplate template) {
        this(template, 1.0);
    }

    public DailyJob(JobTemplate template, double scale) {
        this.template = Objects.requireNonNull(template, "JobTemplate cannot be null for a DailyJob");
        this.objectiveProgress = new ConcurrentHashMap<>();
        this.scale = sane(scale);
        this.completed = false;
        for (JobObjective objective : template.getObjectives()) {
            this.objectiveProgress.put(objective.getId(), 0);
        }
    }

    /** Chargement depuis la base : {@code scale} absent des anciennes lignes = 1. */
    public DailyJob(JobTemplate template, Map<String, Integer> objectiveProgress, boolean completed, Double scale) {
        this.template = template;
        this.objectiveProgress = new ConcurrentHashMap<>(objectiveProgress != null ? objectiveProgress : Map.of());
        this.scale = sane(scale == null ? 1.0 : scale);
        this.completed = completed;
    }

    private static double sane(double scale) {
        return Double.isFinite(scale) && scale > 0 ? scale : 1.0;
    }

    public JobTemplate getTemplate() {
        return template;
    }

    /** Multiplicateur de palier de cette quête (1 = aucun). */
    public double getScale() {
        return scale;
    }

    /** Total à atteindre pour un objectif, palier compris. */
    public int getRequired(JobObjective objective) {
        if (scale == 1.0) {
            return objective.getAmount();
        }
        return (int) Math.ceil(objective.getAmount() * scale);
    }

    /** XP de la récompense, palier compris. */
    public long getRewardExp() {
        if (scale == 1.0) {
            return template.getRewardJobExp();
        }
        return Math.round(template.getRewardJobExp() * scale);
    }

    public Map<String, Integer> getObjectiveProgress() {
        // Retourner une copie pour éviter la modification externe non contrôlée
        return new HashMap<>(objectiveProgress);
    }

    public int getProgress(String objectiveId) {
        return objectiveProgress.getOrDefault(objectiveId, 0);
    }

    public JobObjective getObjectiveById(String objectiveId) {
        return template.getObjectiveById(objectiveId).orElse(null);
    }

    /**
     * Incrémente la progression pour un objectif spécifique.
     *
     * @return true si l'objectif est maintenant complété, false sinon.
     */
    public boolean incrementProgress(String objectiveId, int amount) {
        if (completed) return true; // Job déjà complété

        JobObjective objective = template.getObjectiveById(objectiveId)
            .orElseThrow(() -> new IllegalArgumentException("Objective with ID " + objectiveId + " not found in template " + template.getId()));

        int required = getRequired(objective);
        int currentProgress = objectiveProgress.getOrDefault(objectiveId, 0);
        int newProgress = Math.min(currentProgress + amount, required); // Ne pas dépasser le total requis
        objectiveProgress.put(objectiveId, newProgress);

        if (newProgress >= required) {
            checkOverallCompletion();
            return true;
        }
        return false;
    }

    /** Définit la progression pour un objectif spécifique. */
    public void setProgress(String objectiveId, int progress) {
        JobObjective objective = template.getObjectiveById(objectiveId)
            .orElseThrow(() -> new IllegalArgumentException("Objective with ID " + objectiveId + " not found in template " + template.getId()));
        int required = getRequired(objective);
        if (completed && progress < required) {
            completed = false;
        }
        int newProgress = Math.max(0, Math.min(progress, required));
        objectiveProgress.put(objectiveId, newProgress);
        checkOverallCompletion();
    }

    public boolean isObjectiveCompleted(String objectiveId) {
        JobObjective objective = template.getObjectiveById(objectiveId).orElse(null);
        if (objective == null) return false;
        return getProgress(objectiveId) >= getRequired(objective);
    }

    public boolean isCompleted() {
        return completed;
    }

    /** Vérifie si tous les objectifs du job sont atteints et met à jour le statut {@code completed}. */
    public void checkOverallCompletion() {
        if (completed) return;
        for (JobObjective objective : template.getObjectives()) {
            if (getProgress(objective.getId()) < getRequired(objective)) {
                this.completed = false;
                return;
            }
        }
        this.completed = true;
    }

    /** Réinitialise la progression de tous les objectifs de ce job. */
    public void resetProgress() {
        for (JobObjective objective : template.getObjectives()) {
            this.objectiveProgress.put(objective.getId(), 0);
        }
        this.completed = false;
    }

    public String getFormattedProgress(String objectiveId) {
        JobObjective objective = template.getObjectiveById(objectiveId).orElse(null);
        if (objective == null) return "N/A";
        return getProgress(objectiveId) + "/" + getRequired(objective);
    }
}
