package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.frontofficeciudadanos.model.Empresa;

@Stateless
public class EmpresaDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Empresa buscarPorId(Long nroEmpresa) {
        return em.find(Empresa.class, nroEmpresa);
    }

    public void guardar(Empresa empresa) {
        em.persist(empresa);
    }

    public List<Empresa> listarTodas() {
        return em.createQuery("SELECT e FROM Empresa e", Empresa.class)
                .getResultList();
    }

}