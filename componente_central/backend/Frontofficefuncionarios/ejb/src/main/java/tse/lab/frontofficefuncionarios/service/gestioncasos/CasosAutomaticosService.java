package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficefuncionarios.data.AuditoriaCasoDAO;
import tse.lab.frontofficefuncionarios.data.CasoFiscalizacionDAO;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.ClaveEvento;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EventoPesajes;
import tse.lab.model.Guia;
import tse.lab.model.RegistroAuditoriaCaso;
import tse.lab.model.Vehiculo;

// Operaciones de la fiscalizacion automatica sobre los casos. No decide nada: que caso crear o a cual sumar un
// evento lo resuelve la fiscalizacion automatica con la jerarquia de situaciones. Lo que la base garantiza:
// un evento es evidencia de un unico caso y un vehiculo no tiene dos casos abiertos del mismo tipo.
@Stateless
public class CasosAutomaticosService implements CasosAutomaticosRemote {

    private static final Logger LOG = Logger.getLogger(CasosAutomaticosService.class.getName());

    @EJB
    private CasoFiscalizacionDAO casos;
    @EJB
    private AuditoriaCasoDAO auditoria;

    @Override
    public List<DatosCaso> casosAbiertos(String matricula) {
        return casos.abiertosDelVehiculo(matricula).stream().map(CasosAutomaticosService::datos).toList();
    }

    @Override
    public DatosCaso casoDelEvento(EventoFiscalizado evento) {
        ClaveEvento clave = clave(evento);
        CasoFiscalizacion caso = evento.getOrigen() == OrigenEvento.TRACKING
                ? casos.casoConEventoTracking(clave)
                : casos.casoConPesaje(clave);
        return caso == null ? null : datos(caso);
    }

    @Override
    public DatosCaso crearCaso(TipoCasoAutomatico tipoCaso, String idGuia, EventoFiscalizado evento) {
        if (tipoCaso == null) {
            throw new IllegalArgumentException("Falta el tipo de caso");
        }
        Vehiculo vehiculo = casos.buscarVehiculo(evento.getMatricula());
        if (vehiculo == null) {
            throw new IllegalArgumentException("El vehiculo no existe: " + evento.getMatricula());
        }
        Guia guia = null;
        if (idGuia != null) {
            guia = casos.buscarGuia(idGuia);
            if (guia == null) {
                throw new IllegalArgumentException("La guia no existe: " + idGuia);
            }
        }
        CasoFiscalizacion caso = new CasoFiscalizacion(tipoCaso.getDescripcion(), tipoCaso.name(), vehiculo, guia);
        asociar(caso, evento);
        casos.guardar(caso);
        auditoria.registrar(new RegistroAuditoriaCaso(caso.getId(), RegistroAuditoriaCaso.SISTEMA,
                AccionAuditoriaCaso.CREACION, null, EstadoCaso.DETECTADA, "Detectado por la fiscalizacion"
                        + " automatica en un evento de " + evento.getOrigen() + " del " + evento.getFechaHora(),
                null));
        LOG.info(() -> "evento=caso.creado caso=" + caso.getId() + " tipo=" + tipoCaso + " matricula="
                + evento.getMatricula() + " fechaHora=" + evento.getFechaHora());
        return datos(caso);
    }

    @Override
    public void agregarEvidencia(long idCaso, EventoFiscalizado evento) {
        CasoFiscalizacion caso = casos.buscarPorId(idCaso);
        if (caso == null) {
            throw new IllegalArgumentException("El caso no existe: " + idCaso);
        }
        asociar(caso, evento);
        LOG.info(() -> "evento=caso.evidencia_agregada caso=" + idCaso + " matricula=" + evento.getMatricula()
                + " fechaHora=" + evento.getFechaHora());
    }

    @Override
    public DatosCaso notificarCaso(long idCaso) {
        CasoFiscalizacion caso = casos.buscarPorIdParaActualizar(idCaso);
        if (caso == null) {
            throw new IllegalArgumentException("El caso no existe: " + idCaso);
        }
        if (caso.getEstado() == EstadoCaso.DETECTADA) {
            caso.cambiarEstado(EstadoCaso.NOTIFICADA);
            Long nroEmpresa = caso.getVehiculo() == null || caso.getVehiculo().getEmpresa() == null ? null
                    : caso.getVehiculo().getEmpresa().getNroEmpresa();
            auditoria.registrar(new RegistroAuditoriaCaso(idCaso, RegistroAuditoriaCaso.SISTEMA,
                    AccionAuditoriaCaso.CAMBIO_ESTADO, EstadoCaso.DETECTADA, EstadoCaso.NOTIFICADA,
                    "Caso notificado a la empresa N° " + nroEmpresa, null));
            LOG.info(() -> "evento=caso.notificado caso=" + idCaso + " empresa=" + nroEmpresa);
        }
        return datos(caso);
    }

    private void asociar(CasoFiscalizacion caso, EventoFiscalizado evento) {
        ClaveEvento clave = clave(evento);
        if (evento.getOrigen() == OrigenEvento.TRACKING) {
            caso.getClavesEventosTracking().add(clave);
            return;
        }
        EventoPesajes pesaje = casos.buscarPesaje(clave);
        if (pesaje == null) {
            throw new IllegalArgumentException("El pesaje no esta guardado: " + clave.getMatricula() + " "
                    + clave.getFechaHora());
        }
        caso.getEventosPesajes().add(pesaje);
    }

    private static ClaveEvento clave(EventoFiscalizado evento) {
        return new ClaveEvento(evento.getMatricula(), evento.getFechaHora());
    }

    private static DatosCaso datos(CasoFiscalizacion caso) {
        return new DatosCaso(caso.getId(), caso.getTipoCaso(), caso.getEstado().name());
    }
}
