package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficefuncionarios.data.AuditoriaCasoDAO;
import tse.lab.frontofficefuncionarios.data.CasoFiscalizacionDAO;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.frontofficefuncionarios.service.ConflictoException;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Descargo;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EventoPesajes;
import tse.lab.model.Funcionario;
import tse.lab.model.FundamentoResolucion;
import tse.lab.model.Guia;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.RegistroAuditoriaCaso;
import tse.lab.model.Vehiculo;

// CU Gestionar casos de fiscalizacion (funcionario): bandeja de trabajo, detalle del caso, evidencia,
// descargo de la empresa y resolucion (confirmar o desestimar con fundamento). Cada operacion verifica
// primero que quien llama sea funcionario (FA1). Las consultas no modifican el caso; la resolucion
// cambia el estado solo por una transicion prevista (FA6) y queda en la pista de auditoria.
@Stateless
public class GestionCasosService {

    private static final Logger LOG = Logger.getLogger(GestionCasosService.class.getName());

    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");

    static final int TAMANIO_POR_DEFECTO = 20;
    static final int TAMANIO_MAXIMO = 100;

    @EJB
    private AutorizacionFuncionario autorizacion;
    @EJB
    private CasoFiscalizacionDAO casos;
    @EJB
    private AuditoriaCasoDAO auditoria;

    public enum Decision {
        CONFIRMAR(EstadoCaso.CONFIRMADA),
        DESESTIMAR(EstadoCaso.DESESTIMADA);

        private final EstadoCaso estadoResultante;

        Decision(EstadoCaso estadoResultante) {
            this.estadoResultante = estadoResultante;
        }

        public EstadoCaso estadoResultante() {
            return estadoResultante;
        }
    }

    public record CasoResumen(Long id, String tipoCaso, String descripcion, String estado, String fechaDeteccion,
            String idGuia, Long nroEmpresa, String empresa, String matricula, boolean tieneDescargo,
            boolean puedeResolverse) {
    }

    // pagina empieza en 1; totalPaginas es 0 si no hay casos
    public record PaginaCasos(List<CasoResumen> casos, int pagina, int tamanio, long total, int totalPaginas) {
    }

    public record GuiaVista(String id, String fecha, String origen, String destino, String rubroCliente,
            String tipoCarga, long pesoCargaKg, String estado) {
    }

    public record ResolucionVista(String decision, String fundamento, String ciFuncionario, String fechaHora) {
    }

    public record RegistroVista(String fechaHora, String usuario, String accion, String estadoAnterior,
            String estadoNuevo, String fundamento) {
    }

    public record CasoDetalle(Long id, String tipoCaso, String descripcion, String estado, String fechaDeteccion,
            Long nroEmpresa, String empresa, GuiaVista guia, String matricula, boolean tieneDescargo,
            boolean puedeResolverse, List<String> estadosSiguientes, ResolucionVista resolucion,
            List<RegistroVista> historial) {
    }

    // El permiso es uno por vehiculo y se renueva en el lugar: si emisionPermiso es posterior a la deteccion,
    // no se sabe cual estaba vigente ese dia. vencimientoItv es el de la ITV vigente al detectarse.
    public record VehiculoVista(String matricula, String marca, String modelo, long pesoKg, long capacidadCargaKg,
            long pesoMaximoKg, String nroPermiso, String emisionPermiso, String vencimientoPermiso,
            String vencimientoItv) {
    }

    // excesoKg: lo que el pesaje supera al peso del vehiculo mas su capacidad
    // declarada (0 si no supera)
    public record PesajeVista(String fechaHora, String balanza, long pesoKg, long excesoKg) {
    }

    // Un evento de tracking (MongoDB) se identifica por el vehiculo y el momento
    public record EventoTrackingVista(String matricula, String fechaHora) {
    }

    public record EvidenciaVista(Long idCaso, String tipoCaso, String descripcion, String fechaDeteccion,
            GuiaVista guia, VehiculoVista vehiculo, List<PesajeVista> pesajes,
            List<EventoTrackingVista> eventosTracking) {
    }

    // fechaPresentacion y ciPresentante: cuando y quien (responsable de la empresa) lo presento; null en
    // descargos cargados antes de registrarlos
    public record DescargoVista(Long idCaso, Long id, String descripcion, String estado, String fechaPresentacion,
            String ciPresentante) {
    }

    // decision: CONFIRMAR o DESESTIMAR
    public record Resolucion(String decision, String fundamento) {
    }

    public record ResolucionRegistrada(String mensaje, CasoDetalle caso) {
    }

    // bandeja paginada con los casos, opcionalmente filtrada por estado. pagina (desde 1) y tamanio son
    // opcionales: por defecto la primera pagina de TAMANIO_POR_DEFECTO casos. Una pagina posterior a la
    // ultima devuelve la lista vacia (con el total, para que el cliente pueda volver).
    public PaginaCasos bandeja(String ci, String estado, String pagina, String tamanio, String correlationId)
            throws NoAutorizadoException, DatosInvalidosException {
        autorizacion.funcionario(ci, correlationId);
        Map<String, String> errores = new LinkedHashMap<>();
        EstadoCaso filtro = null;
        if (estado != null && !estado.isBlank()) {
            filtro = estadoValido(estado);
            if (filtro == null) {
                errores.put("estado", "El estado tiene que ser uno de " + List.of(EstadoCaso.values()) + ".");
            }
        }
        Integer nroPagina = entero(pagina, 1);
        if (nroPagina == null || nroPagina < 1) {
            errores.put("pagina", "La página tiene que ser un número entero mayor o igual a 1.");
        }
        Integer cantidad = entero(tamanio, TAMANIO_POR_DEFECTO);
        if (cantidad == null || cantidad < 1 || cantidad > TAMANIO_MAXIMO) {
            errores.put("tamanio", "El tamaño de página tiene que ser un número entero entre 1 y " + TAMANIO_MAXIMO + ".");
        }
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        long total = casos.contarBandeja(filtro);
        int totalPaginas = (int) ((total + cantidad - 1) / cantidad);
        // long: con paginas muy altas (nroPagina - 1) * cantidad desborda un int
        long desde = (long) (nroPagina - 1) * cantidad;
        List<CasoResumen> lista = desde >= total ? List.of()
                : casos.bandeja(filtro, (int) desde, cantidad).stream().map(GestionCasosService::resumen).toList();
        final EstadoCaso f = filtro;
        LOG.info(() -> "evento=casos.bandeja correlationId=" + correlationId + " usuario=" + ci + " estado=" + f
                + " pagina=" + nroPagina + " tamanio=" + cantidad + " cantidad=" + lista.size() + " total=" + total);
        return new PaginaCasos(lista, nroPagina, cantidad, total, totalPaginas);
    }

    public CasoDetalle detalle(String ci, Long idCaso, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        CasoFiscalizacion caso = caso(idCaso, ci, correlationId);
        LOG.info(() -> "evento=caso.consulta correlationId=" + correlationId + " usuario=" + ci + " caso=" + idCaso
                + " estado=" + caso.getEstado());
        return detalle(caso);
    }

    // Flujo Alt 2: la evidencia que origino el caso (guia, datos declarados del
    // vehiculo y pesajes)
    public EvidenciaVista evidencia(String ci, Long idCaso, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        CasoFiscalizacion caso = caso(idCaso, ci, correlationId);
        Guia guia = caso.getGuia();
        Vehiculo v = vehiculo(caso);
        long pesoMaximo = v == null ? 0 : v.getPeso() + v.getCapacidadCargaKg();
        VehiculoVista vehiculo = null;
        if (v != null) {
            // Habilitacion al dia de la deteccion (en Uruguay): una ITV sacada despues no oculta que estaba vencida
            LocalDate dia = LocalDate.ofInstant(caso.getFechaDeteccion(), URUGUAY);
            PermisoCirculacion permiso = casos.permisoDelVehiculo(v);
            ITV itv = casos.itvVigenteAl(v, dia);
            vehiculo = new VehiculoVista(v.getMatricula(), v.getMarca(), v.getModelo(), v.getPeso(),
                    v.getCapacidadCargaKg(), pesoMaximo, permiso == null ? null : permiso.getNro(),
                    permiso == null ? null : texto(permiso.getFechaEmision()),
                    permiso == null ? null : texto(permiso.getFechaVencimiento()),
                    itv == null ? null : texto(itv.getFechaVencimiento()));
        }
        List<PesajeVista> pesajes = caso.getEventosPesajes().stream()
                .sorted(Comparator.comparing(EventoPesajes::getFechaHora,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new PesajeVista(texto(p.getFechaHora()),
                        p.getNodoBalanza() == null ? null : p.getNodoBalanza().getNombre(), p.getPeso(),
                        v == null ? 0 : Math.max(0, p.getPeso() - pesoMaximo)))
                .toList();
        EvidenciaVista evidencia = new EvidenciaVista(caso.getId(), caso.getTipoCaso(), caso.getDescripcion(),
                texto(caso.getFechaDeteccion()), guia(guia), vehiculo, pesajes,
                caso.getClavesEventosTracking().stream()
                        .map(t -> new EventoTrackingVista(t.getMatricula(), texto(t.getFechaHora())))
                        .toList());
        LOG.info(() -> "evento=caso.evidencia correlationId=" + correlationId + " usuario=" + ci + " caso=" + idCaso
                + " pesajes=" + pesajes.size() + " eventosTracking=" + evidencia.eventosTracking().size());
        return evidencia;
    }

    // Flujo Alt3: el descargo presentado por la empresa. Flujo Alt 4: si no hay,
    // 404 SIN_DESCARGO y el caso no cambia.
    public DescargoVista descargo(String ci, Long idCaso, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        CasoFiscalizacion caso = caso(idCaso, ci, correlationId);
        Descargo d = caso.getDescargo();
        if (d == null) {
            LOG.info(() -> "evento=caso.descargo correlationId=" + correlationId + " usuario=" + ci + " caso="
                    + idCaso + " resultado=sin_descargo");
            throw new RecursoNoEncontradoException("SIN_DESCARGO",
                    "La empresa no presentó un descargo para este caso.");
        }
        LOG.info(() -> "evento=caso.descargo correlationId=" + correlationId + " usuario=" + ci + " caso=" + idCaso
                + " descargo=" + d.getId());
        return new DescargoVista(caso.getId(), d.getId(), d.getDescripcion(), d.getEstado(),
                texto(d.getFechaPresentacion()), d.getCiPresentante());
    }

    // FA5: resolver el caso (confirmar o desestimar) con fundamento. FA6: si el
    // estado actual no admite
    // resolucion, 409 TRANSICION_NO_PERMITIDA y el caso conserva su estado (la
    // excepcion hace rollback).
    public ResolucionRegistrada resolver(String ci, Long idCaso, Resolucion datos, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException,
            DatosInvalidosException {
        Funcionario funcionario = autorizacion.funcionario(ci, correlationId);
        CasoFiscalizacion caso = idCaso == null ? null : casos.buscarPorIdParaActualizar(idCaso);
        if (caso == null) {
            throw noEncontrado(idCaso, ci, correlationId);
        }
        EstadoCaso anterior = caso.getEstado();
        if (anterior == null || !anterior.admiteResolucion()) {
            LOG.warning(() -> "evento=caso.resolucion.rechazada correlationId=" + correlationId + " usuario=" + ci
                    + " caso=" + idCaso + " estado=" + anterior + " motivo=TRANSICION_NO_PERMITIDA");
            throw new ConflictoException("TRANSICION_NO_PERMITIDA",
                    "El caso no puede ser resuelto en su estado actual (" + anterior + ").");
        }

        Map<String, String> errores = new LinkedHashMap<>();
        Decision decision = decision(datos == null ? null : datos.decision());
        if (decision == null) {
            errores.put("decision", "La decisión tiene que ser CONFIRMAR o DESESTIMAR.");
        }
        String fundamento = datos == null || datos.fundamento() == null ? "" : datos.fundamento().trim();
        if (fundamento.isEmpty()) {
            errores.put("fundamento", "El fundamento de la resolución es obligatorio.");
        } else if (fundamento.length() > RegistroAuditoriaCaso.MAX_FUNDAMENTO) {
            errores.put("fundamento", "El fundamento no puede tener más de " + RegistroAuditoriaCaso.MAX_FUNDAMENTO
                    + " caracteres.");
        }
        if (!errores.isEmpty()) {
            LOG.warning(() -> "evento=caso.resolucion.rechazada correlationId=" + correlationId + " usuario=" + ci
                    + " caso=" + idCaso + " motivo=datos_invalidos campos=" + errores.keySet());
            throw new DatosInvalidosException(errores);
        }

        EstadoCaso nuevo = decision.estadoResultante();
        caso.cambiarEstado(nuevo);
        FundamentoResolucion resolucion = new FundamentoResolucion(fundamento, funcionario, caso);
        casos.guardarResolucion(resolucion);
        caso.setFundamentoResolucion(resolucion);
        auditoria.registrar(new RegistroAuditoriaCaso(caso.getId(), funcionario.getCi(),
                AccionAuditoriaCaso.RESOLUCION, anterior, nuevo, fundamento, correlationId));
        LOG.info(() -> "evento=caso.resuelto correlationId=" + correlationId + " usuario=" + ci + " caso=" + idCaso
                + " estado=" + anterior + "->" + nuevo);
        return new ResolucionRegistrada("La resolución fue registrada.", detalle(caso));
    }

    private CasoFiscalizacion caso(Long idCaso, String ci, String correlationId) throws RecursoNoEncontradoException {
        CasoFiscalizacion caso = idCaso == null ? null : casos.buscarPorId(idCaso);
        if (caso == null) {
            throw noEncontrado(idCaso, ci, correlationId);
        }
        return caso;
    }

    private static RecursoNoEncontradoException noEncontrado(Long idCaso, String ci, String correlationId) {
        LOG.info(() -> "evento=caso.no_encontrado correlationId=" + correlationId + " usuario=" + ci + " caso="
                + idCaso);
        return new RecursoNoEncontradoException("CASO_NO_ENCONTRADO", "El caso de fiscalización no existe.");
    }

    private CasoDetalle detalle(CasoFiscalizacion caso) {
        Guia g = caso.getGuia();
        Vehiculo v = vehiculo(caso);
        Empresa e = empresa(caso);
        FundamentoResolucion f = caso.getFundamentoResolucion();
        ResolucionVista resolucion = f == null ? null
                : new ResolucionVista(caso.getEstado() == EstadoCaso.CONFIRMADA ? Decision.CONFIRMAR.name()
                        : Decision.DESESTIMAR.name(), f.getDescripcion(), f.getFuncionario().getCi(),
                        texto(f.getFechaHora()));
        List<RegistroVista> historial = auditoria.historialDelCaso(caso.getId()).stream()
                .map(r -> new RegistroVista(texto(r.getFechaHora()), r.getUsuario(), r.getAccion().name(),
                        r.getEstadoAnterior() == null ? null : r.getEstadoAnterior().name(), r.getEstadoNuevo().name(),
                        r.getFundamento()))
                .toList();
        EstadoCaso estado = caso.getEstado();
        return new CasoDetalle(caso.getId(), caso.getTipoCaso(), caso.getDescripcion(), texto(estado),
                texto(caso.getFechaDeteccion()), e == null ? null : e.getNroEmpresa(),
                e == null ? null : e.getNombrePublico(), guia(g), v == null ? null : v.getMatricula(),
                caso.getDescargo() != null, estado != null && estado.admiteResolucion(),
                estado == null ? List.of() : estado.siguientes().stream().map(Enum::name).sorted().toList(),
                resolucion, historial);
    }

    private static CasoResumen resumen(CasoFiscalizacion c) {
        Guia g = c.getGuia();
        Vehiculo v = vehiculo(c);
        Empresa e = empresa(c);
        EstadoCaso estado = c.getEstado();
        return new CasoResumen(c.getId(), c.getTipoCaso(), c.getDescripcion(), texto(estado),
                texto(c.getFechaDeteccion()), g == null ? null : g.getId(), e == null ? null : e.getNroEmpresa(),
                e == null ? null : e.getNombrePublico(), v == null ? null : v.getMatricula(),
                c.getDescargo() != null, estado != null && estado.admiteResolucion());
    }

    // El vehiculo del caso; los casos cargados antes de que el caso tuviera vehiculo lo toman de la guia
    private static Vehiculo vehiculo(CasoFiscalizacion c) {
        if (c.getVehiculo() != null) {
            return c.getVehiculo();
        }
        return c.getGuia() == null ? null : c.getGuia().getVehiculo();
    }

    // La empresa de la guia o, sin guia (Viaje sin guia), la duenia del vehiculo
    private static Empresa empresa(CasoFiscalizacion c) {
        if (c.getGuia() != null) {
            return c.getGuia().getEmpresa();
        }
        Vehiculo v = vehiculo(c);
        return v == null ? null : v.getEmpresa();
    }

    // null si el caso no tiene guia (Viaje sin guia)
    private static GuiaVista guia(Guia g) {
        if (g == null) {
            return null;
        }
        return new GuiaVista(g.getId(), texto(g.getFecha()), g.getOrigen(), g.getDestino(), g.getRubroCliente(),
                g.getTipoCarga(), g.getPesoCargaKg(), texto(g.getEstado()));
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

    private static EstadoCaso estadoValido(String valor) {
        try {
            return EstadoCaso.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Decision decision(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Decision.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
