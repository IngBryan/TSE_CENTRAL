package tse.lab.frontofficefuncionarios.rest;

import static tse.lab.frontofficefuncionarios.rest.EmpresasResource.USUARIO_CI;
import static tse.lab.frontofficefuncionarios.rest.EmpresasResource.correlationId;
import static tse.lab.frontofficefuncionarios.rest.EmpresasResource.ejecutar;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService;

// CU Fiscalizacion de los permisos y estado de los vehiculos de las empresas. Igual que EmpresasResource:
// quien llama se identifica con X-Usuario-Ci y la autorizacion (que sea funcionario) la hace el servicio.
@Path("/empresas/{nro}/vehiculos")
@Produces(MediaType.APPLICATION_JSON)
public class HabilitacionesResource {

    @EJB
    private FiscalizacionHabilitacionesService habilitaciones;

    // Los vehiculos de la empresa con la vigencia de su permiso y su ITV, y el resumen.
    // 404 EMPRESA_NO_ENCONTRADA.
    @GET
    public Response listar(@HeaderParam(USUARIO_CI) String usuario, @PathParam("nro") Long nro,
            @Context HttpServletRequest req) {
        return ejecutar(() -> habilitaciones.vehiculosDeEmpresa(usuario, nro, correlationId(req)));
    }

    // Un vehiculo con su historial de ITV. 404 EMPRESA_NO_ENCONTRADA o VEHICULO_NO_ENCONTRADO (tambien si
    // la matricula es de otra empresa).
    @GET
    @Path("/{matricula}")
    public Response detalle(@HeaderParam(USUARIO_CI) String usuario, @PathParam("nro") Long nro,
            @PathParam("matricula") String matricula, @Context HttpServletRequest req) {
        return ejecutar(() -> habilitaciones.vehiculo(usuario, nro, matricula, correlationId(req)));
    }
}
