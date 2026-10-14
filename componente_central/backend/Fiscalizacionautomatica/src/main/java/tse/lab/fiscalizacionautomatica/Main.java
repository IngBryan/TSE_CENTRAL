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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.bson.Document;
import tse.lab.fiscalizacionautomatica.ruleengine.data.MongoScalesMetadataDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.data.MongoTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.ActiveScalesClient;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.ScalesApiClient;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.WeighingsPoller;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.WeighingsPoller.PollResult;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingConsumer;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ActiveTripsClient;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;

public class Main {

    private static final long RETRY_DELAY_MS = 5_000;

    /** Hardcoded test value: which vehicles to query is still to be defined. */
    private static final String LICENSE_PLATE = "SBC1234";

    /**
     * The same jar runs as the tracking consumer (default: keeps running, scalable) or as the
     * weighings poller (polls once and exits, launched by a cron).
     */
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "tracking-consumer" : args[0];
        switch (mode) {
            case "tracking-consumer" -> startTrackingConsumer();
            case "weighings-poller" -> System.exit(pollWeighings());
            case "active-trips" -> System.exit(printActiveTrips());
            default -> throw new IllegalArgumentException(
                    "Unknown mode: " + mode + " (expected tracking-consumer, weighings-poller or active-trips)");
        }
    }

    private static void startTrackingConsumer() throws IOException, InterruptedException {
        requireEnv("RABBITMQ_USER", "RABBITMQ_PASSWORD", "MONGO_USER", "MONGO_PASSWORD");

        MongoClient mongo = createMongoClient();
        MongoDatabase database = mongo.getDatabase(env("MONGO_DB", "main_db"));
        waitForMongo(database);

        ConnectionFactory factory = createRabbitConnectionFactory();
        Connection connection = connectToRabbit(factory);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            closeQuietly(connection);
            mongo.close();
        }));

        MongoTrackingEventDAO dao = new MongoTrackingEventDAO(database);
        dao.createIndexes();
        TrackingEventReceiver receiver = new TrackingEventReceiver(dao);

        new TrackingConsumer(connection.createChannel(), receiver).start();
        System.out.printf("Consuming %s from %s:%d, saving to %s.%s%n", TrackingConsumer.QUEUE,
                factory.getHost(), factory.getPort(), database.getName(), MongoTrackingEventDAO.COLLECTION);
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
        try (ActiveScalesClient nodes = new ActiveScalesClient(wildFlyUrl(), System.getenv("EJB_USER"),
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

    /** Test of the remote EJB call: prints the active trips of LICENSE_PLATE. */
    private static int printActiveTrips() {
        requireEnv("EJB_USER", "EJB_PASSWORD");
        String url = wildFlyUrl();

        try (ActiveTripsClient client = new ActiveTripsClient(url, System.getenv("EJB_USER"),
                System.getenv("EJB_PASSWORD"))) {
            List<DatosViaje> trips = client.findActiveTrips(LICENSE_PLATE);
            System.out.printf("%d active trips for %s from %s%n", trips.size(), LICENSE_PLATE, url);
            trips.forEach(System.out::println);
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
