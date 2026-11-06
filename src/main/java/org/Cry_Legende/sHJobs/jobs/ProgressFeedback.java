package org.Cry_Legende.sHJobs.jobs;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHJobs.SHJobs;
import org.Cry_Legende.sHJobs.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Montre l'avancée d'une quête pendant que le joueur travaille, sans qu'il ait à rouvrir /jobs :
 * barre de boss éphémère et note à chaque palier ({@code progress_feedback} dans config.yml).
 * L'action bar n'est pas utilisée : QuestCreator l'écrit à chaque progression de ses propres quêtes.
 */
public class ProgressFeedback {

    private record Milestone(int percent, SoundUtil.Spec sound) {
    }

    private static final class Shown {
        private final BossBar bar;
        private int lastUpdateTick;
        private int lastProgressTick;

        private Shown(BossBar bar) {
            this.bar = bar;
        }
    }

    private final SHJobs plugin;
    private final Map<UUID, Shown> shown = new HashMap<>();
    private BukkitTask hideTask;
    private boolean enabled;
    private int updateIntervalTicks;
    private int hideAfterTicks;
    private BossBar.Color barColor;
    private BossBar.Overlay barStyle;
    private List<Milestone> milestones = List.of();

    public ProgressFeedback(SHJobs plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        hideAll();
        ConfigurationSection section = plugin.getConfigManager().getRawConfig().getConfigurationSection("progress_feedback");
        enabled = section != null && section.getBoolean("enabled", true);
        updateIntervalTicks = section == null ? 10 : section.getInt("update_interval_ticks", 10);
        hideAfterTicks = section == null ? 60 : section.getInt("hide_after_ticks", 60);
        barColor = BossBar.Color.valueOf(setting(section, "bar_color", "GREEN"));
        barStyle = BossBar.Overlay.valueOf(setting(section, "bar_style", "NOTCHED_10"));

        List<Milestone> parsed = new ArrayList<>();
        ConfigurationSection steps = section == null ? null : section.getConfigurationSection("milestones");
        if (steps != null) {
            for (String percent : steps.getKeys(false)) {
                SoundUtil.Spec sound = SoundUtil.parse(steps.getString(percent));
                if (sound == null) {
                    plugin.getLogger().warning("Son inconnu dans progress_feedback.milestones." + percent);
                } else {
                    parsed.add(new Milestone(Integer.parseInt(percent), sound));
                }
            }
        }
        milestones = parsed;
    }

    private static String setting(ConfigurationSection section, String key, String fallback) {
        return (section == null ? fallback : section.getString(key, fallback)).toUpperCase(Locale.ROOT);
    }

    /**
     * @param before progression de l'objectif avant cette action
     */
    public void onProgress(Player player, DailyJob job, JobObjective objective, int before) {
        if (!enabled) {
            return;
        }
        int progress = job.getProgress(objective.getId());
        int total = objective.getAmount();
        for (Milestone milestone : milestones) {
            long threshold = (long) total * milestone.percent() / 100;
            if (before < threshold && progress >= threshold) {
                SoundUtil.Spec spec = milestone.sound();
                player.playSound(player.getLocation(), spec.sound(), spec.volume(), spec.pitch());
            }
        }

        int now = Bukkit.getCurrentTick();
        Shown current = shown.get(player.getUniqueId());
        if (current != null) {
            current.lastProgressTick = now;
            if (now - current.lastUpdateTick < updateIntervalTicks) {
                return;
            }
        }

        String text = plugin.getMessages().getMessage("jobs.progress_bossbar", false,
            "%job_name%", job.getTemplate().getDisplayName(),
            "%objective_name%", objective.getObjectiveDisplayName(),
            "%progress%", String.valueOf(progress),
            "%amount%", String.valueOf(total));
        Component title = LegacyComponentSerializer.legacySection().deserialize(text);
        float filled = total <= 0 ? 1f : Math.min(1f, (float) progress / total);

        if (current == null) {
            current = new Shown(BossBar.bossBar(title, filled, barColor, barStyle));
            current.lastProgressTick = now;
            shown.put(player.getUniqueId(), current);
            player.showBossBar(current.bar);
            startHideTask();
        } else {
            current.bar.name(title);
            current.bar.progress(filled);
        }
        current.lastUpdateTick = now;
    }

    /**
     * Le contrôle des barres inactives ne tourne que tant qu'une barre est affichée.
     */
    private void startHideTask() {
        if (hideTask == null) {
            hideTask = Bukkit.getScheduler().runTaskTimer(plugin, this::hideIdle, 20L, 20L);
        }
    }

    private void hideIdle() {
        int now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Shown>> entries = shown.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Shown> entry = entries.next();
            if (now - entry.getValue().lastProgressTick >= hideAfterTicks) {
                hide(entry.getKey(), entry.getValue());
                entries.remove();
            }
        }
        if (shown.isEmpty()) {
            hideTask.cancel();
            hideTask = null;
        }
    }

    private static void hide(UUID playerId, Shown entry) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.hideBossBar(entry.bar);
        }
    }

    public void forget(UUID playerId) {
        Shown entry = shown.remove(playerId);
        if (entry != null) {
            hide(playerId, entry);
        }
    }

    public final void hideAll() {
        shown.forEach(ProgressFeedback::hide);
        shown.clear();
        if (hideTask != null) {
            hideTask.cancel();
            hideTask = null;
        }
    }
}
