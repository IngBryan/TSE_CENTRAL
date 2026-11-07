
package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.DatosNodo;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.DatosNodoInvalidosException;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.GestionNodosPerifericosService;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.NodoDuplicadoException;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.TipoNodo;

/**
 * API REST para la gestión administrativa de nodos periféricos.
 *
 * Expone las operaciones de consulta y alta, delegando la lógica
 * de negocio en GestionNodosPerifericosService.
 */
@Path("/nodos-perifericos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NodosPerifericosResource {

    @EJB
    private GestionNodosPerifericosService servicio;

    @Context
    private SecurityContext seguridad;

    public record AltaNodoRequest(
            TipoNodo tipo,
            String nombre,
            String ubicacion,
            Long idExterno) {
    }

    /**
     * Obtiene todos los nodos periféricos registrados.
     */
    @GET
    public Response listar() {
        Response acceso = AccesoAdministrador.verificar(seguridad);

        if (acceso != null) {
            return acceso;
        }

        return Response.ok(servicio.listarTodos()).build();
    }

    /**
     * Registra un nuevo nodo periférico.
     */
    @POST
    public Response registrar(AltaNodoRequest datos) {
        Response acceso = AccesoAdministrador.verificar(seguridad);

        if (acceso != null) {
            return acceso;
        }

        if (datos == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "datos_invalidos",
                            "detalle", "El cuerpo es obligatorio"))
                    .build();
        }

        try {
            DatosNodo nodo = servicio.registrar(
                    datos.tipo(),
                    datos.nombre(),
                    datos.ubicacion(),
                    datos.idExterno()
            );

            return Response.status(Response.Status.CREATED)
                    .entity(nodo)
                    .build();

        } catch (DatosNodoInvalidosException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "datos_invalidos",
                            "detalle", e.getMessage()))
                    .build();

        } catch (NodoDuplicadoException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "error", "nodo_duplicado",
                            "detalle", e.getMessage()))
                    .build();
        }
    }
}
