package tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficefuncionarios.data.EmpresaDAO;
import tse.lab.frontofficefuncionarios.data.ITVDAO;
import tse.lab.frontofficefuncionarios.data.PermisoCirculacionDAO;
import tse.lab.frontofficefuncionarios.data.VehiculoDAO;
import tse.lab.frontofficefuncionarios.service.LogFuncionarios;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.model.Empresa;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Vehiculo;

// CU Fiscalizacion de los permisos y estado de los vehiculos de las empresas (funcionario): listado por
// empresa de sus vehiculos con el periodo de validez del permiso nacional de circulacion y de la ITV, y el
// detalle de un vehiculo con su historial de ITV. Solo lectura. Cada operacion verifica primero que quien
// llama sea funcionario: si no, se rechaza sin leer nada.
// - La vigencia es la del modelo (PermisoCirculacion.vigenteEn, ITV.vigenteEn, Vehiculo.habilitadoEn): la
//   misma que ve la empresa en su ABM de vehiculos.
// - POR_VENCER es solo un aviso: el documento sigue vigente y el vehiculo habilitado.
@Stateless
public class FiscalizacionHabilitacionesService {

    private static final Logger LOG = Logger.getLogger(FiscalizacionHabilitacionesService.class.getName());
    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");
    // Un documento vigente que vence dentro de estos dias (o menos) se informa POR_VENCER
    static final int DIAS_POR_VENCER = 30;

    @EJB
    private AutorizacionFuncionario autorizacion;
    @EJB
    private EmpresaDAO empresas;
    @EJB
    private VehiculoDAO vehiculos;
    @EJB
    private PermisoCirculacionDAO permisos;
    @EJB
    private ITVDAO itvs;

    private final Clock reloj;

    public FiscalizacionHabilitacionesService() {
        this(Clock.system(ZONA));
    }

    // For tests: fixes the day of the query
    FiscalizacionHabilitacionesService(Clock reloj) {
        this.reloj = reloj;
    }

    public enum EstadoDocumento {
        VIGENTE, POR_VENCER, VENCIDO, AUN_NO_VIGENTE, SIN_REGISTRO
    }

    public enum EstadoVehiculo {
        HABILITADO, NO_HABILITADO, DADO_DE_BAJA
    }

    // Periodo de validez de un permiso o una ITV. nro: solo el permiso tiene numero. Sin registro: estado
    // SIN_REGISTRO y el resto null. diasParaVencer: de hoy al vencimiento (negativo si ya vencio).
    // Fechas en formato ISO (yyyy-MM-dd).
    public record PeriodoVista(String nro, String desde, String hasta, EstadoDocumento estado,
            Long diasParaVencer) {
    }

    private static final PeriodoVista SIN_REGISTRO = new PeriodoVista(null, null, null,
            EstadoDocumento.SIN_REGISTRO, null);

    // itv: la de vencimiento mas lejano
    public record VehiculoFiscalizado(String matricula, String marca, String modelo, EstadoVehiculo estado,
            PeriodoVista permiso, PeriodoVista itv) {
    }

    // porVencer: habilitados con el permiso o la ITV POR_VENCER
    public record ResumenHabilitaciones(int total, int habilitados, int noHabilitados, int dadosDeBaja,
            int porVencer) {

        public static final ResumenHabilitaciones SIN_VEHICULOS = new ResumenHabilitaciones(0, 0, 0, 0, 0);
    }

    // vehiculos: primero los no habilitados, despues los habilitados y al final los dados de baja; dentro de
    // cada grupo, por matricula. fecha: el dia contra el que se calculo la vigencia.
    public record FiscalizacionEmpresa(Long nroEmpresa, String nombrePublico, boolean habilitada, String fecha,
            ResumenHabilitaciones resumen, List<VehiculoFiscalizado> vehiculos) {
    }

    // itvs: el historial completo, la de vencimiento mas lejano primero
    public record VehiculoDetalle(Long nroEmpresa, String fecha, VehiculoFiscalizado vehiculo,
            List<PeriodoVista> itvs) {
    }

    public FiscalizacionEmpresa vehiculosDeEmpresa(String ci, Long nroEmpresa, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        Empresa empresa = empresa(nroEmpresa, ci, correlationId);
        LocalDate hoy = LocalDate.now(reloj);

        // Tres consultas para toda la empresa, agrupadas aca por matricula. Los vehiculos van primero: el
        // permiso y la ITV los cargan (@ManyToOne / @OneToOne) y asi ya estan en el contexto de persistencia,
        // en vez de buscarse de a uno.
        List<Vehiculo> deLaEmpresa = vehiculos.buscarPorEmpresa(empresa);
        Map<String, PermisoCirculacion> permisoPorMatricula = permisos.buscarPorEmpresa(empresa).stream()
                .collect(Collectors.toMap(p -> p.getVehiculo().getMatricula(), Function.identity()));
        Map<String, List<ITV>> itvsPorMatricula = itvs.buscarPorEmpresa(empresa).stream()
                .collect(Collectors.groupingBy(i -> i.getVehiculo().getMatricula()));
        List<VehiculoFiscalizado> lista = deLaEmpresa.stream()
                .map(v -> fiscalizar(v, permisoPorMatricula.get(v.getMatricula()),
                        itvsPorMatricula.getOrDefault(v.getMatricula(), List.of()), hoy))
                .sorted(Comparator.comparing(VehiculoFiscalizado::estado, Comparator.comparingInt(
                        FiscalizacionHabilitacionesService::orden)).thenComparing(VehiculoFiscalizado::matricula))
                .toList();

        ResumenHabilitaciones resumen = resumen(lista);
        LOG.info(() -> LogFuncionarios.evento("empresa.vehiculos.listado").correlationId(correlationId).usuario(ci)
                .campo("nroEmpresa", nroEmpresa).campo("cantidad", resumen.total())
                .campo("noHabilitados", resumen.noHabilitados()).campo("porVencer", resumen.porVencer()).toString());
        return new FiscalizacionEmpresa(empresa.getNroEmpresa(), empresa.getNombrePublico(), empresa.isHabilitada(),
                hoy.toString(), resumen, lista);
    }

    // Resumen de los vehiculos de cada empresa, para el listado de empresas. Solo estan las empresas con
    // vehiculos. Tres consultas en total, no tres por empresa: si las empresas ya se cargaron en la misma
    // transaccion (GestionEmpresasService.listar), los vehiculos las toman del contexto de persistencia.
    public Map<Long, ResumenHabilitaciones> resumenPorEmpresa(String ci, String correlationId)
            throws NoAutorizadoException {
        autorizacion.funcionario(ci, correlationId);
        LocalDate hoy = LocalDate.now(reloj);
        List<Vehiculo> todos = vehiculos.listar();
        Map<String, PermisoCirculacion> permisoPorMatricula = permisos.listar().stream()
                .collect(Collectors.toMap(p -> p.getVehiculo().getMatricula(), Function.identity()));
        Map<String, List<ITV>> itvsPorMatricula = itvs.listar().stream()
                .collect(Collectors.groupingBy(i -> i.getVehiculo().getMatricula()));
        return todos.stream().collect(Collectors.groupingBy(v -> v.getEmpresa().getNroEmpresa(),
                Collectors.collectingAndThen(Collectors.mapping(v -> fiscalizar(v,
                        permisoPorMatricula.get(v.getMatricula()),
                        itvsPorMatricula.getOrDefault(v.getMatricula(), List.of()), hoy), Collectors.toList()),
                        FiscalizacionHabilitacionesService::resumen)));
    }

    // 404 VEHICULO_NO_ENCONTRADO si la matricula no existe o es de otra empresa
    public VehiculoDetalle vehiculo(String ci, Long nroEmpresa, String matricula, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        Empresa empresa = empresa(nroEmpresa, ci, correlationId);
        String normalizada = Vehiculo.normalizarMatricula(matricula);
        Vehiculo v = normalizada == null || normalizada.isEmpty() ? null : vehiculos.buscarPorMatricula(normalizada);
        if (v == null || v.getEmpresa() == null || !empresa.getNroEmpresa().equals(v.getEmpresa().getNroEmpresa())) {
            LOG.info(() -> LogFuncionarios.evento("empresa.vehiculo.no_encontrado").correlationId(correlationId).usuario(ci)
                    .campo("nroEmpresa", nroEmpresa).campo("matricula", normalizada).toString());
            throw new RecursoNoEncontradoException("VEHICULO_NO_ENCONTRADO",
                    "La empresa no tiene un vehículo con esa matrícula.");
        }
        LocalDate hoy = LocalDate.now(reloj);
        List<ITV> historial = itvs.buscarPorVehiculo(v);
        VehiculoFiscalizado fiscalizado = fiscalizar(v, permisos.buscarPorVehiculo(v), historial, hoy);
        LOG.info(() -> LogFuncionarios.evento("empresa.vehiculo.consulta").correlationId(correlationId).usuario(ci)
                .campo("nroEmpresa", nroEmpresa).campo("matricula", v.getMatricula())
                .campo("estado", fiscalizado.estado()).toString());
        return new VehiculoDetalle(empresa.getNroEmpresa(), hoy.toString(), fiscalizado,
                historial.stream().map(i -> itv(i, hoy)).toList());
    }

    // historial: las ITV del vehiculo, la de vencimiento mas lejano primero (ITVDAO)
    private static VehiculoFiscalizado fiscalizar(Vehiculo v, PermisoCirculacion permiso, List<ITV> historial,
            LocalDate hoy) {
        EstadoVehiculo estado = !v.isActivo() ? EstadoVehiculo.DADO_DE_BAJA
                : v.habilitadoEn(hoy, permiso, historial) ? EstadoVehiculo.HABILITADO
                        : EstadoVehiculo.NO_HABILITADO;
        return new VehiculoFiscalizado(v.getMatricula(), v.getMarca(), v.getModelo(), estado, permiso(permiso, hoy),
                historial.isEmpty() ? SIN_REGISTRO : itv(historial.get(0), hoy));
    }

    private static PeriodoVista permiso(PermisoCirculacion p, LocalDate hoy) {
        if (p == null) {
            return SIN_REGISTRO;
        }
        EstadoDocumento estado = hoy.isBefore(p.getFechaEmision())
                ? EstadoDocumento.AUN_NO_VIGENTE
                : vigencia(p.vigenteEn(hoy), p.getFechaVencimiento(), hoy);
        return new PeriodoVista(p.getNro(), p.getFechaEmision().toString(), p.getFechaVencimiento().toString(), estado,
                dias(hoy, p.getFechaVencimiento()));
    }

    private static PeriodoVista itv(ITV i, LocalDate hoy) {
        return new PeriodoVista(null, i.getFechaEmision().toString(), i.getFechaVencimiento().toString(),
                vigencia(i.vigenteEn(hoy), i.getFechaVencimiento(), hoy), dias(hoy, i.getFechaVencimiento()));
    }

    private static EstadoDocumento vigencia(boolean vigente, LocalDate vencimiento, LocalDate hoy) {
        if (!vigente) {
            return EstadoDocumento.VENCIDO;
        }
        return dias(hoy, vencimiento) <= DIAS_POR_VENCER ? EstadoDocumento.POR_VENCER : EstadoDocumento.VIGENTE;
    }

    private static long dias(LocalDate hoy, LocalDate vencimiento) {
        return ChronoUnit.DAYS.between(hoy, vencimiento);
    }

    private static ResumenHabilitaciones resumen(List<VehiculoFiscalizado> lista) {
        int habilitados = 0;
        int noHabilitados = 0;
        int dadosDeBaja = 0;
        int porVencer = 0;
        for (VehiculoFiscalizado v : lista) {
            switch (v.estado()) {
                case HABILITADO -> {
                    habilitados++;
                    if (v.permiso().estado() == EstadoDocumento.POR_VENCER
                            || v.itv().estado() == EstadoDocumento.POR_VENCER) {
                        porVencer++;
                    }
                }
                case NO_HABILITADO -> noHabilitados++;
                case DADO_DE_BAJA -> dadosDeBaja++;
            }
        }
        return new ResumenHabilitaciones(lista.size(), habilitados, noHabilitados, dadosDeBaja, porVencer);
    }

    private static int orden(EstadoVehiculo estado) {
        return switch (estado) {
            case NO_HABILITADO -> 0;
            case HABILITADO -> 1;
            case DADO_DE_BAJA -> 2;
        };
    }

    private Empresa empresa(Long nroEmpresa, String ci, String correlationId) throws RecursoNoEncontradoException {
        Empresa empresa = nroEmpresa == null ? null : empresas.buscarPorId(nroEmpresa);
        if (empresa == null) {
            LOG.info(() -> LogFuncionarios.evento("empresa.no_encontrada").correlationId(correlationId).usuario(ci)
                    .campo("nroEmpresa", nroEmpresa).toString());
            throw new RecursoNoEncontradoException("EMPRESA_NO_ENCONTRADA", "La empresa no existe.");
        }
        return empresa;
    }
}
