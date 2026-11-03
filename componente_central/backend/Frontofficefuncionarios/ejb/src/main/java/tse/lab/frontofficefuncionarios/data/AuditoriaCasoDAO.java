package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.log.PistaAuditoria;
import tse.lab.model.RegistroAuditoriaCaso;

// Pista de auditoria de solo agregado: este DAO solo inserta y lee, no tiene update ni delete.
@Stateless
public class AuditoriaCasoDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    // flush: la linea de la pista por consola solo se escribe si la base acepto el registro
    public void registrar(RegistroAuditoriaCaso registro) {
        em.persist(registro);
        em.flush();
        PistaAuditoria.registrar("frontofficefuncionarios", registro);
    }

    public List<RegistroAuditoriaCaso> historialDelCaso(Long idCaso) {
        return em.createQuery("SELECT r FROM RegistroAuditoriaCaso r WHERE r.idCaso = :idCaso"
                + " ORDER BY r.fechaHora, r.id", RegistroAuditoriaCaso.class)
                .setParameter("idCaso", idCaso)
                .getResultList();
    }
}
