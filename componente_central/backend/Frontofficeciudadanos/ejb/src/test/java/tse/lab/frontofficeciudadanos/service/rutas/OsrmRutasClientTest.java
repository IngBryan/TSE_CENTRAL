package tse.lab.frontofficeciudadanos.service.rutas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.model.Localidad;

class OsrmRutasClientTest {
    private static final String GEOMETRIA = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.1,-33.1]]}";
    private static final String RESPUESTA = "{\"code\":\"Ok\",\"routes\":[{\"distance\":10000,\"duration\":600,"
            + "\"geometry\":" + GEOMETRIA + "}]}";
    private static final Localidad ORIGEN = new Localidad(1L, "Mercedes", "Soriano", -33.25, -58.03);
    private static final Localidad DESTINO = new Localidad(2L, "Dolores", "Soriano", -33.53, -58.21);

    private final OsrmRutasClient cliente = new OsrmRutasClient();
    private HttpServer servidor;
    private String base;
    private volatile String pedido;
    private volatile int status = 200;
    private volatile String cuerpo = RESPUESTA;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            pedido = intercambio.getRequestURI().toString();
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(status, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private RutasException falla(String url) {
        return assertThrows(RutasException.class, () -> cliente.obtener(url, ORIGEN, DESTINO));
    }

    @Test
    void asksForTheRouteWithLongitudeFirst() {
        List<OsrmRutasClient.Candidata> rutas = cliente.obtener(base + "//", ORIGEN, DESTINO);

        assertEquals(1, rutas.size());
        assertEquals(10000, rutas.getFirst().distanciaMetros());
        assertTrue(pedido.startsWith("/route/v1/driving/-58.030000,-33.250000;-58.210000,-33.530000?"), pedido);
    }

    @Test
    void anErrorStatusIsAnUnavailableService() {
        status = 500;

        RutasException e = falla(base);

        assertEquals(503, e.status());
        assertEquals("osrm_no_disponible", e.codigo());
    }

    @Test
    void anOversizedAnswerIsRejected() {
        cuerpo = " ".repeat(2_000_001);

        assertEquals("osrm_respuesta_invalida", falla(base).codigo());
    }

    @Test
    void aServerThatDoesNotAnswerIsAnUnavailableService() {
        servidor.stop(0);

        assertEquals("osrm_no_disponible", falla(base).codigo());
    }

    @Test
    void aBaseUrlThatIsNotHttpIsAConfigurationError() {
        RutasException e = falla("ftp://osrm");

        assertEquals(500, e.status());
        assertEquals("osrm_configuracion", e.codigo());
        assertEquals("osrm_configuracion", falla("http://osrm?x=1").codigo());
    }

    @Test
    void removesOnlyTheTrailingSlashes() {
        assertEquals("http://osrm/a", OsrmRutasClient.sinBarrasFinales("http://osrm/a///"));
        assertEquals("http://osrm", OsrmRutasClient.sinBarrasFinales("http://osrm"));
        assertEquals("", OsrmRutasClient.sinBarrasFinales("//"));
    }

    @Test
    void anUnknownCodeIsAnInvalidAnswer() {
        RutasException e = assertThrows(RutasException.class, () -> OsrmRutasClient.parsear("{\"code\":\"Error\"}"));

        assertEquals(502, e.status());
    }

    @Test
    void withoutRoutesTheAnswerIsInvalid() {
        for (String json : List.of("{\"code\":\"Ok\"}", "{\"code\":\"Ok\",\"routes\":[]}")) {
            RutasException e = assertThrows(RutasException.class, () -> OsrmRutasClient.parsear(json));
            assertEquals("osrm_respuesta_invalida", e.codigo());
        }
    }

    @Test
    void aRouteWithoutDistanceOrDurationIsInvalid() {
        for (String medidas : List.of("\"distance\":0,\"duration\":600", "\"distance\":10,\"duration\":0")) {
            String json = "{\"code\":\"Ok\",\"routes\":[{" + medidas + ",\"geometry\":" + GEOMETRIA + "}]}";
            RutasException e = assertThrows(RutasException.class, () -> OsrmRutasClient.parsear(json));
            assertEquals("osrm_respuesta_invalida", e.codigo());
        }
    }
}
