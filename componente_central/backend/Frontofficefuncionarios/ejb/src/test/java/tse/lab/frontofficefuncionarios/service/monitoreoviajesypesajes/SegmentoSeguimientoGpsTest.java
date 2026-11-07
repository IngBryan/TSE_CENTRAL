package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

class SegmentoSeguimientoGpsTest {
    static final Instant T = Instant.parse("2026-10-20T12:00:00Z");
    static PuntoGpsSeguimiento p(int segundos) {
        return new PuntoGpsSeguimiento("m"+segundos, 1, "SBC1234", -34, -56, T.plusSeconds(segundos));
    }
    @Test void elCursorSeBasaEnTodosLosCandidatosNoEnLosFiltrados() {
        var a = new IntervaloGuiaGps("A", "SBC1234", "FINALIZADA", T.minusSeconds(30), T, T.plusSeconds(1));
        var b = new IntervaloGuiaGps("B", "SBC1234", "EN_CURSO", T.plusSeconds(1), T.plusSeconds(1), null);
        var pagina = SegmentoSeguimientoGps.filtrar("A", List.of(a,b), List.of(p(0),p(1),p(2)), 2);
        assertEquals(List.of(p(0)), pagina.posiciones());
        assertTrue(pagina.hayMas());
        assertEquals(T.plusSeconds(1).plusMillis(1), pagina.siguienteDesde());
    }
    @Test void ambiguosSeExcluyenYNoHayCursorSinMasCandidatos() {
        var a = new IntervaloGuiaGps("A", "SBC1234", "ASIGNADA", T.minusSeconds(1), null, null);
        var b = new IntervaloGuiaGps("B", "SBC1234", "ASIGNADA", T.minusSeconds(1), null, null);
        var pagina = SegmentoSeguimientoGps.filtrar("A", List.of(a,b), List.of(p(0)), 5);
        assertTrue(pagina.posiciones().isEmpty());
        assertFalse(pagina.hayMas());
        assertEquals(null, pagina.siguienteDesde());
    }
}
