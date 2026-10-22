package tse.lab.frontofficeciudadanos.rest;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import tse.lab.frontofficeciudadanos.service.apimovil.login.TokenMovil;
import tse.lab.frontofficeciudadanos.service.apimovil.login.TokenMovil.TokenInvalidoException;

// API de la app movil (/movil/*, salvo /movil/auth/token): exige Authorization: Bearer <token de la
// app> (TokenMovil) y deja la cedula del token en la propiedad CEDULA para MovilResource. La app nunca
// manda la cedula: sale del token firmado por el central. 401 { "error": "token_vencido" | "token_invalido" }.
@Provider
@Priority(Priorities.AUTHENTICATION)
public class MovilTokenFilter implements ContainerRequestFilter {

    static final String CEDULA = "movil.cedula";
    private static final Pattern BEARER = Pattern.compile("^Bearer\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
    private static final byte[] SECRETO = TokenMovil.secretoConfigurado();

    @Override
    public void filter(ContainerRequestContext req) {
        if (!esApiMovil(req) || esLogin(req)) {
            return;
        }
        String auth = req.getHeaderString("Authorization");
        Matcher m = auth == null ? null : BEARER.matcher(auth);
        if (m == null || !m.matches() || SECRETO.length == 0) {
            noAutorizado(req, "token_invalido");
            return;
        }
        try {
            req.setProperty(CEDULA, TokenMovil.validar(m.group(1), SECRETO, Instant.now()));
        } catch (TokenInvalidoException e) {
            noAutorizado(req, e.isVencido() ? "token_vencido" : "token_invalido");
        }
    }

    static boolean esApiMovil(ContainerRequestContext req) {
        return ruta(req).startsWith("movil/");
    }

    static boolean esLogin(ContainerRequestContext req) {
        return ruta(req).equals("movil/auth/token");
    }

    private static String ruta(ContainerRequestContext req) {
        String p = req.getUriInfo().getPath();
        return p.startsWith("/") ? p.substring(1) : p;
    }

    private static void noAutorizado(ContainerRequestContext req, String error) {
        req.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer error=\"invalid_token\"")
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", error))
                .build());
    }
}
