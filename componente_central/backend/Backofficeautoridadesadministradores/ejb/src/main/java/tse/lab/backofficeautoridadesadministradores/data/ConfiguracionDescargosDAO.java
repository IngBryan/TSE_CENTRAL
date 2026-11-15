package tse.lab.backofficeautoridadesadministradores.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import tse.lab.model.ConfiguracionDescargos;
import tse.lab.backofficeautoridadesadministradores.service.descargos.PlazoDescargoException;

@Stateless
public class ConfiguracionDescargosDAO {
    @PersistenceContext(unitName = "backofficeautoridadesadministradoresPersistenceUnit")
    private EntityManager em;

    public ConfiguracionDescargos obtener(boolean bloquear) {
        ConfiguracionDescargos c = em.find(ConfiguracionDescargos.class, 1L);
        if (c == null) throw new PlazoDescargoException(503, "Falta inicializar la configuración de descargos");
        // Relee después del bloqueo para detectar otra edición incluso entre réplicas.
        em.refresh(c, bloquear ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        return c;
    }
}
