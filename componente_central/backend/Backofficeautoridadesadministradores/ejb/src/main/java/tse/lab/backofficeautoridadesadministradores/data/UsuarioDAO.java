package tse.lab.backofficeautoridadesadministradores.data;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.RolUsuario;
import tse.lab.model.Ciudadano;
import tse.lab.model.Funcionario;

/**
 * Consultas para el listado paginado de usuarios del sistema.
 *
 * Primero se pagina sobre las cédulas y después se traen los datos solo de
 * esa página. De los roles y de los usuarios del Backoffice se trae
 * únicamente la cédula: el hash de la contraseña no sale de la base.
 */
@Stateless
public class UsuarioDAO {

    // UNION (no UNION ALL): una cédula con varios roles cuenta una sola vez. Un
    // ciudadano sin rol no entra.
    // usuarios_backoffice es la tabla base de administradores y autoridades.
    private static final String CEDULAS_USUARIOS = """
            SELECT ci FROM funcionarios WHERE activo = TRUE
            UNION SELECT ci FROM responsables
            UNION SELECT ci FROM choferes
            UNION SELECT ci FROM usuarios_backoffice
            """;

    // Con filtro de rol se pagina solo sobre su tabla. Un chofer de varias empresas
    // tiene varias filas.
    private static final Map<RolUsuario, String> CEDULAS_POR_ROL = new EnumMap<>(Map.of(
            RolUsuario.FUNCIONARIO, "SELECT ci FROM funcionarios WHERE activo = TRUE",
            RolUsuario.RESPONSABLE, "SELECT ci FROM responsables",
            RolUsuario.CHOFER, "SELECT DISTINCT ci FROM choferes",
            RolUsuario.ADMINISTRADOR, "SELECT ci FROM administradores",
            RolUsuario.AUTORIDAD, "SELECT ci FROM autoridades"));

    @PersistenceContext(unitName = "backofficeautoridadesadministradoresPersistenceUnit")
    private EntityManager em;

    /**
     * @param rol       solo los usuarios con este rol; null para todos
     * @param prefijoCi solo las cédulas que empiezan así (solo dígitos); null para
     *                  todas
     */
    public long contarUsuarios(RolUsuario rol, String prefijoCi) {
        return ((Number) consulta("COUNT(*)", rol, prefijoCi, "")
                .getSingleResult()).longValue();
    }

    /**
     * Una página de cédulas, ordenadas, con los mismos filtros que contarUsuarios.
     */
    public List<String> listarCedulas(RolUsuario rol, String prefijoCi, int desde, int cantidad) {
        return consulta("ci", rol, prefijoCi, " ORDER BY ci")
                .setFirstResult(desde)
                .setMaxResults(cantidad)
                .getResultList()
                .stream()
                .map(Object::toString)
                .toList();
    }

    // El SQL solo se arma con constantes: la cédula va siempre como parámetro
    private Query consulta(String columnas, RolUsuario rol, String prefijoCi, String orden) {
        String origen = rol == null ? CEDULAS_USUARIOS : CEDULAS_POR_ROL.get(rol);
        String filtro = prefijoCi == null ? "" : " WHERE ci LIKE ?1";

        Query consulta = em.createNativeQuery(
                "SELECT " + columnas + " FROM (" + origen + ") usuarios" + filtro + orden);

        if (prefijoCi != null) {
            consulta.setParameter(1, prefijoCi + "%");
        }

        return consulta;
    }

    public List<Ciudadano> buscarCiudadanos(List<String> cedulas) {
        return em.createQuery(
                "SELECT c FROM Ciudadano c WHERE c.ci IN :cedulas",
                Ciudadano.class)
                .setParameter("cedulas", cedulas)
                .getResultList();
    }

    public List<Funcionario> buscarFuncionarios(List<String> cedulas) {
        return em.createQuery(
                "SELECT f FROM Funcionario f WHERE f.ci IN :cedulas AND f.activo = TRUE",
                Funcionario.class)
                .setParameter("cedulas", cedulas)
                .getResultList();
    }

    public List<String> filtrarResponsables(List<String> cedulas) {
        return filtrar("SELECT r.ci FROM Responsable r WHERE r.ci IN :cedulas", cedulas);
    }

    // Una persona puede ser chofer de varias empresas
    public List<String> filtrarChoferes(List<String> cedulas) {
        return filtrar("SELECT DISTINCT c.ciudadano.ci FROM Chofer c WHERE c.ciudadano.ci IN :cedulas", cedulas);
    }

    public List<String> filtrarAdministradores(List<String> cedulas) {
        return filtrar("SELECT a.ci FROM Administrador a WHERE a.ci IN :cedulas", cedulas);
    }

    public List<String> filtrarAutoridades(List<String> cedulas) {
        return filtrar("SELECT a.ci FROM Autoridad a WHERE a.ci IN :cedulas", cedulas);
    }

    // Cuenta en vez de buscar la entidad: no trae el hash de la contraseña
    public boolean existeUsuarioBackoffice(String ci) {
        return existe("SELECT COUNT(u) FROM UsuarioBackoffice u WHERE u.ci = :ci", ci);
    }

    /**
     * @return ADMINISTRADOR, AUTORIDAD o null si la cédula no es usuario del
     *         backoffice. No trae el hash de la contraseña.
     */
    public RolUsuario rolBackoffice(String ci) {
        if (existe("SELECT COUNT(a) FROM Administrador a WHERE a.ci = :ci", ci)) {
            return RolUsuario.ADMINISTRADOR;
        }
        if (existe("SELECT COUNT(a) FROM Autoridad a WHERE a.ci = :ci", ci)) {
            return RolUsuario.AUTORIDAD;
        }
        return null;
    }

    // Activo o inactivo; null si nunca fue funcionario
    public Funcionario buscarFuncionario(String ci) {
        return em.find(Funcionario.class, ci);
    }

    // Administrador, Autoridad o Funcionario
    public void guardar(Object usuario) {
        em.persist(usuario);
        em.flush();
    }

    // Tablas propias de cada subtipo (herencia JOINED): la fila base, con la contrasena, esta en usuarios_backoffice
    private static final Map<RolUsuario, String> TABLA_BACKOFFICE = new EnumMap<>(Map.of(
            RolUsuario.ADMINISTRADOR, "administradores",
            RolUsuario.AUTORIDAD, "autoridades"));

    /**
     * Pasa un usuario del backoffice de administrador a autoridad o al revés,
     * conservando su contraseña. JPA no permite cambiarle el tipo a una
     * entidad: se mueve la fila entre las tablas de cada subtipo.
     */
    public void cambiarRolBackoffice(String ci, RolUsuario anterior, RolUsuario nuevo) {
        ejecutar("DELETE FROM " + TABLA_BACKOFFICE.get(anterior) + " WHERE ci = ?1", ci);
        ejecutar("INSERT INTO " + TABLA_BACKOFFICE.get(nuevo) + " (ci) VALUES (?1)", ci);
    }

    // Quita al administrador o autoridad, con su contrasena
    public void eliminarUsuarioBackoffice(String ci, RolUsuario rol) {
        ejecutar("DELETE FROM " + TABLA_BACKOFFICE.get(rol) + " WHERE ci = ?1", ci);
        ejecutar("DELETE FROM usuarios_backoffice WHERE ci = ?1", ci);
    }

    private void ejecutar(String sql, String ci) {
        em.createNativeQuery(sql).setParameter(1, ci).executeUpdate();
    }

    private boolean existe(String consulta, String ci) {
        return em.createQuery(consulta, Long.class)
                .setParameter("ci", ci)
                .getSingleResult() > 0;
    }

    private List<String> filtrar(String consulta, List<String> cedulas) {
        return em.createQuery(consulta, String.class)
                .setParameter("cedulas", cedulas)
                .getResultList();
    }
}
