package tse.lab.backofficeautoridadesadministradores.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

class CorrelacionFilterTest {

    private final CorrelacionFilter filtro = new CorrelacionFilter();
    private final Logger logger = Logger.getLogger(CorrelacionFilter.class.getName());
    private final List<LogRecord> registros = new ArrayList<>();
    private final Handler captura = new Handler() {
        @Override
        public void publish(LogRecord registro) {
            registros.add(registro);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private ContainerRequestContext req;
    private ContainerResponseContext res;
    private SecurityContext seguridad;
    private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

    @BeforeEach
    void preparar() {
        logger.addHandler(captura);

        // Las propiedades del pedido se guardan de verdad: el filtro de respuesta lee lo que dejo el de pedido
        Map<String, Object> propiedades = new HashMap<>();
        req = mock(ContainerRequestContext.class);
        doAnswer(i -> propiedades.put(i.getArgument(0), i.getArgument(1))).when(req)
                .setProperty(anyString(), any());
        when(req.getProperty(anyString())).thenAnswer(i -> propiedades.get(i.<String>getArgument(0)));

        seguridad = mock(SecurityContext.class);
        when(req.getSecurityContext()).thenReturn(seguridad);
        when(req.getMethod()).thenReturn("GET");
        UriInfo uri = mock(UriInfo.class);
        when(uri.getPath()).thenReturn("/nodos");
        when(req.getUriInfo()).thenReturn(uri);

        res = mock(ContainerResponseContext.class);
        when(res.getHeaders()).thenReturn(headers);
        when(res.getStatus()).thenReturn(200);
    }

    @AfterEach
    void limpiar() {
        logger.removeHandler(captura);
    }

    @Test
    void usaElIdDelFrontLoDevuelveYDejaElLogDeAcceso() {
        when(req.getHeaderString(CorrelacionFilter.CORRELATION_ID)).thenReturn("abc-123");
        when(seguridad.getUserPrincipal()).thenReturn(() -> "99999999");

        filtro.filter(req);
        filtro.filter(req, res);

        assertEquals("abc-123", headers.getFirst(CorrelacionFilter.CORRELATION_ID));
        LogRecord registro = registros.getLast();
        assertEquals(Level.INFO, registro.getLevel());
        String log = registro.getMessage();
        assertTrue(log.contains("\"componente\":\"backoffice\",\"evento\":\"http.request\""), log);
        assertTrue(log.contains("\"correlationId\":\"abc-123\",\"usuario\":\"99999999\",\"responseCode\":200"), log);
        assertTrue(log.contains("\"datos\":{\"metodo\":\"GET\",\"ruta\":\"/nodos\"}"), log);
    }

    @Test
    void generaUnIdSiElFrontNoManda() {
        filtro.filter(req);
        filtro.filter(req, res);

        String id = (String) headers.getFirst(CorrelacionFilter.CORRELATION_ID);
        assertEquals(id, UUID.fromString(id).toString());
        assertFalse(registros.getLast().getMessage().contains("usuario"));
    }

    @Test
    void noAceptaUnIdQuePodriaFalsearElLog() {
        when(req.getHeaderString(CorrelacionFilter.CORRELATION_ID)).thenReturn("x\n{\"evento\":\"falso\"}");

        filtro.filter(req);
        filtro.filter(req, res);

        String id = (String) headers.getFirst(CorrelacionFilter.CORRELATION_ID);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unErrorDelServidorSeLogueaComoSevere() {
        when(res.getStatus()).thenReturn(500);

        filtro.filter(req);
        filtro.filter(req, res);

        assertEquals(Level.SEVERE, registros.getLast().getLevel());
        assertTrue(registros.getLast().getMessage().contains("\"responseCode\":500"));
    }

    @Test
    void sinElFiltroDePedidoNoDevuelveIdNiLoguea() {
        filtro.filter(req, res);

        assertFalse(headers.containsKey(CorrelacionFilter.CORRELATION_ID));
        assertTrue(registros.isEmpty());
    }
}
