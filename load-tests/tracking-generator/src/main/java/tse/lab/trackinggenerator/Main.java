package tse.lab.trackinggenerator;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Load generator of tracking events. Publishes positions to RabbitMQ at the estimated rate
 * λ = round(N × p) / T events per second, shaped by a load profile. For practicality the events come
 * from the 500 vehicles of the load test seed, each reporting proportionally more often,
 * so the central receives the same events per second as from the whole estimated fleet.
 */
public class Main {

    private static final Duration REPORT_EVERY = Duration.ofSeconds(5);
    private static final Map<String, String> ENV_FILE = EnvFile.load();

    private static final String USAGE = """
            Usage: java -jar tracking-generator.jar --active-ratio <p> --interval <T>
                     [--vehicles 48370] [--duration 10m] [--profile constant|spikes|ramp] [--publishers 2]
                     [--duplicate-ratio 0.02] [--out-of-order-ratio 0.05]
                     spikes: [--spike-factor 3] [--spike-every 5m] [--spike-length 1m]
                     ramp:   [--ramp-step 0.5] [--ramp-step-duration 2m] [--ramp-max 10]
            RabbitMQ from RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_VHOST, RABBITMQ_USER, RABBITMQ_PASSWORD, RABBITMQ_TLS
            (environment variables, else load-tests/tracking-generator/.env).
            """;

    public static void main(String[] args) throws Exception {
        try {
            run(Options.parse(args, 0));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.print(USAGE);
            System.exit(2);
        }
    }

    /** λ = round(N × p) / T: events per second of the estimated fleet. */
    static double estimatedRate(int vehicles, double activeRatio, Duration interval) {
        if (activeRatio <= 0 || activeRatio > 1) {
            throw new IllegalArgumentException("--active-ratio must be in (0, 1], got " + activeRatio);
        }
        if (interval.toMillis() < 1) {
            throw new IllegalArgumentException("--interval must be at least 1 ms");
        }
        return Math.round(vehicles * activeRatio) / (interval.toMillis() / 1000.0);
    }

    private static void run(Options options) throws Exception {
        int vehicles = options.integer("vehicles", 48_370);
        double activeRatio = options.decimal("active-ratio", null);
        Duration interval = Options.parseDuration(options.string("interval", null));
        double rate = estimatedRate(vehicles, activeRatio, interval);
        Publisher.Settings settings = new Publisher.Settings(rate, options.duration("duration", "10m"),
                LoadProfile.from(options), options.decimal("duplicate-ratio", 0.02),
                options.decimal("out-of-order-ratio", 0.05));
        int publishers = options.integer("publishers", 2);
        options.rejectUnknown();
        // Each publisher sends the events of its own vehicles
        if (publishers < 1 || publishers > Fleet.SIZE) {
            throw new IllegalArgumentException("--publishers must be between 1 and " + Fleet.SIZE);
        }
        // Events of a vehicle are told apart by their millisecond timestamp
        if (settings.spacingMicros() < 1000) {
            throw new IllegalArgumentException("Each simulated vehicle would report more than once per ms:"
                    + " lower the rate");
        }

        Instant clocksStart = Instant.now().minusMillis(
                Math.round(settings.profile().catchUpSeconds(settings.duration()) * 1000));
        System.out.printf("Estimated fleet: %d active of %d reporting every %s = %.1f events/s%n",
                Math.round(vehicles * activeRatio), vehicles, interval, rate);
        System.out.printf("Simulated by %d vehicles reporting every %d ms, profile %s, for %s%n",
                Fleet.SIZE, settings.spacingMicros() / 1000, settings.profile(), settings.duration());

        // One connection per publisher: the client writes and the broker applies flow control per connection,
        // so a shared one caps the rate however many publishers there are
        ConnectionFactory factory = connectionFactory();
        List<Connection> connections = new ArrayList<>();
        try {
            for (int i = 0; i < publishers; i++) {
                connections.add(factory.newConnection("tracking-generator-" + i));
            }
        } catch (IOException | TimeoutException e) {
            System.err.printf("Could not connect to RabbitMQ at %s:%d: %s%n", factory.getHost(), factory.getPort(),
                    e.getMessage());
            System.exit(1);
            return;
        }

        Stats stats = new Stats();
        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        // Ctrl+C stops the publishers and waits for the summary
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop.set(true);
            try {
                finished.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        try {
            long startNanos = System.nanoTime();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < publishers; i++) {
                Thread thread = new Thread(new Publisher(connections.get(i), settings, i, publishers, stats, stop,
                        startNanos, clocksStart), "publisher-" + i);
                thread.start();
                threads.add(thread);
            }
            report(threads, settings.profile(), rate, stats, startNanos);
            for (Thread thread : threads) {
                thread.join();
            }
        } finally {
            for (Connection connection : connections) {
                try {
                    connection.close();
                } catch (IOException | RuntimeException e) {
                    // An already closed connection must not skip the summary
                    System.err.println("Could not close a RabbitMQ connection: " + e.getMessage());
                }
            }
            printSummary(stats);
            finished.countDown();
        }
    }

    private static void report(List<Thread> threads, LoadProfile profile, double baseRate, Stats stats,
            long startNanos) throws InterruptedException {
        long lastUnique = 0;
        double lastElapsed = 0;
        while (threads.stream().anyMatch(Thread::isAlive)) {
            long deadline = System.currentTimeMillis() + REPORT_EVERY.toMillis();
            for (Thread thread : threads) {
                thread.join(Math.max(1, deadline - System.currentTimeMillis()));
            }
            double elapsed = (System.nanoTime() - startNanos) / 1e9;
            long unique = stats.uniquePublished.get();
            // Divided by the real time since the last line: under load the lines come later than every 5 s
            System.out.printf("%6.0fs  target %8.1f/s  sent %8.1f/s  unique %d  duplicates %d  confirmed %d"
                    + "  pending %d  rejected %d%n",
                    elapsed, baseRate * profile.factor(elapsed), (unique - lastUnique) / (elapsed - lastElapsed),
                    unique, stats.duplicatesPublished.get(), stats.uniqueConfirmed.get(), stats.outstanding.get(),
                    stats.rejected.get() + stats.unroutable.get());
            lastUnique = unique;
            lastElapsed = elapsed;
        }
    }

    private static void printSummary(Stats stats) {
        long unique = stats.uniqueConfirmed.get();
        long expected = Math.max(0, unique - stats.unroutable.get());
        System.out.printf("""
                Summary
                  unique events published:   %d (%d sent out of order)
                  duplicates published:      %d
                  confirmed by RabbitMQ:     %d unique, %d duplicates
                  rejected / unroutable:     %d / %d
                The central must have saved %d events of plates LT*: compare with
                  db.tracking.countDocuments({licensePlate: /^LT/})
                """, stats.uniquePublished.get(), stats.outOfOrder.get(), stats.duplicatesPublished.get(), unique,
                stats.duplicatesConfirmed.get(), stats.rejected.get(), stats.unroutable.get(), expected);
    }

    private static ConnectionFactory connectionFactory() throws GeneralSecurityException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(env("RABBITMQ_HOST", "localhost"));
        factory.setPort(Integer.parseInt(env("RABBITMQ_PORT", "5672")));
        factory.setVirtualHost(env("RABBITMQ_VHOST", "/"));
        factory.setUsername(env("RABBITMQ_USER", "guest"));
        factory.setPassword(env("RABBITMQ_PASSWORD", "guest"));
        if (Boolean.parseBoolean(env("RABBITMQ_TLS", "false"))) {
            factory.useSslProtocol();
        }
        return factory;
    }

    /** Environment variable, else the generator's .env, else the default. */
    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = ENV_FILE.get(name);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
