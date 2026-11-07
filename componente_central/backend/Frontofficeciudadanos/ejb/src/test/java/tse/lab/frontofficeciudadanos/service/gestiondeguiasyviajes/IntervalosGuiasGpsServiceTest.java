package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.data.EventoViajeDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;
import tse.lab.model.TipoEventoViaje;
import tse.lab.model.Vehiculo;

class IntervalosGuiasGpsServiceTest {
    private static final Instant T = Instant.parse("2026-10-10T12:00:00Z");
    private final GuiaDAO guias = mock(GuiaDAO.class);
    private final EventoViajeDAO eventos = mock(EventoViajeDAO.class);

    private static Guia guia(String id, EstadoGuia estado) throws Exception {
        Empresa empresa = new Empresa(1L, "Empresa", "Razon", "Direccion", null);
        Vehiculo v = new Vehiculo("SBC1234", "Scania", "R", 9000, 18000, empresa);
        Guia g = new Guia(id, "Forestal", 1000, LocalDate.of(2026, 10, 10), "A", "B", empresa);
        g.setVehiculo(v);
        g.cambiarEstado(estado);
        Field f = Guia.class.getDeclaredField("fechaAsignacion");
        f.setAccessible(true);
        f.set(g, T);
        return g;
    }

    private IntervalosGuiasGpsService service() throws Exception {
        IntervalosGuiasGpsService s = new IntervalosGuiasGpsService();
        for (var entry : List.of(new Object[]{"guias", guias}, new Object[]{"eventos", eventos})) {
            Field f = IntervalosGuiasGpsService.class.getDeclaredField((String) entry[0]);
            f.setAccessible(true);
            f.set(s, entry[1]);
        }
        return s;
    }

    @Test
    void incluyeFinalizadaConFinAplicadoYNoTomaEventosConflictivos() throws Exception {
        Guia terminada = guia("G1", EstadoGuia.FINALIZADA);
        Guia sinFin = guia("G2", EstadoGuia.FINALIZADA);
        when(guias.buscarCandidatasGps("SBC1234", T.plusSeconds(200)))
                .thenReturn(List.of(terminada, sinFin));
        // DAO ya filtra resultado APLICADO y excluye CONFLICTO/SIN_EFECTO.
        when(eventos.buscarLimitesAplicados(List.of(terminada, sinFin)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"G1", TipoEventoViaje.INICIO, T.plusSeconds(10)},
                        new Object[]{"G1", TipoEventoViaje.FIN, T.plusSeconds(100)}));
        List<IntervaloGuiaGps> intervalos = service().buscar("SBC1234", T, T.plusSeconds(200));
        assertEquals(2, intervalos.size());
        assertEquals("G1", intervalos.getFirst().getIdGuia());
        assertEquals(T.plusSeconds(10), intervalos.getFirst().getInicioAplicado());
        assertEquals(T.plusSeconds(100), intervalos.getFirst().getFinAplicado());
        assertEquals("G2", intervalos.get(1).getIdGuia());
        assertEquals(null, intervalos.get(1).getFinAplicado());
    }

    @Test
    void unFinAnteriorAlRangoNoSeIncluye() throws Exception {
        Guia terminada = guia("G1", EstadoGuia.FINALIZADA);
        when(guias.buscarCandidatasGps("SBC1234", T.plusSeconds(400)))
                .thenReturn(List.of(terminada));
        when(eventos.buscarLimitesAplicados(List.of(terminada)))
                .thenReturn(List.<Object[]>of(new Object[]{"G1", TipoEventoViaje.FIN, T.plusSeconds(100)}));
        assertEquals(List.of(), service().buscar("SBC1234", T.plusSeconds(200), T.plusSeconds(400)));
    }

    @Test
    void entradaIncorrectaSeRechaza() throws Exception {
        assertEquals(List.of(), service().buscar(" ", T, T.plusSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> service().buscar("SBC1234", T, T));
    }
}
