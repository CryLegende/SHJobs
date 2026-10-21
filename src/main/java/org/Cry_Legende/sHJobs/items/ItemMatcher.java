package org.Cry_Legende.sHJobs.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

/**
 * Compare un objet d'inventaire à un objet de référence de custom_items.yml.
 * <p>
 * isSimilar exige des composants strictement identiques, or une référence enregistrée sous une
 * ancienne version de Minecraft ne l'est plus après une mise à jour : événements de clic perdus à
 * la relecture, tooltip_display recalculé, custom_data ajouté par le plugin d'origine. On compare
 * donc ce qui identifie l'objet : type, données persistantes de la référence, nom et lore tels
 * qu'affichés (texte, couleurs, styles), enchantements.
 */
public final class ItemMatcher {

    private static final List<PersistentDataType<?, ?>> PDC_TYPES = List.of(
        PersistentDataType.STRING, PersistentDataType.INTEGER, PersistentDataType.LONG,
        PersistentDataType.DOUBLE, PersistentDataType.FLOAT, PersistentDataType.SHORT,
        PersistentDataType.BYTE, PersistentDataType.BYTE_ARRAY, PersistentDataType.INTEGER_ARRAY,
        PersistentDataType.LONG_ARRAY);

    private ItemMatcher() {
    }

    public static boolean matches(ItemStack reference, ItemStack candidate) {
        if (reference == null || candidate == null || candidate.getType() != reference.getType()) {
            return false;
        }
        if (candidate.isSimilar(reference)) {
            return true;
        }
        // Référence sans métadonnées : seul un objet nu lui correspond, et isSimilar l'aurait accepté
        if (!reference.hasItemMeta() || !candidate.hasItemMeta()) {
            return false;
        }
        ItemMeta ref = reference.getItemMeta();
        ItemMeta cand = candidate.getItemMeta();
        return samePersistentData(ref.getPersistentDataContainer(), cand.getPersistentDataContainer())
            && visual(ref.displayName()).equals(visual(cand.displayName()))
            && sameLore(ref.lore(), cand.lore())
            && ref.getEnchants().equals(cand.getEnchants());
    }

    /**
     * Chaque donnée persistante de la référence doit exister à l'identique sur l'objet ; celles que
     * l'objet a en plus sont ignorées.
     */
    private static boolean samePersistentData(PersistentDataContainer ref, PersistentDataContainer cand) {
        for (NamespacedKey key : ref.getKeys()) {
            if (!cand.has(key)) {
                return false;
            }
            for (PersistentDataType<?, ?> type : PDC_TYPES) {
                if (ref.has(key, type)) {
                    if (!cand.has(key, type) || !Objects.deepEquals(ref.get(key, type), cand.get(key, type))) {
                        return false;
                    }
                    break;
                }
            }
            // Type composé (conteneur imbriqué) : la présence de la clé suffit
        }
        return true;
    }

    private static boolean sameLore(List<Component> ref, List<Component> cand) {
        List<Component> a = ref == null ? List.of() : ref;
        List<Component> b = cand == null ? List.of() : cand;
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!visual(a.get(i)).equals(visual(b.get(i)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rendu du texte tel que le joueur le voit : chaque segment porte sa couleur et ses styles
     * actifs, hérités du parent. Les événements de clic/survol et la découpe en segments ne
     * changent pas le résultat.
     */
    private static String visual(Component component) {
        if (component == null) {
            return "";
        }
        Visual out = new Visual();
        out.append(component, Style.empty());
        return out.text.toString();
    }

    private static final class Visual {
        private final StringBuilder text = new StringBuilder();
        private String lastStyle = null;

        private void append(Component component, Style inherited) {
            Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
            String content = component instanceof TextComponent textComponent
                ? textComponent.content()
                : PlainTextComponentSerializer.plainText().serialize(component.children(List.of()));
            if (!content.isEmpty()) {
                String key = styleKey(style);
                if (!key.equals(lastStyle)) {
                    text.append(key);
                    lastStyle = key;
                }
                text.append(content);
            }
            for (Component child : component.children()) {
                append(child, style);
            }
        }

        private static String styleKey(Style style) {
            TextColor color = style.color();
            StringBuilder key = new StringBuilder("{").append(color == null ? "" : color.asHexString());
            for (TextDecoration decoration : TextDecoration.values()) {
                if (style.decoration(decoration) == TextDecoration.State.TRUE) {
                    key.append(decoration.name().charAt(0));
                }
            }
            return key.append('}').toString();
        }
    }
}
