package tse.lab.fiscalizacionautomatica.ruleengine.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;

/**
 * Calcula la distancia a la polilínea más cercana, nunca a su recta origen-destino.
 * JTS opera en un plano métrico local tangente al GPS (WGS84 no está en metros).
 * Aproximación equirectangular local válida para el corredor uruguayo a escala de kilómetros.
 * NO debe emplearse para establecer una medida geodésica legal o a escala continental.
 */
public final class CorredorRutas {
    private static final double RADIO_TIERRA_METROS = 6_371_008.8;
    private static final int MAX_VERTICES = 20_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GeometryFactory GEOMETRIAS = new GeometryFactory();

    private final List<List<Coordenada>> rutas;

    public record Coordenada(double longitud, double latitud) { }

    public CorredorRutas(List<DatosRutaAutorizada> autorizadas) throws IOException {
        if (autorizadas == null || autorizadas.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una ruta autorizada");
        }
        List<List<Coordenada>> resultado = new ArrayList<>();
        for (DatosRutaAutorizada ruta : autorizadas) {
            if (ruta == null || ruta.getGeometriaGeoJson() == null) {
                throw new IOException("Ruta autorizada sin geometría");
            }
            JsonNode geojson = JSON.readTree(ruta.getGeometriaGeoJson());
            if (geojson == null || !"LineString".equals(geojson.path("type").asText())) {
                throw new IOException("La ruta autorizada no es un LineString GeoJSON");
            }
            JsonNode coords = geojson.path("coordinates");
            if (!coords.isArray() || coords.size() < 2 || coords.size() > MAX_VERTICES) {
                throw new IOException("Cantidad de vértices inválida en ruta autorizada");
            }
            List<Coordenada> puntos = new ArrayList<>();
            for (JsonNode par : coords) {
                if (!par.isArray() || par.size() != 2 || !par.get(0).isNumber() || !par.get(1).isNumber()) {
                    throw new IOException("Coordenadas GeoJSON inválidas");
                }
                double lon = par.get(0).asDouble();
                double lat = par.get(1).asDouble();
                if (!Double.isFinite(lon) || !Double.isFinite(lat) || lon < -180 || lon > 180
                        || lat < -90 || lat > 90) {
                    throw new IOException("Coordenadas fuera de WGS84");
                }
                puntos.add(new Coordenada(lon, lat));
            }
            if (puntos.stream().distinct().count() < 2) {
                throw new IOException("Ruta sin dos puntos distintos");
            }
            resultado.add(List.copyOf(puntos));
        }
        rutas = List.copyOf(resultado);
    }

    public double distanciaMinimaMetros(double latitud, double longitud) {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud) || latitud < -90 || latitud > 90
                || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("GPS fuera de WGS84");
        }
        Point gps = GEOMETRIAS.createPoint(new Coordinate(0, 0));
        double cosLat = Math.cos(Math.toRadians(latitud));
        double distancia = Double.POSITIVE_INFINITY;
        for (List<Coordenada> ruta : rutas) {
            Coordinate[] vertices = new Coordinate[ruta.size()];
            for (int i = 0; i < ruta.size(); i++) {
                Coordenada p = ruta.get(i);
                double dLon = p.longitud() - longitud;
                // En Uruguay no se cruza el antimeridiano, pero evitamos diferencias >180°.
                dLon = ((dLon + 540) % 360) - 180;
                vertices[i] = new Coordinate(RADIO_TIERRA_METROS * Math.toRadians(dLon) * cosLat,
                        RADIO_TIERRA_METROS * Math.toRadians(p.latitud() - latitud));
            }
            LineString linea = GEOMETRIAS.createLineString(vertices);
            distancia = Math.min(distancia, gps.distance(linea));
        }
        return distancia;
    }
}
