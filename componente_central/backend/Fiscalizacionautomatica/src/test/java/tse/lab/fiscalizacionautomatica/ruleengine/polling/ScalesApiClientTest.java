package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

class ScalesApiClientTest {

    private static final String CLIENT_ID = "componente-central";
    private static final String CLIENT_SECRET = "s3cr3t";

    private final AtomicInteger issuedTokens = new AtomicInteger();
    private final List<String> tokenAuthHeaders = new CopyOnWriteArrayList<>();
    private final List<String> upgradeHeaders = new CopyOnWriteArrayList<>();
    private final List<String> weighingQueries = new CopyOnWriteArrayList<>();
    private volatile String rejectedToken;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T12:00:00Z"));
    private HttpServer server;
    private ScalesApiClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/oauth/token", exchange -> {
            tokenAuthHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            if (upgrade != null) {
                upgradeHeaders.add(upgrade);
            }
            respond(exchange, 200, """
                    {"access_token": "token-%d", "token_type": "Bearer", "expires_in": 3600}
                    """.formatted(issuedTokens.incrementAndGet()));
        });
        server.createContext("/api/scales/1/weighings", exchange -> {
            if (("Bearer " + rejectedToken).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                respond(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }
            weighingQueries.add(exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                    [{"id": 1, "scaleId": 1, "licensePlate": "SBC1234", "datetime": "2026-10-10T07:15:00.000-03:00", "weight": 18450},
                     {"id": 2, "scaleId": 1, "licensePlate": "STU5678", "datetime": "2026-10-10T10:20:00.000Z"},
                     {"id": 3, "scaleId": 1, "licensePlate": "AAB9012", "datetime": "today", "weight": 30000}]
                    """);
        });
        server.start();
        client = new ScalesApiClient("http://localhost:" + server.getAddress().getPort() + "/", CLIENT_ID,
                CLIENT_SECRET, clock);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private List<Weighing> fetchScale1() throws Exception {
        return client.listWeighings(1, "SBC1234", Instant.parse("2026-10-10T10:00:00Z"), Instant.parse("2026-10-10T11:00:00Z"));
    }

    @Test
    void sendsTheDateRangeAndSkipsIncompleteWeighings() throws Exception {
        List<Weighing> weighings = fetchScale1();

        // 10:00Z / 11:00Z travel in Uruguay time
        assertEquals(List.of("licensePlate=SBC1234&from=2026-10-10T07:00:00.000-03:00&to=2026-10-10T08:00:00.000-03:00"),
                weighingQueries);
        assertEquals(List.of(new Weighing(1, 1, "SBC1234", Instant.parse("2026-10-10T10:15:00Z"), 18450)),
                weighings);
    }

    @Test
    void reusesTheTokenUntilItIsAboutToExpire() throws Exception {
        fetchScale1();
        fetchScale1();
        assertEquals(1, issuedTokens.get());

        clock.advance(Duration.ofMinutes(59).plusSeconds(31));
        fetchScale1();
        assertEquals(2, issuedTokens.get());

        String basic = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(UTF_8));
        assertEquals("Basic " + basic, tokenAuthHeaders.getFirst());
    }

    @Test
    void doesNotAskForAnHttp2Upgrade() throws Exception {
        // The Node.js server of the balanzas node drops connections that ask for an upgrade
        fetchScale1();

        assertEquals(List.of(), upgradeHeaders);
    }

    @Test
    void getsANewTokenAndRetriesOnceWhenRejected() throws Exception {
        fetchScale1();
        rejectedToken = "token-1";

        List<Weighing> weighings = fetchScale1();

        assertEquals(2, issuedTokens.get());
        assertEquals(1, weighings.size());
    }
}
