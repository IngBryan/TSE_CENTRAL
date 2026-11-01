package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

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

import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.ITVDAO;
import tse.lab.frontofficeciudadanos.data.PermisoCirculacionDAO;
import tse.lab.frontofficeciudadanos.data.PermisoVehiculoDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.DatosItv;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.DatosPermiso;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.EdicionVehiculo;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.NuevoVehiculo;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.VehiculoDetalle;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.GestionVehiculosService.VehiculoVista;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.Guia;
import tse.lab.model.Responsable;
import tse.lab.model.TipoPermiso;
import tse.lab.model.Vehiculo;

// ABM de Vehiculos contra H2 con los DAOs reales.
class GestionVehiculosServiceTest {

    private static final String RESPONSABLE = "54074380";
    private static final String OTRO_RESPONSABLE = "11111111";
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Montevideo"));

    private EntityManagerFactory emf;
    private EntityManager em;
    private GestionVehiculosService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        AutorizacionResponsable autorizacion = new AutorizacionResponsable();
        set(autorizacion, "responsables", conEm(new ResponsableDAO()));
        set(autorizacion, "perfiles", conEm(new CiudadanoDAO()));
        servicio = new GestionVehiculosService();
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "vehiculos", conEm(new VehiculoDAO()));
        set(servicio, "permisos", conEm(new PermisoCirculacionDAO()));
        set(servicio, "itvs", conEm(new ITVDAO()));
        set(servicio, "permisosExtra", conEm(new PermisoVehiculoDAO()));
        set(servicio, "guias", conEm(new GuiaDAO()));

        Empresa empresa = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
        empresa.setHabilitada(true);
        Empresa otra = new Empresa(2L, "Otra", "Otra S.A.", "Montevideo", null);
        otra.setHabilitada(true);
        Ciudadano responsable = validado(RESPONSABLE);
        Ciudadano otroResponsable = validado(OTRO_RESPONSABLE);
        em.getTransaction().begin();
        em.persist(empresa);
        em.persist(otra);
        em.persist(responsable);
        em.persist(otroResponsable);
        em.persist(new Responsable(responsable, empresa));
        em.persist(new Responsable(otroResponsable, otra));
        em.persist(new Vehiculo("OTR0001", "Ford", "Cargo", 5000, 8000, otra));
        em.getTransaction().commit();
    }

    // Vehiculo con PNC e ITV vigentes y un permiso a demanda asignado a su empresa
    private Long vehiculoConPermisoExigido(String nombre, boolean pideNumero, boolean pideVencimiento) throws Exception {
        TipoPermiso tipo = new TipoPermiso(nombre, pideNumero, pideVencimiento);
        em.getTransaction().begin();
        em.persist(tipo);
        em.find(Empresa.class, 1L).getPermisosRequeridos().add(tipo);
        em.getTransaction().commit();
        enTx(() -> servicio.crear(RESPONSABLE, new NuevoVehiculo("EXT1234", "Scania", "R", 9000L, 20000L), "t"));
        enTx(() -> servicio.registrarPermiso(RESPONSABLE, "EXT1234",
                new GestionVehiculosService.DatosPermiso("PNC-EXT", HOY.minusDays(1).toString(), HOY.plusDays(30).toString()), "t"));
        enTx(() -> servicio.agregarItv(RESPONSABLE, "EXT1234",
                new GestionVehiculosService.DatosItv(HOY.plusDays(30).toString()), "t"));
        return tipo.getId();
    }

    @Test
    void unPermisoAsignadoSinCargarDejaElVehiculoNoHabilitado() throws Exception {
        Long tipo = vehiculoConPermisoExigido("Mercaderia peligrosa", true, true);
        VehiculoVista v = servicio.detalle(RESPONSABLE, "EXT1234", "t").vehiculo();
        assertFalse(v.habilitado());
        assertEquals(1, v.permisosExtra().size());
        assertFalse(v.permisosExtra().get(0).cargado());

        VehiculoDetalle d = enTx(() -> servicio.registrarPermisoExtra(RESPONSABLE, "EXT1234", tipo,
                new GestionVehiculosService.DatosPermisoExtra(" mp-1 ", HOY.plusDays(10).toString()), "t"));
        assertTrue(d.vehiculo().habilitado());
        assertEquals("MP-1", d.vehiculo().permisosExtra().get(0).numero());
    }

    @Test
    void unPermisoVencidoDejaElVehiculoNoHabilitadoYActualizarloNoDuplica() throws Exception {
        Long tipo = vehiculoConPermisoExigido("Refrigerado", false, true);
        enTx(() -> servicio.registrarPermisoExtra(RESPONSABLE, "EXT1234", tipo,
                new GestionVehiculosService.DatosPermisoExtra(null, HOY.minusDays(1).toString()), "t"));
        assertFalse(servicio.detalle(RESPONSABLE, "EXT1234", "t").vehiculo().habilitado());

        VehiculoDetalle d = enTx(() -> servicio.registrarPermisoExtra(RESPONSABLE, "EXT1234", tipo,
                new GestionVehiculosService.DatosPermisoExtra(null, HOY.toString()), "t"));
        assertTrue(d.vehiculo().habilitado());
        assertEquals(1, d.vehiculo().permisosExtra().size());
    }

    @Test
    void losDatosSePidenSegunElTipoYUnTipoNoAsignadoNoSeAcepta() throws Exception {
        Long tipo = vehiculoConPermisoExigido("Mercaderia peligrosa", true, true);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> servicio.registrarPermisoExtra(
                RESPONSABLE, "EXT1234", tipo, new GestionVehiculosService.DatosPermisoExtra("", null), "t"));
        assertTrue(e.getErrores().containsKey("numero"));
        assertTrue(e.getErrores().containsKey("fechaVencimiento"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.registrarPermisoExtra(RESPONSABLE, "EXT1234",
                tipo + 1, new GestionVehiculosService.DatosPermisoExtra("X", HOY.toString()), "t"));
    }

    @Test
    void unTipoDesactivadoDejaDeExigirse() throws Exception {
        Long tipo = vehiculoConPermisoExigido("Mercaderia peligrosa", true, true);
        em.getTransaction().begin();
        em.find(TipoPermiso.class, tipo).setActivo(false);
        em.getTransaction().commit();
        VehiculoVista v = servicio.detalle(RESPONSABLE, "EXT1234", "t").vehiculo();
        assertTrue(v.habilitado());
        assertTrue(v.permisosExtra().isEmpty());
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void altaNormalizaLaMatriculaYApareceEnElListado() throws Exception {
        VehiculoVista v = alta(" sba-1234 ");
        assertEquals("SBA1234", v.matricula());
        assertEquals("Scania", v.marca());
        assertEquals("R 450", v.modelo());
        assertTrue(v.activo());
        assertNull(v.permiso());
        assertNull(v.itv());
        assertFalse(v.habilitado());
        assertEquals(List.of(v), servicio.listar(RESPONSABLE, "t"));
        // la busqueda tambien normaliza
        assertEquals("SBA1234", servicio.detalle(RESPONSABLE, "sba 1234", "t").vehiculo().matricula());
    }

    @Test
    void validacionesAlDarDeAlta() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.crear(RESPONSABLE, new NuevoVehiculo("ab", " ", null, 0L, 200_000L), "t"));
        assertEquals(5, e.getErrores().size(), e.getErrores().toString());
        assertTrue(e.getErrores().get("matricula").contains("4 a 10"));
        assertTrue(e.getErrores().get("marca").contains("obligatori"));
        assertTrue(e.getErrores().get("modelo").contains("obligatori"));
        assertTrue(e.getErrores().get("peso").contains("mayor que 0"));
        assertTrue(e.getErrores().get("capacidadCargaKg").contains("no puede superar"));
        assertTrue(assertThrows(DatosInvalidosException.class, () -> servicio.crear(RESPONSABLE, null, "t"))
                .getErrores().containsKey("matricula"));
    }

    @Test
    void laMatriculaEsUnicaAunqueSeaDeOtraEmpresa() throws Exception {
        alta("SBA1234");
        assertTrue(assertThrows(DatosInvalidosException.class, () -> alta("SBA 1234")).getErrores().get("matricula")
                .contains("Ya hay"));
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> alta("otr-0001"));
        assertFalse(e.getErrores().get("matricula").contains("Otra"), "no revela la otra empresa");
    }

    @Test
    void editarCambiaLosDatosPeroNoLaMatricula() throws Exception {
        alta("SBA1234");
        VehiculoDetalle d = enTx(() -> servicio.editar(RESPONSABLE, "SBA1234",
                new EdicionVehiculo("Scania", "R 500", 9500L, 27000L), "t"));
        assertEquals("R 500", d.vehiculo().modelo());
        assertEquals(9500, d.vehiculo().peso());
        assertEquals(27000, d.vehiculo().capacidadCargaKg());
        assertEquals("No cambiaste ningún dato del vehículo.", assertThrows(DatosInvalidosException.class,
                () -> servicio.editar(RESPONSABLE, "SBA1234", new EdicionVehiculo("Scania", "R 500", 9500L, 27000L), "t"))
                .getErrores().get("general"));
    }

    @Test
    void permisoYItvVigentesDejanAlVehiculoHabilitado() throws Exception {
        alta("SBA1234");
        VehiculoDetalle conPermiso = enTx(() -> servicio.registrarPermiso(RESPONSABLE, "SBA1234",
                new DatosPermiso(" pnc-001 ", HOY.minusMonths(1).toString(), HOY.plusYears(1).toString()), "t"));
        assertEquals("PNC-001", conPermiso.vehiculo().permiso().nro());
        assertTrue(conPermiso.vehiculo().permiso().vigente());
        assertFalse(conPermiso.vehiculo().habilitado(), "falta la ITV");

        enTx(() -> servicio.agregarItv(RESPONSABLE, "SBA1234", new DatosItv(HOY.minusDays(1).toString()), "t"));
        VehiculoDetalle d = enTx(() -> servicio.agregarItv(RESPONSABLE, "SBA1234",
                new DatosItv(HOY.plusMonths(6).toString()), "t"));
        assertEquals(2, d.itvs().size());
        assertEquals(HOY.plusMonths(6).toString(), d.vehiculo().itv().fechaVencimiento(), "la vigente es la mas lejana");
        assertFalse(d.itvs().get(1).vigente());
        assertTrue(d.vehiculo().habilitado());
    }

    @Test
    void unVehiculoDadoDeBajaNoEstaHabilitado() throws Exception {
        alta("SBA1234");
        enTx(() -> servicio.registrarPermiso(RESPONSABLE, "SBA1234",
                new DatosPermiso("PNC-001", HOY.toString(), HOY.plusYears(1).toString()), "t"));
        assertTrue(enTx(() -> servicio.agregarItv(RESPONSABLE, "SBA1234", new DatosItv(HOY.plusMonths(6).toString()),
                "t")).vehiculo().habilitado());
        assertFalse(enTx(() -> servicio.darDeBaja(RESPONSABLE, "SBA1234", "t")).habilitado());
    }

    @Test
    void permisoVencidoNoHabilita() throws Exception {
        alta("SBA1234");
        enTx(() -> servicio.agregarItv(RESPONSABLE, "SBA1234", new DatosItv(HOY.plusMonths(6).toString()), "t"));
        VehiculoDetalle d = enTx(() -> servicio.registrarPermiso(RESPONSABLE, "SBA1234",
                new DatosPermiso("PNC-001", HOY.minusYears(2).toString(), HOY.minusDays(1).toString()), "t"));
        assertFalse(d.vehiculo().permiso().vigente());
        assertFalse(d.vehiculo().habilitado());
    }

    @Test
    void reemplazarElPermisoConOtroNumero() throws Exception {
        alta("SBA1234");
        enTx(() -> servicio.registrarPermiso(RESPONSABLE, "SBA1234",
                new DatosPermiso("PNC-001", HOY.toString(), HOY.plusYears(1).toString()), "t"));
        VehiculoDetalle d = enTx(() -> servicio.registrarPermiso(RESPONSABLE, "SBA1234",
                new DatosPermiso("PNC-002", HOY.toString(), HOY.plusYears(2).toString()), "t"));
        assertEquals("PNC-002", d.vehiculo().permiso().nro());
        assertNull(inyectar(new PermisoCirculacionDAO()).buscarPorNro("PNC-001"));
    }

    @Test
    void validacionesDelPermisoYLaItv() throws Exception {
        alta("SBA1234");
        alta("SAB5678");
        enTx(() -> servicio.registrarPermiso(RESPONSABLE, "SAB5678",
                new DatosPermiso("PNC-001", HOY.toString(), HOY.plusYears(1).toString()), "t"));
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> servicio.registrarPermiso(
                RESPONSABLE, "SBA1234", new DatosPermiso("pnc-001", HOY.toString(), HOY.toString()), "t"));
        assertTrue(e.getErrores().get("nro").contains("otro vehículo"));
        assertTrue(e.getErrores().get("fechaVencimiento").contains("posterior"));
        assertEquals(3, assertThrows(DatosInvalidosException.class, () -> servicio.registrarPermiso(RESPONSABLE,
                "SBA1234", new DatosPermiso("", "ayer", null), "t")).getErrores().size());

        enTx(() -> servicio.agregarItv(RESPONSABLE, "SBA1234", new DatosItv(HOY.toString()), "t"));
        assertTrue(assertThrows(DatosInvalidosException.class,
                () -> servicio.agregarItv(RESPONSABLE, "SBA1234", new DatosItv(HOY.toString()), "t"))
                .getErrores().get("fechaVencimiento").contains("Ya hay"));
        assertTrue(assertThrows(DatosInvalidosException.class,
                () -> servicio.agregarItv(RESPONSABLE, "SBA1234", new DatosItv("31/12/2027"), "t"))
                .getErrores().containsKey("fechaVencimiento"));
    }

    @Test
    void bajaLogicaYYaNoSePuedeModificar() throws Exception {
        alta("SBA1234");
        VehiculoVista baja = enTx(() -> servicio.darDeBaja(RESPONSABLE, "SBA1234", "t"));
        assertFalse(baja.activo());
        assertFalse(servicio.listar(RESPONSABLE, "t").get(0).activo(), "sigue en el listado");
        assertTrue(inyectar(new VehiculoDAO()).buscarActivosPorEmpresa(em.find(Empresa.class, 1L)).isEmpty());
        assertEquals("VEHICULO_DADO_DE_BAJA", assertThrows(ConflictoException.class, () -> servicio.editar(RESPONSABLE,
                "SBA1234", new EdicionVehiculo("Volvo", "FH", 1L, 1L), "t")).getCodigo());
        assertEquals("VEHICULO_DADO_DE_BAJA", assertThrows(ConflictoException.class,
                () -> servicio.agregarItv(RESPONSABLE, "SBA1234", new DatosItv(HOY.toString()), "t")).getCodigo());
        assertFalse(enTx(() -> servicio.darDeBaja(RESPONSABLE, "SBA1234", "t")).activo(), "la segunda baja no falla");
    }

    @Test
    void noSeDaDeBajaConViajesAsignados() throws Exception {
        alta("SBA1234");
        enTx(() -> {
            Guia g = new Guia("GV-1", "Forestal", 20000, HOY, "Rivera", "Montevideo", em.find(Empresa.class, 1L));
            g.asignar(em.find(Vehiculo.class, "SBA1234"), null);
            em.persist(g);
            return g;
        });
        assertEquals("VEHICULO_CON_VIAJES", assertThrows(ConflictoException.class,
                () -> servicio.darDeBaja(RESPONSABLE, "SBA1234", "t")).getCodigo());
    }

    @Test
    void soloElResponsableDeLaEmpresaVeYModificaSusVehiculos() throws Exception {
        alta("SBA1234");
        assertEquals(List.of("OTR0001"),
                servicio.listar(OTRO_RESPONSABLE, "t").stream().map(VehiculoVista::matricula).toList());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.detalle(OTRO_RESPONSABLE, "SBA1234", "t"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.darDeBaja(OTRO_RESPONSABLE, "SBA1234", "t"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.detalle(RESPONSABLE, "NOEXISTE", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.listar("99999999", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.crear(null, null, "t"));
    }

    @Test
    void empresaNoHabilitadaNoPuedeGestionarVehiculos() {
        enTxSinResultado(() -> em.find(Empresa.class, 1L).setHabilitada(false));
        assertThrows(NoAutorizadoException.class, () -> servicio.listar(RESPONSABLE, "t"));
    }

    // ===== helpers =====

    private VehiculoVista alta(String matricula) throws Exception {
        return enTx(() -> servicio.crear(RESPONSABLE, new NuevoVehiculo(matricula, " Scania ", "R  450", 9000L, 26000L), "t"));
    }

    private static Ciudadano validado(String ci) {
        Ciudadano p = new Ciudadano(ci, null, null);
        p.setEstado(EstadoValidacion.VALIDADO);
        return p;
    }

    interface Accion<T> {
        T ejecutar() throws Exception;
    }

    private <T> T enTx(Accion<T> accion) throws Exception {
        em.getTransaction().begin();
        try {
            T r = accion.ejecutar();
            em.getTransaction().commit();
            return r;
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        }
    }

    private void enTxSinResultado(Runnable accion) {
        em.getTransaction().begin();
        accion.run();
        em.getTransaction().commit();
    }

    private <T> T inyectar(T dao) throws Exception {
        return conEm(dao);
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
