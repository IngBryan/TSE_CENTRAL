package tse.lab.fiscalizacionautomatica.ruleengine.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
}
