package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.ejb.EJBException;
import java.io.IOException;
import java.time.Instant;
import javax.naming.Context;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficefuncionarios.service.gestioncasos.*;

class RemoteCaseStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-17T13:00:00Z");
    private static final TrackingEvent EVENT = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9, -56.1, NOW);
    private static final EventoFiscalizado EVENTO = new EventoFiscalizado("SBC1234", NOW, OrigenEvento.TRACKING);
    private static final DatosCaso CASE = new DatosCaso(4L, "VIAJE_SIN_GUIA", "DETECTADA");
    private final Context context = mock(Context.class);
    private final CasosAutomaticosRemote remote = mock(CasosAutomaticosRemote.class);
    private final RemoteCaseStore store = new RemoteCaseStore(context, remote);

    @Test void convierteAmbosTiposDeEvento() {
        assertEquals(EVENTO, RemoteCaseStore.toEventoFiscalizado(EVENT));
        assertEquals(new EventoFiscalizado("SBC1234", NOW, OrigenEvento.PESAJE),
                RemoteCaseStore.toEventoFiscalizado(new Weighing(3, 4, "SBC1234", NOW, 18_390)));
    }
    @Test void registraYNotificaMedianteEjb() throws IOException {
        when(remote.registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, EVENTO)).thenReturn(CASE);
        assertEquals(CASE, store.registrar(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, EVENT));
        verify(remote).registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, EVENTO);
        when(remote.notificarCaso(4L)).thenReturn(new DatosCaso(4L, "VIAJE_SIN_GUIA", "NOTIFICADA"));
        assertEquals("NOTIFICADA", store.notify(4L).getEstado());
    }
    @Test void errorEJBPermiteReintento() {
        when(remote.registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, EVENTO))
                .thenThrow(new EJBException("uk_caso_abierto_sin_guia"));
        assertThrows(IOException.class, () -> store.registrar(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, EVENT));
    }
    @Test void closeLiberaContexto() throws Exception {
        store.close();
        verify(context).close();
    }
}
