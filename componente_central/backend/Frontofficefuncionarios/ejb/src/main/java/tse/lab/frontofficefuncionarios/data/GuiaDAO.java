package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;

// Solo lectura: las guias las crean y asignan los responsables (Frontofficeciudadanos) y las mueve el
// chofer desde la app movil
@Stateless
public class GuiaDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public Guia buscarPorId(String id) {
        return em.find(Guia.class, id);
    }

    // Una pagina del listado de viajes en alguno de los estados dados, del mas nuevo al mas viejo. Los filtros
    // nulos no se aplican. Empresa, vehiculo y chofer (con su ciudadano) se traen en la misma consulta con un
    // entity graph, igual que la bandeja de casos; son relaciones a-uno, asi que el limite se aplica en el SQL.
    public List<Guia> listar(List<EstadoGuia> estados, String matricula, Long nroEmpresa, int desde, int cantidad) {
        EntityGraph<Guia> grafo = em.createEntityGraph(Guia.class);
        grafo.addAttributeNodes("empresa", "vehiculo");
        grafo.addSubgraph("chofer").addAttributeNodes("ciudadano");
        return consulta("SELECT g", Guia.class, estados, matricula, nroEmpresa, " ORDER BY g.fecha DESC, g.id DESC")
                .setHint("jakarta.persistence.fetchgraph", grafo)
                .setFirstResult(desde)
                .setMaxResults(cantidad)
                .getResultList();
    }

    public long contar(List<EstadoGuia> estados, String matricula, Long nroEmpresa) {
        return consulta("SELECT COUNT(g)", Long.class, estados, matricula, nroEmpresa, "").getSingleResult();
    }

    // Las condiciones opcionales se agregan solo si el filtro vino: con "(:matricula IS NULL OR ...)"
    // PostgreSQL no puede deducir el tipo de un parametro nulo
    private <T> TypedQuery<T> consulta(String select, Class<T> tipo, List<EstadoGuia> estados, String matricula,
            Long nroEmpresa, String orden) {
        StringBuilder jpql = new StringBuilder(select).append(" FROM Guia g WHERE g.estado IN :estados");
        if (matricula != null) {
            jpql.append(" AND g.vehiculo.matricula = :matricula");
        }
        if (nroEmpresa != null) {
            jpql.append(" AND g.empresa.nroEmpresa = :nroEmpresa");
        }
        TypedQuery<T> q = em.createQuery(jpql.append(orden).toString(), tipo).setParameter("estados", estados);
        if (matricula != null) {
            q.setParameter("matricula", matricula);
        }
        if (nroEmpresa != null) {
            q.setParameter("nroEmpresa", nroEmpresa);
        }
        return q;
    }
}
