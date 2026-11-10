package tse.lab.backofficeautoridadesadministradores.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

import tse.lab.backofficeautoridadesadministradores.rest.seguridad.AutenticacionHttpMechanism;

class CambioContrasenaPendienteFilterTest {

    private final CambioContrasenaPendienteFilter filtro = new CambioContrasenaPendienteFilter();

    private ContainerRequestContext pedido;
    private SecurityContext seguridad;
    private UriInfo uri;

    @BeforeEach
    void preparar() {
        pedido = mock(ContainerRequestContext.class);
        seguridad = mock(SecurityContext.class);
        uri = mock(UriInfo.class);
        when(pedido.getSecurityContext()).thenReturn(seguridad);
        when(pedido.getUriInfo()).thenReturn(uri);
    }

    private void logueado(boolean pendiente, String ruta) {
        when(seguridad.getUserPrincipal()).thenReturn(() -> "87654321");
        when(seguridad.isUserInRole("AUTORIDAD")).thenReturn(true);
        when(seguridad.isUserInRole(AutenticacionHttpMechanism.CAMBIO_CONTRASENA_PENDIENTE)).thenReturn(pendiente);
        when(uri.getPath()).thenReturn(ruta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports", "reports", "/usuarios", "/usuarios/12345678/rol", "/auth/me/otra", "/nodos/"})
    void conElCambioPendienteBloqueaElRestoDeLaApi(String ruta) {
        logueado(true, ruta);

        filtro.filter(pedido);

        ArgumentCaptor<Response> respuesta = ArgumentCaptor.forClass(Response.class);
        verify(pedido).abortWith(respuesta.capture());
        assertEquals(403, respuesta.getValue().getStatus());
        assertEquals("cambio_contrasena_requerido", ((Map<?, ?>) respuesta.getValue().getEntity()).get("error"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/me", "auth/contrasena", "/auth/contrasena/", "/auth/logout", "/auth/login"})
    void conElCambioPendienteDejaVerLaIdentidadCambiarlaYSalir(String ruta) {
        logueado(true, ruta);

        filtro.filter(pedido);

        verify(pedido, never()).abortWith(any());
    }

    @Test
    void sinElCambioPendienteNoBloquea() {
        logueado(false, "/usuarios");

        filtro.filter(pedido);

        verify(pedido, never()).abortWith(any());
    }

    // Sin autenticar decide cada recurso (401)
    @Test
    void sinAutenticarNoBloquea() {
        when(seguridad.getUserPrincipal()).thenReturn(null);
        when(uri.getPath()).thenReturn("/usuarios");

        filtro.filter(pedido);

        verify(pedido, never()).abortWith(any());
    }
}
