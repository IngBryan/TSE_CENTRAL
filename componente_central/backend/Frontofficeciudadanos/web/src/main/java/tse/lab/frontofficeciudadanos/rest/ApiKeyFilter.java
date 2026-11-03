package tse.lab.frontofficeciudadanos.rest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import tse.lab.frontofficeciudadanos.service.LogCiudadanos;
import tse.lab.log.LogEstructurado;

// El puerto 8080 esta publicado: toda la API exige la clave compartida con el frontoffice de Next
// en el header X-Api-Key (CIUDADANOS_API_KEY, del .env raiz). Sin clave configurada no atiende nada.
// Excepcion: /movil/*, que llama la app del chofer con su propio token (MovilTokenFilter).
// Tambien toma el X-Correlation-Id que manda el front (o genera uno), lo devuelve en la respuesta y deja el
// log de acceso de cada pedido con su codigo de respuesta y lo que tardo.
@Provider
public class ApiKeyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String CORRELATION_ID = "X-Correlation-Id";
    private static final String INICIO = "inicio";
    private static final Logger LOG = Logger.getLogger(ApiKeyFilter.class.getName());
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final byte[] CLAVE = claveConfigurada();

    @Override
    public void filter(ContainerRequestContext req) {
        req.setProperty(INICIO, System.nanoTime());
        String recibido = req.getHeaderString(CORRELATION_ID);
        req.setProperty(CORRELATION_ID,
                recibido != null && ID_VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString());

        // La app movil llama directo (sin el front de Next): se autentica con su token en MovilTokenFilter
        if (MovilTokenFilter.esApiMovil(req)) {
            return;
        }
        String clave = req.getHeaderString("X-Api-Key");
        if (CLAVE.length == 0 || clave == null
                || !MessageDigest.isEqual(CLAVE, clave.getBytes(StandardCharsets.UTF_8))) {
            req.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "no_autorizado"))
                    .build());
        }
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        Object id = req.getProperty(CORRELATION_ID);
        if (id != null) {
            res.getHeaders().putSingle(CORRELATION_ID, id);
        }
        if (req.getProperty(INICIO) instanceof Long inicio) {
            LOG.log(res.getStatus() >= 500 ? Level.SEVERE : Level.INFO, () -> LogCiudadanos.evento("http.request")
                    .correlationId(String.valueOf(id)).usuario(req.getHeaderString("X-Usuario-Ci"))
                    .responseCode(res.getStatus()).roundtripSeconds(LogEstructurado.segundosDesde(inicio))
                    .campo("metodo", req.getMethod()).campo("ruta", req.getUriInfo().getPath()).toString());
        }
    }

    private static byte[] claveConfigurada() {
        String valor = System.getenv("CIUDADANOS_API_KEY");
        return valor == null ? new byte[0] : valor.getBytes(StandardCharsets.UTF_8);
    }
}
