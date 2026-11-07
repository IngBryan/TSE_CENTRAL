package tse.lab.frontofficeciudadanos.service.rutas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService;
import tse.lab.frontofficeciudadanos.service.rutas.PrevisualizacionRutasService.Previsualizacion;
import tse.lab.frontofficeciudadanos.service.rutas.PrevisualizacionRutasService.RutaVista;
import tse.lab.model.Empresa;
import tse.lab.model.Guia;

class RutasGuiaServiceTest {
    private static final String CI = "12345678";
    private static final String ID_PREVIA = "a".repeat(64);
    private static final List<DatosRutaAutorizada> GUARDADAS = List.of(new DatosRutaAutorizada(7L, 1, "sha", 100, "geo"));

    private final AutorizacionResponsable autorizacion = mock(AutorizacionResponsable.class);
    private final GuiaDAO guias = mock(GuiaDAO.class);
    private final RutasAutorizadasService rutas = mock(RutasAutorizadasService.class);
    private final PrevisualizacionRutasService planificador = mock(PrevisualizacionRutasService.class);
    private final GeometriasService geometrias = mock(GeometriasService.class);
    private final AsignacionRutasService asignacion = mock(AsignacionRutasService.class);
    private final GuiasService viajes = mock(GuiasService.class);
    private final RutasGuiaService servicio = new RutasGuiaService();
    private final Empresa empresa = empresa(1L);

    private static Empresa empresa(long nro) {
        Empresa e = new Empresa();
        e.setNroEmpresa(nro);
        return e;
    }

    private static Guia guia(Empresa duenia) {
        return new Guia("G1", "Agro", 20000L, LocalDate.of(2026, 11, 1), "Mercedes", "Dolores", duenia);
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }

    @BeforeEach
    void setUp() throws Exception {
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "guias", guias);
        set(servicio, "rutas", rutas);
        set(servicio, "planificador", planificador);
        set(servicio, "geometrias", geometrias);
        set(servicio, "asignacion", asignacion);
        set(servicio, "viajes", viajes);
        when(autorizacion.empresaDelResponsable(CI, true, "c")).thenReturn(empresa);
        when(guias.buscarPorId("G1")).thenReturn(guia(empresa));
        when(rutas.obtenerPorGuia("G1")).thenReturn(GUARDADAS);
        when(planificador.calcular(1L, 2L)).thenReturn(new Previsualizacion(1L, 2L, ID_PREVIA,
                List.of(new RutaVista(1, "sha", 100, 60, "geo"))));
    }

    @Test
    void returnsTheRoutesOfItsOwnGuide() throws Exception {
        assertSame(GUARDADAS, servicio.consultar(CI, "G1", "c"));
    }

    @Test
    void aGuideOfAnotherCompanyDoesNotExist() {
        when(guias.buscarPorId("G2")).thenReturn(guia(empresa(2L)));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar(CI, "G2", "c"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar(CI, "G3", "c"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar(CI, null, "c"));
    }

    @Test
    void aGuideWithoutCompanyDoesNotExist() {
        when(guias.buscarPorId("G2")).thenReturn(guia(null));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar(CI, "G2", "c"));
    }

    @Test
    void confirmingStoresTheRoutesOfThePreview() throws Exception {
        when(geometrias.obtenerOCrear("geo", 100)).thenReturn(7L);

        assertSame(GUARDADAS, servicio.confirmar(CI, "G1", 1L, 2L, ID_PREVIA, "c"));

        verify(asignacion).asociar(CI, "G1", 1L, 2L, List.of(7L), "c");
    }

    @Test
    void aPreviewIsRequired() {
        for (String id : new String[] { null, "corto" }) {
            RutasException e = assertThrows(RutasException.class,
                    () -> servicio.confirmar(CI, "G1", 1L, 2L, id, "c"));
            assertEquals(400, e.status());
            assertEquals("previsualizacion_requerida", e.codigo());
        }
    }

    @Test
    void anOutdatedPreviewIsAConflict() {
        RutasException e = assertThrows(RutasException.class,
                () -> servicio.confirmar(CI, "G1", 1L, 2L, "b".repeat(64), "c"));

        assertEquals(409, e.status());
        assertEquals("rutas_actualizadas", e.codigo());
        verify(geometrias, never()).obtenerOCrear(anyString(), anyDouble());
    }

    @Test
    void ifAnotherTransactionInsertedTheGeometryItUsesThatOne() throws Exception {
        when(geometrias.obtenerOCrear("geo", 100)).thenThrow(new IllegalStateException("unique"));
        when(geometrias.buscarId("sha")).thenReturn(9L);

        servicio.confirmar(CI, "G1", 1L, 2L, ID_PREVIA, "c");

        verify(asignacion).asociar(CI, "G1", 1L, 2L, List.of(9L), "c");
    }

    @Test
    void aFailureThatLeftNoGeometryIsNotHidden() {
        IllegalStateException fallo = new IllegalStateException("base caida");
        when(geometrias.obtenerOCrear("geo", 100)).thenThrow(fallo);
        when(geometrias.buscarId("sha")).thenReturn(null);

        assertSame(fallo, assertThrows(IllegalStateException.class,
                () -> servicio.confirmar(CI, "G1", 1L, 2L, ID_PREVIA, "c")));
    }

    @Test
    void assigningATripChecksTheGuideBeforeCallingTheRouter() throws Exception {
        when(geometrias.obtenerOCrear("geo", 100)).thenReturn(7L);

        servicio.asignarViaje(CI, "G1", "SBC1234", 5L, 1L, 2L, ID_PREVIA, "c");

        verify(viajes).opcionesAsignacion(CI, "G1", "c");
        verify(viajes).asignarConRutas(CI, "G1", "SBC1234", 5L, 1L, 2L, List.of(7L), "c");
    }

    @Test
    void assigningWithoutAPreviewChangesNothing() throws Exception {
        assertThrows(RutasException.class, () -> servicio.asignarViaje(CI, "G1", "SBC1234", 5L, 1L, 2L, null, "c"));

        verify(viajes, never()).asignarConRutas(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
