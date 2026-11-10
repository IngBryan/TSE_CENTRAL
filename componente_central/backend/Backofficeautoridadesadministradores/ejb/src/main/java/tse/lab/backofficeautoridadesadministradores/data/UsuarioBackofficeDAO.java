
package tse.lab.backofficeautoridadesadministradores.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tse.lab.backofficeautoridadesadministradores.service.autenticacion.CuentaBackoffice;
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
     * El rol de un usuario y si tiene pendiente el cambio de contraseña, sin
     * traer la entidad ni el hash de la contraseña: se consulta en cada pedido
     * autenticado.
     *
     * @return la cuenta, o null si no es usuario del Backoffice
     */
    public CuentaBackoffice buscarCuenta(String ci) {
        return em.createQuery("""
                SELECT u.ci,
                    CASE
                        WHEN TYPE(u) = Administrador THEN 'ADMINISTRADOR'
                        WHEN TYPE(u) = Autoridad THEN 'AUTORIDAD'
                    END,
                    u.debeCambiarContrasena
                FROM UsuarioBackoffice u
                WHERE u.ci = :ci
                """, Object[].class)
                .setParameter("ci", ci)
                .getResultStream()
                .findFirst()
                .filter(fila -> fila[1] != null)
                .map(fila -> new CuentaBackoffice((String) fila[0], (String) fila[1], (Boolean) fila[2]))
                .orElse(null);
    }

    /**
     * Reemplaza el hash de la contraseña solo si todavía es el anterior: si
     * otro cambio se confirmó entre la lectura y este update, no lo pisa. La
     * contraseña nueva la eligió el propio usuario: deja de tener pendiente
     * el cambio obligatorio.
     *
     * @return true si se actualizó
     */
    public boolean actualizarContrasena(String ci, String hashAnterior, String hashNuevo) {
        return em.createNativeQuery("""
                UPDATE usuarios_backoffice SET contrasena = ?1, debe_cambiar_contrasena = FALSE
                WHERE ci = ?2 AND contrasena = ?3
                """)
                .setParameter(1, hashNuevo)
                .setParameter(2, ci)
                .setParameter(3, hashAnterior)
                .executeUpdate() > 0;
    }
}

