package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

class HistorialGpsServiceTest {
    private static final Instant T = Instant.parse("2026-10-10T12:00:00Z");
    private final InMemoryTrackingEventDAO dao = new InMemoryTrackingEventDAO(Clock.fixed(T, ZoneOffset.UTC));

    private void save(int seconds) {
        dao.save(new TrackingEvent("msg-" + seconds, seconds, 3, "SBC1234", -34, -56,
                T.plusSeconds(seconds)));
    }

    @Test
    void historialSeparaViajesDelMismoCamionYSorteaPorTiempoGPS() throws IOException {
        save(150); save(20); save(130); save(10); save(70);
        IntervalosGuiasGpsSource fuente = (matricula, desde, hasta) -> List.of(
                new IntervaloGuiaGps("G1", matricula, "FINALIZADA", T, T.plusSeconds(5), T.plusSeconds(100)),
                new IntervaloGuiaGps("G2", matricula, "EN_CURSO", T.plusSeconds(110), T.plusSeconds(120), null));
        HistorialGpsService servicio = new HistorialGpsService(dao, fuente);
        assertEquals(List.of(10L, 20L, 70L), servicio.obtenerPorGuia("G1", "SBC1234", T,
                T.plusSeconds(200), 100).stream().map(TrackingEvent::externalId).toList());
        assertEquals(List.of(130L, 150L), servicio.obtenerPorGuia("G2", "SBC1234", T,
                T.plusSeconds(200), 100).stream().map(TrackingEvent::externalId).toList());
    }

    @Test
    void ambiguedadNoContaminaNingunaGuia() throws IOException {
        save(15);
        IntervalosGuiasGpsSource fuente = (matricula, desde, hasta) -> List.of(
                new IntervaloGuiaGps("G1", matricula, "ASIGNADA", T, null, null),
                new IntervaloGuiaGps("G2", matricula, "ASIGNADA", T.plusSeconds(2), null, null));
        assertEquals(List.of(), new HistorialGpsService(dao, fuente)
                .obtenerPorGuia("G1", "SBC1234", T, T.plusSeconds(20), 20));
    }

    @Test
    void errorDelBackendNoSeConfundeConAusenciaDeGps() {
        IntervalosGuiasGpsSource fuente = (matricula, desde, hasta) -> {
            throw new IOException("WildFly caido");
        };
        HistorialGpsService servicio = new HistorialGpsService(dao, fuente);
        assertThrows(IOException.class, () -> servicio.obtenerPorGuia("G1", "SBC1234", T,
                T.plusSeconds(20), 20));
        assertThrows(IllegalArgumentException.class, () -> servicio.obtenerPorGuia("G1", "SBC1234", T,
                T.plusSeconds(20), 5_001));
    }
}
