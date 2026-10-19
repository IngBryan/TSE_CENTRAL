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
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.Resolucion;

// CU Gestionar casos de fiscalizacion: cada endpoint delega en el servicio con el correlation id del
// filtro, y las excepciones de negocio se traducen a codigos HTTP.
class CasosResourceTest {

    private static final String CI = "33333333";
    private static final String CORRELATION = "corr-1";

    private final CasosResource recurso = new CasosResource();
    private final GestionCasosService casos = mock(GestionCasosService.class);
    private final HttpServletRequest req = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() throws Exception {
        Field campo = CasosResource.class.getDeclaredField("casos");
        campo.setAccessible(true);
        campo.set(recurso, casos);
        when(req.getAttribute(ApiKeyFilter.CORRELATION_ID)).thenReturn(CORRELATION);
    }

    @Test
    void cadaEndpointDelegaEnElServicioConElCorrelationId() throws Exception {
        Resolucion body = new Resolucion("CONFIRMAR", "Se confirma");

        assertEquals(200, recurso.bandeja(CI, "EN_DESCARGO", "1", "20", req).getStatus());
        assertEquals(200, recurso.detalle(CI, 4L, req).getStatus());
        assertEquals(200, recurso.evidencia(CI, 4L, req).getStatus());
        assertEquals(200, recurso.descargo(CI, 4L, req).getStatus());
        assertEquals(200, recurso.resolver(CI, 4L, body, req).getStatus());

        verify(casos).bandeja(CI, "EN_DESCARGO", "1", "20", CORRELATION);
        verify(casos).detalle(CI, 4L, CORRELATION);
        verify(casos).evidencia(CI, 4L, CORRELATION);
        verify(casos).descargo(CI, 4L, CORRELATION);
        verify(casos).resolver(CI, 4L, body, CORRELATION);
    }

    @Test
    void laRespuestaLlevaLoQueDevuelveElServicio() {
        Object resultado = new Object();

        assertSame(resultado, CasosResource.ejecutar(() -> resultado).getEntity());
    }

    @Test
    void elCorrelationIdEsElQueDejoElFiltro() {
        assertEquals(CORRELATION, CasosResource.correlationId(req));
    }

    @Test
    void datosInvalidosEs400ConLosCampos() {
        Response r = CasosResource.ejecutar(() -> {
            throw new DatosInvalidosException(Map.of("pagina", "debe ser un numero"));
        });

        assertEquals(400, r.getStatus());
        assertEquals(Map.of("error", "datos_invalidos", "campos", Map.of("pagina", "debe ser un numero")),
                r.getEntity());
    }

    @Test
    void noAutorizadoEs403() {
        Response r = CasosResource.ejecutar(() -> {
            throw new NoAutorizadoException("no es funcionario");
        });

        assertEquals(403, r.getStatus());
        assertEquals("no_autorizado", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void recursoNoEncontradoEs404ConSuCodigo() {
        Response r = CasosResource.ejecutar(() -> {
            throw new RecursoNoEncontradoException("SIN_DESCARGO", "La empresa no presento descargo");
        });

        assertEquals(404, r.getStatus());
        assertEquals("SIN_DESCARGO", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void conflictoEs409ConSuCodigo() {
        Response r = CasosResource.ejecutar(() -> {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "El caso no admite resolucion");
        });

        assertEquals(409, r.getStatus());
        assertEquals("TRANSICION_NO_PERMITIDA", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void losErroresInesperadosNoSeOcultan() {
        IllegalArgumentException runtime = new IllegalArgumentException("bug");

        assertSame(runtime, assertThrows(IllegalArgumentException.class, () -> CasosResource.ejecutar(() -> {
            throw runtime;
        })));
        assertThrows(IllegalStateException.class, () -> CasosResource.ejecutar(() -> {
            throw new Exception("checked");
        }));
    }
}
