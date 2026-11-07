package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

class ContextoEventoSourceTest {
    private static final Instant T = Instant.parse("2026-10-17T13:00:00Z");
    private static final TrackingEvent EVENTO = new TrackingEvent("m", 1, 1, "SBC1234", -34, -56, T);

    @Test void guiaDelEventoAtrasadoNoEsLaActual() throws Exception {
        IntervaloGuiaGps antigua = new IntervaloGuiaGps("G-ANT", "SBC1234", "FINALIZADA",
                T.minusSeconds(100), T.minusSeconds(90), T.plusSeconds(10));
        IntervaloGuiaGps nueva = new IntervaloGuiaGps("G-ACT", "SBC1234", "EN_CURSO",
                T.plusSeconds(20), T.plusSeconds(30), null);
        var src = new ContextoEventoSource((m, desde, hasta) -> List.of(antigua, nueva));
        assertEquals("G-ANT", src.resolver(EVENTO).idGuia());
    }

    @Test void dosGuiasPosiblesDaAmbiguo() throws Exception {
        var a = new IntervaloGuiaGps("A", "SBC1234", "ASIGNADA", T.minusSeconds(5), null, null);
        var b = new IntervaloGuiaGps("B", "SBC1234", "ASIGNADA", T.minusSeconds(5), null, null);
        assertEquals(ResultadoAsociacionGpsGuia.Estado.AMBIGUO,
                new ContextoEventoSource((m, desde, hasta) -> List.of(a, b)).resolver(EVENTO).estado());
    }

    @Test void sinRespuestaDelBackendSeReintenta() {
        var src = new ContextoEventoSource((m, desde, hasta) -> null);
        assertThrows(java.io.IOException.class, () -> src.resolver(EVENTO));
    }

    @Test void sinIntervalosNoHayGuia() throws Exception {
        assertEquals(ResultadoAsociacionGpsGuia.Estado.SIN_GUIA,
                new ContextoEventoSource((m, desde, hasta) -> List.of()).resolver(EVENTO).estado());
    }

    @Test void guiaFinalizadaSinFinNoEsDeterminable() throws Exception {
        var sinFin = new IntervaloGuiaGps("A", "SBC1234", "FINALIZADA", T.minusSeconds(5), T.minusSeconds(4), null);
        assertEquals(ResultadoAsociacionGpsGuia.Estado.NO_DETERMINABLE,
                new ContextoEventoSource((m, desde, hasta) -> List.of(sinFin)).resolver(EVENTO).estado());
    }
}
