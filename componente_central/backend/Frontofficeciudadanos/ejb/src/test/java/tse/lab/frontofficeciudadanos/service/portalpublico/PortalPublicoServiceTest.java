package tse.lab.frontofficeciudadanos.service.portalpublico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.EmpresaDAO;
import tse.lab.frontofficeciudadanos.data.ITVDAO;
import tse.lab.frontofficeciudadanos.data.PermisoCirculacionDAO;
import tse.lab.frontofficeciudadanos.data.PortalPublicoDAO;
import tse.lab.frontofficeciudadanos.data.RubroDAO;
import tse.lab.frontofficeciudadanos.data.TipoCargaDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.portalpublico.Agregacion.FilaPublica;
import tse.lab.frontofficeciudadanos.service.portalpublico.PortalPublicoService.Estadisticas;
import tse.lab.frontofficeciudadanos.service.portalpublico.PortalPublicoService.ValidacionEmpresa;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Funcionario;
import tse.lab.model.FundamentoResolucion;
import tse.lab.model.Guia;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Rubro;
import tse.lab.model.TipoCarga;
import tse.lab.model.Vehiculo;

// Portal publico contra H2 con los DAOs reales: validacion de empresas y estadisticas con el umbral.
class PortalPublicoServiceTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Montevideo"));

    private EntityManagerFactory emf;
    private EntityManager em;
    private PortalPublicoService servicio;
    private int guias;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        servicio = new PortalPublicoService(3);
        set(servicio, "portal", conEm(new PortalPublicoDAO()));
        set(servicio, "empresas", conEm(new EmpresaDAO()));
        set(servicio, "vehiculos", conEm(new VehiculoDAO()));
        set(servicio, "permisos", conEm(new PermisoCirculacionDAO()));
        set(servicio, "itvs", conEm(new ITVDAO()));
        set(servicio, "rubros", conEm(new RubroDAO()));
        set(servicio, "tiposCarga", conEm(new TipoCargaDAO()));

        em.getTransaction().begin();
        Rubro agro = new Rubro(1L, "Agropecuario");
        Rubro construccion = new Rubro(2L, "Construccion");
        em.persist(agro);
        em.persist(construccion);
        em.persist(new Rubro(3L, "Forestal"));
        em.persist(new TipoCarga(1L, "Granos a granel"));
        em.persist(new TipoCarga(2L, "Arena"));
        // Empresas 1 a 3 habilitadas y de rubro "Agropecuario", cada una con un vehiculo habilitado; la 4 no
        // habilitada y de rubro "Construccion", cada una con un vehiculo habilitado; la 4 no habilitada
        for (long nro = 1; nro <= 4; nro++) {
            Empresa e = new Empresa(nro, "Transportes " + nro, "Transportes " + nro + " S.A.", "Ruta " + nro, null);
            e.setHabilitada(nro <= 3);
            e.setRubros(List.of(nro <= 3 ? agro : construccion));
            em.persist(e);
            Vehiculo v = new Vehiculo("SAA000" + nro, "Scania", "R 450", 9000, 26000, e);
            em.persist(v);
            em.persist(new PermisoCirculacion("PNC-" + nro, HOY.minusDays(10), HOY.plusDays(300), v));
            em.persist(new ITV(HOY.minusDays(10), HOY.plusDays(100), v));
            // Todas viajan en "Agropecuario"; solo la 4 en "Construccion"
            viaje(e, v, "Agropecuario", "Granos a granel", 20000);
        }
        Empresa cuatro = em.find(Empresa.class, 4L);
        viaje(cuatro, em.find(Vehiculo.class, "SAA0004"), "construcción ", "Contenedor", 10000);
        // Vehiculo de la 1 con la ITV vencida
        Vehiculo vencido = new Vehiculo("SBB9999", "Volvo", "FH", 9000, 26000, em.find(Empresa.class, 1L));
        em.persist(vencido);
        em.persist(new PermisoCirculacion("PNC-X", HOY.minusDays(10), HOY.plusDays(300), vencido));
        em.persist(new ITV(HOY.minusDays(400), HOY.minusDays(1), vencido));
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void validarUnaEmpresaYUnVehiculoSuyo() throws Exception {
        ValidacionEmpresa v = servicio.validarEmpresa(1L, "saa-0001", "t");

        assertTrue(v.habilitada());
        assertEquals("SAA0001", v.vehiculo().matricula());
        assertTrue(v.vehiculo().deLaEmpresa());
        assertTrue(v.vehiculo().habilitado());
    }

    @Test
    void unVehiculoConLaItvVencidaNoEstaHabilitado() throws Exception {
        ValidacionEmpresa v = servicio.validarEmpresa(1L, "SBB9999", "t");

        assertTrue(v.vehiculo().permisoVigente());
        assertFalse(v.vehiculo().itvVigente());
        assertFalse(v.vehiculo().habilitado());
    }

    @Test
    void unVehiculoDeOtraEmpresaNoMuestraSusDatos() throws Exception {
        ValidacionEmpresa v = servicio.validarEmpresa(1L, "SAA0002", "t");

        assertFalse(v.vehiculo().deLaEmpresa());
        assertFalse(v.vehiculo().habilitado());
        assertNull(v.vehiculo().permisoVence());
        assertNull(v.vehiculo().activo());
    }

    @Test
    void unaEmpresaNoHabilitadaNoTieneVehiculosHabilitados() throws Exception {
        ValidacionEmpresa v = servicio.validarEmpresa(4L, "SAA0004", "t");

        assertFalse(v.habilitada());
        assertFalse(v.vehiculo().habilitado());
    }

    @Test
    void unaEmpresaQueNoExisteEs404() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.validarEmpresa(99L, null, "t"));
    }

    @Test
    void buscarPorNombre() throws Exception {
        assertEquals(4, servicio.buscarEmpresas("transportes", "t").size());
        assertEquals(List.of(2L), servicio.buscarEmpresas("PORTES 2", "t").stream()
                .map(PortalPublicoService.EmpresaEncontrada::nroEmpresa).toList());
        assertThrows(DatosInvalidosException.class, () -> servicio.buscarEmpresas("ab", "t"));
    }

    @Test
    void lasEstadisticasReservanLoQueNoJuntaTresEmpresas() throws Exception {
        Estadisticas e = servicio.estadisticas(HOY.getYear(), "t");

        assertEquals(3, e.empresasHabilitadas().valor());
        assertEquals(3, e.vehiculosHabilitados().valor());
        assertEquals(5, e.viajes().valor());
        assertEquals(90000, e.kgTransportados().valor());
        assertEquals(12, e.viajesPorMes().size());

        // Carga por rubro: una fila por rubro del catalogo, por kg. "construcción " se cuenta en "Construccion".
        // "Construccion" es de una sola empresa: se reserva, y con ella "Agropecuario" (supresion
        // complementaria), porque si no se deduciria restando del total. "Forestal" no tiene viajes: 0 se publica.
        assertEquals(List.of("Agropecuario", "Construccion", "Forestal"),
                e.cargaPorRubro().stream().map(FilaPublica::etiqueta).toList());
        assertEquals(List.of(true, true, false), e.cargaPorRubro().stream().map(FilaPublica::reservado).toList());
        assertEquals(0, e.cargaPorRubro().get(2).cantidad());
    }

    @Test
    void laCargaPorTipoJuntaEnOtrosLoQueNoEstaEnElCatalogo() throws Exception {
        Estadisticas e = servicio.estadisticas(HOY.getYear(), "t");

        // "Contenedor" no esta en el catalogo: va a "Otros tipos de carga" (sin su nombre), de una sola empresa,
        // asi que se reserva y con ella "Granos a granel". "Arena" en 0.
        assertEquals(List.of("Granos a granel", "Arena", "Otros tipos de carga"),
                e.cargaPorTipoCarga().stream().map(FilaPublica::etiqueta).toList());
        assertEquals(PortalPublicoService.OTROS, e.cargaPorTipoCarga().get(2).clave());
        assertEquals(List.of(true, false, true), e.cargaPorTipoCarga().stream().map(FilaPublica::reservado).toList());
    }

    @Test
    void empresasHabilitadasPorRubroSinContarLasNoHabilitadas() throws Exception {
        Estadisticas e = servicio.estadisticas(HOY.getYear(), "t");

        // La 4 es de "Construccion" pero no esta habilitada: no cuenta
        assertEquals(List.of("Agropecuario", "Construccion", "Forestal"),
                e.empresasPorRubro().stream().map(FilaPublica::etiqueta).toList());
        assertEquals(java.util.Arrays.asList(3L, 0L, 0L),
                e.empresasPorRubro().stream().map(FilaPublica::cantidad).toList());
    }

    @Test
    void unRubroConMenosDeTresEmpresasSeReservaConSupresionComplementaria() throws Exception {
        em.getTransaction().begin();
        em.find(Empresa.class, 1L).setRubros(List.of(em.find(Rubro.class, 1L), em.find(Rubro.class, 3L)));
        em.getTransaction().commit();
        em.clear();

        Estadisticas e = servicio.estadisticas(HOY.getYear(), "t");

        // "Forestal" solo lo tiene la empresa 1: se reserva y con ella "Agropecuario", la mas chica visible
        FilaPublica forestal = e.empresasPorRubro().stream().filter(f -> f.etiqueta().equals("Forestal"))
                .findFirst().orElseThrow();
        assertTrue(forestal.reservado());
        assertNull(forestal.cantidad());
        assertTrue(e.empresasPorRubro().get(0).reservado());
    }

    @Test
    void laValidacionMuestraLosRubrosDeLaEmpresa() throws Exception {
        assertEquals(List.of("Agropecuario"), servicio.validarEmpresa(1L, null, "t").rubros());
        assertEquals(List.of("Construccion"), servicio.validarEmpresa(4L, null, "t").rubros());
    }

    @Test
    void unAnioFueraDeRangoEsInvalido() {
        assertThrows(DatosInvalidosException.class, () -> servicio.estadisticas(HOY.getYear() + 1, "t"));
        assertThrows(DatosInvalidosException.class, () -> servicio.estadisticas(1999, "t"));
    }

    @Test
    void indicadoresDeInfraccionesYResolucionConTresEmpresas() throws Exception {
        em.getTransaction().begin();
        Funcionario f = new Funcionario("54074380", "f@mtop.gub.uy");
        em.persist(f);
        for (long nro = 1; nro <= 3; nro++) {
            caso("SAA000" + nro, EstadoCaso.CONFIRMADA, f);
        }
        caso("SAA0001", EstadoCaso.DESESTIMADA, f);
        em.getTransaction().commit();

        Estadisticas e = servicio.estadisticas(HOY.getYear(), "t");

        // 3 confirmadas en 5 viajes finalizados
        assertEquals(60.0, e.infraccionesCada100Viajes().valor());
        assertEquals(4, e.casosResueltos().valor());
        assertEquals(25.0, e.porcentajeDesestimados().valor());
        assertEquals(0.0, e.medianaDiasResolucion().valor());
    }

    @Test
    void losIndicadoresDeUnaSolaEmpresaSeReservan() throws Exception {
        em.getTransaction().begin();
        Funcionario f = new Funcionario("54074380", "f@mtop.gub.uy");
        em.persist(f);
        caso("SAA0001", EstadoCaso.CONFIRMADA, f);
        caso("SAA0001", EstadoCaso.DESESTIMADA, f);
        em.getTransaction().commit();

        Estadisticas e = servicio.estadisticas(HOY.getYear(), "t");

        assertTrue(e.infraccionesCada100Viajes().reservado());
        assertNull(e.infraccionesCada100Viajes().valor());
        assertTrue(e.casosResueltos().reservado());
        assertTrue(e.porcentajeDesestimados().reservado());
        assertTrue(e.medianaDiasResolucion().reservado());
    }

    @Test
    void sinCasosResueltosNoHayDatosPeroNoSeReserva() throws Exception {
        Estadisticas e = servicio.estadisticas(HOY.getYear(), "t");

        assertEquals(0.0, e.infraccionesCada100Viajes().valor());
        assertEquals(0, e.casosResueltos().valor());
        assertNull(e.porcentajeDesestimados().valor());
        assertFalse(e.porcentajeDesestimados().reservado());
    }

    @Test
    void medianaConCantidadParYImpar() {
        assertEquals(2.0, PortalPublicoService.mediana(List.of(3.0, 1.0, 2.0)));
        assertEquals(2.5, PortalPublicoService.mediana(List.of(4.0, 1.0, 2.0, 3.0)));
        // Un caso trabado no la mueve
        assertEquals(2.0, PortalPublicoService.mediana(List.of(1.0, 2.0, 300.0)));
    }

    // Caso resuelto del vehiculo, con su fundamento
    private void caso(String matricula, EstadoCaso resolucion, Funcionario funcionario) {
        CasoFiscalizacion c = new CasoFiscalizacion("t", "EXCESO_CARGA", em.find(Vehiculo.class, matricula), null);
        c.cambiarEstado(EstadoCaso.NOTIFICADA);
        c.cambiarEstado(EstadoCaso.EN_DESCARGO);
        c.cambiarEstado(resolucion);
        em.persist(c);
        em.persist(new FundamentoResolucion("t", funcionario, c));
    }

    private void viaje(Empresa empresa, Vehiculo vehiculo, String rubro, String tipoCarga, long kg) {
        Guia g = new Guia("GV-T" + (++guias), rubro, kg, HOY, "Montevideo", "Paysandu", empresa);
        g.setTipoCarga(tipoCarga);
        g.setVehiculo(vehiculo);
        g.cambiarEstado(EstadoGuia.FINALIZADA);
        em.persist(g);
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
