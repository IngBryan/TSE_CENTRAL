package tse.lab.fiscalizacionautomatica.ruleengine.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;

/**
 * Calcula la distancia a la polilínea admitida, nunca a su recta origen-destino.
 * GeoTools transforma WGS84 (longitud, latitud) a UTM, en metros, sobre el elipsoide WGS84.
 * Se elige la zona y el hemisferio por el centro de la envolvente de la ruta; toda la
 * polilínea y los GPS usan ese mismo CRS, incluso al cruzar un límite de zona.
 * JTS mide la distancia plana mínima a los segmentos proyectados. Está destinado a
 * rutas regionales, como las de Uruguay; conserva la distorsión propia de UTM.
 */
public final class CorredorRutas {
    private static final int MAX_VERTICES = 20_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GeometryFactory GEOMETRIAS = new GeometryFactory();

    private final MathTransform aMetros;
    private final LineString rutaMetros;

    public record Coordenada(double longitud, double latitud) { }

    public CorredorRutas(DatosRutaAutorizada autorizada) throws IOException {
        if (autorizada == null || autorizada.getGeometriaGeoJson() == null) {
            throw new IOException("Ruta admitida sin geometría");
        }
        JsonNode geojson = JSON.readTree(autorizada.getGeometriaGeoJson());
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
        LineString rutaWgs84 = GEOMETRIAS.createLineString(puntos.stream()
                .map(p -> new Coordinate(p.longitud(), p.latitud())).toArray(Coordinate[]::new));
        Coordinate centro = rutaWgs84.getEnvelopeInternal().centre();
        int zona = Math.min(60, (int) Math.floor((centro.x + 180) / 6) + 1);
        int epsg = (centro.y < 0 ? 32700 : 32600) + zona;
        try {
            // GeoJSON usa longitud, latitud; EPSG:4326 tiene otro orden por defecto.
            aMetros = CRS.findMathTransform(CRS.decode("EPSG:4326", true),
                    CRS.decode("EPSG:" + epsg), false);
            rutaMetros = (LineString) JTS.transform(rutaWgs84, aMetros);
        } catch (FactoryException | TransformException e) {
            throw new IOException("No se pudo proyectar la ruta autorizada a UTM", e);
        }
    }

    public double distanciaMetros(double latitud, double longitud) throws IOException {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud) || latitud < -90 || latitud > 90
                || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("GPS fuera de WGS84");
        }
        try {
            Point gps = GEOMETRIAS.createPoint(JTS.transform(new Coordinate(longitud, latitud), null, aMetros));
            double distancia = gps.distance(rutaMetros);
            if (!Double.isFinite(distancia)) {
                throw new IOException("La proyección UTM produjo una distancia no finita");
            }
            return distancia;
        } catch (TransformException e) {
            throw new IOException("No se pudo proyectar el GPS a UTM", e);
        }
    }
}
