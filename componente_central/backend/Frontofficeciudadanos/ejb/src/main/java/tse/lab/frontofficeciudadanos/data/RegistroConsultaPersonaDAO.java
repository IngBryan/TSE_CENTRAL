package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.RegistroConsultaPersona;

// Auditoria de solo agregado (Ley 18.331): no expone modificar ni borrar.
@Stateless
public class RegistroConsultaPersonaDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public void registrar(RegistroConsultaPersona registro) {
        em.persist(registro);
    }

    public List<RegistroConsultaPersona> buscarPorCiConsultada(String ci) {
        return em.createQuery(
                "SELECT r FROM RegistroConsultaPersona r WHERE r.ciConsultada = :ci ORDER BY r.fechaHora",
                RegistroConsultaPersona.class)
                .setParameter("ci", ci)
                .getResultList();
    }
}
