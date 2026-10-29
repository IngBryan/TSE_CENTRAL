package tse.lab.frontofficefuncionarios.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficefuncionarios.rest.IngresoResource.IngresoRequest;
import tse.lab.frontofficefuncionarios.rest.IngresoResource.IngresoResponse;
import tse.lab.frontofficefuncionarios.service.autenticacion.IngresoFuncionarioService;
import tse.lab.frontofficefuncionarios.service.autenticacion.IngresoFuncionarioService.Resultado;
import tse.lab.frontofficefuncionarios.service.autenticacion.IngresoFuncionarioService.ResultadoIngreso;

class IngresoResourceTest {

    private static final String CI = "45678901";
    private static final String EMAIL = "ana@mtop.gub.uy";
    private static final String CORRELATION = "corr-1";

    private final IngresoResource resource = new IngresoResource();
    private final IngresoFuncionarioService service = mock(IngresoFuncionarioService.class);
    private final HttpServletRequest req = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() throws Exception {
        Field field = IngresoResource.class.getDeclaredField("ingreso");
        field.setAccessible(true);
        field.set(resource, service);
        when(req.getAttribute(ApiKeyFilter.CORRELATION_ID)).thenReturn(CORRELATION);
    }

    @Test
    void gubUyLoginReturnsTheServiceResult() {
        when(service.ingresarConGubUy(CI, EMAIL, CORRELATION)).thenReturn(
                new ResultadoIngreso(Resultado.VALIDADO, true, CI, EMAIL, "Ana Maria", "Perez Gomez"));

        Response r = resource.ingresoGubUy(new IngresoRequest(CI, EMAIL), req);

        assertEquals(200, r.getStatus());
        assertEquals(new IngresoResponse("VALIDADO", true, CI, EMAIL, "Ana Maria", "Perez Gomez"), r.getEntity());
    }

    @Test
    void gubUyLoginRejectsAMissingOrMalformedCedula() {
        assertEquals(400, resource.ingresoGubUy(null, req).getStatus());
        assertEquals(400, resource.ingresoGubUy(new IngresoRequest(null, EMAIL), req).getStatus());
        assertEquals(400, resource.ingresoGubUy(new IngresoRequest("12345", EMAIL), req).getStatus());
        assertEquals(400, resource.ingresoGubUy(new IngresoRequest("123456789", EMAIL), req).getStatus());
        assertEquals(400, resource.ingresoGubUy(new IngresoRequest("1234a678", EMAIL), req).getStatus());
        verifyNoInteractions(service);
    }

    @Test
    void testingLoginIsNotFoundWhenDisabled() {
        resource.testingHabilitado = false;

        assertEquals(404, resource.ingresoTesting(new IngresoRequest(CI, null), req).getStatus());
        verifyNoInteractions(service);
    }

    @Test
    void testingLoginReturnsTheServiceResultWhenEnabled() {
        resource.testingHabilitado = true;
        when(service.ingresarParaTesting(CI, CORRELATION)).thenReturn(
                new ResultadoIngreso(Resultado.NO_DISPONIBLE, true, CI, "viejo@mtop.gub.uy", null, null));

        Response r = resource.ingresoTesting(new IngresoRequest(CI, null), req);

        assertEquals(200, r.getStatus());
        assertEquals(new IngresoResponse("NO_DISPONIBLE", true, CI, "viejo@mtop.gub.uy", null, null),
                r.getEntity());
    }

    @Test
    void testingLoginRejectsAMalformedCedulaWhenEnabled() {
        resource.testingHabilitado = true;

        assertEquals(400, resource.ingresoTesting(null, req).getStatus());
        assertEquals(400, resource.ingresoTesting(new IngresoRequest("abc", null), req).getStatus());
        verifyNoInteractions(service);
    }
}
