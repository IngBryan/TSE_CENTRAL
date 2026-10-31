package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

class AsociacionTemporalGuiaTest {
    static final Instant T = Instant.parse("2026-10-20T12:00:00Z");
    private static IntervaloGuiaGps guia(String id, String estado, Instant inicio, Instant fin) {
        return new IntervaloGuiaGps(id, "SBC1234", estado, T.minusSeconds(300), inicio, fin);
    }

    @Test void asignacionUnicaYFinExclusivo() {
        var g = guia("G1", "FINALIZADA", T.minusSeconds(60), T.plusSeconds(60));
        assertEquals("G1", AsociacionTemporalGuia.resolver("SBC1234", T, List.of(g)).idGuia());
        assertEquals(AsociacionTemporalGuia.Estado.SIN_GUIA,
                AsociacionTemporalGuia.resolver("SBC1234", T.plusSeconds(60), List.of(g)).estado());
    }
    @Test void desambiguaPorInicioYRechazaSuperposicion() {
        var a = guia("A", "ASIGNADA", null, null);
        var b = guia("B", "EN_CURSO", T.minusSeconds(120), null);
        assertEquals("B", AsociacionTemporalGuia.resolver("SBC1234", T, List.of(a,b)).idGuia());
        assertEquals(AsociacionTemporalGuia.Estado.AMBIGUO,
                AsociacionTemporalGuia.resolver("SBC1234", T, List.of(a, guia("C", "ASIGNADA", null, null))).estado());
    }
    @Test void finalizadaSinFinNoSeAdjudica() {
        var sinFin = guia("F", "FINALIZADA", T.minusSeconds(100), null);
        assertEquals(AsociacionTemporalGuia.Estado.NO_DETERMINABLE,
                AsociacionTemporalGuia.resolver("SBC1234", T, List.of(sinFin)).estado());
    }
}
