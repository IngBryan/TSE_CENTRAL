package tse.lab.backofficeautoridadesadministradores.rest;

import java.security.Principal;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;
import tse.lab.log.LogEstructurado;

/**
 * Toma el X-Correlation-Id que manda el front (o genera uno), lo devuelve
 * en la respuesta y deja el log de acceso de cada pedido.
 */
@Provider
public class CorrelacionFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String CORRELATION_ID = "X-Correlation-Id";
    private static final String INICIO = "inicio";
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger LOG = Logger.getLogger(CorrelacionFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext req) {
        req.setProperty(INICIO, System.nanoTime());
        String recibido = req.getHeaderString(CORRELATION_ID);
        req.setProperty(CORRELATION_ID,
                recibido != null && ID_VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString());
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        Object id = req.getProperty(CORRELATION_ID);
        if (id != null) {
            res.getHeaders().putSingle(CORRELATION_ID, id);
        }
        if (req.getProperty(INICIO) instanceof Long inicio) {
            Principal usuario = req.getSecurityContext().getUserPrincipal();
            LOG.log(res.getStatus() >= 500 ? Level.SEVERE : Level.INFO, () -> LogBackoffice.evento("http.request")
                    .correlationId(String.valueOf(id)).usuario(usuario == null ? null : usuario.getName())
                    .responseCode(res.getStatus()).roundtripSeconds(LogEstructurado.segundosDesde(inicio))
                    .campo("metodo", req.getMethod()).campo("ruta", req.getUriInfo().getPath()).toString());
        }
    }
}
