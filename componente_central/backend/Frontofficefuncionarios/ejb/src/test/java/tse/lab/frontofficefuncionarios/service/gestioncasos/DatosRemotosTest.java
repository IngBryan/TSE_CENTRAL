package tse.lab.frontofficefuncionarios.service.gestioncasos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

// DTOs del EJB remoto CasosAutomaticos: viajan por valor, se comparan por sus datos.
class DatosRemotosTest {

    private static final Instant T1 = Instant.parse("2026-10-19T13:00:00Z");

    @Test
    void elEventoFiscalizadoSeComparaPorMatriculaFechaYOrigen() {
        EventoFiscalizado evento = new EventoFiscalizado("SBC1234", T1, OrigenEvento.TRACKING);

        assertEquals(evento, new EventoFiscalizado("SBC1234", T1, OrigenEvento.TRACKING));
        assertEquals(evento.hashCode(), new EventoFiscalizado("SBC1234", T1, OrigenEvento.TRACKING).hashCode());
        assertNotEquals(evento, new EventoFiscalizado("STU5678", T1, OrigenEvento.TRACKING));
        assertNotEquals(evento, new EventoFiscalizado("SBC1234", T1.plusSeconds(1), OrigenEvento.TRACKING));
        assertNotEquals(evento, new EventoFiscalizado("SBC1234", T1, OrigenEvento.PESAJE));
        assertNotEquals(evento, "SBC1234");
        assertTrue(evento.toString().contains("SBC1234"));
    }

    @Test
    void losDatosDelCasoSeComparanPorIdTipoYEstado() {
        DatosCaso caso = new DatosCaso(4L, "VIAJE_SIN_GUIA", "NOTIFICADA");

        assertEquals(caso, new DatosCaso(4L, "VIAJE_SIN_GUIA", "NOTIFICADA"));
        assertEquals(caso.hashCode(), new DatosCaso(4L, "VIAJE_SIN_GUIA", "NOTIFICADA").hashCode());
        assertNotEquals(caso, new DatosCaso(5L, "VIAJE_SIN_GUIA", "NOTIFICADA"));
        assertNotEquals(caso, new DatosCaso(4L, "EXCESO_CARGA", "NOTIFICADA"));
        assertNotEquals(caso, new DatosCaso(4L, "VIAJE_SIN_GUIA", "DETECTADA"));
        assertNotEquals(caso, 4L);
        assertTrue(caso.toString().contains("VIAJE_SIN_GUIA"));
    }
}
