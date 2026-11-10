package tse.lab.frontofficefuncionarios.service.gestioncasos;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService;
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService.NuevoDescargo;
import tse.lab.frontofficefuncionarios.data.AuditoriaCasoDAO;
import tse.lab.frontofficefuncionarios.data.CasoFiscalizacionDAO;
import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.frontofficefuncionarios.data.NoOpTransactions;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.Resolucion;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Ciudadano;
import tse.lab.model.ClaveEvento;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.EventoPesajes;
import tse.lab.model.Funcionario;
import tse.lab.model.Guia;
import tse.lab.model.NodoBalanza;
import tse.lab.model.RegistroAuditoriaCaso;
import tse.lab.model.Responsable;
import tse.lab.model.Vehiculo;

/** CL-R01 a CL-R04: operaciones reales de ambos frontoffices y DAOs sobre H2.
 * Las transacciones son locales; no prueba el contenedor EJB ni el motor de detección. */
class CicloVidaCasosFiscalizacionTest {

    private static final String RESPONSABLE = "54074380";
    private static final String AJENO = "12345678";
    private static final String FUNCIONARIO = "45678901";
    private static final String MATRICULA = "SBC1234";
    private static final String GUIA = "GV-1";
    private static final Instant MEDICION = Instant.parse("2026-11-01T15:00:00Z");
    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");
    private static final String DESCARGO = "La balanza estaba descalibrada.";
    private static final String FUNDAMENTO = "Se revisaron el pesaje y los antecedentes del caso.";

    private EntityManagerFactory emf;
    private EntityManager em;
    private CasosAutomaticosService automaticos;
    private GestionCasosService funcionarios;
    private InfraccionesService ciudadanos;

    @BeforeEach
    void preparar() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficefuncionariosTestPersistenceUnit",
                Map.of("jakarta.persistence.jdbc.url", "jdbc:h2:mem:ciclo_casos;DB_CLOSE_DELAY=-1"));
        em = emf.createEntityManager();
        var casos = conEm(new CasoFiscalizacionDAO());
        var auditoria = conEm(NoOpTransactions.wired(new AuditoriaCasoDAO()));
        automaticos = new CasosAutomaticosService();
        set(automaticos, "casos", casos);
        set(automaticos, "auditoria", auditoria);
        var autorizacion = new AutorizacionFuncionario();
        set(autorizacion, "funcionarios", conEm(new FuncionarioDAO()));
        funcionarios = new GestionCasosService();
        set(funcionarios, "autorizacion", autorizacion);
        set(funcionarios, "casos", casos);
        set(funcionarios, "auditoria", auditoria);
        ciudadanos = conReloj(MEDICION);

        enTx(() -> {
            var empresa = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
            var otra = new Empresa(2L, "Transportes del Sur", "TDS S.A.", "Colonia", null);
            em.persist(empresa);
            em.persist(otra);
            responsable(RESPONSABLE, empresa);
            responsable(AJENO, otra);
            em.persist(new Funcionario(FUNCIONARIO, "funcionario@mtop.gub.uy"));
            var vehiculo = new Vehiculo(MATRICULA, "Scania", "R450", 9000, 26000, empresa);
            em.persist(vehiculo);
            var guia = new Guia(GUIA, "Agropecuario", 18000, LocalDate.of(2026, 11, 1),
                    "Montevideo", "Salto", empresa);
            guia.setVehiculo(vehiculo);
            em.persist(guia);
            var balanza = new NodoBalanza("Pando", "Ruta 8 km 32", 1L);
            em.persist(balanza);
            em.persist(new EventoPesajes(1001L, MATRICULA, MEDICION, 38500, balanza));
            return null;
        });
    }

    @AfterEach
    void cerrar() {
        if (em != null) em.close();
        if (emf != null) emf.close();
    }

    @ParameterizedTest(name = "{0}: {1} → {3}")
    @CsvSource({
            "CL-R01, DESCARGO, CONFIRMAR, CONFIRMADA",
            "CL-R02, DESCARGO, DESESTIMAR, DESESTIMADA",
            "CL-R03, VENCIMIENTO, CONFIRMAR, CONFIRMADA",
            "CL-R04, VENCIMIENTO, DESESTIMAR, DESESTIMADA"
    })
    void cicloCompletoConservaEvidenciaYAuditaCadaTransicion(String escenario, String entrada,
            String decision, EstadoCaso estadoFinal) throws Exception {
        // Crear: no se preparan estados, descargos ni resoluciones directamente.
        var creado = enTx(() -> automaticos.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, GUIA,
                new EventoFiscalizado(MATRICULA, MEDICION, OrigenEvento.PESAJE, "creacion")));
        Long id = creado.getId();
        Instant deteccion = caso(id, EstadoCaso.DETECTADA).getFechaDeteccion();
        assertNotNull(deteccion);
        assertEquals("DETECTADA", creado.getEstado());
        assertTrue(ciudadanos.listar(RESPONSABLE, "consulta").isEmpty());
        var creacion = historial(id);
        assertEquals(1, creacion.size());
        assertAsiento(creacion.getFirst(), id, RegistroAuditoriaCaso.SISTEMA, AccionAuditoriaCaso.CREACION,
                null, EstadoCaso.DETECTADA, "creacion");

        // Notificar y observar la infracción desde la empresa correspondiente.
        var notificado = enTx(() -> automaticos.notificarCaso(id, "notificacion"));
        assertEquals(id, notificado.getId());
        caso(id, EstadoCaso.NOTIFICADA);
        var notificacion = historial(id);
        assertEquals(2, notificacion.size());
        assertEquals(creacion, notificacion.subList(0, 1));
        assertAsiento(notificacion.get(1), id, RegistroAuditoriaCaso.SISTEMA, AccionAuditoriaCaso.CAMBIO_ESTADO,
                EstadoCaso.DETECTADA, EstadoCaso.NOTIFICADA, "notificacion");
        LocalDate ultimoDia = LocalDate.ofInstant(notificacion.get(1).fecha(), URUGUAY).plusDays(10);
        ciudadanos = conReloj(ultimoDia.atTime(12, 0).atZone(URUGUAY).toInstant());
        var lista = ciudadanos.listar(RESPONSABLE, "consulta");
        assertEquals(List.of(id), lista.stream().map(InfraccionesService.InfraccionResumen::id).toList());
        assertEquals(ultimoDia.toString(), lista.getFirst().venceDescargo());
        assertTrue(lista.getFirst().admiteDescargo());
        assertEquals(List.of(id), ciudadanos.notificaciones(RESPONSABLE, "consulta").notificaciones()
                .stream().map(InfraccionesService.Notificacion::id).toList());
        assertTrue(ciudadanos.listar(AJENO, "consulta").isEmpty());
        assertThrows(tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException.class,
                () -> enTx(() -> ciudadanos.detalle(AJENO, id, "consulta-ajena")));

        boolean conDescargo = entrada.equals("DESCARGO");
        if (conDescargo) {
            var presentado = enTx(() -> ciudadanos.presentarDescargo(RESPONSABLE, id,
                    new NuevoDescargo(DESCARGO), "entrada-descargo"));
            assertEquals("EN_DESCARGO", presentado.infraccion().estado());
            assertEquals(DESCARGO, presentado.descargo().descripcion());
            var visto = funcionarios.descargo(FUNCIONARIO, id, "consulta");
            assertEquals(DESCARGO, visto.descripcion());
            assertEquals(RESPONSABLE, visto.ciPresentante());
        } else {
            ciudadanos = conReloj(ultimoDia.plusDays(1).atStartOfDay(URUGUAY).toInstant());
            assertEquals(List.of(id), ciudadanos.casosConPlazoVencido());
            assertTrue(enTx(() -> ciudadanos.vencerPlazo(id, "entrada-descargo")));
            assertTrue(ciudadanos.casosConPlazoVencido().isEmpty());
        }
        var enDescargo = caso(id, EstadoCaso.EN_DESCARGO);
        if (conDescargo) {
            assertEquals(DESCARGO, enDescargo.getDescargo().getDescripcion());
            assertEquals(RESPONSABLE, enDescargo.getDescargo().getCiPresentante());
            assertNotNull(enDescargo.getDescargo().getFechaPresentacion());
        } else {
            assertNull(enDescargo.getDescargo());
        }
        var antesDeResolver = historial(id);
        assertEquals(3, antesDeResolver.size());
        assertEquals(notificacion, antesDeResolver.subList(0, 2));
        assertAsiento(antesDeResolver.get(2), id, conDescargo ? RESPONSABLE : RegistroAuditoriaCaso.SISTEMA,
                AccionAuditoriaCaso.CAMBIO_ESTADO, EstadoCaso.NOTIFICADA, EstadoCaso.EN_DESCARGO,
                "entrada-descargo");
        assertFalse(antesDeResolver.get(2).fundamento().isBlank());
        assertTrue(funcionarios.detalle(FUNCIONARIO, id, "consulta").puedeResolverse());

        // Resolver y releer: misma evidencia, resolución única y pista completa.
        var resuelto = enTx(() -> funcionarios.resolver(FUNCIONARIO, id,
                new Resolucion(decision, FUNDAMENTO), "resolucion"));
        assertEquals(estadoFinal.name(), resuelto.caso().estado());
        assertFalse(resuelto.caso().puedeResolverse());
        assertEquals(4, resuelto.caso().historial().size());
        var guardado = caso(id, estadoFinal);
        assertEquals(deteccion, guardado.getFechaDeteccion());
        var fundamento = guardado.getFundamentoResolucion();
        assertNotNull(fundamento.getId());
        assertEquals(FUNDAMENTO, fundamento.getDescripcion());
        assertEquals(FUNCIONARIO, fundamento.getFuncionario().getCi());
        assertEquals(id, fundamento.getCasoFiscalizacion().getId());
        assertNotNull(fundamento.getFechaHora());
        assertEquals(conDescargo ? 1L : 0L, cantidad("Descargo"));
        assertEquals(1L, cantidad("FundamentoResolucion"));
        if (conDescargo) assertEquals(DESCARGO, guardado.getDescargo().getDescripcion());
        else assertNull(guardado.getDescargo());

        var pista = historial(id);
        assertEquals(4, pista.size());
        assertEquals(antesDeResolver, pista.subList(0, 3));
        assertAsiento(pista.get(3), id, FUNCIONARIO, AccionAuditoriaCaso.RESOLUCION,
                EstadoCaso.EN_DESCARGO, estadoFinal, "resolucion");
        assertEquals(FUNDAMENTO, pista.get(3).fundamento());
        var vistoPorEmpresa = enTx(() -> ciudadanos.detalle(RESPONSABLE, id, "consulta-final"));
        assertEquals(estadoFinal.name(), vistoPorEmpresa.infraccion().estado());
        assertEquals(estadoFinal.name(), vistoPorEmpresa.resolucion().decision());
        assertEquals(FUNDAMENTO, vistoPorEmpresa.resolucion().fundamento());
        assertFalse(vistoPorEmpresa.infraccion().admiteDescargo());
        assertEquals(conDescargo, vistoPorEmpresa.descargo() != null);
        var vistoPorFuncionario = funcionarios.detalle(FUNCIONARIO, id, "consulta-final");
        assertEquals(decision, vistoPorFuncionario.resolucion().decision());
        assertEquals(FUNDAMENTO, vistoPorFuncionario.resolucion().fundamento());
        assertEquals(FUNCIONARIO, vistoPorFuncionario.resolucion().ciFuncionario());
        assertEquals(1, funcionarios.evidencia(FUNCIONARIO, id, "consulta-final").pesajes().size());
        assertEquals(38500, vistoPorEmpresa.pesajes().getFirst().pesoKg());
        assertEquals(pista, historial(id));
    }

    private CasoFiscalizacion caso(Long id, EstadoCaso estado) {
        em.clear();
        var caso = em.find(CasoFiscalizacion.class, id);
        assertNotNull(caso);
        assertEquals(estado, caso.getEstado());
        assertEquals(MATRICULA, caso.getVehiculo().getMatricula());
        assertEquals(1L, caso.getVehiculo().getEmpresa().getNroEmpresa());
        assertEquals(GUIA, caso.getGuia().getId());
        assertEquals(1L, cantidad("CasoFiscalizacion"));
        assertEquals(1, caso.getEventosPesajes().size());
        var pesaje = caso.getEventosPesajes().getFirst();
        assertEquals(new ClaveEvento(MATRICULA, MEDICION), pesaje.getClave());
        assertEquals(1001L, pesaje.getIdExterno());
        assertEquals(38500, pesaje.getPeso());
        return caso;
    }

    private record Asiento(Long id, Long caso, String usuario, AccionAuditoriaCaso accion,
            EstadoCaso anterior, EstadoCaso nuevo, String fundamento, Instant fecha, String correlacion) { }

    private List<Asiento> historial(Long id) {
        return em.createQuery("SELECT r FROM RegistroAuditoriaCaso r WHERE r.idCaso = :id ORDER BY r.id",
                RegistroAuditoriaCaso.class).setParameter("id", id).getResultList().stream()
                .map(r -> new Asiento(r.getId(), r.getIdCaso(), r.getUsuario(), r.getAccion(),
                        r.getEstadoAnterior(), r.getEstadoNuevo(), r.getFundamento(), r.getFechaHora(),
                        r.getCorrelationId())).toList();
    }

    private static void assertAsiento(Asiento asiento, Long caso, String usuario, AccionAuditoriaCaso accion,
            EstadoCaso anterior, EstadoCaso nuevo, String correlacion) {
        assertNotNull(asiento.id());
        assertEquals(caso, asiento.caso());
        assertEquals(usuario, asiento.usuario());
        assertEquals(accion, asiento.accion());
        assertEquals(anterior, asiento.anterior());
        assertEquals(nuevo, asiento.nuevo());
        assertEquals(correlacion, asiento.correlacion());
        assertNotNull(asiento.fecha());
    }

    private long cantidad(String entidad) {
        return em.createQuery("SELECT COUNT(e) FROM " + entidad + " e", Long.class).getSingleResult();
    }

    private void responsable(String ci, Empresa empresa) {
        var ciudadano = new Ciudadano(ci, ci + "@prueba.uy", "sub-" + ci);
        ciudadano.setEstado(EstadoValidacion.VALIDADO);
        em.persist(ciudadano);
        em.persist(new Responsable(ciudadano, empresa));
    }

    private InfraccionesService conReloj(Instant ahora) throws Exception {
        var constructor = InfraccionesService.class.getDeclaredConstructor(int.class, Clock.class);
        constructor.setAccessible(true);
        var servicio = constructor.newInstance(10, Clock.fixed(ahora, URUGUAY));
        var autorizacion = new AutorizacionResponsable();
        set(autorizacion, "responsables", conEm(new ResponsableDAO()));
        set(autorizacion, "perfiles", conEm(new CiudadanoDAO()));
        set(servicio, "autorizacion", autorizacion);
        var casos = conEm(new tse.lab.frontofficeciudadanos.data.CasoFiscalizacionDAO());
        // Solo el callback de logging posterior al commit usa este doble; las filas de auditoría son reales.
        set(casos, "transacciones", Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { TransactionSynchronizationRegistry.class }, (proxy, metodo, args) -> null));
        set(servicio, "casos", casos);
        return servicio;
    }

    private <T> T enTx(Callable<T> accion) throws Exception {
        em.getTransaction().begin();
        try {
            T resultado = accion.call();
            em.getTransaction().commit();
            return resultado;
        } catch (Exception | AssertionError e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.clear();
        }
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
