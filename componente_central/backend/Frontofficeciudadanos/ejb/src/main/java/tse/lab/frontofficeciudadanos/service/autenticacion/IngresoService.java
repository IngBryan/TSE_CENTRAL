package tse.lab.frontofficeciudadanos.service.autenticacion;

import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ResultadoValidacion;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ValidacionPdiService;
import tse.lab.model.Ciudadano;

// CU-01 Ingresar al sistema con Usuario gub.uy, pasos 6 a 9. Los pasos 1 a 5 (OIDC) y la sesion
// los hace el frontoffice de Next; este servicio recibe la cedula y el email que gub.uy ya valido
// y devuelve, ademas del resultado, los roles para mostrar la pantalla de inicio que corresponde.
@Stateless
public class IngresoService {

    private static final Logger LOG = Logger.getLogger(IngresoService.class.getName());

    @EJB
    private CiudadanoDAO perfiles;
    @EJB
    private ValidacionPdiService validacionPdi;
    @EJB
    private RolesService roles;

    public ResultadoIngreso ingresarConGubUy(String cedula, String email, String subGubUy, String correlationId) {
        return ingresar(cedula, email, subGubUy, correlationId, "gubuy");
    }

    // Ingreso de testing (INGRESO_TESTING=true, mientras no este registrada la redirect URI de gub.uy del
    // ambiente desplegado): mismos pasos 6 a 9 (perfil, PDI, roles) con la cedula que se ingresa a mano.
    // No toca el sub ni el email: el vinculo con la cuenta gub.uy queda como estaba.
    public ResultadoIngreso ingresarParaTesting(String cedula, String correlationId) {
        return ingresar(cedula, null, null, correlationId, "testing");
    }

    private ResultadoIngreso ingresar(String cedula, String email, String subGubUy, String correlationId, String via) {
        boolean nuevo = false;
        Ciudadano perfil = perfiles.buscarPorCi(cedula);

        if (perfil == null) {
            // Paso 7: primer ingreso. Si ya tenia un rol (chofer o responsable dado de alta antes), su
            // Ciudadano ya existe y entra por la rama de abajo (FA2).
            perfil = new Ciudadano(cedula, email, subGubUy);
            perfiles.guardar(perfil);
            nuevo = true;
        } else if (subGubUy != null) {
            // FA1 (ya habia ingresado) o FA2 (Ciudadano creado al darle un rol, sin cuenta gub.uy todavia)
            if (perfil.getSubGubUy() == null) {
                LOG.info(() -> "evento=ingreso.vinculacion correlationId=" + correlationId + " ci=" + cedula);
            } else if (!perfil.getSubGubUy().equals(subGubUy)) {
                LOG.warning(() -> "evento=ingreso.sub_distinto correlationId=" + correlationId + " ci=" + cedula
                        + " detalle=\"la cedula estaba vinculada a otra cuenta gub.uy, se actualiza\"");
            }
            perfil.setSubGubUy(subGubUy);
            if (email != null && !email.isBlank()) {
                perfil.setEmail(email);
            }
        }
        perfil.setUltimoIngreso(Instant.now());

        // Paso 8 / FA1 6b-6c / FA6-FA8
        ResultadoValidacion resultado = validacionPdi.validarSiCorresponde(perfil, correlationId);

        boolean sesion = resultado == ResultadoValidacion.VALIDADO || resultado == ResultadoValidacion.NO_DISPONIBLE;
        List<RolPersona> rolesPersona = sesion ? roles.rolesDe(cedula) : List.of();
        final Ciudadano p = perfil;
        final boolean esNuevo = nuevo;
        LOG.info(() -> "evento=ingreso." + via + " correlationId=" + correlationId + " ci=" + cedula + " nuevo=" + esNuevo
                + " resultado=" + resultado + " estado=" + p.getEstado() + " sesion=" + sesion
                + " roles=" + rolesPersona.stream().map(r -> r.rol() + ":" + r.nroEmpresa()).toList());
        return new ResultadoIngreso(perfil, resultado, nuevo, sesion, rolesPersona);
    }
}
