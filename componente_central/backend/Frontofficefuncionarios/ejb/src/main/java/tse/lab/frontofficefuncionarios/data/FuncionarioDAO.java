package tse.lab.frontofficefuncionarios.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Funcionario;

@Stateless
public class FuncionarioDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public Funcionario buscarPorCi(String ci) {
        return em.find(Funcionario.class, ci);
    }
}
