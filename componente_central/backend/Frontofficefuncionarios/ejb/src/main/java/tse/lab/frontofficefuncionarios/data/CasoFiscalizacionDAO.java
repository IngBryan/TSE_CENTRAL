package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Descargo;
import tse.lab.model.EstadoCaso;
import tse.lab.model.FundamentoResolucion;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Vehiculo;

@Stateless
public class CasoFiscalizacionDAO {

    // Trae en la misma consulta lo que muestra la bandeja (evita una consulta por caso)
    private static final String BANDEJA = "SELECT c FROM CasoFiscalizacion c JOIN FETCH c.guia g"
            + " LEFT JOIN FETCH g.empresa LEFT JOIN FETCH g.vehiculo"
            + " LEFT JOIN FETCH c.descargo LEFT JOIN FETCH c.fundamentoResolucion";
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

    public List<CasoFiscalizacion> bandeja(EstadoCaso estado) {
        if (estado == null) {
            return em.createQuery(BANDEJA + ORDEN, CasoFiscalizacion.class).getResultList();
        }
        return em.createQuery(BANDEJA + " WHERE c.estado = :estado" + ORDEN, CasoFiscalizacion.class)
                .setParameter("estado", estado)
                .getResultList();
    }

    // Por el lado duenio de la relacion: no depende de que el caso tenga la referencia cargada
    public Descargo descargoDelCaso(CasoFiscalizacion caso) {
        return em.createQuery("SELECT d FROM Descargo d WHERE d.casoFiscalizacion = :caso", Descargo.class)
                .setParameter("caso", caso)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public FundamentoResolucion resolucionDelCaso(CasoFiscalizacion caso) {
        return em.createQuery("SELECT f FROM FundamentoResolucion f JOIN FETCH f.funcionario"
                + " WHERE f.casoFiscalizacion = :caso", FundamentoResolucion.class)
                .setParameter("caso", caso)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public void guardarResolucion(FundamentoResolucion resolucion) {
        em.persist(resolucion);
    }

    // Evidencia de habilitacion del vehiculo (permiso nacional de circulacion e ITV mas reciente)
    public PermisoCirculacion permisoDelVehiculo(Vehiculo vehiculo) {
        return em.createQuery("SELECT p FROM PermisoCirculacion p WHERE p.vehiculo = :vehiculo",
                PermisoCirculacion.class)
                .setParameter("vehiculo", vehiculo)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public ITV ultimaItvDelVehiculo(Vehiculo vehiculo) {
        return em.createQuery("SELECT i FROM ITV i WHERE i.vehiculo = :vehiculo ORDER BY i.fechaVencimiento DESC",
                ITV.class)
                .setParameter("vehiculo", vehiculo)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
