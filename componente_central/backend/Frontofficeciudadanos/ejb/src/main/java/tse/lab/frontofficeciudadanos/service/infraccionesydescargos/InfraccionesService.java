package tse.lab.frontofficeciudadanos.service.infraccionesydescargos;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.CasoFiscalizacionDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.LogCiudadanos;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Descargo;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EventoPesajes;
import tse.lab.model.FundamentoResolucion;
import tse.lab.model.Guia;
import tse.lab.model.RegistroAuditoriaCaso;
import tse.lab.model.Vehiculo;

// Gestion de Infracciones (frontoffice de ciudadanos): el responsable de la empresa consulta los casos de
// fiscalizacion de sus vehiculos con la evidencia que los origino y presenta un descargo dentro del plazo.
// - La empresa ve el caso recien cuando se le notifica (NOTIFICADA en adelante), nunca DETECTADA.
// - El descargo se presenta una sola vez, con el caso NOTIFICADA y dentro de PLAZO_DESCARGO_DIAS dias
//   corridos (10 por defecto) desde la notificacion. Pasa el caso a EN_DESCARGO (la transicion la controla
//   CasoFiscalizacion) y queda en la pista de auditoria con la cedula de quien lo presento.
// - No se exige que la empresa este habilitada: una empresa inhabilitada igual tiene que poder defenderse.
// - Cada caso notificado es una notificacion para la empresa (la campana del front): queda vista cuando el
//   responsable la marca o abre el detalle del caso.
@Stateless
public class InfraccionesService {

    private static final Logger LOG = Logger.getLogger(InfraccionesService.class.getName());
    private static final Set<EstadoCaso> VISIBLES = Set.of(EstadoCaso.NOTIFICADA, EstadoCaso.EN_DESCARGO,
            EstadoCaso.CONFIRMADA, EstadoCaso.DESESTIMADA);
    static final int MAX_NOTIFICACIONES = 10;

    @EJB
    private AutorizacionResponsable autorizacion;
    @EJB
    private CasoFiscalizacionDAO casos;

    private final int plazoDias;

    public InfraccionesService() {
        this(plazoConfigurado());
    }

    // Para los tests: sin leer el entorno
    InfraccionesService(int plazoDias) {
        this.plazoDias = plazoDias;
    }

    public record InfraccionResumen(Long id, String tipoCaso, String descripcion, String estado,
            String fechaDeteccion, String matricula, boolean nueva, String venceDescargo, boolean admiteDescargo) {
    }

    public record GuiaVista(String id, String fecha, String origen, String destino, String rubroCliente,
            long pesoCargaKg) {
    }

    public record VehiculoVista(String matricula, String marca, String modelo, long pesoKg, long capacidadCargaKg,
            long pesoMaximoKg) {
    }

    public record PesajeVista(String fechaHora, String balanza, long pesoKg, long excesoKg) {
    }

    public record DescargoVista(String descripcion, String fechaPresentacion, String ciPresentante) {
    }

    // decision: CONFIRMADA o DESESTIMADA
    public record ResolucionVista(String decision, String fundamento, String fechaHora) {
    }

    public record InfraccionDetalle(InfraccionResumen infraccion, GuiaVista guia, VehiculoVista vehiculo,
            List<PesajeVista> pesajes, int eventosTracking, DescargoVista descargo, ResolucionVista resolucion,
            int plazoDescargoDias) {
    }

    public record NuevoDescargo(String descripcion) {
    }

    // fecha: cuando se le notifico el caso a la empresa
    public record Notificacion(Long id, String tipoCaso, String matricula, String estado, String fecha,
            boolean vista, boolean admiteDescargo, String venceDescargo) {
    }

    public record Notificaciones(int noVistas, List<Notificacion> notificaciones) {
    }

    public List<InfraccionResumen> listar(String ciResponsable, String correlationId) throws NoAutorizadoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, false, correlationId);
        List<InfraccionResumen> lista = casos.buscarPorEmpresa(empresa, VISIBLES).stream().map(this::resumen)
                .toList();
        LOG.info(() -> LogCiudadanos.evento("infracciones.listado").correlationId(correlationId)
                .usuario(ciResponsable).campo("nroEmpresa", empresa.getNroEmpresa()).campo("casos", lista.size())
                .toString());
        return lista;
    }

    // Al abrir el detalle la notificacion queda vista
    public InfraccionDetalle detalle(String ciResponsable, Long idCaso, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, false, correlationId);
        CasoFiscalizacion caso = casoDeLaEmpresa(empresa, idCaso == null ? null : casos.buscarPorId(idCaso));
        InfraccionDetalle detalle = detalle(caso);
        marcarVista(caso, ciResponsable, correlationId);
        return detalle;
    }

    // Notificaciones de casos para la campana del encabezado: cuantas no vio la empresa y las ultimas
    // MAX_NOTIFICACIONES (primero las no vistas, despues por fecha de notificacion). El front la consulta
    // seguido, por eso no se loguea.
    public Notificaciones notificaciones(String ciResponsable, String correlationId) throws NoAutorizadoException {
        return notificaciones(autorizacion.empresaDelResponsable(ciResponsable, false, correlationId));
    }

    public Notificaciones marcarVista(String ciResponsable, Long idCaso, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, false, correlationId);
        marcarVista(casoDeLaEmpresa(empresa, idCaso == null ? null : casos.buscarPorId(idCaso)), ciResponsable,
                correlationId);
        return notificaciones(empresa);
    }

    public Notificaciones marcarTodasVistas(String ciResponsable, String correlationId) throws NoAutorizadoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, false, correlationId);
        for (CasoFiscalizacion caso : casos.buscarPorEmpresa(empresa, VISIBLES)) {
            marcarVista(caso, ciResponsable, correlationId);
        }
        return notificaciones(empresa);
    }

    private Notificaciones notificaciones(Empresa empresa) {
        Map<Long, Instant> fechas = new HashMap<>();
        List<CasoFiscalizacion> lista = casos.buscarPorEmpresa(empresa, VISIBLES);
        for (CasoFiscalizacion caso : lista) {
            Instant notificada = casos.fechaNotificacion(caso.getId());
            fechas.put(caso.getId(), notificada != null ? notificada : caso.getFechaDeteccion());
        }
        List<Notificacion> todas = lista.stream()
                .sorted(Comparator.comparing(CasoFiscalizacion::isNotificacionVista)
                        .thenComparing(c -> fechas.get(c.getId()), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> notificacion(c, fechas.get(c.getId())))
                .toList();
        int noVistas = (int) todas.stream().filter(n -> !n.vista()).count();
        return new Notificaciones(noVistas, todas.stream().limit(MAX_NOTIFICACIONES).toList());
    }

    private Notificacion notificacion(CasoFiscalizacion caso, Instant fecha) {
        InfraccionResumen r = resumen(caso);
        return new Notificacion(caso.getId(), caso.getTipoCaso(), r.matricula(), r.estado(), texto(fecha),
                caso.isNotificacionVista(), r.admiteDescargo(), r.venceDescargo());
    }

    private static void marcarVista(CasoFiscalizacion caso, String ciResponsable, String correlationId) {
        if (!caso.isNotificacionVista()) {
            caso.marcarNotificacionVista();
            LOG.info(() -> LogCiudadanos.evento("infraccion.notificacion_vista").correlationId(correlationId)
                    .usuario(ciResponsable).campo("caso", caso.getId()).toString());
        }
    }

    public InfraccionDetalle presentarDescargo(String ciResponsable, Long idCaso, NuevoDescargo datos,
            String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, DatosInvalidosException, ConflictoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, false, correlationId);
        CasoFiscalizacion caso = casoDeLaEmpresa(empresa,
                idCaso == null ? null : casos.buscarPorIdParaActualizar(idCaso));

        String texto = datos == null || datos.descripcion() == null ? "" : datos.descripcion().strip();
        if (texto.isEmpty()) {
            throw new DatosInvalidosException(Map.of("descripcion", "Escribí el descargo."));
        }
        if (texto.length() > Descargo.MAX_DESCRIPCION) {
            throw new DatosInvalidosException(Map.of("descripcion",
                    "El descargo puede tener hasta " + Descargo.MAX_DESCRIPCION + " caracteres."));
        }
        if (caso.getDescargo() != null) {
            throw rechazo(caso, ciResponsable, correlationId, "DESCARGO_YA_PRESENTADO",
                    "Ya se presentó un descargo para esta infracción.");
        }
        if (caso.getEstado() != EstadoCaso.NOTIFICADA) {
            throw rechazo(caso, ciResponsable, correlationId, "TRANSICION_NO_PERMITIDA",
                    "La infracción ya no admite descargo (estado " + caso.getEstado() + ").");
        }
        if (!Instant.now().isBefore(venceDescargo(caso))) {
            throw rechazo(caso, ciResponsable, correlationId, "PLAZO_VENCIDO",
                    "Venció el plazo de " + plazoDias + " días para presentar el descargo.");
        }

        Descargo descargo = Descargo.presentar(texto, caso, ciResponsable);
        casos.guardarDescargo(descargo);
        caso.setDescargo(descargo);
        caso.cambiarEstado(EstadoCaso.EN_DESCARGO);
        casos.registrarAuditoria(new RegistroAuditoriaCaso(caso.getId(), ciResponsable,
                AccionAuditoriaCaso.CAMBIO_ESTADO, EstadoCaso.NOTIFICADA, EstadoCaso.EN_DESCARGO,
                "Descargo presentado por la empresa N° " + empresa.getNroEmpresa(), correlationId));
        LOG.info(() -> LogCiudadanos.evento("infraccion.descargo").correlationId(correlationId)
                .usuario(ciResponsable).campo("nroEmpresa", empresa.getNroEmpresa()).campo("caso", idCaso)
                .campo("caracteres", texto.length()).toString());
        return detalle(caso);
    }

    // Un caso de otra empresa (o todavia no notificado) se trata igual que uno que no existe
    private static CasoFiscalizacion casoDeLaEmpresa(Empresa empresa, CasoFiscalizacion caso)
            throws RecursoNoEncontradoException {
        Vehiculo v = caso == null ? null : caso.getVehiculo();
        if (v == null || v.getEmpresa() == null || !v.getEmpresa().getNroEmpresa().equals(empresa.getNroEmpresa())
                || !VISIBLES.contains(caso.getEstado())) {
            throw new RecursoNoEncontradoException("La infracción no existe.");
        }
        return caso;
    }

    private ConflictoException rechazo(CasoFiscalizacion caso, String ci, String correlationId, String codigo,
            String mensaje) {
        LOG.info(() -> LogCiudadanos.evento("infraccion.descargo_rechazado").correlationId(correlationId)
                .usuario(ci).campo("caso", caso.getId()).campo("motivo", codigo).toString());
        return new ConflictoException(codigo, mensaje);
    }

    // El plazo corre desde la notificacion (pista de auditoria); si no quedo registrada, desde la deteccion
    Instant venceDescargo(CasoFiscalizacion caso) {
        Instant notificada = casos.fechaNotificacion(caso.getId());
        return (notificada != null ? notificada : caso.getFechaDeteccion()).plus(Duration.ofDays(plazoDias));
    }

    private InfraccionResumen resumen(CasoFiscalizacion caso) {
        Instant vence = venceDescargo(caso);
        boolean admite = caso.getEstado() == EstadoCaso.NOTIFICADA && caso.getDescargo() == null
                && Instant.now().isBefore(vence);
        return new InfraccionResumen(caso.getId(), caso.getTipoCaso(), caso.getDescripcion(),
                caso.getEstado().name(), texto(caso.getFechaDeteccion()),
                caso.getVehiculo() == null ? null : caso.getVehiculo().getMatricula(), !caso.isNotificacionVista(),
                caso.getEstado() == EstadoCaso.NOTIFICADA ? texto(vence) : null, admite);
    }

    private InfraccionDetalle detalle(CasoFiscalizacion caso) {
        Vehiculo v = caso.getVehiculo();
        long pesoMaximo = v == null ? 0 : v.getPeso() + v.getCapacidadCargaKg();
        Guia g = caso.getGuia();
        List<PesajeVista> pesajes = caso.getEventosPesajes().stream()
                .sorted(Comparator.comparing(EventoPesajes::getFechaHora,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new PesajeVista(texto(p.getFechaHora()),
                        p.getNodoBalanza() == null ? null : p.getNodoBalanza().getNombre(), p.getPeso(),
                        v == null ? 0 : Math.max(0, p.getPeso() - pesoMaximo)))
                .toList();
        Descargo d = caso.getDescargo();
        FundamentoResolucion f = caso.getFundamentoResolucion();
        boolean resuelto = caso.getEstado() == EstadoCaso.CONFIRMADA || caso.getEstado() == EstadoCaso.DESESTIMADA;
        return new InfraccionDetalle(resumen(caso),
                g == null ? null
                        : new GuiaVista(g.getId(), texto(g.getFecha()), g.getOrigen(), g.getDestino(),
                                g.getRubroCliente(), g.getPesoCargaKg()),
                v == null ? null
                        : new VehiculoVista(v.getMatricula(), v.getMarca(), v.getModelo(), v.getPeso(),
                                v.getCapacidadCargaKg(), pesoMaximo),
                pesajes, caso.getClavesEventosTracking().size(),
                d == null ? null : new DescargoVista(d.getDescripcion(), texto(d.getFechaPresentacion()),
                        d.getCiPresentante()),
                resuelto ? new ResolucionVista(caso.getEstado().name(), f == null ? null : f.getDescripcion(),
                        f == null ? null : texto(f.getFechaHora())) : null,
                plazoDias);
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static int plazoConfigurado() {
        String valor = System.getenv("PLAZO_DESCARGO_DIAS");
        try {
            int dias = valor == null || valor.isBlank() ? 10 : Integer.parseInt(valor.trim());
            return dias > 0 ? dias : 10;
        } catch (NumberFormatException e) {
            return 10;
        }
    }
}
