package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

// DTO del EJB remoto de pesajes: viaja por valor, asi que equals y hashCode tienen que comparar todo.
class DatosPesajeTest {

    private static final Instant FECHA = Instant.parse("2026-10-12T17:41:00Z");

    private static DatosPesaje pesaje() {
        return new DatosPesaje(10, 4, "SBC1234", FECHA, 38500);
    }

    @Test
    void getters() {
        DatosPesaje p = pesaje();
        assertEquals(10, p.getIdExterno());
        assertEquals(4, p.getIdExternoBalanza());
        assertEquals("SBC1234", p.getMatricula());
        assertEquals(FECHA, p.getFechaHora());
        assertEquals(38500, p.getPesoKg());
    }

    @Test
    void igualesSiCoincidenTodosLosCampos() {
        assertEquals(pesaje(), pesaje());
        assertEquals(pesaje().hashCode(), pesaje().hashCode());
        assertTrue(pesaje().toString().contains("SBC1234"));
    }

    @Test
    void distintosSiCambiaCualquierCampo() {
        DatosPesaje p = pesaje();
        assertNotEquals(p, null);
        Object otraClase = "SBC1234";
        assertNotEquals(p, otraClase);
        assertNotEquals(p, new DatosPesaje(11, 4, "SBC1234", FECHA, 38500));
        assertNotEquals(p, new DatosPesaje(10, 5, "SBC1234", FECHA, 38500));
        assertNotEquals(p, new DatosPesaje(10, 4, "STU5678", FECHA, 38500));
        assertNotEquals(p, new DatosPesaje(10, 4, "SBC1234", FECHA.plusSeconds(1), 38500));
        assertNotEquals(p, new DatosPesaje(10, 4, "SBC1234", FECHA, 38000));
    }
}
