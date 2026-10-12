package tse.lab.frontofficeciudadanos.servicio;

import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Ciudadano;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.model.Responsable;
import tse.lab.frontofficeciudadanos.pdi.ResultadoValidacion;
import tse.lab.frontofficeciudadanos.pdi.ValidacionPdiService;

// CU-01 Ingresar al sistema con Usuario gub.uy, pasos 6 a 9. Los pasos 1 a 5 (OIDC) y la sesion
// los hace el frontoffice de Next; este servicio recibe la cedula y el email que gub.uy ya valido
// y devuelve, ademas del resultado, los roles para mostrar la pantalla de inicio que corresponde.
@Stateless
public class IngresoService {

    private static final Logger LOG = Logger.getLogger(IngresoService.class.getName());

    @EJB
    private PerfilCiudadanoDAO perfiles;
    @EJB
    private ChoferDAO choferes;
    @EJB
    private ResponsableDAO responsables;
    @EJB
    private ValidacionPdiService validacionPdi;
    @EJB
    private RolesService roles;

    public ResultadoIngreso ingresarConGubUy(String cedula, String email, String subGubUy, String correlationId) {
        boolean nuevo = false;
        PerfilCiudadano perfil = perfiles.buscarPorCi(cedula);

        if (perfil == null) {
            // Paso 7, o FA2 si la empresa ya lo habia dado de alta como chofer o responsable:
            // en los dos casos se crea el perfil pendiente y se lo vincula a la cuenta gub.uy.
            perfil = new PerfilCiudadano(cedula, email, subGubUy);
            perfiles.guardar(perfil);
            nuevo = !vincularRegistroPrevio(cedula, email, correlationId);
        } else {
            // FA1 (ya tenia perfil) o FA2 (perfil creado sin cuenta gub.uy, por ejemplo en CU-02)
            if (perfil.getSubGubUy() == null) {
                LOG.info(() -> "evento=ingreso.vinculacion correlationId=" + correlationId + " ci=" + cedula);
            } else if (!perfil.getSubGubUy().equals(subGubUy)) {
                LOG.warning(() -> "evento=ingreso.sub_distinto correlationId=" + correlationId + " ci=" + cedula
                        + " detalle=\"la cedula estaba vinculada a otra cuenta gub.uy, se actualiza\"");
            }
            perfil.setSubGubUy(subGubUy);
            if (email != null && !email.isBlank()) {
                perfil.setEmail(email);
                completarEmailRegistrosPrevios(cedula, email);
            }
        }
        perfil.setUltimoIngreso(Instant.now());

        // Paso 8 / FA1 6b-6c / FA6-FA8
        ResultadoValidacion resultado = validacionPdi.validarSiCorresponde(perfil, correlationId);

        boolean sesion = resultado == ResultadoValidacion.VALIDADO || resultado == ResultadoValidacion.NO_DISPONIBLE;
        List<RolPersona> rolesPersona = sesion ? roles.rolesDe(cedula) : List.of();
        final PerfilCiudadano p = perfil;
        final boolean esNuevo = nuevo;
        LOG.info(() -> "evento=ingreso.gubuy correlationId=" + correlationId + " ci=" + cedula + " nuevo=" + esNuevo
                + " resultado=" + resultado + " estado=" + p.getEstado() + " sesion=" + sesion
                + " roles=" + rolesPersona.stream().map(r -> r.rol() + ":" + r.nroEmpresa()).toList());
        return new ResultadoIngreso(perfil, resultado, nuevo, sesion, rolesPersona);
    }

    // FA2: existe un chofer o responsable con esa cedula creado por una empresa o un funcionario.
    // Devuelve true si habia registro previo.
    private boolean vincularRegistroPrevio(String cedula, String email, String correlationId) {
        List<Chofer> comoChofer = choferes.buscarPorCi(cedula);
        Responsable responsable = responsables.buscarPorCi(cedula);
        completarEmailRegistrosPrevios(cedula, email);
        boolean previo = !comoChofer.isEmpty() || responsable != null;
        if (previo) {
            LOG.info(() -> "evento=ingreso.vinculacion correlationId=" + correlationId + " ci=" + cedula
                    + " choferEn=" + comoChofer.size() + " responsable=" + (responsable != null));
        }
        return previo;
    }

    // Guarda el email de gub.uy en los registros de chofer/responsable que no lo tenian.
    private void completarEmailRegistrosPrevios(String cedula, String email) {
        choferes.buscarPorCi(cedula).forEach(c -> completarEmail(c, email));
        completarEmail(responsables.buscarPorCi(cedula), email);
    }

    private static void completarEmail(Ciudadano registro, String email) {
        if (registro != null && (registro.getEmail() == null || registro.getEmail().isBlank()) && email != null) {
            registro.setEmail(email); // entidad administrada: se guarda al cerrar la transaccion
        }
    }
}
