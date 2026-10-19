package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ejb.EJBException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import javax.naming.Context;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficefuncionarios.service.gestioncasos.CasosAutomaticosRemote;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.EventoFiscalizado;
import tse.lab.frontofficefuncionarios.service.gestioncasos.OrigenEvento;

class RemoteCaseStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-17T13:00:00Z");
    private static final TrackingEvent EVENT = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9, -56.1, NOW);
    private static final EventoFiscalizado EVENTO = new EventoFiscalizado("SBC1234", NOW, OrigenEvento.TRACKING);
    private static final DatosCaso CASE = new DatosCaso(4L, "VIAJE_SIN_GUIA", "DETECTADA");

    private final Context context = mock(Context.class);
    private final CasosAutomaticosRemote remote = mock(CasosAutomaticosRemote.class);
    private final RemoteCaseStore store = new RemoteCaseStore(context, remote);

    @Test
    void aTrackingEventGoesAsTrackingAndAWeighingAsPesaje() {
        assertEquals(EVENTO, RemoteCaseStore.toEventoFiscalizado(EVENT));
        assertEquals(new EventoFiscalizado("SBC1234", NOW, OrigenEvento.PESAJE),
                RemoteCaseStore.toEventoFiscalizado(new Weighing(3, 4, "SBC1234", NOW, 18_390)));
    }

    @Test
    void everyOperationGoesToTheEjb() throws IOException {
        when(remote.casosAbiertos("SBC1234")).thenReturn(List.of(CASE));
        when(remote.casoDelEvento(EVENTO)).thenReturn(CASE);
        when(remote.crearCaso("VIAJE_SIN_GUIA", null, EVENTO)).thenReturn(CASE);
        DatosCaso notified = new DatosCaso(4L, "VIAJE_SIN_GUIA", "NOTIFICADA");
        when(remote.notificarCaso(4L)).thenReturn(notified);

        assertEquals(List.of(CASE), store.openCases("SBC1234"));
        assertEquals(CASE, store.caseOf(EVENT));
        assertEquals(CASE, store.create("VIAJE_SIN_GUIA", null, EVENT));
        store.addEvidence(4L, EVENT);
        assertEquals(notified, store.notify(4L));

        verify(remote).agregarEvidencia(4L, EVENTO);
    }

    @Test
    void aFailedRemoteCallIsAnIOException() {
        // e.g. the partial unique index rejected a duplicate open case
        when(remote.crearCaso(any(), any(), any())).thenThrow(new EJBException("uk_caso_abierto"));

        assertThrows(IOException.class, () -> store.create("VIAJE_SIN_GUIA", null, EVENT));
    }

    @Test
    void closingClosesTheContext() throws Exception {
        store.close();

        verify(context).close();
    }
}
