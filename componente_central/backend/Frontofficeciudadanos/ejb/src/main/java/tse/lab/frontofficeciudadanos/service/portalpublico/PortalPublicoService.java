package tse.lab.frontofficeciudadanos.service.portalpublico;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.EmpresaDAO;
import tse.lab.frontofficeciudadanos.data.ITVDAO;
import tse.lab.frontofficeciudadanos.data.PermisoCirculacionDAO;
import tse.lab.frontofficeciudadanos.data.PortalPublicoDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.portalpublico.Agregacion.Fila;
import tse.lab.frontofficeciudadanos.service.portalpublico.Agregacion.FilaPublica;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Vehiculo;

// Portal publico del frontoffice de ciudadanos (sin login):
// - Validacion de Permisos de Empresas: un cliente verifica si una transportista esta habilitada y, si quiere,
//   si un vehiculo es de esa empresa y tiene permiso e ITV vigentes. Son datos de empresas y vehiculos, nunca
//   de personas (no se muestra el responsable ni los choferes). Una matricula de otra empresa solo dice eso.
//   Tampoco se dan numeros de la empresa (por ejemplo, cuantos vehiculos tiene): eso es un dato de una sola
//   empresa y no hace falta para validarla.
// - Listados de Informacion Publica: estadisticas agregadas con la regla de Agregacion (umbral minimo de
//   empresas, PORTAL_UMBRAL_EMPRESAS, 3 por defecto y nunca menos de 3).
@Stateless
public class PortalPublicoService {

    private static final Logger LOG = Logger.getLogger(PortalPublicoService.class.getName());
    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");
    private static final Locale ES = Locale.forLanguageTag("es-UY");
    static final int UMBRAL_MINIMO = 3;
    static final int MAX_RESULTADOS_BUSQUEDA = 10;
    static final int PRIMER_ANIO = 2020;
    // Clave de la fila que junta los rubros reservados (no choca con un rubro: las claves no llevan "~")
    static final String OTROS_RUBROS = "~otros";

    @EJB
    private PortalPublicoDAO portal;
    @EJB
    private EmpresaDAO empresas;
    @EJB
    private VehiculoDAO vehiculos;
    @EJB
    private PermisoCirculacionDAO permisos;
    @EJB
    private ITVDAO itvs;

    private final int umbral;

    public PortalPublicoService() {
        this(umbralConfigurado());
    }

    // Para los tests: sin leer el entorno
    PortalPublicoService(int umbral) {
        this.umbral = umbral;
    }

    public record EmpresaEncontrada(Long nroEmpresa, String nombrePublico, String razonSocial, boolean habilitada) {
    }

    // Datos del vehiculo solo si es de la empresa consultada (deLaEmpresa)
    public record ValidacionVehiculo(String matricula, boolean deLaEmpresa, Boolean activo, String permisoVence,
            Boolean permisoVigente, String itvVence, Boolean itvVigente, boolean habilitado) {
    }

    public record ValidacionEmpresa(Long nroEmpresa, String nombrePublico, String razonSocial, boolean habilitada,
            ValidacionVehiculo vehiculo, String consultadoEl) {
    }

    // valor null cuando no alcanza el umbral
    public record Valor(Long valor, boolean reservado) {
    }

    // Indicador con decimales: valor null y reservado cuando no alcanza el umbral; null sin reservar cuando no
    // hay datos (por ejemplo, ningun caso resuelto en el anio)
    public record Indicador(Double valor, boolean reservado) {
    }

    public record Estadisticas(int anio, List<Integer> aniosDisponibles, int umbralEmpresas, String generadas,
            Valor empresasHabilitadas, Valor vehiculosHabilitados, Valor viajes, Valor kgTransportados,
            List<FilaPublica> viajesPorMes, List<FilaPublica> cargaPorRubro,
            List<FilaPublica> infraccionesConfirmadasPorTipo, Indicador infraccionesCada100Viajes,
            Valor casosResueltos, Indicador porcentajeDesestimados, Indicador medianaDiasResolucion) {
    }

    public List<EmpresaEncontrada> buscarEmpresas(String texto, String correlationId) throws DatosInvalidosException {
        String t = texto == null ? "" : texto.strip();
        if (t.length() < 3 || t.length() > 60) {
            throw new DatosInvalidosException(Map.of("nombre", "Escribí entre 3 y 60 letras del nombre de la empresa."));
        }
        List<EmpresaEncontrada> lista = portal.buscarEmpresas(t, MAX_RESULTADOS_BUSQUEDA).stream()
                .map(e -> new EmpresaEncontrada(e.getNroEmpresa(), e.getNombrePublico(), e.getRazonSocial(),
                        e.isHabilitada()))
                .toList();
        LOG.info(() -> "evento=publico.busqueda_empresas correlationId=" + correlationId + " resultados=" + lista.size());
        return lista;
    }

    public ValidacionEmpresa validarEmpresa(Long nroEmpresa, String matricula, String correlationId)
            throws RecursoNoEncontradoException {
        Empresa empresa = nroEmpresa == null ? null : empresas.buscarPorId(nroEmpresa);
        if (empresa == null) {
            LOG.info(() -> "evento=publico.validacion correlationId=" + correlationId + " nroEmpresa=" + nroEmpresa
                    + " resultado=no_existe");
            throw new RecursoNoEncontradoException("No hay ninguna empresa registrada con ese número.");
        }
        LocalDate hoy = LocalDate.now(ZONA);
        ValidacionVehiculo vehiculo = matricula == null || matricula.isBlank() ? null
                : validarVehiculo(empresa, matricula, hoy);
        LOG.info(() -> "evento=publico.validacion correlationId=" + correlationId + " nroEmpresa=" + nroEmpresa
                + " habilitada=" + empresa.isHabilitada()
                + (vehiculo == null ? "" : " matricula=" + vehiculo.matricula() + " deLaEmpresa="
                        + vehiculo.deLaEmpresa() + " habilitado=" + vehiculo.habilitado()));
        return new ValidacionEmpresa(empresa.getNroEmpresa(), empresa.getNombrePublico(), empresa.getRazonSocial(),
                empresa.isHabilitada(), vehiculo, Instant.now().toString());
    }

    private ValidacionVehiculo validarVehiculo(Empresa empresa, String matricula, LocalDate hoy) {
        String normalizada = Vehiculo.normalizarMatricula(matricula);
        Vehiculo v = normalizada == null || normalizada.isEmpty() ? null : vehiculos.buscarPorMatricula(normalizada);
        if (v == null || v.getEmpresa() == null || !v.getEmpresa().getNroEmpresa().equals(empresa.getNroEmpresa())) {
            // No se dice si existe en otra empresa: es un dato de otra empresa
            return new ValidacionVehiculo(normalizada, false, null, null, null, null, null, false);
        }
        PermisoCirculacion p = permisos.buscarPorVehiculo(v);
        boolean permisoVigente = p != null && !hoy.isBefore(p.getFechaEmision()) && !hoy.isAfter(p.getFechaVencimiento());
        List<ITV> historial = itvs.buscarPorVehiculo(v);
        ITV itv = historial.isEmpty() ? null : historial.get(0);
        boolean itvVigente = itv != null && !hoy.isAfter(itv.getFechaVencimiento());
        return new ValidacionVehiculo(v.getMatricula(), true, v.isActivo(),
                p == null ? null : p.getFechaVencimiento().toString(), permisoVigente,
                itv == null ? null : itv.getFechaVencimiento().toString(), itvVigente,
                empresa.isHabilitada() && v.isActivo() && permisoVigente && itvVigente);
    }

    public Estadisticas estadisticas(Integer anioPedido, String correlationId) throws DatosInvalidosException {
        LocalDate hoy = LocalDate.now(ZONA);
        int anio = anioPedido == null ? hoy.getYear() : anioPedido;
        if (anio < PRIMER_ANIO || anio > hoy.getYear()) {
            throw new DatosInvalidosException(Map.of("anio", "El año tiene que estar entre " + PRIMER_ANIO + " y "
                    + hoy.getYear() + "."));
        }
        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);

        // Situacion actual (no depende del anio)
        long habilitadas = portal.empresasHabilitadas();
        Valor empresasHabilitadas = habilitadas >= umbral ? new Valor(habilitadas, false) : new Valor(null, true);
        Set<Long> empresasConVehiculos = new HashSet<>();
        long vehiculosHabilitados = 0;
        for (Object[] r : portal.vehiculosHabilitadosPorEmpresa(hoy, null)) {
            empresasConVehiculos.add((Long) r[0]);
            vehiculosHabilitados += (Long) r[1];
        }

        // Viajes finalizados del anio, por mes
        Map<String, Fila> porMes = new LinkedHashMap<>();
        for (Month m : Month.values()) {
            String clave = anio + "-" + String.format("%02d", m.getValue());
            porMes.put(clave, new Fila(clave, m.getDisplayName(TextStyle.FULL, ES), new HashSet<>(), 0, 0));
        }
        Set<Long> empresasConViajes = new HashSet<>();
        long viajes = 0;
        long kg = 0;
        for (Object[] r : portal.viajesPorEmpresaYFecha(desde, hasta)) {
            LocalDate fecha = (LocalDate) r[1];
            String clave = anio + "-" + String.format("%02d", fecha.getMonthValue());
            porMes.put(clave, sumar(porMes.get(clave), (Long) r[0], (Long) r[2], numero(r[3])));
            empresasConViajes.add((Long) r[0]);
            viajes += (Long) r[2];
            kg += numero(r[3]);
        }

        // Carga por rubro del cliente (texto libre: se agrupa sin distinguir mayusculas ni tildes)
        Map<String, Fila> porRubro = new LinkedHashMap<>();
        for (Object[] r : portal.viajesPorEmpresaYRubro(desde, hasta)) {
            String etiqueta = r[1] == null || ((String) r[1]).isBlank() ? "Sin rubro" : ((String) r[1]).strip();
            String clave = clave(etiqueta);
            Fila actual = porRubro.getOrDefault(clave, new Fila(clave, etiqueta, new HashSet<>(), 0, 0));
            porRubro.put(clave, sumar(actual, (Long) r[0], (Long) r[2], numero(r[3])));
        }
        List<Fila> rubros = new ArrayList<>(porRubro.values());
        rubros.sort((a, b) -> Long.compare(b.kg(), a.kg()));

        // Infracciones confirmadas por tipo
        Map<String, Fila> porTipo = new LinkedHashMap<>();
        Instant inicio = desde.atStartOfDay(ZONA).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
        Set<Long> empresasConInfracciones = new HashSet<>();
        long infracciones = 0;
        for (Object[] r : portal.infraccionesConfirmadasPorEmpresaYTipo(inicio, fin)) {
            String tipo = (String) r[1];
            Fila actual = porTipo.getOrDefault(tipo, new Fila(tipo, tipo, new HashSet<>(), 0, 0));
            porTipo.put(tipo, sumar(actual, (Long) r[0], (Long) r[2], 0));
            empresasConInfracciones.add((Long) r[0]);
            infracciones += (Long) r[2];
        }

        // Infracciones confirmadas cada 100 viajes finalizados: mas comparable que el total, que crece con la
        // actividad. Las dos partes del cociente tienen que alcanzar el umbral.
        boolean viajesPublicables = Agregacion.publicable(empresasConViajes, viajes, umbral);
        Indicador tasa = viajes == 0 ? new Indicador(null, false)
                : viajesPublicables && Agregacion.publicable(empresasConInfracciones, infracciones, umbral)
                        ? new Indicador(redondear(100.0 * infracciones / viajes), false) : new Indicador(null, true);

        // Casos resueltos: que parte se desestima y cuanto tarda la resolucion (mediana, no promedio, para que
        // un caso trabado meses no la distorsione)
        Set<Long> empresasConResueltos = new HashSet<>();
        List<Double> dias = new ArrayList<>();
        long desestimados = 0;
        for (Object[] r : portal.casosResueltos(inicio, fin)) {
            empresasConResueltos.add((Long) r[0]);
            if (r[1] == EstadoCaso.DESESTIMADA) {
                desestimados++;
            }
            dias.add(Duration.between((Instant) r[2], (Instant) r[3]).toMinutes() / (60.0 * 24));
        }
        long resueltos = dias.size();
        boolean resueltosPublicables = Agregacion.publicable(empresasConResueltos, resueltos, umbral);
        Indicador sinDatos = new Indicador(null, false);
        Indicador reservado = new Indicador(null, true);

        Estadisticas e = new Estadisticas(anio, aniosDisponibles(hoy), umbral, Instant.now().toString(),
                empresasHabilitadas,
                Agregacion.publicable(empresasConVehiculos, vehiculosHabilitados, umbral)
                        ? new Valor(vehiculosHabilitados, false) : new Valor(null, true),
                viajesPublicables ? new Valor(viajes, false) : new Valor(null, true),
                viajesPublicables ? new Valor(kg, false) : new Valor(null, true),
                Agregacion.proteger(new ArrayList<>(porMes.values()), umbral),
                // El rubro lo escribe cada empresa y su nombre puede identificarla: los reservados van juntos
                Agregacion.protegerAgrupando(rubros, umbral, OTROS_RUBROS, "Otros rubros"),
                Agregacion.proteger(new ArrayList<>(porTipo.values()), umbral),
                tasa,
                resueltosPublicables ? new Valor(resueltos, false) : new Valor(null, true),
                resueltos == 0 ? sinDatos : resueltosPublicables
                        ? new Indicador(redondear(100.0 * desestimados / resueltos), false) : reservado,
                resueltos == 0 ? sinDatos : resueltosPublicables ? new Indicador(redondear(mediana(dias)), false) : reservado);
        LOG.info(() -> "evento=publico.estadisticas correlationId=" + correlationId + " anio=" + e.anio()
                + " umbral=" + umbral + " reservadas=" + (e.viajesPorMes().stream().filter(FilaPublica::reservado).count()
                        + e.cargaPorRubro().stream().filter(FilaPublica::reservado).count()
                        + e.infraccionesConfirmadasPorTipo().stream().filter(FilaPublica::reservado).count()));
        return e;
    }

    private static Fila sumar(Fila f, Long nroEmpresa, long cantidad, long kg) {
        Set<Long> empresasFila = new HashSet<>(f.empresas());
        empresasFila.add(nroEmpresa);
        return new Fila(f.clave(), f.etiqueta(), empresasFila, f.cantidad() + cantidad, f.kg() + kg);
    }

    static double mediana(List<Double> valores) {
        List<Double> ordenados = valores.stream().sorted().toList();
        int n = ordenados.size();
        return n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2;
    }

    // Un decimal
    private static double redondear(double valor) {
        return Math.round(valor * 10) / 10.0;
    }

    private static long numero(Object valor) {
        return valor == null ? 0 : ((Number) valor).longValue();
    }

    static String clave(String texto) {
        return Normalizer.normalize(texto.strip().toLowerCase(ES), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static List<Integer> aniosDisponibles(LocalDate hoy) {
        List<Integer> anios = new ArrayList<>();
        for (int a = hoy.getYear(); a >= Math.max(PRIMER_ANIO, hoy.getYear() - 4); a--) {
            anios.add(a);
        }
        return anios;
    }

    private static int umbralConfigurado() {
        String valor = System.getenv("PORTAL_UMBRAL_EMPRESAS");
        try {
            int u = valor == null || valor.isBlank() ? UMBRAL_MINIMO : Integer.parseInt(valor.trim());
            return Math.max(u, UMBRAL_MINIMO);
        } catch (NumberFormatException e) {
            return UMBRAL_MINIMO;
        }
    }
}
