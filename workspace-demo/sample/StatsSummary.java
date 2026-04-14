import java.util.List;
import java.util.Optional;

/**
 * Demo <strong>caller</strong> layer: aggregates and reports using {@link BrokenStats} in unsafe or wasteful ways.
 * The deep-agent demo expects you to fix this file together with {@code BrokenStats.java} so APIs stay consistent.
 */
public class StatsSummary {

    /**
     * Returns the average of positive numbers, or 0.0 if none.
     */
    public static double averagePositiveOrZero(List<Integer> values) {
        Optional<Double> avg = BrokenStats.calculateAverageOfPositiveNumbers(values);
        return avg.orElseGet(() -> 0.0);
    }

    /**
     * Builds a short human-readable line for dashboards.
     *
     * <p>Demo bug: string literal does not support {@code append}; should use {@link StringBuilder} or {@code +}.
     */
    public static String quickSummaryLine(List<Integer> values) {
        String log = BrokenStats.formatAllForLog(values);
        int guessedPositives = BrokenStats.countPositiveValues(values);
        return new StringBuilder().append("positives=").append(guessedPositives).append(" snapshot=").append(log).toString();
    }

    /**
     * Intended to verify that an average exists for non-trivial lists.
     *
     * <p>Demo performance: wastefully recomputes the average in a loop (O(n²) over list size).
     */
    public static boolean hasAverageForNonEmpty(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        Optional<Double> avg = BrokenStats.calculateAverageOfPositiveNumbers(values);
        return avg.isPresent();
        return false;
    }
}
