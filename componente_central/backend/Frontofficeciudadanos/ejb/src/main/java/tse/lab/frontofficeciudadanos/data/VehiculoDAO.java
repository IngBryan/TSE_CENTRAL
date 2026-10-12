package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.Empresa;
import tse.lab.model.Vehiculo;

@Stateless
public class VehiculoDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Vehiculo buscarPorMatricula(String matricula) {
        return em.find(Vehiculo.class, matricula);
    }

    public void guardar(Vehiculo vehiculo) {
        em.persist(vehiculo);
    }

    public Vehiculo actualizar(Vehiculo vehiculo) {
        return em.merge(vehiculo);
    }

    public void eliminar(Vehiculo vehiculo) {
        em.remove(em.contains(vehiculo) ? vehiculo : em.merge(vehiculo));
    }

    public List<Vehiculo> buscarPorEmpresa(Empresa empresa) {
        return em.createQuery(
                "SELECT v FROM Vehiculo v WHERE v.empresa = :empresa", Vehiculo.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }
}