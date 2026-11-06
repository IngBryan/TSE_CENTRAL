package tse.lab.fiscalizacionautomatica.ruleengine.detection.rules;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.*;
import tse.lab.fiscalizacionautomatica.ruleengine.model.*;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.model.EvidenciaHabilitacion;

class VehicleWithoutValidPermitRuleTest {
    private static final Instant FECHA = Instant.parse("2026-11-06T12:00:00Z");
    private static final LocalDate DIA = LocalDate.of(2026, 11, 6);
    private static final TrackingEvent GPS = new TrackingEvent("msg", 1, 1, "ABC1234", -34, -56, FECHA);
    private static final Weighing PESAJE = new Weighing(1, 1, "ABC1234", FECHA, 5000);
    private final VehicleWithoutValidPermitRule regla = new VehicleWithoutValidPermitRule();

    private static VehicleFacts hechos(EvidenciaHabilitacion prueba, boolean conGuia) {
        return new VehicleFacts(new DatosVehiculo("ABC1234", 1L, 1000, 4000, prueba), conGuia
                ? List.of(new DatosViaje("G-1", "EN_CURSO", 1L, "ABC1234", DIA, FECHA,
                        "Montevideo", "Salto", 1000)) : List.of());
    }

    @Test
    void documentosAusentesSeFiscalizanEnAmbosOrigenesConOSinGuia() throws Exception {
        var prueba = new EvidenciaHabilitacion("ABC1234", FECHA, null, null, null, null, null);
        for (boolean conGuia : List.of(true, false)) {
            assertTrue(regla.appliesTo(GPS, hechos(prueba, conGuia)));
            assertTrue(regla.appliesTo(PESAJE, hechos(prueba, conGuia)));
        }
    }

    @Test
    void documentosVigentesNoGeneranSituacionPeroCadaUnoVencidoSi() throws Exception {
        for (int permiso : List.of(0, 1)) {
            for (int itv : List.of(0, 1)) {
                var prueba = new EvidenciaHabilitacion("ABC1234", FECHA, "PNC", DIA.minusYears(1),
                        DIA.minusDays(permiso), DIA.minusYears(1), DIA.minusDays(itv));
                assertEquals(permiso == 1 || itv == 1, regla.appliesTo(GPS, hechos(prueba, false)));
            }
        }
    }

    @Test
    void fuenteIncompletaODeOtroEventoFallaParaReintentar() {
        assertThrows(IOException.class, () -> regla.appliesTo(GPS, hechos(null, false)));
        var ajena = new EvidenciaHabilitacion("ABC1234", FECHA.plusSeconds(1), null, null, null, null, null);
        assertThrows(IOException.class, () -> regla.appliesTo(GPS, hechos(ajena, false)));
    }

    @Test
    void detectorDetectaAmbasInfraccionesSinGuiaYOmiteVehiculosDesconocidos() throws Exception {
        var prueba = new EvidenciaHabilitacion("ABC1234", FECHA, null, null, null, null, null);
        var detector = new SituationDetector(m -> Optional.of(hechos(prueba, false)),
                List.of(regla, new TripWithoutGuideRule()));
        assertEquals(List.of(Situation.TRIP_WITHOUT_GUIDE, Situation.VEHICLE_WITHOUT_VALID_PERMIT),
                detector.detect(GPS).situations());
        assertTrue(new SituationDetector(m -> Optional.empty(), List.of(regla)).detect(GPS).situations().isEmpty());
    }
}
