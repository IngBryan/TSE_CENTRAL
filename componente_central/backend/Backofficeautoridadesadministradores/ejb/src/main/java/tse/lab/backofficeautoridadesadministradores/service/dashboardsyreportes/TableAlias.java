package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

/**
 * A table a KPI uses, with the alias its query gives it and how it joins the ones before. The first table of a KPI
 * is the one it counts and has no join; the rest say theirs: "LEFT JOIN g.origenLocalidad origin".
 */
public record TableAlias(Table table, String alias, String join) {

    public static TableAlias main(Table table, String alias) {
        return new TableAlias(table, alias, null);
    }

    public static TableAlias joined(Table table, String alias, String join) {
        return new TableAlias(table, alias, join);
    }
}
