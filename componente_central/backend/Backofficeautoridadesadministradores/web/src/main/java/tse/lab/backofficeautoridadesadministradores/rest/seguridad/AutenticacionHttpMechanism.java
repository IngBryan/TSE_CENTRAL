
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

/**
 * Integrates the backoffice authentication with the HTTP security of the Jakarta EE container, without a
 * session: the login checks the credentials (and AutenticacionResource issues a BackofficeToken), and every
 * other request is authenticated by the token in its Authorization header, so any WildFly replica can serve it.
 */
@ApplicationScoped
public class AutenticacionHttpMechanism
        implements HttpAuthenticationMechanism {

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

        var identidad = servicio.autenticar(
                datos.getCaller(),
                datos.getPasswordAsString());

        if (identidad.isEmpty()) {
            return contexto.responseUnauthorized();
        }

        var usuario = identidad.get();

        return contexto.notifyContainerAboutLogin(
                usuario.ci(),
                Set.of(usuario.rol()));
    }

    /** Without a valid token the request goes on unauthenticated: the resources answer 401. */
    private AuthenticationStatus fromToken(HttpServletRequest request, HttpMessageContext contexto) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return contexto.doNothing();
        }
        return BackofficeToken.verify(header.substring(BEARER.length()), tokenSecret, Instant.now())
                .map(identity -> contexto.notifyContainerAboutLogin(identity.ci(), Set.of(identity.rol())))
                .orElseGet(contexto::doNothing);
    }
}
