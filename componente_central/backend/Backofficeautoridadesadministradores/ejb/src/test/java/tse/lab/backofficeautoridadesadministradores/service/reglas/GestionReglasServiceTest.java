package tse.lab.backofficeautoridadesadministradores.service.reglas;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.function.Supplier;
import jakarta.ejb.SessionContext;
import jakarta.persistence.*;
import tse.lab.backofficeautoridadesadministradores.data.ReglasDAO;
import tse.lab.model.*;
import tse.lab.reglas.ConfiguracionReglas;

class GestionReglasServiceTest {
    EntityManagerFactory emf; EntityManager em;
    GestionReglasService service; ReglasDAO dao;
    boolean admin=true;
    @BeforeEach void iniciar() throws Exception {
        emf=Persistence.createEntityManagerFactory("backofficeTestPersistenceUnit"); em=emf.createEntityManager();
        dao=new ReglasDAO(); service=new GestionReglasService();
        set(dao,"em",em); set(service,"reglas",dao);
        SessionContext ctx=(SessionContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SessionContext.class},
                (p,m,a)->switch(m.getName()) {
                    case "isCallerInRole" -> admin;
                    case "getCallerPrincipal" -> (Principal)()->"12345678";
                    default -> null;
                });
        set(service,"seguridad",ctx);
        tx(()->{ em.persist(new RevisionReglas(ConfiguracionReglas.inicial(),"seed")); em.persist(new ReglasVigentes(1)); return null; });
    }
    @AfterEach void cerrar() { if(em.getTransaction().isActive()) em.getTransaction().rollback(); em.close(); emf.close(); }
    static void set(Object o,String name,Object v) throws Exception { Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o,v); }
    <T>T tx(Supplier<T> s) {
        em.getTransaction().begin();
        try { T t=s.get(); em.getTransaction().commit(); return t; }
        catch(RuntimeException e) { em.getTransaction().rollback(); em.clear(); throw e; }
    }
    ConfiguracionReglas cambiar(long base) { return service.actualizar(base,new BigDecimal("2"),500,1500,60,"Ajuste de prueba","corr"); }
    @Test void cambioEHistorialYOutboxSonAtomicos() {
        ConfiguracionReglas c=tx(()->cambiar(1));
        em.clear();
        assertEquals(2,c.getRevision()); assertEquals("12345678",c.getUsuario());
        assertEquals(2,tx(service::obtener).getRevision());
        assertEquals(2,tx(service::historial).size());
        assertTrue(tx(()->dao.revision(2).pendiente()));
        assertEquals(0,tx(()->dao.revision(1).datos().getToleranciaCargaPorcentaje()).signum());
    }
    @Test void edicionObsoletaNoSobrescribeYNoCreaOtraRevision() {
        tx(()->cambiar(1));
        ReglasException e=assertThrows(ReglasException.class,()->tx(()->cambiar(1)));
        assertEquals(409,e.getStatus()); assertEquals(2,tx(service::historial).size());
    }
    @Test void autorizacionSeAplicaEnElServicio() {
        admin=false;
        assertEquals(403,assertThrows(ReglasException.class,service::obtener).getStatus());
        assertEquals(403,assertThrows(ReglasException.class,()->tx(()->cambiar(1))).getStatus());
        admin=true; assertEquals(1,tx(service::obtener).getRevision());
    }
    @Test void valoresInvalidosNoAvanzanLaRevision() {
        assertEquals(400,assertThrows(ReglasException.class,()->tx(()->service.actualizar(1,new BigDecimal("21"),0,2000,30,"x","c"))).getStatus());
        assertEquals(1,tx(service::obtener).getRevision());
        assertEquals(1,tx(service::historial).size());
    }
    @Test void publicarElOutboxNoRepiteRevisionesYaConfirmadas() {
        assertEquals(1, tx(()->dao.siguientePendiente().datos().getRevision()));
        tx(()->{ dao.revision(1).publicada(); return null; });
        em.clear();
        assertNull(tx(dao::siguientePendiente));
        tx(()->cambiar(1));
        assertEquals(2, tx(()->dao.siguientePendiente().datos().getRevision()));
    }
    @Test void laConsultaInternaDevuelveLaVigenteYLaRevisionHistorica() throws Exception {
        var consulta=new ConsultaReglasService(); set(consulta,"reglas",dao);
        tx(()->cambiar(1));
        assertEquals(2,tx(consulta::obtenerVigente).getRevision());
        assertEquals(1,tx(()->consulta.obtenerRevision(1)).getRevision());
        assertEquals(404,assertThrows(ReglasException.class,()->tx(()->consulta.obtenerRevision(99))).getStatus());
    }
    @Test void sinConfiguracionInicialSeInformaIndisponibilidad() {
        tx(()->{ em.createQuery("DELETE FROM ReglasVigentes").executeUpdate(); return null; });
        em.clear();
        assertEquals(503,assertThrows(ReglasException.class,()->tx(service::obtener)).getStatus());
    }
    @Test void fundamentoInvalidoNoModificaElHistorial() {
        for (String fundamento : new String[]{null," ","x".repeat(501)})
            assertEquals(400,assertThrows(ReglasException.class,()->tx(()->service.actualizar(
                    1,BigDecimal.ZERO,0,2000,30,fundamento,"c"))).getStatus());
        assertEquals(1,tx(service::historial).size());
        admin=false;
        assertEquals(403,assertThrows(ReglasException.class,service::historial).getStatus());
    }
    @Test void sinContextoDeSeguridadNoSeAutoriza() throws Exception {
        set(service,"seguridad",null);
        assertEquals(403,assertThrows(ReglasException.class,service::obtener).getStatus());
    }

}
