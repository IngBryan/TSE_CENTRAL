package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficefuncionarios.service.gestioncasos.EventoFiscalizado;
import tse.lab.frontofficefuncionarios.service.gestioncasos.OrigenEvento;

class RemoteCaseStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-17T13:00:00Z");

    @Test
    void aTrackingEventGoesAsTrackingAndAWeighingAsPesaje() {
        assertEquals(new EventoFiscalizado("SBC1234", NOW, OrigenEvento.TRACKING), RemoteCaseStore
                .toEventoFiscalizado(new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9, -56.1, NOW)));
        assertEquals(new EventoFiscalizado("SBC1234", NOW, OrigenEvento.PESAJE),
                RemoteCaseStore.toEventoFiscalizado(new Weighing(3, 4, "SBC1234", NOW, 18_390)));
    }
}
