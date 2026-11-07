package tse.lab.frontofficeciudadanos.data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EstadoGuia;

// Consultas del portal publico. Las de estadisticas devuelven los datos agrupados POR EMPRESA (no filas
// sueltas): el servicio necesita saber cuantas empresas distintas aporta cada numero para aplicar el umbral
// de agregacion antes de publicarlo.
@Stateless
public class PortalPublicoDAO {

    private static final String CARGA_DE_VIAJES = ", COUNT(g), SUM(g.pesoCargaKg) FROM Guia g"
            + " WHERE g.estado = :finalizada AND g.fecha BETWEEN :desde AND :hasta GROUP BY g.empresa.nroEmpresa, ";
    private static final String VIAJES_POR_RUBRO = "SELECT g.empresa.nroEmpresa, g.rubroCliente" + CARGA_DE_VIAJES
            + "g.rubroCliente";
    private static final String VIAJES_POR_TIPO_CARGA = "SELECT g.empresa.nroEmpresa, g.tipoCarga" + CARGA_DE_VIAJES
            + "g.tipoCarga";

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    // Busqueda por nombre publico o razon social (sin distinguir mayusculas), hasta `maximo` resultados
    public List<Empresa> buscarEmpresas(String texto, int maximo) {
        String patron = "%" + texto.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return em.createQuery("SELECT e FROM Empresa e WHERE LOWER(e.nombrePublico) LIKE :p ESCAPE '\\'"
                + " OR LOWER(e.razonSocial) LIKE :p ESCAPE '\\' ORDER BY e.nombrePublico, e.nroEmpresa", Empresa.class)
                .setParameter("p", patron)
                .setMaxResults(maximo)
                .getResultList();
    }

    public long empresasHabilitadas() {
        return em.createQuery("SELECT COUNT(e) FROM Empresa e WHERE e.habilitada = true", Long.class)
                .getSingleResult();
    }

    // [nroEmpresa, cantidad] de vehiculos habilitados al dia `hoy`: activos, de una empresa habilitada, con
    // permiso vigente e ITV sin vencer (la misma regla que Vehiculo.habilitadoEn). nroEmpresa null = todas.
    public List<Object[]> vehiculosHabilitadosPorEmpresa(LocalDate hoy, Long nroEmpresa) {
        // Sin ":nro IS NULL OR ...": PostgreSQL no puede deducir el tipo de un parametro null
        var query = em.createQuery("SELECT v.empresa.nroEmpresa, COUNT(v) FROM Vehiculo v"
                + " WHERE v.activo = true AND v.empresa.habilitada = true"
                + (nroEmpresa == null ? "" : " AND v.empresa.nroEmpresa = :nro")
                + " AND EXISTS (SELECT p.nro FROM PermisoCirculacion p WHERE p.vehiculo = v"
                + "     AND p.fechaEmision <= :hoy AND p.fechaVencimiento >= :hoy)"
                + " AND EXISTS (SELECT i.id FROM ITV i WHERE i.vehiculo = v AND i.fechaVencimiento >= :hoy)"
                + " GROUP BY v.empresa.nroEmpresa", Object[].class)
                .setParameter("hoy", hoy);
        if (nroEmpresa != null) {
            query.setParameter("nro", nroEmpresa);
        }
        return query.getResultList();
    }

    // [nroEmpresa, fecha, viajes, kg] de los viajes FINALIZADOS entre las dos fechas (inclusive)
    public List<Object[]> viajesPorEmpresaYFecha(LocalDate desde, LocalDate hasta) {
        return em.createQuery("SELECT g.empresa.nroEmpresa, g.fecha, COUNT(g), SUM(g.pesoCargaKg) FROM Guia g"
                + " WHERE g.estado = :finalizada AND g.fecha BETWEEN :desde AND :hasta"
                + " GROUP BY g.empresa.nroEmpresa, g.fecha", Object[].class)
                .setParameter("finalizada", EstadoGuia.FINALIZADA)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();
    }

    // [nroEmpresa, rubroCliente, viajes, kg] de los viajes FINALIZADOS entre las dos fechas (inclusive)
    public List<Object[]> viajesPorEmpresaYRubro(LocalDate desde, LocalDate hasta) {
        return viajesFinalizados(VIAJES_POR_RUBRO, desde, hasta);
    }

    // [nroEmpresa, tipoCarga, viajes, kg] de los viajes FINALIZADOS entre las dos fechas (inclusive)
    public List<Object[]> viajesPorEmpresaYTipoCarga(LocalDate desde, LocalDate hasta) {
        return viajesFinalizados(VIAJES_POR_TIPO_CARGA, desde, hasta);
    }

    private List<Object[]> viajesFinalizados(String consulta, LocalDate desde, LocalDate hasta) {
        return em.createQuery(consulta, Object[].class)
                .setParameter("finalizada", EstadoGuia.FINALIZADA)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();
    }

    // [nombre del rubro, nroEmpresa] de cada rubro de cada empresa habilitada (una empresa puede tener varios)
    public List<Object[]> rubrosDeEmpresasHabilitadas() {
        return em.createQuery("SELECT r.nombre, e.nroEmpresa FROM Empresa e JOIN e.rubros r"
                + " WHERE e.habilitada = true", Object[].class)
                .getResultList();
    }

    // [nroEmpresa, tipoCaso, cantidad] de los casos CONFIRMADOS detectados en [desde, hasta)
    public List<Object[]> infraccionesConfirmadasPorEmpresaYTipo(Instant desde, Instant hasta) {
        return em.createQuery("SELECT c.vehiculo.empresa.nroEmpresa, c.tipoCaso, COUNT(c) FROM CasoFiscalizacion c"
                + " WHERE c.estado = :confirmada AND c.fechaDeteccion >= :desde AND c.fechaDeteccion < :hasta"
                + " GROUP BY c.vehiculo.empresa.nroEmpresa, c.tipoCaso", Object[].class)
                .setParameter("confirmada", EstadoCaso.CONFIRMADA)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();
    }

    // [nroEmpresa, estado, fechaDeteccion, fechaResolucion] de los casos resueltos (confirmados o desestimados)
    // detectados en [desde, hasta)
    public List<Object[]> casosResueltos(Instant desde, Instant hasta) {
        return em.createQuery("SELECT c.vehiculo.empresa.nroEmpresa, c.estado, c.fechaDeteccion, f.fechaHora"
                + " FROM FundamentoResolucion f JOIN f.casoFiscalizacion c"
                + " WHERE c.estado IN :resueltos AND c.fechaDeteccion >= :desde AND c.fechaDeteccion < :hasta",
                Object[].class)
                .setParameter("resueltos", List.of(EstadoCaso.CONFIRMADA, EstadoCaso.DESESTIMADA))
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();
    }
}
