package tse.lab.fiscalizacionautomatica.ruleengine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ejb.EJBException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import javax.naming.Context;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosPesaje;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.PesajesRemote;

class RemoteWeighingDAOTest {

    private static final Instant T1 = Instant.parse("2026-10-13T10:00:00Z");
    private static final Weighing WEIGHING = new Weighing(3, 4, "SBC1234", T1, 18_450);
    private static final DatosPesaje DATOS = new DatosPesaje(3, 4, "SBC1234", T1, 18_450);

    private final Context context = mock(Context.class);
    private final PesajesRemote remote = mock(PesajesRemote.class);
    private final RemoteWeighingDAO dao = new RemoteWeighingDAO(context, remote);

    @Test
    void weighingGoesToTheEjbAndComesBackTheSame() {
        assertEquals(DATOS, RemoteWeighingDAO.toDatos(WEIGHING));
        assertEquals(WEIGHING, RemoteWeighingDAO.toWeighing(DATOS));
    }

    @Test
    void savesListsAndMarksThroughTheEjb() throws IOException {
        when(remote.guardarPesajes(List.of(DATOS))).thenReturn(1);
        when(remote.listarPendientes(100)).thenReturn(List.of(DATOS));

        assertEquals(1, dao.save(List.of(WEIGHING)));
        assertEquals(List.of(WEIGHING), dao.findPending(100));
        dao.markDetected(WEIGHING);

        verify(remote).marcarDetectado("SBC1234", T1);
    }

    @Test
    void aFailedRemoteCallIsAnIOException() {
        when(remote.guardarPesajes(any())).thenThrow(new EJBException("WildFly down"));
        when(remote.listarPendientes(anyInt())).thenThrow(new EJBException("WildFly down"));
        doThrow(new EJBException("WildFly down")).when(remote).marcarDetectado(any(), any());

        assertThrows(IOException.class, () -> dao.save(List.of(WEIGHING)));
        assertThrows(IOException.class, () -> dao.findPending(100));
        assertThrows(IOException.class, () -> dao.markDetected(WEIGHING));
    }

    @Test
    void closingClosesTheContext() throws Exception {
        dao.close();

        verify(context).close();
    }
    @Test void usaLaRevisionQueYaHabiaFijadoElServidor() throws IOException {
        when(remote.fijarRevisionReglas("SBC1234",T1,5)).thenReturn(2L);
        assertEquals(2,dao.fijarRevisionReglas(WEIGHING,5));
        when(remote.fijarRevisionReglas("SBC1234",T1,5)).thenThrow(new EJBException("sin servidor"));
        assertThrows(IOException.class,()->dao.fijarRevisionReglas(WEIGHING,5));
    }

}
