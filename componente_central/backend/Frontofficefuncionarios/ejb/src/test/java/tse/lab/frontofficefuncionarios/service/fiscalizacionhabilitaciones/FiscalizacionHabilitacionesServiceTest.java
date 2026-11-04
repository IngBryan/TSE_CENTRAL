package tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.EstadoDocumento.AUN_NO_VIGENTE;
import static tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.EstadoDocumento.POR_VENCER;
import static tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.EstadoDocumento.SIN_REGISTRO;
import static tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.EstadoDocumento.VENCIDO;
import static tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.EstadoDocumento.VIGENTE;
import static tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.EstadoVehiculo.DADO_DE_BAJA;
import static tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.EstadoVehiculo.HABILITADO;
import static tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.EstadoVehiculo.NO_HABILITADO;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficefuncionarios.data.EmpresaDAO;
import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.frontofficefuncionarios.data.ITVDAO;
import tse.lab.frontofficefuncionarios.data.PermisoCirculacionDAO;
import tse.lab.frontofficefuncionarios.data.VehiculoDAO;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.FiscalizacionEmpresa;
import tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.PeriodoVista;
import tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.ResumenHabilitaciones;
import tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.VehiculoDetalle;
import tse.lab.frontofficefuncionarios.service.fiscalizacionhabilitaciones.FiscalizacionHabilitacionesService.VehiculoFiscalizado;
import tse.lab.model.Empresa;
import tse.lab.model.Funcionario;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Vehiculo;

// CU Fiscalizacion de los permisos y estado de los vehiculos contra H2 con los DAOs reales, con el dia de
// la consulta fijo (HOY).
class FiscalizacionHabilitacionesServiceTest {

    private static final String FUNCIONARIO = "45678901";
    private static final String NO_FUNCIONARIO = "54074380";
    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);
    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");

    private EntityManagerFactory emf;
    private EntityManager em;
    private FiscalizacionHabilitacionesService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficefuncionariosTestPersistenceUnit");
        em = emf.createEntityManager();
        AutorizacionFuncionario autorizacion = new AutorizacionFuncionario();
        set(autorizacion, "funcionarios", conEm(new FuncionarioDAO()));
        servicio = new FiscalizacionHabilitacionesService(Clock.fixed(HOY.atStartOfDay(ZONA).toInstant(), ZONA));
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "empresas", conEm(new EmpresaDAO()));
        set(servicio, "vehiculos", conEm(new VehiculoDAO()));
        set(servicio, "permisos", conEm(new PermisoCirculacionDAO()));
        set(servicio, "itvs", conEm(new ITVDAO()));

        Empresa empresa = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
        empresa.setHabilitada(true);
        Empresa otra = new Empresa(2L, "Fletes del Norte", "FDN S.R.L.", "Rivera", null);
        Empresa sinVehiculos = new Empresa(3L, "Recién Llegados", "RL S.A.", "Salto", null);

        em.getTransaction().begin();
        em.persist(new Funcionario(FUNCIONARIO, "funcionario@mtop.gub.uy"));
        em.persist(empresa);
        em.persist(otra);
        em.persist(sinVehiculos);
        // Al dia: el permiso y la ultima ITV vencen en mas de 30 dias; la ITV anterior ya vencio
        Vehiculo alDia = vehiculo("AAA0001", empresa);
        em.persist(new PermisoCirculacion("PC-1", HOY.minusMonths(6), HOY.plusMonths(6), alDia));
        em.persist(new ITV(HOY.minusYears(1), HOY.minusDays(10), alDia));
        em.persist(new ITV(HOY.minusDays(9), HOY.plusDays(31), alDia));
        // Habilitado con el permiso por vencer: vence justo dentro de 30 dias
        Vehiculo porVencer = vehiculo("AAA0002", empresa);
        em.persist(new PermisoCirculacion("PC-2", HOY.minusYears(1), HOY.plusDays(30), porVencer));
        em.persist(new ITV(HOY.minusDays(10), HOY.plusYears(1), porVencer));
        // Permiso vencido ayer
        Vehiculo permisoVencido = vehiculo("AAA0003", empresa);
        em.persist(new PermisoCirculacion("PC-3", HOY.minusYears(1), HOY.minusDays(1), permisoVencido));
        em.persist(new ITV(HOY.minusDays(10), HOY.plusYears(1), permisoVencido));
        // Sin permiso ni ITV registrados
        vehiculo("AAA0004", empresa);
        // Permiso que empieza manana; la ITV vence hoy (sigue vigente)
        Vehiculo aunNoVigente = vehiculo("AAA0005", empresa);
        em.persist(new PermisoCirculacion("PC-5", HOY.plusDays(1), HOY.plusYears(1), aunNoVigente));
        em.persist(new ITV(HOY.minusYears(1), HOY, aunNoVigente));
        // Dado de baja con los papeles al dia
        Vehiculo deBaja = vehiculo("AAA0006", empresa);
        deBaja.setActivo(false);
        em.persist(new PermisoCirculacion("PC-6", HOY.minusMonths(1), HOY.plusYears(1), deBaja));
        em.persist(new ITV(HOY.minusDays(10), HOY.plusYears(1), deBaja));
        // De otra empresa
        Vehiculo ajeno = vehiculo("BBB0001", otra);
        em.persist(new PermisoCirculacion("PC-9", HOY.minusMonths(1), HOY.plusYears(1), ajeno));
        em.persist(new ITV(HOY.minusDays(10), HOY.plusYears(1), ajeno));
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void quienNoEsFuncionarioNoConsultaNada() {
        for (String ci : new String[] { NO_FUNCIONARIO, null, " " }) {
            assertThrows(NoAutorizadoException.class, () -> servicio.vehiculosDeEmpresa(ci, 1L, "t"));
            assertThrows(NoAutorizadoException.class, () -> servicio.vehiculo(ci, 1L, "AAA0001", "t"));
        }
    }

    @Test
    void unaEmpresaQueNoExisteEs404() {
        assertEquals("EMPRESA_NO_ENCONTRADA", assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.vehiculosDeEmpresa(FUNCIONARIO, 99L, "t")).getCodigo());
        assertEquals("EMPRESA_NO_ENCONTRADA", assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.vehiculosDeEmpresa(FUNCIONARIO, null, "t")).getCodigo());
        assertEquals("EMPRESA_NO_ENCONTRADA", assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.vehiculo(FUNCIONARIO, 99L, "AAA0001", "t")).getCodigo());
    }

    @Test
    void listaLosVehiculosDeLaEmpresaConElEstadoDeSuPermisoYSuItv() throws Exception {
        FiscalizacionEmpresa resultado = servicio.vehiculosDeEmpresa(FUNCIONARIO, 1L, "t");

        assertEquals(1L, resultado.nroEmpresa());
        assertEquals("Transportes del Este", resultado.nombrePublico());
        assertEquals(HOY.toString(), resultado.fecha());
        Map<String, VehiculoFiscalizado> porMatricula = resultado.vehiculos().stream()
                .collect(Collectors.toMap(VehiculoFiscalizado::matricula, Function.identity()));
        assertEquals(6, porMatricula.size());

        VehiculoFiscalizado alDia = porMatricula.get("AAA0001");
        assertEquals(HABILITADO, alDia.estado());
        assertEquals(new PeriodoVista("PC-1", HOY.minusMonths(6).toString(), HOY.plusMonths(6).toString(), VIGENTE,
                183L), alDia.permiso());
        // La ITV que se muestra es la de vencimiento mas lejano, no la vencida
        assertEquals(new PeriodoVista(null, HOY.minusDays(9).toString(), HOY.plusDays(31).toString(), VIGENTE, 31L),
                alDia.itv());

        VehiculoFiscalizado porVencer = porMatricula.get("AAA0002");
        assertEquals(HABILITADO, porVencer.estado());
        assertEquals(POR_VENCER, porVencer.permiso().estado());
        assertEquals(30L, porVencer.permiso().diasParaVencer());

        VehiculoFiscalizado permisoVencido = porMatricula.get("AAA0003");
        assertEquals(NO_HABILITADO, permisoVencido.estado());
        assertEquals(VENCIDO, permisoVencido.permiso().estado());
        assertEquals(-1L, permisoVencido.permiso().diasParaVencer());
        assertEquals(VIGENTE, permisoVencido.itv().estado());

        VehiculoFiscalizado sinPapeles = porMatricula.get("AAA0004");
        assertEquals(NO_HABILITADO, sinPapeles.estado());
        assertEquals(new PeriodoVista(null, null, null, SIN_REGISTRO, null), sinPapeles.permiso());
        assertEquals(new PeriodoVista(null, null, null, SIN_REGISTRO, null), sinPapeles.itv());

        VehiculoFiscalizado aunNoVigente = porMatricula.get("AAA0005");
        assertEquals(NO_HABILITADO, aunNoVigente.estado());
        assertEquals(AUN_NO_VIGENTE, aunNoVigente.permiso().estado());
        assertEquals(POR_VENCER, aunNoVigente.itv().estado());
        assertEquals(0L, aunNoVigente.itv().diasParaVencer());

        VehiculoFiscalizado deBaja = porMatricula.get("AAA0006");
        assertEquals(DADO_DE_BAJA, deBaja.estado());
        assertEquals(VIGENTE, deBaja.permiso().estado());
    }

    @Test
    void primeroLosNoHabilitadosDespuesLosHabilitadosYAlFinalLosDadosDeBaja() throws Exception {
        List<String> orden = servicio.vehiculosDeEmpresa(FUNCIONARIO, 1L, "t").vehiculos().stream()
                .map(VehiculoFiscalizado::matricula).toList();

        assertEquals(List.of("AAA0003", "AAA0004", "AAA0005", "AAA0001", "AAA0002", "AAA0006"), orden);
    }

    @Test
    void elResumenCuentaLosVehiculosPorEstado() throws Exception {
        assertEquals(new ResumenHabilitaciones(6, 2, 3, 1, 1),
                servicio.vehiculosDeEmpresa(FUNCIONARIO, 1L, "t").resumen());
        assertEquals(new ResumenHabilitaciones(0, 0, 0, 0, 0),
                servicio.vehiculosDeEmpresa(FUNCIONARIO, 3L, "t").resumen());
    }

    @Test
    void elListadoNoHaceUnaConsultaPorVehiculo() throws Exception {
        Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        servicio.vehiculosDeEmpresa(FUNCIONARIO, 2L, "t");
        em.clear();
        estadisticas.clear();
        servicio.vehiculosDeEmpresa(FUNCIONARIO, 2L, "t");
        long conUnVehiculo = estadisticas.getPrepareStatementCount();
        em.clear();
        estadisticas.clear();

        servicio.vehiculosDeEmpresa(FUNCIONARIO, 1L, "t");

        // La misma cantidad de consultas con 1 vehiculo que con 6
        assertEquals(conUnVehiculo, estadisticas.getPrepareStatementCount());
    }

    @Test
    void elDetalleTraeElHistorialDeItvConLaMasLejanaPrimero() throws Exception {
        // La matricula se normaliza como en el alta
        VehiculoDetalle detalle = servicio.vehiculo(FUNCIONARIO, 1L, "aaa-0001", "t");

        assertEquals(1L, detalle.nroEmpresa());
        assertEquals(HOY.toString(), detalle.fecha());
        assertEquals("AAA0001", detalle.vehiculo().matricula());
        assertEquals(HABILITADO, detalle.vehiculo().estado());
        assertEquals(List.of(HOY.plusDays(31).toString(), HOY.minusDays(10).toString()),
                detalle.itvs().stream().map(PeriodoVista::hasta).toList());
        assertEquals(List.of(VIGENTE, VENCIDO), detalle.itvs().stream().map(PeriodoVista::estado).toList());
    }

    @Test
    void unVehiculoQueNoEsDeLaEmpresaEs404() {
        for (String matricula : new String[] { "BBB0001", "ZZZ9999", "", null }) {
            assertEquals("VEHICULO_NO_ENCONTRADO", assertThrows(RecursoNoEncontradoException.class,
                    () -> servicio.vehiculo(FUNCIONARIO, 1L, matricula, "t")).getCodigo());
        }
    }

    private Vehiculo vehiculo(String matricula, Empresa empresa) {
        Vehiculo v = new Vehiculo(matricula, "Volvo", "FH", 15_000, 25_000, empresa);
        em.persist(v);
        return v;
    }

    private <T> T conEm(T dao) throws Exception {
        set(dao, "em", em);
        return dao;
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
