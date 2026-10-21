package org.Cry_Legende.sHJobs.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

public class TimeUtil {

    /**
     * Formate une durée en une chaîne lisible (ex: "1h 30m 15s").
     *
     * @param duration La durée à formater.
     * @return Une chaîne formatée.
     */
    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 0) seconds = 0;

        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.MINUTES.toSeconds(minutes);

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) { // Afficher les minutes même si 0 si des heures sont présentes
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Calcule la durée jusqu'à la prochaine occurrence de LocalTime.
     *
     * @param targetTime L'heure cible.
     * @return La durée.
     */
    public static Duration getTimeUntilNext(LocalTime targetTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.with(targetTime);

        if (now.isAfter(nextRun) || now.equals(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun);
    }

    /**
     * Surcharge pour obtenir la durée jusqu'au reset quotidien configuré.
     *
     * @param dailyResetTime L'heure du reset.
     * @return Chaîne formatée du temps restant.
     */
    public static String formatDuration(LocalTime dailyResetTime) {
        return formatDuration(getTimeUntilNext(dailyResetTime));
    }
}
