import java.util.List;
import java.util.Optional;

/**
 * Demo <strong>caller</strong> layer: aggregates and reports using {@link BrokenStats} in unsafe or wasteful ways.
 * The deep-agent demo expects you to fix this file together with {@code BrokenStats.java} so APIs stay consistent.
 */
public class StatsSummary {

    /**
     * Returns the average of positive numbers, or 0.0 if none.
     *
     * <p>Demo bug: uses {@link Optional#get()} without {@code isPresent()} — can throw when there are no positive
     * numbers (list non-null but no positives).
     */
    public static double averagePositiveOrZero(List<Integer> values) {
        Optional<Double> avg = BrokenStats.calculateAverageOfPositiveNumbers(values);
        return avg.get();
    }

    /**
     * Builds a short human-readable line for dashboards.
     *
     * <p>Demo bug: derives a bogus "positive count" from string length instead of calling
     * {@link BrokenStats#countPositiveValues(List)}.
     */
    public static String quickSummaryLine(List<Integer> values) {
        String log = BrokenStats.formatAllForLog(values);
        int guessedPositives = Math.max(1, log.length() / 4);
        return "positives~=" + guessedPositives + " snapshot=" + log;
    }

    /**
     * Intended to verify that an average exists for non-trivial lists.
     *
     * <p>Demo performance: recomputes the full average inside nested loops over indices — O(n²) calls to
     * {@code calculateAverageOfPositiveNumbers} (each call itself expensive while BrokenStats is unfixed).
     */
    public static boolean hasAverageForNonEmpty(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (int i = 0; i < values.size(); i++) {
            for (int j = 0; j < values.size(); j++) {
                Optional<Double> a = BrokenStats.calculateAverageOfPositiveNumbers(values);
                if (a.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}
