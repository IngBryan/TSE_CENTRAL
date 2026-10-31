package tse.lab.frontofficeciudadanos.service.rutas;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;
import tse.lab.model.RutaReferencia;

class OsrmRutasTest {
    private static final String A = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.1,-33.1]]}";
    private static final String B = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.2,-33.1]]}";
    private static final String C = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.3,-33.1]]}";
    private static final String D = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.4,-33.1]]}";

    private String normalizar(String texto) {
        try (var lector = Json.createReader(new StringReader(texto))) {
            return GeoJsonRutas.normalizar(lector.readObject());
        }
    }

    private OsrmRutasClient.Candidata candidata(String geo, double duracion) {
        return new OsrmRutasClient.Candidata(normalizar(geo), 10_000, duracion);
    }

    @Test
    void normalizaDecimalesEspaciosYDuplicadosConsecutivos() {
        String geometry = "{\"coordinates\":[[-58.0000004,-33.0000000],[-58,-33],[-58.1000002,-33.1000001]],"
                + "\"type\":\"LineString\"}";
        assertEquals(normalizar(A), normalizar(geometry));
    }

    @Test
    void noAceptaLineaConUnPuntoUnico() {
        assertThrows(IllegalArgumentException.class, () -> normalizar(
                "{\"type\":\"LineString\",\"coordinates\":[[0,0],[0,0]]}"));
    }

    @Test
    void noAceptaCoordenadasInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> normalizar(
                "{\"type\":\"LineString\",\"coordinates\":[[-181,0],[0,0]]}"));
    }

    @Test
    void interpretaRespuestaOsrmSinHacerLlamadaDeRed() {
        String json = "{\"code\":\"Ok\",\"routes\":[{\"distance\":10000,\"duration\":600,"
                + "\"geometry\":" + A + "}]}";
        var resultado = OsrmRutasClient.parsear(json);
        assertEquals(1, resultado.size());
        assertEquals(600, resultado.getFirst().duracionSegundos());
        assertEquals(normalizar(A), resultado.getFirst().geoJsonCanonico());
    }

    @Test
    void noRouteNoSeInterpretaComoRutaVaciaValida() {
        var error = assertThrows(RutasException.class,
                () -> OsrmRutasClient.parsear("{\"code\":\"NoRoute\"}"));
        assertEquals(422, error.status());
    }

    @Test
    void seleccionaRutasPor25PorCientoYNoPorOrdenDeRespuesta() {
        var p = PrevisualizacionRutasService.seleccionar(7L, 8L,
                List.of(candidata(C, 920), candidata(B, 700), candidata(A, 600)));
        assertEquals(2, p.rutas().size());
        assertEquals(normalizar(A), p.rutas().get(0).geoJson());
        assertEquals(normalizar(B), p.rutas().get(1).geoJson());
    }

    @Test
    void deduplicaGeometriasCanonicas() {
        var p = PrevisualizacionRutasService.seleccionar(7L, 8L,
                List.of(candidata(A, 600), candidata(A, 650), candidata(B, 700)));
        assertEquals(2, p.rutas().size());
        assertEquals(RutaReferencia.calcularHuella(normalizar(A)), p.rutas().get(0).sha256());
    }

    @Test
    void noSuperaTresAlternativas() {
        var p = PrevisualizacionRutasService.seleccionar(7L, 8L,
                List.of(candidata(A, 600), candidata(B, 601), candidata(C, 602), candidata(D, 603)));
        assertEquals(3, p.rutas().size());
    }

    @Test
    void identificadorDePrevisualizacionEsEstableYSensibleACambios() {
        var uno = PrevisualizacionRutasService.seleccionar(7L, 8L, List.of(candidata(A, 600)));
        var dos = PrevisualizacionRutasService.seleccionar(7L, 8L, List.of(candidata(A, 600)));
        var otro = PrevisualizacionRutasService.seleccionar(7L, 8L, List.of(candidata(B, 600)));
        assertEquals(uno.previsualizacionId(), dos.previsualizacionId());
        assertNotEquals(uno.previsualizacionId(), otro.previsualizacionId());
    }
}
