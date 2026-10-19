package tse.lab.frontofficefuncionarios.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

// La clave de los tests (FUNCIONARIOS_API_KEY = clave-de-test) la define surefire en el pom.
class ApiKeyFilterTest {

    private final ApiKeyFilter filtro = new ApiKeyFilter();
    private final ContainerRequestContext req = mock(ContainerRequestContext.class);

    @Test
    void conLaClaveCorrectaDejaPasarYConservaElCorrelationId() {
        when(req.getHeaderString("X-Api-Key")).thenReturn("clave-de-test");
        when(req.getHeaderString(ApiKeyFilter.CORRELATION_ID)).thenReturn("corr-1");

        filtro.filter(req);

        verify(req, never()).abortWith(any());
        verify(req).setProperty(ApiKeyFilter.CORRELATION_ID, "corr-1");
    }

    @Test
    void sinClaveOConOtraClaveResponde401() {
        when(req.getHeaderString("X-Api-Key")).thenReturn(null, "otra-clave");

        filtro.filter(req);
        filtro.filter(req);

        ArgumentCaptor<Response> respuesta = ArgumentCaptor.forClass(Response.class);
        verify(req, org.mockito.Mockito.times(2)).abortWith(respuesta.capture());
        assertEquals(401, respuesta.getValue().getStatus());
    }

    @Test
    void unCorrelationIdInvalidoSeReemplazaPorUnoNuevo() {
        when(req.getHeaderString("X-Api-Key")).thenReturn("clave-de-test");
        when(req.getHeaderString(ApiKeyFilter.CORRELATION_ID)).thenReturn("con espacios y ;");

        filtro.filter(req);

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(req).setProperty(eq(ApiKeyFilter.CORRELATION_ID), id.capture());
        assertNotEquals("con espacios y ;", id.getValue());
        assertEquals(36, id.getValue().length());
    }

    @Test
    void laRespuestaDevuelveElCorrelationIdDelPedido() {
        ContainerResponseContext res = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(res.getHeaders()).thenReturn(headers);
        when(req.getProperty(ApiKeyFilter.CORRELATION_ID)).thenReturn("corr-1", (Object) null);

        filtro.filter(req, res);
        filtro.filter(req, mock(ContainerResponseContext.class));

        assertEquals("corr-1", headers.getFirst(ApiKeyFilter.CORRELATION_ID));
        verify(req, never()).setProperty(anyString(), any());
    }
}
