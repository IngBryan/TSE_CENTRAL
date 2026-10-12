package tse.lab.frontofficeciudadanos.rest;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.servicio.IngresoService;
import tse.lab.frontofficeciudadanos.servicio.ResultadoIngreso;
import tse.lab.frontofficeciudadanos.servicio.RolPersona;

// CU-01: el frontoffice de Next llama aca despues de validar el login con gub.uy.
@Path("/ciudadanos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IngresoResource {

    private static final Pattern CEDULA = Pattern.compile("\\d{6,8}");

    public record IngresoRequest(String cedula, String email, String sub) {
    }

    public record PerfilResponse(String cedula, String email, String nombres, String apellidos, String estado) {
        static PerfilResponse de(PerfilCiudadano p) {
            return new PerfilResponse(p.getCi(), p.getEmail(), p.getNombres(), p.getApellidos(), p.getEstado().name());
        }
    }

    public record IngresoResponse(String resultado, boolean sesionPermitida, boolean nuevo, PerfilResponse perfil,
            List<RolPersona> roles) {
    }

    @EJB
    private IngresoService ingreso;

    @POST
    @Path("/ingreso-gubuy")
    public Response ingresoGubUy(IngresoRequest body, @Context HttpServletRequest req) {
        if (body == null || body.cedula() == null || !CEDULA.matcher(body.cedula()).matches()
                || body.sub() == null || body.sub().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "cedula (6 a 8 digitos) y sub son obligatorios"))
                    .build();
        }
        String correlationId = (String) req.getAttribute(ApiKeyFilter.CORRELATION_ID);
        ResultadoIngreso r = ingreso.ingresarConGubUy(body.cedula(), body.email(), body.sub(), correlationId);
        return Response.ok(new IngresoResponse(r.resultado().name(), r.sesionPermitida(), r.nuevo(),
                PerfilResponse.de(r.perfil()), r.roles())).build();
    }
}
