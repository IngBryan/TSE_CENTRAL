package tse.lab.frontofficeciudadanos.service.adaptadorpdi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

// Cliente SOAP del Servicio de Informacion DNIC expuesto por la PDI (TSE_NODOS/pdi-service).
//
// Resiliencia (CU-03 FA3):
// - timeout por intento (PDI_TIMEOUT_MS, 5000 por defecto);
// - reintentos con espera exponencial y jitter (PDI_REINTENTOS, 2 por defecto: hasta 3 intentos).
//   Antes del reintento n se espera PDI_REINTENTO_BASE_MS * 2^(n-1) (500 ms, 1 s, 2 s...), con tope
//   PDI_REINTENTO_MAX_MS (4000), y de eso entre la mitad y el total al azar;
// - circuit breaker: tras PDI_CB_UMBRAL consultas fallidas seguidas (3) no se llama a la PDI
//   durante PDI_CB_ESPERA_S segundos (30) y se responde NO_DISPONIBLE de inmediato. Pasado ese
//   tiempo se deja pasar una consulta de prueba: si anda se cierra; si falla se vuelve a abrir por
//   el doble (30 s, 60 s, 120 s...) hasta PDI_CB_ESPERA_MAX_S (300).
//
// Las validaciones que quedaron pendientes mientras la PDI no respondia las reenvia
// RevalidacionPdiJob (CU-04).
//
// Configuracion por variables de entorno del contenedor de WildFly (docker-compose.yml).
@ApplicationScoped
public class ClientePdi {

    private static final Logger LOG = Logger.getLogger(ClientePdi.class.getName());

    private final String url = env("PDI_URL", "http://host.docker.internal:3005/ws/servicioInformacion");
    private final String organismo = env("PDI_ORGANISMO_CODIGO", "");
    private final String clave = env("PDI_ORGANISMO_CLAVE", "");
    private final Duration timeout = Duration.ofMillis(Long.parseLong(env("PDI_TIMEOUT_MS", "5000")));
    private final int reintentos = Integer.parseInt(env("PDI_REINTENTOS", "2"));
    private final Duration esperaReintento = Duration.ofMillis(Long.parseLong(env("PDI_REINTENTO_BASE_MS", "500")));
    private final Duration esperaReintentoMax = Duration.ofMillis(Long.parseLong(env("PDI_REINTENTO_MAX_MS", "4000")));
    private final int umbralCircuito = Integer.parseInt(env("PDI_CB_UMBRAL", "3"));
    private final Duration esperaCircuito = Duration.ofSeconds(Long.parseLong(env("PDI_CB_ESPERA_S", "30")));
    private final Duration esperaCircuitoMax = Duration.ofSeconds(Long.parseLong(env("PDI_CB_ESPERA_MAX_S", "300")));

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final AtomicInteger fallosSeguidos = new AtomicInteger();
    private final AtomicInteger aperturasSeguidas = new AtomicInteger();
    private volatile Instant circuitoAbiertoHasta = Instant.EPOCH;

    // Datos de la persona, o vacio si DNIC no la encuentra (no existe o dada de baja).
    public Optional<DatosPersonaPdi> consultarPersona(String cedula, String correlationId)
            throws PdiNoDisponibleException {
        if (Instant.now().isBefore(circuitoAbiertoHasta)) {
            log(Level.WARNING, correlationId, cedula, "circuito_abierto", 0, 0, "no se consulta a la PDI");
            throw new PdiNoDisponibleException("Circuit breaker abierto hasta " + circuitoAbiertoHasta);
        }
        if (organismo.isBlank() || clave.isBlank()) {
            throw new PdiNoDisponibleException("Faltan PDI_ORGANISMO_CODIGO / PDI_ORGANISMO_CLAVE");
        }

        PdiNoDisponibleException ultimo = null;
        for (int intento = 1; intento <= reintentos + 1; intento++) {
            long inicio = System.nanoTime();
            try {
                Optional<DatosPersonaPdi> persona = llamar(cedula, correlationId);
                fallosSeguidos.set(0);
                aperturasSeguidas.set(0);
                circuitoAbiertoHasta = Instant.EPOCH;
                log(Level.INFO, correlationId, cedula, persona.isPresent() ? "encontrada" : "no_encontrada",
                        intento, ms(inicio), null);
                return persona;
            } catch (PdiNoDisponibleException e) {
                ultimo = e;
                log(Level.WARNING, correlationId, cedula, "fallo", intento, ms(inicio), e.getMessage());
                if (intento <= reintentos && !esperar(
                        Backoff.conJitter(Backoff.exponencial(intento, esperaReintento, esperaReintentoMax)))) {
                    break;
                }
            }
        }

        if (fallosSeguidos.incrementAndGet() >= umbralCircuito) {
            // Cada vez que la consulta de prueba falla, el circuito queda abierto el doble de tiempo
            Duration espera = Backoff.exponencial(aperturasSeguidas.incrementAndGet(), esperaCircuito, esperaCircuitoMax);
            circuitoAbiertoHasta = Instant.now().plus(espera);
            log(Level.WARNING, correlationId, cedula, "circuito_abre", 0, 0,
                    fallosSeguidos.get() + " consultas fallidas seguidas, se reintenta en " + espera.toSeconds() + "s");
        }
        throw ultimo;
    }

    private Optional<DatosPersonaPdi> llamar(String cedula, String correlationId) throws PdiNoDisponibleException {
        String cuerpo = MensajesDnic.request(url, "urn:uuid:" + UUID.randomUUID(), organismo, clave, cedula,
                ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE), Instant.now(), 300);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + MensajesDnic.ACCION + "\"")
                .header("X-Correlation-Id", correlationId)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PdiNoDisponibleException("Consulta a la PDI interrumpida", e);
        } catch (Exception e) {
            throw new PdiNoDisponibleException("No se pudo contactar la PDI: " + e, e);
        }
        if (respuesta.statusCode() >= 500 && !respuesta.body().contains("Fault")) {
            throw new PdiNoDisponibleException("La PDI respondio HTTP " + respuesta.statusCode());
        }
        return MensajesDnic.respuesta(respuesta.body());
    }

    private static boolean esperar(Duration d) {
        try {
            Thread.sleep(d);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long ms(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    private static void log(Level nivel, String correlationId, String cedula, String evento, int intento,
            long duracionMs, String detalle) {
        LOG.log(nivel, () -> "evento=pdi.consulta correlationId=" + correlationId + " ci=" + cedula
                + " resultado=" + evento + (intento > 0 ? " intento=" + intento + " duracionMs=" + duracionMs : "")
                + (detalle != null ? " detalle=\"" + detalle.replace('"', '\'') + "\"" : ""));
    }

    private static String env(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}
