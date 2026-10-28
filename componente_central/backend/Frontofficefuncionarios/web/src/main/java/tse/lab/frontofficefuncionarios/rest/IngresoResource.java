package tse.lab.frontofficefuncionarios.rest;

import java.util.Map;
import java.util.regex.Pattern;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficefuncionarios.service.autenticacion.IngresoFuncionarioService;
import tse.lab.frontofficefuncionarios.service.autenticacion.IngresoFuncionarioService.ResultadoIngreso;

// Ingreso de funcionarios: el frontoffice de Next llama aca despues de validar el login con gub.uy (o, en
// testing, con la cedula ingresada a mano). Responde siempre 200 con el resultado; sesionPermitida dice si
// el front puede abrir la sesion.
@Path("/funcionarios")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IngresoResource {

    private static final Pattern CEDULA = Pattern.compile("\\d{6,8}");

    public record IngresoRequest(String cedula, String email) {
    }

    public record IngresoResponse(String resultado, boolean sesionPermitida, String cedula, String email,
            String nombres, String apellidos) {
        static IngresoResponse de(ResultadoIngreso r) {
            return new IngresoResponse(r.resultado().name(), r.sesionPermitida(), r.cedula(), r.email(), r.nombres(),
                    r.apellidos());
        }
    }

    @EJB
    private IngresoFuncionarioService ingreso;

    // FUNCIONARIOS_INGRESO_TESTING=true habilita /ingreso-testing (se puede fijar en los tests)
    boolean testingHabilitado = "true".equals(System.getenv("FUNCIONARIOS_INGRESO_TESTING"));

    @POST
    @Path("/ingreso-gubuy")
    public Response ingresoGubUy(IngresoRequest body, @Context HttpServletRequest req) {
        if (!cedulaValida(body == null ? null : body.cedula())) {
            return cedulaInvalida();
        }
        return Response.ok(IngresoResponse.de(ingreso.ingresarConGubUy(body.cedula(), body.email(),
                CasosResource.correlationId(req)))).build();
    }

    // Solo con FUNCIONARIOS_INGRESO_TESTING=true en WildFly; si no, 404 (como si no existiera). El front lo
    // llama solo si ademas tiene LOGIN_TESTING=true.
    @POST
    @Path("/ingreso-testing")
    public Response ingresoTesting(IngresoRequest body, @Context HttpServletRequest req) {
        if (!testingHabilitado) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!cedulaValida(body == null ? null : body.cedula())) {
            return cedulaInvalida();
        }
        return Response.ok(IngresoResponse.de(ingreso.ingresarParaTesting(body.cedula(),
                CasosResource.correlationId(req)))).build();
    }

    private static boolean cedulaValida(String cedula) {
        return cedula != null && CEDULA.matcher(cedula).matches();
    }

    private static Response cedulaInvalida() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "cedula (6 a 8 digitos) obligatoria"))
                .build();
    }
}
