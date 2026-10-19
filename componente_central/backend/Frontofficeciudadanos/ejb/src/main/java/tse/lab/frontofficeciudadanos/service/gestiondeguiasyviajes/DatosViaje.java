package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

// estado: nombre de EstadoGuia (ASIGNADA o EN_CURSO); va como texto para no depender de Modelo.
// Es una clase y no un record: el cliente de EJB remoto (JBoss Marshalling) no puede deserializar records.
public final class DatosViaje implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String idGuia;
    private final String matricula;
    private final Long nroEmpresa;
    private final String estado;
    private final LocalDate fecha;
    private final Instant fechaAsignacion;
    private final String origen;
    private final String destino;
    private final long pesoCargaKg;

    public DatosViaje(String idGuia, String matricula, Long nroEmpresa, String estado, LocalDate fecha,
            Instant fechaAsignacion, String origen, String destino, long pesoCargaKg) {
        this.idGuia = idGuia;
        this.matricula = matricula;
        this.nroEmpresa = nroEmpresa;
        this.estado = estado;
        this.fecha = fecha;
        this.fechaAsignacion = fechaAsignacion;
        this.origen = origen;
        this.destino = destino;
        this.pesoCargaKg = pesoCargaKg;
    }

    public String getIdGuia() {
        return idGuia;
    }

    public String getMatricula() {
        return matricula;
    }

    public Long getNroEmpresa() {
        return nroEmpresa;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public Instant getFechaAsignacion() {
        return fechaAsignacion;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public long getPesoCargaKg() {
        return pesoCargaKg;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DatosViaje otro && Objects.equals(idGuia, otro.idGuia)
                && Objects.equals(matricula, otro.matricula) && Objects.equals(nroEmpresa, otro.nroEmpresa)
                && Objects.equals(estado, otro.estado) && Objects.equals(fecha, otro.fecha)
                && Objects.equals(fechaAsignacion, otro.fechaAsignacion) && Objects.equals(origen, otro.origen)
                && Objects.equals(destino, otro.destino) && pesoCargaKg == otro.pesoCargaKg;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idGuia, matricula, nroEmpresa, estado, fecha, fechaAsignacion, origen, destino,
                pesoCargaKg);
    }

    @Override
    public String toString() {
        return "DatosViaje[idGuia=" + idGuia + ", matricula=" + matricula + ", estado=" + estado + ", fecha="
                + fecha + ", origen=" + origen + ", destino=" + destino + "]";
    }
}
