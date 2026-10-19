
package tse.lab.backofficeautoridadesadministradores.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tse.lab.model.UsuarioBackoffice;

/**
 * Proporciona las operaciones de persistencia necesarias
 * para la autenticación de usuarios del Backoffice.
 *
 * Permite consultar usuarios independientemente de su
 * subtipo concreto (Administrador o Autoridad).
 */
@Stateless
public class UsuarioBackofficeDAO {

    @PersistenceContext(
        unitName = "backofficeautoridadesadministradoresPersistenceUnit"
    )
    private EntityManager em;

    /**
     * Busca un usuario por su cédula de identidad.
     *
     * @param ci cédula de identidad del usuario
     * @return usuario encontrado o null si no existe
     */
    public UsuarioBackoffice buscarPorCi(String ci) {
        return em.find(UsuarioBackoffice.class, ci);
    }
}

