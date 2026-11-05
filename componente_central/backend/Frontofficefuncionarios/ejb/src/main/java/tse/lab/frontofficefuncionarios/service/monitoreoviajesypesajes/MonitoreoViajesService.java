package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficefuncionarios.data.CasoFiscalizacionDAO;
import tse.lab.frontofficefuncionarios.data.CiudadanoDAO;
import tse.lab.frontofficefuncionarios.data.EventoPesajesDAO;
import tse.lab.frontofficefuncionarios.data.EventoViajeDAO;
import tse.lab.frontofficefuncionarios.data.EventoViajeFila;
import tse.lab.frontofficefuncionarios.data.GuiaDAO;
import tse.lab.frontofficefuncionarios.data.ModificacionGuiaDAO;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.LogFuncionarios;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.model.Chofer;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;
import tse.lab.model.ResultadoEventoViaje;
import tse.lab.model.TipoEventoViaje;
import tse.lab.model.Vehiculo;

// CU Monitorear viajes y pesajes (funcionario): listado de los viajes de todas las empresas y detalle de
// uno con su linea de tiempo (eventos que registro el chofer desde la app movil y modificaciones del
// responsable), los pesajes del vehiculo durante el viaje y los casos de fiscalizacion que genero. Solo
// consultas: cada operacion verifica
// primero que quien llama sea funcionario y no modifica nada.
@Stateless
public class MonitoreoViajesService {

    private static final Logger LOG = Logger.getLogger(MonitoreoViajesService.class.getName());

    // Un viaje es una guia con vehiculo y chofer asignados: ASIGNADA (por empezar), EN_CURSO o FINALIZADA.
    // Las SIN_ASIGNAR y CANCELADA no se muestran, ni en el listado ni en el detalle
    static final List<EstadoGuia> VISIBLES = List.of(EstadoGuia.ASIGNADA, EstadoGuia.EN_CURSO,
            EstadoGuia.FINALIZADA);

    static final int TAMANIO_POR_DEFECTO = 20;
    static final int TAMANIO_MAXIMO = 100;

    @EJB
    private AutorizacionFuncionario autorizacion;
    @EJB
    private GuiaDAO guias;
    @EJB
    private EventoViajeDAO eventos;
    @EJB
    private ModificacionGuiaDAO modificaciones;
    @EJB
    private EventoPesajesDAO pesajes;
    @EJB
    private CiudadanoDAO ciudadanos;
    @EJB
    private CasoFiscalizacionDAO casos;

    // Solo primer nombre y primer apellido del chofer (Ley 18.331)
    public record ChoferVista(String ci, String primerNombre, String primerApellido) {
    }

    public record ViajeResumen(String id, String fecha, String origen, String destino, String estado,
            Long nroEmpresa, String empresa, String matricula, ChoferVista chofer) {
    }

    public record PaginaViajes(List<ViajeResumen> viajes, int pagina, int tamanio, long total, int totalPaginas) {
    }

    public record VehiculoVista(String matricula, String marca, String modelo, long pesoKg, long capacidadCargaKg,
            long pesoMaximoKg) {
    }

    public record EventoVista(String idEvento, String tipo, String fechaHoraDispositivo, String fechaRecepcion,
            ChoferVista chofer, String descripcion, Double latitud, Double longitud, boolean conFoto,
            String resultado, String codigo, String detalle) {
    }

    public record ModificacionVista(String tipo, String fecha, String ciResponsable, String cambios, String motivo) {
    }

    // excesoKg: lo que el pesaje supera al peso del vehiculo mas su capacidad
    // declarada (0 si no supera)
    public record PesajeVista(String fechaHora, String balanza, long pesoKg, long excesoKg) {
    }

    // Caso de fiscalizacion generado durante el viaje (el detalle completo esta en /casos/{id})
    public record CasoVista(Long id, String tipo, String descripcion, String estado, String fechaDeteccion) {
    }

    public record ViajeDetalle(String id, String fecha, String origen, String destino, String rubroCliente,
            String tipoCarga, long pesoCargaKg, String estado, String fechaAsignacion, Long nroEmpresa,
            String empresa, VehiculoVista vehiculo, ChoferVista chofer, String inicio, String fin,
            List<EventoVista> eventos, List<ModificacionVista> modificaciones, List<PesajeVista> pesajes,
            List<CasoVista> casos) {
    }

    public PaginaViajes listar(String ci, String estado, String matricula, String nroEmpresa, String pagina,
            String tamanio, String correlationId) throws NoAutorizadoException, DatosInvalidosException {
        autorizacion.funcionario(ci, correlationId);
        Map<String, String> errores = new LinkedHashMap<>();
        EstadoGuia filtroEstado = null;
        if (estado != null && !estado.isBlank()) {
            filtroEstado = estadoValido(estado);
            if (filtroEstado == null || !VISIBLES.contains(filtroEstado)) {
                errores.put("estado", "El estado tiene que ser uno de " + VISIBLES + ".");
            }
        }
        String filtroMatricula = matricula == null || matricula.isBlank() ? null
                : Vehiculo.normalizarMatricula(matricula);
        Long filtroEmpresa = null;
        if (nroEmpresa != null && !nroEmpresa.isBlank()) {
            filtroEmpresa = largo(nroEmpresa);
            if (filtroEmpresa == null || filtroEmpresa < 1) {
                errores.put("nroEmpresa", "El número de empresa tiene que ser un entero positivo.");
            }
        }
        Integer nroPagina = entero(pagina, 1);
        if (nroPagina == null || nroPagina < 1) {
            errores.put("pagina", "La página tiene que ser un número entero mayor o igual a 1.");
        }
        Integer cantidad = entero(tamanio, TAMANIO_POR_DEFECTO);
        if (cantidad == null || cantidad < 1 || cantidad > TAMANIO_MAXIMO) {
            errores.put("tamanio",
                    "El tamaño de página tiene que ser un número entero entre 1 y " + TAMANIO_MAXIMO + ".");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        List<EstadoGuia> estados = filtroEstado == null ? VISIBLES : List.of(filtroEstado);
        long total = guias.contar(estados, filtroMatricula, filtroEmpresa);
        int totalPaginas = (int) ((total + cantidad - 1) / cantidad);
        // long: con paginas muy altas (nroPagina - 1) * cantidad desborda un int
        long desde = (long) (nroPagina - 1) * cantidad;
        List<ViajeResumen> lista = desde >= total ? List.of()
                : guias.listar(estados, filtroMatricula, filtroEmpresa, (int) desde, cantidad).stream()
                        .map(MonitoreoViajesService::resumen).toList();
        final EstadoGuia e = filtroEstado;
        final Long emp = filtroEmpresa;
        LOG.info(() -> LogFuncionarios.evento("viajes.listado").correlationId(correlationId).usuario(ci)
                .campo("estado", e).campo("matricula", filtroMatricula).campo("nroEmpresa", emp)
                .campo("pagina", nroPagina).campo("tamanio", cantidad).campo("cantidad", lista.size())
                .campo("total", total).toString());
        return new PaginaViajes(lista, nroPagina, cantidad, total, totalPaginas);
    }

    public ViajeDetalle detalle(String ci, String idGuia, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        Guia g = guia(idGuia, ci, correlationId);
        List<EventoViajeFila> filas = eventos.buscarPorGuia(g);
        // Los choferes de los eventos suelen ser uno o dos: se busca cada ciudadano una
        // sola vez
        Map<String, ChoferVista> choferes = new HashMap<>();
        List<EventoVista> lineaDeTiempo = filas.stream()
                .map(f -> new EventoVista(f.idEvento(), texto(f.tipo()), texto(f.fechaHoraDispositivo()),
                        texto(f.fechaRecepcion()), choferes.computeIfAbsent(f.ciChofer(), this::chofer),
                        f.descripcion(), f.latitud(), f.longitud(), Boolean.TRUE.equals(f.conFoto()),
                        resultado(f).name(), f.codigo(), f.detalle()))
                .toList();
        List<ModificacionVista> mods = modificaciones.buscarPorGuia(g).stream()
                .map(m -> new ModificacionVista(texto(m.getTipo()), texto(m.getFecha()), m.getCiResponsable(),
                        m.getCambios(), m.getMotivo()))
                .toList();

        Instant inicio = horaAplicado(filas, TipoEventoViaje.INICIO);
        Instant fin = horaAplicado(filas, TipoEventoViaje.FIN);
        Vehiculo v = g.getVehiculo();
        long pesoMaximo = v == null ? 0 : v.getPeso() + v.getCapacidadCargaKg();
        // Las guias en viaje sin un INICIO registrado (cargadas directamente en la
        // base) usan la asignacion. Una ASIGNADA todavia no empezo: no tiene pesajes
        Instant desde = inicio != null ? inicio : g.getFechaAsignacion();
        List<PesajeVista> pesajesDelViaje = v == null || desde == null || estado(g) == EstadoGuia.ASIGNADA
                ? List.of()
                : pesajes.buscarPorMatriculaEntre(v.getMatricula(), desde, fin).stream()
                        .map(p -> new PesajeVista(texto(p.getFechaHora()),
                                p.getNodoBalanza() == null ? null : p.getNodoBalanza().getNombre(), p.getPeso(),
                                Math.max(0, p.getPeso() - pesoMaximo)))
                        .toList();
        List<CasoVista> casosDelViaje = casos.buscarPorGuia(g).stream()
                .map(c -> new CasoVista(c.getId(), c.getTipoCaso(), c.getDescripcion(), texto(c.getEstado()),
                        texto(c.getFechaDeteccion())))
                .toList();

        Empresa e = g.getEmpresa();
        ViajeDetalle detalle = new ViajeDetalle(g.getId(), texto(g.getFecha()), g.getOrigen(), g.getDestino(),
                g.getRubroCliente(), g.getTipoCarga(), g.getPesoCargaKg(), texto(estado(g)),
                texto(g.getFechaAsignacion()), e == null ? null : e.getNroEmpresa(),
                e == null ? null : e.getNombrePublico(),
                v == null ? null
                        : new VehiculoVista(v.getMatricula(), v.getMarca(), v.getModelo(), v.getPeso(),
                                v.getCapacidadCargaKg(), pesoMaximo),
                chofer(g.getChofer()), texto(inicio), texto(fin), lineaDeTiempo, mods, pesajesDelViaje,
                casosDelViaje);
        LOG.info(() -> LogFuncionarios.evento("viaje.consulta").correlationId(correlationId).usuario(ci)
                .campo("guia", idGuia).campo("estado", detalle.estado()).campo("eventos", lineaDeTiempo.size())
                .campo("modificaciones", mods.size()).campo("pesajes", pesajesDelViaje.size())
                .campo("casos", casosDelViaje.size()).toString());
        return detalle;
    }

    // Foto de un evento (incidente) del viaje. 404 VIAJE_NO_ENCONTRADO si la guia
    // no es un viaje visible,
    // EVENTO_NO_ENCONTRADO si el evento no es de esa guia y SIN_FOTO si no tiene.
    public byte[] fotoEvento(String ci, String idGuia, String idEvento, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        guia(idGuia, ci, correlationId);
        List<byte[]> resultado = idEvento == null ? List.of() : eventos.foto(idGuia, idEvento);
        if (resultado.isEmpty()) {
            LOG.info(() -> LogFuncionarios.evento("viaje.foto").correlationId(correlationId).usuario(ci)
                    .campo("guia", idGuia).campo("idEvento", idEvento).campo("resultado", "no_encontrado")
                    .toString());
            throw new RecursoNoEncontradoException("EVENTO_NO_ENCONTRADO", "El evento no existe en este viaje.");
        }
        byte[] foto = resultado.get(0);
        if (foto == null) {
            throw new RecursoNoEncontradoException("SIN_FOTO", "El evento no tiene foto.");
        }
        LOG.info(() -> LogFuncionarios.evento("viaje.foto").correlationId(correlationId).usuario(ci)
                .campo("guia", idGuia).campo("idEvento", idEvento).campo("bytes", foto.length).toString());
        return foto;
    }

    private static Instant horaAplicado(List<EventoViajeFila> filas, TipoEventoViaje tipo) {
        return filas.stream()
                .filter(f -> f.tipo() == tipo && resultado(f) == ResultadoEventoViaje.APLICADO)
                .map(EventoViajeFila::fechaHoraDispositivo)
                .findFirst()
                .orElse(null);
    }

    private static ResultadoEventoViaje resultado(EventoViajeFila f) {
        return f.resultado() == null ? ResultadoEventoViaje.APLICADO : f.resultado();
    }

    private static EstadoGuia estado(Guia g) {
        return g.getEstado() == null ? EstadoGuia.SIN_ASIGNAR : g.getEstado();
    }

    // La guia, si es un viaje visible (VISIBLES); si no, 404 como si no existiera
    private Guia guia(String idGuia, String ci, String correlationId) throws RecursoNoEncontradoException {
        Guia g = idGuia == null ? null : guias.buscarPorId(idGuia);
        if (g == null || !VISIBLES.contains(g.getEstado())) {
            LOG.info(() -> LogFuncionarios.evento("viaje.no_encontrado").correlationId(correlationId).usuario(ci)
                    .campo("guia", idGuia).toString());
            throw new RecursoNoEncontradoException("VIAJE_NO_ENCONTRADO", "El viaje no existe.");
        }
        return g;
    }

    private static ViajeResumen resumen(Guia g) {
        Empresa e = g.getEmpresa();
        Vehiculo v = g.getVehiculo();
        return new ViajeResumen(g.getId(), texto(g.getFecha()), g.getOrigen(), g.getDestino(), texto(estado(g)),
                e == null ? null : e.getNroEmpresa(), e == null ? null : e.getNombrePublico(),
                v == null ? null : v.getMatricula(), chofer(g.getChofer()));
    }

    private static ChoferVista chofer(Chofer c) {
        if (c == null) {
            return null;
        }
        Ciudadano p = c.getCiudadano();
        return new ChoferVista(c.getCi(), p == null ? null : p.getPrimerNombre(),
                p == null ? null : p.getPrimerApellido());
    }

    private ChoferVista chofer(String ci) {
        Ciudadano p = ci == null ? null : ciudadanos.buscarPorCi(ci);
        return new ChoferVista(ci, p == null ? null : p.getPrimerNombre(), p == null ? null : p.getPrimerApellido());
    }

    // null si no es un entero; porDefecto si no vino
    private static Integer entero(String valor, int porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long largo(String valor) {
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static EstadoGuia estadoValido(String valor) {
        try {
            return EstadoGuia.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
