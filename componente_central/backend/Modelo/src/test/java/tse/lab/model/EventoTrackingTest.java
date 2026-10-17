package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class EventoTrackingTest {

    private static final Instant FECHA = Instant.parse("2026-10-12T17:41:00Z");

    @Test
    void seIdentificaPorMatriculaYFecha() {
        EventoTracking evento = new EventoTracking("msg-1", 7L, 1L, "SBC1234", -34.9, -56.1, FECHA);

        assertEquals("msg-1", evento.getMessageId());
        assertEquals(7L, evento.getIdExterno());
        assertEquals(1L, evento.getGpsId());
        assertEquals("SBC1234", evento.getMatricula());
        assertEquals(-34.9, evento.getLatitud());
        assertEquals(-56.1, evento.getLongitud());
        assertEquals(FECHA, evento.getFechaHora());
        assertEquals(new ClaveEvento("SBC1234", FECHA), evento.getClave());
    }

    @Test
    void setters() {
        EventoTracking evento = new EventoTracking();
        assertNull(evento.getMessageId());

        evento.setMessageId("msg-2");
        evento.setIdExterno(8L);
        evento.setGpsId(2L);
        evento.setMatricula("STU5678");
        evento.setLatitud(-30.9);
        evento.setLongitud(-55.5);
        evento.setFechaHora(FECHA);

        assertEquals("msg-2", evento.getMessageId());
        assertEquals(8L, evento.getIdExterno());
        assertEquals(2L, evento.getGpsId());
        assertEquals("STU5678", evento.getMatricula());
        assertEquals(-30.9, evento.getLatitud());
        assertEquals(-55.5, evento.getLongitud());
        assertEquals(new ClaveEvento("STU5678", FECHA), evento.getClave());
    }
}
