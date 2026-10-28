package tse.lab.frontofficefuncionarios.service.autenticacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.adaptadorpdi.ClientePdi;
import tse.lab.adaptadorpdi.DatosPersonaPdi;
import tse.lab.adaptadorpdi.PdiNoDisponibleException;
import tse.lab.frontofficefuncionarios.service.autenticacion.IngresoFuncionarioService.Resultado;
import tse.lab.frontofficefuncionarios.service.autenticacion.IngresoFuncionarioService.ResultadoIngreso;
import tse.lab.model.Funcionario;

// Ingreso de funcionarios contra H2 con el DAO real. La PDI es falsa.
class IngresoFuncionarioServiceTest {

    private static final String FUNCIONARIO = "45678901";
    private static final String NO_FUNCIONARIO = "54074380";

    private EntityManagerFactory emf;
    private EntityManager em;
    private IngresoFuncionarioService servicio;
    private PdiFalsa pdi;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficefuncionariosTestPersistenceUnit");
        em = emf.createEntityManager();
        FuncionarioDAO dao = new FuncionarioDAO();
        set(dao, "em", em);
        pdi = new PdiFalsa();
        servicio = new IngresoFuncionarioService();
        set(servicio, "funcionarios", dao);
        set(servicio, "clientePdi", pdi);

        em.getTransaction().begin();
        em.persist(new Funcionario(FUNCIONARIO, "viejo@mtop.gub.uy"));
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void funcionarioMayorEntraConElNombreDeLaPdiYGuardaElEmailDeGubUy() {
        pdi.persona = new DatosPersonaPdi("Ana", "Perez", "Ana Maria", "Perez Gomez", LocalDate.of(1990, 5, 1));

        em.getTransaction().begin();
        ResultadoIngreso r = servicio.ingresarConGubUy(FUNCIONARIO, "ana@mtop.gub.uy", "t");
        em.getTransaction().commit();

        assertEquals(Resultado.VALIDADO, r.resultado());
        assertTrue(r.sesionPermitida());
        assertEquals("Ana Maria", r.nombres());
        assertEquals("Perez Gomez", r.apellidos());
        em.clear();
        assertEquals("ana@mtop.gub.uy", em.find(Funcionario.class, FUNCIONARIO).getEmail());
    }

    @Test
    void quienNoEsFuncionarioNoEntraNiSeConsultaLaPdi() {
        ResultadoIngreso r = servicio.ingresarConGubUy(NO_FUNCIONARIO, "x@prueba.uy", "t");

        assertEquals(Resultado.NO_ES_FUNCIONARIO, r.resultado());
        assertFalse(r.sesionPermitida());
        assertEquals(0, pdi.consultas);
    }

    @Test
    void siDnicNoLoEncuentraNoEntra() {
        pdi.persona = null;

        ResultadoIngreso r = servicio.ingresarParaTesting(FUNCIONARIO, "t");

        assertEquals(Resultado.NO_EXISTE, r.resultado());
        assertFalse(r.sesionPermitida());
    }

    @Test
    void menorDeEdadNoEntra() {
        pdi.persona = new DatosPersonaPdi("Leo", "Diaz", "Leo", "Diaz", LocalDate.now().minusYears(17));

        ResultadoIngreso r = servicio.ingresarParaTesting(FUNCIONARIO, "t");

        assertEquals(Resultado.MENOR_DE_EDAD, r.resultado());
        assertFalse(r.sesionPermitida());
    }

    @Test
    void conLaPdiCaidaEntraSinNombre() {
        pdi.caida = true;

        ResultadoIngreso r = servicio.ingresarParaTesting(FUNCIONARIO, "t");

        assertEquals(Resultado.NO_DISPONIBLE, r.resultado());
        assertTrue(r.sesionPermitida());
        assertNull(r.nombres());
        assertEquals("viejo@mtop.gub.uy", r.email());
    }

    static class PdiFalsa extends ClientePdi {
        DatosPersonaPdi persona;
        boolean caida;
        int consultas;

        @Override
        public Optional<DatosPersonaPdi> consultarPersona(String cedula, String correlationId)
                throws PdiNoDisponibleException {
            consultas++;
            if (caida) {
                throw new PdiNoDisponibleException("caida");
            }
            return Optional.ofNullable(persona);
        }
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
