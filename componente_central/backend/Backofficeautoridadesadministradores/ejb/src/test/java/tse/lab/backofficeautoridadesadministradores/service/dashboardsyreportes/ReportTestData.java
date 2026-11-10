package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;

import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;
import tse.lab.model.ITV;
import tse.lab.model.Localidad;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Rubro;
import tse.lab.model.TipoCarga;
import tse.lab.model.Vehiculo;

/**
 * The data the KPI and report tests run on: three finished trips from October 1 to November 7, 2026, one in
 * progress and one a year before; a case for each finished trip; two enabled companies and a disabled one, registered on January 1.
 */
public final class ReportTestData {

    public static final Period PERIOD = new Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 7));
    public static final String SOJA = "1";
    public static final String ARENA = "2";

    private final EntityManager em;

    private ReportTestData(EntityManager em) {
        this.em = em;
    }

    public static void load(EntityManager em) {
        em.getTransaction().begin();
        new ReportTestData(em).persist();
        em.getTransaction().commit();
        em.clear();
    }

    private void persist() {
        Localidad montevideo = new Localidad(1L, "Montevideo", "Montevideo", -34.9, -56.2);
        Localidad salto = new Localidad(2L, "Salto", "Salto", -31.4, -57.9);
        TipoCarga soja = new TipoCarga(1L, "Soja a granel");
        TipoCarga arena = new TipoCarga(2L, "Arena");
        Rubro agro = new Rubro(1L, "Agropecuario");
        Rubro construccion = new Rubro(2L, "Construcción");
        List.of(montevideo, salto, soja, arena, agro, construccion).forEach(em::persist);

        Empresa campo = empresa(1L, true, agro);
        Empresa obras = empresa(2L, true, construccion);
        empresa(3L, false, agro);

        Vehiculo camion = vehiculo("SAA1001", campo, LocalDate.of(2027, 1, 1));
        vehiculo("SAA1002", campo, LocalDate.of(2026, 10, 15));
        Vehiculo volquete = vehiculo("SBB2001", obras, LocalDate.of(2027, 1, 1));

        Guia g1 = viaje("G1", campo, camion, LocalDate.of(2026, 10, 5), montevideo, soja, 10_000, "Agropecuario");
        Guia g2 = viaje("G2", campo, camion, LocalDate.of(2026, 10, 20), salto, soja, 20_000, "Agropecuario");
        Guia g3 = viaje("G3", obras, volquete, LocalDate.of(2026, 11, 2), montevideo, arena, 30_000, "Construcción");
        viaje("G4", obras, volquete, LocalDate.of(2026, 11, 3), montevideo, arena, 5_000, "Construcción")
                .cambiarEstado(EstadoGuia.EN_CURSO);
        Guia g5 = viaje("G5", campo, camion, LocalDate.of(2025, 10, 10), montevideo, soja, 8_000, "Agropecuario");

        caso(g1, "2026-10-06T15:00:00Z", EstadoCaso.CONFIRMADA);
        caso(g2, "2026-10-21T15:00:00Z", EstadoCaso.CONFIRMADA);
        caso(g3, "2026-11-02T15:00:00Z", EstadoCaso.DETECTADA);
        // A year before the period: an OR in a KPI condition must not let it in
        caso(g5, "2025-10-11T15:00:00Z", EstadoCaso.CONFIRMADA);
    }

    private Empresa empresa(Long nro, boolean habilitada, Rubro rubro) {
        Empresa empresa = new Empresa(nro, "Empresa " + nro, "Empresa " + nro + " SA", "Dirección " + nro, null);
        empresa.setHabilitada(habilitada);
        empresa.setFechaAlta(LocalDate.of(2026, 1, 1));
        empresa.setRubros(List.of(rubro));
        em.persist(empresa);
        return empresa;
    }

    private Vehiculo vehiculo(String matricula, Empresa empresa, LocalDate vencimientoItv) {
        Vehiculo vehiculo = new Vehiculo(matricula, "Scania", "R450", 15_000, 30_000, empresa);
        em.persist(vehiculo);
        em.persist(new ITV(LocalDate.of(2026, 1, 1), vencimientoItv, vehiculo));
        em.persist(new PermisoCirculacion("P-" + matricula, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1), vehiculo));
        return vehiculo;
    }

    private Guia viaje(String id, Empresa empresa, Vehiculo vehiculo, LocalDate fecha, Localidad origen,
            TipoCarga tipoCarga, long kg, String rubroCliente) {
        Guia guia = new Guia(id, rubroCliente, kg, fecha, origen.getNombre(), "Montevideo", empresa);
        guia.setOrigenLocalidad(origen);
        guia.setTipoCarga(tipoCarga.getNombre());
        guia.setVehiculo(vehiculo);
        guia.cambiarEstado(EstadoGuia.FINALIZADA);
        em.persist(guia);
        return guia;
    }

    // The entity stamps the detection with now and starts DETECTADA: the test sets both
    private void caso(Guia guia, String detectado, EstadoCaso estado) {
        CasoFiscalizacion caso = new CasoFiscalizacion("Caso de prueba", "EXCESO_CARGA", guia);
        em.persist(caso);
        em.flush();
        em.createQuery("UPDATE CasoFiscalizacion c SET c.fechaDeteccion = :fecha, c.estado = :estado WHERE c.id = :id")
                .setParameter("fecha", Instant.parse(detectado))
                .setParameter("estado", estado)
                .setParameter("id", caso.getId())
                .executeUpdate();
    }
}
