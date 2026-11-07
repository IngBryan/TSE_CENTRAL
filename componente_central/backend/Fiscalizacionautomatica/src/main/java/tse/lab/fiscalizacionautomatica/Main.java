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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bson.Document;
import tse.lab.fiscalizacionautomatica.ruleengine.cases.CaseAssigner;
import tse.lab.fiscalizacionautomatica.ruleengine.cases.RemoteCaseStore;
import tse.lab.fiscalizacionautomatica.ruleengine.data.MongoScalesMetadataDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.data.MongoTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.data.RemoteWeighingDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.PendingWeighingsDetection;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.SituationDetector;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.SituationRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.TrackingDetectionWorker;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFactsSource;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.OverloadRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule;
import tse.lab.fiscalizacionautomatica.ruleengine.routes.RemoteRutasAutorizadasSource;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ContextoEventoSource;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.RemoteIntervalosGuiasGpsSource;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.nodes.ActiveGpsRegistry;
import tse.lab.fiscalizacionautomatica.ruleengine.nodes.ActiveNodesClient;
import tse.lab.fiscalizacionautomatica.ruleengine.nodes.NodeCommunications;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.BalanzasConfig;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.ScalesApiClient;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.WeighingsPoller;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.WeighingsPoller.PollResult;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingConsumer;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver;
import tse.lab.fiscalizacionautomatica.ruleengine.vehicles.VehicleFactsClient;

public class Main {

    private static final long RETRY_DELAY_MS = 5_000;
    private static final Duration ACTIVE_GPS_REFRESH = Duration.ofSeconds(60);
    /** How often the last communication of each GPS is sent to the backoffice. */
    private static final Duration GPS_COMMUNICATIONS_FLUSH = Duration.ofSeconds(60);
    /**
     * Consumers of the tracking queue per instance, each on its own channel: RabbitMQ delivers the messages of a
     * channel one at a time, so a single one serializes the whole instance.
     */
    private static final int CONSUMER_CHANNELS = 4;
    /** Detection threads of the tracking consumer: the events of a vehicle always go to the same one. */
    private static final int DETECTION_LANES = 4;
    private static final int DETECTION_QUEUE_PER_LANE = 1_000;
    /** Events still pending detection after PENDING_RECOVERY_AFTER are queued again every period. */
    private static final Duration PENDING_RECOVERY_PERIOD = Duration.ofSeconds(60);
    private static final Duration PENDING_RECOVERY_AFTER = Duration.ofMinutes(1);
    /** How long the shutdown hook waits for the consumer to close (the queued detections get 10 s). */
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(20);

    /** Default vehicle of the detect-situations manual test. */
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

    /**
     * Consumes until the process is stopped (SIGTERM from Kubernetes or docker stop): the shutdown hook
     * releases the main thread, which closes everything in reverse order (first RabbitMQ, so nothing new
     * arrives, then the queued detections, then the clients) before the JVM exits.
     */
    private static void startTrackingConsumer() throws IOException, InterruptedException {
        requireEnv("RABBITMQ_USER", "RABBITMQ_PASSWORD", "MONGO_USER", "MONGO_PASSWORD", "EJB_USER", "EJB_PASSWORD");
        String user = System.getenv("EJB_USER");
        String password = System.getenv("EJB_PASSWORD");

        boolean desvioHabilitado = Boolean.parseBoolean(env("ROUTE_DEVIATION_ENABLED", "false"));
        CountDownLatch stopRequested = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopRequested.countDown();
            awaitQuietly(stopped, SHUTDOWN_WAIT);
        }));

        try (MongoClient mongo = createMongoClient();
                ActiveNodesClient nodes = new ActiveNodesClient(wildFlyUrl(), user, password);
                ActiveGpsRegistry activeGps = new ActiveGpsRegistry(nodes::findActiveGpsIds);
                NodeCommunications gpsCommunications = new NodeCommunications(nodes::reportGpsCommunications,
                        Clock.systemUTC());
                VehicleFactsClient facts = new VehicleFactsClient(wildFlyUrl(), user, password);
                RemoteCaseStore cases = new RemoteCaseStore(wildFlyUrl(), user, password);
                RemoteIntervalosGuiasGpsSource intervalos =
                        new RemoteIntervalosGuiasGpsSource(wildFlyUrl(), user, password);
                RemoteRutasAutorizadasSource rutas = desvioHabilitado
                        ? new RemoteRutasAutorizadasSource(wildFlyUrl(), user, password) : null) {
            MongoDatabase database = mongo.getDatabase(env("MONGO_DB", "main_db"));
            if (!waitForMongo(database, stopRequested) || !waitForActiveGps(activeGps, stopRequested)) {
                return; // stopped while waiting
            }
            activeGps.refreshEvery(ACTIVE_GPS_REFRESH);
            gpsCommunications.flushEvery(GPS_COMMUNICATIONS_FLUSH);

            MongoTrackingEventDAO dao = new MongoTrackingEventDAO(database, Clock.systemUTC());
            dao.createIndexes();
            ConnectionFactory factory = createRabbitConnectionFactory();
            List<SituationRule> reglas = new java.util.ArrayList<>(List.of(
                    new TripWithoutGuideRule(), new OverloadRule()));
            // La regla puede evaluarse en pruebas; en producción se habilita una vez
            // integrado el agrupamiento y las evidencias de 4B/4C.
            if (desvioHabilitado) {
                reglas.add(new RouteDeviationRule(intervalos, rutas, dao));
            }
            try (TrackingDetectionWorker detection = new TrackingDetectionWorker(new SituationDetector(facts, reglas, new ContextoEventoSource(intervalos)), dao,
                    new CaseAssigner(cases), Clock.systemUTC(), DETECTION_LANES, DETECTION_QUEUE_PER_LANE);
                    Connection connection = connectToRabbit(factory, stopRequested)) {
                if (connection == null) {
                    return; // stopped while waiting
                }
                detection.recoverPendingEvery(PENDING_RECOVERY_PERIOD, PENDING_RECOVERY_AFTER);

                // A saved event is a successful communication with its GPS
                TrackingEventReceiver receiver = new TrackingEventReceiver(dao, activeGps::isActive, event -> {
                    gpsCommunications.record(event.gpsId());
                    detection.submit(event);
                });
                for (int i = 0; i < CONSUMER_CHANNELS; i++) {
                    new TrackingConsumer(connection.createChannel(), receiver).start();
                }
                LogFiscalizacion.info(LogFiscalizacion.evento("tracking.consumidor_iniciado").campo("cola", TrackingConsumer.QUEUE)
                        .campo("rabbitmq", factory.getHost() + ":" + factory.getPort())
                        .campo("canales", CONSUMER_CHANNELS).campo("coleccion", database.getName() + "."
                                + MongoTrackingEventDAO.COLLECTION)
                        .campo("gpsActivos", activeGps.size()));

                stopRequested.await();
                LogFiscalizacion.info(LogFiscalizacion.evento("tracking.consumidor_detenido"));
            }
        } finally {
            stopped.countDown();
        }
    }

    private static void awaitQuietly(CountDownLatch latch, Duration timeout) {
        try {
            latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Retries until WildFly answers (it takes a while to deploy): without the list no event can be checked.
     *
     * @return false if the process was stopped while waiting
     */
    private static boolean waitForActiveGps(ActiveGpsRegistry activeGps, CountDownLatch stop)
            throws InterruptedException {
        while (true) {
            try {
                activeGps.load();
                return true;
            } catch (IOException e) {
                LogFiscalizacion.error(LogFiscalizacion.evento("gps_activos.no_disponibles").campo("reintentoSegundos", RETRY_DELAY_MS / 1000)
                        .errorSinPila(e));
                if (stop.await(RETRY_DELAY_MS, TimeUnit.MILLISECONDS)) {
                    return false;
                }
            }
        }
    }

    /**
     * Polls the active scales (asked to the backoffice by remote EJB), saves their weighings pending
     * detection and then evaluates every pending weighing.
     *
     * @return exit code: 0 if every scale was polled and every pending weighing evaluated, 1 otherwise
     *         (so the cron sees the failure)
     */
    private static int pollWeighings() {
        requireEnv("BALANZAS_CLIENT_SECRET", "MONGO_USER", "MONGO_PASSWORD", "EJB_USER", "EJB_PASSWORD");
        BalanzasConfig balanzas;
        try {
            balanzas = BalanzasConfig.load();
        } catch (IOException e) {
            LogFiscalizacion.error(LogFiscalizacion.evento("balanzas.configuracion_invalida").error(e));
            return 1;
        }
        try (ActiveNodesClient nodes = new ActiveNodesClient(wildFlyUrl(), System.getenv("EJB_USER"),
                System.getenv("EJB_PASSWORD"))) {
            return pollWeighings(nodes, balanzas);
        } catch (IOException e) {
            LogFiscalizacion.error(LogFiscalizacion.evento("balanzas.nodos_no_disponibles").error(e));
            return 1;
        }
    }

    private static int pollWeighings(ActiveNodesClient nodes, BalanzasConfig balanzas) {
        String url = balanzas.url();
        List<Long> scaleIds;
        try {
            scaleIds = nodes.findActiveScaleIds();
        } catch (IOException e) {
            LogFiscalizacion.error(LogFiscalizacion.evento("balanzas.activas_no_disponibles").error(e));
            return 1;
        }
        if (scaleIds.isEmpty()) {
            LogFiscalizacion.info(LogFiscalizacion.evento("balanzas.sin_activas"));
            return 0;
        }

        String user = System.getenv("EJB_USER");
        String password = System.getenv("EJB_PASSWORD");
        try (MongoClient mongo = createMongoClient();
        RemoteWeighingDAO weighingDAO = new RemoteWeighingDAO(wildFlyUrl(), user, password);
        VehicleFactsClient facts = new VehicleFactsClient(wildFlyUrl(), user, password);
        RemoteCaseStore cases = new RemoteCaseStore(wildFlyUrl(), user, password);
        RemoteIntervalosGuiasGpsSource intervalos =
                new RemoteIntervalosGuiasGpsSource(wildFlyUrl(), user, password)) {
            MongoDatabase database = mongo.getDatabase(env("MONGO_DB", "main_db"));
            ScalesApiClient api = new ScalesApiClient(url, balanzas.clientId(),
                    System.getenv("BALANZAS_CLIENT_SECRET"), Clock.systemUTC());
            PollResult result = new WeighingsPoller(scaleIds, api, weighingDAO,
                    new MongoScalesMetadataDAO(database), Clock.systemUTC()).poll();

            LogFiscalizacion.info(LogFiscalizacion.evento("balanzas.consultadas").campo("pesajes", result.weighings().size())
                    .campo("nuevos", result.saved()).campo("balanzas", scaleIds).campo("url", url)
                    .campo("hasta", result.to()));
            if (!result.failedScaleIds().isEmpty()) {
                LogFiscalizacion.error(LogFiscalizacion.evento("balanzas.fallidas").campo("balanzas", result.failedScaleIds()));
            }
            reportScaleCommunications(nodes, result);

            // Also the ones left pending by previous runs
            int notEvaluated = new PendingWeighingsDetection(createDetector(facts, new ContextoEventoSource(intervalos)), weighingDAO,
                    new CaseAssigner(cases)).detectPending();
            if (notEvaluated > 0) {
                LogFiscalizacion.error(LogFiscalizacion.evento("pesajes.no_evaluados").campo("cantidad", notEvaluated));
            }
            return result.failedScaleIds().isEmpty() && notEvaluated == 0 ? 0 : 1;
        } catch (MongoException e) {
            // Without the last timestamp the range is unknown: the next run retries
            LogFiscalizacion.error(LogFiscalizacion.evento("mongodb.no_disponible").error(e));
            return 1;
        } catch (IOException e) {
            LogFiscalizacion.error(LogFiscalizacion.evento("pesajes.evaluacion_fallida").error(e));
            return 1;
        }
    }

    /**
     * ultimaComunicacionExitosa of the scales that answered: the moment of the poll. A failure loses no
     * weighing, so it is only logged and the next run records it again.
     */
    private static void reportScaleCommunications(ActiveNodesClient nodes, PollResult result) {
        if (result.polledScaleIds().isEmpty()) {
            return;
        }
        Map<Long, Instant> communications = result.polledScaleIds().stream()
                .collect(Collectors.toMap(scaleId -> scaleId, scaleId -> result.to()));
        try {
            nodes.reportScaleCommunications(communications);
        } catch (IOException e) {
            LogFiscalizacion.error(LogFiscalizacion.evento("balanzas.comunicacion_no_registrada")
                    .campo("balanzas", result.polledScaleIds()).error(e));
        }
    }

    /** The situations that are detected; adding a situation means adding its rule here. */
    private static SituationDetector createDetector(VehicleFactsSource facts) {
        return new SituationDetector(facts, List.of(new TripWithoutGuideRule(), new OverloadRule()));
    }

    private static SituationDetector createDetector(VehicleFactsSource facts, ContextoEventoSource contexto) {
        return new SituationDetector(facts, List.of(new TripWithoutGuideRule(), new OverloadRule()), contexto);
    }

    /**
     * Manual test of the detection against WildFly: evaluates an event of the vehicle at this moment
     * (default LICENSE_PLATE) and prints what the central knows about it and the situations that apply.
     */
    private static int detectSituations(String licensePlate) {
        requireEnv("EJB_USER", "EJB_PASSWORD");

        try (VehicleFactsClient facts = new VehicleFactsClient(wildFlyUrl(), System.getenv("EJB_USER"),
                System.getenv("EJB_PASSWORD"))) {
            LogFiscalizacion.info(LogFiscalizacion.evento("deteccion_manual.hechos").campo("matricula", licensePlate)
                    .campo("hechos", facts.find(licensePlate).map(Object::toString).orElse("not registered")));
            TrackingEvent event = new TrackingEvent("manual", 0, 0, licensePlate, 0, 0, Instant.now());
            LogFiscalizacion.info(LogFiscalizacion.evento("deteccion_manual.situaciones").campo("matricula", licensePlate)
                    .campo("situaciones", createDetector(facts).detect(event).situations()));
            return 0;
        } catch (IOException e) {
            LogFiscalizacion.error(LogFiscalizacion.evento("deteccion_manual.fallida").error(e));
            return 1;
        }
    }

    private static String wildFlyUrl() {
        return env("WILDFLY_URL", "remote+http://localhost:8081");
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

    /**
     * Retries until MongoDB is available (the script may start first).
     *
     * @return false if the process was stopped while waiting
     */
    private static boolean waitForMongo(MongoDatabase database, CountDownLatch stop) throws InterruptedException {
        while (true) {
            try {
                database.runCommand(new Document("ping", 1));
                return true;
            } catch (MongoSecurityException e) {
                throw e; // wrong credentials: retrying won't help
            } catch (MongoTimeoutException e) {
                LogFiscalizacion.error(LogFiscalizacion.evento("mongodb.reintentando").campo("reintentoSegundos", RETRY_DELAY_MS / 1000));
                if (stop.await(RETRY_DELAY_MS, TimeUnit.MILLISECONDS)) {
                    return false;
                }
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

    /**
     * Retries until RabbitMQ is available (the script may start first).
     *
     * @return null if the process was stopped while waiting
     */
    private static Connection connectToRabbit(ConnectionFactory factory, CountDownLatch stop)
            throws InterruptedException, IOException {
        while (true) {
            try {
                return factory.newConnection("fiscalizacion-automatica");
            } catch (AuthenticationFailureException e) {
                throw e;
            } catch (IOException | TimeoutException e) {
                LogFiscalizacion.error(LogFiscalizacion.evento("rabbitmq.reintentando")
                        .campo("rabbitmq", factory.getHost() + ":" + factory.getPort())
                        .campo("reintentoSegundos", RETRY_DELAY_MS / 1000));
                if (stop.await(RETRY_DELAY_MS, TimeUnit.MILLISECONDS)) {
                    return null;
                }
            }
        }
    }
}
