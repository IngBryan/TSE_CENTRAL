package tse.lab.backofficeautoridadesadministradores.service.descargos;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.security.Principal;
import java.util.function.Supplier;
import jakarta.ejb.SessionContext;
import jakarta.persistence.*;
import tse.lab.backofficeautoridadesadministradores.data.ConfiguracionDescargosDAO;
import tse.lab.model.*;
import tse.lab.reglas.ConfiguracionReglas;

class GestionPlazoDescargoServiceTest {
    EntityManagerFactory emf; EntityManager em;
    GestionPlazoDescargoService servicio;
    boolean admin = true;
    @BeforeEach void iniciar() throws Exception {
        emf = Persistence.createEntityManagerFactory("backofficeTestPersistenceUnit"); em = emf.createEntityManager();
        var dao = new ConfiguracionDescargosDAO(); servicio = new GestionPlazoDescargoService();
        set(dao, "em", em); set(servicio, "configuracion", dao);
        SessionContext ctx = (SessionContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SessionContext.class},
                (p, m, a) -> switch(m.getName()) {
                    case "isCallerInRole" -> admin;
                    case "getCallerPrincipal" -> (Principal) () -> "12345678";
                    default -> null;
                });
        set(servicio, "seguridad", ctx);
        tx(() -> { em.persist(ConfiguracionDescargos.inicial());
            em.persist(new RevisionReglas(ConfiguracionReglas.inicial(), "seed"));
            em.persist(new ReglasVigentes(1)); return null; });
    }
    @AfterEach void cerrar() { if (em.getTransaction().isActive()) em.getTransaction().rollback(); em.close(); emf.close(); }
    static void set(Object o, String name, Object v) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, v); }
    <T> T tx(Supplier<T> s) {
        em.getTransaction().begin();
        try { T t = s.get(); em.getTransaction().commit(); return t; }
        catch (RuntimeException e) { em.getTransaction().rollback(); em.clear(); throw e; }
    }
    @Test void guardaElPlazoSinModificarReglasNiSuOutbox() {
        assertEquals(10, tx(servicio::obtener).plazoDescargoDias());
        var c = tx(() -> servicio.actualizar(1, 15, "Ampliación del plazo", "corr"));
        em.clear();
        assertEquals(2, c.revision()); assertEquals("12345678", c.usuario());
        assertEquals(15, tx(servicio::obtener).plazoDescargoDias());
        assertEquals(1, tx(() -> em.find(ReglasVigentes.class, 1L).getRevision()));
        assertEquals(1L, tx(() -> em.createQuery("SELECT COUNT(r) FROM RevisionReglas r", Long.class).getSingleResult()));
        var motor = tx(() -> em.find(RevisionReglas.class, 1L).datos());
        assertEquals(0, motor.getToleranciaCargaPorcentaje().signum());
        assertEquals(0, motor.getToleranciaCargaKg());
        assertEquals(2000, motor.getDistanciaDesvioMetros());
        assertEquals(30, motor.getVentanaDesvioSegundos());
        assertFalse(motor.isPublicada());
    }
    @Test void edicionObsoletaNoSobrescribe() {
        tx(() -> servicio.actualizar(1, 15, "Prueba", "corr"));
        assertEquals(409, assertThrows(PlazoDescargoException.class,
                () -> tx(() -> servicio.actualizar(1, 5, "Obsoleto", "corr"))).getStatus());
        assertEquals(15, tx(servicio::obtener).plazoDescargoDias());
    }
    @Test void requiereAdministradorParaLeerYGuardar() {
        admin = false;
        assertEquals(403, assertThrows(PlazoDescargoException.class, servicio::obtener).getStatus());
        assertEquals(403, assertThrows(PlazoDescargoException.class,
                () -> tx(() -> servicio.actualizar(1, 15, "Prueba", "corr"))).getStatus());
        admin = true; assertEquals(1, tx(servicio::obtener).revision());
    }
    @Test void validaPlazoYFundamentoSinModificarLaConfiguracion() {
        for (int dias : new int[]{-1, 0, 366})
            assertEquals(400, assertThrows(PlazoDescargoException.class,
                    () -> tx(() -> servicio.actualizar(1, dias, "Prueba", "corr"))).getStatus());
        for (String motivo : new String[]{null, " ", "x".repeat(501)})
            assertEquals(400, assertThrows(PlazoDescargoException.class,
                    () -> tx(() -> servicio.actualizar(1, 15, motivo, "corr"))).getStatus());
        assertEquals(1, tx(servicio::obtener).revision());
        assertEquals(10, tx(servicio::obtener).plazoDescargoDias());
    }
}
