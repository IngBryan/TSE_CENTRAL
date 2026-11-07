package tse.lab.fiscalizacionautomatica.ruleengine.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import javax.naming.Context;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;
import tse.lab.frontofficeciudadanos.service.rutas.RutasAutorizadasRemote;

class RemoteRutasAutorizadasSourceTest {
    private final Context context = mock(Context.class);
    private final RutasAutorizadasRemote remote = mock(RutasAutorizadasRemote.class);
    private final RemoteRutasAutorizadasSource fuente = new RemoteRutasAutorizadasSource(context, remote);

    @Test
    void asksTheEjbForTheRoutes() throws IOException {
        List<DatosRutaAutorizada> rutas = List.of(new DatosRutaAutorizada(1L, 1, "sha", 100, "geo"));
        when(remote.obtenerPorGuia("G1")).thenReturn(rutas);

        assertEquals(rutas, fuente.obtenerPorGuia("G1"));
    }

    @Test
    void anEjbFailureIsReportedForRetry() {
        when(remote.obtenerPorGuia("G1")).thenThrow(new IllegalStateException("WildFly down"));

        assertThrows(IOException.class, () -> fuente.obtenerPorGuia("G1"));
    }

    @Test
    void closingReleasesTheContext() throws Exception {
        fuente.close();

        verify(context).close();
    }
}
