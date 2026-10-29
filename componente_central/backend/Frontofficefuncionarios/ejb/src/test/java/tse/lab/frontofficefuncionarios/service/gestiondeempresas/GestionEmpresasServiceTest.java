package tse.lab.frontofficefuncionarios.service.gestiondeempresas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficefuncionarios.data.CiudadanoDAO;
import tse.lab.frontofficefuncionarios.data.EmpresaDAO;
import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.frontofficefuncionarios.data.ResponsableDAO;
import tse.lab.frontofficefuncionarios.service.ConflictoException;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AltaEmpresa;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AsociacionUsuario;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.CambiosEmpresa;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.EmpresaDetalle;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.EmpresaResumen;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.OperacionRealizada;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.Funcionario;
import tse.lab.model.Responsable;

// CU Gestionar empresas de transporte contra H2 con los DAOs reales.
class GestionEmpresasServiceTest {

    private static final String FUNCIONARIO = "45678901";
    private static final String NO_FUNCIONARIO = "54074380";
    private static final String RESPONSABLE = "11111111";
    private static final String CIUDADANO = "22222222";
    private static final String PENDIENTE = "44444444";
    private static final String RECHAZADO = "33333333";

    private EntityManagerFactory emf;
    private EntityManager em;
    private GestionEmpresasService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficefuncionariosTestPersistenceUnit");
        em = emf.createEntityManager();
        AutorizacionFuncionario autorizacion = new AutorizacionFuncionario();
        set(autorizacion, "funcionarios", conEm(new FuncionarioDAO()));
        servicio = new GestionEmpresasService();
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "empresas", conEm(new EmpresaDAO()));
        set(servicio, "ciudadanos", conEm(new CiudadanoDAO()));
        set(servicio, "responsables", conEm(new ResponsableDAO()));

        Empresa conResponsable = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
        conResponsable.setHabilitada(true);
        Empresa sinResponsable = new Empresa(2L, "Fletes del Norte", "FDN S.R.L.", "Rivera", null);
        Ciudadano responsable = new Ciudadano(RESPONSABLE, "r@example.com", "sub-1");
        responsable.setPrimerNombre("Ana");
        responsable.setPrimerApellido("Pérez");
        responsable.setEstado(EstadoValidacion.VALIDADO);
        Ciudadano ciudadano = new Ciudadano(CIUDADANO, "c@example.com", "sub-2");
        ciudadano.setEstado(EstadoValidacion.VALIDADO);
        Ciudadano pendiente = new Ciudadano(PENDIENTE, null, null);
        Ciudadano rechazado = new Ciudadano(RECHAZADO, null, null);
        rechazado.setEstado(EstadoValidacion.RECHAZADO);

        em.getTransaction().begin();
        em.persist(new Funcionario(FUNCIONARIO, "funcionario@mtop.gub.uy"));
        em.persist(conResponsable);
        em.persist(sinResponsable);
        em.persist(responsable);
        em.persist(ciudadano);
        em.persist(pendiente);
        em.persist(rechazado);
        em.persist(new Responsable(responsable, conResponsable));
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    // ===== FA5: usuario no autorizado =====

    @Test
    void quienNoEsFuncionarioNoAccedeANingunaOperacionNiModificaNada() {
        AltaEmpresa alta = new AltaEmpresa(9L, "X", "X", "X", null, null);
        CambiosEmpresa cambios = new CambiosEmpresa("Otro", "Otra", "Otra", false, 0L);
        for (String ci : new String[] { NO_FUNCIONARIO, null, " " }) {
            assertThrows(NoAutorizadoException.class, () -> servicio.listar(ci, "t"));
            assertThrows(NoAutorizadoException.class, () -> servicio.detalle(ci, 1L, "t"));
            assertThrows(NoAutorizadoException.class, () -> enTx(() -> servicio.alta(ci, alta, "t")));
            assertThrows(NoAutorizadoException.class, () -> enTx(() -> servicio.modificar(ci, 1L, cambios, "t")));
            assertThrows(NoAutorizadoException.class,
                    () -> enTx(() -> servicio.asociarUsuario(ci, 2L, new AsociacionUsuario(CIUDADANO), "t")));
        }
        em.clear();
        assertNull(em.find(Empresa.class, 9L));
        assertEquals("Transportes del Este", em.find(Empresa.class, 1L).getNombrePublico());
        assertNull(em.find(Responsable.class, CIUDADANO));
    }

    // ===== Flujo principal y FA2: listado y consulta =====

    @Test
    void listaLasEmpresasOrdenadasConSuResponsable() throws Exception {
        List<EmpresaResumen> lista = servicio.listar(FUNCIONARIO, "t");

        assertEquals(List.of(new EmpresaResumen(1L, "Transportes del Este", "TDE S.A.", true, RESPONSABLE),
                new EmpresaResumen(2L, "Fletes del Norte", "FDN S.R.L.", false, null)), lista);
    }

    @Test
    void elListadoNoHaceUnaConsultaPorEmpresaNiCargaLosCiudadanos() throws Exception {
        Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        servicio.listar(FUNCIONARIO, "t");
        em.clear();
        estadisticas.clear();

        servicio.listar(FUNCIONARIO, "t");

        // Una consulta para el funcionario (autorizacion) y una para las empresas con su responsable
        assertEquals(2, estadisticas.getPrepareStatementCount());
        assertEquals(0, estadisticas.getEntityStatistics(Ciudadano.class.getName()).getLoadCount());
    }

    @Test
    void consultaElDetalleConElPrimerNombreYApellidoDelResponsable() throws Exception {
        EmpresaDetalle d = servicio.detalle(FUNCIONARIO, 1L, "t");

        assertEquals("Pando", d.direccion());
        assertTrue(d.habilitada());
        assertEquals(new GestionEmpresasService.ResponsableVista(RESPONSABLE, "Ana", "Pérez", "VALIDADO"),
                d.responsable());
        assertNull(servicio.detalle(FUNCIONARIO, 2L, "t").responsable());
    }

    @Test
    void consultarUnaEmpresaInexistenteEs404() {
        for (Long nro : new Long[] { 99L, null }) {
            RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class,
                    () -> servicio.detalle(FUNCIONARIO, nro, "t"));
            assertEquals("EMPRESA_NO_ENCONTRADA", e.getCodigo());
        }
    }

    // ===== FA1: alta =====

    @Test
    void altaRegistraLaEmpresaNoHabilitadaPorDefecto() throws Exception {
        OperacionRealizada r = enTx(() -> servicio.alta(FUNCIONARIO,
                new AltaEmpresa(3L, "  Carga   Sur ", "Carga Sur S.A.", "Salto", null, null), "t"));

        assertEquals("La empresa fue registrada.", r.mensaje());
        assertEquals(new EmpresaDetalle(3L, "Carga Sur", "Carga Sur S.A.", "Salto", false, null, 0L), r.empresa());
        em.clear();
        Empresa guardada = em.find(Empresa.class, 3L);
        assertNotNull(guardada);
        assertEquals("Carga Sur", guardada.getNombrePublico());
        assertFalse(guardada.isHabilitada());
    }

    @Test
    void altaConResponsablePuedeDejarLaEmpresaHabilitada() throws Exception {
        enTx(() -> servicio.alta(FUNCIONARIO, new AltaEmpresa(3L, "A", "B", "C", true, CIUDADANO), "t"));

        em.clear();
        assertTrue(em.find(Empresa.class, 3L).isHabilitada());
    }

    @Test
    void altaHabilitadaSinResponsableEs400YNoRegistra() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> enTx(() -> servicio.alta(FUNCIONARIO, new AltaEmpresa(3L, "A", "B", "C", true, " "), "t")));

        assertEquals(List.of("habilitada"), List.copyOf(e.getErrores().keySet()));
        em.clear();
        assertNull(em.find(Empresa.class, 3L));
    }

    @Test
    void altaConDatosInvalidosDevuelveTodosLosErroresYNoRegistra() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> enTx(
                () -> servicio.alta(FUNCIONARIO, new AltaEmpresa(0L, " ", null, "x".repeat(201), null, "12a"), "t")));

        assertEquals(List.of("nroEmpresa", "nombrePublico", "razonSocial", "direccion", "ciResponsable"),
                List.copyOf(e.getErrores().keySet()));
        assertThrows(DatosInvalidosException.class, () -> enTx(() -> servicio.alta(FUNCIONARIO, null, "t")));
        em.clear();
        assertNull(em.find(Empresa.class, 0L));
    }

    @Test
    void altaConUnNumeroYaRegistradoEs409YNoModificaLaExistente() {
        ConflictoException e = assertThrows(ConflictoException.class,
                () -> enTx(() -> servicio.alta(FUNCIONARIO, new AltaEmpresa(1L, "Otra", "Otra", "Otra", null, null), "t")));

        assertEquals("EMPRESA_EXISTENTE", e.getCodigo());
        em.clear();
        assertEquals("Transportes del Este", em.find(Empresa.class, 1L).getNombrePublico());
    }

    @Test
    void siOtraAltaRegistroElMismoNumeroMientrasTantoEs409YNoQuedaNada() throws Exception {
        // Emula la carrera: el control de buscarPorId no la ve (la otra alta todavia no habia hecho commit),
        // pero el insert choca con la PK. Tiene que ser 409, no un error al cerrar la transaccion.
        EmpresaDAO sinVerLaOtra = new EmpresaDAO() {
            @Override
            public Empresa buscarPorId(Long nroEmpresa) {
                return null;
            }
        };
        set(servicio, "empresas", conEm(sinVerLaOtra));

        ConflictoException e = assertThrows(ConflictoException.class, () -> enTx(() -> servicio.alta(FUNCIONARIO,
                new AltaEmpresa(1L, "Otra", "Otra", "Otra", true, CIUDADANO), "t")));

        assertEquals("EMPRESA_EXISTENTE", e.getCodigo());
        em.clear();
        assertEquals("Transportes del Este", em.find(Empresa.class, 1L).getNombrePublico());
        assertNull(em.find(Responsable.class, CIUDADANO));
    }

    @Test
    void altaConResponsableRegistraLaEmpresaYSuResponsableJuntos() throws Exception {
        OperacionRealizada r = enTx(() -> servicio.alta(FUNCIONARIO,
                new AltaEmpresa(3L, "Carga Sur", "Carga Sur S.A.", "Salto", true, " " + CIUDADANO + " "), "t"));

        assertEquals(CIUDADANO, r.empresa().responsable().ci());
        em.clear();
        assertEquals(CIUDADANO, em.find(Empresa.class, 3L).getResponsable().getCi());
        assertEquals(3L, em.find(Responsable.class, CIUDADANO).getEmpresa().getNroEmpresa());
    }

    @Test
    void siElResponsableNoSePuedeAsociarLaEmpresaTampocoSeCrea() {
        assertAltaRechazada(ConflictoException.class, "RESPONSABLE_DE_OTRA_EMPRESA", RESPONSABLE);
        assertAltaRechazada(ConflictoException.class, "USUARIO_NO_VALIDADO", PENDIENTE);
        assertAltaRechazada(RecursoNoEncontradoException.class, "USUARIO_NO_ENCONTRADO", "5555555");
    }

    private void assertAltaRechazada(Class<? extends Exception> tipo, String codigo, String ciResponsable) {
        Exception e = assertThrows(tipo, () -> enTx(() -> servicio.alta(FUNCIONARIO,
                new AltaEmpresa(3L, "Carga Sur", "Carga Sur S.A.", "Salto", null, ciResponsable), "t")));
        assertEquals(codigo, e instanceof ConflictoException c ? c.getCodigo()
                : ((RecursoNoEncontradoException) e).getCodigo());
        em.clear();
        assertNull(em.find(Empresa.class, 3L));
    }

    // ===== FA4, paso 2: usuarios que se pueden elegir como responsable =====

    @Test
    void soloSonElegiblesLosValidadosQueNoSonResponsables() throws Exception {
        // RESPONSABLE esta validado pero ya es responsable; PENDIENTE y RECHAZADO no estan validados
        assertEquals(List.of(new GestionEmpresasService.UsuarioDisponible(CIUDADANO, null, null)),
                servicio.usuariosDisponibles(FUNCIONARIO, null, "t"));
        assertEquals(1, servicio.usuariosDisponibles(FUNCIONARIO, "222", "t").size());
        assertTrue(servicio.usuariosDisponibles(FUNCIONARIO, "9", "t").isEmpty());
    }

    @Test
    void elFiltroDeUsuariosDisponiblesSoloAdmiteDigitos() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.usuariosDisponibles(FUNCIONARIO, "12%", "t"));
        assertTrue(e.getErrores().containsKey("ci"));
        assertThrows(NoAutorizadoException.class, () -> servicio.usuariosDisponibles(NO_FUNCIONARIO, null, "t"));
    }

    // ===== FA3: modificacion =====

    @Test
    void modificaLosDatosYConservaHabilitadaSiNoViene() throws Exception {
        OperacionRealizada r = enTx(() -> servicio.modificar(FUNCIONARIO, 1L,
                new CambiosEmpresa("TDE", "TDE Sociedad Anónima", "Ruta 8 km 29", null, 0L), "t"));

        assertEquals("La empresa fue modificada.", r.mensaje());
        em.clear();
        Empresa e = em.find(Empresa.class, 1L);
        assertEquals("TDE", e.getNombrePublico());
        assertEquals("TDE Sociedad Anónima", e.getRazonSocial());
        assertEquals("Ruta 8 km 29", e.getDireccion());
        assertTrue(e.isHabilitada());
        assertEquals(RESPONSABLE, e.getResponsable().getCi());
    }

    @Test
    void modificarPuedeDeshabilitarYHabilitarUnaEmpresaConResponsable() throws Exception {
        enTx(() -> servicio.modificar(FUNCIONARIO, 1L, new CambiosEmpresa("A", "B", "C", false, 0L), "t"));
        em.clear();
        assertFalse(em.find(Empresa.class, 1L).isHabilitada());

        enTx(() -> servicio.modificar(FUNCIONARIO, 1L, new CambiosEmpresa("A", "B", "C", true, 1L), "t"));
        em.clear();
        assertTrue(em.find(Empresa.class, 1L).isHabilitada());
    }

    @Test
    void noSeHabilitaUnaEmpresaSinResponsable() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> enTx(() -> servicio.modificar(FUNCIONARIO, 2L, new CambiosEmpresa("A", "B", "C", true, 0L), "t")));

        assertEquals(List.of("habilitada"), List.copyOf(e.getErrores().keySet()));
        em.clear();
        Empresa empresa = em.find(Empresa.class, 2L);
        assertFalse(empresa.isHabilitada());
        assertEquals("Fletes del Norte", empresa.getNombrePublico());
    }

    @Test
    void unaEmpresaHabilitadaSinResponsableSePuedeSeguirEditando() throws Exception {
        // Datos anteriores a la regla: habilitada sin responsable
        em.getTransaction().begin();
        em.find(Empresa.class, 2L).setHabilitada(true);
        em.getTransaction().commit();
        em.clear();

        enTx(() -> servicio.modificar(FUNCIONARIO, 2L, new CambiosEmpresa("Otro", "B", "C", true, 1L), "t"));
        em.clear();
        assertEquals("Otro", em.find(Empresa.class, 2L).getNombrePublico());
    }

    @Test
    void modificarConDatosInvalidosNoCambiaNada() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> enTx(() -> servicio
                .modificar(FUNCIONARIO, 1L, new CambiosEmpresa("", "x".repeat(101), "Pando", false, 0L), "t")));

        assertEquals(List.of("nombrePublico", "razonSocial"), List.copyOf(e.getErrores().keySet()));
        em.clear();
        Empresa empresa = em.find(Empresa.class, 1L);
        assertEquals("Transportes del Este", empresa.getNombrePublico());
        assertTrue(empresa.isHabilitada());
    }

    @Test
    void modificarUnaEmpresaInexistenteEs404() {
        RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class, () -> enTx(
                () -> servicio.modificar(FUNCIONARIO, 99L, new CambiosEmpresa("A", "B", "C", null, 0L), "t")));

        assertEquals("EMPRESA_NO_ENCONTRADA", e.getCodigo());
    }

    @Test
    void modificarDevuelveLaVersionNueva() throws Exception {
        OperacionRealizada r = enTx(() -> servicio.modificar(FUNCIONARIO, 1L,
                new CambiosEmpresa("TDE", "TDE S.A.", "Pando", null, 0L), "t"));

        assertEquals(1L, r.empresa().version());
        assertEquals(1L, servicio.detalle(FUNCIONARIO, 1L, "t").version());
    }

    @Test
    void modificarConUnaVersionViejaEs409YNoPisaLoQueCambioOtro() throws Exception {
        // A y B abrieron el formulario con la version 0; A guarda primero la direccion nueva
        enTx(() -> servicio.modificar(FUNCIONARIO, 1L,
                new CambiosEmpresa("Transportes del Este", "TDE S.A.", "Ruta 8 km 29", null, 0L), "t"));

        ConflictoException e = assertThrows(ConflictoException.class, () -> enTx(() -> servicio.modificar(FUNCIONARIO,
                1L, new CambiosEmpresa("Otro nombre", "TDE S.A.", "Pando", null, 0L), "t")));

        assertEquals("EMPRESA_DESACTUALIZADA", e.getCodigo());
        em.clear();
        Empresa empresa = em.find(Empresa.class, 1L);
        assertEquals("Transportes del Este", empresa.getNombrePublico());
        assertEquals("Ruta 8 km 29", empresa.getDireccion());
    }

    @Test
    void modificarSinVersionEs409() {
        ConflictoException e = assertThrows(ConflictoException.class, () -> enTx(
                () -> servicio.modificar(FUNCIONARIO, 1L, new CambiosEmpresa("A", "B", "C", null, null), "t")));

        assertEquals("EMPRESA_DESACTUALIZADA", e.getCodigo());
        em.clear();
        assertEquals("Transportes del Este", em.find(Empresa.class, 1L).getNombrePublico());
    }

    // ===== FA4: asociacion de usuario =====

    @Test
    void asociaUnCiudadanoExistenteComoResponsable() throws Exception {
        OperacionRealizada r = enTx(
                () -> servicio.asociarUsuario(FUNCIONARIO, 2L, new AsociacionUsuario(" " + CIUDADANO + " "), "t"));

        assertEquals("El usuario fue asociado a la empresa.", r.mensaje());
        assertEquals(CIUDADANO, r.empresa().responsable().ci());
        em.clear();
        assertEquals(2L, em.find(Responsable.class, CIUDADANO).getEmpresa().getNroEmpresa());
        assertEquals(CIUDADANO, em.find(Empresa.class, 2L).getResponsable().getCi());
    }

    @Test
    void asociarUnaCedulaNoRegistradaEs404YNoCreaAlCiudadano() {
        RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class,
                () -> enTx(() -> servicio.asociarUsuario(FUNCIONARIO, 2L, new AsociacionUsuario("5555555"), "t")));

        assertEquals("USUARIO_NO_ENCONTRADO", e.getCodigo());
        em.clear();
        assertNull(em.find(Ciudadano.class, "5555555"));
        assertNull(em.find(Empresa.class, 2L).getResponsable());
    }

    @Test
    void repetirLaMismaAsociacionNoCambiaNada() throws Exception {
        OperacionRealizada r = enTx(
                () -> servicio.asociarUsuario(FUNCIONARIO, 1L, new AsociacionUsuario(RESPONSABLE), "t"));

        assertEquals("El usuario ya estaba asociado a la empresa.", r.mensaje());
        assertEquals(RESPONSABLE, r.empresa().responsable().ci());
    }

    @Test
    void noSeAsociaAUnaEmpresaQueYaTieneOtroResponsable() {
        assertConflicto("EMPRESA_CON_RESPONSABLE", 1L, CIUDADANO);
        em.clear();
        assertNull(em.find(Responsable.class, CIUDADANO));
        assertEquals(RESPONSABLE, em.find(Empresa.class, 1L).getResponsable().getCi());
    }

    @Test
    void noSeAsociaAQuienYaEsResponsableDeOtraEmpresa() {
        assertConflicto("RESPONSABLE_DE_OTRA_EMPRESA", 2L, RESPONSABLE);
        em.clear();
        assertNull(em.find(Empresa.class, 2L).getResponsable());
    }

    @Test
    void soloSeAsociaAUnCiudadanoValidado() {
        for (String ci : new String[] { PENDIENTE, RECHAZADO }) {
            assertConflicto("USUARIO_NO_VALIDADO", 2L, ci);
            em.clear();
            assertNull(em.find(Responsable.class, ci));
        }
        assertNull(em.find(Empresa.class, 2L).getResponsable());
    }

    @Test
    void asociarConCedulaInvalidaEs400() {
        for (AsociacionUsuario datos : new AsociacionUsuario[] { new AsociacionUsuario("12a45678"),
                new AsociacionUsuario("12345"), new AsociacionUsuario(null), null }) {
            DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                    () -> enTx(() -> servicio.asociarUsuario(FUNCIONARIO, 2L, datos, "t")));
            assertTrue(e.getErrores().containsKey("ci"));
        }
    }

    @Test
    void asociarAUnaEmpresaInexistenteEs404() {
        for (Long nro : new Long[] { 99L, null }) {
            RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class,
                    () -> enTx(() -> servicio.asociarUsuario(FUNCIONARIO, nro, new AsociacionUsuario(CIUDADANO), "t")));
            assertEquals("EMPRESA_NO_ENCONTRADA", e.getCodigo());
        }
    }

    // Dos funcionarios asocian al mismo ciudadano a empresas distintas a la vez, cada uno en su transaccion:
    // el lock de su fila hace que el segundo espere al primero y responda 409, no un error de PK al commit.
    @Test
    void dosAsociacionesSimultaneasDelMismoCiudadanoDejanUnaYLaOtraEs409() throws Exception {
        em.getTransaction().begin();
        em.persist(new Empresa(3L, "Carga Sur", "Carga Sur S.A.", "Salto", null));
        em.getTransaction().commit();
        em.clear();

        // Primer funcionario: asocia y deja la transaccion abierta (la fila del ciudadano queda bloqueada)
        em.getTransaction().begin();
        servicio.asociarUsuario(FUNCIONARIO, 2L, new AsociacionUsuario(CIUDADANO), "t1");
        em.flush();

        // Segundo funcionario, con su propio EntityManager y servicio, en otro hilo
        EntityManager em2 = emf.createEntityManager();
        GestionEmpresasService servicio2 = servicioCon(em2);
        ExecutorService hilo = Executors.newSingleThreadExecutor();
        Future<Exception> segundo = hilo.submit(() -> {
            em2.getTransaction().begin();
            try {
                servicio2.asociarUsuario(FUNCIONARIO, 3L, new AsociacionUsuario(CIUDADANO), "t2");
                em2.getTransaction().commit();
                return null;
            } catch (Exception e) {
                if (em2.getTransaction().isActive()) {
                    em2.getTransaction().rollback();
                }
                return e;
            }
        });
        try {
            // Mientras el primero no termina, el segundo sigue esperando el lock
            assertThrows(TimeoutException.class, () -> segundo.get(500, TimeUnit.MILLISECONDS));
            em.getTransaction().commit();

            Exception e = segundo.get(10, TimeUnit.SECONDS);
            assertTrue(e instanceof ConflictoException, "se esperaba 409 y fue " + e);
            assertEquals("RESPONSABLE_DE_OTRA_EMPRESA", ((ConflictoException) e).getCodigo());
        } finally {
            hilo.shutdownNow();
            em2.close();
        }
        em.clear();
        assertEquals(2L, em.find(Responsable.class, CIUDADANO).getEmpresa().getNroEmpresa());
        assertNull(em.find(Empresa.class, 3L).getResponsable());
    }

    private GestionEmpresasService servicioCon(EntityManager otro) throws Exception {
        AutorizacionFuncionario autorizacion = new AutorizacionFuncionario();
        FuncionarioDAO funcionarios = new FuncionarioDAO();
        set(funcionarios, "em", otro);
        set(autorizacion, "funcionarios", funcionarios);
        GestionEmpresasService s = new GestionEmpresasService();
        set(s, "autorizacion", autorizacion);
        for (Object[] dao : new Object[][] { { "empresas", new EmpresaDAO() }, { "ciudadanos", new CiudadanoDAO() },
                { "responsables", new ResponsableDAO() } }) {
            set(dao[1], "em", otro);
            set(s, (String) dao[0], dao[1]);
        }
        return s;
    }

    private void assertConflicto(String codigo, Long nroEmpresa, String ci) {
        ConflictoException e = assertThrows(ConflictoException.class,
                () -> enTx(() -> servicio.asociarUsuario(FUNCIONARIO, nroEmpresa, new AsociacionUsuario(ci), "t")));
        assertEquals(codigo, e.getCodigo());
    }

    interface Accion<T> {
        T ejecutar() throws Exception;
    }

    // Emula la transaccion del contenedor: commit si termina bien, rollback si tira excepcion
    private <T> T enTx(Accion<T> accion) throws Exception {
        em.getTransaction().begin();
        try {
            T r = accion.ejecutar();
            em.getTransaction().commit();
            return r;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        }
    }

    private <T> T conEm(T dao) throws Exception {
        set(dao, "em", em);
        return dao;
    }

    // Busca el campo tambien en las superclases (los DAOs con metodos redefinidos en un test son subclases)
    private static void set(Object destino, String campo, Object valor) throws Exception {
        Class<?> c = destino.getClass();
        while (c.getSuperclass() != null && java.util.Arrays.stream(c.getDeclaredFields())
                .noneMatch(f -> f.getName().equals(campo))) {
            c = c.getSuperclass();
        }
        Field f = c.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
