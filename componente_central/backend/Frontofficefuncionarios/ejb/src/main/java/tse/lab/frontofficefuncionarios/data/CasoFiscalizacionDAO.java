package tse.lab.frontofficefuncionarios.data;

import java.time.LocalDate;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.ClaveEvento;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EventoPesajes;
import tse.lab.model.FundamentoResolucion;
import tse.lab.model.Guia;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Vehiculo;

@Stateless
public class CasoFiscalizacionDAO {

    private static final String BANDEJA = "SELECT c FROM CasoFiscalizacion c";
    private static final String ORDEN = " ORDER BY c.fechaDeteccion DESC, c.id DESC";

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public CasoFiscalizacion buscarPorId(Long id) {
        return em.find(CasoFiscalizacion.class, id);
    }

    // Bloquea la fila hasta el fin de la transaccion: si dos funcionarios resuelven el mismo caso a la
    // vez, el segundo espera al primero y despues ve el estado final (y se le rechaza la resolucion).
    // No se usa find(..., PESSIMISTIC_WRITE): Hibernate 7 falla al bloquear con follow-on locking una
    // entidad con @OneToOne(mappedBy). Se bloquea la fila con lock y se relee el estado con refresh.
    public CasoFiscalizacion buscarPorIdParaActualizar(Long id) {
        CasoFiscalizacion caso = em.find(CasoFiscalizacion.class, id);
        if (caso != null) {
            em.lock(caso, LockModeType.PESSIMISTIC_WRITE);
            em.refresh(caso);
        }
        return caso;
    }

    // Una pagina de la bandeja. Lo que muestra cada fila (guia con empresa y vehiculo, descargo y
    // resolucion) se trae en la misma consulta con un entity graph, para no hacer una consulta por caso.
    // No se usa "JOIN FETCH c.guia g ...": WildFly corre Hibernate con JPQL estricto, que no admite
    // alias en un fetch join. Las relaciones son todas a-uno, asi que el limite se aplica en el SQL.
    public List<CasoFiscalizacion> bandeja(EstadoCaso estado, int desde, int cantidad) {
        EntityGraph<CasoFiscalizacion> grafo = em.createEntityGraph(CasoFiscalizacion.class);
        grafo.addAttributeNodes("descargo", "fundamentoResolucion");
        grafo.addSubgraph("guia").addAttributeNodes("empresa", "vehiculo");
        grafo.addSubgraph("vehiculo").addAttributeNodes("empresa");
        TypedQuery<CasoFiscalizacion> q = estado == null
                ? em.createQuery(BANDEJA + ORDEN, CasoFiscalizacion.class)
                : em.createQuery(BANDEJA + " WHERE c.estado = :estado" + ORDEN, CasoFiscalizacion.class)
                        .setParameter("estado", estado);
        return q.setHint("jakarta.persistence.fetchgraph", grafo)
                .setFirstResult(desde)
                .setMaxResults(cantidad)
                .getResultList();
    }

    public long contarBandeja(EstadoCaso estado) {
        if (estado == null) {
            return em.createQuery("SELECT COUNT(c) FROM CasoFiscalizacion c", Long.class).getSingleResult();
        }
        return em.createQuery("SELECT COUNT(c) FROM CasoFiscalizacion c WHERE c.estado = :estado", Long.class)
                .setParameter("estado", estado)
                .getSingleResult();
    }

    public void guardar(CasoFiscalizacion caso) {
        em.persist(caso);
    }

    public Vehiculo buscarVehiculo(String matricula) {
        return em.find(Vehiculo.class, matricula);
    }

    public Guia buscarGuia(String id) {
        return em.find(Guia.class, id);
    }

    // Casos abiertos (EstadoCaso.ABIERTOS) del vehiculo
    public List<CasoFiscalizacion> abiertosDelVehiculo(String matricula) {
        return em.createQuery("SELECT c FROM CasoFiscalizacion c WHERE c.vehiculo.matricula = :matricula"
                + " AND c.estado IN :abiertos ORDER BY c.fechaDeteccion, c.id", CasoFiscalizacion.class)
                .setParameter("matricula", matricula)
                .setParameter("abiertos", EstadoCaso.ABIERTOS)
                .getResultList();
    }

    // El caso que ya tiene ese evento de tracking como evidencia; null si ninguno
    public CasoFiscalizacion casoConEventoTracking(ClaveEvento clave) {
        return em.createQuery("SELECT c FROM CasoFiscalizacion c JOIN c.clavesEventosTracking t"
                + " WHERE t.matricula = :matricula AND t.fechaHora = :fechaHora", CasoFiscalizacion.class)
                .setParameter("matricula", clave.getMatricula())
                .setParameter("fechaHora", clave.getFechaHora())
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    // El caso que ya tiene ese pesaje como evidencia; null si ninguno
    public CasoFiscalizacion casoConPesaje(ClaveEvento clave) {
        return em.createQuery("SELECT c FROM CasoFiscalizacion c JOIN c.eventosPesajes p"
                + " WHERE p.matricula = :matricula AND p.fechaHora = :fechaHora", CasoFiscalizacion.class)
                .setParameter("matricula", clave.getMatricula())
                .setParameter("fechaHora", clave.getFechaHora())
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public EventoPesajes buscarPesaje(ClaveEvento clave) {
        return em.find(EventoPesajes.class, clave);
    }

    public void guardarResolucion(FundamentoResolucion resolucion) {
        em.persist(resolucion);
    }

    // Evidencia de habilitacion del vehiculo (permiso nacional de circulacion e ITV vigente al detectarse)
    public PermisoCirculacion permisoDelVehiculo(Vehiculo vehiculo) {
        return em.createQuery("SELECT p FROM PermisoCirculacion p WHERE p.vehiculo = :vehiculo",
                PermisoCirculacion.class)
                .setParameter("vehiculo", vehiculo)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    // La ITV emitida hasta ese dia que vence ultima. NULLS LAST: en PostgreSQL DESC pone los nulos primero.
    public ITV itvVigenteAl(Vehiculo vehiculo, LocalDate dia) {
        return em.createQuery("SELECT i FROM ITV i WHERE i.vehiculo = :vehiculo AND i.fechaEmision <= :dia"
                + " ORDER BY i.fechaVencimiento DESC NULLS LAST", ITV.class)
                .setParameter("vehiculo", vehiculo)
                .setParameter("dia", dia)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
