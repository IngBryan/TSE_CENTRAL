package tse.lab.fiscalizacionautomatica.ruleengine.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;

class CorredorRutasTest {
    private static DatosRutaAutorizada ruta(String geometria) {
        return new DatosRutaAutorizada(1L, 1, "hash", 100_000, geometria);
    }
    private static CorredorRutas corredor(String geometria) throws IOException {
        return new CorredorRutas(ruta(geometria));
    }
    @Test
    void puntoSobreUnaRutaTieneDistanciaCero() throws IOException {
        // El meridiano central de UTM 21S se proyecta como una recta.
        var c = corredor("{\"type\":\"LineString\",\"coordinates\":[[-57,-34.5],[-57,-34]]}");
        assertEquals(0, c.distanciaMetros(-34.25, -57), 0.001);
    }

    @Test
    void coincideConReferenciasIndependientesDeProjEnAmbosHusosDeUruguay() throws IOException {
        // PROJ: +proj=utm +zone=21/22 +south +datum=WGS84. Distancia cartesiana
        // punto-segmento después de proyectar; tolerancia de dos centímetros.
        var oeste = corredor("{\"type\":\"LineString\",\"coordinates\":[[-56,-34.26],[-56,-34.24]]}");
        assertEquals(1998.273089448, oeste.distanciaMetros(-34.25, -55.9783), 0.02);

        var este = corredor("{\"type\":\"LineString\",\"coordinates\":[[-53,-32.01],[-53,-31.99]]}");
        assertEquals(2834.886713628, este.distanciaMetros(-32, -52.97), 0.02);
    }

    @Test
    void unaRutaQueCruzaElLimiteDeHusosUsaUnSoloPlano() throws IOException {
        var c = corredor("{\"type\":\"LineString\",\"coordinates\":[[-54.01,-34],[-53.99,-34]]}");
        // Centro en -54: EPSG:32722. Referencia independiente obtenida con PROJ.
        assertEquals(2219.702393497, c.distanciaMetros(-33.98, -54), 0.02);
        assertEquals(0, c.distanciaMetros(-34, -54.01), 0.001);
        assertEquals(0, c.distanciaMetros(-34, -53.99), 0.001);
    }

    @Test
    void fueraDelSegmentoMideHastaElExtremoMasCercano() throws IOException {
        var c = corredor("{\"type\":\"LineString\",\"coordinates\":[[-56,-34.26],[-56,-34.24]]}");
        assertEquals(4530.392077342, c.distanciaMetros(-34.20, -55.99), 0.02);
    }

    @Test
    void distingueUnMetroACadaLadoDelUmbral() throws IOException {
        var c = corredor("{\"type\":\"LineString\",\"coordinates\":[[-57,-34.26],[-57,-34.24]]}");
        // Inversa PROJ de (501999, y) y (502001, y) en EPSG:32721,
        // con y correspondiente a (-57, -34.25).
        assertEquals(1999, c.distanciaMetros(-34.249998077708, -56.978289772720), 0.001);
        assertEquals(2001, c.distanciaMetros(-34.249998073860, -56.978268051634), 0.001);
    }
    @Test
    void lasDistanciasEnWgs84SeMidenEnMetrosNoGrados() throws IOException {
        var c = corredor("{\"type\":\"LineString\",\"coordinates\":[[-56,-34.5],[-56,-34]]}");
        assertTrue(c.distanciaMetros(-34.25, -55.97) > 2000);
    }
    @Test
    void puntoCercaDelSegmentoYLejosDeLosExtremosTieneDistanciaCorta() throws IOException {
        var c = corredor("{\"type\":\"LineString\",\"coordinates\":[[-56,-34.5],[-56,-34]]}");
        assertTrue(c.distanciaMetros(-34.25, -55.99) < 2000);
    }
    @Test
    void validaGeometriaCorrupta() {
        assertThrows(IOException.class, () -> corredor("{\"type\":\"Point\",\"coordinates\":[-56,-34]}"));
    }

    @Test
    void anAuthorizedRouteIsRequired() {
        assertThrows(IOException.class, () -> new CorredorRutas(null));
    }

    @Test
    void aRouteWithoutGeometryFails() {
        assertThrows(IOException.class, () -> corredor(null));
        assertThrows(IOException.class, () -> corredor("{"));
    }

    @Test
    void invalidCoordinatesFail() {
        for (String coordenadas : List.of("5", "[[0,0]]", "[1,[0,0]]", "[[0],[1,1]]", "[[\"a\",0],[1,1]]",
                "[[0,\"a\"],[1,1]]", "[[181,0],[1,1]]", "[[-181,0],[1,1]]", "[[0,91],[1,1]]", "[[0,-91],[1,1]]",
                "[[1,1],[1,1]]")) {
            assertThrows(IOException.class,
                    () -> corredor("{\"type\":\"LineString\",\"coordinates\":" + coordenadas + "}"), coordenadas);
        }
    }

    @Test
    void aGpsOutsideWgs84IsRejected() throws IOException {
        var c = corredor("{\"type\":\"LineString\",\"coordinates\":[[-56,-34.5],[-56,-34]]}");

        assertThrows(IllegalArgumentException.class, () -> c.distanciaMetros(Double.NaN, -56));
        assertThrows(IllegalArgumentException.class, () -> c.distanciaMetros(-34, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> c.distanciaMetros(-91, -56));
        assertThrows(IllegalArgumentException.class, () -> c.distanciaMetros(91, -56));
        assertThrows(IllegalArgumentException.class, () -> c.distanciaMetros(-34, -181));
        assertThrows(IllegalArgumentException.class, () -> c.distanciaMetros(-34, 181));
    }

}
