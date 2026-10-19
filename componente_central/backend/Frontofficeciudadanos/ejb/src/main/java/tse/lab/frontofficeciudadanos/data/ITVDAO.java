package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.ITV;
import tse.lab.model.Vehiculo;

@Stateless
public class ITVDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public void guardar(ITV itv) {
        em.persist(itv);
    }

    // Historial de inspecciones del vehiculo, la de vencimiento mas lejano primero (la vigente).
    public List<ITV> buscarPorVehiculo(Vehiculo vehiculo) {
        return em.createQuery("SELECT i FROM ITV i WHERE i.vehiculo = :vehiculo ORDER BY i.fechaVencimiento DESC, i.id DESC",
                ITV.class)
                .setParameter("vehiculo", vehiculo)
                .getResultList();
    }
}
