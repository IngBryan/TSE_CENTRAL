
package tse.lab.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NodoPerifericoTest {

    @Test
    void nuevaBalanzaSeInicializaCorrectamente() {
        NodoBalanza balanza = new NodoBalanza(
            "Balanza Ruta 5",
            "Ruta 5 km 30",
            5L
        );

        assertNull(balanza.getId());
        assertEquals("Balanza Ruta 5", balanza.getNombre());
        assertEquals("Ruta 5 km 30", balanza.getUbicacion());
        assertEquals(5L, balanza.getIdExterno());
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
            1L
        );

        assertNull(tracking.getId());
        assertEquals("Tracking Norte", tracking.getNombre());
        assertEquals("Artigas", tracking.getUbicacion());
        assertEquals(1L, tracking.getIdExterno());
        assertTrue(tracking.isActivo());
        assertNull(tracking.getUltimaComunicacionExitosa());
    }

    @Test
    void distintosTiposPuedenCompartirIdExterno() {
        NodoBalanza balanza = new NodoBalanza(
            "Balanza Ruta 5",
            "Ruta 5 km 30",
            100L
        );

        NodoTracking tracking = new NodoTracking(
            "Tracking Norte",
            "Artigas",
            100L
        );

        assertEquals(balanza.getIdExterno(), tracking.getIdExterno());
        assertNotEquals(balanza.getClass(), tracking.getClass());
    }
}
