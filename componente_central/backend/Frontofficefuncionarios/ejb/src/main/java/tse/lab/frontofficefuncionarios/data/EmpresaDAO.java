package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Empresa;

@Stateless
public class EmpresaDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public Empresa buscarPorId(Long nroEmpresa) {
        return em.find(Empresa.class, nroEmpresa);
    }

    // Bloquea la fila hasta el fin de la transaccion, para que dos funcionarios no asocien a la vez un
    // responsable a la misma empresa. Igual que en CasoFiscalizacionDAO: lock y refresh en vez de
    // find(..., PESSIMISTIC_WRITE), que en Hibernate 7 falla con el @OneToOne(mappedBy) del responsable.
    public Empresa buscarPorIdParaActualizar(Long nroEmpresa) {
        Empresa empresa = em.find(Empresa.class, nroEmpresa);
        if (empresa != null) {
            em.lock(empresa, LockModeType.PESSIMISTIC_WRITE);
            em.refresh(empresa);
        }
        return empresa;
    }

    // Todas las empresas con su responsable en la misma consulta (entity graph, no una consulta por empresa)
    public List<Empresa> listar() {
        EntityGraph<Empresa> grafo = em.createEntityGraph(Empresa.class);
        grafo.addSubgraph("responsable").addAttributeNodes("ciudadano");
        return em.createQuery("SELECT e FROM Empresa e ORDER BY e.nroEmpresa", Empresa.class)
                .setHint("jakarta.persistence.fetchgraph", grafo)
                .getResultList();
    }

    public void guardar(Empresa empresa) {
        em.persist(empresa);
    }
}
