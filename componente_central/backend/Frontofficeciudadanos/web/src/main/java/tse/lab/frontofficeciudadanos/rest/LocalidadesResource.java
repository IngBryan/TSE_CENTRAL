package tse.lab.frontofficeciudadanos.rest;

import java.util.List;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;

/** Contrato de lectura para la seleccion de localidades del Frontoffice Ciudadanos. */
@Path("/localidades")
@Produces(MediaType.APPLICATION_JSON)
public class LocalidadesResource {
    @EJB
    private LocalidadDAO localidades;

    public record LocalidadVista(Long id, String nombre, String departamento) { }

    @GET
    public List<LocalidadVista> listar() {
        return localidades.listar().stream()
                .map(l -> new LocalidadVista(l.getId(), l.getNombre(), l.getDepartamento()))
                .toList();
    }
}
