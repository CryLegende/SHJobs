package org.Cry_Legende.sHJobs.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.data.PlayerData;
import org.Cry_Legende.sHJobs.jobs.DailyJob;
import org.Cry_Legende.sHJobs.jobs.JobObjective;
import org.Cry_Legende.sHJobs.jobs.JobTemplate;
import org.Cry_Legende.sHJobs.jobs.JobType;
import org.Cry_Legende.sHJobs.jobs.LeaderboardEntry;
import org.Cry_Legende.sHJobs.utils.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class SHJobsExpansion extends PlaceholderExpansion {

    private final SHJobs plugin;

    public SHJobsExpansion(SHJobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shjobs";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        // Handle non-player specific placeholders first
        if (params.equalsIgnoreCase("time_until_reset")) {
            return TimeUtil.formatDuration(plugin.getConfigManager().getDailyResetTime());
        }

        if (offlinePlayer == null) {
            return null; // All other placeholders require a player
        }

        PlayerData playerData = plugin.getPlayerCache().getPlayerData(offlinePlayer.getUniqueId());
        if (playerData == null) {
            return plugin.getMessages().getMessage("papi.job_not_loaded", false);
        }

        // --- Leaderboard Placeholders ---
        // Format: %shjobs_leaderboard_<job_type>_<rank>_<data>%
        if (params.startsWith("leaderboard_")) {
            String[] parts = params.split("_");
            if (parts.length == 4) { // e.g., [leaderboard, mineur, 1, name]
                Optional<JobType> jobTypeOpt = JobType.fromString(parts[1]);
                try {
                    int rank = Integer.parseInt(parts[2]);
                    String dataType = parts[3];

                    if (jobTypeOpt.isPresent() && rank > 0 && rank <= 10) {
                        List<LeaderboardEntry> leaderboard = plugin.getLeaderboardManager().getLeaderboard(jobTypeOpt.get());
                        if (leaderboard.size() >= rank) {
                            LeaderboardEntry entry = leaderboard.get(rank - 1);
                            switch (dataType.toLowerCase()) {
                                case "name":
                                    return entry.getPlayerName();
                                case "level":
                                    return String.valueOf(entry.getLevel());
                                case "exp":
                                    return String.valueOf(entry.getTotalExperience());
                            }
                        } else {
                            return "---"; // No player at this rank
                        }
                    }
                } catch (NumberFormatException e) {
                    return "Invalid Rank";
                }
            }
        }

        // --- Player Rank Placeholder ---
        // Format: %shjobs_player_rank_<job_type>%
        if (params.startsWith("player_rank_")) {
            String jobTypeStr = params.substring("player_rank_".length());
            Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
            if (jobTypeOpt.isPresent()) {
                int rank = plugin.getLeaderboardManager().getPlayerRank(offlinePlayer.getUniqueId(), jobTypeOpt.get());

                if (rank == -1) {
                    return "&7Calcul..."; // Intermediate state
                }
                if (rank == 0) {
                    return "&7Non classé"; // Player has no data for this job
                }
                return String.valueOf(rank); // Display the rank
            }
        }

        // --- Player Stats Placeholders ---

        // %shjobs_player_points%
        if (params.equalsIgnoreCase("player_points")) {
            return String.valueOf(playerData.getJobPoints());
        }

        // %shjobs_player_level_<job_type>%
        if (params.startsWith("player_level_")) {
            String jobTypeStr = params.substring("player_level_".length());
            Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
            if (jobTypeOpt.isPresent()) {
                return String.valueOf(playerData.getLevel(jobTypeOpt.get()));
            }
        }

        // %shjobs_player_exp_<job_type>%
        if (params.startsWith("player_exp_") && !params.contains("_progress_") && !params.contains("_bar_") && !params.contains("_percentage_")) {
            String jobTypeStr = params.substring("player_exp_".length());
            Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
            if (jobTypeOpt.isPresent()) {
                return String.valueOf(playerData.getExperience(jobTypeOpt.get()));
            }
        }

        // %shjobs_player_exp_required_<job_type>%
        if (params.startsWith("player_exp_required_")) {
            String jobTypeStr = params.substring("player_exp_required_".length());
            Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
            if (jobTypeOpt.isPresent()) {
                JobType jt = jobTypeOpt.get();
                if (playerData.getLevel(jt) >= jt.getMaxLevel()) {
                    return plugin.getMessages().getMessage("jobs.exp_max_level_short", false);
                }
                return String.valueOf(playerData.getExpToNextLevel(jt));
            }
        }

        // %shjobs_player_exp_progress_<job_type>%
        if (params.startsWith("player_exp_progress_")) {
            String jobTypeStr = params.substring("player_exp_progress_".length());
            Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
            if (jobTypeOpt.isPresent()) {
                JobType jt = jobTypeOpt.get();
                if (playerData.getLevel(jt) >= jt.getMaxLevel()) {
                    return plugin.getMessages().getMessage("jobs.exp_max_level_short", false);
                }
                long currentExpInLevel = playerData.getCurrentLevelProgressExp(jt);
                long expForNextStep = jt.getExpForLevel(playerData.getLevel(jt) + 1);
                return currentExpInLevel + "/" + expForNextStep;
            }
        }

        // %shjobs_player_exp_bar_<job_type>%
        if (params.startsWith("player_exp_bar_")) {
            String jobTypeStr = params.substring("player_exp_bar_".length());
            Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
            if (jobTypeOpt.isPresent()) {
                JobType jt = jobTypeOpt.get();
                if (playerData.getLevel(jt) >= jt.getMaxLevel()) {
                    return plugin.getMessages().generateProgressBar(1, 1, 20); // Full bar
                }
                long currentExpInLevel = playerData.getCurrentLevelProgressExp(jt);
                long expForNextStep = jt.getExpForLevel(playerData.getLevel(jt) + 1);
                return plugin.getMessages().generateProgressBar(currentExpInLevel, expForNextStep, 20);
            }
        }

        // %shjobs_player_exp_percentage_<job_type>%
        if (params.startsWith("player_exp_percentage_")) {
            String jobTypeStr = params.substring("player_exp_percentage_".length());
            Optional<JobType> jobTypeOpt = JobType.fromString(jobTypeStr);
            if (jobTypeOpt.isPresent()) {
                JobType jt = jobTypeOpt.get();
                if (playerData.getLevel(jt) >= jt.getMaxLevel()) {
                    return "100";
                }
                long currentExpInLevel = playerData.getCurrentLevelProgressExp(jt);
                long expForNextStep = jt.getExpForLevel(playerData.getLevel(jt) + 1);
                double percentage = (expForNextStep > 0) ? ((double) currentExpInLevel / expForNextStep) * 100 : 0;
                return String.format("%.1f", percentage);
            }
        }

        // --- Daily Job Placeholders ---
        // Format: %shjobs_player_job_daily_<slot>_...%
        if (params.startsWith("player_job_daily_")) {
            String[] parts = params.split("_");
            if (parts.length >= 5) { // e.g., player_job_daily_1_name
                try {
                    int slot = Integer.parseInt(parts[3]);
                    if (slot < 1 || slot > 6) return "Invalid Slot";

                    List<String> dailyJobIds = plugin.getJobManager().getCurrentDailyJobTemplateIds();
                    if (slot - 1 >= dailyJobIds.size() || dailyJobIds.get(slot - 1) == null) {
                        return plugin.getMessages().getMessage("papi.job_not_available", false);
                    }

                    String templateId = dailyJobIds.get(slot - 1);
                    Optional<JobTemplate> templateOpt = plugin.getJobManager().getJobTemplate(templateId);
                    Optional<DailyJob> dailyJobOpt = playerData.getDailyJobByTemplateId(templateId);

                    if (templateOpt.isPresent() && dailyJobOpt.isPresent()) {
                        JobTemplate template = templateOpt.get();
                        DailyJob dailyJob = dailyJobOpt.get();
                        String dataType = parts[4];

                        if (dataType.equalsIgnoreCase("name")) {
                            return template.getDisplayName();
                        }
                        if (dataType.equalsIgnoreCase("progress")) {
                            JobObjective firstObjective = template.getObjectives().get(0);
                            return dailyJob.isCompleted() ? "Terminé" :
                                dailyJob.getProgress(firstObjective.getId()) + "/" + firstObjective.getAmount();
                        }
                    } else {
                        return plugin.getMessages().getMessage("papi.job_not_loaded", false);
                    }
                } catch (NumberFormatException e) {
                    return "Invalid Slot Num";
                }
            }
        }

        return null; // Unrecognized placeholder
    }
}
