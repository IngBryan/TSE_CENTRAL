package tse.lab.frontofficeciudadanos.rest;

import java.util.Map;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.service.rutas.PrevisualizacionRutasService;
import tse.lab.frontofficeciudadanos.service.rutas.RutasException;

/** Previsualizacion de GeoJSON, sin crear ni modificar Guías. Requiere X-Api-Key. */
@Path("/rutas/previsualizacion")
@Produces(MediaType.APPLICATION_JSON)
public class RutasPrevisualizacionResource {
    @EJB private PrevisualizacionRutasService rutas;

    @GET
    public Response previsualizar(@QueryParam("origenId") Long origenId,
            @QueryParam("destinoId") Long destinoId) {
        try {
            return Response.ok(rutas.calcular(origenId, destinoId)).build();
        } catch (RutasException e) {
            return Response.status(e.status()).entity(Map.of("error", e.codigo(), "detalle", e.getMessage())).build();
        }
    }
}
