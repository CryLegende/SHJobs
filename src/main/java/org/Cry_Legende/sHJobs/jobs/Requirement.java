package org.Cry_Legende.sHJobs.jobs;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Condition d'une quête du jour (bloc {@code requirement} de config.yml) : un joueur qui ne la remplit pas reçoit la
 * quête {@code fallback} à la place quand la quête tombe dans la rotation. La condition compare deux nombres, chacun
 * pouvant être un placeholder PlaceholderAPI, par exemple {@code %superior_island_level_int% >= 1500}.
 */
public final class Requirement {

    private static final Pattern CONDITION = Pattern.compile("^\\s*(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");
    private static final Pattern THOUSANDS = Pattern.compile("^-?\\d{1,3}(,\\d{3})+$");

    private final String condition;
    private final String left;
    private final String operator;
    private final String right;
    private final String fallbackId;

    private Requirement(String condition, String left, String operator, String right, String fallbackId) {
        this.condition = condition;
        this.left = left;
        this.operator = operator;
        this.right = right;
        this.fallbackId = fallbackId;
    }

    /** Vide si la condition n'a pas la forme « valeur opérateur valeur » ou si la quête de remplacement manque. */
    public static Optional<Requirement> parse(String condition, String fallbackId) {
        if (condition == null || fallbackId == null || fallbackId.isBlank()) {
            return Optional.empty();
        }
        Matcher m = CONDITION.matcher(condition);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Requirement(condition.trim(), m.group(1), m.group(2), m.group(3), fallbackId.trim()));
    }

    public String getCondition() {
        return condition;
    }

    public String getFallbackId() {
        return fallbackId;
    }

    /**
     * Thread principal. Une valeur qui ne se lit pas comme un nombre (pas d'île, placeholder inconnu) compte comme
     * condition non remplie : le joueur reçoit la quête de remplacement, qu'il peut toujours faire.
     */
    public boolean isMetBy(OfflinePlayer player) {
        double a = number(PlaceholderAPI.setPlaceholders(player, left));
        double b = number(PlaceholderAPI.setPlaceholders(player, right));
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        return switch (operator) {
            case ">=" -> a >= b;
            case "<=" -> a <= b;
            case ">" -> a > b;
            case "<" -> a < b;
            case "==" -> a == b;
            case "!=" -> a != b;
            default -> false;
        };
    }

    /** « 1 500 », « 1,500 », « 1500.0 » ou « 12,5 » ; NaN si ce n'est pas un nombre. */
    static double number(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = raw.replaceAll("[\\s\\u00A0\\u202F]", "");
        if (s.isEmpty()) {
            return Double.NaN;
        }
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(",", "");
        } else if (THOUSANDS.matcher(s).matches()) {
            s = s.replace(",", "");
        } else {
            s = s.replace(',', '.');
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
