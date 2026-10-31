package tse.lab.frontofficeciudadanos.service.rutas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/** LineString canonico: [longitud,latitud], redondeo fijo, sin puntos consecutivos repetidos. */
public final class GeoJsonRutas {
    private static final int MAX_PUNTOS = 20_000;
    private static final int DECIMALES = 6;

    private GeoJsonRutas() { }

    public static String normalizar(JsonObject geometria) {
        if (geometria == null || !"LineString".equals(geometria.getString("type", ""))) {
            throw new IllegalArgumentException("OSRM debe devolver un GeoJSON LineString");
        }
        JsonArray coordenadas = geometria.getJsonArray("coordinates");
        if (coordenadas == null || coordenadas.size() < 2 || coordenadas.size() > MAX_PUNTOS) {
            throw new IllegalArgumentException("Geometria sin puntos suficientes o demasiado extensa");
        }
        List<String> puntos = new ArrayList<>();
        String anterior = null;
        for (JsonValue valor : coordenadas) {
            if (valor.getValueType() != JsonValue.ValueType.ARRAY) {
                throw new IllegalArgumentException("Coordenada GeoJSON no es un par de numeros");
            }
            JsonArray par = valor.asJsonArray();
            if (par.size() != 2 || par.get(0).getValueType() != JsonValue.ValueType.NUMBER
                    || par.get(1).getValueType() != JsonValue.ValueType.NUMBER) {
                throw new IllegalArgumentException("Coordenada debe ser [longitud,latitud]");
            }
            double lon = par.getJsonNumber(0).doubleValue();
            double lat = par.getJsonNumber(1).doubleValue();
            if (!Double.isFinite(lon) || !Double.isFinite(lat) || lon < -180 || lon > 180
                    || lat < -90 || lat > 90) {
                throw new IllegalArgumentException("Coordenadas WGS84 invalidas");
            }
            String punto = "[" + decimal(lon) + "," + decimal(lat) + "]";
            if (!punto.equals(anterior)) {
                puntos.add(punto);
                anterior = punto;
            }
        }
        if (puntos.size() < 2) {
            throw new IllegalArgumentException("La ruta no contiene dos puntos distintos");
        }
        return "{\"type\":\"LineString\",\"coordinates\":[" + String.join(",", puntos) + "]}";
    }

    private static String decimal(double valor) {
        BigDecimal numero = BigDecimal.valueOf(valor).setScale(DECIMALES, RoundingMode.HALF_UP).stripTrailingZeros();
        return numero.signum() == 0 ? "0" : numero.toPlainString();
    }
}
