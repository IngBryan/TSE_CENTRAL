
package tse.lab.backofficeautoridadesadministradores.rest;

import java.time.Instant;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import tse.lab.backofficeautoridadesadministradores.rest.seguridad.BackofficeToken;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.IdentidadBackoffice;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AutenticacionResource {

    private final jakarta.security.enterprise.SecurityContext seguridad;

    @Inject
    public AutenticacionResource(
            jakarta.security.enterprise.SecurityContext seguridad) {
        this.seguridad = seguridad;
    }

    // RESTEasy makes the resource @RequestScoped: the CDI proxy needs a no-args constructor
    protected AutenticacionResource() {
        this.seguridad = null;
    }

    @Context
    private HttpServletRequest solicitud;

    @Context
    private HttpServletResponse respuesta;

    @Context
    private jakarta.ws.rs.core.SecurityContext seguridadRest;

    private byte[] tokenSecret = BackofficeToken.configuredSecret();

    public record Credenciales(
            String ci,
            String contrasena) {
    }

    @POST
    @Path("/login")
    public Response login(Credenciales datos) {

        if (datos == null
                || datos.ci() == null
                || datos.ci().isBlank()
                || datos.contrasena() == null
                || datos.contrasena().isEmpty()) {

            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "credenciales_invalidas"))
                    .build();
        }

        // Without BACKOFFICE_JWT_SECRET there is no way to issue the token
        if (tokenSecret.length == 0) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "login_no_disponible"))
                    .build();
        }

        AuthenticationStatus estado = seguridad.authenticate(
                solicitud,
                respuesta,
                AuthenticationParameters.withParams()
                        .credential(new UsernamePasswordCredential(
                                datos.ci().strip(),
                                datos.contrasena())));

        if (estado != AuthenticationStatus.SUCCESS) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "credenciales_invalidas"))
                    .build();
        }

        String rol = seguridad.isCallerInRole("ADMINISTRADOR") ? "ADMINISTRADOR"
                : seguridad.isCallerInRole("AUTORIDAD") ? "AUTORIDAD" : null;

        if (rol == null) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "no_autorizado"))
                    .build();
        }

        // No session: the client sends this token in "Authorization: Bearer" on every request
        String token = BackofficeToken.issue(
                new IdentidadBackoffice(seguridad.getCallerPrincipal().getName(), rol),
                tokenSecret,
                Instant.now());

        return Response.ok(Map.of(
                "token", token,
                "expiraEn", BackofficeToken.LIFETIME_S)).build();
    }

    @GET
    @Path("/me")
    public Response usuarioActual() {

        if (seguridadRest.getUserPrincipal() == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "no_autenticado"))
                    .build();
        }

        String rol;

        if (seguridadRest.isUserInRole("ADMINISTRADOR")) {
            rol = "ADMINISTRADOR";
        } else if (seguridadRest.isUserInRole("AUTORIDAD")) {
            rol = "AUTORIDAD";
        } else {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "no_autorizado"))
                    .build();
        }

        return Response.ok(Map.of(
                "ci", seguridadRest.getUserPrincipal().getName(),
                "rol", rol)).build();
    }

    /** There is no session to close in the server: the client discards its token. */
    @POST
    @Path("/logout")
    public Response logout() throws ServletException {

        solicitud.logout();

        return Response.noContent().build();
    }
}
