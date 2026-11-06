package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.ejb.EJBException;
import java.io.IOException;
import java.time.Instant;
import javax.naming.Context;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule.PruebaDesvio;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficefuncionarios.service.gestioncasos.*;
import tse.lab.model.EvidenciaHabilitacion;

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
        assertEquals(EVENT.correlationId(), RemoteCaseStore.toEventoFiscalizado(EVENT).getCorrelationId());
        assertEquals(new EventoFiscalizado("SBC1234", NOW, OrigenEvento.PESAJE),
                RemoteCaseStore.toEventoFiscalizado(new Weighing(3, 4, "SBC1234", NOW, 18_390)));
    }
    @Test void registraYNotificaMedianteEjb() throws IOException {
        when(remote.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, null, EVENTO))
                .thenReturn(CASE);
        assertEquals(CASE, store.registrar(TipoCasoAutomatico.EXCESO_CARGA, null, EVENT));
        verify(remote).registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, null, EVENTO);
        when(remote.registrarCirculacionSinGuia(any(PosicionSinGuiaRemota.class))).thenReturn(CASE);
        assertEquals(CASE, store.registrarCirculacionSinGuia(EVENT));
        var captor = org.mockito.ArgumentCaptor.forClass(PosicionSinGuiaRemota.class);
        verify(remote).registrarCirculacionSinGuia(captor.capture());
        assertEquals(NOW, captor.getValue().getFechaHora());
        assertEquals(-34.9, captor.getValue().getLatitud());
        when(remote.notificarCaso(4L, "c-1")).thenReturn(new DatosCaso(4L, "VIAJE_SIN_GUIA", "NOTIFICADA"));
        assertEquals("NOTIFICADA", store.notify(4L, "c-1").getEstado());
        verify(remote).notificarCaso(4L, "c-1");
    }
    @Test void transmiteLaPruebaDocumentalConElEventoYPropagaErroresRemotos() throws IOException {
        var prueba = new EvidenciaHabilitacion("SBC1234", NOW, null, null, null, null, null);
        when(remote.registrarHabilitacion("GV-1", EVENTO, prueba)).thenReturn(CASE);
        assertEquals(CASE, store.registrarHabilitacion("GV-1", EVENT, prueba));
        verify(remote).registrarHabilitacion("GV-1", EVENTO, prueba);
        when(remote.registrarHabilitacion("GV-1", EVENTO, prueba)).thenThrow(new EJBException("fallo"));
        assertThrows(IOException.class, () -> store.registrarHabilitacion("GV-1", EVENT, prueba));
    }
    @Test void transmitePruebaGeometricaConElEventoAlEjb() throws IOException {
        TrackingEvent otra = new TrackingEvent("msg-2", 8, 1, "SBC1234", -34.91, -56.12,
                NOW.plusSeconds(10));
        PruebaDesvio prueba = new PruebaDesvio(EVENT, otra, 2500, 2600, 2000, 30,
                List.of("sha-a", "sha-b"));
        when(remote.registrarDesvio(eq("GV-1"), eq(EVENTO), any(PruebaDesvioRemota.class)))
                .thenReturn(CASE);
        store.registrarDesvio("GV-1", EVENT, prueba);
        var captor = org.mockito.ArgumentCaptor.forClass(PruebaDesvioRemota.class);
        verify(remote).registrarDesvio(eq("GV-1"), eq(EVENTO), captor.capture());
        assertEquals(NOW.plusSeconds(10), captor.getValue().getInstantePareja());
        assertEquals(List.of("sha-a", "sha-b"), captor.getValue().getHashesRutas());
    }
    @Test void errorEJBPermiteReintento() {
        when(remote.registrarCirculacionSinGuia(any(PosicionSinGuiaRemota.class)))
                .thenThrow(new EJBException("backend temporalmente no disponible"));
        assertThrows(IOException.class, () -> store.registrarCirculacionSinGuia(EVENT));
    }
    @Test void closeLiberaContexto() throws Exception {
        store.close();
        verify(context).close();
    }
}
