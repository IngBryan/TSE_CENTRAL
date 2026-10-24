package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Ciudadano;
import tse.lab.model.EstadoValidacion;

@Stateless
public class CiudadanoDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    // Solo lectura: los ciudadanos los crea y valida Frontofficeciudadanos
    public Ciudadano buscarPorCi(String ci) {
        return em.find(Ciudadano.class, ci);
    }

    // Bloquea la fila hasta el fin de la transaccion, para que dos funcionarios no lo asocien a la vez como
    // responsable de empresas distintas (el lock de la empresa no alcanza: son filas distintas)
    public Ciudadano buscarPorCiParaActualizar(String ci) {
        return em.find(Ciudadano.class, ci, LockModeType.PESSIMISTIC_WRITE);
    }

    // Validados, cuya cedula empieza con prefijo y que no son responsables de ninguna empresa
    public List<Ciudadano> disponiblesComoResponsable(String prefijo, int maximo) {
        return em.createQuery("SELECT c FROM Ciudadano c WHERE c.estado = :validado AND c.ci LIKE :prefijo"
                + " AND NOT EXISTS (SELECT r FROM Responsable r WHERE r.ci = c.ci) ORDER BY c.ci", Ciudadano.class)
                .setParameter("validado", EstadoValidacion.VALIDADO)
                .setParameter("prefijo", prefijo + "%")
                .setMaxResults(maximo)
                .getResultList();
    }
}
