package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import java.io.Serializable;
import java.util.Objects;
import tse.lab.model.EvidenciaHabilitacion;

// nroEmpresa: la empresa duenia del vehiculo, a la que se notifican sus casos de fiscalizacion.
// pesoKg y capacidadCargaKg: lo declarado del vehiculo; su suma es el maximo que puede marcar una balanza.
// Es una clase y no un record: el cliente de EJB remoto (JBoss Marshalling) no puede deserializar records.
public final class DatosVehiculo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String matricula;
    private final Long nroEmpresa;
    private final long pesoKg;
    private final long capacidadCargaKg;
    private final EvidenciaHabilitacion habilitacion;

    public DatosVehiculo(String matricula, Long nroEmpresa, long pesoKg, long capacidadCargaKg) {
        this(matricula, nroEmpresa, pesoKg, capacidadCargaKg, null);
    }

    public DatosVehiculo(String matricula, Long nroEmpresa, long pesoKg, long capacidadCargaKg,
            EvidenciaHabilitacion habilitacion) {
        this.matricula = matricula;
        this.nroEmpresa = nroEmpresa;
        this.pesoKg = pesoKg;
        this.capacidadCargaKg = capacidadCargaKg;
        this.habilitacion = habilitacion;
    }

    public EvidenciaHabilitacion getHabilitacion() { return habilitacion; }

    public String getMatricula() {
        return matricula;
    }

    public Long getNroEmpresa() {
        return nroEmpresa;
    }

    public long getPesoKg() {
        return pesoKg;
    }

    public long getCapacidadCargaKg() {
        return capacidadCargaKg;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DatosVehiculo otro && Objects.equals(matricula, otro.matricula)
                && Objects.equals(nroEmpresa, otro.nroEmpresa) && pesoKg == otro.pesoKg
                && capacidadCargaKg == otro.capacidadCargaKg && Objects.equals(habilitacion, otro.habilitacion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matricula, nroEmpresa, pesoKg, capacidadCargaKg, habilitacion);
    }

    @Override
    public String toString() {
        return "DatosVehiculo[matricula=" + matricula + ", nroEmpresa=" + nroEmpresa + ", pesoKg=" + pesoKg
                + ", capacidadCargaKg=" + capacidadCargaKg + "]";
    }
}
