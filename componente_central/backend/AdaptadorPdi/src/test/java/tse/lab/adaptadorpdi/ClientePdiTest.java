package tse.lab.adaptadorpdi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// ClientePdi against a local HTTP server that answers with the queued responses, in order
class ClientePdiTest {

    private static final String CI = "11111111";
    private static final String CORRELATION = "corr-1";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private static final String FOUND = envelope("""
            <ns2:ObjPersona>
              <ns2:primerApellido>Perez</ns2:primerApellido><ns2:Nombre1>Juan</ns2:Nombre1>
              <ns2:FechaNacimiento>1980-05-10</ns2:FechaNacimiento>
            </ns2:ObjPersona>""");
    private static final String NOT_FOUND = envelope("""
            <ns2:Errores><ns2:Mensaje><ns2:CodMensaje>1001</ns2:CodMensaje></ns2:Mensaje></ns2:Errores>""");
    private static final String FAULT = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"><SOAP-ENV:Body>
            <SOAP-ENV:Fault><faultcode>SOAP-ENV:Server</faultcode><faultstring>PDI caida</faultstring></SOAP-ENV:Fault>
            </SOAP-ENV:Body></SOAP-ENV:Envelope>""";

    private record Reply(int status, String body, long delayMs) {
        static Reply ok(String body) {
            return new Reply(200, body, 0);
        }

        static Reply error(int status) {
            return new Reply(status, "<html>error</html>", 0);
        }
    }

    private final Deque<Reply> replies = new ArrayDeque<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String soapAction;
    private volatile String correlationHeader;
    private HttpServer server;
    private String url;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/ws", this::handle);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ws";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        Thread.interrupted();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        soapAction = exchange.getRequestHeaders().getFirst("SOAPAction");
        correlationHeader = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        exchange.getRequestBody().readAllBytes();
        Reply reply;
        synchronized (replies) {
            reply = replies.isEmpty() ? Reply.error(503) : replies.poll();
        }
        if (reply.delayMs() > 0) {
            try {
                Thread.sleep(reply.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(reply.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException e) {
            // the client already gave up (timeout)
        }
    }

    private void queue(Reply... rs) {
        synchronized (replies) {
            replies.addAll(List.of(rs));
        }
    }

    private ClientePdi client(int retries, int circuitThreshold, Duration circuitWait) {
        return new ClientePdi(url, "ORG", "clave", TIMEOUT, retries, circuitThreshold, circuitWait);
    }

    @Test
    void returnsThePersonAndSendsTheSoapHeaders() throws Exception {
        queue(Reply.ok(FOUND));

        DatosPersonaPdi persona = client(0, 3, Duration.ofSeconds(30)).consultarPersona(CI, CORRELATION).orElseThrow();

        assertEquals("Juan", persona.primerNombre());
        assertEquals("\"" + MensajesDnic.ACCION + "\"", soapAction);
        assertEquals(CORRELATION, correlationHeader);
    }

    @Test
    void personNotFoundIsEmpty() throws Exception {
        queue(Reply.ok(NOT_FOUND));

        assertFalse(client(0, 3, Duration.ofSeconds(30)).consultarPersona(CI, CORRELATION).isPresent());
    }

    @Test
    void retriesAfterAServerErrorAndThenSucceeds() throws Exception {
        queue(Reply.error(503), Reply.ok(FOUND));

        assertTrue(client(1, 3, Duration.ofSeconds(30)).consultarPersona(CI, CORRELATION).isPresent());
        assertEquals(2, requests.get());
    }

    @Test
    void failsAfterTheLastRetry() {
        queue(Reply.error(500), Reply.error(502));

        assertThrows(PdiNoDisponibleException.class,
                () -> client(1, 3, Duration.ofSeconds(30)).consultarPersona(CI, CORRELATION));
        assertEquals(2, requests.get());
    }

    @Test
    void aSoapFaultWithStatus500IsNotAvailable() {
        queue(new Reply(500, FAULT, 0));

        PdiNoDisponibleException e = assertThrows(PdiNoDisponibleException.class,
                () -> client(0, 3, Duration.ofSeconds(30)).consultarPersona(CI, CORRELATION));
        assertTrue(e.getMessage().contains("PDI caida"));
    }

    @Test
    void aSlowAnswerTimesOut() {
        queue(new Reply(200, FOUND, 1500));
        ClientePdi client = new ClientePdi(url, "ORG", "clave", Duration.ofMillis(200), 0, 3, Duration.ofSeconds(30));

        assertThrows(PdiNoDisponibleException.class, () -> client.consultarPersona(CI, CORRELATION));
    }

    @Test
    void anUnreachablePdiIsNotAvailable() {
        server.stop(0);

        assertThrows(PdiNoDisponibleException.class,
                () -> client(0, 3, Duration.ofSeconds(30)).consultarPersona(CI, CORRELATION));
    }

    @Test
    void missingCredentialsDoNotCallThePdi() {
        ClientePdi client = new ClientePdi(url, "", " ", TIMEOUT, 0, 3, Duration.ofSeconds(30));

        assertThrows(PdiNoDisponibleException.class, () -> client.consultarPersona(CI, CORRELATION));
        assertEquals(0, requests.get());
    }

    @Test
    void theCircuitOpensAfterTheThresholdAndClosesAfterTheWait() throws Exception {
        ClientePdi client = client(0, 1, Duration.ofMillis(300));
        queue(Reply.error(503));

        assertThrows(PdiNoDisponibleException.class, () -> client.consultarPersona(CI, CORRELATION));
        PdiNoDisponibleException open = assertThrows(PdiNoDisponibleException.class,
                () -> client.consultarPersona(CI, CORRELATION));
        assertTrue(open.getMessage().startsWith("Circuit breaker"));
        assertEquals(1, requests.get());

        Thread.sleep(400);
        queue(Reply.ok(FOUND));
        assertTrue(client.consultarPersona(CI, CORRELATION).isPresent());
        assertEquals(2, requests.get());
    }

    @Test
    void anInterruptedCallStopsRetrying() {
        queue(Reply.error(503), Reply.error(503));
        ClientePdi client = client(1, 3, Duration.ofSeconds(30));

        Thread.currentThread().interrupt();
        assertThrows(PdiNoDisponibleException.class, () -> client.consultarPersona(CI, CORRELATION));
        assertTrue(Thread.interrupted());
    }

    @Test
    void theDefaultConstructorReadsTheEnvironment() {
        // Without PDI_ORGANISMO_CODIGO / PDI_ORGANISMO_CLAVE it never calls the PDI
        if (System.getenv("PDI_ORGANISMO_CODIGO") == null) {
            assertThrows(PdiNoDisponibleException.class, () -> new ClientePdi().consultarPersona(CI, CORRELATION));
        }
    }

    private static String envelope(String result) {
        return """
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                  <SOAP-ENV:Body>
                    <ns2:obtDocDigitalizadoResponse xmlns:ns2="http://wsServicioDeInformacion/">
                      <ns2:obtDocDigitalizadoResult>%s</ns2:obtDocDigitalizadoResult>
                    </ns2:obtDocDigitalizadoResponse>
                  </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>""".formatted(result);
    }
}
