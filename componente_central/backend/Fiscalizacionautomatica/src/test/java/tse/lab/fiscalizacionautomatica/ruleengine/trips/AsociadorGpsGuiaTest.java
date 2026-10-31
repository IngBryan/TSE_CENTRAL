package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ResultadoAsociacionGpsGuia.Estado;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

class AsociadorGpsGuiaTest {
    private static final Instant T = Instant.parse("2026-10-10T12:00:00Z");
    private static TrackingEvent gps(long seconds) {
        return new TrackingEvent("msg", 1, 3, "SBC1234", -34, -56, T.plusSeconds(seconds));
    }
    private static IntervaloGuiaGps guia(String id, long asignada, Long inicio, Long fin) {
        return new IntervaloGuiaGps(id, "SBC1234", fin == null ? "EN_CURSO" : "FINALIZADA",
                T.plusSeconds(asignada), inicio == null ? null : T.plusSeconds(inicio),
                fin == null ? null : T.plusSeconds(fin));
    }

    @Test
    void asociaPorFechaDeMedicionNoPorRecepcion() {
        List<IntervaloGuiaGps> ventanas = List.of(guia("G1", 0, 10L, 100L), guia("G2", 120, 130L, null));
        assertEquals("G1", AsociadorGpsGuia.resolver(gps(30), ventanas).idGuia());
        assertEquals("G2", AsociadorGpsGuia.resolver(gps(140), ventanas).idGuia());
        assertEquals(Estado.SIN_GUIA, AsociadorGpsGuia.resolver(gps(110), ventanas).estado());
    }

    @Test
    void finEsExclusivoYLaAsignacionInclusiva() {
        assertEquals("G1", AsociadorGpsGuia.resolver(gps(0), List.of(guia("G1", 0, 10L, 100L)))
                .idGuia());
        assertEquals(Estado.SIN_GUIA, AsociadorGpsGuia.resolver(gps(100), List.of(guia("G1", 0, 10L, 100L))).estado());
    }

    @Test
    void siHayDosAsignadasSinInicioLaPosicionEsAmbigua() {
        assertEquals(Estado.AMBIGUO, AsociadorGpsGuia.resolver(gps(30),
                List.of(guia("G1", 0, null, null), guia("G2", 20, null, null))).estado());
    }

    @Test
    void inicioAplicadoPriorizaUnaGuiaSobreOtraSoloAsignada() {
        assertEquals("G1", AsociadorGpsGuia.resolver(gps(30),
                List.of(guia("G1", 0, 10L, null), guia("G2", 20, null, null))).idGuia());
    }

    @Test
    void dosIniciosSimultaneosNoSeDesempatanArbitrariamente() {
        assertEquals(Estado.AMBIGUO, AsociadorGpsGuia.resolver(gps(30),
                List.of(guia("G1", 0, 10L, null), guia("G2", 20, 25L, null))).estado());
    }

    @Test
    void finalizadaSinFinAplicadoNoSeInterpretaComoSinGuia() {
        IntervaloGuiaGps incompleta = new IntervaloGuiaGps("G1", "SBC1234", "FINALIZADA", T, T, null);
        assertEquals(Estado.NO_DETERMINABLE,
                AsociadorGpsGuia.resolver(gps(300), List.of(incompleta)).estado());
    }

    @Test
    void finalizadaInciertaBloqueaAtribucionAOtraGuia() {
        IntervaloGuiaGps incompleta = new IntervaloGuiaGps("G1", "SBC1234", "FINALIZADA", T, T, null);
        assertEquals(Estado.NO_DETERMINABLE,
                AsociadorGpsGuia.resolver(gps(300), List.of(incompleta, guia("G2", 200, 210L, null))).estado());
    }

    @Test
    void resultadoInvalidoNoAdmiteGuiaEnUnEstadoIncierto() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoAsociacionGpsGuia(Estado.AMBIGUO, "G1"));
    }

    @Test
    void noCruzaMatriculasAunqueElHorarioCoincida() {
        IntervaloGuiaGps otra = new IntervaloGuiaGps("X", "OTRO", "EN_CURSO", T, T, null);
        assertEquals(Estado.SIN_GUIA, AsociadorGpsGuia.resolver(gps(20), List.of(otra)).estado());
    }
}
