package tse.lab.frontofficeciudadanos.rest;

import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.service.apimovil.login.ErrorLoginMovil;
import tse.lab.frontofficeciudadanos.service.apimovil.login.LoginMovilService;
import tse.lab.frontofficeciudadanos.service.apimovil.login.LoginMovilService.ResultadoLoginMovil;
import tse.lab.frontofficeciudadanos.service.apimovil.login.LoginMovilService.UsuarioMovil;

// Login de la app movil con gub.uy (ver LoginMovilService). Es la unica ruta de /movil sin token:
// la llama la app con el code que le devolvio ID Uruguay. No exige X-Api-Key (ver ApiKeyFilter).
//
// POST /api/movil/auth/token  { "code", "codeVerifier", "nonce", "redirectUri" }
//   200 { "token", "tipo": "Bearer", "expira", "usuario": { "cedula", "nombre", "roles" } }
//   400/403/503 { "error": "token_invalido" | "no_chofer" | "no_existe" | "menor_de_edad" | "idp_no_disponible" | ... }
@Path("/movil/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MovilAuthResource {

    public record LoginRequest(String code, String codeVerifier, String nonce, String redirectUri) {
    }

    public record TokenResponse(String token, String tipo, long expira, UsuarioMovil usuario) {
    }

    @EJB
    private LoginMovilService login;

    @POST
    @Path("/token")
    public Response token(LoginRequest body, @Context HttpServletRequest req) {
        String correlationId = GuiasResource.correlationId(req);
        try {
            ResultadoLoginMovil r = body == null
                    ? login.ingresar(null, null, null, null, correlationId)
                    : login.ingresar(body.code(), body.codeVerifier(), body.nonce(), body.redirectUri(), correlationId);
            return Response.ok(new TokenResponse(r.token(), "Bearer", r.expira(), r.usuario()))
                    .header("Cache-Control", "no-store").build();
        } catch (ErrorLoginMovil e) {
            return Response.status(e.getMotivo().status())
                    .entity(Map.of("error", e.getMotivo().codigo()))
                    .header("Cache-Control", "no-store").build();
        }
    }
}
