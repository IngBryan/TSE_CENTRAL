package tse.lab.frontofficefuncionarios.rest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

// Igual que en Frontofficeciudadanos: toda la API exige la clave compartida con el frontoffice de Next
// en el header X-Api-Key (BACKEND_API_KEY, del .env raiz). Sin clave configurada no atiende nada.
// Tambien toma el X-Correlation-Id que manda el front (o genera uno) y lo devuelve en la respuesta.
@Provider
public class ApiKeyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String CORRELATION_ID = "X-Correlation-Id";
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final byte[] CLAVE = claveConfigurada();

    @Override
    public void filter(ContainerRequestContext req) {
        String recibido = req.getHeaderString(CORRELATION_ID);
        req.setProperty(CORRELATION_ID,
                recibido != null && ID_VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString());

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
    }

    private static byte[] claveConfigurada() {
        String valor = System.getenv("BACKEND_API_KEY");
        return valor == null ? new byte[0] : valor.getBytes(StandardCharsets.UTF_8);
    }
}
