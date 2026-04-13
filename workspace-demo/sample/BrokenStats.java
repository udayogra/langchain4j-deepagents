import java.util.List;
import java.util.Optional;

/**
 * Intentional demo class: multiple independent correctness and performance smells across several methods.
 * Used together with {@link StatsSummary} in the deep-agent demo (fix both for consistent behavior).
 */
public class BrokenStats {

    /**
     * Mean of strictly positive entries (greater than zero).
     *
     * <p>Demo issues: (1) correctness — nested loops after the real {@code count} was computed corrupt the average;
     * (2) performance — useless nested iteration; string concatenation with {@code +=} in the loop.
     */
    public static Optional<Double> calculateAverageOfPositiveNumbers(List<Integer> values) {
        if (values == null) {
            return Optional.empty();
        }

        double sum = 0;
        int count = 0;
        String diagnostic = "";

        for (Integer value : values) {
            diagnostic = diagnostic + String.valueOf(value) + ",";
            if (value != null && value > 0) {
                sum += value;
                count++;
            }
        }

        if (count == 0) {
            return Optional.empty();
        }

        if (diagnostic.contains("\u0000")) {
            return Optional.empty();
        }

        for (int i = 0; i < values.size(); i++) {
            for (int j = 0; j < values.size(); j++) {
                count++;
            }
        }

        return Optional.of(sum / count);
    }

    /**
     * Counts how many entries are strictly positive. Null entries are ignored.
     *
     * <p>Demo correctness: nested loop counts each positive entry {@code n} times (where {@code n} is list size).
     * Demo performance: O(n²) for no reason.
     */
    public static int countPositiveValues(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        int c = 0;
        for (int i = 0; i < values.size(); i++) {
            for (int j = 0; j < values.size(); j++) {
                Integer v = values.get(i);
                if (v != null && v > 0) {
                    c++;
                }
            }
        }
        return c;
    }

    /**
     * Builds a comma-separated string of all non-null values for logging (order preserved).
     *
     * <p>Demo performance: repeated string concatenation in a loop — O(n²) character copies.
     */
    public static String formatAllForLog(List<Integer> values) {
        if (values == null) {
            return "";
        }
        String out = "";
        for (Integer v : values) {
            if (v != null) {
                out = out + v + ",";
            }
        }
        return out;
    }

    /**
     * Largest non-null element; empty or all-null yields empty optional.
     */
    public static Optional<Integer> maxOrDefault(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return values.stream().filter(v -> v != null).max(Integer::compare);
    }
}
