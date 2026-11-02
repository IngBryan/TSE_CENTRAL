package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Empresa;
import tse.lab.model.Vehiculo;

@Stateless
public class VehiculoDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public Vehiculo buscarPorMatricula(String matricula) {
        return em.find(Vehiculo.class, matricula);
    }

    // Todos los vehiculos de la empresa, tambien los dados de baja (la fiscalizacion necesita su historia)
    public List<Vehiculo> buscarPorEmpresa(Empresa empresa) {
        return em.createQuery("SELECT v FROM Vehiculo v WHERE v.empresa = :empresa ORDER BY v.matricula",
                Vehiculo.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }
}
