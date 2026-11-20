package org.Cry_Legende.sHJobs.listeners;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.jobs.PlacedBlocks;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tient à jour {@link PlacedBlocks} : un bloc suivi posé par un joueur est retenu ; il est oublié quand il disparaît
 * sans être cassé (explosion, feu) ou quand le jeu en fait pousser ou former un nouveau au même endroit. Un piston
 * déplace la marque avec le bloc. La casse elle-même est traitée par {@link JobProgressListener}.
 */
public class PlacedBlockListener implements Listener {

    private final SHJobs plugin;

    public PlacedBlockListener(SHJobs plugin) {
        this.plugin = plugin;
    }

    private PlacedBlocks placed() {
        return plugin.getJobManager().getPlacedBlocks();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (placed().isTracked(block.getType())) {
            placed().mark(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    /** Les marques suivent les blocs poussés ou tirés (toutes retirées d'abord : une rangée se décale d'un cran). */
    private void move(List<Block> blocks, BlockFace direction) {
        PlacedBlocks placed = placed();
        List<Block> moved = new ArrayList<>();
        for (Block block : blocks) {
            if (placed.isTracked(block.getType()) && placed.consume(block)) {
                moved.add(block);
            }
        }
        for (Block block : moved) {
            placed.mark(block.getRelative(direction));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        forget(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        forget(event.blockList());
    }

    private void forget(List<Block> blocks) {
        PlacedBlocks placed = placed();
        for (Block block : blocks) {
            if (placed.isTracked(block.getType())) {
                placed.consume(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (placed().isTracked(event.getBlock().getType())) {
            placed().consume(event.getBlock());
        }
    }

    // Nouveaux blocs venus du jeu (pierre de lave et d'eau, canne, bambou, melon, arbre, champignon géant) : une
    // marque restée à cet endroit est périmée.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (placed().isTracked(event.getNewState().getType())) {
            placed().consume(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (placed().isTracked(event.getNewState().getType())) {
            placed().consume(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        PlacedBlocks placed = placed();
        for (BlockState state : event.getBlocks()) {
            if (placed.isTracked(state.getType())) {
                placed.consume(state.getBlock());
            }
        }
    }
}
