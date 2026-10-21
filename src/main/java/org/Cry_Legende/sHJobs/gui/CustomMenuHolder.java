package org.Cry_Legende.sHJobs.gui;

import org.Cry_Legende.sHJobs.SHJobs;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class CustomMenuHolder implements InventoryHolder {

    protected final SHJobs plugin;
    protected final String title;
    protected final int size;
    protected Inventory inventory; // Référence à l'inventaire créé

    public CustomMenuHolder(SHJobs plugin, String title, int size) {
        this.plugin = plugin;
        this.title = title;
        this.size = size;
        // L'inventaire sera créé par le MenuManager et set via setInventory()
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        // Important: Bukkit.createInventory DOIT être appelé avec this (le holder)
        // pour que les events InventoryClickEvent puissent le récupérer via event.getInventory().getHolder().
        // Ici, on retourne l'inventaire qui a été set par MenuManager.
        if (this.inventory == null) {
            // Cela ne devrait pas arriver si MenuManager est bien utilisé
            throw new IllegalStateException("Inventory not set for CustomMenuHolder. MenuManager should set it.");
        }
        return this.inventory;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public abstract void handleClick(Player player, int slot, ItemStack clickedItem, ClickType clickType);
}