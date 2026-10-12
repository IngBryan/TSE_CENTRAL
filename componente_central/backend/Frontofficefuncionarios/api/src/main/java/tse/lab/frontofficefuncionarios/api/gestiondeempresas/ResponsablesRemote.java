package tse.lab.frontofficefuncionarios.api.gestiondeempresas;

// Vista remota de los responsables de empresa. La usa Frontofficeciudadanos (otro EAR del mismo
// WildFly) con @EJB(lookup = ResponsablesRemote.JNDI); los datos viajan por valor (DatosResponsable).
public interface ResponsablesRemote {

    String JNDI = "java:global/frontofficefuncionarios/frontofficefuncionarios-ejb/ResponsablesService!"
            + "tse.lab.frontofficefuncionarios.api.gestiondeempresas.ResponsablesRemote";

    // null si la cedula no es responsable de ninguna empresa
    DatosResponsable buscarPorCi(String ci);

    // Guarda el email de gub.uy si el responsable todavia no tenia uno
    void completarEmail(String ci, String email);
}
