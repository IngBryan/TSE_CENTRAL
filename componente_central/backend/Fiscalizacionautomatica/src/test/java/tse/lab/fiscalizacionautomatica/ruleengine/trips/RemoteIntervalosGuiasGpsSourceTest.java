package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import javax.naming.Context;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervalosGuiasGpsRemote;

class RemoteIntervalosGuiasGpsSourceTest {
    private static final Instant T = Instant.parse("2026-11-01T12:00:00Z");
    private final Context context = mock(Context.class);
    private final IntervalosGuiasGpsRemote remote = mock(IntervalosGuiasGpsRemote.class);
    private final RemoteIntervalosGuiasGpsSource fuente = new RemoteIntervalosGuiasGpsSource(context, remote);

    @Test
    void asksTheEjbForTheIntervals() throws IOException {
        List<IntervaloGuiaGps> intervalos = List.of(new IntervaloGuiaGps("G1", "SBC1234", "EN_CURSO", T, T, null));
        when(remote.buscar("SBC1234", T, T.plusSeconds(1))).thenReturn(intervalos);

        assertEquals(intervalos, fuente.buscar("SBC1234", T, T.plusSeconds(1)));
    }

    @Test
    void anEjbFailureIsReportedForRetry() {
        when(remote.buscar("SBC1234", T, T.plusSeconds(1))).thenThrow(new IllegalStateException("WildFly down"));

        assertThrows(IOException.class, () -> fuente.buscar("SBC1234", T, T.plusSeconds(1)));
    }

    @Test
    void closingReleasesTheContext() throws Exception {
        fuente.close();

        verify(context).close();
    }
}
