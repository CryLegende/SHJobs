package org.Cry_Legende.sHJobs.jobs;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Blocs posés par un joueur, pour qu'une casse d'objectif BREAK ne compte que les blocs venus du jeu (arbre poussé,
 * générateur, culture, pierre de lave et d'eau) : poser une bûche et la recasser ne fait plus avancer une quête.
 * <p>
 * Seuls les matériaux des objectifs BREAK sans âge requis sont suivis (les cultures avec âge ne se posent pas
 * mûres). Les positions sont rangées dans le conteneur de données du chunk, triées (tableau d'entiers : y depuis le
 * bas du monde, x et z dans le chunk), donc elles survivent aux redémarrages. Chaque chunk n'est lu et écrit que par
 * le fil de son monde : pas de cache partagé, compatible avec les ticks de mondes en parallèle.
 */
public final class PlacedBlocks {

    /** Au-delà, un chunk ne retient plus de nouveaux blocs posés (construction géante, tableau trop lourd). */
    private static final int MAX_PER_CHUNK = 32768;

    private final NamespacedKey key;
    private volatile Set<Material> tracked = Set.of();
    private volatile boolean enabled = true;

    public PlacedBlocks(SHJobs plugin) {
        this.key = new NamespacedKey(plugin, "placed");
    }

    /** Recalculé à chaque chargement des quêtes ({@code general.placed_blocks_count: false} active le suivi). */
    void refresh(Collection<JobTemplate> templates, boolean enabled) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (JobTemplate template : templates) {
            for (JobObjective objective : template.getObjectives()) {
                if (objective.getAction() == JobAction.BREAK && objective.getRequiredAge() == -1) {
                    set.addAll(objective.getMaterials());
                }
            }
        }
        this.tracked = set;
        this.enabled = enabled;
    }

    public boolean isTracked(Material material) {
        return enabled && tracked.contains(material);
    }

    public int trackedCount() {
        return tracked.size();
    }

    private static int pack(Block block) {
        int y = block.getY() - block.getWorld().getMinHeight();
        return (y << 8) | ((block.getX() & 15) << 4) | (block.getZ() & 15);
    }

    /** Retient le bloc comme posé par un joueur. */
    public void mark(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        int packed = pack(block);
        int[] marks = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        if (marks == null || marks.length == 0) {
            pdc.set(key, PersistentDataType.INTEGER_ARRAY, new int[]{packed});
            return;
        }
        int index = Arrays.binarySearch(marks, packed);
        if (index >= 0 || marks.length >= MAX_PER_CHUNK) {
            return;
        }
        int insert = -index - 1;
        int[] grown = new int[marks.length + 1];
        System.arraycopy(marks, 0, grown, 0, insert);
        grown[insert] = packed;
        System.arraycopy(marks, insert, grown, insert + 1, marks.length - insert);
        pdc.set(key, PersistentDataType.INTEGER_ARRAY, grown);
    }

    /**
     * Oublie le bloc.
     *
     * @return vrai s'il avait été posé par un joueur
     */
    public boolean consume(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        if (!pdc.has(key)) {
            return false;
        }
        int[] marks = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        if (marks == null) {
            return false;
        }
        int index = Arrays.binarySearch(marks, pack(block));
        if (index < 0) {
            return false;
        }
        if (marks.length == 1) {
            pdc.remove(key);
            return true;
        }
        int[] shrunk = new int[marks.length - 1];
        System.arraycopy(marks, 0, shrunk, 0, index);
        System.arraycopy(marks, index + 1, shrunk, index, marks.length - index - 1);
        pdc.set(key, PersistentDataType.INTEGER_ARRAY, shrunk);
        return true;
    }
}
