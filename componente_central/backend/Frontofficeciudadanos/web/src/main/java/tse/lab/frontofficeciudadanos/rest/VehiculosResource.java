package tse.lab.frontofficeciudadanos.rest;

import static tse.lab.frontofficeciudadanos.rest.GuiasResource.correlationId;
import static tse.lab.frontofficeciudadanos.rest.GuiasResource.ejecutar;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.DatosItv;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.DatosPermiso;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.EdicionVehiculo;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.NuevoVehiculo;

// ABM de Vehiculos de la empresa del responsable, con su permiso de circulacion y sus ITV. Quien llama
// se identifica con X-Usuario-Ci (ver ChoferesResource para el criterio de confianza). Los errores se
// traducen como en GuiasResource: 400 datos invalidos, 403, 404 y 409 (VEHICULO_DADO_DE_BAJA,
// VEHICULO_CON_VIAJES).
@Path("/empresa/vehiculos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VehiculosResource {

    @EJB
    private GestionVehiculosService vehiculos;

    @GET
    public Response listar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @Context HttpServletRequest req) {
        return ejecutar(() -> vehiculos.listar(usuario, correlationId(req)));
    }

    @POST
    public Response crear(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, NuevoVehiculo datos,
            @Context HttpServletRequest req) {
        return ejecutar(() -> vehiculos.crear(usuario, datos, correlationId(req)));
    }

    @GET
    @Path("/{matricula}")
    public Response detalle(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @PathParam("matricula") String matricula, @Context HttpServletRequest req) {
        return ejecutar(() -> vehiculos.detalle(usuario, matricula, correlationId(req)));
    }

    @PUT
    @Path("/{matricula}")
    public Response editar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @PathParam("matricula") String matricula, EdicionVehiculo datos, @Context HttpServletRequest req) {
        return ejecutar(() -> vehiculos.editar(usuario, matricula, datos, correlationId(req)));
    }

    @PUT
    @Path("/{matricula}/permiso")
    public Response permiso(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @PathParam("matricula") String matricula, DatosPermiso datos, @Context HttpServletRequest req) {
        return ejecutar(() -> vehiculos.registrarPermiso(usuario, matricula, datos, correlationId(req)));
    }

    @POST
    @Path("/{matricula}/itv")
    public Response itv(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @PathParam("matricula") String matricula, DatosItv datos, @Context HttpServletRequest req) {
        return ejecutar(() -> vehiculos.agregarItv(usuario, matricula, datos, correlationId(req)));
    }

    // Baja logica (POST y no DELETE: el vehiculo se conserva, como la cancelacion de una guia)
    @POST
    @Path("/{matricula}/baja")
    public Response baja(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @PathParam("matricula") String matricula, @Context HttpServletRequest req) {
        return ejecutar(() -> vehiculos.darDeBaja(usuario, matricula, correlationId(req)));
    }
}
