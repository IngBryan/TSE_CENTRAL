package tse.lab.frontofficeciudadanos.service.rutas;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import tse.lab.model.Localidad;

/** Cliente del servicio /route/v1/driving de OSRM. Sin acceso a JPA ni transaccion. */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class OsrmRutasClient {
    public record Candidata(String geoJsonCanonico, double distanciaMetros, double duracionSegundos) { }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final int LIMITE_RESPUESTA = 2_000_000;

    public List<Candidata> obtener(Localidad origen, Localidad destino) {
        String base = System.getenv("OSRM_BASE_URL");
        if (base == null || base.isBlank()) {
            base = "https://router.project-osrm.org"; // solo desarrollo: configurar servicio propio para carga
        }
        if (!base.matches("https?://[^?#]+")) {
            throw new RutasException(500, "osrm_configuracion", "OSRM_BASE_URL invalida");
        }
        String ruta = String.format(Locale.ROOT, "/route/v1/driving/%.6f,%.6f;%.6f,%.6f"
                + "?alternatives=false&overview=full&geometries=geojson&steps=false",
                origen.getLongitud(), origen.getLatitud(), destino.getLongitud(), destino.getLatitud());
        URI url = URI.create(base.replaceAll("/+$", "") + ruta);
        HttpRequest req = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "TSE-educativo/1.0 (route-preview)").GET().build();
        try {
            HttpResponse<String> respuesta = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new RutasException(503, "osrm_no_disponible", "OSRM devolvio HTTP " + respuesta.statusCode());
            }
            if (respuesta.body().length() > LIMITE_RESPUESTA) {
                throw new RutasException(502, "osrm_respuesta_invalida", "Respuesta OSRM demasiado extensa");
            }
            return parsear(respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RutasException(503, "osrm_interrumpido", "Consulta OSRM interrumpida", e);
        } catch (java.io.IOException e) {
            throw new RutasException(503, "osrm_no_disponible", "No se pudo contactar OSRM", e);
        }
    }

    /** Puro respecto a la red: permite probar OSRM con respuestas fijadas en tests. */
    public static List<Candidata> parsear(String respuesta) {
        try (JsonReader lector = Json.createReader(new StringReader(respuesta))) {
            JsonObject raiz = lector.readObject();
            String codigo = raiz.getString("code", "");
            if ("NoRoute".equals(codigo)) {
                throw new RutasException(422, "sin_ruta_vial", "OSRM no encontro ruta entre las localidades");
            }
            if (!"Ok".equals(codigo)) {
                throw new RutasException(502, "osrm_respuesta_invalida", "OSRM devolvio: " + codigo);
            }
            JsonArray rutas = raiz.getJsonArray("routes");
            if (rutas == null || rutas.isEmpty() || rutas.size() > 10) {
                throw new IllegalArgumentException("Cantidad de rutas OSRM invalida");
            }
            List<Candidata> resultado = new ArrayList<>();
            for (int i = 0; i < rutas.size(); i++) {
                JsonObject ruta = rutas.getJsonObject(i);
                double distancia = ruta.getJsonNumber("distance").doubleValue();
                double duracion = ruta.getJsonNumber("duration").doubleValue();
                if (!Double.isFinite(distancia) || distancia <= 0
                        || !Double.isFinite(duracion) || duracion <= 0) {
                    throw new IllegalArgumentException("Distancia o duracion OSRM invalida");
                }
                resultado.add(new Candidata(
                        GeoJsonRutas.normalizar(ruta.getJsonObject("geometry")), distancia, duracion));
            }
            return List.copyOf(resultado);
        } catch (RutasException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RutasException(502, "osrm_respuesta_invalida", "Geometria OSRM invalida", e);
        }
    }
}
