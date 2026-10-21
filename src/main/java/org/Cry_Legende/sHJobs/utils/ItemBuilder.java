package org.Cry_Legende.sHJobs.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ItemBuilder {

    private ItemStack itemStack;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone(); // Cloner pour éviter de modifier l'original
    }

    public ItemBuilder setMaterial(Material material) {
        this.itemStack.setType(material);
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        this.itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder setName(String name) {
        if (name == null) return this;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        if (lore == null || lore.isEmpty()) return this;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setLore(lore.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList()));
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }

    public ItemBuilder addLoreLine(String line) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            List<String> currentLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            currentLore.add(ChatColor.translateAlternateColorCodes('&', line));
            meta.setLore(currentLore);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder replaceLorePlaceholder(String placeholder, String replacement) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> newLore = new ArrayList<>();
            for (String line : meta.getLore()) {
                newLore.add(line.replace(placeholder, replacement));
            }
            meta.setLore(newLore);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder replaceAllLorePlaceholders(Map<String, String> placeholders) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasLore() && placeholders != null && !placeholders.isEmpty()) {
            List<String> newLore = new ArrayList<>();
            for (String line : meta.getLore()) {
                String currentLine = line;
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    currentLine = currentLine.replace(entry.getKey(), entry.getValue());
                }
                newLore.add(currentLine);
            }
            meta.setLore(newLore);
            itemStack.setItemMeta(meta);
        }
        return this;
    }


    public ItemBuilder addEnchant(Enchantment enchantment, int level, boolean ignoreRestrictions) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchantment, level, ignoreRestrictions);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setGlowing(boolean glowing) {
        if (glowing) {
            addEnchant(Enchantment.INFINITY, 1, true); // Un enchantement commun pour faire briller
            addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null && meta.hasEnchant(Enchantment.INFINITY) && meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
                meta.removeEnchant(Enchantment.INFINITY);
                // Ne pas retirer HIDE_ENCHANTS si d'autres enchantements cachés sont présents.
                // C'est une simplification; une gestion plus fine serait nécessaire si on utilise HIDE_ENCHANTS pour autre chose.
                // Pour l'instant, on assume que ARROW_INFINITE + HIDE_ENCHANTS est uniquement pour le glow.
                if (!meta.hasEnchants()) { // S'il n'y a plus d'enchantements
                    meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                itemStack.setItemMeta(meta);
            }
        }
        return this;
    }


    public ItemBuilder addItemFlags(ItemFlag... flags) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(flags);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setUnbreakable(boolean unbreakable) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(unbreakable);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setCustomModelData(int data) {
        if (data <= 0) return this; // Souvent, 0 signifie pas de custom model data.
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(data);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setSkullOwner(String ownerName) {
        if (itemStack.getType() == Material.PLAYER_HEAD && ownerName != null) {
            SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(ownerName)); // Peut causer un lag si le joueur n'est pas en cache
                itemStack.setItemMeta(skullMeta);
            }
        }
        return this;
    }

    @SuppressWarnings("deprecation") // For setOwner
    public ItemBuilder setSkullOwner(UUID ownerUUID) {
        if (itemStack.getType() == Material.PLAYER_HEAD && ownerUUID != null) {
            SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(ownerUUID));
                itemStack.setItemMeta(skullMeta);
            }
        }
        return this;
    }

    public ItemBuilder setSkullTexture(String textureValue) {
        if (itemStack.getType() != Material.PLAYER_HEAD || textureValue == null || textureValue.isEmpty()) {
            return this;
        }
        SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
        if (skullMeta == null) return this;

        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", textureValue));
        try {
            Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace(); // Log l'erreur si la réflection échoue
        }
        itemStack.setItemMeta(skullMeta);
        return this;
    }


    public ItemStack build() {
        return itemStack;
    }
}
