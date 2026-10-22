package tse.lab.frontofficefuncionarios.rest;

import static tse.lab.frontofficefuncionarios.rest.CasosResource.USUARIO_CI;
import static tse.lab.frontofficefuncionarios.rest.CasosResource.correlationId;
import static tse.lab.frontofficefuncionarios.rest.CasosResource.ejecutar;

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
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AltaEmpresa;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AsociacionUsuario;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.CambiosEmpresa;

// CU Gestionar empresas de transporte. Igual que CasosResource: quien llama se identifica con
// X-Usuario-Ci y la autorizacion (que sea funcionario) la hace el servicio; las excepciones de negocio
// se traducen a 400 / 403 / 404 / 409 con CasosResource.ejecutar.
@Path("/empresas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EmpresasResource {

    @EJB
    private GestionEmpresasService empresas;

    @GET
    public Response listar(@HeaderParam(USUARIO_CI) String usuario, @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.listar(usuario, correlationId(req)));
    }

    // Body {"nroEmpresa": 2, "nombrePublico": "...", "razonSocial": "...", "direccion": "...",
    // "habilitada": false}. 409 EMPRESA_EXISTENTE si el numero ya esta registrado.
    @POST
    public Response alta(@HeaderParam(USUARIO_CI) String usuario, AltaEmpresa body, @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.alta(usuario, body, correlationId(req)));
    }

    @GET
    @Path("/{nro}")
    public Response detalle(@HeaderParam(USUARIO_CI) String usuario, @PathParam("nro") Long nro,
            @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.detalle(usuario, nro, correlationId(req)));
    }

    // Body {"nombrePublico": "...", "razonSocial": "...", "direccion": "...", "habilitada": true};
    // habilitada es opcional
    @PUT
    @Path("/{nro}")
    public Response modificar(@HeaderParam(USUARIO_CI) String usuario, @PathParam("nro") Long nro,
            CambiosEmpresa body, @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.modificar(usuario, nro, body, correlationId(req)));
    }

    // Body {"ci": "12345678"}: asocia al usuario (registrado y validado) como responsable de la empresa.
    // 404 USUARIO_NO_ENCONTRADO; 409 USUARIO_NO_VALIDADO, EMPRESA_CON_RESPONSABLE o RESPONSABLE_DE_OTRA_EMPRESA.
    @POST
    @Path("/{nro}/responsable")
    public Response asociarUsuario(@HeaderParam(USUARIO_CI) String usuario, @PathParam("nro") Long nro,
            AsociacionUsuario body, @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.asociarUsuario(usuario, nro, body, correlationId(req)));
    }
}
