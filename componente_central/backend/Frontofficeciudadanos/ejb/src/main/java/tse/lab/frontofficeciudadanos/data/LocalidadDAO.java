package tse.lab.frontofficeciudadanos.data;

import java.util.List;
import java.text.Normalizer;
import java.util.Locale;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Localidad;

/** Catalogo de localidades para los formularios de guias y el servicio de rutas. */
@Stateless
public class LocalidadDAO {
    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Localidad buscarPorId(Long id) {
        return id == null ? null : em.find(Localidad.class, id);
    }

    /** Compatibilidad con guias antiguas: solo acepta un nombre si identifica una unica localidad. */
    public Localidad buscarUnicaPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        String buscado = normalizar(nombre);
        List<Localidad> coincidencias = listar().stream()
                .filter(l -> normalizar(l.getNombre()).equals(buscado)).limit(2).toList();
        return coincidencias.size() == 1 ? coincidencias.get(0) : null;
    }

    private static String normalizar(String nombre) {
        return Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    public List<Localidad> listar() {
        return em.createQuery("SELECT l FROM Localidad l ORDER BY l.departamento, l.nombre", Localidad.class)
                .getResultList();
    }
}
