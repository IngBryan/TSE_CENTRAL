package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.AsociacionTemporalGuia;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervalosGuiasGpsRemote;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;
import tse.lab.frontofficeciudadanos.service.rutas.RutasAutorizadasRemote;
import tse.lab.frontofficefuncionarios.data.GuiaDAO;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;

/**
 * Consultas cartográficas para la vista de Gala. Solo lectura. Reutiliza la
 * misma asociación temporal que Fiscalización, y NO consume RabbitMQ.
 */
@Stateless
public class SeguimientoGpsService {
    public static final int LIMITE_PREDETERMINADO = 500;
    public static final int LIMITE_MAXIMO = 1000;
    private static final Duration MAX_VENTANA = Duration.ofDays(31);

    @EJB private AutorizacionFuncionario autorizacion;
    @EJB private GuiaDAO guias;
    @EJB private MongoSeguimientoDAO tracking;
    
    @EJB(lookup = "java:global/frontofficeciudadanos/frontofficeciudadanos-ejb/RutasAutorizadasService!tse.lab.frontofficeciudadanos.service.rutas.RutasAutorizadasRemote")
    private RutasAutorizadasRemote rutas;
    @EJB(lookup = "java:global/frontofficeciudadanos/frontofficeciudadanos-ejb/IntervalosGuiasGpsService!tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervalosGuiasGpsRemote")
    private IntervalosGuiasGpsRemote intervalos;

    public record PaginaGps(String idGuia, String matricula, String desde, String hasta,
            List<PuntoGpsSeguimiento> posiciones, String siguienteDesde, boolean hayMas) { }

    public record UltimaGps(String idGuia, String matricula, PuntoGpsSeguimiento posicion) { }

    public PaginaGps historial(String ci, String idGuia, String desdeTexto, String hastaTexto,
            String limiteTexto, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, DatosInvalidosException {
        autorizacion.funcionario(ci, correlationId);
        Guia guia = viajeVisible(idGuia);
        Map<String, String> errores = new LinkedHashMap<>();
        Instant desde = instante(desdeTexto, "desde", errores);
        Instant hasta = instante(hastaTexto, "hasta", errores);
        int limite = limite(limiteTexto, errores);
        if (desde != null && hasta != null && (!desde.isBefore(hasta)
                || Duration.between(desde, hasta).compareTo(MAX_VENTANA) > 0)) {
            errores.put("rango", "El rango debe ser positivo y no superar 31 días.");
        }
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);

        String matricula = guia.getVehiculo().getMatricula();
        List<IntervaloGuiaGps> ventanas = consultarVentanas(matricula, desde, hasta);
        // Se lee limite+1: indica si hay más candidatas. Nunca paginar basándose
        // en el último punto adjudicado; podrían existir puntos de otra Guía.
        List<PuntoGpsSeguimiento> candidatos = tracking.buscar(matricula, desde, hasta, limite + 1);
        var pagina = SegmentoSeguimientoGps.filtrar(idGuia, ventanas, candidatos, limite);
        return new PaginaGps(idGuia, matricula, desde.toString(), hasta.toString(), pagina.posiciones(),
                pagina.siguienteDesde() == null ? null : pagina.siguienteDesde().toString(), pagina.hayMas());
    }

    /** Última posición adjudicable en las últimas 24 horas. No equivale a GPS 'online'. */
    public UltimaGps ultima(String ci, String idGuia, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        Guia guia = viajeVisible(idGuia);
        String matricula = guia.getVehiculo().getMatricula();
        Instant hasta = Clock.systemUTC().instant().plusSeconds(1);
        Instant desde = hasta.minus(Duration.ofHours(24));
        List<IntervaloGuiaGps> ventanas = consultarVentanas(matricula, desde, hasta);
        PuntoGpsSeguimiento posicion = tracking.recientes(matricula, desde, hasta, 500).stream()
                .filter(p -> AsociacionTemporalGuia.resolver(matricula, p.fechaHora(), ventanas)
                        .correspondeA(idGuia))
                .findFirst().orElse(null);
        return new UltimaGps(idGuia, matricula, posicion);
    }

    /** GeoJSON de rutas fijadas en la Guía. Puede ser vacío si todavía no están generadas. */
    public List<DatosRutaAutorizada> rutas(String ci, String idGuia, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        viajeVisible(idGuia);
        try {
            return rutas.obtenerPorGuia(idGuia);
        } catch (RuntimeException e) {
            throw new SeguimientoNoDisponibleException("No se pudieron consultar las rutas autorizadas", e);
        }
    }

    private List<IntervaloGuiaGps> consultarVentanas(String matricula, Instant desde, Instant hasta) {
        try {
            return intervalos.buscar(matricula, desde, hasta);
        } catch (RuntimeException e) {
            throw new SeguimientoNoDisponibleException("No se pudieron consultar los intervalos de Guías", e);
        }
    }

    private Guia viajeVisible(String id) throws RecursoNoEncontradoException {
        Guia g = id == null ? null : guias.buscarPorId(id);
        if (g == null || g.getVehiculo() == null || g.getChofer() == null
                || (g.getEstado() != EstadoGuia.ASIGNADA && g.getEstado() != EstadoGuia.EN_CURSO
                        && g.getEstado() != EstadoGuia.FINALIZADA)) {
            throw new RecursoNoEncontradoException("VIAJE_NO_ENCONTRADO", "El viaje no existe.");
        }
        return g;
    }

    private static Instant instante(String texto, String campo, Map<String, String> errores) {
        if (texto == null || texto.isBlank()) {
            errores.put(campo, "Requerido: instante ISO-8601 UTC.");
            return null;
        }
        try {
            return Instant.parse(texto);
        } catch (DateTimeParseException e) {
            errores.put(campo, "Fecha inválida: utilizar ISO-8601, por ejemplo 2026-10-31T12:00:00Z.");
            return null;
        }
    }

    private static int limite(String texto, Map<String, String> errores) {
        if (texto == null || texto.isBlank()) return LIMITE_PREDETERMINADO;
        try {
            int valor = Integer.parseInt(texto);
            if (valor >= 1 && valor <= LIMITE_MAXIMO) return valor;
        } catch (NumberFormatException ignored) { }
        errores.put("limite", "Debe ser un entero entre 1 y " + LIMITE_MAXIMO + ".");
        return LIMITE_PREDETERMINADO;
    }
}
