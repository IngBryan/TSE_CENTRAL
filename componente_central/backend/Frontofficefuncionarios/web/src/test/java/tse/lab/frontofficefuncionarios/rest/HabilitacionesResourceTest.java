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
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService;

// CU Fiscalizacion de los permisos y estado de los vehiculos: cada endpoint delega en el servicio con el
// correlation id del filtro, y las excepciones de negocio se traducen a codigos HTTP.
class HabilitacionesResourceTest {

    private static final String CI = "33333333";
    private static final String CORRELATION = "corr-1";

    private final HabilitacionesResource recurso = new HabilitacionesResource();
    private final FiscalizacionHabilitacionesService habilitaciones = mock(FiscalizacionHabilitacionesService.class);
    private final HttpServletRequest req = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() throws Exception {
        Field campo = HabilitacionesResource.class.getDeclaredField("habilitaciones");
        campo.setAccessible(true);
        campo.set(recurso, habilitaciones);
        when(req.getAttribute(ApiKeyFilter.CORRELATION_ID)).thenReturn(CORRELATION);
    }

    @Test
    void cadaEndpointDelegaEnElServicioConElCorrelationId() throws Exception {
        assertEquals(200, recurso.listar(CI, 3L, req).getStatus());
        assertEquals(200, recurso.detalle(CI, 3L, "SBA1234", req).getStatus());

        verify(habilitaciones).vehiculosDeEmpresa(CI, 3L, CORRELATION);
        verify(habilitaciones).vehiculo(CI, 3L, "SBA1234", CORRELATION);
    }

    @Test
    void quienNoEsFuncionarioRecibe403() throws Exception {
        when(habilitaciones.vehiculosDeEmpresa(CI, 3L, CORRELATION))
                .thenThrow(new NoAutorizadoException("no es funcionario"));

        assertEquals(403, recurso.listar(CI, 3L, req).getStatus());
    }

    @Test
    void unVehiculoDeOtraEmpresaEs404ConSuCodigo() throws Exception {
        when(habilitaciones.vehiculo(CI, 3L, "SBA1234", CORRELATION))
                .thenThrow(new RecursoNoEncontradoException("VEHICULO_NO_ENCONTRADO", "no es de la empresa"));

        Response r = recurso.detalle(CI, 3L, "SBA1234", req);

        assertEquals(404, r.getStatus());
        assertEquals("VEHICULO_NO_ENCONTRADO", ((Map<?, ?>) r.getEntity()).get("error"));
    }
}
