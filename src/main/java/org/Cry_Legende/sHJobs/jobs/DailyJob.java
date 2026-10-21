package org.Cry_Legende.sHJobs.jobs;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DailyJob {

    private final JobTemplate template;
    // La clé est l'ID de l'objectif (JobObjective.getId()), la valeur est la progression actuelle.
    private final Map<String, Integer> objectiveProgress;
    private boolean completed; // Si tous les objectifs sont atteints

    public DailyJob(JobTemplate template) {
        this.template = Objects.requireNonNull(template, "JobTemplate cannot be null for a DailyJob");
        this.objectiveProgress = new HashMap<>();
        this.completed = false;
        // Initialiser la progression à 0 pour tous les objectifs du template
        for (JobObjective objective : template.getObjectives()) {
            this.objectiveProgress.put(objective.getId(), 0);
        }
    }

    // Constructeur pour le chargement depuis la DB (si on stocke la progression détaillée)
    public DailyJob(JobTemplate template, Map<String, Integer> objectiveProgress, boolean completed) {
        this.template = template;
        this.objectiveProgress = new HashMap<>(objectiveProgress); // Copie
        this.completed = completed;
    }


    public JobTemplate getTemplate() {
        return template;
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
     * @param objectiveId L'ID de l'objectif à mettre à jour.
     * @param amount      L'augmentation de la progression (généralement 1).
     * @return true si l'objectif est maintenant complété, false sinon.
     */
    public boolean incrementProgress(String objectiveId, int amount) {
        if (completed) return true; // Job déjà complété

        JobObjective objective = template.getObjectiveById(objectiveId)
            .orElseThrow(() -> new IllegalArgumentException("Objective with ID " + objectiveId + " not found in template " + template.getId()));

        int currentProgress = objectiveProgress.getOrDefault(objectiveId, 0);
        int newProgress = Math.min(currentProgress + amount, objective.getAmount()); // Ne pas dépasser le total requis
        objectiveProgress.put(objectiveId, newProgress);

        if (newProgress >= objective.getAmount()) {
            // Vérifier si tous les objectifs sont maintenant complétés
            checkOverallCompletion();
            return true; // Cet objectif spécifique est complété
        }
        return false; // Cet objectif spécifique n'est pas encore complété
    }

    /**
     * Définit la progression pour un objectif spécifique.
     *
     * @param objectiveId L'ID de l'objectif.
     * @param progress    La nouvelle valeur de progression.
     */
    public void setProgress(String objectiveId, int progress) {
        if (completed && progress < getObjectiveById(objectiveId).getAmount()) {
            // Si on réduit la progression d'un job complété, il n'est plus complété
            completed = false;
        }

        JobObjective objective = template.getObjectiveById(objectiveId)
            .orElseThrow(() -> new IllegalArgumentException("Objective with ID " + objectiveId + " not found in template " + template.getId()));

        int newProgress = Math.max(0, Math.min(progress, objective.getAmount()));
        objectiveProgress.put(objectiveId, newProgress);
        checkOverallCompletion();
    }


    public boolean isObjectiveCompleted(String objectiveId) {
        JobObjective objective = template.getObjectiveById(objectiveId).orElse(null);
        if (objective == null) return false;
        return getProgress(objectiveId) >= objective.getAmount();
    }

    public boolean isCompleted() {
        return completed;
    }

    /**
     * Vérifie si tous les objectifs du job sont atteints et met à jour le statut `completed`.
     */
    public void checkOverallCompletion() {
        if (completed) return; // Déjà marqué comme complété, pas besoin de revérifier sauf si un progrès est retiré

        for (JobObjective objective : template.getObjectives()) {
            if (getProgress(objective.getId()) < objective.getAmount()) {
                this.completed = false; // Au moins un objectif n'est pas terminé
                return;
            }
        }
        this.completed = true; // Tous les objectifs sont terminés
    }

    /**
     * Réinitialise la progression de tous les objectifs de ce job.
     */
    public void resetProgress() {
        for (JobObjective objective : template.getObjectives()) {
            this.objectiveProgress.put(objective.getId(), 0);
        }
        this.completed = false;
    }

    // Méthodes utilitaires pour l'affichage dans les GUIs (lore)
    public String getFormattedProgress(String objectiveId) {
        JobObjective objective = template.getObjectiveById(objectiveId).orElse(null);
        if (objective == null) return "N/A";
        return getProgress(objectiveId) + "/" + objective.getAmount();
    }
}
