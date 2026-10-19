package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

// Un evento de un vehiculo que es evidencia de un caso: de tracking (en MongoDB) o un pesaje (eventos_pesajes).
// Es una clase y no un record: el cliente de EJB remoto (JBoss Marshalling) no puede deserializar records.
public final class EventoFiscalizado implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String matricula;
    private final Instant fechaHora;
    private final OrigenEvento origen;

    public EventoFiscalizado(String matricula, Instant fechaHora, OrigenEvento origen) {
        this.matricula = matricula;
        this.fechaHora = fechaHora;
        this.origen = origen;
    }

    public String getMatricula() {
        return matricula;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public OrigenEvento getOrigen() {
        return origen;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EventoFiscalizado otro && Objects.equals(matricula, otro.matricula)
                && Objects.equals(fechaHora, otro.fechaHora) && origen == otro.origen;
    }

    @Override
    public int hashCode() {
        return Objects.hash(matricula, fechaHora, origen);
    }

    @Override
    public String toString() {
        return "EventoFiscalizado[matricula=" + matricula + ", fechaHora=" + fechaHora + ", origen=" + origen + "]";
    }
}
