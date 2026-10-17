package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

// Pesaje registrado por una balanza del nodo de balanzas. idExterno: id del pesaje en ese nodo;
// idExternoBalanza: id de la balanza en ese nodo (idExterno del NodoBalanza).
// Es una clase y no un record: el cliente de EJB remoto (JBoss Marshalling) no puede deserializar records.
public final class DatosPesaje implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long idExterno;
    private final long idExternoBalanza;
    private final String matricula;
    private final Instant fechaHora;
    private final long pesoKg;

    public DatosPesaje(long idExterno, long idExternoBalanza, String matricula, Instant fechaHora, long pesoKg) {
        this.idExterno = idExterno;
        this.idExternoBalanza = idExternoBalanza;
        this.matricula = matricula;
        this.fechaHora = fechaHora;
        this.pesoKg = pesoKg;
    }

    public long getIdExterno() {
        return idExterno;
    }

    public long getIdExternoBalanza() {
        return idExternoBalanza;
    }

    public String getMatricula() {
        return matricula;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public long getPesoKg() {
        return pesoKg;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DatosPesaje otro && idExterno == otro.idExterno && idExternoBalanza == otro.idExternoBalanza
                && Objects.equals(matricula, otro.matricula) && Objects.equals(fechaHora, otro.fechaHora)
                && pesoKg == otro.pesoKg;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idExterno, idExternoBalanza, matricula, fechaHora, pesoKg);
    }

    @Override
    public String toString() {
        return "DatosPesaje[idExterno=" + idExterno + ", balanza=" + idExternoBalanza + ", matricula=" + matricula
                + ", fechaHora=" + fechaHora + ", pesoKg=" + pesoKg + "]";
    }
}
