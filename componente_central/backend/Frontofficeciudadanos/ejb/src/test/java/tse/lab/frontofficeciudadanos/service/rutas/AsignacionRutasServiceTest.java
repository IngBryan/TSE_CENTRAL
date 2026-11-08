package tse.lab.frontofficeciudadanos.service.rutas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.frontofficeciudadanos.data.RutaReferenciaDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;
import tse.lab.model.Localidad;
import tse.lab.model.RutaReferencia;

class AsignacionRutasServiceTest {
    private static final String CI = "12345678";
    private static final Localidad MERCEDES = new Localidad(1L, "Mercedes", "Soriano", -33.25, -58.03);
    private static final Localidad DOLORES = new Localidad(2L, "Dolores", "Soriano", -33.53, -58.21);
    private static final RutaReferencia RUTA = new RutaReferencia(
            "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}", 100);

    private final AutorizacionResponsable autorizacion = mock(AutorizacionResponsable.class);
    private final GuiaDAO guias = mock(GuiaDAO.class);
    private final LocalidadDAO localidades = mock(LocalidadDAO.class);
    private final RutaReferenciaDAO rutas = mock(RutaReferenciaDAO.class);
    private final AsignacionRutasService servicio = new AsignacionRutasService();
    private final Empresa empresa = new Empresa();
    private Guia guia;

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }

    @BeforeEach
    void setUp() throws Exception {
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "guias", guias);
        set(servicio, "localidades", localidades);
        set(servicio, "rutas", rutas);
        empresa.setNroEmpresa(1L);
        guia = new Guia("G1", "Agro", 20000L, LocalDate.of(2026, 11, 1), "Mercedes", "Dolores", empresa);
        when(autorizacion.empresaDelResponsable(CI, true, "c")).thenReturn(empresa);
        when(guias.buscarPorIdParaActualizar("G1")).thenReturn(guia);
        when(localidades.buscarPorId(1L)).thenReturn(MERCEDES);
        when(localidades.buscarPorId(2L)).thenReturn(DOLORES);
        when(rutas.buscarPorId(5L)).thenReturn(RUTA);
    }

    private void conLocalidades() {
        guia.setOrigenLocalidad(MERCEDES);
        guia.setDestinoLocalidad(DOLORES);
    }

    private String codigoAlAsociar(Long origen, Long destino) {
        return assertThrows(RutasException.class,
                () -> servicio.asociar(CI, "G1", origen, destino, List.of(5L), "c")).codigo();
    }

    @Test
    void associatesTheRoutesToTheGuide() throws Exception {
        conLocalidades();

        servicio.asociar(CI, "G1", 1L, 2L, List.of(5L), "c");

        assertEquals(1, guia.getRutasAutorizadas().size());
    }

    @Test
    void anOldGuideIsMatchedByItsUniqueName() throws Exception {
        when(localidades.buscarUnicaPorNombre("Mercedes")).thenReturn(MERCEDES);
        when(localidades.buscarUnicaPorNombre("Dolores")).thenReturn(DOLORES);

        servicio.asociar(CI, "G1", 1L, 2L, List.of(5L), "c");

        assertEquals(1, guia.getRutasAutorizadas().size());
    }

    @Test
    void anOldGuideWithAnAmbiguousNameIsRejected() {
        assertEquals("origen_destino_desactualizado", codigoAlAsociar(1L, 2L));
    }

    @Test
    void anotherCompanyGuideDoesNotExist() {
        Empresa otra = new Empresa();
        otra.setNroEmpresa(2L);
        when(guias.buscarPorIdParaActualizar("G2")).thenReturn(
                new Guia("G2", "Agro", 20000L, LocalDate.of(2026, 11, 1), "Mercedes", "Dolores", otra));

        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.asociar(CI, "G2", 1L, 2L, List.of(5L), "c"));
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.asociar(CI, "G3", 1L, 2L, List.of(5L), "c"));
    }

    @Test
    void localitiesThatAreNotTheOnesOfTheGuideAreRejected() {
        conLocalidades();

        assertEquals("origen_destino_desactualizado", codigoAlAsociar(2L, 2L));
        assertEquals("origen_destino_desactualizado", codigoAlAsociar(1L, 1L));
        assertEquals("origen_destino_desactualizado", codigoAlAsociar(9L, 2L));
        assertEquals("origen_destino_desactualizado", codigoAlAsociar(1L, 9L));
    }

    @Test
    void aGeometryThatIsGoneIsAConflict() {
        conLocalidades();

        RutasException e = assertThrows(RutasException.class,
                () -> servicio.asociar(CI, "G1", 1L, 2L, List.of(6L), "c"));

        assertEquals(409, e.status());
        assertEquals("geometria_no_encontrada", e.codigo());
    }

    @Test
    void aStartedTripKeepsItsRoutes() {
        conLocalidades();
        guia.cambiarEstado(EstadoGuia.EN_CURSO);

        assertEquals("viaje_no_modificable", codigoAlAsociar(1L, 2L));
    }
}
