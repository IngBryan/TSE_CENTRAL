package tse.lab.fiscalizacionautomatica.ruleengine.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ejb.EJBException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.naming.Context;
import org.junit.jupiter.api.Test;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.DatosNodo;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.NodosActivosRemote;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.TipoNodo;

class ActiveNodesClientTest {

    private static final Instant NOW = Instant.parse("2026-10-19T13:00:00Z");

    private final Context context = mock(Context.class);
    private final NodosActivosRemote remote = mock(NodosActivosRemote.class);
    private final ActiveNodesClient client = new ActiveNodesClient(context, remote);

    private static DatosNodo node(long externalId, TipoNodo type) {
        return new DatosNodo(externalId * 10, externalId, "node " + externalId, type, "somewhere", true, null);
    }

    @Test
    void separatesTheActiveScalesFromTheActiveGps() throws IOException {
        when(remote.listarNodos()).thenReturn(List.of(node(4, TipoNodo.BALANZA), node(1, TipoNodo.TRACKING),
                node(2, TipoNodo.TRACKING)));

        assertEquals(List.of(4L), client.findActiveScaleIds());
        assertEquals(Set.of(1L, 2L), client.findActiveGpsIds());
    }

    @Test
    void reportsTheCommunicationsWithTheTypeOfNode() throws IOException {
        client.reportScaleCommunications(Map.of(4L, NOW));
        client.reportGpsCommunications(Map.of(1L, NOW));

        verify(remote).registrarComunicaciones(TipoNodo.BALANZA, Map.of(4L, NOW));
        verify(remote).registrarComunicaciones(TipoNodo.TRACKING, Map.of(1L, NOW));
    }

    @Test
    void aFailedRemoteCallIsAnIOException() {
        when(remote.listarNodos()).thenThrow(new EJBException("WildFly down"));
        doThrow(new EJBException("WildFly down")).when(remote).registrarComunicaciones(eq(TipoNodo.TRACKING), any());

        assertThrows(IOException.class, client::findActiveScaleIds);
        assertThrows(IOException.class, () -> client.reportGpsCommunications(Map.of(1L, NOW)));
    }

    @Test
    void closingClosesTheContext() throws Exception {
        client.close();

        verify(context).close();
    }
}
