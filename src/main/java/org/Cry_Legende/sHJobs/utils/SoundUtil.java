package org.Cry_Legende.sHJobs.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;

/**
 * Lecture des sons écrits en config (config.yml, menus.yml).
 */
public final class SoundUtil {

    /**
     * Son avec son volume et sa hauteur.
     */
    public record Spec(Sound sound, float volume, float pitch) {
    }

    private SoundUtil() {
    }

    /**
     * Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup).
     */
    public static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUND_EVENT.get(key);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    /**
     * Lit "SON[:volume[:hauteur]]" où SON est un ancien nom ou une clé (la clé contient elle-même un ':').
     *
     * @return null si le son est inconnu
     * @throws NumberFormatException si le volume ou la hauteur ne sont pas des nombres
     */
    public static Spec parse(String raw) {
        if (raw == null) return null;
        String[] parts = raw.trim().split(":");
        int next = 1;
        String name = parts[0];
        if (parts.length > 1 && !isNumber(parts[1])) {
            // clé avec espace de noms : minecraft:entity.player.levelup[:volume[:hauteur]]
            name = parts[0] + ":" + parts[1];
            next = 2;
        }
        Sound sound = sound(name);
        if (sound == null) return null;
        float volume = parts.length > next ? Float.parseFloat(parts[next]) : 1.0f;
        float pitch = parts.length > next + 1 ? Float.parseFloat(parts[next + 1]) : 1.0f;
        return new Spec(sound, volume, pitch);
    }

    private static boolean isNumber(String text) {
        try {
            Float.parseFloat(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
