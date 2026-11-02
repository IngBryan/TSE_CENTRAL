package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Empresa;
import tse.lab.model.ITV;
import tse.lab.model.Vehiculo;

@Stateless
public class ITVDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    // Historial de inspecciones del vehiculo, la de vencimiento mas lejano primero (la vigente)
    public List<ITV> buscarPorVehiculo(Vehiculo vehiculo) {
        return em.createQuery(
                "SELECT i FROM ITV i WHERE i.vehiculo = :vehiculo ORDER BY i.fechaVencimiento DESC, i.id DESC",
                ITV.class)
                .setParameter("vehiculo", vehiculo)
                .getResultList();
    }

    // Las ITV de todos los vehiculos de la empresa en una sola consulta, con el mismo orden por vehiculo
    public List<ITV> buscarPorEmpresa(Empresa empresa) {
        return em.createQuery("SELECT i FROM ITV i WHERE i.vehiculo.empresa = :empresa"
                + " ORDER BY i.fechaVencimiento DESC, i.id DESC", ITV.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }

    // Todas las ITV, con el mismo orden por vehiculo
    public List<ITV> listar() {
        return em.createQuery("SELECT i FROM ITV i ORDER BY i.fechaVencimiento DESC, i.id DESC", ITV.class)
                .getResultList();
    }
}
