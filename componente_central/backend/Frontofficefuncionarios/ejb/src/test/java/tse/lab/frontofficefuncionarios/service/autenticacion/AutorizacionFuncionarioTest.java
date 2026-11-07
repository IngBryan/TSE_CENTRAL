package tse.lab.frontofficefuncionarios.service.autenticacion;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.model.Funcionario;

class AutorizacionFuncionarioTest {

    private final Funcionario activo = new Funcionario("45678901", "ana@mtop.gub.uy");
    private final Funcionario inactivo = new Funcionario("45678902", "beto@mtop.gub.uy");
    private AutorizacionFuncionario autorizacion;

    @BeforeEach
    void setUp() throws Exception {
        inactivo.setActivo(false);
        FuncionarioDAO dao = new FuncionarioDAO() {
            @Override
            public Funcionario buscarPorCi(String ci) {
                return ci.equals(activo.getCi()) ? activo : ci.equals(inactivo.getCi()) ? inactivo : null;
            }
        };
        autorizacion = new AutorizacionFuncionario();
        Field campo = AutorizacionFuncionario.class.getDeclaredField("funcionarios");
        campo.setAccessible(true);
        campo.set(autorizacion, dao);
    }

    @Test
    void autorizaAUnFuncionarioActivo() throws Exception {
        assertSame(activo, autorizacion.funcionario(" 45678901 ", "t"));
    }

    @Test
    void rechazaAUnFuncionarioInactivo() {
        assertThrows(NoAutorizadoException.class, () -> autorizacion.funcionario("45678902", "t"));
    }

    @Test
    void rechazaAQuienNoEsFuncionario() {
        assertThrows(NoAutorizadoException.class, () -> autorizacion.funcionario("11111111", "t"));
        assertThrows(NoAutorizadoException.class, () -> autorizacion.funcionario(" ", "t"));
        assertThrows(NoAutorizadoException.class, () -> autorizacion.funcionario(null, "t"));
    }
}
