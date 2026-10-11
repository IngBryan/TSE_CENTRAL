package tse.lab.frontofficeciudadanos.servicio;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.EstadoChofer;
import tse.lab.frontofficeciudadanos.model.EstadoGuia;
import tse.lab.frontofficeciudadanos.model.Guia;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.model.Vehiculo;

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

    public record NuevaGuia(String rubroCliente, Long pesoCargaKg, String fecha, String origen, String destino) {
    }

    // Solo primer nombre y primer apellido del chofer (Ley 18.331)
    public record ChoferResumen(Long id, String cedula, String primerNombre, String primerApellido) {
    }

    public record VehiculoResumen(String matricula, String marca, String modelo, long capacidadCargaKg) {
    }

    public record GuiaVista(String id, String rubroCliente, long pesoCargaKg, String fecha, String origen,
            String destino, String estado, VehiculoResumen vehiculo, ChoferResumen chofer) {
    }

    public record OpcionesAsignacion(GuiaVista guia, List<VehiculoResumen> vehiculos, List<ChoferResumen> choferes) {
    }

    public List<GuiaVista> listar(String ciResponsable, String correlationId) throws NoAutorizadoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        return guias.buscarPorEmpresaOrdenadas(empresa).stream().map(this::vista).toList();
    }

    public GuiaVista crear(String ciResponsable, NuevaGuia datos, String correlationId)
            throws NoAutorizadoException, DatosInvalidosException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);

        Map<String, String> errores = new LinkedHashMap<>();
        String rubro = texto(errores, "rubroCliente", "El rubro del cliente", datos == null ? null : datos.rubroCliente(), MAX_TEXTO);
        String origen = texto(errores, "origen", "El origen", datos == null ? null : datos.origen(), MAX_LUGAR);
        String destino = texto(errores, "destino", "El destino", datos == null ? null : datos.destino(), MAX_LUGAR);
        Long carga = datos == null ? null : datos.pesoCargaKg();
        if (carga == null || carga <= 0) {
            errores.put("pesoCargaKg", "La carga tiene que ser mayor que 0 kg.");
        } else if (carga > MAX_CARGA_KG) {
            errores.put("pesoCargaKg", "La carga no puede superar los " + MAX_CARGA_KG + " kg.");
        }
        LocalDate fecha = null;
        try {
            fecha = datos == null || datos.fecha() == null ? null : LocalDate.parse(datos.fecha().trim());
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

        Guia guia = new Guia(nuevoId(), rubro, carga, fecha, origen, destino, empresa);
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

        guia.asignar(vehiculo, chofer); // entidad administrada: se guarda al cerrar la transaccion
        LOG.info(() -> "evento=guia.asignada correlationId=" + correlationId + " usuario=" + ciResponsable
                + " nroEmpresa=" + empresa.getNroEmpresa() + " guia=" + guia.getId() + " vehiculo="
                + vehiculo.getMatricula() + " chofer=" + chofer.getCi());
        return vista(guia);
    }

    private Guia guiaSinAsignar(Empresa empresa, String idGuia) throws RecursoNoEncontradoException, ConflictoException {
        Guia guia = idGuia == null ? null : guias.buscarPorIdYEmpresa(idGuia, empresa);
        if (guia == null) {
            throw new RecursoNoEncontradoException("No existe la guia " + idGuia + " en la empresa");
        }
        // Postcondicion de CU-07: una guia asignada no se puede volver a asignar
        if (guia.getEstado() == EstadoGuia.ASIGNADA) {
            throw new ConflictoException("GUIA_YA_ASIGNADA", "La guía ya está asignada a un vehículo y un chofer.");
        }
        return guia;
    }

    private GuiaVista vista(Guia g) {
        return new GuiaVista(g.getId(), g.getRubroCliente(), g.getPesoCargaKg(),
                g.getFecha() == null ? null : g.getFecha().toString(), g.getOrigen(), g.getDestino(),
                (g.getEstado() == null ? EstadoGuia.SIN_ASIGNAR : g.getEstado()).name(),
                g.getVehiculo() == null ? null : resumen(g.getVehiculo()),
                g.getChofer() == null ? null : resumen(g.getChofer()));
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
