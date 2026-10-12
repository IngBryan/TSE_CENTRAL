package tse.lab.frontofficeciudadanos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.EmpresaDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.EstadoValidacion;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.service.PerfilEmpresaService.CambiosEmpresa;
import tse.lab.frontofficeciudadanos.service.PerfilEmpresaService.DatosEmpresa;

// Perfil de la Empresa contra H2 con los DAOs reales.
class PerfilEmpresaServiceTest {

    private static final String RESPONSABLE = "54074380";

    private EntityManagerFactory emf;
    private EntityManager em;
    private PerfilEmpresaService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        AutorizacionResponsable autorizacion = new AutorizacionResponsable();
        set(autorizacion, "responsables", new ResponsablesFalsos().con(RESPONSABLE, 1L));
        set(autorizacion, "empresas", conEm(new EmpresaDAO()));
        set(autorizacion, "perfiles", conEm(new PerfilCiudadanoDAO()));
        servicio = new PerfilEmpresaService();
        set(servicio, "autorizacion", autorizacion);

        // Empresa NO habilitada: el responsable igual puede ver y editar sus datos
        Empresa empresa = new Empresa(1L, "Transportes del Este", "Transportes del Este S.A.", "Ruta 8 km 29");
        PerfilCiudadano perfil = new PerfilCiudadano(RESPONSABLE, "r@prueba.uy", "sub-r");
        perfil.setEstado(EstadoValidacion.VALIDADO);
        em.getTransaction().begin();
        em.persist(empresa);
        em.persist(perfil);
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void elResponsableConsultaLosDatosDeSuEmpresa() throws Exception {
        assertEquals(new DatosEmpresa(1L, "Transportes del Este", "Transportes del Este S.A.", "Ruta 8 km 29", false),
                servicio.obtener(RESPONSABLE, "t"));
    }

    @Test
    void actualizaNombreRazonSocialYDireccionRecortandoEspacios() throws Exception {
        em.getTransaction().begin();
        DatosEmpresa r = servicio.actualizar(RESPONSABLE,
                new CambiosEmpresa("  TDE   Cargas ", "TDE Cargas S.R.L.", " Av. Italia 1234, Montevideo "), "t");
        em.getTransaction().commit();
        em.clear();

        assertEquals(new DatosEmpresa(1L, "TDE Cargas", "TDE Cargas S.R.L.", "Av. Italia 1234, Montevideo", false), r);
        Empresa guardada = em.find(Empresa.class, 1L);
        assertEquals("TDE Cargas", guardada.getNombrePublico());
        assertEquals("Av. Italia 1234, Montevideo", guardada.getDireccion());
    }

    @Test
    void camposObligatoriosYLargoMaximo() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> servicio.actualizar(RESPONSABLE,
                new CambiosEmpresa(" ", "x".repeat(PerfilEmpresaService.MAX_NOMBRE + 1), null), "t"));
        assertEquals(3, e.getErrores().size());
        assertTrue(e.getErrores().get("nombrePublico").contains("obligatorio"));
        assertTrue(e.getErrores().get("razonSocial").contains("100"));
        assertEquals("La dirección principal es obligatoria.", e.getErrores().get("direccion"));
        assertThrows(DatosInvalidosException.class, () -> servicio.actualizar(RESPONSABLE, null, "t"));
    }

    @Test
    void soloElResponsableValidado() throws Exception {
        assertThrows(NoAutorizadoException.class, () -> servicio.obtener("11111111", "t"));
        assertThrows(NoAutorizadoException.class,
                () -> servicio.actualizar(null, new CambiosEmpresa("a", "b", "c"), "t"));

        em.getTransaction().begin();
        em.find(PerfilCiudadano.class, RESPONSABLE).setEstado(EstadoValidacion.PENDIENTE_VALIDACION);
        em.getTransaction().commit();
        assertThrows(NoAutorizadoException.class, () -> servicio.obtener(RESPONSABLE, "t"));
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
