package tse.lab.frontofficeciudadanos.service.autenticacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ResultadoValidacion;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ValidacionPdiService;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.Responsable;

// CU-01 pasos 6 a 9 contra H2: ingreso con gub.uy e ingreso de testing (sin gub.uy). La PDI es falsa.
class IngresoServiceTest {

    private static final String RESPONSABLE = "54074380";

    private EntityManagerFactory emf;
    private EntityManager em;
    private IngresoService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        RolesService roles = new RolesService();
        set(roles, "responsables", conEm(new ResponsableDAO()));
        set(roles, "choferes", conEm(new ChoferDAO()));
        servicio = new IngresoService();
        set(servicio, "perfiles", conEm(new CiudadanoDAO()));
        set(servicio, "roles", roles);
        set(servicio, "validacionPdi", new PdiQueValida());

        // Responsable con su cuenta gub.uy ya vinculada (sub-real)
        Empresa empresa = new Empresa(1L, "Transportes del Este", "Transportes del Este S.A.", "Ruta 8 km 29", null);
        Ciudadano perfil = new Ciudadano(RESPONSABLE, "r@prueba.uy", "sub-real");
        perfil.setEstado(EstadoValidacion.VALIDADO);
        em.getTransaction().begin();
        em.persist(empresa);
        em.persist(perfil);
        em.persist(new Responsable(perfil, empresa));
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void ingresoDeTestingDevuelveLosRolesSinTocarLaCuentaGubUy() {
        em.getTransaction().begin();
        ResultadoIngreso r = servicio.ingresarParaTesting(RESPONSABLE, "t");
        em.getTransaction().commit();

        assertTrue(r.sesionPermitida());
        assertEquals(List.of(RolPersona.Rol.RESPONSABLE), r.roles().stream().map(RolPersona::rol).toList());
        em.clear();
        Ciudadano guardado = em.find(Ciudadano.class, RESPONSABLE);
        assertEquals("sub-real", guardado.getSubGubUy());
        assertEquals("r@prueba.uy", guardado.getEmail());
    }

    @Test
    void ingresoDeTestingConCedulaNuevaCreaElPerfilSinCuentaGubUy() {
        em.getTransaction().begin();
        ResultadoIngreso r = servicio.ingresarParaTesting("48060995", "t");
        em.getTransaction().commit();

        assertTrue(r.nuevo());
        assertTrue(r.roles().isEmpty());
        em.clear();
        assertNull(em.find(Ciudadano.class, "48060995").getSubGubUy());
    }

    @Test
    void ingresoConGubUyActualizaElSubYElEmail() {
        em.getTransaction().begin();
        servicio.ingresarConGubUy(RESPONSABLE, "nuevo@prueba.uy", "sub-otro", "t");
        em.getTransaction().commit();

        em.clear();
        Ciudadano guardado = em.find(Ciudadano.class, RESPONSABLE);
        assertEquals("sub-otro", guardado.getSubGubUy());
        assertEquals("nuevo@prueba.uy", guardado.getEmail());
    }

    // PDI falsa: valida a cualquiera
    private static class PdiQueValida extends ValidacionPdiService {
        @Override
        public ResultadoValidacion validarSiCorresponde(Ciudadano perfil, String correlationId) {
            perfil.setEstado(EstadoValidacion.VALIDADO);
            return ResultadoValidacion.VALIDADO;
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
