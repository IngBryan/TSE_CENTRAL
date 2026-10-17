package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

// Clave (matricula, fecha_hora): primaria de EventoPesajes y referencia a los eventos de tracking.
class ClaveEventoTest {

    private static final Instant FECHA = Instant.parse("2026-10-12T17:41:00Z");

    @Test
    void guardaMatriculaYFecha() {
        ClaveEvento clave = new ClaveEvento("SBC1234", FECHA);
        assertEquals("SBC1234", clave.getMatricula());
        assertEquals(FECHA, clave.getFechaHora());

        ClaveEvento vacia = new ClaveEvento();
        assertNull(vacia.getMatricula());
        assertNull(vacia.getFechaHora());
    }

    @Test
    void igualesSiCoincidenMatriculaYFecha() {
        assertEquals(new ClaveEvento("SBC1234", FECHA), new ClaveEvento("SBC1234", FECHA));
        assertEquals(new ClaveEvento("SBC1234", FECHA).hashCode(), new ClaveEvento("SBC1234", FECHA).hashCode());
    }

    @Test
    void distintasSiCambiaMatriculaOFecha() {
        ClaveEvento clave = new ClaveEvento("SBC1234", FECHA);
        assertNotEquals(clave, null);
        Object otraClase = "SBC1234";
        assertNotEquals(clave, otraClase);
        assertNotEquals(clave, new ClaveEvento("STU5678", FECHA));
        assertNotEquals(clave, new ClaveEvento("SBC1234", FECHA.plusSeconds(1)));
    }
}
