package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import tse.lab.model.ConfiguracionDescargos;

@Stateless
public class PlazoDescargoDAO {
    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;
    public int dias() {
        ConfiguracionDescargos c = em.find(ConfiguracionDescargos.class, 1L);
        if (c == null) throw new IllegalStateException("Falta inicializar la configuración de descargos");
        return c.getPlazoDias();
    }
}
