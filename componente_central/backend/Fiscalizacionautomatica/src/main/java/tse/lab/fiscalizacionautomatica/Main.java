package tse.lab.fiscalizacionautomatica;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.bson.Document;
import tse.lab.fiscalizacionautomatica.ruleengine.data.MongoTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingConsumer;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver;

public class Main {

    private static final long RETRY_DELAY_MS = 5_000;

    public static void main(String[] args) throws Exception {
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

        TrackingEventReceiver receiver = new TrackingEventReceiver(new MongoTrackingEventDAO(database));

        new TrackingConsumer(connection.createChannel(), receiver).start();
        System.out.printf("Consuming %s from %s:%d, saving to %s.%s%n", TrackingConsumer.QUEUE,
                factory.getHost(), factory.getPort(), database.getName(), MongoTrackingEventDAO.COLLECTION);
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
