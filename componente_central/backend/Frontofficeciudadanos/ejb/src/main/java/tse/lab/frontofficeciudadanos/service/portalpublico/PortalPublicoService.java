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
import tse.lab.frontofficeciudadanos.data.RubroDAO;
import tse.lab.frontofficeciudadanos.data.TipoCargaDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.LogCiudadanos;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.portalpublico.Agregacion.Fila;
import tse.lab.frontofficeciudadanos.service.portalpublico.Agregacion.FilaPublica;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Rubro;
import tse.lab.model.TipoCarga;
import tse.lab.model.Vehiculo;

// Portal publico del frontoffice de ciudadanos (sin login):
// - Validacion de Permisos de Empresas: un cliente verifica si una transportista esta habilitada, en que rubros
//   trabaja y, si quiere, si un vehiculo es de esa empresa y tiene permiso e ITV vigentes. Son datos del
//   registro de empresas y vehiculos, nunca de personas (no se muestra el responsable ni los choferes). Una
//   matricula de otra empresa solo dice eso. Tampoco se dan numeros de la empresa (por ejemplo, cuantos
//   vehiculos tiene o cuantos viajes hizo): eso es un dato de una sola empresa y no hace falta para validarla.
// - Listados de Informacion Publica: estadisticas agregadas con la regla de Agregacion (umbral minimo de
//   empresas, PORTAL_UMBRAL_EMPRESAS, 3 por defecto y nunca menos de 3). Las tablas por rubro y por tipo de
//   carga usan los catalogos precargados: sus nombres no identifican a nadie, asi que cada fila se muestra por
//   nombre (reservada si no junta el umbral) y tambien las que no tienen datos (en 0).
@Stateless
public class PortalPublicoService {

    private static final Logger LOG = Logger.getLogger(PortalPublicoService.class.getName());
    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");
    private static final Locale ES = Locale.forLanguageTag("es-UY");
    static final int UMBRAL_MINIMO = 3;
    static final int MAX_RESULTADOS_BUSQUEDA = 10;
    static final int PRIMER_ANIO = 2020;
    // Clave de la fila que junta los valores que no estan en el catalogo (no choca con uno: las claves no llevan
    // "~"). No deberia haber, porque las guias se validan contra los catalogos; si los hay (datos viejos de
    // texto libre), van juntos y sin su nombre, que lo escribio una empresa y podria identificarla.
    static final String OTROS = "~otros";

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
    @EJB
    private RubroDAO rubros;
    @EJB
    private TipoCargaDAO tiposCarga;

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

    // rubros: los de la empresa (catalogo precargado), ordenados por nombre
    public record ValidacionEmpresa(Long nroEmpresa, String nombrePublico, String razonSocial, boolean habilitada,
            List<String> rubros, ValidacionVehiculo vehiculo, String consultadoEl) {
    }

    // valor null cuando no alcanza el umbral
    public record Valor(Long valor, boolean reservado) {
    }

    // Indicador con decimales: valor null y reservado cuando no alcanza el umbral; null sin reservar cuando no
    // hay datos (por ejemplo, ningun caso resuelto en el anio)
    public record Indicador(Double valor, boolean reservado) {
    }

    // cargaPorRubro: por rubro del cliente de la guia. empresasPorRubro: empresas habilitadas por rubro de la
    // empresa (situacion actual; una empresa con varios rubros cuenta en cada uno, asi que no suman el total)
    public record Estadisticas(int anio, List<Integer> aniosDisponibles, int umbralEmpresas, String generadas,
            Valor empresasHabilitadas, Valor vehiculosHabilitados, Valor viajes, Valor kgTransportados,
            List<FilaPublica> viajesPorMes, List<FilaPublica> cargaPorRubro, List<FilaPublica> cargaPorTipoCarga,
            List<FilaPublica> empresasPorRubro, List<FilaPublica> infraccionesConfirmadasPorTipo, Indicador infraccionesCada100Viajes,
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
        LOG.info(() -> LogCiudadanos.evento("publico.busqueda_empresas").correlationId(correlationId)
                .campo("resultados", lista.size()).toString());
        return lista;
    }

    public ValidacionEmpresa validarEmpresa(Long nroEmpresa, String matricula, String correlationId)
            throws RecursoNoEncontradoException {
        Empresa empresa = nroEmpresa == null ? null : empresas.buscarPorId(nroEmpresa);
        if (empresa == null) {
            LOG.info(() -> LogCiudadanos.evento("publico.validacion").correlationId(correlationId)
                    .campo("nroEmpresa", nroEmpresa).campo("resultado", "no_existe").toString());
            throw new RecursoNoEncontradoException("No hay ninguna empresa registrada con ese número.");
        }
        LocalDate hoy = LocalDate.now(ZONA);
        ValidacionVehiculo vehiculo = matricula == null || matricula.isBlank() ? null
                : validarVehiculo(empresa, matricula, hoy);
        LOG.info(() -> LogCiudadanos.evento("publico.validacion").correlationId(correlationId)
                .campo("nroEmpresa", nroEmpresa)
                .campo("habilitada", empresa.isHabilitada() + (vehiculo == null ? "" : " matricula=" + vehiculo.matricula() + " deLaEmpresa=" + vehiculo.deLaEmpresa() + " habilitado=" + vehiculo.habilitado()))
                .toString());
        return new ValidacionEmpresa(empresa.getNroEmpresa(), empresa.getNombrePublico(), empresa.getRazonSocial(),
                empresa.isHabilitada(), empresa.getRubros().stream().map(Rubro::getNombre).toList(), vehiculo,
                Instant.now().toString());
    }

    private ValidacionVehiculo validarVehiculo(Empresa empresa, String matricula, LocalDate hoy) {
        String normalizada = Vehiculo.normalizarMatricula(matricula);
        Vehiculo v = normalizada == null || normalizada.isEmpty() ? null : vehiculos.buscarPorMatricula(normalizada);
        if (v == null || v.getEmpresa() == null || !v.getEmpresa().getNroEmpresa().equals(empresa.getNroEmpresa())) {
            // No se dice si existe en otra empresa: es un dato de otra empresa
            return new ValidacionVehiculo(normalizada, false, null, null, null, null, null, false);
        }
        PermisoCirculacion p = permisos.buscarPorVehiculo(v);
        boolean permisoVigente = p != null && p.vigenteEn(hoy);
        List<ITV> historial = itvs.buscarPorVehiculo(v);
        ITV itv = historial.isEmpty() ? null : historial.get(0);
        boolean itvVigente = itv != null && itv.vigenteEn(hoy);
        return new ValidacionVehiculo(v.getMatricula(), true, v.isActivo(),
                p == null ? null : p.getFechaVencimiento().toString(), permisoVigente,
                itv == null ? null : itv.getFechaVencimiento().toString(), itvVigente,
                empresa.isHabilitada() && v.habilitadoEn(hoy, p, historial));
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

        // Carga por rubro del cliente y por tipo de carga, sobre los catalogos precargados
        List<String> nombresRubros = rubros.listar().stream().map(Rubro::getNombre).toList();
        List<Fila> cargaPorRubro = porCatalogo(nombresRubros, portal.viajesPorEmpresaYRubro(desde, hasta),
                "Otros rubros");
        List<Fila> cargaPorTipo = porCatalogo(tiposCarga.listar().stream().map(TipoCarga::getNombre).toList(),
                portal.viajesPorEmpresaYTipoCarga(desde, hasta), "Otros tipos de carga");

        // Empresas habilitadas por rubro de la empresa (situacion actual: no depende del anio)
        Map<String, Fila> empresasRubro = new LinkedHashMap<>();
        nombresRubros.forEach(n -> empresasRubro.put(clave(n), new Fila(clave(n), n, new HashSet<>(), 0, 0)));
        for (Object[] r : portal.rubrosDeEmpresasHabilitadas()) {
            Fila f = empresasRubro.get(clave((String) r[0]));
            if (f != null) {
                Set<Long> empresasFila = new HashSet<>(f.empresas());
                empresasFila.add((Long) r[1]);
                empresasRubro.put(f.clave(), new Fila(f.clave(), f.etiqueta(), empresasFila, empresasFila.size(), 0));
            }
        }
        List<Fila> empresasPorRubro = new ArrayList<>(empresasRubro.values());
        empresasPorRubro.sort((a, b) -> b.cantidad() != a.cantidad() ? Long.compare(b.cantidad(), a.cantidad())
                : a.etiqueta().compareTo(b.etiqueta()));

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
                Agregacion.proteger(cargaPorRubro, umbral),
                Agregacion.proteger(cargaPorTipo, umbral),
                Agregacion.proteger(empresasPorRubro, umbral),
                Agregacion.proteger(new ArrayList<>(porTipo.values()), umbral),
                tasa,
                resueltosPublicables ? new Valor(resueltos, false) : new Valor(null, true),
                resueltos == 0 ? sinDatos : resueltosPublicables
                        ? new Indicador(redondear(100.0 * desestimados / resueltos), false) : reservado,
                resueltos == 0 ? sinDatos : resueltosPublicables ? new Indicador(redondear(mediana(dias)), false) : reservado);
        LOG.info(() -> LogCiudadanos.evento("publico.estadisticas").correlationId(correlationId)
                .campo("anio", e.anio()).campo("umbral", umbral)
                .campo("reservadas", java.util.stream.Stream.of(e.viajesPorMes(), e.cargaPorRubro(),
                        e.cargaPorTipoCarga(), e.empresasPorRubro(), e.infraccionesConfirmadasPorTipo())
                        .flatMap(List::stream).filter(FilaPublica::reservado).count())
                .toString());
        return e;
    }

    // Viajes y kg por valor de un catalogo ([nroEmpresa, valor, viajes, kg]): una fila por cada valor del
    // catalogo, aunque no tenga viajes (en 0), ordenadas por kg. Lo que no esta en el catalogo (o vacio) va a la
    // fila OTROS, al final, solo si hay.
    private static List<Fila> porCatalogo(List<String> catalogo, List<Object[]> datos, String etiquetaOtros) {
        Map<String, Fila> porClave = new LinkedHashMap<>();
        catalogo.forEach(n -> porClave.put(clave(n), new Fila(clave(n), n, new HashSet<>(), 0, 0)));
        Fila otros = new Fila(OTROS, etiquetaOtros, new HashSet<>(), 0, 0);
        for (Object[] r : datos) {
            String valor = (String) r[1];
            Fila actual = valor == null ? null : porClave.get(clave(valor));
            if (actual == null) {
                otros = sumar(otros, (Long) r[0], (Long) r[2], numero(r[3]));
            } else {
                porClave.put(actual.clave(), sumar(actual, (Long) r[0], (Long) r[2], numero(r[3])));
            }
        }
        List<Fila> filas = new ArrayList<>(porClave.values());
        filas.sort((a, b) -> b.kg() != a.kg() ? Long.compare(b.kg(), a.kg()) : a.etiqueta().compareTo(b.etiqueta()));
        if (otros.cantidad() > 0) {
            filas.add(otros);
        }
        return filas;
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
