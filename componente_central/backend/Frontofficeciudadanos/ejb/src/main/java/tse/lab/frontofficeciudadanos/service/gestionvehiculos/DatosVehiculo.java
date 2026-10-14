package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import java.io.Serializable;
import java.util.Objects;

// nroEmpresa: la empresa duenia del vehiculo, a la que se notifican sus casos de fiscalizacion.
// Es una clase y no un record: el cliente de EJB remoto (JBoss Marshalling) no puede deserializar records.
public final class DatosVehiculo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String matricula;
    private final Long nroEmpresa;

    public DatosVehiculo(String matricula, Long nroEmpresa) {
        this.matricula = matricula;
        this.nroEmpresa = nroEmpresa;
    }

    public String getMatricula() {
        return matricula;
    }

    public Long getNroEmpresa() {
        return nroEmpresa;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DatosVehiculo otro && Objects.equals(matricula, otro.matricula)
                && Objects.equals(nroEmpresa, otro.nroEmpresa);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matricula, nroEmpresa);
    }

    @Override
    public String toString() {
        return "DatosVehiculo[matricula=" + matricula + ", nroEmpresa=" + nroEmpresa + "]";
    }
}
