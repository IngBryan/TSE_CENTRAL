
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

    /**
     * El rol de un usuario, sin traer la entidad ni el hash de la contraseña:
     * se consulta en cada pedido autenticado.
     *
     * @return "ADMINISTRADOR", "AUTORIDAD" o null si no es usuario del Backoffice
     */
    public String buscarRol(String ci) {
        return em.createQuery("""
                SELECT CASE
                    WHEN TYPE(u) = Administrador THEN 'ADMINISTRADOR'
                    WHEN TYPE(u) = Autoridad THEN 'AUTORIDAD'
                END
                FROM UsuarioBackoffice u
                WHERE u.ci = :ci
                """, String.class)
                .setParameter("ci", ci)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Reemplaza el hash de la contraseña solo si todavía es el anterior: si
     * otro cambio se confirmó entre la lectura y este update, no lo pisa.
     *
     * @return true si se actualizó
     */
    public boolean actualizarContrasena(String ci, String hashAnterior, String hashNuevo) {
        return em.createNativeQuery(
                "UPDATE usuarios_backoffice SET contrasena = ?1 WHERE ci = ?2 AND contrasena = ?3")
                .setParameter(1, hashNuevo)
                .setParameter(2, ci)
                .setParameter(3, hashAnterior)
                .executeUpdate() > 0;
    }
}

