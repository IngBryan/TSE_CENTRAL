package tse.lab.frontofficefuncionarios.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AltaEmpresa;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AsociacionUsuario;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.CambiosEmpresa;

// CU Gestionar empresas de transporte: cada endpoint delega en el servicio con el correlation id del
// filtro; la traduccion de excepciones es la de CasosResource.ejecutar (probada en CasosResourceTest).
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
        AltaEmpresa alta = new AltaEmpresa(3L, "A", "B", "C", null);
        CambiosEmpresa cambios = new CambiosEmpresa("A", "B", "C", true);
        AsociacionUsuario asociacion = new AsociacionUsuario("12345678");

        assertEquals(200, recurso.listar(CI, req).getStatus());
        assertEquals(200, recurso.alta(CI, alta, req).getStatus());
        assertEquals(200, recurso.detalle(CI, 3L, req).getStatus());
        assertEquals(200, recurso.modificar(CI, 3L, cambios, req).getStatus());
        assertEquals(200, recurso.asociarUsuario(CI, 3L, asociacion, req).getStatus());

        verify(empresas).listar(CI, CORRELATION);
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
}
