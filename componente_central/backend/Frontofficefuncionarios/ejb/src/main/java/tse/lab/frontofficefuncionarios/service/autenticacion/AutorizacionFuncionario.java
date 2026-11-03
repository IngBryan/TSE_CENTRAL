package tse.lab.frontofficefuncionarios.service.autenticacion;

import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.frontofficefuncionarios.service.LogFuncionarios;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.model.Funcionario;

// Control de acceso del frontoffice de funcionarios (capa de servicios, no la UI): la cedula de la
// sesion del front tiene que estar registrada como funcionario del MTOP. Si no, la operacion se rechaza
// antes de leer o modificar cualquier caso.
@Stateless
public class AutorizacionFuncionario {

    private static final Logger LOG = Logger.getLogger(AutorizacionFuncionario.class.getName());

    @EJB
    private FuncionarioDAO funcionarios;

    public Funcionario funcionario(String ci, String correlationId) throws NoAutorizadoException {
        Funcionario funcionario = ci == null || ci.isBlank() ? null : funcionarios.buscarPorCi(ci.trim());
        if (funcionario == null) {
            LOG.warning(() -> LogFuncionarios.evento("funcionario.no_autorizado").correlationId(correlationId)
                    .usuario(ci).campo("detalle", "\"no es funcionario\"").toString());
            throw new NoAutorizadoException("no es funcionario del MTOP");
        }
        return funcionario;
    }
}
