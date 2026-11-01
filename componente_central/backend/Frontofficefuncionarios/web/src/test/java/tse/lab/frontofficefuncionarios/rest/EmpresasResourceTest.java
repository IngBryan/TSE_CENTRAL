package tse.lab.frontofficefuncionarios.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficefuncionarios.service.ConflictoException;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AltaEmpresa;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AsociacionUsuario;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.CambiosEmpresa;

// CU Gestionar empresas de transporte: cada endpoint delega en el servicio con el correlation id del
// filtro, y las excepciones de negocio se traducen a codigos HTTP.
class EmpresasResourceTest {

    private static final String CI = "33333333";
    private static final String CORRELATION = "corr-1";

    private final EmpresasResource recurso = new EmpresasResource();
    private final GestionEmpresasService empresas = mock(GestionEmpresasService.class);
    private final HttpServletRequest req = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() throws Exception {
        Field campo = EmpresasResource.class.getDeclaredField("empresas");
        campo.setAccessible(true);
        campo.set(recurso, empresas);
        when(req.getAttribute(ApiKeyFilter.CORRELATION_ID)).thenReturn(CORRELATION);
    }

    @Test
    void cadaEndpointDelegaEnElServicioConElCorrelationId() throws Exception {
        AltaEmpresa alta = new AltaEmpresa(3L, "A", "B", "C", null, "12345678", null);
        CambiosEmpresa cambios = new CambiosEmpresa("A", "B", "C", true, 0L, null);
        AsociacionUsuario asociacion = new AsociacionUsuario("12345678");

        assertEquals(200, recurso.listar(CI, req).getStatus());
        assertEquals(200, recurso.usuariosDisponibles(CI, "123", req).getStatus());
        assertEquals(200, recurso.alta(CI, alta, req).getStatus());
        assertEquals(200, recurso.detalle(CI, 3L, req).getStatus());
        assertEquals(200, recurso.modificar(CI, 3L, cambios, req).getStatus());
        assertEquals(200, recurso.asociarUsuario(CI, 3L, asociacion, req).getStatus());

        verify(empresas).listar(CI, CORRELATION);
        verify(empresas).usuariosDisponibles(CI, "123", CORRELATION);
        verify(empresas).alta(CI, alta, CORRELATION);
        verify(empresas).detalle(CI, 3L, CORRELATION);
        verify(empresas).modificar(CI, 3L, cambios, CORRELATION);
        verify(empresas).asociarUsuario(CI, 3L, asociacion, CORRELATION);
    }

    @Test
    void losConflictosDelServicioSeDevuelvenComo409() throws Exception {
        AsociacionUsuario asociacion = new AsociacionUsuario("12345678");
        when(empresas.asociarUsuario(CI, 1L, asociacion, CORRELATION))
                .thenThrow(new ConflictoException("EMPRESA_CON_RESPONSABLE", "ya tiene"));

        Response r = recurso.asociarUsuario(CI, 1L, asociacion, req);

        assertEquals(409, r.getStatus());
        assertEquals("EMPRESA_CON_RESPONSABLE", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void laRespuestaLlevaLoQueDevuelveElServicio() {
        Object resultado = new Object();

        assertSame(resultado, EmpresasResource.ejecutar(() -> resultado).getEntity());
    }

    @Test
    void elCorrelationIdEsElQueDejoElFiltro() {
        assertEquals(CORRELATION, EmpresasResource.correlationId(req));
    }

    @Test
    void datosInvalidosEs400ConLosCampos() {
        Response r = EmpresasResource.ejecutar(() -> {
            throw new DatosInvalidosException(Map.of("nombrePublico", "es obligatorio"));
        });

        assertEquals(400, r.getStatus());
        assertEquals(Map.of("error", "datos_invalidos", "campos", Map.of("nombrePublico", "es obligatorio")),
                r.getEntity());
    }

    @Test
    void noAutorizadoEs403() {
        Response r = EmpresasResource.ejecutar(() -> {
            throw new NoAutorizadoException("no es funcionario");
        });

        assertEquals(403, r.getStatus());
        assertEquals("no_autorizado", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void recursoNoEncontradoEs404ConSuCodigo() {
        Response r = EmpresasResource.ejecutar(() -> {
            throw new RecursoNoEncontradoException("EMPRESA_NO_ENCONTRADA", "La empresa no existe");
        });

        assertEquals(404, r.getStatus());
        assertEquals("EMPRESA_NO_ENCONTRADA", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void losErroresInesperadosNoSeOcultan() {
        IllegalArgumentException runtime = new IllegalArgumentException("bug");

        assertSame(runtime, assertThrows(IllegalArgumentException.class, () -> EmpresasResource.ejecutar(() -> {
            throw runtime;
        })));
        assertThrows(IllegalStateException.class, () -> EmpresasResource.ejecutar(() -> {
            throw new Exception("checked");
        }));
    }
}
