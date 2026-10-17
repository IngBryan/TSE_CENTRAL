
package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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

    @Context
    private HttpServletRequest solicitud;

    @Context
    private HttpServletResponse respuesta;

    @Context
    private jakarta.ws.rs.core.SecurityContext seguridadRest;

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

        HttpSession sesion = solicitud.getSession(false);

        if (sesion != null) {
            solicitud.changeSessionId();
        }

        return Response.ok(Map.of("autenticado", true)).build();
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

    @POST
    @Path("/logout")
    public Response logout() throws ServletException {

        solicitud.logout();

        HttpSession sesion = solicitud.getSession(false);

        if (sesion != null) {
            sesion.invalidate();
        }

        return Response.noContent().build();
    }
}
