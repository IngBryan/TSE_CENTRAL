package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.frontofficeciudadanos.model.Guia;
import tse.lab.frontofficeciudadanos.model.Empresa;

@Stateless
public class GuiaDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Guia buscarPorId(String id) {
        return em.find(Guia.class, id);
    }

    public void guardar(Guia guia) {
        em.persist(guia);
    }

    public Guia actualizar(Guia guia) {
        return em.merge(guia);
    }

    public List<Guia> listarTodas() {
        return em.createQuery("SELECT g FROM Guia g", Guia.class)
                .getResultList();
    }

    public List<Guia> buscarPorEmpresa(Empresa empresa) {
        return em.createQuery(
                "SELECT g FROM Guia g WHERE g.empresa = :empresa", Guia.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }

    public List<Guia> buscarSinVehiculoAsignado() {
        return em.createQuery(
                "SELECT g FROM Guia g WHERE g.vehiculo IS NULL", Guia.class)
                .getResultList();
    }
}