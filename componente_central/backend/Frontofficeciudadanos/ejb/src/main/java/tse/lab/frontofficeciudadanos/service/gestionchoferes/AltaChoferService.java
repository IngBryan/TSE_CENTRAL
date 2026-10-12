package tse.lab.frontofficeciudadanos.service.gestionchoferes;

import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.RegistroConsultaPersonaDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.EstadoChofer;
import tse.lab.frontofficeciudadanos.model.EstadoValidacion;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.model.RegistroConsultaPersona;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ResultadoValidacion;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ValidacionPdiService;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.gestionchoferes.ResultadoAltaChofer.Resultado;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;

// CU-02 Dar de alta chofer en la empresa, en dos pasos:
// - consultar (pasos 2 a 7): busca o crea a la persona, la valida contra la PDI si hace falta y
//   devuelve su primer nombre y primer apellido para que el responsable confirme;
// - confirmar (pasos 8 a 10): asocia a la persona con la empresa como chofer habilitado.
// Regla de negocio: solo el responsable (validado) de una empresa habilitada puede dar de alta
// choferes, y se controla aca, no en la interfaz.
@Stateless
public class AltaChoferService {

    private static final Logger LOG = Logger.getLogger(AltaChoferService.class.getName());
    static final String OP_CONSULTA = "ALTA_CHOFER_CONSULTA";
    static final String OP_CONFIRMACION = "ALTA_CHOFER_CONFIRMACION";

    @EJB
    private AutorizacionResponsable autorizacion;
    @EJB
    private PerfilCiudadanoDAO perfiles;
    @EJB
    private ChoferDAO choferes;
    @EJB
    private RegistroConsultaPersonaDAO auditoria;
    @EJB
    private ValidacionPdiService validacionPdi;

    public record ChoferVista(String cedula, String primerNombre, String primerApellido, String estado) {
    }

    public ResultadoAltaChofer consultar(String ciResponsable, String ciChofer, String correlationId)
            throws NoAutorizadoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);

        // Paso 3 / FA1
        Chofer existente = choferes.buscarPorCiYEmpresa(ciChofer, empresa);
        if (existente != null) {
            return registrar(ciResponsable, ciChofer, OP_CONSULTA, correlationId,
                    new ResultadoAltaChofer(Resultado.YA_ES_CHOFER, ciChofer, null, null, existente.getEstado().name()));
        }

        // Pasos 4 y 5: la persona puede existir por haber entrado con gub.uy o por ser chofer de otra empresa
        PerfilCiudadano persona = perfiles.buscarPorCi(ciChofer);
        if (persona == null) {
            persona = new PerfilCiudadano(ciChofer, null, null);
            perfiles.guardar(persona);
        }

        // Paso 6 (CU-03) / FA2 (ya validada: no se consulta la PDI) / FA3 (pendiente: se consulta)
        ResultadoValidacion validacion = validacionPdi.validarSiCorresponde(persona, correlationId);
        ResultadoAltaChofer resultado = switch (validacion) {
            case VALIDADO -> new ResultadoAltaChofer(Resultado.CONFIRMAR, ciChofer, persona.getPrimerNombre(),
                    persona.getPrimerApellido(), null);
            case NO_EXISTE -> ResultadoAltaChofer.de(Resultado.NO_EXISTE, ciChofer);
            case MENOR_DE_EDAD -> ResultadoAltaChofer.de(Resultado.MENOR_DE_EDAD, ciChofer);
            case NO_DISPONIBLE -> {
                // FA5: no hay nombre para confirmar; se asocia pendiente y no se puede asignar a viajes
                choferes.guardar(new Chofer(ciChofer, persona.getEmail(), empresa, EstadoChofer.PENDIENTE_VALIDACION));
                yield new ResultadoAltaChofer(Resultado.ASOCIADO_PENDIENTE, ciChofer, null, null,
                        EstadoChofer.PENDIENTE_VALIDACION.name());
            }
        };
        return registrar(ciResponsable, ciChofer, OP_CONSULTA, correlationId, resultado);
    }

    public ResultadoAltaChofer confirmar(String ciResponsable, String ciChofer, String correlationId)
            throws NoAutorizadoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);

        Chofer existente = choferes.buscarPorCiYEmpresa(ciChofer, empresa);
        if (existente != null) {
            return registrar(ciResponsable, ciChofer, OP_CONFIRMACION, correlationId,
                    new ResultadoAltaChofer(Resultado.YA_ES_CHOFER, ciChofer, null, null, existente.getEstado().name()));
        }
        // Solo se confirma a alguien que la consulta dejo validado
        PerfilCiudadano persona = perfiles.buscarPorCi(ciChofer);
        if (persona == null || persona.getEstado() != EstadoValidacion.VALIDADO) {
            return registrar(ciResponsable, ciChofer, OP_CONFIRMACION, correlationId,
                    ResultadoAltaChofer.de(Resultado.DEBE_CONSULTAR, ciChofer));
        }

        // Paso 9: rol chofer, estado habilitado
        Chofer chofer = new Chofer(ciChofer, persona.getEmail(), empresa, EstadoChofer.HABILITADO);
        choferes.guardar(chofer);
        return registrar(ciResponsable, ciChofer, OP_CONFIRMACION, correlationId,
                new ResultadoAltaChofer(Resultado.ALTA_CONFIRMADA, ciChofer, persona.getPrimerNombre(),
                        persona.getPrimerApellido(), chofer.getEstado().name()));
    }

    public List<ChoferVista> listar(String ciResponsable, String correlationId) throws NoAutorizadoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        return choferes.buscarPorEmpresa(empresa).stream().map(c -> {
            PerfilCiudadano p = perfiles.buscarPorCi(c.getCi());
            return new ChoferVista(c.getCi(), p == null ? null : p.getPrimerNombre(),
                    p == null ? null : p.getPrimerApellido(), c.getEstado().name());
        }).toList();
    }

    // Precondiciones de CU-02: responsable autenticado y validado, empresa habilitada.
    private Empresa empresaDelResponsable(String ciResponsable, String correlationId) throws NoAutorizadoException {
        return autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
    }

    // Ley 18.331: cada consulta queda registrada con usuario, cedula consultada y fecha y hora.
    private ResultadoAltaChofer registrar(String ciUsuario, String ciConsultada, String operacion, String correlationId,
            ResultadoAltaChofer resultado) {
        auditoria.registrar(new RegistroConsultaPersona(ciUsuario, ciConsultada, operacion,
                resultado.resultado().name(), correlationId));
        LOG.info(() -> "evento=persona.consulta correlationId=" + correlationId + " usuario=" + ciUsuario
                + " ciConsultada=" + ciConsultada + " operacion=" + operacion + " resultado=" + resultado.resultado());
        return resultado;
    }
}
