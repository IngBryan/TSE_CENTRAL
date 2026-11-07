package tse.lab.frontofficefuncionarios.rest;

import static tse.lab.frontofficefuncionarios.rest.CasosResource.correlationId;
import static tse.lab.frontofficefuncionarios.rest.CasosResource.ejecutar;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.MonitoreoViajesService;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.SeguimientoGpsService;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.SeguimientoNoDisponibleException;

// CU Monitorear viajes y pesajes. Igual que CasosResource: quien llama se identifica con X-Usuario-Ci y la
// autorizacion (que sea funcionario) la hace el servicio. Solo consultas.
@Path("/viajes")
@Produces(MediaType.APPLICATION_JSON)
public class ViajesResource {

    static final String USUARIO_CI = "X-Usuario-Ci";

    @EJB
    private MonitoreoViajesService viajes;

    @EJB
    private SeguimientoGpsService seguimiento;

    // Listado paginado: ?estado=EN_CURSO&matricula=SBA1234&nroEmpresa=1&pagina=2&tamanio=20, todos opcionales.
    // Llegan como texto y los valida el servicio (400 en vez del 404 de JAX-RS con un numero invalido).
    @GET
    public Response listar(@HeaderParam(USUARIO_CI) String usuario, @QueryParam("estado") String estado,
            @QueryParam("matricula") String matricula, @QueryParam("nroEmpresa") String nroEmpresa,
            @QueryParam("pagina") String pagina, @QueryParam("tamanio") String tamanio,
            @Context HttpServletRequest req) {
        return ejecutar(() -> viajes.listar(usuario, estado, matricula, nroEmpresa, pagina, tamanio,
                correlationId(req)));
    }

    // Detalle con la linea de tiempo (eventos de la app y modificaciones) y los pesajes del viaje.
    // 404 VIAJE_NO_ENCONTRADO.
    @GET
    @Path("/{id}")
    public Response detalle(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") String id,
            @Context HttpServletRequest req) {
        return ejecutar(() -> viajes.detalle(usuario, id, correlationId(req)));
    }

    // Trayectoria de Tracking en MongoDB, no confundir con los eventos de la app móvil.
    // [desde,hasta) ISO-8601 UTC, limite por página de candidatos (no por puntos adjudicados).
    @GET
    @Path("/{id}/gps")
    public Response historialGps(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") String id,
            @QueryParam("desde") String desde, @QueryParam("hasta") String hasta,
            @QueryParam("limite") String limite, @Context HttpServletRequest req) {
        return ejecutarSeguimiento(() -> seguimiento.historial(usuario, id, desde, hasta, limite, correlationId(req)));
    }

    @GET
    @Path("/{id}/gps/ultima")
    public Response ultimaGps(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") String id,
            @Context HttpServletRequest req) {
        return ejecutarSeguimiento(() -> seguimiento.ultima(usuario, id, correlationId(req)));
    }

    @GET
    @Path("/{id}/rutas-autorizadas")
    public Response rutasAutorizadas(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") String id,
            @Context HttpServletRequest req) {
        return ejecutarSeguimiento(() -> seguimiento.rutas(usuario, id, correlationId(req)));
    }

    private static Response ejecutarSeguimiento(CasosResource.Operacion op) {
        try {
            return ejecutar(op);
        } catch (SeguimientoNoDisponibleException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(java.util.Map.of("error", "seguimiento_no_disponible", "detalle", e.getMessage()))
                    .build();
        }
    }

    // Foto adjunta a un evento (incidente): JPEG o PNG tal como la mando el movil. 404 EVENTO_NO_ENCONTRADO
    // o SIN_FOTO.
    @GET
    @Path("/{id}/eventos/{idEvento}/foto")
    @Produces({ "image/jpeg", "image/png", MediaType.APPLICATION_JSON })
    public Response foto(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") String id,
            @PathParam("idEvento") String idEvento, @Context HttpServletRequest req) {
        Response r = ejecutar(() -> viajes.fotoEvento(usuario, id, idEvento, correlationId(req)));
        if (r.getStatus() != 200) {
            return Response.fromResponse(r).type(MediaType.APPLICATION_JSON).build();
        }
        byte[] foto = (byte[]) r.getEntity();
        boolean png = foto.length > 1 && (foto[0] & 0xFF) == 0x89;
        return Response.ok(foto, png ? "image/png" : "image/jpeg").header("Cache-Control", "private, max-age=3600")
                .build();
    }
}
