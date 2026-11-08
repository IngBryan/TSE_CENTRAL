package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportRequest.KpiRequest;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportRequest.Range;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportResult.Row;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;

// Reports on ReportTestData, from October 1 to November 7 of 2026 against the same days of 2025
class ReportServiceTest {

    private static final Range PERIOD = new Range("2026-10-01T00:00:00", "2026-11-07T23:59:59.999");
    private static final Range COMPARISON = new Range("2025-10-01T00:00:00", "2025-11-07T23:59:59.999");
    private static final List<String> ALL_MEASURES = List.of("current", "previous", "growth", "difference");

    private EntityManagerFactory emf;
    private EntityManager em;
    private ReportService service;

    @BeforeEach
    void setUp() {
        emf = Persistence.createEntityManagerFactory("backofficeTestPersistenceUnit");
        em = emf.createEntityManager();
        ReportTestData.load(em);
        service = new ReportService(em);
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    // October 2026 against October 2025, November against November; November 2025 had no trips
    @Test
    void comparesEachMonthWithTheSameMonthOfTheComparison() {
        ReportResult result = service.load(request(List.of(new KpiRequest("viajes", ALL_MEASURES)), COMPARISON,
                "month", Map.of()));

        assertEquals("month", result.groupBy());
        assertEquals(List.of("2026-10", "2026-11"), result.rows().stream().map(row -> row.group().id()).toList());
        assertEquals(measures(2.0, 1.0, 1.0, 1.0), result.rows().get(0).values().get("viajes"));
        assertEquals(measures(1.0, 0.0, null, 1.0), result.rows().get(1).values().get("viajes"));
        assertEquals(measures(3.0, 1.0, 2.0, 2.0), result.total().get("viajes"));
    }

    // Each group against the same group; Salto had no cases in 2025, so it has no previous rate
    @Test
    void recalculatesTheRateOfEachGroupAndOfTheTotal() {
        ReportResult result = service.load(request(List.of(new KpiRequest("tasa", List.of("current", "growth"))),
                COMPARISON, "department", Map.of()));

        assertEquals(List.of(new FacetValue("Salto", "Salto"), new FacetValue("Montevideo", "Montevideo")),
                result.rows().stream().map(Row::group).toList());
        assertEquals(Map.of("current", 1.0), withoutNulls(result.rows().get(0).values().get("tasa")));
        assertNull(result.rows().get(0).values().get("tasa").get("growth"));
        assertEquals(Map.of("current", 0.5, "growth", -0.5), result.rows().get(1).values().get("tasa"));

        Map<String, Double> total = result.total().get("tasa");
        assertEquals(2.0 / 3, total.get("current"), 1e-9);
        assertEquals(-1.0 / 3, total.get("growth"), 1e-9);
    }

    @Test
    void listsEveryDayOfThePeriodEvenWithoutRows() {
        ReportResult result = service.load(request(List.of(new KpiRequest("viajes", List.of("current"))), null,
                "day", Map.of()));

        assertEquals(38, result.rows().size());
        assertEquals("2026-10-01", result.rows().get(0).group().id());
        assertEquals(Map.of("current", 0.0), result.rows().get(0).values().get("viajes"));
        assertEquals(Map.of("current", 1.0), result.rows().get(4).values().get("viajes"));
    }

    // A snapshot's total is taken on the last day, not added up over the months
    @Test
    void takesTheTotalOfASnapshotOnTheLastDay() {
        ReportResult result = service.load(request(List.of(new KpiRequest("empresas", List.of("current")),
                new KpiRequest("toneladas", List.of("current"))), null, "month", Map.of(
                        "category", new Selection(List.of("1"), List.of()))));

        assertEquals(Map.of("current", 1.0), result.total().get("empresas"));
        assertEquals(Map.of("current", 30.0), result.total().get("toneladas"));
        assertEquals(Map.of("current", 1.0), result.rows().get(0).values().get("empresas"));
    }

    @Test
    void refusesWhatCannotBeCalculated() {
        List<KpiRequest> viajes = List.of(new KpiRequest("viajes", List.of("current")));

        assertInvalid(request(viajes, null, null, Map.of()));
        assertInvalid(request(viajes, null, "time", Map.of()));
        assertInvalid(request(List.of(new KpiRequest("ventas", List.of("current"))), null, "month", Map.of()));
        assertInvalid(request(List.of(new KpiRequest("viajes", List.of("max"))), null, "month", Map.of()));
        assertInvalid(request(List.of(new KpiRequest("viajes", ALL_MEASURES)), null, "month", Map.of()));
        assertInvalid(request(List.of(new KpiRequest("empresas", List.of("current"))), null, "department", Map.of()));
        assertInvalid(request(viajes, null, "month", Map.of("month", Selection.ALL)));
        assertInvalid(new ReportRequest(viajes, new Range("2026-10-01T00:00:00", "2026-11-07T12:00:00"), null,
                "month", Map.of()));
        assertInvalid(new ReportRequest(viajes, new Range("2026-10-01T08:00:00", "2026-11-07T23:59:59"), null,
                "month", Map.of()));
        assertInvalid(new ReportRequest(viajes, new Range("2026-11-07T00:00:00", "2026-10-01T23:59:59"), null,
                "month", Map.of()));
        assertInvalid(new ReportRequest(viajes, new Range("1 de octubre", "2026-11-07T23:59:59"), null,
                "month", Map.of()));
    }

    @Test
    void listsTheKpisInTheOrderOfTheReport() {
        List<KpiDefinition> kpis = service.kpis();
        assertEquals(List.of("viajes", "toneladas", "infracciones", "casos_cerrados", "tasa", "empresas", "vehiculos"),
                kpis.stream().map(KpiDefinition::id).toList());
        assertEquals(new KpiDefinition("viajes", "Viajes realizados"), kpis.get(0));
    }

    // Companies have no department or cargo type; a rate takes what both of its KPIs take; several KPIs, what all take
    @Test
    void listsTheFacetsAllTheKpisTake() {
        List<String> all = List.of("day", "week", "month", "year", "department", "cargoType", "category");
        List<String> companies = List.of("day", "week", "month", "year", "category");
        assertEquals(all, facetIds("viajes"));
        assertEquals(all, facetIds("tasa"));
        assertEquals(companies, facetIds("empresas"));
        assertEquals(companies, facetIds("viajes", "vehiculos"));
        assertEquals(companies, facetIds("toneladas", "tasa", "empresas"));
        assertEquals(all, facetIds("viajes", "infracciones"));
        assertEquals(new FacetDefinition("month", "Mes", "time"), service.facets(List.of("viajes")).get(2));
        assertEquals(new FacetDefinition("category", "Rubro", "catalog"),
                service.facets(List.of("viajes", "empresas")).get(4));
        assertThrows(KpiNotFoundException.class, () -> service.facets(List.of("viajes", "ventas")));
        assertThrows(InvalidReportException.class, () -> service.facets(List.of()));
    }

    private List<String> facetIds(String... kpiIds) {
        return service.facets(List.of(kpiIds)).stream().map(FacetDefinition::id).toList();
    }

    @Test
    void listsTheValuesOfACatalogFacet() {
        assertEquals(List.of(new FacetValue("1", "Agropecuario"), new FacetValue("2", "Construcción")),
                service.values("category"));
        assertThrows(InvalidReportException.class, () -> service.values("month"));
        assertThrows(FacetNotFoundException.class, () -> service.values("color"));
    }

    private void assertInvalid(ReportRequest request) {
        assertThrows(InvalidReportException.class, () -> service.load(request));
    }

    private static ReportRequest request(List<KpiRequest> kpis, Range comparison, String groupBy,
            Map<String, Selection> filters) {
        return new ReportRequest(kpis, PERIOD, comparison, groupBy, filters);
    }

    // current, previous, growth and difference; a null stays, as in the response
    private static Map<String, Double> measures(Double... values) {
        Map<String, Double> measures = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            measures.put(ALL_MEASURES.get(i), values[i]);
        }
        return measures;
    }

    private static Map<String, Double> withoutNulls(Map<String, Double> values) {
        Map<String, Double> copy = new HashMap<>(values);
        copy.values().removeIf(value -> value == null);
        return copy;
    }
}
