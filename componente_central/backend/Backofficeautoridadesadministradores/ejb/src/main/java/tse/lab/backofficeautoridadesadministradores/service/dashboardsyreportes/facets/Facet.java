package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Selection;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;

/**
 * A dimension the KPIs are grouped or filtered by. It defines two things: all its values (values(), what the
 * sidebar lists) and, table by table, how a row of that table reaches its value (the links). A table without a link
 * cannot be grouped or filtered by the facet: a company has no department, the model keeps no address of it.
 */
public abstract class Facet {

    /**
     * How a row reaches the facet: the joins it needs and the expressions of the value's id and name. They are
     * written with {t} for the alias the KPI gives the table; the joins are LEFT, so a row without the value (a
     * case without a trip) stays and falls in a group without value.
     */
    public record Link(List<String> joins, String id, String name) {

        public static Link of(String expression) {
            return new Link(List.of(), expression, expression);
        }

        Link forAlias(String alias) {
            return new Link(joins.stream().map(join -> join.replace("{t}", alias)).toList(),
                    id.replace("{t}", alias), name.replace("{t}", alias));
        }
    }

    private final String id;
    private final Map<Table, Link> links;

    protected Facet(String id, Map<Table, Link> links) {
        this.id = id;
        this.links = Map.copyOf(links);
    }

    public String id() {
        return id;
    }

    // Every value of the facet, with no filter: what the sidebar lists
    public abstract List<FacetValue> values(EntityManager em);

    // How the first table of a KPI the facet is linked to reaches it, or null when none is
    public Link resolve(List<TableAlias> tables) {
        for (TableAlias table : tables) {
            Link link = links.get(table.table());
            if (link != null) {
                return link.forAlias(table.alias());
            }
        }
        return null;
    }

    // The expressions a KPI is grouped by: the id, and the name when it is another column
    public List<String> groupBy(Link link) {
        return link.id().equals(link.name()) ? List.of(link.id()) : List.of(link.id(), link.name());
    }

    // The value of a result row, from the columns of groupBy(); null when the row has none
    public FacetValue key(Link link, Object[] columns) {
        if (columns[0] == null) {
            return null;
        }
        Object name = link.id().equals(link.name()) ? columns[0] : columns[1];
        return new FacetValue(columns[0].toString(), name == null ? null : name.toString());
    }

    // Conditions over the id that keep the included values (all when none is) minus the excluded ones; the
    // parameters take this facet's id as prefix, so several facets can filter one query
    public List<String> filter(Link link, Selection selection, Map<String, Object> parameters) {
        List<String> conditions = new ArrayList<>();
        if (!selection.include().isEmpty()) {
            conditions.add(link.id() + " IN :" + id + "Included");
            parameters.put(id + "Included", parameterValues(selection.include()));
        }
        if (!selection.exclude().isEmpty()) {
            conditions.add("(" + link.id() + " IS NULL OR " + link.id() + " NOT IN :" + id + "Excluded)");
            parameters.put(id + "Excluded", parameterValues(selection.exclude()));
        }
        return conditions;
    }

    // The ids as the query compares them: text by default, numbers for a catalog with numeric ids
    protected Object parameterValues(List<String> ids) {
        return ids;
    }
}
