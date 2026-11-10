
package tse.lab.backofficeautoridadesadministradores.rest.seguridad;

import java.time.Instant;
import java.util.Set;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tse.lab.backofficeautoridadesadministradores.service.autenticacion.AutenticacionBackofficeService;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.CuentaBackoffice;

/**
 * Integrates the backoffice authentication with the HTTP security of the Jakarta EE container, without a
 * session: the login checks the credentials (and AutenticacionResource issues a BackofficeToken), and every
 * other request is authenticated by the token in its Authorization header, so any WildFly replica can serve it.
 */
@ApplicationScoped
public class AutenticacionHttpMechanism
        implements HttpAuthenticationMechanism {

    /**
     * Grupo que se agrega junto al rol mientras el usuario no cambie la contraseña que le dio un
     * administrador. CambioContrasenaPendienteFilter le bloquea la API, salvo cambiarla.
     */
    public static final String CAMBIO_CONTRASENA_PENDIENTE = "CAMBIO_CONTRASENA_PENDIENTE";

    private static final String BEARER = "Bearer ";

    @EJB
    private AutenticacionBackofficeService servicio;

    private byte[] tokenSecret = BackofficeToken.configuredSecret();

    @Override
    public AuthenticationStatus validateRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpMessageContext contexto)
            throws AuthenticationException {

        if (!contexto.isAuthenticationRequest()) {
            return fromToken(request, contexto);
        }

        var credencial = contexto.getAuthParameters()
                .getCredential();

        if (!(credencial instanceof UsernamePasswordCredential datos)) {
            return contexto.responseUnauthorized();
        }

        return servicio.autenticar(
                datos.getCaller(),
                datos.getPasswordAsString())
                .map(cuenta -> login(contexto, cuenta))
                .orElseGet(contexto::responseUnauthorized);
    }

    /**
     * Sin un token válido el pedido sigue sin autenticar: los recursos responden 401. El rol y el cambio de
     * contraseña pendiente salen de la base, no del token: después del login un administrador puede haber
     * cambiado el rol (o quitado al usuario), o el usuario puede haber cambiado la contraseña, y eso vale
     * desde el pedido siguiente en vez de cuando vence el token.
     */
    private AuthenticationStatus fromToken(HttpServletRequest request, HttpMessageContext contexto) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return contexto.doNothing();
        }
        return BackofficeToken.verify(header.substring(BEARER.length()), tokenSecret, Instant.now())
                .flatMap(identity -> servicio.cuentaActual(identity.ci()))
                .map(cuenta -> login(contexto, cuenta))
                .orElseGet(contexto::doNothing);
    }

    private static AuthenticationStatus login(HttpMessageContext contexto, CuentaBackoffice cuenta) {
        return contexto.notifyContainerAboutLogin(
                cuenta.ci(),
                cuenta.debeCambiarContrasena()
                        ? Set.of(cuenta.rol(), CAMBIO_CONTRASENA_PENDIENTE)
                        : Set.of(cuenta.rol()));
    }
}
