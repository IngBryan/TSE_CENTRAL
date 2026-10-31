
package tse.lab.backofficeautoridadesadministradores.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;

import tse.lab.backofficeautoridadesadministradores.rest.seguridad.BackofficeToken;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.IdentidadBackoffice;

class AutenticacionResourceTest {

    private static final byte[] SECRET =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private AutenticacionResource recurso;

    private jakarta.security.enterprise.SecurityContext
            seguridadJakarta;

    private jakarta.ws.rs.core.SecurityContext
            seguridadRest;

    private HttpServletRequest solicitud;
    private HttpServletResponse respuestaHttp;

    @BeforeEach
    void preparar() throws Exception {
        seguridadJakarta = mock(
                jakarta.security.enterprise.SecurityContext.class);

        recurso = new AutenticacionResource(seguridadJakarta);

        seguridadRest = mock(
                jakarta.ws.rs.core.SecurityContext.class);

        solicitud = mock(HttpServletRequest.class);
        respuestaHttp = mock(HttpServletResponse.class);

        inyectar("seguridadRest", seguridadRest);
        inyectar("solicitud", solicitud);
        inyectar("respuesta", respuestaHttp);
        inyectar("tokenSecret", SECRET);
    }

    private void loggedInAs(String ci, String rol) {
        when(seguridadJakarta.authenticate(
                eq(solicitud),
                eq(respuestaHttp),
                any(AuthenticationParameters.class)
        )).thenReturn(AuthenticationStatus.SUCCESS);
        when(seguridadJakarta.getCallerPrincipal()).thenReturn(() -> ci);
        when(seguridadJakarta.isCallerInRole(rol)).thenReturn(true);
    }

    private void inyectar(String nombre, Object valor)
            throws Exception {
        Field campo = AutenticacionResource.class
                .getDeclaredField(nombre);

        campo.setAccessible(true);
        campo.set(recurso, valor);
    }

    @Test
    void validLoginReturnsATokenWithTheIdentityAndRole() {
        loggedInAs("99999999", "ADMINISTRADOR");

        Response respuesta = recurso.login(
                new AutenticacionResource.Credenciales(
                        " 99999999 ",
                        "ClaveValida"
                ));

        assertEquals(200, respuesta.getStatus());

        Map<?, ?> cuerpo =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals(
                new IdentidadBackoffice("99999999", "ADMINISTRADOR"),
                BackofficeToken.verify((String) cuerpo.get("token"), SECRET, Instant.now()).orElseThrow());
        assertEquals(BackofficeToken.LIFETIME_S, cuerpo.get("expiraEn"));
        // No session: nothing is stored in WildFly
        verify(solicitud, never()).getSession(anyBoolean());

        ArgumentCaptor<AuthenticationParameters> captor =
                ArgumentCaptor.forClass(
                        AuthenticationParameters.class);

        verify(seguridadJakarta).authenticate(
                eq(solicitud),
                eq(respuestaHttp),
                captor.capture()
        );

        UsernamePasswordCredential credencial =
                assertInstanceOf(
                        UsernamePasswordCredential.class,
                        captor.getValue().getCredential()
                );

        assertEquals("99999999",
                credencial.getCaller());

        assertEquals("ClaveValida",
                credencial.getPasswordAsString());
    }

    @Test
    void theTokenOfAnAuthorityCarriesItsRole() {
        loggedInAs("87654321", "AUTORIDAD");

        Response respuesta = recurso.login(
                new AutenticacionResource.Credenciales(
                        "87654321", "ClaveValida"));

        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getEntity();
        assertEquals(
                "AUTORIDAD",
                BackofficeToken.verify((String) cuerpo.get("token"), SECRET, Instant.now()).orElseThrow().rol());
    }

    @Test
    void withoutTheTokenSecretTheLoginIsNotAvailable() throws Exception {
        inyectar("tokenSecret", new byte[0]);

        Response respuesta = recurso.login(
                new AutenticacionResource.Credenciales(
                        "99999999", "ClaveValida"));

        assertEquals(503, respuesta.getStatus());
        assertEquals("login_no_disponible", ((Map<?, ?>) respuesta.getEntity()).get("error"));
        verifyNoInteractions(seguridadJakarta);
    }

    @Test
    void loginRechazaCredencialesIncompletas() {
        var invalidas = new AutenticacionResource.Credenciales[] {
                null,
                new AutenticacionResource.Credenciales(
                        null, "clave"),
                new AutenticacionResource.Credenciales(
                        " ", "clave"),
                new AutenticacionResource.Credenciales(
                        "99999999", null),
                new AutenticacionResource.Credenciales(
                        "99999999", "")
        };

        for (var datos : invalidas) {
            Response respuesta = recurso.login(datos);

            assertEquals(401, respuesta.getStatus());

            Map<?, ?> error =
                    (Map<?, ?>) respuesta.getEntity();

            assertEquals("credenciales_invalidas",
                    error.get("error"));
        }

        verifyNoInteractions(seguridadJakarta);
    }

    @Test
    void loginCredencialesIncorrectasDevuelve401() {
        when(seguridadJakarta.authenticate(
                eq(solicitud),
                eq(respuestaHttp),
                any(AuthenticationParameters.class)
        )).thenReturn(AuthenticationStatus.SEND_FAILURE);

        Response respuesta = recurso.login(
                new AutenticacionResource.Credenciales(
                        "99999999", "Incorrecta"));

        assertEquals(401, respuesta.getStatus());

        Map<?, ?> error =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("credenciales_invalidas",
                error.get("error"));
    }

    @Test
    void consultarSinAutenticacionDevuelve401() {
        when(seguridadRest.getUserPrincipal())
                .thenReturn(null);

        Response respuesta = recurso.usuarioActual();

        assertEquals(401, respuesta.getStatus());

        Map<?, ?> error =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("no_autenticado",
                error.get("error"));
    }

    @Test
    void consultarAdministradorDevuelveIdentidad() {
        Principal principal = () -> "99999999";

        when(seguridadRest.getUserPrincipal())
                .thenReturn(principal);

        when(seguridadRest.isUserInRole("ADMINISTRADOR"))
                .thenReturn(true);

        Response respuesta = recurso.usuarioActual();

        assertEquals(200, respuesta.getStatus());

        Map<?, ?> identidad =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("99999999",
                identidad.get("ci"));

        assertEquals("ADMINISTRADOR",
                identidad.get("rol"));
    }

    @Test
    void consultarAutoridadDevuelveIdentidad() {
        Principal principal = () -> "87654321";

        when(seguridadRest.getUserPrincipal())
                .thenReturn(principal);

        when(seguridadRest.isUserInRole("ADMINISTRADOR"))
                .thenReturn(false);

        when(seguridadRest.isUserInRole("AUTORIDAD"))
                .thenReturn(true);

        Response respuesta = recurso.usuarioActual();

        assertEquals(200, respuesta.getStatus());

        Map<?, ?> identidad =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("87654321",
                identidad.get("ci"));

        assertEquals("AUTORIDAD",
                identidad.get("rol"));
    }

    @Test
    void consultarUsuarioSinRolDevuelve403() {
        Principal principal = () -> "11111111";

        when(seguridadRest.getUserPrincipal())
                .thenReturn(principal);

        Response respuesta = recurso.usuarioActual();

        assertEquals(403, respuesta.getStatus());

        Map<?, ?> error =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("no_autorizado",
                error.get("error"));
    }

    @Test
    void logoutReturns204WithoutTouchingASession() throws Exception {
        Response respuesta = recurso.logout();

        assertEquals(204, respuesta.getStatus());

        verify(solicitud).logout();
        verify(solicitud, never()).getSession(anyBoolean());
    }
}
