package tse.lab.model;

/** Contrato REST compartido por las vistas de funcionarios y de la empresa. */
public record EvidenciaHabilitacionVista(String fechaHora, String origen, String nroPermiso,
        String emisionPermiso, String vencimientoPermiso, String estadoPermiso,
        String emisionItv, String vencimientoItv, String estadoItv) {

    public static EvidenciaHabilitacionVista de(EvidenciaHabilitacion prueba) {
        return new EvidenciaHabilitacionVista(prueba.getFechaHora().toString(), prueba.getOrigen(),
                prueba.getNroPermiso(), texto(prueba.getEmisionPermiso()), texto(prueba.getVencimientoPermiso()),
                prueba.getEstadoPermiso().name(), texto(prueba.getEmisionItv()),
                texto(prueba.getVencimientoItv()), prueba.getEstadoItv().name());
    }

    private static String texto(Object valor) { return valor == null ? null : valor.toString(); }
}
