package tse.lab.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NodoPerifericoTest {

    @Test
    void nuevaBalanzaSeInicializaCorrectamente() {
        NodoBalanza balanza = new NodoBalanza(
            "Balanza Ruta 5",
            "Ruta 5 km 30",
            "BAL-005"
        );

        assertNull(balanza.getId());
        assertEquals("Balanza Ruta 5", balanza.getNombre());
        assertEquals("Ruta 5 km 30", balanza.getUbicacion());
        assertEquals("BAL-005", balanza.getIdExterno());
        assertTrue(balanza.isActivo());
        assertNull(balanza.getUltimaComunicacionExitosa());
        assertNotNull(balanza.getEventos());
        assertTrue(balanza.getEventos().isEmpty());
    }

    @Test
    void nuevoTrackingSeInicializaCorrectamente() {
        NodoTracking tracking = new NodoTracking(
            "Tracking Norte",
            "Artigas",
            "GPS-001"
        );

        assertNull(tracking.getId());
        assertEquals("Tracking Norte", tracking.getNombre());
        assertEquals("Artigas", tracking.getUbicacion());
        assertEquals("GPS-001", tracking.getIdExterno());
        assertTrue(tracking.isActivo());
        assertNull(tracking.getUltimaComunicacionExitosa());
    }
}
