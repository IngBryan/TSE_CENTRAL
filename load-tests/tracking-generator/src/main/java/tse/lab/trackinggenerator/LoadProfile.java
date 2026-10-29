package tse.lab.trackinggenerator;

import java.time.Duration;

/** Multiplier of the base rate along the run: 1 is the daily operation (λ = N × p / T). */
sealed interface LoadProfile {

    double factor(double elapsedSeconds);

    /** Daily operation: λ for the whole run. */
    record Constant() implements LoadProfile {
        @Override
        public double factor(double elapsedSeconds) {
            return 1;
        }
    }

    /**
     * Each period runs at λ and ends with a peak of {@code factor} × λ lasting {@code length}: vehicles
     * that get coverage back and send the events they had accumulated.
     */
    record Spikes(double factor, Duration every, Duration length) implements LoadProfile {
        @Override
        public double factor(double elapsedSeconds) {
            double period = every.toMillis() / 1000.0;
            double inPeriod = elapsedSeconds % period;
            return inPeriod >= period - length.toMillis() / 1000.0 ? factor : 1;
        }
    }

    /** Goes up {@code step} every {@code stepDuration} until {@code max}, to find the breaking point. */
    record Ramp(double step, Duration stepDuration, double max) implements LoadProfile {
        @Override
        public double factor(double elapsedSeconds) {
            long steps = (long) (elapsedSeconds / (stepDuration.toMillis() / 1000.0));
            return Math.min(max, 1 + step * steps);
        }
    }

    /**
     * Seconds the event clocks get ahead of real time over the run: above λ each vehicle sends faster
     * than every T. The clocks start that far back, so no event is dated in the future.
     */
    default double catchUpSeconds(Duration duration) {
        double total = 0;
        for (long second = 0; second < duration.toSeconds(); second++) {
            total += factor(second) - 1;
        }
        return total;
    }

    static LoadProfile from(Options options) {
        String name = options.string("profile", "constant");
        return switch (name) {
            case "constant" -> new Constant();
            case "spikes" -> {
                Spikes spikes = new Spikes(options.decimal("spike-factor", 3.0),
                        options.duration("spike-every", "5m"), options.duration("spike-length", "1m"));
                if (spikes.factor() < 1 || spikes.length().compareTo(spikes.every()) >= 0) {
                    throw new IllegalArgumentException("--spike-factor must be >= 1 and --spike-length < --spike-every");
                }
                yield spikes;
            }
            case "ramp" -> new Ramp(options.decimal("ramp-step", 0.5),
                    options.duration("ramp-step-duration", "2m"), options.decimal("ramp-max", 10.0));
            default -> throw new IllegalArgumentException(
                    "Unknown --profile " + name + " (expected constant, spikes or ramp)");
        };
    }
}
