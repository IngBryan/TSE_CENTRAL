package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     *
     * exclusion is filled in by resolve(): the condition that leaves out the rows of the table that reach an
     * excluded value, with :values for them.
     */
    public record Link(List<String> joins, String id, String name, String exclusion) {

        public Link(List<String> joins, String id, String name) {
            this(joins, id, name, null);
        }

        public static Link of(String expression) {
            return new Link(List.of(), expression, expression);
        }

        Link forAlias(String alias) {
            return new Link(joins.stream().map(join -> join.replace("{t}", alias)).toList(),
                    id.replace("{t}", alias), name.replace("{t}", alias), exclusion);
        }
    }

    private static final String SUBQUERY = "Excluded";
    private static final Pattern DECLARED_ALIAS = Pattern.compile("JOIN\\s+\\S+\\s+(\\w+)");

    private final String id;
    private final String name;
    private final Map<Table, Link> links;

    protected Facet(String id, String name, Map<Table, Link> links) {
        this.id = id;
        this.name = name;
        this.links = Map.copyOf(links);
    }

    public String id() {
        return id;
    }

    // What the report shows: "Departamento"
    public String name() {
        return name;
    }

    // Every value of the facet, with no filter: what the sidebar lists
    public abstract List<FacetValue> values(EntityManager em);

    // How the first table of a KPI the facet is linked to reaches it, or null when none is
    public Link resolve(List<TableAlias> tables) {
        for (TableAlias table : tables) {
            Link link = links.get(table.table());
            if (link != null) {
                Link resolved = link.forAlias(table.alias());
                return new Link(resolved.joins(), resolved.id(), resolved.name(), exclusion(link, table));
            }
        }
        return null;
    }

    /**
     * Excluding leaves out whole rows of the table, not rows of its joins: a company that works in an excluded rubro
     * is out even if it works in others too. The rows that reach an excluded value come from a subquery with the
     * same joins, under aliases of their own; a row of a joined table that is not there (a case without a trip) has
     * no value, so it stays.
     */
    private static String exclusion(Link link, TableAlias table) {
        String row = table.alias() + SUBQUERY;
        List<String> joins = link.joins().stream().map(join -> join.replace("{t}", row)).toList();
        String value = link.id().replace("{t}", row);
        for (String alias : declaredAliases(link.joins())) {
            String word = "\\b" + alias + "\\b";
            String renamed = alias + SUBQUERY;
            joins = joins.stream().map(join -> join.replaceAll(word, renamed)).toList();
            value = value.replaceAll(word, renamed);
        }

        String excluded = "SELECT " + row + " FROM " + table.table().entity() + " " + row
                + (joins.isEmpty() ? "" : " " + String.join(" ", joins)) + " WHERE " + value + " IN :values";
        String condition = table.alias() + " NOT IN (" + excluded + ")";
        return table.join() == null ? condition : "(" + table.alias() + " IS NULL OR " + condition + ")";
    }

    // "LEFT JOIN {t}.rubros category" and "LEFT JOIN Rubro category ON ..." declare "category"
    private static List<String> declaredAliases(List<String> joins) {
        List<String> aliases = new ArrayList<>();
        for (String join : joins) {
            Matcher declared = DECLARED_ALIAS.matcher(join);
            if (declared.find()) {
                aliases.add(declared.group(1));
            }
        }
        return aliases;
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
            conditions.add(link.exclusion().replace(":values", ":" + id + "Excluded"));
            parameters.put(id + "Excluded", parameterValues(selection.exclude()));
        }
        return conditions;
    }

    // The ids as the query compares them: text by default, numbers for a catalog with numeric ids
    protected Object parameterValues(List<String> ids) {
        return ids;
    }
}
