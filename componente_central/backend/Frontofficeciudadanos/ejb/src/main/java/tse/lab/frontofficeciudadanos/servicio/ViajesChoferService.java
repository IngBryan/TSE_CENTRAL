package tse.lab.frontofficeciudadanos.servicio;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.EventoViajeDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.EstadoChofer;
import tse.lab.frontofficeciudadanos.model.EstadoGuia;
import tse.lab.frontofficeciudadanos.model.EventoViaje;
import tse.lab.frontofficeciudadanos.model.Guia;
import tse.lab.frontofficeciudadanos.model.ResultadoEventoViaje;
import tse.lab.frontofficeciudadanos.model.TipoEventoViaje;
import tse.lab.frontofficeciudadanos.model.Vehiculo;

// App movil del chofer: sus viajes pendientes y los eventos que registra durante el viaje (inicio,
// fin, cargas y descargas parciales, incidentes). El movil trabaja sin conexion y reenvia los
// eventos cuando vuelve la red, por eso el registro es idempotente por idEvento (FA2).
@Stateless
public class ViajesChoferService {

    private static final Logger LOG = Logger.getLogger(ViajesChoferService.class.getName());
    static final int MAX_DESCRIPCION = 500;
    private static final List<EstadoGuia> PENDIENTES = List.of(EstadoGuia.ASIGNADA, EstadoGuia.EN_CURSO);
    private static final Pattern UUID = Pattern
            .compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    // Base64 de MAX_FOTO_BYTES (4 caracteres cada 3 bytes)
    private static final int MAX_FOTO_BASE64 = (EventoViaje.MAX_FOTO_BYTES + 2) / 3 * 4;

    @EJB
    private ChoferDAO choferes;
    @EJB
    private GuiaDAO guias;
    @EJB
    private EventoViajeDAO eventos;

    public record ViajeVista(String id, Long nroEmpresa, String empresa, String rubroCliente, String tipoCarga,
            long pesoCargaKg,
            String fecha, String origen, String destino, String matricula, String vehiculo, String estado,
            Long version) {
    }

    // fechaHora: ISO-8601 con zona (2026-10-11T21:30:00-03:00 o ...Z). foto: JPEG o PNG en base64.
    // versionGuia: la version de la guia que conocia el movil al enviar (deteccion de guia modificada).
    public record NuevoEvento(String idEvento, String idGuia, String tipo, String fechaHora, String descripcion,
            Double latitud, Double longitud, String foto, Long versionGuia) {
    }

    // resultado: APLICADO, SIN_EFECTO o CONFLICTO (ResultadoEventoViaje); codigo/detalle lo explican.
    // guiaModificada: la guia cambio desde la version que conocia el movil; guia trae sus datos actuales
    // (null si ya no es de este chofer) para que el movil se actualice sin esperar al proximo refresco.
    public record ResultadoEvento(String idEvento, String idGuia, String tipo, String resultado, String codigo,
            String detalle, String estadoGuia, Long version, String recibido, boolean duplicado,
            boolean guiaModificada, ViajeVista guia) {
    }

    // Que hacer con un evento segun el estado actual de la guia (politica de resolucion de conflictos)
    record Decision(ResultadoEventoViaje resultado, EstadoGuia nuevoEstado, String codigo, String detalle) {
        static Decision aplicado(EstadoGuia nuevo) {
            return new Decision(ResultadoEventoViaje.APLICADO, nuevo, null, null);
        }

        static Decision sinEfecto(EstadoGuia actual, String codigo, String detalle) {
            return new Decision(ResultadoEventoViaje.SIN_EFECTO, actual, codigo, detalle);
        }

        static Decision conflicto(EstadoGuia actual, String codigo, String detalle) {
            return new Decision(ResultadoEventoViaje.CONFLICTO, actual, codigo, detalle);
        }
    }

    // Guias ASIGNADA o EN_CURSO del chofer, en todas las empresas donde esta HABILITADO. Lista vacia si
    // no tiene viajes (FA2); 403 si la persona no es chofer habilitado de ninguna empresa.
    public List<ViajeVista> viajes(String ci, String correlationId) throws NoAutorizadoException {
        List<Chofer> habilitados = choferesHabilitados(ci, correlationId);
        List<ViajeVista> lista = guias.buscarPorChoferesYEstados(habilitados, PENDIENTES).stream()
                .map(ViajesChoferService::vista)
                .toList();
        LOG.info(() -> "evento=movil.viajes correlationId=" + correlationId + " usuario=" + ci + " cantidad="
                + lista.size());
        return lista;
    }

    public ResultadoEvento registrarEvento(String ci, NuevoEvento datos, String correlationId)
            throws NoAutorizadoException, DatosInvalidosException, RecursoNoEncontradoException, ConflictoException {
        List<Chofer> habilitados = choferesHabilitados(ci, correlationId);

        Map<String, String> errores = new LinkedHashMap<>();
        String idEvento = datos == null || datos.idEvento() == null ? null
                : datos.idEvento().trim().toLowerCase(Locale.ROOT);
        if (idEvento == null || !UUID.matcher(idEvento).matches()) {
            errores.put("idEvento", "El idEvento tiene que ser un UUID.");
        }
        String idGuia = datos == null || datos.idGuia() == null || datos.idGuia().isBlank() ? null
                : datos.idGuia().trim();
        if (idGuia == null) {
            errores.put("idGuia", "La guía es obligatoria.");
        }
        TipoEventoViaje tipo = null;
        try {
            tipo = datos == null || datos.tipo() == null ? null
                    : TipoEventoViaje.valueOf(datos.tipo().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // queda null y se informa abajo
        }
        if (tipo == null) {
            errores.put("tipo", "El tipo tiene que ser INICIO, FIN, CARGA_PARCIAL, DESCARGA_PARCIAL o INCIDENTE.");
        }
        Instant fechaHora = fechaHora(datos == null ? null : datos.fechaHora());
        if (fechaHora == null) {
            errores.put("fechaHora", "La fecha y hora del evento tiene que estar en formato ISO-8601 con zona.");
        }
        String descripcion = datos == null || datos.descripcion() == null ? null
                : datos.descripcion().trim().replaceAll("\\s+", " ");
        if (descripcion != null && descripcion.isEmpty()) {
            descripcion = null;
        }
        if (descripcion != null && descripcion.length() > MAX_DESCRIPCION) {
            errores.put("descripcion", "La descripción no puede tener más de " + MAX_DESCRIPCION + " caracteres.");
        } else if (tipo == TipoEventoViaje.INCIDENTE && descripcion == null) {
            errores.put("descripcion", "Describí el incidente.");
        }
        Double latitud = datos == null ? null : datos.latitud();
        Double longitud = datos == null ? null : datos.longitud();
        if ((latitud == null) != (longitud == null)
                || latitud != null && (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180)) {
            errores.put("ubicacion", "La ubicación no es válida.");
        }
        byte[] foto = foto(errores, datos == null ? null : datos.foto());
        if (!errores.isEmpty()) {
            LOG.warning(() -> "evento=movil.evento.rechazado correlationId=" + correlationId + " usuario=" + ci
                    + " idEvento=" + (datos == null ? null : datos.idEvento()) + " motivo=datos_invalidos campos="
                    + errores.keySet());
            throw new DatosInvalidosException(errores);
        }

        // Se bloquea la guia antes de mirar si el evento ya existe: si llegan dos envios del mismo
        // evento a la vez, el segundo espera al primero y despues lo encuentra (respuesta original).
        Guia guia = guias.buscarPorIdParaActualizar(idGuia);
        Long versionGuia = datos.versionGuia();
        EventoViaje previo = eventos.buscarPorId(idEvento);
        if (previo != null) {
            if (!previo.getCiChofer().equals(ci)) {
                throw rechazo(correlationId, ci, idEvento, idGuia,
                        new ConflictoException("ID_EVENTO_EN_USO", "El identificador del evento ya fue usado."));
            }
            LOG.info(() -> "evento=movil.evento.duplicado correlationId=" + correlationId + " usuario=" + ci
                    + " idEvento=" + idEvento + " guia=" + previo.getGuia().getId());
            // en un reenvio: la guia cambio despues de procesar el evento original
            return resultado(previo, true,
                    !Objects.equals(previo.getGuia().getVersion(), previo.getVersionGuiaResultante()), habilitados);
        }
        // La guia tiene que existir y ser de una empresa donde la persona es chofer habilitado. Si es de
        // la empresa pero esta asignada a otro chofer (reasignada mientras el movil estaba sin conexion),
        // el evento se guarda como CONFLICTO (caso 2 de la politica); si no, 404 y no se guarda nada.
        if (guia == null || guia.getEmpresa() == null || habilitados.stream()
                .noneMatch(c -> Objects.equals(c.getEmpresa().getNroEmpresa(), guia.getEmpresa().getNroEmpresa()))) {
            throw rechazo(correlationId, ci, idEvento, idGuia,
                    new RecursoNoEncontradoException("La guía no existe o no está asignada a este chofer."));
        }

        EstadoGuia anterior = guia.getEstado();
        Decision decision = guia.getChofer() == null || esDelChofer(guia, habilitados) ? decidir(tipo, anterior)
                : Decision.conflicto(anterior, "GUIA_REASIGNADA", "La guía ya no está asignada a este chofer.");
        boolean modificada = versionGuia != null && !versionGuia.equals(guia.getVersion());
        if (decision.nuevoEstado() != anterior) {
            guia.cambiarEstado(decision.nuevoEstado());
            guias.sincronizar(); // flush: incrementa la version de la guia
        }
        EventoViaje evento = new EventoViaje(idEvento, guia, ci, tipo, fechaHora, descripcion, latitud, longitud,
                foto);
        evento.setVersionGuiaDispositivo(versionGuia);
        evento.registrarResultado(decision.resultado(), decision.codigo(), decision.detalle(), guia.getEstado(),
                guia.getVersion());
        eventos.guardar(evento);
        final TipoEventoViaje t = tipo;
        if (decision.resultado() == ResultadoEventoViaje.CONFLICTO) {
            LOG.warning(() -> "evento=movil.evento.conflicto correlationId=" + correlationId + " usuario=" + ci
                    + " idEvento=" + idEvento + " guia=" + guia.getId() + " tipo=" + t + " estado=" + anterior
                    + " motivo=" + decision.codigo() + " fechaHoraDispositivo=" + fechaHora);
        } else {
            LOG.info(() -> "evento=movil.evento.registrado correlationId=" + correlationId + " usuario=" + ci
                    + " idEvento=" + idEvento + " guia=" + guia.getId() + " tipo=" + t + " resultado="
                    + decision.resultado() + " estado=" + anterior + "->" + guia.getEstado() + " guiaModificada="
                    + modificada + " foto=" + (foto != null) + " fechaHoraDispositivo=" + fechaHora);
        }
        return resultado(evento, false, modificada, habilitados);
    }

    // Lo usa la REST si el insert choco con un envio simultaneo del mismo evento (clave primaria).
    public ResultadoEvento resultadoExistente(String ci, String idEvento) {
        EventoViaje previo = idEvento == null ? null : eventos.buscarPorId(idEvento.trim().toLowerCase(Locale.ROOT));
        if (previo == null || !previo.getCiChofer().equals(ci)) {
            return null;
        }
        List<Chofer> habilitados = choferes.buscarPorCi(ci).stream()
                .filter(c -> c.getEstado() == EstadoChofer.HABILITADO).toList();
        return resultado(previo, true, false, habilitados);
    }

    // Politica de resolucion de conflictos (ver TSE_MOVIL/docs/adr/0002): los eventos son hechos que
    // ocurrieron en el viaje. Si el efecto ya esta aplicado se aceptan sin cambios (SIN_EFECTO); si no
    // se pueden aplicar al estado actual se registran como CONFLICTO y la guia no cambia.
    static Decision decidir(TipoEventoViaje tipo, EstadoGuia actual) {
        if (actual == EstadoGuia.CANCELADA) {
            return Decision.conflicto(actual, "GUIA_CANCELADA", "La empresa canceló la guía.");
        }
        if (actual == null || actual == EstadoGuia.SIN_ASIGNAR) {
            return Decision.conflicto(actual, "GUIA_NO_ASIGNADA", "La guía no está asignada.");
        }
        return switch (tipo) {
            case INICIO -> switch (actual) {
                case ASIGNADA -> Decision.aplicado(EstadoGuia.EN_CURSO);
                case EN_CURSO -> Decision.sinEfecto(actual, "VIAJE_YA_INICIADO", "El viaje ya estaba iniciado.");
                default -> Decision.conflicto(actual, "VIAJE_FINALIZADO", "El viaje ya está finalizado.");
            };
            case FIN -> switch (actual) {
                case EN_CURSO -> Decision.aplicado(EstadoGuia.FINALIZADA);
                case FINALIZADA -> Decision.sinEfecto(actual, "VIAJE_YA_FINALIZADO", "El viaje ya estaba finalizado.");
                default -> Decision.conflicto(actual, "VIAJE_NO_INICIADO", "El viaje no estaba iniciado.");
            };
            default -> switch (actual) {
                case EN_CURSO -> Decision.aplicado(EstadoGuia.EN_CURSO);
                case ASIGNADA -> Decision.conflicto(actual, "VIAJE_NO_INICIADO", "El viaje no estaba iniciado.");
                default -> Decision.conflicto(actual, "VIAJE_FINALIZADO", "El viaje ya está finalizado.");
            };
        };
    }

    private static boolean esDelChofer(Guia guia, List<Chofer> habilitados) {
        return guia.getChofer() != null
                && habilitados.stream().anyMatch(c -> Objects.equals(c.getId(), guia.getChofer().getId()));
    }

    private List<Chofer> choferesHabilitados(String ci, String correlationId) throws NoAutorizadoException {
        List<Chofer> habilitados = ci == null ? List.of()
                : choferes.buscarPorCi(ci).stream().filter(c -> c.getEstado() == EstadoChofer.HABILITADO).toList();
        if (habilitados.isEmpty()) {
            LOG.warning(() -> "evento=movil.no_autorizado correlationId=" + correlationId + " usuario=" + ci
                    + " detalle=\"no es chofer habilitado de ninguna empresa\"");
            throw new NoAutorizadoException("no es chofer habilitado de ninguna empresa");
        }
        return habilitados;
    }

    private static <E extends Exception> E rechazo(String correlationId, String ci, String idEvento, String idGuia,
            E e) {
        String codigo = e instanceof ConflictoException c ? c.getCodigo() : "GUIA_NO_ENCONTRADA";
        LOG.warning(() -> "evento=movil.evento.rechazado correlationId=" + correlationId + " usuario=" + ci
                + " idEvento=" + idEvento + " guia=" + idGuia + " motivo=" + codigo);
        return e;
    }

    private static Instant fechaHora(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor.trim()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static byte[] foto(Map<String, String> errores, String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        String limpio = base64.trim();
        int coma = limpio.indexOf(',');
        if (limpio.startsWith("data:") && coma > 0) {
            limpio = limpio.substring(coma + 1); // data:image/jpeg;base64,...
        }
        String muyGrande = "La foto no puede superar los " + EventoViaje.MAX_FOTO_BYTES / (1024 * 1024) + " MB.";
        if (limpio.length() > MAX_FOTO_BASE64) {
            errores.put("foto", muyGrande); // antes de decodificar, para no reservar memoria de mas
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(limpio);
        } catch (IllegalArgumentException e) {
            errores.put("foto", "La foto no está en base64.");
            return null;
        }
        if (bytes.length > EventoViaje.MAX_FOTO_BYTES) {
            errores.put("foto", muyGrande);
            return null;
        }
        boolean jpeg = bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
        boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        if (!jpeg && !png) {
            errores.put("foto", "La foto tiene que ser JPEG o PNG.");
            return null;
        }
        return bytes;
    }

    private static ViajeVista vista(Guia g) {
        Vehiculo v = g.getVehiculo();
        return new ViajeVista(g.getId(), g.getEmpresa() == null ? null : g.getEmpresa().getNroEmpresa(),
                g.getEmpresa() == null ? null : g.getEmpresa().getNombrePublico(), g.getRubroCliente(), g.getTipoCarga(),
                g.getPesoCargaKg(), g.getFecha() == null ? null : g.getFecha().toString(), g.getOrigen(),
                g.getDestino(), v == null ? null : v.getMatricula(),
                v == null ? null : (v.getMarca() + " " + v.getModelo()).trim(), g.getEstado().name(), g.getVersion());
    }

    // La guia actual solo se devuelve si sigue siendo de este chofer (si fue reasignada, null).
    // modificada: la guia cambio desde la version que conocia el movil (se calcula antes de aplicar el evento)
    private static ResultadoEvento resultado(EventoViaje e, boolean duplicado, boolean modificada,
            List<Chofer> habilitados) {
        Guia g = e.getGuia();
        ResultadoEventoViaje r = e.getResultado() == null ? ResultadoEventoViaje.APLICADO : e.getResultado();
        return new ResultadoEvento(e.getIdEvento(), g.getId(), e.getTipo().name(), r.name(), e.getCodigo(),
                e.getDetalle(), e.getEstadoGuiaResultante() == null ? null : e.getEstadoGuiaResultante().name(),
                e.getVersionGuiaResultante(), e.getFechaRecepcion().toString(), duplicado, modificada,
                esDelChofer(g, habilitados) ? vista(g) : null);
    }
}
