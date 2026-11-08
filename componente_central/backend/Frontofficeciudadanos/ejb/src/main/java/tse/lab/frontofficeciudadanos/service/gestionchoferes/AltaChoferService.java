package tse.lab.frontofficeciudadanos.service.gestionchoferes;

import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.RegistroConsultaPersonaDAO;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.LogCiudadanos;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ResultadoValidacion;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ValidacionPdiService;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.gestionchoferes.ResultadoAltaChofer.Resultado;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.model.Chofer;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoChofer;
import tse.lab.model.EstadoGuia;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.Ciudadano;
import tse.lab.model.RegistroConsultaPersona;

// CU-02 Dar de alta chofer en la empresa, en dos pasos:
// - consultar (pasos 2 a 7): busca o crea a la persona, la valida contra la PDI si hace falta y
//   devuelve su primer nombre y primer apellido para que el responsable confirme;
// - confirmar (pasos 8 a 10): asocia a la persona con la empresa como chofer habilitado.
// Regla de negocio: solo el responsable (validado) de una empresa habilitada puede dar de alta
// choferes, y se controla aca, no en la interfaz.
// Baja: el responsable quita un chofer de la empresa (baja logica, DADO_DE_BAJA), salvo que tenga un viaje
// asignado o en curso. Un chofer dado de baja se puede volver a agregar con el mismo alta: se reactiva la
// asociacion que ya existia (ci y empresa son unicos).
@Stateless
public class AltaChoferService {

    private static final Logger LOG = Logger.getLogger(AltaChoferService.class.getName());
    static final String OP_CONSULTA = "ALTA_CHOFER_CONSULTA";
    static final String OP_CONFIRMACION = "ALTA_CHOFER_CONFIRMACION";
    // Viajes que impiden dar de baja al chofer: el que tiene asignado y el que esta haciendo
    static final List<EstadoGuia> VIAJES_ACTIVOS = List.of(EstadoGuia.ASIGNADA, EstadoGuia.EN_CURSO);

    @EJB
    private AutorizacionResponsable autorizacion;
    @EJB
    private CiudadanoDAO perfiles;
    @EJB
    private ChoferDAO choferes;
    @EJB
    private RegistroConsultaPersonaDAO auditoria;
    @EJB
    private ValidacionPdiService validacionPdi;
    @EJB
    private GuiaDAO guias;

    public record ChoferVista(String cedula, String primerNombre, String primerApellido, String estado) {
    }

    public ResultadoAltaChofer consultar(String ciResponsable, String ciChofer, String correlationId)
            throws NoAutorizadoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);

        // Paso 3 / FA1. Uno dado de baja no cuenta: se lo puede volver a agregar.
        Chofer existente = choferes.buscarPorCiYEmpresa(ciChofer, empresa);
        if (existente != null && !existente.isDadoDeBaja()) {
            return registrar(ciResponsable, ciChofer, OP_CONSULTA, correlationId,
                    new ResultadoAltaChofer(Resultado.YA_ES_CHOFER, ciChofer, null, null, existente.getEstado().name()));
        }

        // Pasos 4 y 5: la persona puede existir por haber entrado con gub.uy o por ser chofer de otra empresa
        Ciudadano persona = perfiles.buscarPorCi(ciChofer);
        if (persona == null) {
            persona = new Ciudadano(ciChofer, null, null);
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
                asociar(existente, persona, empresa, EstadoChofer.PENDIENTE_VALIDACION);
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
        if (existente != null && !existente.isDadoDeBaja()) {
            return registrar(ciResponsable, ciChofer, OP_CONFIRMACION, correlationId,
                    new ResultadoAltaChofer(Resultado.YA_ES_CHOFER, ciChofer, null, null, existente.getEstado().name()));
        }
        // Solo se confirma a alguien que la consulta dejo validado
        Ciudadano persona = perfiles.buscarPorCi(ciChofer);
        if (persona == null || persona.getEstado() != EstadoValidacion.VALIDADO) {
            return registrar(ciResponsable, ciChofer, OP_CONFIRMACION, correlationId,
                    ResultadoAltaChofer.de(Resultado.DEBE_CONSULTAR, ciChofer));
        }

        // Paso 9: rol chofer, estado habilitado
        Chofer chofer = asociar(existente, persona, empresa, EstadoChofer.HABILITADO);
        return registrar(ciResponsable, ciChofer, OP_CONFIRMACION, correlationId,
                new ResultadoAltaChofer(Resultado.ALTA_CONFIRMADA, ciChofer, persona.getPrimerNombre(),
                        persona.getPrimerApellido(), chofer.getEstado().name()));
    }

    public List<ChoferVista> listar(String ciResponsable, String correlationId) throws NoAutorizadoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        return choferes.buscarPorEmpresa(empresa).stream().filter(c -> !c.isDadoDeBaja()).map(c -> {
            Ciudadano p = perfiles.buscarPorCi(c.getCi());
            return new ChoferVista(c.getCi(), p == null ? null : p.getPrimerNombre(),
                    p == null ? null : p.getPrimerApellido(), c.getEstado().name());
        }).toList();
    }

    // Quita al chofer de la empresa (baja logica). 404 si no es chofer de la empresa (o ya esta dado de baja);
    // 409 CHOFER_CON_VIAJE si tiene un viaje asignado o en curso: primero hay que reasignarlo o cancelarlo.
    public void darDeBaja(String ciResponsable, String ciChofer, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        Chofer chofer = choferes.buscarPorCiYEmpresa(ciChofer, empresa);
        if (chofer == null || chofer.isDadoDeBaja()) {
            throw new RecursoNoEncontradoException("La persona no es chofer de la empresa.");
        }
        if (!guias.buscarPorChoferesYEstados(List.of(chofer), VIAJES_ACTIVOS).isEmpty()) {
            throw new ConflictoException("CHOFER_CON_VIAJE", "El chofer tiene un viaje asignado o en curso. "
                    + "Reasigná o cancelá ese viaje antes de quitarlo de la empresa.");
        }
        chofer.setEstado(EstadoChofer.DADO_DE_BAJA);
        LOG.info(() -> LogCiudadanos.evento("chofer.baja").correlationId(correlationId).usuario(ciResponsable)
                .campo("nroEmpresa", empresa.getNroEmpresa()).campo("ciChofer", ciChofer).toString());
    }

    // Alta del chofer en la empresa: si ya habia estado (dado de baja) se reactiva esa asociacion; si no, se crea
    private Chofer asociar(Chofer existente, Ciudadano persona, Empresa empresa, EstadoChofer estado) {
        if (existente != null) {
            existente.reactivar(estado);
            return existente;
        }
        Chofer chofer = new Chofer(persona, empresa, estado);
        choferes.guardar(chofer);
        return chofer;
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
        LOG.info(() -> LogCiudadanos.evento("persona.consulta").correlationId(correlationId).usuario(ciUsuario)
                .campo("ciConsultada", ciConsultada).campo("operacion", operacion)
                .campo("resultado", resultado.resultado()).toString());
        return resultado;
    }
}
