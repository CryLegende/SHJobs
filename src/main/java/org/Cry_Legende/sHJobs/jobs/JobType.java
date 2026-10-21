package org.Cry_Legende.sHJobs.jobs;

import org.Cry_Legende.sHJobs.SHJobs; // Pour accéder au ConfigManager

import java.util.Arrays;
import java.util.Optional;

public enum JobType {
    BUCHERON,
    MINEUR,
    AGRICULTEUR,
    CHASSEUR,
    ARTISAN,
    PECHEUR;

    public String getDisplayName() {
        // Récupère le nom d'affichage depuis la config
        return SHJobs.getInstance().getConfigManager().getJobDisplayName(this);
    }

    public org.bukkit.Material getIcon() {
        // Récupère l'icône depuis la config
        return SHJobs.getInstance().getConfigManager().getJobIcon(this);
    }

    public static Optional<JobType> fromString(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(type -> type.name().equalsIgnoreCase(text))
            .findFirst();
    }

    // Vous pouvez ajouter d'autres méthodes utilitaires ici si nécessaire,
    // par exemple, pour obtenir la formule d'XP spécifique ou le niveau max
    // directement depuis le JobType, en appelant le ConfigManager.
    public long getExpForLevel(int targetLevel) {
        return SHJobs.getInstance().getConfigManager().getExpForLevel(this, targetLevel);
    }

    public int getMaxLevel() {
        return SHJobs.getInstance().getConfigManager().getMaxLevel(this);
    }
}
