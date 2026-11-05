package tse.lab.frontofficeciudadanos.service.infraccionesydescargos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.CasoFiscalizacionDAO;
import tse.lab.frontofficeciudadanos.data.NoOpTransactions;
import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService.InfraccionDetalle;
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService.InfraccionResumen;
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService.Notificacion;
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService.Notificaciones;
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService.NuevoDescargo;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Ciudadano;
import tse.lab.model.Descargo;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.RegistroAuditoriaCaso;
import tse.lab.model.Responsable;
import tse.lab.model.Vehiculo;

// Gestion de Infracciones contra H2 con los DAOs reales: que ve la empresa y las reglas del descargo.
class InfraccionesServiceTest {

    private static final String RESPONSABLE = "54074380";

    private EntityManagerFactory emf;
    private EntityManager em;
    private InfraccionesService servicio;
    private Long notificado;
    private Long detectado;
    private Long deOtraEmpresa;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        servicio = conPlazo(10);

        Empresa este = new Empresa(1L, "Transportes del Este", "Transportes del Este S.A.", "Ruta 8 km 29", null);
        Empresa otra = new Empresa(2L, "Logistica del Litoral", "Logistica del Litoral S.A.", "Paysandu", null);
        Ciudadano perfil = new Ciudadano(RESPONSABLE, "r@prueba.uy", "sub");
        perfil.setEstado(EstadoValidacion.VALIDADO);
        Vehiculo propio = new Vehiculo("SAB5678", "Scania", "R 450", 9000, 26000, este);
        Vehiculo ajeno = new Vehiculo("SCL2001", "Volvo", "FH", 9000, 26000, otra);

        em.getTransaction().begin();
        em.persist(este);
        em.persist(otra);
        em.persist(perfil);
        em.persist(new Responsable(perfil, este));
        em.persist(propio);
        em.persist(ajeno);
        notificado = caso("Desvio de ruta", "DESVIO_RUTA", propio, true);
        detectado = caso("Sin guia", "VIAJE_SIN_GUIA", propio, false);
        deOtraEmpresa = caso("Exceso", "EXCESO_CARGA", ajeno, true);
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void laEmpresaVeSoloSusCasosNotificadosConElPlazoDeDescargo() throws Exception {
        List<InfraccionResumen> lista = servicio.listar(RESPONSABLE, "t");

        assertEquals(List.of(notificado), lista.stream().map(InfraccionResumen::id).toList());
        InfraccionResumen r = lista.get(0);
        assertTrue(r.nueva());
        assertTrue(r.admiteDescargo());
        assertNotNull(r.venceDescargo());
    }

    @Test
    void lasNotificacionesSonLosCasosNotificadosDeLaEmpresaYSeMarcanVistas() throws Exception {
        Notificaciones n = servicio.notificaciones(RESPONSABLE, "t");
        // Ni el caso DETECTADA ni el de la otra empresa
        assertEquals(1, n.noVistas());
        assertEquals(List.of(notificado), n.notificaciones().stream().map(Notificacion::id).toList());
        assertFalse(n.notificaciones().get(0).vista());
        assertTrue(n.notificaciones().get(0).admiteDescargo());

        em.getTransaction().begin();
        n = servicio.marcarVista(RESPONSABLE, notificado, "t");
        em.getTransaction().commit();
        assertEquals(0, n.noVistas());
        assertTrue(n.notificaciones().get(0).vista());
        em.clear();
        assertTrue(em.find(CasoFiscalizacion.class, notificado).isNotificacionVista());
    }

    @Test
    void marcarTodasLasNotificacionesNoTocaLasDeOtraEmpresa() throws Exception {
        em.getTransaction().begin();
        Notificaciones n = servicio.marcarTodasVistas(RESPONSABLE, "t");
        em.getTransaction().commit();

        assertEquals(0, n.noVistas());
        em.clear();
        assertTrue(em.find(CasoFiscalizacion.class, notificado).isNotificacionVista());
        assertFalse(em.find(CasoFiscalizacion.class, deOtraEmpresa).isNotificacionVista());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.marcarVista(RESPONSABLE, deOtraEmpresa, "t"));
    }

    @Test
    void presentarElDescargoPasaElCasoAEnDescargoYQuedaEnLaAuditoria() throws Exception {
        em.getTransaction().begin();
        InfraccionDetalle d = servicio.presentarDescargo(RESPONSABLE, notificado,
                new NuevoDescargo("  La ruta estaba cortada por obras  "), "corr-1");
        em.getTransaction().commit();

        assertEquals("EN_DESCARGO", d.infraccion().estado());
        assertFalse(d.infraccion().admiteDescargo());
        assertEquals("La ruta estaba cortada por obras", d.descargo().descripcion());
        em.clear();
        CasoFiscalizacion caso = em.find(CasoFiscalizacion.class, notificado);
        assertEquals(EstadoCaso.EN_DESCARGO, caso.getEstado());
        Descargo guardado = caso.getDescargo();
        assertEquals(RESPONSABLE, guardado.getCiPresentante());
        assertNotNull(guardado.getFechaPresentacion());
        assertEquals(Descargo.PRESENTADO, guardado.getEstado());
        RegistroAuditoriaCaso ultimo = em.createQuery("SELECT r FROM RegistroAuditoriaCaso r WHERE r.idCaso = :id"
                + " ORDER BY r.id DESC", RegistroAuditoriaCaso.class).setParameter("id", notificado)
                .setMaxResults(1).getSingleResult();
        assertEquals(RESPONSABLE, ultimo.getUsuario());
        assertEquals(AccionAuditoriaCaso.CAMBIO_ESTADO, ultimo.getAccion());
        assertEquals(EstadoCaso.EN_DESCARGO, ultimo.getEstadoNuevo());
        assertEquals("corr-1", ultimo.getCorrelationId());
    }

    @Test
    void noSePuedePresentarDosVeces() throws Exception {
        em.getTransaction().begin();
        servicio.presentarDescargo(RESPONSABLE, notificado, new NuevoDescargo("Primero"), "t");
        em.getTransaction().commit();

        em.getTransaction().begin();
        ConflictoException e = assertThrows(ConflictoException.class,
                () -> servicio.presentarDescargo(RESPONSABLE, notificado, new NuevoDescargo("Segundo"), "t"));
        em.getTransaction().rollback();
        assertEquals("DESCARGO_YA_PRESENTADO", e.getCodigo());
    }

    @Test
    void elDiaDeLaNotificacionNoCuentaYElUltimoDiaEsDeUruguay() throws Exception {
        // 1/11 a las 23:30 en Uruguay es el 2/11 en UTC: el plazo se cuenta desde el 1/11
        notificadoEl(Instant.parse("2026-11-02T02:30:00Z"));

        InfraccionResumen r = servicio.listar(RESPONSABLE, "t").get(0);

        assertEquals("2026-11-11", r.venceDescargo());
    }

    @Test
    void elUltimoDiaSePuedePresentarHastaLas2359() throws Exception {
        notificadoEl(Instant.parse("2026-11-01T15:00:00Z"));
        servicio = conPlazo(10, enUruguay("2026-11-11T23:59:59"));

        assertTrue(servicio.listar(RESPONSABLE, "t").get(0).admiteDescargo());
        em.getTransaction().begin();
        InfraccionDetalle d = servicio.presentarDescargo(RESPONSABLE, notificado, new NuevoDescargo("A tiempo"), "t");
        em.getTransaction().commit();
        assertEquals("EN_DESCARGO", d.infraccion().estado());
    }

    @Test
    void conElPlazoVencidoSeRechazaYElCasoNoCambia() throws Exception {
        notificadoEl(Instant.parse("2026-11-01T15:00:00Z"));
        servicio = conPlazo(10, enUruguay("2026-11-12T00:00:00"));

        assertFalse(servicio.listar(RESPONSABLE, "t").get(0).admiteDescargo());
        em.getTransaction().begin();
        ConflictoException e = assertThrows(ConflictoException.class,
                () -> servicio.presentarDescargo(RESPONSABLE, notificado, new NuevoDescargo("Tarde"), "t"));
        em.getTransaction().rollback();

        assertEquals("PLAZO_VENCIDO", e.getCodigo());
        em.clear();
        assertEquals(EstadoCaso.NOTIFICADA, em.find(CasoFiscalizacion.class, notificado).getEstado());
    }

    @Test
    void alVencerElPlazoElCasoPasaAEnDescargoUnaSolaVezYQuedaEnLaAuditoria() throws Exception {
        notificadoEl(Instant.parse("2026-11-01T15:00:00Z"));
        servicio = conPlazo(10, enUruguay("2026-11-12T00:00:00"));

        assertEquals(List.of(notificado), servicio.casosConPlazoVencido());
        em.getTransaction().begin();
        assertTrue(servicio.vencerPlazo(notificado, "venc-1"));
        em.getTransaction().commit();

        em.clear();
        CasoFiscalizacion caso = em.find(CasoFiscalizacion.class, notificado);
        assertEquals(EstadoCaso.EN_DESCARGO, caso.getEstado());
        assertNull(caso.getDescargo());
        RegistroAuditoriaCaso ultimo = ultimoRegistro(notificado);
        assertEquals(RegistroAuditoriaCaso.SISTEMA, ultimo.getUsuario());
        assertEquals(EstadoCaso.NOTIFICADA, ultimo.getEstadoAnterior());
        assertEquals(EstadoCaso.EN_DESCARGO, ultimo.getEstadoNuevo());
        assertEquals("venc-1", ultimo.getCorrelationId());

        // Otra corrida no lo vuelve a tocar ni duplica la auditoria
        assertTrue(servicio.casosConPlazoVencido().isEmpty());
        em.getTransaction().begin();
        assertFalse(servicio.vencerPlazo(notificado, "venc-2"));
        em.getTransaction().commit();
        assertEquals(ultimo.getId(), ultimoRegistro(notificado).getId());

        // Y la empresa ya no puede presentar: se le dice que vencio el plazo
        em.getTransaction().begin();
        ConflictoException e = assertThrows(ConflictoException.class,
                () -> servicio.presentarDescargo(RESPONSABLE, notificado, new NuevoDescargo("Tarde"), "t"));
        em.getTransaction().rollback();
        assertEquals("PLAZO_VENCIDO", e.getCodigo());
    }

    @Test
    void elUltimoDiaDelPlazoElCasoNoSeVence() throws Exception {
        notificadoEl(Instant.parse("2026-11-01T15:00:00Z"));
        servicio = conPlazo(10, enUruguay("2026-11-11T23:59:59"));

        assertTrue(servicio.casosConPlazoVencido().isEmpty());
        em.getTransaction().begin();
        assertFalse(servicio.vencerPlazo(notificado, "t"));
        em.getTransaction().commit();
        em.clear();
        assertEquals(EstadoCaso.NOTIFICADA, em.find(CasoFiscalizacion.class, notificado).getEstado());
    }

    @Test
    void unCasoConDescargoNoSeVence() throws Exception {
        notificadoEl(Instant.parse("2026-11-01T15:00:00Z"));
        servicio = conPlazo(10, enUruguay("2026-11-11T12:00:00"));
        em.getTransaction().begin();
        servicio.presentarDescargo(RESPONSABLE, notificado, new NuevoDescargo("A tiempo"), "t");
        em.getTransaction().commit();
        em.clear();
        Long registroDescargo = ultimoRegistro(notificado).getId();

        servicio = conPlazo(10, enUruguay("2026-11-12T00:05:00"));
        assertTrue(servicio.casosConPlazoVencido().isEmpty());
        em.getTransaction().begin();
        assertFalse(servicio.vencerPlazo(notificado, "t"));
        em.getTransaction().commit();
        assertEquals(registroDescargo, ultimoRegistro(notificado).getId());
    }

    @Test
    void elDescargoVacioOMuyLargoEsInvalido() {
        em.getTransaction().begin();
        assertThrows(DatosInvalidosException.class,
                () -> servicio.presentarDescargo(RESPONSABLE, notificado, new NuevoDescargo("   "), "t"));
        assertThrows(DatosInvalidosException.class, () -> servicio.presentarDescargo(RESPONSABLE, notificado,
                new NuevoDescargo("x".repeat(Descargo.MAX_DESCRIPCION + 1)), "t"));
        em.getTransaction().rollback();
    }

    @Test
    void losCasosDeOtraEmpresaOSinNotificarNoExisten() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.detalle(RESPONSABLE, deOtraEmpresa, "t"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.detalle(RESPONSABLE, detectado, "t"));
        em.getTransaction().begin();
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.presentarDescargo(RESPONSABLE,
                deOtraEmpresa, new NuevoDescargo("No es mio"), "t"));
        em.getTransaction().rollback();
    }

    @Test
    void abrirElDetalleMarcaLaNotificacionComoVista() throws Exception {
        em.getTransaction().begin();
        InfraccionDetalle d = servicio.detalle(RESPONSABLE, notificado, "t");
        em.getTransaction().commit();

        assertEquals("SAB5678", d.vehiculo().matricula());
        assertEquals(35000, d.vehiculo().pesoMaximoKg());
        em.clear();
        assertTrue(em.find(CasoFiscalizacion.class, notificado).isNotificacionVista());
        assertFalse(servicio.listar(RESPONSABLE, "t").get(0).nueva());
    }

    // Caso creado por la fiscalizacion automatica; notificado = ademas se le notifico a la empresa
    private Long caso(String descripcion, String tipo, Vehiculo vehiculo, boolean notificado) {
        CasoFiscalizacion caso = new CasoFiscalizacion(descripcion, tipo, vehiculo, null);
        if (notificado) {
            caso.cambiarEstado(EstadoCaso.NOTIFICADA);
        }
        em.persist(caso);
        if (notificado) {
            em.persist(new RegistroAuditoriaCaso(caso.getId(), RegistroAuditoriaCaso.SISTEMA,
                    AccionAuditoriaCaso.CAMBIO_ESTADO, EstadoCaso.DETECTADA, EstadoCaso.NOTIFICADA, null, "t"));
        }
        return caso.getId();
    }

    // Cambia la fecha del pasaje a NOTIFICADA del caso notificado (la auditoria la pone al crearse) y la de
    // deteccion, que no puede ser posterior
    private void notificadoEl(Instant fecha) {
        em.getTransaction().begin();
        em.createQuery("UPDATE RegistroAuditoriaCaso r SET r.fechaHora = :fecha WHERE r.idCaso = :id")
                .setParameter("fecha", fecha).setParameter("id", notificado).executeUpdate();
        em.createQuery("UPDATE CasoFiscalizacion c SET c.fechaDeteccion = :fecha WHERE c.id = :id")
                .setParameter("fecha", fecha).setParameter("id", notificado).executeUpdate();
        em.getTransaction().commit();
        em.clear();
    }

    private RegistroAuditoriaCaso ultimoRegistro(Long idCaso) {
        return em.createQuery("SELECT r FROM RegistroAuditoriaCaso r WHERE r.idCaso = :id ORDER BY r.id DESC",
                RegistroAuditoriaCaso.class).setParameter("id", idCaso).setMaxResults(1).getSingleResult();
    }

    private static Clock enUruguay(String fechaHora) {
        return Clock.fixed(LocalDateTime.parse(fechaHora).atZone(InfraccionesService.URUGUAY).toInstant(),
                InfraccionesService.URUGUAY);
    }

    private InfraccionesService conPlazo(int dias) throws Exception {
        return conPlazo(dias, Clock.systemUTC());
    }

    private InfraccionesService conPlazo(int dias, Clock reloj) throws Exception {
        AutorizacionResponsable autorizacion = new AutorizacionResponsable();
        set(autorizacion, "responsables", conEm(new ResponsableDAO()));
        set(autorizacion, "perfiles", conEm(new CiudadanoDAO()));
        InfraccionesService s = new InfraccionesService(dias, reloj);
        set(s, "autorizacion", autorizacion);
        set(s, "casos", conEm(NoOpTransactions.wired(new CasoFiscalizacionDAO())));
        return s;
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
