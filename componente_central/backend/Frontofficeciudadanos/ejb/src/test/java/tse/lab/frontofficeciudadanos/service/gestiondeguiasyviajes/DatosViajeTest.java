package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// DTO del EJB remoto de viajes en curso: viaja por valor, asi que equals y hashCode tienen que comparar todo.
class DatosViajeTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 10, 12);
    private static final Instant ASIGNACION = Instant.parse("2026-10-12T10:00:00Z");

    private static DatosViaje viaje() {
        return new DatosViaje("GV-1", "STU5678", 1L, "EN_CURSO", FECHA, ASIGNACION, "Mercedes", "Nueva Palmira",
                20000);
    }

    @Test
    void getters() {
        DatosViaje v = viaje();
        assertEquals("GV-1", v.getIdGuia());
        assertEquals("STU5678", v.getMatricula());
        assertEquals(1L, v.getNroEmpresa());
        assertEquals("EN_CURSO", v.getEstado());
        assertEquals(FECHA, v.getFecha());
        assertEquals(ASIGNACION, v.getFechaAsignacion());
        assertEquals("Mercedes", v.getOrigen());
        assertEquals("Nueva Palmira", v.getDestino());
        assertEquals(20000, v.getPesoCargaKg());
    }

    @Test
    void igualesSiCoincidenTodosLosCampos() {
        assertEquals(viaje(), viaje());
        assertEquals(viaje().hashCode(), viaje().hashCode());
        assertTrue(viaje().toString().contains("GV-1"));
    }

    @Test
    void distintosSiCambiaCualquierCampo() {
        DatosViaje v = viaje();
        assertNotEquals(v, null);
        assertNotEquals(v, "GV-1");
        assertNotEquals(v, new DatosViaje("GV-2", "STU5678", 1L, "EN_CURSO", FECHA, ASIGNACION, "Mercedes",
                "Nueva Palmira", 20000));
        assertNotEquals(v, new DatosViaje("GV-1", "SBC1234", 1L, "EN_CURSO", FECHA, ASIGNACION, "Mercedes",
                "Nueva Palmira", 20000));
        assertNotEquals(v, new DatosViaje("GV-1", "STU5678", 2L, "EN_CURSO", FECHA, ASIGNACION, "Mercedes",
                "Nueva Palmira", 20000));
        assertNotEquals(v, new DatosViaje("GV-1", "STU5678", 1L, "ASIGNADA", FECHA, ASIGNACION, "Mercedes",
                "Nueva Palmira", 20000));
        assertNotEquals(v, new DatosViaje("GV-1", "STU5678", 1L, "EN_CURSO", FECHA.plusDays(1), ASIGNACION,
                "Mercedes", "Nueva Palmira", 20000));
        assertNotEquals(v, new DatosViaje("GV-1", "STU5678", 1L, "EN_CURSO", FECHA, ASIGNACION.plusSeconds(1),
                "Mercedes", "Nueva Palmira", 20000));
        assertNotEquals(v, new DatosViaje("GV-1", "STU5678", 1L, "EN_CURSO", FECHA, ASIGNACION, "Rivera",
                "Nueva Palmira", 20000));
        assertNotEquals(v, new DatosViaje("GV-1", "STU5678", 1L, "EN_CURSO", FECHA, ASIGNACION, "Mercedes",
                "Montevideo", 20000));
        assertNotEquals(v, new DatosViaje("GV-1", "STU5678", 1L, "EN_CURSO", FECHA, ASIGNACION, "Mercedes",
                "Nueva Palmira", 21000));
    }
}
