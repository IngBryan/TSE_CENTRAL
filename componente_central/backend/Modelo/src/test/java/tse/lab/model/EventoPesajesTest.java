package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class EventoPesajesTest {

    private static final Instant FECHA = Instant.parse("2026-10-12T17:41:00Z");

    @Test
    void nuevoPesajeQuedaPendienteDeDeteccion() {
        NodoBalanza balanza = new NodoBalanza("Ruta 8 - Km 150", "Ruta 8 - Km 150", 4L);
        EventoPesajes pesaje = new EventoPesajes(10L, "SBC1234", FECHA, 38500, balanza);

        assertEquals(10L, pesaje.getIdExterno());
        assertEquals("SBC1234", pesaje.getMatricula());
        assertEquals(FECHA, pesaje.getFechaHora());
        assertEquals(38500, pesaje.getPeso());
        assertSame(balanza, pesaje.getNodoBalanza());
        assertEquals(new ClaveEvento("SBC1234", FECHA), pesaje.getClave());
        assertTrue(pesaje.getCasosFiscalizacion().isEmpty());
        assertFalse(pesaje.isDetectado());

        pesaje.marcarDetectado();
        assertTrue(pesaje.isDetectado());
    }

    @Test
    void setters() {
        EventoPesajes pesaje = new EventoPesajes();
        assertNull(pesaje.getIdExterno());
        NodoBalanza balanza = new NodoBalanza();

        pesaje.setIdExterno(11L);
        pesaje.setPeso(40000);
        pesaje.setNodoBalanza(balanza);

        assertEquals(11L, pesaje.getIdExterno());
        assertEquals(40000, pesaje.getPeso());
        assertSame(balanza, pesaje.getNodoBalanza());
    }

    @Test
    void casoReferenciaPesajesYEventosDeTrackingPorClave() {
        CasoFiscalizacion caso = new CasoFiscalizacion("Viaje sin guia", "VIAJE_SIN_GUIA", null);
        EventoPesajes pesaje = new EventoPesajes(10L, "SBC1234", FECHA, 38500, null);
        caso.getEventosPesajes().add(pesaje);
        caso.getClavesEventosTracking().add(new ClaveEvento("SBC1234", FECHA));

        assertEquals(1, caso.getEventosPesajes().size());
        assertEquals(new ClaveEvento("SBC1234", FECHA), caso.getClavesEventosTracking().get(0));
    }
}
