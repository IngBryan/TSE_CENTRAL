package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.EventoViajeDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.ModificacionGuiaDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.EstadoChofer;
import tse.lab.frontofficeciudadanos.model.EstadoGuia;
import tse.lab.frontofficeciudadanos.model.EventoViaje;
import tse.lab.frontofficeciudadanos.model.Guia;
import tse.lab.frontofficeciudadanos.model.ModificacionGuia;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.model.TipoModificacionGuia;
import tse.lab.frontofficeciudadanos.model.Vehiculo;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;

// Gestion de Viajes de Carga: el responsable de una empresa habilitada ingresa guias de viaje y las
// asigna a un vehiculo y un chofer de la empresa (CU-07). El control de acceso y las reglas se
// aplican aca, no en la interfaz.
@Stateless
public class GuiasService {

    private static final Logger LOG = Logger.getLogger(GuiasService.class.getName());
    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");
    static final int MAX_TEXTO = 100;
    static final int MAX_LUGAR = 150;
    static final long MAX_CARGA_KG = 100_000;
    static final int MAX_MOTIVO = 500;

    @EJB
    private AutorizacionResponsable autorizacion;
    @EJB
    private GuiaDAO guias;
    @EJB
    private VehiculoDAO vehiculos;
    @EJB
    private ChoferDAO choferes;
    @EJB
    private PerfilCiudadanoDAO perfiles;
    @EJB
    private EventoViajeDAO eventos;
    @EJB
    private ModificacionGuiaDAO modificaciones;

    public record NuevaGuia(String rubroCliente, String tipoCarga, Long pesoCargaKg, String fecha, String origen, String destino) {
    }

    // Solo primer nombre y primer apellido del chofer (Ley 18.331)
    public record ChoferResumen(Long id, String cedula, String primerNombre, String primerApellido) {
    }

    public record VehiculoResumen(String matricula, String marca, String modelo, long capacidadCargaKg) {
    }

    // version: la de la entidad (@Version). El front la manda al modificar para detectar cambios concurrentes.
    public record GuiaVista(String id, String rubroCliente, String tipoCarga, long pesoCargaKg, String fecha, String origen,
            String destino, String estado, VehiculoResumen vehiculo, ChoferResumen chofer, Long version) {
    }

    // Modificacion de una guia ASIGNADA por el responsable. version: la que se estaba mirando al editar.
    public record EdicionGuia(String rubroCliente, String tipoCarga, Long pesoCargaKg, String fecha, String origen,
            String destino, Long version) {
    }

    public record Reasignacion(String matricula, Long idChofer, Long version) {
    }

    public record Cancelacion(String motivo, Long version) {
    }

    public record ModificacionVista(String tipo, String fecha, String ciResponsable, String cambios, String motivo,
            Long versionAnterior, Long versionNueva) {
    }

    public record OpcionesAsignacion(GuiaVista guia, List<VehiculoResumen> vehiculos, List<ChoferResumen> choferes) {
    }

    // Historial de eventos de la app movil para el responsable. resultado: APLICADO, SIN_EFECTO o CONFLICTO.
    public record EventoVista(String idEvento, String tipo, String fechaHoraDispositivo, String fechaRecepcion,
            ChoferResumen chofer, String descripcion, Double latitud, Double longitud, boolean conFoto,
            String resultado, String codigo, String detalle) {
    }

    // vehiculos y choferes: opciones para reasignar (solo si la guia esta ASIGNADA; si no, listas vacias)
    public record GuiaConEventos(GuiaVista guia, List<EventoVista> eventos, List<ModificacionVista> modificaciones,
            List<VehiculoResumen> vehiculos, List<ChoferResumen> choferes) {
    }

    public List<GuiaVista> listar(String ciResponsable, String correlationId) throws NoAutorizadoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        return guias.buscarPorEmpresaOrdenadas(empresa).stream().map(this::vista).toList();
    }

    public GuiaVista crear(String ciResponsable, NuevaGuia datos, String correlationId)
            throws NoAutorizadoException, DatosInvalidosException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        DatosValidos d = datos == null ? validar(null, null, null, null, null, null)
                : validar(datos.rubroCliente(), datos.tipoCarga(), datos.pesoCargaKg(), datos.fecha(), datos.origen(),
                        datos.destino());

        Guia guia = new Guia(nuevoId(), d.rubro(), d.carga(), d.fecha(), d.origen(), d.destino(), empresa);
        guia.setTipoCarga(d.tipoCarga());
        guias.guardar(guia);
        LOG.info(() -> "evento=guia.creada correlationId=" + correlationId + " usuario=" + ciResponsable
                + " nroEmpresa=" + empresa.getNroEmpresa() + " guia=" + guia.getId());
        return vista(guia);
    }

    // CU-07 pasos 1 a 5: la guia elegida, los vehiculos y los choferes habilitados de la empresa.
    public OpcionesAsignacion opcionesAsignacion(String ciResponsable, String idGuia, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = guiaSinAsignar(empresa, idGuia);
        List<VehiculoResumen> vs = vehiculos.buscarPorEmpresa(empresa).stream().map(GuiasService::resumen).toList();
        // Regla de CU-02: solo se ofrecen los choferes habilitados; no se consulta la PDI
        List<ChoferResumen> cs = choferes.buscarPorEmpresa(empresa).stream()
                .filter(c -> c.getEstado() == EstadoChofer.HABILITADO)
                .map(this::resumen)
                .toList();
        return new OpcionesAsignacion(vista(guia), vs, cs);
    }

    // CU-07 pasos 8 y 9: el responsable confirmo; se registra la asignacion y la guia pasa a ASIGNADA.
    public GuiaVista asignar(String ciResponsable, String idGuia, String matricula, Long idChofer, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = guiaSinAsignar(empresa, idGuia);

        Map<String, String> errores = new LinkedHashMap<>();
        Vehiculo vehiculo = matricula == null ? null : vehiculos.buscarPorMatricula(matricula);
        if (vehiculo == null || !esDe(vehiculo.getEmpresa(), empresa)) {
            errores.put("vehiculo", "Elegí un vehículo de la empresa.");
        }
        Chofer chofer = idChofer == null ? null : choferes.buscarPorId(idChofer);
        if (chofer == null || !esDe(chofer.getEmpresa(), empresa)) {
            errores.put("chofer", "Elegí un chofer de la empresa.");
        } else if (chofer.getEstado() != EstadoChofer.HABILITADO) {
            errores.put("chofer", "El chofer está pendiente de validación y todavía no se le pueden asignar viajes.");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        guia.asignar(vehiculo, chofer);
        guias.sincronizar(); // flush: la respuesta lleva la version nueva (la usa la modificacion de guias)
        LOG.info(() -> "evento=guia.asignada correlationId=" + correlationId + " usuario=" + ciResponsable
                + " nroEmpresa=" + empresa.getNroEmpresa() + " guia=" + guia.getId() + " vehiculo="
                + vehiculo.getMatricula() + " chofer=" + chofer.getCi());
        return vista(guia);
    }

    // ===== Modificacion de guias por el responsable (solo ASIGNADA) =====
    // Una guia solo se modifica mientras esta ASIGNADA: el chofer todavia no la inicio. Puede estar sin
    // conexion con la version anterior en el telefono; sus eventos se resuelven con la politica de
    // conflictos (TSE_MOVIL/docs/adr/0002): la edicion sube el @Version y el movil recibe
    // guiaModificada; tras una reasignacion o cancelacion sus eventos quedan CONFLICTO.

    // Edita los datos de la guia (mismas reglas que el alta).
    public GuiaVista editar(String ciResponsable, String idGuia, EdicionGuia datos, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = guiaModificable(empresa, idGuia, datos == null ? null : datos.version());
        DatosValidos d = validar(datos.rubroCliente(), datos.tipoCarga(), datos.pesoCargaKg(), datos.fecha(),
                datos.origen(), datos.destino());

        List<String> cambios = new ArrayList<>();
        cambio(cambios, "rubro", guia.getRubroCliente(), d.rubro());
        cambio(cambios, "tipo de carga", guia.getTipoCarga(), d.tipoCarga());
        cambio(cambios, "carga (kg)", guia.getPesoCargaKg(), d.carga());
        cambio(cambios, "fecha", guia.getFecha(), d.fecha());
        cambio(cambios, "origen", guia.getOrigen(), d.origen());
        cambio(cambios, "destino", guia.getDestino(), d.destino());
        if (cambios.isEmpty()) {
            throw new DatosInvalidosException(Map.of("general", "No cambiaste ningún dato de la guía."));
        }
        guia.setRubroCliente(d.rubro());
        guia.setTipoCarga(d.tipoCarga());
        guia.setPesoCargaKg(d.carga());
        guia.setFecha(d.fecha());
        guia.setOrigen(d.origen());
        guia.setDestino(d.destino());
        return registrarModificacion(guia, TipoModificacionGuia.EDICION, ciResponsable, cambios, null, correlationId);
    }

    // Cambia el vehiculo y/o el chofer (de la empresa; el chofer HABILITADO).
    public GuiaVista reasignar(String ciResponsable, String idGuia, Reasignacion datos, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = guiaModificable(empresa, idGuia, datos == null ? null : datos.version());
        Vehiculo vehiculo = vehiculoValido(empresa, datos.matricula());
        Chofer chofer = choferValido(empresa, datos.idChofer());

        List<String> cambios = new ArrayList<>();
        cambio(cambios, "vehículo", guia.getVehiculo() == null ? null : guia.getVehiculo().getMatricula(),
                vehiculo.getMatricula());
        cambio(cambios, "chofer", guia.getChofer() == null ? null : guia.getChofer().getCi(), chofer.getCi());
        if (cambios.isEmpty()) {
            throw new DatosInvalidosException(Map.of("general", "Elegí otro vehículo u otro chofer."));
        }
        guia.setVehiculo(vehiculo);
        guia.setChofer(chofer);
        return registrarModificacion(guia, TipoModificacionGuia.REASIGNACION, ciResponsable, cambios, null,
                correlationId);
    }

    // Cancela la guia con un motivo obligatorio. Deja de aparecer en la app del chofer.
    public GuiaVista cancelar(String ciResponsable, String idGuia, Cancelacion datos, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = guiaModificable(empresa, idGuia, datos == null ? null : datos.version());
        Map<String, String> errores = new LinkedHashMap<>();
        String motivo = texto(errores, "motivo", "El motivo de la cancelación", datos.motivo(), MAX_MOTIVO);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        guia.cambiarEstado(EstadoGuia.CANCELADA);
        return registrarModificacion(guia, TipoModificacionGuia.CANCELACION, ciResponsable,
                List.of("estado: ASIGNADA -> CANCELADA"), motivo, correlationId);
    }

    // Bloquea la guia (los eventos del movil sobre la misma guia esperan) y controla estado y version.
    private Guia guiaModificable(Empresa empresa, String idGuia, Long version)
            throws RecursoNoEncontradoException, ConflictoException {
        Guia guia = idGuia == null ? null : guias.buscarPorIdParaActualizar(idGuia);
        if (guia == null || !esDe(guia.getEmpresa(), empresa)) {
            throw new RecursoNoEncontradoException("No existe la guia " + idGuia + " en la empresa");
        }
        if (guia.getEstado() != EstadoGuia.ASIGNADA) {
            throw new ConflictoException("GUIA_NO_MODIFICABLE", "Solo se puede modificar una guía asignada que el "
                    + "chofer todavía no inició (estado actual: " + guia.getEstado() + ").");
        }
        if (version == null || !version.equals(guia.getVersion())) {
            throw new ConflictoException("GUIA_DESACTUALIZADA", "La guía cambió mientras la estabas modificando "
                    + "(por ejemplo, el chofer la inició o se modificó desde otra sesión). Volvé a cargarla.");
        }
        return guia;
    }

    private GuiaVista registrarModificacion(Guia guia, TipoModificacionGuia tipo, String ciResponsable,
            List<String> cambios, String motivo, String correlationId) {
        Long anterior = guia.getVersion();
        guias.sincronizar(); // flush: incrementa el @Version, que es lo que detecta el movil
        String texto = String.join("; ", cambios);
        modificaciones.guardar(new ModificacionGuia(guia, tipo, ciResponsable, texto, motivo, anterior,
                guia.getVersion()));
        LOG.info(() -> "evento=guia.modificada correlationId=" + correlationId + " usuario=" + ciResponsable
                + " guia=" + guia.getId() + " tipo=" + tipo + " version=" + anterior + "->" + guia.getVersion()
                + " cambios=\"" + texto + "\"");
        return vista(guia);
    }

    private static void cambio(List<String> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(campo + ": " + (antes == null ? "-" : antes) + " -> " + despues);
        }
    }

    private Vehiculo vehiculoValido(Empresa empresa, String matricula) throws DatosInvalidosException {
        Vehiculo vehiculo = matricula == null ? null : vehiculos.buscarPorMatricula(matricula);
        if (vehiculo == null || !esDe(vehiculo.getEmpresa(), empresa)) {
            throw new DatosInvalidosException(Map.of("vehiculo", "Elegí un vehículo de la empresa."));
        }
        return vehiculo;
    }

    private Chofer choferValido(Empresa empresa, Long idChofer) throws DatosInvalidosException {
        Chofer chofer = idChofer == null ? null : choferes.buscarPorId(idChofer);
        if (chofer == null || !esDe(chofer.getEmpresa(), empresa)) {
            throw new DatosInvalidosException(Map.of("chofer", "Elegí un chofer de la empresa."));
        }
        if (chofer.getEstado() != EstadoChofer.HABILITADO) {
            throw new DatosInvalidosException(Map.of("chofer",
                    "El chofer está pendiente de validación y todavía no se le pueden asignar viajes."));
        }
        return chofer;
    }

    // Linea de tiempo de una guia de la empresa: los eventos que registro el chofer (ordenados por la hora
    // del dispositivo y despues por la de recepcion; los CONFLICTO se marcan en la web) y las
    // modificaciones del responsable. Si la guia esta ASIGNADA trae tambien las opciones para reasignar.
    public GuiaConEventos eventos(String ciResponsable, String idGuia, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = guiaDeLaEmpresa(empresa, idGuia);
        List<EventoVista> lista = eventos.buscarPorGuia(guia).stream().map(this::vista).toList();
        List<ModificacionVista> mods = modificaciones.buscarPorGuia(guia).stream()
                .map(m -> new ModificacionVista(m.getTipo().name(), m.getFecha().toString(), m.getCiResponsable(),
                        m.getCambios(), m.getMotivo(), m.getVersionAnterior(), m.getVersionNueva()))
                .toList();
        boolean modificable = guia.getEstado() == EstadoGuia.ASIGNADA;
        List<VehiculoResumen> vs = modificable
                ? vehiculos.buscarPorEmpresa(empresa).stream().map(GuiasService::resumen).toList()
                : List.of();
        List<ChoferResumen> cs = modificable ? choferes.buscarPorEmpresa(empresa).stream()
                .filter(c -> c.getEstado() == EstadoChofer.HABILITADO).map(this::resumen).toList() : List.of();
        return new GuiaConEventos(vista(guia), lista, mods, vs, cs);
    }

    // Foto de un evento (incidente) de una guia de la empresa; null si no tiene.
    public byte[] fotoEvento(String ciResponsable, String idGuia, String idEvento, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = guiaDeLaEmpresa(empresa, idGuia);
        EventoViaje e = idEvento == null ? null : eventos.buscarPorId(idEvento);
        if (e == null || !e.getGuia().getId().equals(guia.getId())) {
            throw new RecursoNoEncontradoException("No existe el evento " + idEvento + " en la guia");
        }
        return e.getFoto();
    }

    private Guia guiaDeLaEmpresa(Empresa empresa, String idGuia) throws RecursoNoEncontradoException {
        Guia guia = idGuia == null ? null : guias.buscarPorIdYEmpresa(idGuia, empresa);
        if (guia == null) {
            throw new RecursoNoEncontradoException("No existe la guia " + idGuia + " en la empresa");
        }
        return guia;
    }

    private EventoVista vista(EventoViaje e) {
        Chofer chofer = e.getGuia().getEmpresa() == null ? null
                : choferes.buscarPorCiYEmpresa(e.getCiChofer(), e.getGuia().getEmpresa());
        PerfilCiudadano p = perfiles.buscarPorCi(e.getCiChofer());
        ChoferResumen resumen = new ChoferResumen(chofer == null ? null : chofer.getId(), e.getCiChofer(),
                p == null ? null : p.getPrimerNombre(), p == null ? null : p.getPrimerApellido());
        return new EventoVista(e.getIdEvento(), e.getTipo().name(), e.getFechaHoraDispositivo().toString(),
                e.getFechaRecepcion().toString(), resumen, e.getDescripcion(), e.getLatitud(), e.getLongitud(),
                e.getFoto() != null, e.getResultado() == null ? "APLICADO" : e.getResultado().name(), e.getCodigo(),
                e.getDetalle());
    }

    private Guia guiaSinAsignar(Empresa empresa, String idGuia) throws RecursoNoEncontradoException, ConflictoException {
        Guia guia = idGuia == null ? null : guias.buscarPorIdYEmpresa(idGuia, empresa);
        if (guia == null) {
            throw new RecursoNoEncontradoException("No existe la guia " + idGuia + " en la empresa");
        }
        // Postcondicion de CU-07: una guia asignada (o ya en viaje) no se puede volver a asignar
        if (guia.getEstado() != null && guia.getEstado() != EstadoGuia.SIN_ASIGNAR) {
            throw new ConflictoException("GUIA_YA_ASIGNADA", "La guía ya está asignada a un vehículo y un chofer.");
        }
        return guia;
    }

    private GuiaVista vista(Guia g) {
        return new GuiaVista(g.getId(), g.getRubroCliente(), g.getTipoCarga(), g.getPesoCargaKg(),
                g.getFecha() == null ? null : g.getFecha().toString(), g.getOrigen(), g.getDestino(),
                (g.getEstado() == null ? EstadoGuia.SIN_ASIGNAR : g.getEstado()).name(),
                g.getVehiculo() == null ? null : resumen(g.getVehiculo()),
                g.getChofer() == null ? null : resumen(g.getChofer()), g.getVersion());
    }

    private static VehiculoResumen resumen(Vehiculo v) {
        return new VehiculoResumen(v.getMatricula(), v.getMarca(), v.getModelo(), v.getCapacidadCargaKg());
    }

    private ChoferResumen resumen(Chofer c) {
        PerfilCiudadano p = perfiles.buscarPorCi(c.getCi());
        return new ChoferResumen(c.getId(), c.getCi(), p == null ? null : p.getPrimerNombre(),
                p == null ? null : p.getPrimerApellido());
    }

    private static boolean esDe(Empresa de, Empresa empresa) {
        return de != null && Objects.equals(de.getNroEmpresa(), empresa.getNroEmpresa());
    }

    // Identificador legible de la guia, por ejemplo GV-3F9A12C4
    private String nuevoId() {
        String id;
        do {
            id = "GV-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000000, Integer.MAX_VALUE))
                    .toUpperCase(Locale.ROOT);
        } while (guias.buscarPorId(id) != null);
        return id;
    }

    private record DatosValidos(String rubro, String tipoCarga, long carga, LocalDate fecha, String origen,
            String destino) {
    }

    // Reglas de los datos de una guia (alta y edicion): rubro y tipo de carga obligatorios, carga entre 1 y
    // MAX_CARGA_KG, fecha >= hoy, origen y destino obligatorios y distintos.
    private static DatosValidos validar(String rubroCliente, String tipoCargaTexto, Long pesoCargaKg, String fechaTexto,
            String origenTexto, String destinoTexto) throws DatosInvalidosException {
        Map<String, String> errores = new LinkedHashMap<>();
        String rubro = texto(errores, "rubroCliente", "El rubro del cliente", rubroCliente, MAX_TEXTO);
        String tipoCarga = texto(errores, "tipoCarga", "El tipo de carga", tipoCargaTexto, MAX_TEXTO);
        String origen = texto(errores, "origen", "El origen", origenTexto, MAX_LUGAR);
        String destino = texto(errores, "destino", "El destino", destinoTexto, MAX_LUGAR);
        if (pesoCargaKg == null || pesoCargaKg <= 0) {
            errores.put("pesoCargaKg", "La carga tiene que ser mayor que 0 kg.");
        } else if (pesoCargaKg > MAX_CARGA_KG) {
            errores.put("pesoCargaKg", "La carga no puede superar los " + MAX_CARGA_KG + " kg.");
        }
        LocalDate fecha = null;
        try {
            fecha = fechaTexto == null ? null : LocalDate.parse(fechaTexto.trim());
        } catch (DateTimeParseException e) {
            // queda null y se informa abajo
        }
        if (fecha == null) {
            errores.put("fecha", "Ingresá una fecha válida.");
        } else if (fecha.isBefore(LocalDate.now(ZONA))) {
            errores.put("fecha", "La fecha del viaje no puede ser anterior a hoy.");
        }
        if (origen != null && destino != null && origen.equalsIgnoreCase(destino)) {
            errores.put("destino", "El destino tiene que ser distinto del origen.");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        return new DatosValidos(rubro, tipoCarga, pesoCargaKg, fecha, origen, destino);
    }

    private static String texto(Map<String, String> errores, String campo, String nombre, String valor, int max) {
        String limpio = valor == null ? null : valor.trim().replaceAll("\\s+", " ");
        if (limpio == null || limpio.isEmpty()) {
            errores.put(campo, nombre + " es obligatorio.");
            return null;
        }
        if (limpio.length() > max) {
            errores.put(campo, nombre + " no puede tener más de " + max + " caracteres.");
            return null;
        }
        return limpio;
    }
}
