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
    // Para seguir el evento en los logs; no es parte de su identidad
    private final String correlationId;
    private final Long revisionReglas;
    private final Long pesoBaseKg;
    public Long getRevisionReglas() { return revisionReglas; }
    public Long getPesoBaseKg() { return pesoBaseKg; }

    public EventoFiscalizado(String matricula, Instant fechaHora, OrigenEvento origen) {
        this(matricula, fechaHora, origen, null);
    }

    public EventoFiscalizado(String matricula, Instant fechaHora, OrigenEvento origen, String correlationId) {
        this(matricula, fechaHora, origen, correlationId, null, null);
    }

    public EventoFiscalizado(String matricula, Instant fechaHora, OrigenEvento origen, String correlationId,
            Long revisionReglas, Long pesoBaseKg) {
        this.revisionReglas=revisionReglas;
        this.pesoBaseKg=pesoBaseKg;
        this.matricula = matricula;
        this.fechaHora = fechaHora;
        this.origen = origen;
        this.correlationId = correlationId;
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

    public String getCorrelationId() {
        return correlationId;
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
