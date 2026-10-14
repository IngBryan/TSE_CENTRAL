package tse.lab.fiscalizacionautomatica;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.rabbitmq.client.AuthenticationFailureException;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.bson.Document;
import tse.lab.fiscalizacionautomatica.ruleengine.data.MongoScalesMetadataDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.data.MongoTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.SituationDetector;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.TrackingDetectionWorker;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFactsSource;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.nodes.ActiveGpsRegistry;
import tse.lab.fiscalizacionautomatica.ruleengine.nodes.ActiveNodesClient;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.ScalesApiClient;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.WeighingsPoller;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.WeighingsPoller.PollResult;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingConsumer;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver;
import tse.lab.fiscalizacionautomatica.ruleengine.vehicles.VehicleFactsClient;

public class Main {

    private static final long RETRY_DELAY_MS = 5_000;
    private static final Duration ACTIVE_GPS_REFRESH = Duration.ofSeconds(60);
    /** Detection threads of the tracking consumer: the events of a vehicle always go to the same one. */
    private static final int DETECTION_LANES = 4;
    private static final int DETECTION_QUEUE_PER_LANE = 1_000;
    /** Events still pending detection after PENDING_RECOVERY_AFTER are queued again every period. */
    private static final Duration PENDING_RECOVERY_PERIOD = Duration.ofSeconds(60);
    private static final Duration PENDING_RECOVERY_AFTER = Duration.ofMinutes(1);

    /** Hardcoded test value: which vehicles to query is still to be defined. */
    private static final String LICENSE_PLATE = "SBC1234";

    /**
     * The same jar runs as the tracking consumer (default: keeps running, scalable), as the weighings
     * poller (polls once and exits, launched by a cron) or as a manual test of the detection.
     */
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "tracking-consumer" : args[0];
        switch (mode) {
            case "tracking-consumer" -> startTrackingConsumer();
            case "weighings-poller" -> System.exit(pollWeighings());
            case "detect-situations" -> System.exit(detectSituations(args.length > 1 ? args[1] : LICENSE_PLATE));
            default -> throw new IllegalArgumentException("Unknown mode: " + mode
                    + " (expected tracking-consumer, weighings-poller or detect-situations [license plate])");
        }
    }

    private static void startTrackingConsumer() throws IOException, InterruptedException {
        requireEnv("RABBITMQ_USER", "RABBITMQ_PASSWORD", "MONGO_USER", "MONGO_PASSWORD", "EJB_USER", "EJB_PASSWORD");

        MongoClient mongo = createMongoClient();
        MongoDatabase database = mongo.getDatabase(env("MONGO_DB", "main_db"));
        waitForMongo(database);

        ActiveNodesClient nodes = new ActiveNodesClient(wildFlyUrl(), System.getenv("EJB_USER"),
                System.getenv("EJB_PASSWORD"));
        ActiveGpsRegistry activeGps = new ActiveGpsRegistry(nodes::findActiveGpsIds);
        waitForActiveGps(activeGps);
        activeGps.refreshEvery(ACTIVE_GPS_REFRESH);

        MongoTrackingEventDAO dao = new MongoTrackingEventDAO(database, Clock.systemUTC());
        dao.createIndexes();
        VehicleFactsClient facts = new VehicleFactsClient(wildFlyUrl(), System.getenv("EJB_USER"),
                System.getenv("EJB_PASSWORD"));
        TrackingDetectionWorker detection = new TrackingDetectionWorker(createDetector(facts), dao, Clock.systemUTC(),
                DETECTION_LANES, DETECTION_QUEUE_PER_LANE);
        detection.recoverPendingEvery(PENDING_RECOVERY_PERIOD, PENDING_RECOVERY_AFTER);

        ConnectionFactory factory = createRabbitConnectionFactory();
        Connection connection = connectToRabbit(factory);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // First stop receiving, then let the queued detections finish
            closeQuietly(connection);
            detection.close();
            facts.close();
            activeGps.close();
            nodes.close();
            mongo.close();
        }));

        TrackingEventReceiver receiver = new TrackingEventReceiver(dao, activeGps::isActive, detection::submit);

        new TrackingConsumer(connection.createChannel(), receiver).start();
        System.out.printf("Consuming %s from %s:%d, saving to %s.%s (%d active GPS)%n", TrackingConsumer.QUEUE,
                factory.getHost(), factory.getPort(), database.getName(), MongoTrackingEventDAO.COLLECTION,
                activeGps.size());
    }

    /** Retries until WildFly answers (it takes a while to deploy): without the list no event can be checked. */
    private static void waitForActiveGps(ActiveGpsRegistry activeGps) throws InterruptedException {
        while (true) {
            try {
                activeGps.load();
                return;
            } catch (IOException e) {
                System.err.printf("Could not get the active GPS from WildFly (%s), retrying in %d s%n",
                        e.getCause(), RETRY_DELAY_MS / 1000);
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
    }

    /**
     * Polls the active scales (asked to the backoffice by remote EJB).
     *
     * @return exit code: 0 if every scale was polled, 1 otherwise (so the cron sees the failure)
     */
    private static int pollWeighings() {
        requireEnv("BALANZAS_CLIENT_ID", "BALANZAS_CLIENT_SECRET", "MONGO_USER", "MONGO_PASSWORD", "EJB_USER",
                "EJB_PASSWORD");
        String url = env("BALANZAS_URL", "http://localhost:3001");

        List<Long> scaleIds;
        try (ActiveNodesClient nodes = new ActiveNodesClient(wildFlyUrl(), System.getenv("EJB_USER"),
                System.getenv("EJB_PASSWORD"))) {
            scaleIds = nodes.findActiveScaleIds();
        } catch (IOException e) {
            System.err.println("Could not get the active scales: " + e.getMessage() + ": " + e.getCause());
            return 1;
        }
        if (scaleIds.isEmpty()) {
            System.out.println("No active scales: nothing to poll");
            return 0;
        }

        try (MongoClient mongo = createMongoClient()) {
            MongoDatabase database = mongo.getDatabase(env("MONGO_DB", "main_db"));
            ScalesApiClient api = new ScalesApiClient(url, System.getenv("BALANZAS_CLIENT_ID"),
                    System.getenv("BALANZAS_CLIENT_SECRET"), Clock.systemUTC());
            PollResult result = new WeighingsPoller(scaleIds, LICENSE_PLATE, api,
                    new MongoScalesMetadataDAO(database), Clock.systemUTC()).poll();

            System.out.printf("Fetched %d weighings of scales %s from %s (until %s)%n", result.weighings().size(),
                    scaleIds, url, result.to());
            if (!result.failedScaleIds().isEmpty()) {
                System.err.println("Could not poll scales " + result.failedScaleIds()
                        + ": their last timestamp was not updated");
                return 1;
            }
            return 0;
        } catch (MongoException e) {
            // Without the last timestamp the range is unknown: the next run retries
            System.err.println("MongoDB unavailable: " + e.getMessage());
            return 1;
        }
    }

    /** The situations that are detected; adding a situation means adding its rule here. */
    private static SituationDetector createDetector(VehicleFactsSource facts) {
        return new SituationDetector(facts, List.of(new TripWithoutGuideRule()));
    }

    /**
     * Manual test of the detection against WildFly: evaluates an event of the vehicle at this moment
     * (default LICENSE_PLATE) and prints what the central knows about it and the situations that apply.
     */
    private static int detectSituations(String licensePlate) {
        requireEnv("EJB_USER", "EJB_PASSWORD");

        try (VehicleFactsClient facts = new VehicleFactsClient(wildFlyUrl(), System.getenv("EJB_USER"),
                System.getenv("EJB_PASSWORD"))) {
            System.out.println("Vehicle facts: " + facts.find(licensePlate).map(Object::toString).orElse("not registered"));
            TrackingEvent event = new TrackingEvent("manual", 0, 0, licensePlate, 0, 0, Instant.now());
            System.out.println("Situations: " + createDetector(facts).detect(event));
            return 0;
        } catch (IOException e) {
            System.err.println(e.getMessage() + ": " + e.getCause());
            return 1;
        }
    }

    private static String wildFlyUrl() {
        return env("WILDFLY_URL", "remote+http://localhost:8080");
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static void requireEnv(String... names) {
        List<String> missing = Stream.of(names)
                .filter(name -> env(name, null) == null)
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing environment variables: " + String.join(", ", missing));
        }
    }

    private static MongoClient createMongoClient() {
        ServerAddress server = new ServerAddress(env("MONGO_HOST", "localhost"),
                Integer.parseInt(env("MONGO_PORT", "27017")));
        MongoCredential credential = MongoCredential.createCredential(System.getenv("MONGO_USER"),
                env("MONGO_AUTH_DB", "admin"), System.getenv("MONGO_PASSWORD").toCharArray());
        return MongoClients.create(MongoClientSettings.builder()
                .applyToClusterSettings(cluster -> cluster
                        .hosts(List.of(server))
                        .serverSelectionTimeout(5, TimeUnit.SECONDS))
                .credential(credential)
                .build());
    }

    /** Retries until MongoDB is available (the script may start first). */
    private static void waitForMongo(MongoDatabase database) throws InterruptedException {
        while (true) {
            try {
                database.runCommand(new Document("ping", 1));
                return;
            } catch (MongoSecurityException e) {
                throw e; // wrong credentials: retrying won't help
            } catch (MongoTimeoutException e) {
                System.err.printf("MongoDB unavailable, retrying in %d s%n", RETRY_DELAY_MS / 1000);
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
    }

    private static ConnectionFactory createRabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(env("RABBITMQ_HOST", "localhost"));
        factory.setPort(Integer.parseInt(env("RABBITMQ_PORT", "5672")));
        factory.setUsername(System.getenv("RABBITMQ_USER"));
        factory.setPassword(System.getenv("RABBITMQ_PASSWORD"));
        // If the connection drops, the client reconnects on its own and registers the consumer again
        factory.setAutomaticRecoveryEnabled(true);
        return factory;
    }

    /** Retries until RabbitMQ is available (the script may start first). */
    private static Connection connectToRabbit(ConnectionFactory factory) throws InterruptedException, IOException {
        while (true) {
            try {
                return factory.newConnection("fiscalizacion-automatica");
            } catch (AuthenticationFailureException e) {
                throw e;
            } catch (IOException | TimeoutException e) {
                System.err.printf("RabbitMQ unavailable at %s:%d, retrying in %d s%n",
                        factory.getHost(), factory.getPort(), RETRY_DELAY_MS / 1000);
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            if (connection.isOpen()) {
                connection.close();
            }
        } catch (IOException e) {
            // shutting down: nothing else to do
        }
    }
}
