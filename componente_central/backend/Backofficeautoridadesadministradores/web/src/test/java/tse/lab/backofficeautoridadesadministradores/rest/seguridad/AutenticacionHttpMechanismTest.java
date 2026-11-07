
package tse.lab.backofficeautoridadesadministradores.rest.seguridad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tse.lab.backofficeautoridadesadministradores.service.autenticacion.AutenticacionBackofficeService;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.IdentidadBackoffice;

class AutenticacionHttpMechanismTest {

    private static final byte[] SECRET =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private AutenticacionHttpMechanism mecanismo;
    private AutenticacionBackofficeService servicio;

    private HttpMessageContext contexto;
    private HttpServletRequest solicitud;
    private HttpServletResponse respuesta;

    @BeforeEach
    void preparar() throws Exception {
        mecanismo = new AutenticacionHttpMechanism();

        servicio = mock(AutenticacionBackofficeService.class);
        contexto = mock(HttpMessageContext.class);
        solicitud = mock(HttpServletRequest.class);
        respuesta = mock(HttpServletResponse.class);

        Field campo = AutenticacionHttpMechanism.class
                .getDeclaredField("servicio");

        campo.setAccessible(true);
        campo.set(mecanismo, servicio);

        Field secreto = AutenticacionHttpMechanism.class
                .getDeclaredField("tokenSecret");

        secreto.setAccessible(true);
        secreto.set(mecanismo, SECRET);
    }

    private void requestWithAuthorization(String header) {
        when(contexto.isAuthenticationRequest()).thenReturn(false);
        when(solicitud.getHeader("Authorization")).thenReturn(header);
        when(contexto.doNothing()).thenReturn(AuthenticationStatus.NOT_DONE);
    }

    private void configurarCredenciales(
            String ci,
            String contrasena) {

        when(contexto.isAuthenticationRequest())
                .thenReturn(true);

        when(contexto.getAuthParameters())
                .thenReturn(
                        AuthenticationParameters.withParams()
                                .credential(
                                        new UsernamePasswordCredential(
                                                ci, contrasena))
                );
    }

    @Test
    void peticionSinAutenticacionNoHaceNada()
            throws Exception {

        when(contexto.isAuthenticationRequest())
                .thenReturn(false);

        when(contexto.doNothing())
                .thenReturn(AuthenticationStatus.NOT_DONE);

        AuthenticationStatus estado =
                mecanismo.validateRequest(
                        solicitud, respuesta, contexto);

        assertEquals(AuthenticationStatus.NOT_DONE, estado);

        verify(contexto).doNothing();
        verifyNoInteractions(servicio);
    }

    @Test
    void credencialesAusentesDevuelvenUnauthorized()
            throws Exception {

        when(contexto.isAuthenticationRequest())
                .thenReturn(true);

        when(contexto.getAuthParameters())
                .thenReturn(AuthenticationParameters.withParams());

        when(contexto.responseUnauthorized())
                .thenReturn(AuthenticationStatus.SEND_FAILURE);

        AuthenticationStatus estado =
                mecanismo.validateRequest(
                        solicitud, respuesta, contexto);

        assertEquals(
                AuthenticationStatus.SEND_FAILURE,
                estado
        );

        verify(contexto).responseUnauthorized();
        verifyNoInteractions(servicio);
    }

    @Test
    void credencialesIncorrectasDevuelvenUnauthorized()
            throws Exception {

        configurarCredenciales("99999999", "Incorrecta");

        when(servicio.autenticar("99999999", "Incorrecta"))
                .thenReturn(Optional.empty());

        when(contexto.responseUnauthorized())
                .thenReturn(AuthenticationStatus.SEND_FAILURE);

        AuthenticationStatus estado =
                mecanismo.validateRequest(
                        solicitud, respuesta, contexto);

        assertEquals(
                AuthenticationStatus.SEND_FAILURE,
                estado
        );

        verify(servicio).autenticar(
                "99999999", "Incorrecta");

        verify(contexto).responseUnauthorized();
    }

    @Test
    void administradorValidoNotificaIdentidadYRol()
            throws Exception {

        configurarCredenciales("99999999", "ClaveValida");

        when(servicio.autenticar("99999999", "ClaveValida"))
                .thenReturn(Optional.of(
                        new IdentidadBackoffice(
                                "99999999",
                                "ADMINISTRADOR"
                        )
                ));

        when(contexto.notifyContainerAboutLogin(
                "99999999",
                Set.of("ADMINISTRADOR")
        )).thenReturn(AuthenticationStatus.SUCCESS);

        AuthenticationStatus estado =
                mecanismo.validateRequest(
                        solicitud, respuesta, contexto);

        assertEquals(AuthenticationStatus.SUCCESS, estado);

        verify(contexto).notifyContainerAboutLogin(
                "99999999",
                Set.of("ADMINISTRADOR")
        );
    }

    @Test
    void autoridadValidaNotificaRolAutoridad()
            throws Exception {

        configurarCredenciales("87654321", "ClaveValida");

        when(servicio.autenticar("87654321", "ClaveValida"))
                .thenReturn(Optional.of(
                        new IdentidadBackoffice(
                                "87654321",
                                "AUTORIDAD"
                        )
                ));

        when(contexto.notifyContainerAboutLogin(
                "87654321",
                Set.of("AUTORIDAD")
        )).thenReturn(AuthenticationStatus.SUCCESS);

        AuthenticationStatus estado =
                mecanismo.validateRequest(
                        solicitud, respuesta, contexto);

        assertEquals(AuthenticationStatus.SUCCESS, estado);

        verify(contexto).notifyContainerAboutLogin(
                "87654321",
                Set.of("AUTORIDAD")
        );
    }

    @Test
    void aValidTokenAuthenticatesTheRequestWithoutCheckingTheCredentialsAgain() throws Exception {
        String token = BackofficeToken.issue(
                new IdentidadBackoffice("99999999", "ADMINISTRADOR"), SECRET, Instant.now());
        requestWithAuthorization("Bearer " + token);
        when(servicio.rolActual("99999999")).thenReturn(Optional.of("ADMINISTRADOR"));
        when(contexto.notifyContainerAboutLogin("99999999", Set.of("ADMINISTRADOR")))
                .thenReturn(AuthenticationStatus.SUCCESS);

        assertEquals(AuthenticationStatus.SUCCESS, mecanismo.validateRequest(solicitud, respuesta, contexto));
        verify(servicio, never()).autenticar(anyString(), anyString());
    }

    @Test
    void theRoleComesFromTheDatabaseNotFromTheToken() throws Exception {
        // An administrator changed the role to AUTORIDAD after the login
        String token = BackofficeToken.issue(
                new IdentidadBackoffice("99999999", "ADMINISTRADOR"), SECRET, Instant.now());
        requestWithAuthorization("Bearer " + token);
        when(servicio.rolActual("99999999")).thenReturn(Optional.of("AUTORIDAD"));
        when(contexto.notifyContainerAboutLogin("99999999", Set.of("AUTORIDAD")))
                .thenReturn(AuthenticationStatus.SUCCESS);

        assertEquals(AuthenticationStatus.SUCCESS, mecanismo.validateRequest(solicitud, respuesta, contexto));
        verify(contexto, never()).notifyContainerAboutLogin("99999999", Set.of("ADMINISTRADOR"));
    }

    @Test
    void aValidTokenOfAUserThatIsNoLongerInTheBackofficeGoesOnUnauthenticated() throws Exception {
        String token = BackofficeToken.issue(
                new IdentidadBackoffice("99999999", "ADMINISTRADOR"), SECRET, Instant.now());
        requestWithAuthorization("Bearer " + token);
        when(servicio.rolActual("99999999")).thenReturn(Optional.empty());

        assertEquals(AuthenticationStatus.NOT_DONE, mecanismo.validateRequest(solicitud, respuesta, contexto));
        verify(contexto, never()).notifyContainerAboutLogin(anyString(), anySet());
    }

    @Test
    void aTokenSignedWithAnotherSecretGoesOnUnauthenticated() throws Exception {
        String token = BackofficeToken.issue(new IdentidadBackoffice("99999999", "ADMINISTRADOR"),
                "another-secret-of-at-least-32-chars!".getBytes(StandardCharsets.UTF_8), Instant.now());
        requestWithAuthorization("Bearer " + token);

        assertEquals(AuthenticationStatus.NOT_DONE, mecanismo.validateRequest(solicitud, respuesta, contexto));
        verify(contexto, never()).notifyContainerAboutLogin(anyString(), anySet());
    }

    @Test
    void anExpiredTokenGoesOnUnauthenticated() throws Exception {
        String token = BackofficeToken.issue(new IdentidadBackoffice("99999999", "ADMINISTRADOR"), SECRET,
                Instant.now().minusSeconds(BackofficeToken.LIFETIME_S + 1));
        requestWithAuthorization("Bearer " + token);

        assertEquals(AuthenticationStatus.NOT_DONE, mecanismo.validateRequest(solicitud, respuesta, contexto));
        verify(contexto, never()).notifyContainerAboutLogin(anyString(), anySet());
    }

    @Test
    void anAuthorizationThatIsNotBearerIsIgnored() throws Exception {
        requestWithAuthorization("Basic OTk5OTk5OTk6Y2xhdmU=");

        assertEquals(AuthenticationStatus.NOT_DONE, mecanismo.validateRequest(solicitud, respuesta, contexto));
        verify(contexto, never()).notifyContainerAboutLogin(anyString(), anySet());
    }
}
