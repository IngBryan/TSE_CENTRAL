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
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportTestData;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Selection;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facets;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.TimeFacet.Bucket;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.composite.ConfirmationRateKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.CargoVolumeKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.ClosedCasesKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.ConfirmedInfractionsKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.DetectedInfractionsKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.EnabledCompaniesKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.EnabledVehiclesKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.TripsKpi;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.EstadoCaso;
import tse.lab.model.Empresa;
import tse.lab.model.Rubro;
import tse.lab.model.Vehiculo;

// The KPI queries against H2, on ReportTestData
class KpiTest {

    private static final Period PERIOD = ReportTestData.PERIOD;
    private static final Period PERIOD_OCTUBRE = new Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    private static final String SOJA = ReportTestData.SOJA;
    private static final String ARENA = ReportTestData.ARENA;

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        emf = Persistence.createEntityManagerFactory("backofficeTestPersistenceUnit");
        em = emf.createEntityManager();
        ReportTestData.load(em);
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

    // A trip by the company it is made for, a case by the one that owns its vehicle; the third company is disabled
    @Test
    void groupsEveryKpiByCompany() {
        Map<String, Double> unoYUno = Map.of("Empresa 1", 1.0, "Empresa 2", 1.0);
        assertEquals(Map.of("Empresa 1", 2.0, "Empresa 2", 1.0), values(new TripsKpi(), Facets.COMPANY, Map.of()));
        assertEquals(Map.of("Empresa 1", 2.0, "Empresa 2", 1.0),
                values(new DetectedInfractionsKpi(), Facets.COMPANY, Map.of()));
        assertEquals(unoYUno, values(new EnabledCompaniesKpi(), Facets.COMPANY, Map.of()));
        assertEquals(unoYUno, values(new EnabledVehiclesKpi(), Facets.COMPANY, Map.of()));

        Selection soloObras = new Selection(List.of("2"), List.of());
        assertEquals(Map.of("2026", 1.0), values(new TripsKpi(), Facets.YEAR, Map.of(Facets.COMPANY, soloObras)));
        assertEquals(Map.of("2026", 1.0),
                values(new EnabledVehiclesKpi(), Facets.YEAR, Map.of(Facets.COMPANY, soloObras)));
        assertEquals(List.of(new FacetValue("1", "Empresa 1"), new FacetValue("2", "Empresa 2"),
                new FacetValue("3", "Empresa 3")), Facets.COMPANY.values(em));
    }

    // A company counts from the day it was registered on: a period that ends before it does not see it
    @Test
    void countsCompaniesFromTheirRegistration() {
        em.getTransaction().begin();
        em.find(Empresa.class, 2L).setFechaAlta(LocalDate.of(2026, 10, 15));
        em.getTransaction().commit();
        em.clear();

        Period alrededorDelAlta = new Period(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16));
        assertEquals(List.of(1.0, 2.0, 2.0), new EnabledCompaniesKpi()
                .calculate(em, alrededorDelAlta, Facets.DAY, Map.of()).stream().map(KpiValue::value).toList());
        assertEquals(1.0, new EnabledCompaniesKpi().total(em, new Period(LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 10, 14)), Map.of()));
        assertEquals(0.0, new EnabledCompaniesKpi().total(em, new Period(LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31)), Map.of()));
    }

    // A dismissed case is closed but not a confirmed infraction: the rate counts only the confirmed ones
    @Test
    void ratesOnlyTheConfirmedInfractions() {
        em.getTransaction().begin();
        CasoFiscalizacion desestimado = new CasoFiscalizacion("Desestimado", "EXCESO_CARGA", em.find(Vehiculo.class, "SAA1001"), null);
        em.persist(desestimado);
        em.flush();
        em.createQuery("UPDATE CasoFiscalizacion c SET c.fechaDeteccion = :fecha, c.estado = :estado WHERE c.id = :id")
                .setParameter("fecha", Instant.parse("2026-10-30T15:00:00Z"))
                .setParameter("estado", EstadoCaso.DESESTIMADA)
                .setParameter("id", desestimado.getId())
                .executeUpdate();
        em.getTransaction().commit();

        assertEquals(Map.of("2026", 2.0), values(new ConfirmedInfractionsKpi(), Facets.YEAR, Map.of()));
        assertEquals(Map.of("2026", 3.0), values(new ClosedCasesKpi(), Facets.YEAR, Map.of()));
        assertEquals(0.5, values(new ConfirmationRateKpi(), Facets.YEAR, Map.of()).get("2026"), 1e-9);
    }

    // Excluding a rubro leaves out the companies that work in it, even when they work in another one too; the first
    // company works in both rubros here
    @Test
    void excludesTheWholeCompanyOfAnExcludedRubro() {
        em.getTransaction().begin();
        em.find(Empresa.class, 1L).setRubros(List.of(em.find(Rubro.class, 1L), em.find(Rubro.class, 2L)));
        em.getTransaction().commit();
        em.clear();

        Selection sinAgro = new Selection(List.of(), List.of("1"));
        assertEquals(Map.of("2026", 1.0), values(new EnabledCompaniesKpi(), Facets.YEAR, Map.of(Facets.CATEGORY, sinAgro)));
        assertEquals(Map.of("2026", 1.0), values(new EnabledVehiclesKpi(), Facets.YEAR, Map.of(Facets.CATEGORY, sinAgro)));
        assertEquals(Map.of("Construcción", 1.0),
                values(new EnabledCompaniesKpi(), Facets.CATEGORY, Map.of(Facets.CATEGORY, sinAgro)));

        Selection soloAgro = new Selection(List.of("1"), List.of());
        assertEquals(Map.of("2026", 1.0), values(new EnabledCompaniesKpi(), Facets.YEAR, Map.of(Facets.CATEGORY, soloAgro)));
    }

    // The cases of the test are all of overload; one of another kind reads as its code
    @Test
    void groupsCasesByTheKindOfInfraction() {
        assertEquals(Map.of("Exceso de carga", 3.0), values(new DetectedInfractionsKpi(), Facets.CASE_TYPE, Map.of()));
        assertEquals(List.of(new FacetValue("EXCESO_CARGA", "Exceso de carga")), Facets.CASE_TYPE.values(em));
        assertThrows(IllegalArgumentException.class,
                () -> new TripsKpi().calculate(em, PERIOD, Facets.CASE_TYPE, Map.of()));

        em.getTransaction().begin();
        em.persist(new CasoFiscalizacion("Desvío", "DESVIO_RUTA", em.find(Vehiculo.class, "SAA1001"), null));
        em.getTransaction().commit();
        assertEquals(new FacetValue("DESVIO_RUTA", "Desvio ruta"), Facets.CASE_TYPE.values(em).get(0));
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

}
