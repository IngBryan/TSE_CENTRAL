package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Period;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Selection;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facets;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.TimeFacet.Bucket;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.composite.ConfirmationRateKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.CargoVolumeKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.ConfirmedInfractionsKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.DetectedInfractionsKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.EnabledCompaniesKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.EnabledVehiclesKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.TripsKpi;
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

// The KPI queries against H2: three finished trips in the period, one in progress and one a year before
class KpiTest {

    private static final Period PERIOD = new Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 7));
    private static final Period PERIOD_OCTUBRE = new Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    private static final String SOJA = "1";
    private static final String ARENA = "2";

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        emf = Persistence.createEntityManagerFactory("backofficeTestPersistenceUnit");
        em = emf.createEntityManager();
        em.getTransaction().begin();

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

        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void countsTheFinishedTripsOfThePeriod() {
        assertEquals(Map.of("2026", 3.0), values(new TripsKpi(), Facets.YEAR, Map.of()));
    }

    @Test
    void groupsByTime() {
        assertEquals(Map.of("2026-10", 2.0, "2026-11", 1.0), values(new TripsKpi(), Facets.MONTH, Map.of()));
        assertEquals(Map.of("2026-10-05", 1.0, "2026-10-20", 1.0, "2026-11-02", 1.0),
                values(new TripsKpi(), Facets.DAY, Map.of()));
        assertEquals(Map.of("2026", 3.0), values(new TripsKpi(), Facets.YEAR, Map.of()));
        assertEquals(3, new TripsKpi().calculate(em, PERIOD, Facets.WEEK, Map.of()).size());
    }

    @Test
    void groupsByDepartmentCargoTypeAndCategory() {
        assertEquals(Map.of("Montevideo", 2.0, "Salto", 1.0), values(new TripsKpi(), Facets.DEPARTMENT, Map.of()));
        assertEquals(Map.of("Soja a granel", 2.0, "Arena", 1.0), values(new TripsKpi(), Facets.CARGO_TYPE, Map.of()));
        assertEquals(Map.of("Agropecuario", 30.0, "Construcción", 30.0),
                values(new CargoVolumeKpi(), Facets.CATEGORY, Map.of()));
    }

    @Test
    void includesAndExcludesAtTheSameTime() {
        Selection sinSalto = new Selection(List.of(), List.of("Salto"));
        assertEquals(Map.of("2026", 2.0), values(new TripsKpi(), Facets.YEAR, Map.of(Facets.DEPARTMENT, sinSalto)));

        Selection soloArena = new Selection(List.of(ARENA), List.of());
        assertEquals(Map.of("2026", 30.0), values(new CargoVolumeKpi(), Facets.YEAR, Map.of(Facets.CARGO_TYPE, soloArena)));

        Selection sojaYArenaSinSoja = new Selection(List.of(SOJA, ARENA), List.of(SOJA));
        assertEquals(Map.of("2026", 1.0),
                values(new TripsKpi(), Facets.YEAR, Map.of(Facets.CARGO_TYPE, sojaYArenaSinSoja)));

        Map<Facet, Selection> montevideoSinArena = Map.of(
                Facets.DEPARTMENT, new Selection(List.of("Montevideo"), List.of()),
                Facets.CARGO_TYPE, new Selection(List.of(), List.of(ARENA)));
        assertEquals(Map.of("2026", 1.0), values(new TripsKpi(), Facets.YEAR, montevideoSinArena));
    }

    @Test
    void countsInfractionsByDetectionDay() {
        assertEquals(Map.of("2026", 3.0), values(new DetectedInfractionsKpi(), Facets.YEAR, Map.of()));
        assertEquals(Map.of("2026", 2.0), values(new ConfirmedInfractionsKpi(), Facets.YEAR, Map.of()));
        assertEquals(2.0 / 3, values(new ConfirmationRateKpi(), Facets.YEAR, Map.of()).get("2026"), 1e-9);
        assertEquals(Map.of("2026-10", 1.0, "2026-11", 0.0), values(new ConfirmationRateKpi(), Facets.MONTH, Map.of()));
        assertEquals(Map.of("2026-10", 2.0, "2026-11", 1.0),
                values(new DetectedInfractionsKpi(), Facets.MONTH, Map.of()));
        assertEquals(Map.of("Montevideo", 1.0, "Salto", 1.0),
                values(new ConfirmedInfractionsKpi(), Facets.DEPARTMENT, Map.of()));
    }

    @Test
    void countsEnabledCompanies() {
        assertEquals(Map.of("2026", 2.0), values(new EnabledCompaniesKpi(), Facets.YEAR, Map.of()));
    }

    @Test
    void countsVehiclesWithPapersInForceAtTheEndOfThePeriod() {
        assertEquals(Map.of("2026", 2.0), values(new EnabledVehiclesKpi(), Facets.YEAR, Map.of()));

        Period antes = new Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
        assertEquals(3.0, new EnabledVehiclesKpi().calculate(em, antes, Facets.YEAR, Map.of()).get(0).value());
    }

    // A company or vehicle has no day, department or cargo type of its own: its trips would make it another count
    @Test
    void refusesFacetsNoTableOfTheKpiHolds() {
        assertThrows(IllegalArgumentException.class, () -> new EnabledCompaniesKpi().calculate(em, PERIOD, Facets.YEAR,
                Map.of(Facets.DEPARTMENT, new Selection(List.of("Salto"), List.of()))));
        assertThrows(IllegalArgumentException.class,
                () -> new EnabledVehiclesKpi().calculate(em, PERIOD, Facets.DEPARTMENT, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EnabledVehiclesKpi().calculate(em, PERIOD, Facets.YEAR,
                Map.of(Facets.CARGO_TYPE, new Selection(List.of(SOJA), List.of()))));
    }

    @Test
    void listsTheValuesOfEachFacet() {
        assertEquals(List.of(new FacetValue("Montevideo", "Montevideo"), new FacetValue("Salto", "Salto")),
                Facets.DEPARTMENT.values(em));
        assertEquals(List.of(new FacetValue(ARENA, "Arena"), new FacetValue(SOJA, "Soja a granel")),
                Facets.CARGO_TYPE.values(em));
        assertEquals(List.of(new FacetValue("1", "Agropecuario"), new FacetValue("2", "Construcción")),
                Facets.CATEGORY.values(em));
        assertEquals(List.of(), Facets.MONTH.values(em));
        assertEquals(List.of(
                        new Bucket(new FacetValue("2026-10", "2026-10"), PERIOD_OCTUBRE),
                        new Bucket(new FacetValue("2026-11", "2026-11"),
                                new Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7)))),
                Facets.MONTH.buckets(PERIOD));
    }

    @Test
    void groupsByTheIdsTheFiltersUse() {
        List<FacetValue> groups = new TripsKpi().calculate(em, PERIOD, Facets.CARGO_TYPE, Map.of()).stream()
                .map(KpiValue::group).toList();
        assertEquals(List.of(new FacetValue(SOJA, "Soja a granel"), new FacetValue(ARENA, "Arena")), groups);

        Selection soloConstruccion = new Selection(List.of("2"), List.of());
        assertEquals(Map.of("2026", 1.0), values(new TripsKpi(), Facets.YEAR, Map.of(Facets.CATEGORY, soloConstruccion)));
    }

    // The facet joins are LEFT: a case without a trip is still counted, in the group without department
    @Test
    void keepsTheRowsWithoutAValueOfTheFacet() {
        em.getTransaction().begin();
        Vehiculo camion = em.find(Vehiculo.class, "SAA1001");
        CasoFiscalizacion sinGuia = new CasoFiscalizacion("Viaje sin guía", "VIAJE_SIN_GUIA", camion, null);
        em.persist(sinGuia);
        em.flush();
        em.createQuery("UPDATE CasoFiscalizacion c SET c.fechaDeteccion = :fecha WHERE c.id = :id")
                .setParameter("fecha", Instant.parse("2026-10-30T15:00:00Z"))
                .setParameter("id", sinGuia.getId())
                .executeUpdate();
        em.getTransaction().commit();

        assertEquals(Map.of("Montevideo", 2.0, "Salto", 1.0, "sinValor", 1.0),
                values(new DetectedInfractionsKpi(), Facets.DEPARTMENT, Map.of()));
    }

    // A snapshot is evaluated on the last day of each group: the second vehicle's ITV expires on October 15
    @Test
    void evaluatesSnapshotsOnTheLastDayOfEachGroup() {
        Period alrededorDelVencimiento = new Period(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16));
        assertEquals(List.of(3.0, 3.0, 2.0), new EnabledVehiclesKpi()
                .calculate(em, alrededorDelVencimiento, Facets.DAY, Map.of()).stream().map(KpiValue::value).toList());

        assertEquals(Map.of("2026-10", 2.0, "2026-11", 2.0), values(new EnabledVehiclesKpi(), Facets.MONTH, Map.of()));
        assertEquals(Map.of("2026-10", 2.0, "2026-11", 2.0), values(new EnabledCompaniesKpi(), Facets.MONTH, Map.of()));
    }

    @Test
    void groupsCompaniesAndVehiclesByTheRubrosOfTheCompany() {
        assertEquals(Map.of("Agropecuario", 1.0, "Construcción", 1.0),
                values(new EnabledCompaniesKpi(), Facets.CATEGORY, Map.of()));
        assertEquals(Map.of("Agropecuario", 1.0, "Construcción", 1.0),
                values(new EnabledVehiclesKpi(), Facets.CATEGORY, Map.of()));

        Selection soloAgro = new Selection(List.of("1"), List.of());
        assertEquals(Map.of("2026", 1.0), values(new EnabledCompaniesKpi(), Facets.YEAR, Map.of(Facets.CATEGORY, soloAgro)));
    }

    // A report always groups by some facet
    @Test
    void refusesACalculationWithoutGrouping() {
        assertThrows(IllegalArgumentException.class, () -> new TripsKpi().calculate(em, PERIOD, null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfirmationRateKpi().calculate(em, PERIOD, null, Map.of()));
    }

    private Map<String, Double> values(Kpi kpi, Facet grouping, Map<Facet, Selection> filters) {
        return kpi.calculate(em, PERIOD, grouping, filters).stream()
                .collect(Collectors.toMap(v -> v.group() == null ? "sinValor" : v.group().name(), KpiValue::value));
    }

    private Empresa empresa(Long nro, boolean habilitada, Rubro rubro) {
        Empresa empresa = new Empresa(nro, "Empresa " + nro, "Empresa " + nro + " SA", "Dirección " + nro, null);
        empresa.setHabilitada(habilitada);
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
