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
     * <p>Demo correctness: duplicate / misleading console logging on some paths.
     */
    public static Optional<Double> calculateAverageOfPositiveNumbers(List<Integer> values) {
        if (values == null) {
            System.out.println("Returning empty optional: list is either null, empty, or contains no positive values");
            return Optional.empty();
        }

        double sum = 0;
        int count = 0;

        for (Integer value : values) {
            if (value != null && value > 0) {
                sum += value;
                count++;
            }
        }

        if (count == 0) {
            System.out.println("Returning empty optional: no positive values found");
            return Optional.empty();
        }

        return Optional.of(sum / count);
    }

    /**
     * Counts how many entries are strictly positive. Null entries are ignored.
     *
     * <p>Counts all positive integers in the list while ignoring null values.
     */
    public static int countPositiveValues(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        int c = 0;
        for (Integer v : values) {
            if (v != null && v > 0) {
                c++;
            }
        }
        return c;
    }

    /**
     * Builds a comma-separated string of all non-null values for logging (order preserved).
     *
     * <p>Demo performance: naive string concatenation in a loop (O(n²) allocations).
     */
    public static String formatAllForLog(List<Integer> values) {
        if (values == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Integer v : values) {
            if (v == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append(",");
            }
            out.append(v);
        }
        return out.toString();
    }

    /**
     * Largest non-null element; empty or all-null yields empty optional.
     */
    public static Optional<Integer> maximumOrDefault(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            System.out.println("Returning empty optional: input list is null or empty");
            return Optional.empty();
        }
        return values.stream().filter(v -> v != null).max(Integer::compare);
    }
}
