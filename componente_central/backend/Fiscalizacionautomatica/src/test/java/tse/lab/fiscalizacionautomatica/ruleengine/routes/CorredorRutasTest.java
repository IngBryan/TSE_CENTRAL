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
        var c = corredor("{\"type\":\"LineString\",\"coordinates\":[[-56,-34.5],[-56,-34]]}");
        assertEquals(0, c.distanciaMetros(-34.25, -56), 0.001);
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
