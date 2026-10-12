package tse.lab.frontofficeciudadanos.service.adaptadorpdi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.model.Chofer;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.PerfilCiudadano;

// CU-03 Validar ciudadano contra PDI. Lo incluyen CU-01 (ingreso con gub.uy), CU-02 (alta de
// chofer) y CU-04 (reintento de validaciones pendientes).
@Stateless
public class ValidacionPdiService {

    private static final Logger LOG = Logger.getLogger(ValidacionPdiService.class.getName());

    @Inject
    private ClientePdi clientePdi;

    @EJB
    private PerfilCiudadanoDAO perfiles;

    @EJB
    private ChoferDAO choferes;

    // Lo que CU-01 y CU-02 hacen con una persona que ya tiene registro: VALIDADO no consulta la
    // PDI; PENDIENTE_VALIDACION la consulta; RECHAZADO es NO_EXISTE; NO_HABILITADO es MENOR_DE_EDAD
    // salvo que ya haya cumplido 18 desde la ultima validacion, en cuyo caso se vuelve a consultar.
    public ResultadoValidacion validarSiCorresponde(PerfilCiudadano perfil, String correlationId) {
        return switch (perfil.getEstado()) {
            case VALIDADO -> ResultadoValidacion.VALIDADO;
            case PENDIENTE_VALIDACION -> validar(perfil, correlationId);
            case RECHAZADO -> ResultadoValidacion.NO_EXISTE;
            case NO_HABILITADO -> MayoriaDeEdad.cumple(perfil.getFechaNacimiento())
                    ? validar(perfil, correlationId)
                    : ResultadoValidacion.MENOR_DE_EDAD;
        };
    }

    public ResultadoValidacion validar(PerfilCiudadano perfil, String correlationId) {
        ResultadoValidacion resultado;
        Optional<DatosPersonaPdi> datos;
        try {
            datos = clientePdi.consultarPersona(perfil.getCi(), correlationId);
        } catch (PdiNoDisponibleException e) {
            // FA3: queda pendiente; CU-04 la vuelve a intentar
            perfil.setEstado(EstadoValidacion.PENDIENTE_VALIDACION);
            perfiles.actualizar(perfil);
            log(correlationId, perfil, ResultadoValidacion.NO_DISPONIBLE);
            return ResultadoValidacion.NO_DISPONIBLE;
        }

        perfil.setUltimaValidacion(Instant.now());
        if (datos.isEmpty()) {
            // FA1: no existe en DNIC
            perfil.setEstado(EstadoValidacion.RECHAZADO);
            resultado = ResultadoValidacion.NO_EXISTE;
        } else {
            DatosPersonaPdi persona = datos.get();
            perfil.setPrimerNombre(persona.primerNombre());
            perfil.setPrimerApellido(persona.primerApellido());
            perfil.setNombres(persona.nombres());
            perfil.setApellidos(persona.apellidos());
            perfil.setFechaNacimiento(persona.fechaNacimiento());
            if (MayoriaDeEdad.cumple(persona.fechaNacimiento())) {
                perfil.setEstado(EstadoValidacion.VALIDADO);
                resultado = ResultadoValidacion.VALIDADO;
            } else {
                // FA2: menor de edad
                perfil.setEstado(EstadoValidacion.NO_HABILITADO);
                resultado = ResultadoValidacion.MENOR_DE_EDAD;
            }
        }
        perfiles.actualizar(perfil);
        log(correlationId, perfil, resultado);

        if (resultado == ResultadoValidacion.VALIDADO) {
            // CU-02 FA5 6c: las asociaciones como chofer que esperaban la validacion quedan habilitadas
            List<Chofer> habilitados = choferes.habilitarPendientes(perfil.getCi());
            if (!habilitados.isEmpty()) {
                LOG.info(() -> "evento=chofer.habilitado correlationId=" + correlationId + " ci=" + perfil.getCi()
                        + " empresas=" + habilitados.stream().map(c -> c.getEmpresa().getNroEmpresa()).toList());
            }
        }
        return resultado;
    }

    private static void log(String correlationId, PerfilCiudadano perfil, ResultadoValidacion resultado) {
        LOG.log(resultado == ResultadoValidacion.NO_DISPONIBLE ? Level.WARNING : Level.INFO,
                () -> "evento=pdi.validacion correlationId=" + correlationId + " ci=" + perfil.getCi()
                        + " resultado=" + resultado + " estado=" + perfil.getEstado());
    }
}
