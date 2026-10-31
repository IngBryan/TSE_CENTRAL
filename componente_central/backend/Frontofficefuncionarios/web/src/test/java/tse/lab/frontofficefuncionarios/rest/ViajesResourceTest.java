package tse.lab.frontofficefuncionarios.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.MonitoreoViajesService;

// CU Monitorear viajes y pesajes: cada endpoint delega en el servicio con el correlation id del filtro, y
// las excepciones de negocio se traducen a codigos HTTP.
class ViajesResourceTest {

    private static final String CI = "33333333";
    private static final String CORRELATION = "corr-1";

    private final ViajesResource recurso = new ViajesResource();
    private final MonitoreoViajesService viajes = mock(MonitoreoViajesService.class);
    private final HttpServletRequest req = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() throws Exception {
        Field campo = ViajesResource.class.getDeclaredField("viajes");
        campo.setAccessible(true);
        campo.set(recurso, viajes);
        when(req.getAttribute(ApiKeyFilter.CORRELATION_ID)).thenReturn(CORRELATION);
    }

    @Test
    void listadoYDetalleDelegaEnElServicioConElCorrelationId() throws Exception {
        assertEquals(200, recurso.listar(CI, "EN_CURSO", "SBA1234", "1", "2", "10", req).getStatus());
        assertEquals(200, recurso.detalle(CI, "GV-1", req).getStatus());

        verify(viajes).listar(CI, "EN_CURSO", "SBA1234", "1", "2", "10", CORRELATION);
        verify(viajes).detalle(CI, "GV-1", CORRELATION);
    }

    @Test
    void laFotoSeDevuelveComoImagenSegunSuFormato() throws Exception {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, 1 };
        byte[] png = { (byte) 0x89, 'P', 'N', 'G' };
        when(viajes.fotoEvento(CI, "GV-1", "e-1", CORRELATION)).thenReturn(jpeg);
        when(viajes.fotoEvento(CI, "GV-1", "e-2", CORRELATION)).thenReturn(png);

        Response r = recurso.foto(CI, "GV-1", "e-1", req);
        assertEquals(200, r.getStatus());
        assertSame(jpeg, r.getEntity());
        assertEquals("image/jpeg", r.getMediaType().toString());
        assertEquals("image/png", recurso.foto(CI, "GV-1", "e-2", req).getMediaType().toString());
    }

    @Test
    void sinFotoSeResponde404EnJson() throws Exception {
        when(viajes.fotoEvento(CI, "GV-1", "e-1", CORRELATION))
                .thenThrow(new RecursoNoEncontradoException("SIN_FOTO", "no tiene"));

        Response r = recurso.foto(CI, "GV-1", "e-1", req);

        assertEquals(404, r.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, r.getMediaType());
        assertEquals("SIN_FOTO", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void lasExcepcionesDelServicioSeTraducenACodigosHttp() throws Exception {
        when(viajes.listar(CI, "X", null, null, null, null, CORRELATION))
                .thenThrow(new DatosInvalidosException(Map.of("estado", "inválido")));
        when(viajes.detalle(CI, "GV-9", CORRELATION))
                .thenThrow(new RecursoNoEncontradoException("VIAJE_NO_ENCONTRADO", "no existe"));
        when(viajes.detalle("1", "GV-1", CORRELATION)).thenThrow(new NoAutorizadoException("no es funcionario"));

        assertEquals(400, recurso.listar(CI, "X", null, null, null, null, req).getStatus());
        Response noEncontrado = recurso.detalle(CI, "GV-9", req);
        assertEquals(404, noEncontrado.getStatus());
        assertEquals("VIAJE_NO_ENCONTRADO", ((Map<?, ?>) noEncontrado.getEntity()).get("error"));
        assertEquals(403, recurso.detalle("1", "GV-1", req).getStatus());
    }
}
