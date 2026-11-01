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
    public DatosCaso registrarEvidencia(TipoCasoAutomatico tipoCaso, String idGuia, EventoFiscalizado evento) {
        if (tipoCaso == null || evento == null || evento.getOrigen() == null
                || evento.getMatricula() == null || evento.getFechaHora() == null) {
            throw new IllegalArgumentException("Faltan datos del tipo o del evento");
        }
        if (tipoCaso == TipoCasoAutomatico.DESVIO_RUTA && idGuia == null) {
            throw new IllegalArgumentException("Un desvío necesita Guía");
        }
        if (tipoCaso == TipoCasoAutomatico.VIAJE_SIN_GUIA && idGuia != null) {
            throw new IllegalArgumentException("Viaje sin Guía no debe asociarse a una Guía");
        }
        Vehiculo vehiculo = casos.buscarVehiculo(evento.getMatricula());
        if (vehiculo == null) {
            throw new IllegalArgumentException("El vehículo no existe: " + evento.getMatricula());
        }
        Guia guia = idGuia == null ? null : casos.buscarGuia(idGuia);
        if (idGuia != null && guia == null) {
            throw new IllegalArgumentException("La Guía no existe: " + idGuia);
        }
        if (guia != null && (guia.getVehiculo() == null
                || !evento.getMatricula().equals(guia.getVehiculo().getMatricula()))) {
            throw new IllegalArgumentException("La Guía no corresponde al vehículo: " + idGuia);
        }
        // Si el mismo evento ya respaldó este tipo/contexto, el reintento nunca debe
        // reabrir otro caso, aunque el original ahora esté EN_DESCARGO o resuelto.
        CasoFiscalizacion asociado = casos.buscarConEvidencia(evento.getMatricula(), idGuia,
                tipoCaso.name(), clave(evento), evento.getOrigen() == OrigenEvento.TRACKING);
        if (asociado != null) {
            return datos(asociado);
        }
        CasoFiscalizacion caso = casos.buscarAbierto(evento.getMatricula(), idGuia, tipoCaso.name());
        if (caso == null) {
            caso = new CasoFiscalizacion(tipoCaso.getDescripcion(), tipoCaso.name(), vehiculo, guia);
            asociarSiFalta(caso, evento);
            casos.guardar(caso);
            auditoria.registrar(new RegistroAuditoriaCaso(caso.getId(), RegistroAuditoriaCaso.SISTEMA,
                    AccionAuditoriaCaso.CREACION, null, EstadoCaso.DETECTADA,
                    "Detectado por fiscalización automática en evento " + evento.getOrigen() + " del "
                            + evento.getFechaHora(), null));
            Long id = caso.getId();
            LOG.info(() -> "evento=caso.creado caso=" + id + " tipo=" + tipoCaso);
        } else {
            caso = casos.buscarPorIdParaActualizar(caso.getId());
            asociarSiFalta(caso, evento);
        }
        return datos(caso);
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

    /** El bloqueo de caso y la unicidad (id_caso, clave) garantizan idempotencia. */
    private void asociarSiFalta(CasoFiscalizacion caso, EventoFiscalizado evento) {
        ClaveEvento clave = clave(evento);
        if (evento.getOrigen() == OrigenEvento.TRACKING) {
            if (!caso.getClavesEventosTracking().contains(clave)) {
                caso.getClavesEventosTracking().add(clave);
            }
            return;
        }
        EventoPesajes pesaje = casos.buscarPesaje(clave);
        if (pesaje == null) {
            throw new IllegalArgumentException("El pesaje no está guardado: " + clave.getMatricula()
                    + " " + clave.getFechaHora());
        }
        if (!caso.getEventosPesajes().stream().anyMatch(p -> p.getMatricula().equals(clave.getMatricula())
                && p.getFechaHora().equals(clave.getFechaHora()))) {
            caso.getEventosPesajes().add(pesaje);
        }
    }

    private static ClaveEvento clave(EventoFiscalizado evento) {
        return new ClaveEvento(evento.getMatricula(), evento.getFechaHora());
    }

    private static DatosCaso datos(CasoFiscalizacion caso) {
        return new DatosCaso(caso.getId(), caso.getTipoCaso(), caso.getEstado().name());
    }
}
