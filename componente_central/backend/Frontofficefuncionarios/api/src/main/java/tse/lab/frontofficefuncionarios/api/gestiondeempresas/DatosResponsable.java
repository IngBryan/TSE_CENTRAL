package tse.lab.frontofficefuncionarios.api.gestiondeempresas;

import java.io.Serializable;

public record DatosResponsable(String ci, String email, Long nroEmpresa) implements Serializable {
}
