
package tse.lab.backofficeautoridadesadministradores.rest.seguridad;

import java.util.Set;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AutoApplySession;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tse.lab.backofficeautoridadesadministradores.service.autenticacion.AutenticacionBackofficeService;

/**
 * Integra la autenticacion del Backoffice con
 * la seguridad HTTP del contenedor Jakarta EE.
 */
@ApplicationScoped
@AutoApplySession
public class AutenticacionHttpMechanism
        implements HttpAuthenticationMechanism {

    @EJB
    private AutenticacionBackofficeService servicio;

    @Override
    public AuthenticationStatus validateRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpMessageContext contexto)
            throws AuthenticationException {

        if (!contexto.isAuthenticationRequest()) {
            return contexto.doNothing();
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
}
