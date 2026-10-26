package org.Cry_Legende.sHJobs.config;

import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList; // Ajouté pour evaluateSimpleMathExpression
import java.util.Arrays;    // Ajouté pour evaluateSimpleMathExpression
import java.util.HashMap;
import java.util.List;      // Ajouté pour evaluateSimpleMathExpression
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;   // Ajouté pour evaluateSimpleMathExpression
import java.util.regex.Pattern;  // Ajouté pour evaluateSimpleMathExpression

public class ConfigManager {

    private final SHJobs plugin;
    private FileConfiguration config;

    // Config values
    private String storageType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private boolean mysqlUseSSL;
    private LocalTime dailyResetTime;

    private String defaultXpFormula;
    private int defaultMaxLevel;
    private Map<JobType, String> jobSpecificXpFormulas;
    private Map<JobType, Integer> jobSpecificMaxLevels;
    private Map<JobType, Map<Integer, Long>> jobSpecificFixedExp;
    // XP par niveau déjà calculée (la formule passe par des regex, et PlayerData additionne tous les niveaux jusqu'au sien)
    private final Map<Long, Long> expForLevelCache = new ConcurrentHashMap<>();

    public ConfigManager(SHJobs plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadValues();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadValues();
        plugin.getLogger().info("config.yml reloaded.");
    }

    private void loadValues() {
        expForLevelCache.clear();
        storageType = config.getString("database.type", "SQLITE").toUpperCase();
        mysqlHost = config.getString("database.mysql.host", "localhost");
        mysqlPort = config.getInt("database.mysql.port", 3306);
        mysqlDatabase = config.getString("database.mysql.database", "shjobs");
        mysqlUsername = config.getString("database.mysql.username", "user");
        mysqlPassword = config.getString("database.mysql.password", "password");
        mysqlUseSSL = config.getBoolean("database.mysql.useSSL", false);

        String resetTimeStr = config.getString("general.daily_reset_time", "00:00");
        try {
            dailyResetTime = LocalTime.parse(resetTimeStr);
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("Invalid daily_reset_time format in config.yml. Using default 00:00. Error: " + e.getMessage());
            dailyResetTime = LocalTime.MIDNIGHT;
        }

        defaultXpFormula = config.getString("experience.default_xp_formula", "1000 + %level% * 100"); // Exemple simple
        defaultMaxLevel = config.getInt("experience.default_max_level", 50);

        jobSpecificXpFormulas = new HashMap<>();
        jobSpecificMaxLevels = new HashMap<>();
        jobSpecificFixedExp = new HashMap<>();

        ConfigurationSection jobsExpSection = config.getConfigurationSection("experience.jobs");
        if (jobsExpSection != null) {
            for (String jobKey : jobsExpSection.getKeys(false)) {
                Optional<JobType> jobTypeOpt = JobType.fromString(jobKey);
                if (jobTypeOpt.isEmpty()) {
                    plugin.getLogger().warning("Invalid job type key in experience.jobs: " + jobKey + ". Skipping this entry.");
                    continue;
                }
                JobType jobType = jobTypeOpt.get();

                jobSpecificXpFormulas.put(jobType, jobsExpSection.getString(jobKey + ".xp_formula", defaultXpFormula));
                jobSpecificMaxLevels.put(jobType, jobsExpSection.getInt(jobKey + ".max_level", defaultMaxLevel));

                ConfigurationSection fixedExpSection = jobsExpSection.getConfigurationSection(jobKey + ".exp_per_level");
                if (fixedExpSection != null) {
                    Map<Integer, Long> fixedMap = new HashMap<>();
                    for (String levelStr : fixedExpSection.getKeys(false)) {
                        try {
                            int level = Integer.parseInt(levelStr);
                            long exp = fixedExpSection.getLong(levelStr);
                            fixedMap.put(level, exp);
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid level format in " + jobKey + ".exp_per_level: " + levelStr);
                        }
                    }
                    if (!fixedMap.isEmpty()) {
                        jobSpecificFixedExp.put(jobType, fixedMap);
                    }
                }
            }
        }
    }

    public long getExpForLevel(JobType jobType, int targetLevel) {
        if (targetLevel <= 1) return 0;
        return expForLevelCache.computeIfAbsent(((long) jobType.ordinal() << 32) | targetLevel, k -> computeExpForLevel(jobType, targetLevel));
    }

    private long computeExpForLevel(JobType jobType, int targetLevel) {

        if (jobSpecificFixedExp.containsKey(jobType) && jobSpecificFixedExp.get(jobType).containsKey(targetLevel)) {
            return jobSpecificFixedExp.get(jobType).get(targetLevel);
        }

        String formula = jobSpecificXpFormulas.getOrDefault(jobType, defaultXpFormula);
        // Remplacer %level% par la valeur de targetLevel.
        // Si la formule contient des expressions entre parenthèses comme (%level% - 1),
        // ce remplacement simple ne suffira pas pour l'évaluateur actuel.
        // Il faudrait une logique de pré-évaluation des parenthèses autour de %level%.
        // Pour l'instant, on assume que %level% est un terme simple dans la formule.
        String processedFormula = formula.replace("%level%", String.valueOf(targetLevel));
        // Tentative de gérer (%level% - C) ou (%level% + C) avant la multiplication
        // Ceci est une rustine et ne couvre pas tous les cas.
        Pattern levelArithmeticPattern = Pattern.compile("\\((" + Pattern.quote(String.valueOf(targetLevel)) + ")([\\+\\-])([0-9]+)\\)");
        Matcher levelMatcher = levelArithmeticPattern.matcher(processedFormula);
        StringBuffer sb = new StringBuffer();
        while (levelMatcher.find()) {
            double val = targetLevel;
            char op = levelMatcher.group(2).charAt(0);
            double constant = Double.parseDouble(levelMatcher.group(3));
            double subResult = (op == '+') ? (val + constant) : (val - constant);
            levelMatcher.appendReplacement(sb, String.valueOf(subResult));
        }
        levelMatcher.appendTail(sb);
        processedFormula = sb.toString();


        try {
            double result = evaluateSimpleMathExpression(processedFormula);
            return Math.max(0, (long) result);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Error evaluating XP formula '" + formula +
                "' (processed as '" + processedFormula + "') for " + jobType +
                " at target level " + targetLevel + ". Error: " + e.getMessage());
            // Fallback si l'évaluation échoue, en supposant que targetLevel est le niveau à atteindre
            // et que la formule donne l'XP pour l'étape vers ce niveau.
            return (long) (1000 + Math.max(0, targetLevel - 2) * 500.0);
        }
    }

    private double evaluateSimpleMathExpression(String expression) throws IllegalArgumentException {
        String cleanExpression = expression.replaceAll("\\s+", "");

        // Priorité 1: Multiplications
        Pattern multPattern = Pattern.compile("(-?[0-9]+\\.?[0-9]*)\\*(-?[0-9]+\\.?[0-9]*)");
        Matcher matcher = multPattern.matcher(cleanExpression);
        while (matcher.find()) {
            double lhs = Double.parseDouble(matcher.group(1));
            double rhs = Double.parseDouble(matcher.group(2));
            double result = lhs * rhs;
            cleanExpression = matcher.replaceFirst(String.valueOf(result));
            matcher = multPattern.matcher(cleanExpression);
        }

        // Priorité 2: Additions et Soustractions (de gauche à droite)
        List<Double> numbers = new ArrayList<>();
        List<Character> operations = new ArrayList<>();

        // Séparer nombres et opérateurs pour additions/soustractions
        // Cette regex est plus robuste pour capturer les nombres, y compris les négatifs après un opérateur
        Pattern termPattern = Pattern.compile("([+\\-])?([0-9]+\\.?[0-9]*)");
        Matcher termMatcher = termPattern.matcher(cleanExpression);
        int lastEnd = 0;

        // Gérer le premier nombre (peut être positif ou négatif sans opérateur devant)
        if (!cleanExpression.isEmpty() && (Character.isDigit(cleanExpression.charAt(0)) || cleanExpression.charAt(0) == '.' || (cleanExpression.charAt(0) == '-' && cleanExpression.length() > 1 && Character.isDigit(cleanExpression.charAt(1))))) {
            // Tenter de parser le premier segment comme un nombre
            Pattern firstNumPattern = Pattern.compile("^-?[0-9]+\\.?[0-9]*");
            Matcher firstNumMatcher = firstNumPattern.matcher(cleanExpression);
            if (firstNumMatcher.find()) {
                try {
                    numbers.add(Double.parseDouble(firstNumMatcher.group(0)));
                    lastEnd = firstNumMatcher.end();
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid starting number in expression: " + cleanExpression);
                }
            } else {
                throw new IllegalArgumentException("Expression does not start with a valid number: " + cleanExpression);
            }
        } else if (!cleanExpression.isEmpty()) {
            throw new IllegalArgumentException("Expression does not start with a valid number or is malformed: " + cleanExpression);
        } else {
            return 0; // Expression vide
        }


        // Continuer à parser le reste de l'expression
        termMatcher.region(lastEnd, cleanExpression.length());
        while (termMatcher.find()) {
            String opString = termMatcher.group(1); // Opérateur (+ ou -)
            String numString = termMatcher.group(2); // Nombre

            if (opString != null && !opString.isEmpty()) {
                operations.add(opString.charAt(0));
            } else if (!numbers.isEmpty() && lastEnd > 0) { // Si pas d'opérateur mais on s'attendait à un après le premier nombre
                throw new IllegalArgumentException("Missing operator in expression: " + cleanExpression);
            }

            try {
                numbers.add(Double.parseDouble(numString));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number '" + numString + "' in expression: " + cleanExpression);
            }
            lastEnd = termMatcher.end();
        }

        if (lastEnd != cleanExpression.length() && !cleanExpression.substring(lastEnd).trim().isEmpty()) {
            throw new IllegalArgumentException("Malformed expression, unparsed tail: '" + cleanExpression.substring(lastEnd) + "' from '" + cleanExpression + "'");
        }


        double currentResult = numbers.get(0);
        if (operations.size() != numbers.size() - 1 && !(numbers.size() == 1 && operations.isEmpty())) {
            plugin.getLogger().warning("Ops/Nums mismatch. Exp: '" + expression + "' Cleaned: '" + cleanExpression + "' Nums: " + numbers + " Ops: " + operations);
            // Si numbers a une seule valeur et ops est vide, c'est le résultat final.
            // Sinon, c'est une erreur.
            if (!(numbers.size() == 1 && operations.isEmpty())) {
                throw new IllegalArgumentException("Number of operations does not match number of terms for addition/subtraction in: " + cleanExpression);
            }
        }

        for (int i = 0; i < operations.size(); i++) {
            char op = operations.get(i);
            double nextNumber = numbers.get(i + 1);
            if (op == '+') {
                currentResult += nextNumber;
            } else if (op == '-') {
                currentResult -= nextNumber;
            }
        }
        return currentResult;
    }

    public int getMaxLevel(JobType jobType) {
        return jobSpecificMaxLevels.getOrDefault(jobType, defaultMaxLevel);
    }

    public String getStorageType() {
        return storageType;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public boolean mysqlUseSSL() {
        return mysqlUseSSL;
    }

    public LocalTime getDailyResetTime() {
        return dailyResetTime;
    }

    public FileConfiguration getRawConfig() {
        return config;
    }

    public ConfigurationSection getJobTemplatesSection() {
        return config.getConfigurationSection("job_templates");
    }

    public ConfigurationSection getShopItemsSection() {
        return config.getConfigurationSection("shop_items");
    }

    public String getJobDisplayName(String jobTypeKey) {
        return config.getString("job_types." + jobTypeKey.toLowerCase() + ".display_name", jobTypeKey);
    }

    public String getJobDisplayName(JobType jobType) {
        return config.getString("job_types." + jobType.name().toLowerCase() + ".display_name", jobType.name());
    }

    public Material getJobIcon(String jobTypeKey) {
        String matName = config.getString("job_types." + jobTypeKey.toLowerCase() + ".icon", "STONE");
        try {
            return Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for job icon " + jobTypeKey + ": " + matName + ". Defaulting to STONE.");
            return Material.STONE;
        }
    }

    public Material getJobIcon(JobType jobType) {
        String matName = config.getString("job_types." + jobType.name().toLowerCase() + ".icon", "STONE");
        try {
            return Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for job icon " + jobType.name() + ": " + matName + ". Defaulting to STONE.");
            return Material.STONE;
        }
    }
}
