package org.Cry_Legende.sHJobs.jobs;

import java.util.Arrays;
import java.util.Optional;

public enum JobAction {
    BREAK,    // Casser un bloc
    PLACE,    // Placer un bloc
    WALK,     // Marcher une certaine distance (plus complexe, peut-être simplifié ou pour plus tard)
    CRAFT,    // Fabriquer un item
    FISH,     // Pêcher un type d'item spécifique
    KILL,     // Tuer une entité spécifique
    SMELT,    // Faire fondre un item dans un four
    BREW,     // Préparer une potion spécifique
    STRIP;    // "Stripper" un tronc d'arbre avec une hache

    public static Optional<JobAction> fromString(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(action -> action.name().equalsIgnoreCase(text))
            .findFirst();
    }
}
