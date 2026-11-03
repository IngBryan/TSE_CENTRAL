package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.ITVDAO;
import tse.lab.frontofficeciudadanos.data.PermisoCirculacionDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.LogCiudadanos;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoGuia;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Vehiculo;

// ABM de Vehiculos: el responsable de una empresa habilitada da de alta, modifica, consulta y da de
// baja los vehiculos de su empresa, y registra su Permiso Nacional de Circulacion y sus ITV. El
// control de acceso y las reglas se aplican aca, no en la interfaz.
// - La matricula se guarda normalizada (Vehiculo.normalizarMatricula) para cruzarla con balanzas y
//   tracking, y no se modifica (es la clave).
// - La baja es logica: el vehiculo deja de ofrecerse para guias nuevas pero se conserva su historia.
// - Un vehiculo esta habilitado si sigue activo, tiene permiso vigente a la fecha y una ITV que no vencio.
@Stateless
public class GestionVehiculosService {

    private static final Logger LOG = Logger.getLogger(GestionVehiculosService.class.getName());
    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");
    private static final Pattern MATRICULA = Pattern.compile("[A-Z0-9]{4,10}");
    static final int MAX_TEXTO = 50;
    static final int MAX_NRO_PERMISO = 30;
    static final long MAX_PESO_KG = 60_000;
    static final long MAX_CAPACIDAD_KG = 100_000;
    // Mientras tenga un viaje pendiente o en curso, el vehiculo no se puede dar de baja
    private static final List<EstadoGuia> GUIAS_ACTIVAS = List.of(EstadoGuia.ASIGNADA, EstadoGuia.EN_CURSO);

    @EJB
    private AutorizacionResponsable autorizacion;
    @EJB
    private VehiculoDAO vehiculos;
    @EJB
    private PermisoCirculacionDAO permisos;
    @EJB
    private ITVDAO itvs;
    @EJB
    private GuiaDAO guias;

    public record NuevoVehiculo(String matricula, String marca, String modelo, Long peso, Long capacidadCargaKg) {
    }

    public record EdicionVehiculo(String marca, String modelo, Long peso, Long capacidadCargaKg) {
    }

    // Fechas en formato ISO (yyyy-MM-dd), como las manda un <input type="date">
    public record DatosPermiso(String nro, String fechaEmision, String fechaVencimiento) {
    }

    public record DatosItv(String fechaVencimiento) {
    }

    public record PermisoVista(String nro, String fechaEmision, String fechaVencimiento, boolean vigente) {
    }

    public record ItvVista(Long id, String fechaVencimiento, boolean vigente) {
    }

    // permiso / itv: null si no se registraron; itv es la de vencimiento mas lejano.
    public record VehiculoVista(String matricula, String marca, String modelo, long peso, long capacidadCargaKg,
            boolean activo, PermisoVista permiso, ItvVista itv, boolean habilitado) {
    }

    public record VehiculoDetalle(VehiculoVista vehiculo, List<ItvVista> itvs) {
    }

    // Todos los vehiculos de la empresa, primero los activos.
    public List<VehiculoVista> listar(String ciResponsable, String correlationId) throws NoAutorizadoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        LocalDate hoy = LocalDate.now(ZONA);
        return vehiculos.buscarPorEmpresa(empresa).stream()
                .sorted(Comparator.comparing((Vehiculo v) -> !v.isActivo()).thenComparing(Vehiculo::getMatricula))
                .map(v -> vista(v, itvs.buscarPorVehiculo(v), hoy))
                .toList();
    }

    public VehiculoDetalle detalle(String ciResponsable, String matricula, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        return detalle(vehiculoDeLaEmpresa(empresa, matricula));
    }

    public VehiculoVista crear(String ciResponsable, NuevoVehiculo datos, String correlationId)
            throws NoAutorizadoException, DatosInvalidosException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        NuevoVehiculo d = datos == null ? new NuevoVehiculo(null, null, null, null, null) : datos;

        Map<String, String> errores = new LinkedHashMap<>();
        String matricula = Vehiculo.normalizarMatricula(d.matricula());
        if (matricula == null || matricula.isEmpty()) {
            errores.put("matricula", "La matrícula es obligatoria.");
        } else if (!MATRICULA.matcher(matricula).matches()) {
            errores.put("matricula", "La matrícula tiene que tener de 4 a 10 letras o números (por ejemplo SBA1234).");
        } else if (vehiculos.buscarPorMatricula(matricula) != null) {
            // No se dice de que empresa es: es un dato de otra empresa
            errores.put("matricula", "Ya hay un vehículo registrado con la matrícula " + matricula + ".");
        }
        DatosValidos v = validar(errores, d.marca(), d.modelo(), d.peso(), d.capacidadCargaKg());

        Vehiculo vehiculo = new Vehiculo(matricula, v.marca(), v.modelo(), v.peso(), v.capacidad(), empresa);
        vehiculos.guardar(vehiculo);
        LOG.info(() -> LogCiudadanos.evento("vehiculo.alta").correlationId(correlationId).usuario(ciResponsable)
                .campo("nroEmpresa", empresa.getNroEmpresa()).campo("matricula", matricula).toString());
        return vista(vehiculo, List.of(), LocalDate.now(ZONA));
    }

    public VehiculoDetalle editar(String ciResponsable, String matricula, EdicionVehiculo datos, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        Vehiculo vehiculo = vehiculoActivo(empresa, matricula);
        EdicionVehiculo d = datos == null ? new EdicionVehiculo(null, null, null, null) : datos;
        DatosValidos v = validar(new LinkedHashMap<>(), d.marca(), d.modelo(), d.peso(), d.capacidadCargaKg());

        List<String> cambios = new ArrayList<>();
        cambio(cambios, "marca", vehiculo.getMarca(), v.marca());
        cambio(cambios, "modelo", vehiculo.getModelo(), v.modelo());
        cambio(cambios, "peso", vehiculo.getPeso(), v.peso());
        cambio(cambios, "capacidad", vehiculo.getCapacidadCargaKg(), v.capacidad());
        if (cambios.isEmpty()) {
            throw new DatosInvalidosException(Map.of("general", "No cambiaste ningún dato del vehículo."));
        }
        vehiculo.setMarca(v.marca());
        vehiculo.setModelo(v.modelo());
        vehiculo.setPeso(v.peso());
        vehiculo.setCapacidadCargaKg(v.capacidad());
        LOG.info(() -> LogCiudadanos.evento("vehiculo.modificado").correlationId(correlationId)
                .usuario(ciResponsable).campo("nroEmpresa", empresa.getNroEmpresa())
                .campo("matricula", vehiculo.getMatricula())
                .campo("cambios", "\"" + String.join("; ", cambios) + "\"").toString());
        return detalle(vehiculo);
    }

    // Registra o reemplaza el Permiso Nacional de Circulacion (uno por vehiculo).
    public VehiculoDetalle registrarPermiso(String ciResponsable, String matricula, DatosPermiso datos,
            String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        Vehiculo vehiculo = vehiculoActivo(empresa, matricula);
        DatosPermiso d = datos == null ? new DatosPermiso(null, null, null) : datos;

        Map<String, String> errores = new LinkedHashMap<>();
        String nro = d.nro() == null ? null : d.nro().trim().toUpperCase(Locale.ROOT);
        PermisoCirculacion actual = permisos.buscarPorVehiculo(vehiculo);
        if (nro == null || nro.isEmpty()) {
            errores.put("nro", "El número de permiso es obligatorio.");
        } else if (nro.length() > MAX_NRO_PERMISO) {
            errores.put("nro", "El número de permiso no puede tener más de " + MAX_NRO_PERMISO + " caracteres.");
        } else {
            PermisoCirculacion conEseNro = permisos.buscarPorNro(nro);
            if (conEseNro != null && conEseNro != actual) {
                errores.put("nro", "Ese número de permiso ya está registrado para otro vehículo.");
            }
        }
        LocalDate desde = fecha(errores, "fechaEmision", d.fechaEmision());
        LocalDate hasta = fecha(errores, "fechaVencimiento", d.fechaVencimiento());
        if (desde != null && hasta != null && !hasta.isAfter(desde)) {
            errores.put("fechaVencimiento", "El vencimiento tiene que ser posterior a la fecha de emisión.");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        if (actual != null && actual.getNro().equals(nro)) {
            actual.setFechaEmision(desde);
            actual.setFechaVencimiento(hasta);
        } else {
            if (actual != null) {
                permisos.eliminar(actual); // el nro es la clave: cambiarlo es reemplazar el permiso
            }
            permisos.guardar(new PermisoCirculacion(nro, desde, hasta, vehiculo));
        }
        LOG.info(() -> LogCiudadanos.evento("vehiculo.permiso").correlationId(correlationId).usuario(ciResponsable)
                .campo("nroEmpresa", empresa.getNroEmpresa()).campo("matricula", vehiculo.getMatricula())
                .campo("permiso", nro).campo("vigencia", desde + "/" + hasta).toString());
        return detalle(vehiculo);
    }

    // Agrega una Inspeccion Tecnica Vehicular; se conserva el historial.
    public VehiculoDetalle agregarItv(String ciResponsable, String matricula, DatosItv datos, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        Vehiculo vehiculo = vehiculoActivo(empresa, matricula);

        Map<String, String> errores = new LinkedHashMap<>();
        LocalDate vencimiento = fecha(errores, "fechaVencimiento", datos == null ? null : datos.fechaVencimiento());
        if (vencimiento != null
                && itvs.buscarPorVehiculo(vehiculo).stream().anyMatch(i -> vencimiento.equals(i.getFechaVencimiento()))) {
            errores.put("fechaVencimiento", "Ya hay una ITV registrada con ese vencimiento.");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        itvs.guardar(new ITV(vencimiento, vehiculo));
        LOG.info(() -> LogCiudadanos.evento("vehiculo.itv").correlationId(correlationId).usuario(ciResponsable)
                .campo("nroEmpresa", empresa.getNroEmpresa()).campo("matricula", vehiculo.getMatricula())
                .campo("vencimiento", vencimiento).toString());
        return detalle(vehiculo);
    }

    // Baja logica. Si ya estaba dado de baja no hace nada.
    public VehiculoVista darDeBaja(String ciResponsable, String matricula, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException {
        Empresa empresa = empresaDelResponsable(ciResponsable, correlationId);
        Vehiculo vehiculo = vehiculoDeLaEmpresa(empresa, matricula);
        if (vehiculo.isActivo()) {
            if (guias.contarPorVehiculoYEstados(vehiculo, GUIAS_ACTIVAS) > 0) {
                throw new ConflictoException("VEHICULO_CON_VIAJES", "El vehículo tiene viajes asignados o en curso. "
                        + "Reasignalos o esperá a que terminen antes de darlo de baja.");
            }
            vehiculo.setActivo(false);
            LOG.info(() -> LogCiudadanos.evento("vehiculo.baja").correlationId(correlationId).usuario(ciResponsable)
                    .campo("nroEmpresa", empresa.getNroEmpresa()).campo("matricula", vehiculo.getMatricula())
                    .toString());
        }
        return vista(vehiculo, itvs.buscarPorVehiculo(vehiculo), LocalDate.now(ZONA));
    }

    // ===== helpers =====

    private Empresa empresaDelResponsable(String ciResponsable, String correlationId) throws NoAutorizadoException {
        return autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
    }

    // Un vehiculo de otra empresa se trata igual que uno que no existe.
    private Vehiculo vehiculoDeLaEmpresa(Empresa empresa, String matricula) throws RecursoNoEncontradoException {
        String normalizada = Vehiculo.normalizarMatricula(matricula);
        Vehiculo vehiculo = normalizada == null || normalizada.isEmpty() ? null : vehiculos.buscarPorMatricula(normalizada);
        if (vehiculo == null || vehiculo.getEmpresa() == null
                || !Objects.equals(vehiculo.getEmpresa().getNroEmpresa(), empresa.getNroEmpresa())) {
            throw new RecursoNoEncontradoException("No existe el vehiculo " + matricula + " en la empresa");
        }
        return vehiculo;
    }

    private Vehiculo vehiculoActivo(Empresa empresa, String matricula)
            throws RecursoNoEncontradoException, ConflictoException {
        Vehiculo vehiculo = vehiculoDeLaEmpresa(empresa, matricula);
        if (!vehiculo.isActivo()) {
            throw new ConflictoException("VEHICULO_DADO_DE_BAJA", "El vehículo está dado de baja y no se puede modificar.");
        }
        return vehiculo;
    }

    private VehiculoDetalle detalle(Vehiculo vehiculo) {
        LocalDate hoy = LocalDate.now(ZONA);
        List<ITV> historial = itvs.buscarPorVehiculo(vehiculo);
        return new VehiculoDetalle(vista(vehiculo, historial, hoy),
                historial.stream().map(i -> itvVista(i, hoy)).toList());
    }

    // historial: ITV del vehiculo ordenadas por vencimiento descendente (ITVDAO.buscarPorVehiculo)
    private VehiculoVista vista(Vehiculo v, List<ITV> historial, LocalDate hoy) {
        PermisoCirculacion p = permisos.buscarPorVehiculo(v);
        PermisoVista permiso = p == null ? null
                : new PermisoVista(p.getNro(), texto(p.getFechaEmision()), texto(p.getFechaVencimiento()),
                        !hoy.isBefore(p.getFechaEmision()) && !hoy.isAfter(p.getFechaVencimiento()));
        ItvVista itv = historial.isEmpty() ? null : itvVista(historial.get(0), hoy);
        return new VehiculoVista(v.getMatricula(), v.getMarca(), v.getModelo(), v.getPeso(), v.getCapacidadCargaKg(),
                v.isActivo(), permiso, itv,
                v.isActivo() && permiso != null && permiso.vigente() && itv != null && itv.vigente());
    }

    private static ItvVista itvVista(ITV i, LocalDate hoy) {
        return new ItvVista(i.getId(), texto(i.getFechaVencimiento()), !hoy.isAfter(i.getFechaVencimiento()));
    }

    private static String texto(LocalDate fecha) {
        return fecha == null ? null : fecha.toString();
    }

    private record DatosValidos(String marca, String modelo, long peso, long capacidad) {
    }

    // Reglas de los datos del vehiculo (alta y edicion): marca y modelo obligatorios, peso y capacidad de
    // carga mayores que 0 y con un tope razonable para un vehiculo de carga.
    private static DatosValidos validar(Map<String, String> errores, String marcaTexto, String modeloTexto, Long peso,
            Long capacidad) throws DatosInvalidosException {
        String marca = texto(errores, "marca", "La marca", marcaTexto);
        String modelo = texto(errores, "modelo", "El modelo", modeloTexto);
        kilos(errores, "peso", "El peso del vehículo", peso, MAX_PESO_KG);
        kilos(errores, "capacidadCargaKg", "La capacidad de carga", capacidad, MAX_CAPACIDAD_KG);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return new DatosValidos(marca, modelo, peso, capacidad);
    }

    private static void kilos(Map<String, String> errores, String campo, String nombre, Long valor, long max) {
        if (valor == null || valor <= 0) {
            errores.put(campo, nombre + " tiene que ser mayor que 0 kg.");
        } else if (valor > max) {
            errores.put(campo, nombre + " no puede superar los " + max + " kg.");
        }
    }

    private static String texto(Map<String, String> errores, String campo, String nombre, String valor) {
        String limpio = valor == null ? null : valor.trim().replaceAll("\\s+", " ");
        if (limpio == null || limpio.isEmpty()) {
            errores.put(campo, nombre + " es obligatorio.");
            return null;
        }
        if (limpio.length() > MAX_TEXTO) {
            errores.put(campo, nombre + " no puede tener más de " + MAX_TEXTO + " caracteres.");
            return null;
        }
        return limpio;
    }

    private static LocalDate fecha(Map<String, String> errores, String campo, String valor) {
        try {
            if (valor != null && !valor.isBlank()) {
                return LocalDate.parse(valor.trim());
            }
        } catch (DateTimeParseException e) {
            // se informa abajo
        }
        errores.put(campo, "Ingresá una fecha válida.");
        return null;
    }

    private static void cambio(List<String> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(campo + ": " + (antes == null ? "-" : antes) + " -> " + despues);
        }
    }
}
