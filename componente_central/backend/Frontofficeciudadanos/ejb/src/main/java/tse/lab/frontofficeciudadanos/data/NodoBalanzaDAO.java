package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.NodoBalanza;

@Stateless
public class NodoBalanzaDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    // idExterno: id de la balanza en la API de balanzas. null si no esta dada de alta.
    public NodoBalanza buscarPorIdExterno(long idExterno) {
        return em.createQuery("SELECT b FROM NodoBalanza b WHERE b.idExterno = :idExterno", NodoBalanza.class)
                .setParameter("idExterno", idExterno)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
